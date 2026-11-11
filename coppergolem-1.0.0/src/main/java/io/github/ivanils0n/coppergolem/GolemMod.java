package io.github.ivanils0n.coppergolem;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import io.github.ivanils0n.coppergolem.Links.Link;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Golem de Cobre Inteligente, 100% no servidor: o Cajado do Golem é um item vanilla marcado (com modelo próprio pelo
 * resource pack). Nenhum bloco, item ou entidade nova é registrado: o golem é o do jogo, só que vinculado a baús.
 *
 * Uso: clique num golem com o cajado para ligá-lo (abre a tela). Com o cajado ligado, clique num baú de cobre (recarga),
 * ou em baús e barris (guardar ou origem, conforme o modo da tela).
 */
public final class GolemMod implements ModInitializer {
	static final Logger LOG = LoggerFactory.getLogger("coppergolem");
	static final String NAME = "Golem de Cobre";
	static final String WAND_TAG = "coppergolem_wand";
	static final double USE_RANGE_SQR = 12 * 12;

	private static final String PACK_URL = "https://github.com/ivanils0n/MC-Mods/releases/download/coppergolem-" + modVersion() + "/coppergolem-pack-";
	static final String PACK_SHA1 = readPackHash();
	private static final UUID PACK_ID = UUID.nameUUIDFromBytes("coppergolem:pack".getBytes(StandardCharsets.UTF_8));
	private static final Set<UUID> PACK_OK = ConcurrentHashMap.newKeySet();

	static MinecraftServer server;
	static Links links;
	static final Map<UUID, GolemTask> TASKS = new HashMap<>();

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(s -> {
			server = s;
			links = Links.load(s.getWorldPath(LevelResource.ROOT).resolve("coppergolem.json"));
			Categories.build(s);
			LOG.info("{} golems vinculados", links.size());
			if (Boolean.getBoolean("coppergolem.selftest")) SelfTest.run(s);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
			if (links != null) links.saveIfDirty();
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			server = null;
			links = null;
			TASKS.clear();
			PACK_OK.clear();
			Categories.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(s -> {
			if (links != null && s.getTickCount() % 20 == 0) links.saveIfDirty();
		});
		ServerTickEvents.END_LEVEL_TICK.register(GolemMod::onWorldTick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> {
			if (PACK_SHA1 == null || !s.isDedicatedServer()) return; // singleplayer: as texturas vêm do .jar
			sender.sendPacket(new ClientboundResourcePackPushPacket(PACK_ID, PACK_URL + PACK_SHA1 + ".zip", PACK_SHA1, false,
				Optional.of(Component.literal("Texturas do Golem de Cobre"))));
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> PACK_OK.remove(handler.player.getUUID()));
		// Golem morto (ou virado estátua): o vínculo some. Descarregar o chunk não conta.
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (links != null && entity instanceof CopperGolem && entity.getRemovalReason() != null && entity.getRemovalReason().shouldDestroy()) {
				links.remove(entity.getUUID());
				TASKS.remove(entity.getUUID());
			}
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> useOnGolem(player, hand, entity));
		UseBlockCallback.EVENT.register(GolemMod::onUseBlock);
	}

	/** Cajado em punho clicando num golem: liga o cajado a ele e abre a tela. Também chamado pelo mixin do golem. */
	public static InteractionResult useOnGolem(Player player, InteractionHand hand, Entity entity) {
		if (links == null || hand != InteractionHand.MAIN_HAND || player.isSpectator() || !isWand(player.getMainHandItem()) || !(entity instanceof CopperGolem)) {
			return InteractionResult.PASS;
		}
		if (!(player instanceof ServerPlayer sp)) return InteractionResult.CONSUME; // cliente: não deixa o jogo fazer a ação normal do golem
		try {
			ItemStack wand = sp.getMainHandItem();
			CustomData data = wand.get(DataComponents.CUSTOM_DATA);
			CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
			tag.putString("golem", entity.getUUID().toString());
			wand.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
			wand.set(DataComponents.LORE, new ItemLore(List.of(
				plain("Ligado a um Golem de Cobre.", ChatFormatting.GRAY),
				plain("Clique em baús (de cobre = recarga) para vincular.", ChatFormatting.DARK_GRAY))));
			sp.level().playSound(null, entity.blockPosition(), SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 1f, 1.2f);
			say(sp, plain("Cajado ligado a este golem.", ChatFormatting.GREEN));
			GolemMenu.open(sp, entity.getUUID());
		} catch (RuntimeException e) {
			LOG.error("Erro ao ligar o cajado ao golem", e);
			say(sp, plain("Erro ao abrir a tela do golem (veja o log).", ChatFormatting.RED));
		}
		return InteractionResult.SUCCESS;
	}

	// ---------- eventos ----------

	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (links == null || hand != InteractionHand.MAIN_HAND || !(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp)
			|| sp.isSpectator() || !isWand(sp.getMainHandItem())) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		boolean copper = Boxes.isCopperChest(sl, pos);
		if (!copper && !Boxes.isBox(sl.getBlockEntity(pos))) return InteractionResult.PASS; // outros blocos: uso normal
		if (golemOf(sp.getMainHandItem()) == null) say(sp, plain("Primeiro clique num Golem de Cobre com o cajado.", ChatFormatting.RED));
		else if (copper) say(sp, link(sp, sl, sp.getMainHandItem(), pos, Role.REFILL));
		else LinkMenu.open(sp, pos); // baú comum: escolhe origem, guardar ou desligar
		return InteractionResult.SUCCESS;
	}

	private static void onWorldTick(ServerLevel sl) {
		if (links == null || sl.getGameTime() % 2 != 0) return;
		String dim = dim(sl);
		for (UUID id : links.golems()) {
			Link l = links.get(id);
			if (l == null || !dim.equals(l.dim) || !(sl.getEntity(id) instanceof CopperGolem g) || !g.isAlive()) continue;
			TASKS.computeIfAbsent(id, k -> new GolemTask()).step(sl, g, l);
		}
		// Quem segura o cajado ligado a um golem vê partículas nos baús vinculados (só ele vê).
		if (sl.getGameTime() % 20 != 0) return;
		for (ServerPlayer p : sl.players()) {
			UUID id = golemOf(p.getMainHandItem());
			Link l = id == null ? null : links.get(id);
			if (l == null || !dim.equals(l.dim)) continue;
			if (l.refill != null) particles(sl, p, Link.pos(l.refill), ParticleTypes.WAX_ON, 3);
			for (int[] s : l.sources) particles(sl, p, Link.pos(s), ParticleTypes.PORTAL, 4);
			for (int[] d : l.dests) particles(sl, p, Link.pos(d), ParticleTypes.END_ROD, 2);
		}
	}

	static void particles(ServerLevel sl, ServerPlayer p, BlockPos pos, ParticleOptions type, int count) {
		sl.sendParticles(p, type, true, false, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, count, 0.3, 0.35, 0.3, 0.02);
	}

	// ---------- vínculos ----------

	enum Role {REFILL, SOURCE, DEST, NONE}

	/** Liga o baú clicado (e a outra metade, se duplo) ao golem do cajado no papel escolhido. NONE desliga. Devolve a mensagem. */
	static Component link(ServerPlayer sp, ServerLevel sl, ItemStack wand, BlockPos pos, Role role) {
		UUID id = golemOf(wand);
		if (id == null) return plain("Primeiro clique num Golem de Cobre com o cajado.", ChatFormatting.RED);
		if (!sl.mayInteract(sp, pos)) return plain("Você não pode mexer nesse baú.", ChatFormatting.RED);
		Link existing = links.get(id);
		if (existing != null && !existing.dim.equals(dim(sl)) && !existing.isEmpty()) {
			return plain("Esse golem está ligado a baús de outra dimensão. Use 'Desfazer' na tela dele para recomeçar.", ChatFormatting.RED);
		}
		Link l = links.getOrCreate(id, dim(sl));
		List<BlockPos> group = Boxes.group(sl, pos);
		if (role == Role.REFILL) {
			if (l.refill != null && Link.indexOf(List.of(l.refill), pos) >= 0) {
				l.refill = null;
				links.markDirty();
				sl.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1f, 0.6f);
				return plain("Baú de recarga desligado.", ChatFormatting.YELLOW);
			}
			l.refill = Link.arr(pos);
			links.markDirty();
			burst(sl, sp, pos, ParticleTypes.WAX_ON);
			return plain("Baú de recarga ligado: ponha barras de cobre nele (1 a 3 por vez que o golem enferrujar).", ChatFormatting.GOLD);
		}
		boolean had = false;
		for (BlockPos g : group) {
			for (List<int[]> list : List.of(l.sources, l.dests)) {
				int at = Link.indexOf(list, g);
				if (at >= 0) {
					list.remove(at);
					had = true;
				}
			}
		}
		if (role == Role.NONE) {
			links.markDirty();
			sl.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1f, 0.6f);
			return had ? plain("Baú desligado.", ChatFormatting.YELLOW) : plain("Esse baú não estava ligado.", ChatFormatting.GRAY);
		}
		boolean origin = role == Role.SOURCE;
		List<int[]> list = origin ? l.sources : l.dests;
		if (list.size() >= (origin ? Links.MAX_SOURCES : Links.MAX_DESTS)) {
			links.markDirty();
			return plain("Limite de " + (origin ? Links.MAX_SOURCES + " baús de origem" : Links.MAX_DESTS + " baús de guardar") + " atingido.", ChatFormatting.RED);
		}
		list.add(Link.arr(group.get(0)));
		links.markDirty();
		burst(sl, sp, pos, origin ? ParticleTypes.PORTAL : ParticleTypes.HAPPY_VILLAGER);
		return plain("Baú " + (origin ? "de origem" : "de guardar") + " ligado." + (origin ? "" : " Moldura com um item ao lado dele: só esse item entra."), ChatFormatting.GREEN);
	}

	private static void burst(ServerLevel sl, ServerPlayer sp, BlockPos pos, ParticleOptions type) {
		for (BlockPos p : Boxes.group(sl, pos)) particles(sl, sp, p, type, 12);
		sl.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1f, 1.4f);
	}

	// ---------- itens ----------

	static String dim(Level level) {
		return level.dimension().identifier().toString();
	}

	static boolean isWand(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return stack.is(Items.WARPED_FUNGUS_ON_A_STICK) && data != null && data.copyTag().contains(WAND_TAG);
	}

	/** Golem a que o cajado está ligado, ou null. */
	static UUID golemOf(ItemStack wand) {
		CustomData data = wand.get(DataComponents.CUSTOM_DATA);
		if (data == null || !isWand(wand)) return null;
		Optional<String> s = data.copyTag().getString("golem");
		try {
			return s.isEmpty() ? null : UUID.fromString(s.get());
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** O mesmo item da receita (data/coppergolem/recipe/wand.json). */
	static ItemStack wandItem() {
		ItemStack s = new ItemStack(Items.WARPED_FUNGUS_ON_A_STICK);
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(WAND_TAG, true);
		s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		s.set(DataComponents.CUSTOM_NAME, plain("Cajado do Golem", ChatFormatting.GOLD));
		s.set(DataComponents.MAX_STACK_SIZE, 1);
		s.set(DataComponents.ITEM_MODEL, model("wand"));
		return s;
	}

	/** Modelo próprio (assets/coppergolem/items/<nome>.json): nenhum outro resource pack mexe nesse caminho. */
	static Identifier model(String name) {
		return Identifier.fromNamespaceAndPath("coppergolem", name);
	}

	// ---------- resource pack e título da tela ----------

	/** Versão do mod sem o sufixo "+mc..." (a do release no GitHub, ex.: 1.0.0). */
	private static String modVersion() {
		String v = FabricLoader.getInstance().getModContainer("coppergolem").orElseThrow().getMetadata().getVersion().getFriendlyString();
		int i = v.indexOf('+');
		return i < 0 ? v : v.substring(0, i);
	}

	private static String readPackHash() {
		try (InputStream in = GolemMod.class.getResourceAsStream("/coppergolem/pack.sha1")) {
			if (in == null) return null;
			String sha = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
			return sha.matches("[0-9a-f]{40}") ? sha : null;
		} catch (IOException e) {
			return null;
		}
	}

	/** Chamado pelo mixin quando o jogador responde ao pedido do resource pack. */
	public static void packStatus(ServerPlayer p, UUID id, ServerboundResourcePackPacket.Action action) {
		if (!PACK_ID.equals(id)) return;
		if (action == ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED) PACK_OK.add(p.getUUID());
		else if (action != ServerboundResourcePackPacket.Action.ACCEPTED && action != ServerboundResourcePackPacket.Action.DOWNLOADED) PACK_OK.remove(p.getUUID());
	}

	/** Singleplayer: as texturas vêm do .jar do dono do mundo. Servidor: só de quem carregou o pack. */
	static boolean hasPack(ServerPlayer p) {
		return server.isDedicatedServer() ? PACK_OK.contains(p.getUUID()) : server.isSingleplayerOwner(p.nameAndId());
	}

	/** Título da tela: com o pack, o fundo inteiro é um caractere da fonte coppergolem:gui; sem ele, título comum. */
	static Component title(ServerPlayer p) {
		return title(p, "\uE000", NAME);
	}

	/** bg = caractere do fundo na fonte coppergolem:gui (E000 = tela do golem, E001 = tela curta de 1 fileira). */
	static Component title(ServerPlayer p, String bg, String label) {
		if (!hasPack(p)) return Component.literal(label);
		return themedTitle("\uF000" + bg + "\uF001\uF002", label);
	}

	static Component themedTitle(String background, String label) {
		JsonObject bg = new JsonObject();
		bg.addProperty("text", background);
		bg.addProperty("font", "coppergolem:gui");
		bg.addProperty("color", "white"); // o título vanilla é cinza escuro e escureceria o desenho
		JsonObject text = new JsonObject();
		text.addProperty("text", label);
		text.addProperty("color", "#FFE9D8");
		JsonArray parts = new JsonArray();
		parts.add(new JsonPrimitive("")); // raiz sem estilo: o texto não herda a fonte do fundo
		parts.add(bg);
		parts.add(text);
		return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, parts).result().orElse(Component.literal(NAME));
	}

	// ---------- utilidades ----------

	/** Item de vitrine das telas: com o pack, ícone próprio (coppergolem:<model>); sem ele, o item comum parecido. */
	static ItemStack icon(ServerPlayer p, String model, Item fallback, String name, ChatFormatting color, String... lines) {
		boolean pack = hasPack(p);
		ItemStack s = new ItemStack(pack ? Items.PAPER : fallback);
		if (pack) s.set(DataComponents.ITEM_MODEL, model(model));
		s.set(DataComponents.CUSTOM_NAME, plain(name, color));
		List<Component> lore = new java.util.ArrayList<>();
		for (String line : lines) lore.add(plain(line, ChatFormatting.GRAY));
		s.set(DataComponents.LORE, new ItemLore(lore));
		return s;
	}

	static MutableComponent plain(String text, ChatFormatting color) {
		return Component.literal(text).withStyle(st -> st.withItalic(false).withColor(color));
	}

	static void say(Player player, Component text) {
		if (player instanceof ServerPlayer sp) sp.sendSystemMessage(text);
	}
}
