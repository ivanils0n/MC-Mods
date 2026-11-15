package io.github.ivanils0n.coletor;

import io.github.ivanils0n.coletor.ColetorStore.Machine;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coletor, 100% no servidor: é um barril de verdade (27 espaços, funil puxa e põe itens como em qualquer barril)
 * marcado em <mundo>/coletor.json e coberto por um item_display (com o resource pack ele ganha textura própria; sem o
 * pack continua com cara de barril). Nenhum bloco ou item novo é registrado: sem o mod sobra um barril comum com a
 * colheita dentro.
 */
public final class Coletor implements ModInitializer {
	static final Logger LOG = LoggerFactory.getLogger("coletor");
	static final String NAME = "Coletor";
	static final String ITEM_TAG = "coletor";
	private static final double VISUAL_RANGE_SQR = 64 * 64; // sem jogador a essa distância o visual não é conferido

	private static final String PACK_URL = "https://github.com/ivanils0n/MC-Mods/releases/download/coletor-" + modVersion() + "/coletor-pack-";
	static final String PACK_SHA1 = readPackHash();
	private static final UUID PACK_ID = UUID.nameUUIDFromBytes("coletor:pack".getBytes(StandardCharsets.UTF_8));
	/** Tag desta execução: o visual nunca é salvo (mixin/EntityMixin); um "coletor" sem ela é sobra antiga. */
	private static final String RUN_TAG = "coletor_run_" + Long.toHexString(new java.util.Random().nextLong());
	private static final List<Entity> LEFTOVERS = new ArrayList<>();

	private static final Set<UUID> PACK_OK = ConcurrentHashMap.newKeySet();
	private static int selftestIn;
	static MinecraftServer server;
	static ColetorStore store;

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(s -> {
			server = s;
			store = ColetorStore.load(s.getWorldPath(LevelResource.ROOT).resolve("coletor.json"));
			LOG.info("{} coletores carregados", store.size());
			if (Boolean.getBoolean("coletor.selftest")) {
				s.overworld().setChunkForced(0, 0, true); // o teste roda alguns ticks depois, com o chunk já carregado
				selftestIn = 40;
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
			if (store != null) store.flush();
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			server = null;
			store = null;
			LEFTOVERS.clear();
			PACK_OK.clear();
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains("coletor") && !entity.entityTags().contains(RUN_TAG)) LEFTOVERS.add(entity);
		});
		ServerTickEvents.END_SERVER_TICK.register(s -> {
			if (selftestIn > 0 && --selftestIn == 0) SelfTest.run(s);
			for (Entity e : LEFTOVERS) if (!e.isRemoved()) e.discard();
			LEFTOVERS.clear();
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> {
			if (PACK_SHA1 == null || !s.isDedicatedServer()) return; // singleplayer: as texturas vêm do .jar
			sender.sendPacket(new ClientboundResourcePackPushPacket(PACK_ID, PACK_URL + PACK_SHA1 + ".zip", PACK_SHA1, false,
				Optional.of(Component.literal("Texturas do Coletor"))));
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> PACK_OK.remove(handler.player.getUUID()));
		UseBlockCallback.EVENT.register(Coletor::onUseBlock);
		PlayerBlockBreakEvents.BEFORE.register(Coletor::onBreak);
		ServerTickEvents.END_LEVEL_TICK.register(Coletor::onWorldTick);
	}

	static boolean isMachine(ServerLevel sl, BlockPos pos) {
		return store != null && store.get(dim(sl), pos) != null;
	}

	// ---------- eventos ----------

	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (store == null || !(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp) || sp.isSpectator()) {
			return InteractionResult.PASS;
		}
		ItemStack held = sp.getItemInHand(hand);
		BlockPos clicked = hit.getBlockPos();
		Machine m = store.get(dim(sl), clicked);
		// Clique no Coletor abre a tela dele (agachado com um item na mão: usa o item, como num baú).
		if (m != null && !(sp.isSecondaryUseActive() && !held.isEmpty())) {
			if (hand == InteractionHand.MAIN_HAND && sl.getBlockEntity(clicked) instanceof BarrelBlockEntity be) {
				boolean pack = hasPack(sp);
				sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new ColetorMenu(id, inv, be, m, pack), title(pack)));
			}
			return InteractionResult.SUCCESS;
		}
		if (!isMachineItem(held)) return InteractionResult.PASS;
		// Clicar num baú, barril etc. sem agachar abre o bloco, como com qualquer item na mão.
		if (!sp.isSecondaryUseActive() && sl.getBlockState(clicked).getMenuProvider(sl, clicked) != null) return InteractionResult.PASS;
		place(sp, sl, hit, held);
		return InteractionResult.SUCCESS;
	}

	private static boolean onBreak(Level level, Player player, BlockPos pos, BlockState state, BlockEntity be) {
		if (store == null || !(level instanceof ServerLevel sl)) return true;
		Machine m = store.get(dim(sl), pos);
		if (m == null) return true;
		boolean survival = !player.isCreative();
		if (be instanceof BarrelBlockEntity b) { // a colheita guardada sempre volta
			for (int i = 0; i < b.getContainerSize(); i++) {
				ItemStack s = b.removeItemNoUpdate(i);
				if (!s.isEmpty()) Block.popResource(sl, pos, s);
			}
		}
		removeMachine(sl, m);
		sl.destroyBlock(pos, false, player);
		if (survival) {
			player.getMainHandItem().hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
			Block.popResource(sl, pos, machineItem()); // madeira: volta com qualquer ferramenta, como o barril
		}
		return false; // a quebra vanilla soltaria um barril comum
	}

	private static void onWorldTick(ServerLevel sl) {
		if (store == null) return;
		boolean second = sl.getGameTime() % 20 == 0;
		if (sl.getGameTime() % 100 == 0 && sl == server.overworld()) store.flush();
		for (Machine m : store.inDim(dim(sl))) {
			BlockPos pos = m.pos();
			if (!sl.isLoaded(pos) || !sl.isPositionEntityTicking(pos)) continue;
			BlockState state = sl.getBlockState(pos);
			if (!state.is(Blocks.BARREL) || !(sl.getBlockEntity(pos) instanceof BarrelBlockEntity be)) {
				// Sumiu por outro meio (explosão, comando, editor de mundo): sobra o que o jogo deixou.
				LOG.info("Coletor em {} não existe mais; removido", pos);
				removeMachine(sl, m);
				continue;
			}
			Harvester.tick(sl, m, be, sl.hasNeighborSignal(pos));
			if (second && anyPlayerNear(sl, pos)) {
				ensureVisual(sl, m);
				updateLight(sl, m);
			}
		}
	}

	private static boolean anyPlayerNear(ServerLevel sl, BlockPos pos) {
		for (ServerPlayer p : sl.players()) if (p.distanceToSqr(Vec3.atCenterOf(pos)) < VISUAL_RANGE_SQR) return true;
		return false;
	}

	// ---------- colocar e remover ----------

	private static void place(ServerPlayer sp, ServerLevel sl, BlockHitResult hit, ItemStack stack) {
		BlockPos clicked = hit.getBlockPos();
		BlockPos pos = sl.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(hit.getDirection());
		if (!sp.mayUseItemAt(pos, hit.getDirection(), stack) || !sl.mayInteract(sp, pos)) return;
		if (!sl.getBlockState(pos).canBeReplaced() || !sl.isUnobstructed(Blocks.BARREL.defaultBlockState(), pos, CollisionContext.empty())) return;
		if (placeMachine(sl, pos, stack) == null) {
			say(sp, "Não foi possível colocar o " + NAME + " aqui.", ChatFormatting.RED);
			return;
		}
		if (!sp.isCreative()) stack.shrink(1);
		say(sp, NAME + " colocado! Ele colhe e replanta as plantações maduras em volta (9×9). Guarde farinha de osso nele para acelerar.", ChatFormatting.GREEN);
	}

	static Machine placeMachine(ServerLevel sl, BlockPos pos, ItemStack stack) {
		if (!sl.setBlock(pos, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP), Block.UPDATE_ALL)) return null;
		if (sl.getBlockEntity(pos) instanceof BarrelBlockEntity be) be.applyComponentsFromItemStack(stack); // nome na tela
		Machine m = new Machine(dim(sl), pos);
		store.add(m);
		spawnVisual(sl, m);
		sl.playSound(null, pos, SoundEvents.COMPOSTER_FILL_SUCCESS, SoundSource.BLOCKS, 1.0f, 1.0f);
		return m;
	}

	static void removeMachine(ServerLevel sl, Machine m) {
		store.remove(m);
		killVisual(sl, m.pos());
	}

	// ---------- visual ----------

	private static void ensureVisual(ServerLevel sl, Machine m) {
		if (visuals(sl, m.pos()).size() != 1) spawnVisual(sl, m);
	}

	/** Um item_display com o modelo do Coletor por cima do barril (1,004x o bloco, para não piscar com ele). */
	static void spawnVisual(ServerLevel sl, Machine m) {
		BlockPos pos = m.pos();
		killVisual(sl, pos);
		String nbt = "Tags:[\"coletor\",\"" + tag(pos) + "\",\"" + RUN_TAG + "\"],item:{id:\"minecraft:barrel\",count:1,"
			+ "components:{\"minecraft:custom_model_data\":{strings:[\"coletor\"]}}},"
			+ "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,-0.5f,0f],scale:[1.004f,1.004f,1.004f]}";
		command(sl, pos, String.format(Locale.ROOT, "summon minecraft:item_display %.4f %.4f %.4f {%s}",
			pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, nbt));
		m.light = -1;
		updateLight(sl, m);
	}

	/**
	 * A entidade pega a luz do bloco onde está (o de cima do barril, que pode estar coberto): a luz é fixada na maior
	 * entre os 6 vizinhos e conferida a cada segundo.
	 */
	private static void updateLight(ServerLevel sl, Machine m) {
		BlockPos pos = m.pos();
		int block = 0, sky = 0;
		for (Direction d : Direction.values()) {
			BlockPos p = pos.relative(d);
			block = Math.max(block, sl.getBrightness(LightLayer.BLOCK, p));
			sky = Math.max(sky, sl.getBrightness(LightLayer.SKY, p));
		}
		int packed = block << 4 | sky;
		if (packed == m.light) return;
		m.light = packed;
		for (Entity e : visuals(sl, pos)) {
			command(sl, pos, "data merge entity " + e.getStringUUID() + " {brightness:{block:" + block + ",sky:" + sky + "}}");
		}
	}

	static void killVisual(ServerLevel sl, BlockPos pos) {
		for (Entity e : visuals(sl, pos)) e.discard();
	}

	static List<Entity> visuals(ServerLevel sl, BlockPos pos) {
		String tag = tag(pos);
		return sl.getEntitiesOfClass(Entity.class, new AABB(pos).inflate(1), e -> e.entityTags().contains(tag));
	}

	private static String tag(BlockPos pos) {
		return "coletor_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
	}

	/** Roda um comando como o próprio servidor (funciona com cheats desligados). Avisa no log se falhar. */
	private static boolean command(ServerLevel sl, BlockPos pos, String cmd) {
		boolean[] ok = {false};
		CommandSourceStack source = server.createCommandSourceStack().withLevel(sl).withPosition(Vec3.atCenterOf(pos))
			.withSuppressedOutput().withCallback((success, result) -> ok[0] = success);
		server.getCommands().performPrefixedCommand(source, cmd);
		if (!ok[0]) LOG.warn("Comando do Coletor falhou: {}", cmd);
		return ok[0];
	}

	// ---------- item ----------

	static String dim(Level level) {
		return level.dimension().identifier().toString();
	}

	static boolean isMachineItem(ItemStack stack) {
		if (!stack.is(Items.BARREL)) return false;
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().contains(ITEM_TAG);
	}

	/** O mesmo item da receita (data/coletor/recipe/coletor.json), para devolver ao quebrar. */
	static ItemStack machineItem() {
		ItemStack s = new ItemStack(Items.BARREL);
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(ITEM_TAG, true);
		s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		s.set(DataComponents.CUSTOM_NAME, Component.literal(NAME).withStyle(st -> st.withItalic(false).withColor(ChatFormatting.GREEN)));
		return s;
	}

	// ---------- resource pack ----------

	/** Versão do mod sem o sufixo "+mc..." (a do release no GitHub, ex.: 1.0.0). */
	private static String modVersion() {
		String v = FabricLoader.getInstance().getModContainer("coletor").orElseThrow().getMetadata().getVersion().getFriendlyString();
		int i = v.indexOf('+');
		return i < 0 ? v : v.substring(0, i);
	}

	private static String readPackHash() {
		try (InputStream in = Coletor.class.getResourceAsStream("/coletor/pack.sha1")) {
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

	/**
	 * Título da tela. Com o pack: o fundo inteiro é um caractere da fonte coletor:gui (arte/tela.py) e depois vem o
	 * nome, no x=8 de sempre. Sem o pack, título comum (senão apareceriam quadradinhos de caractere desconhecido).
	 */
	static Component title(boolean pack) {
		if (!pack) return Component.literal(NAME);
		JsonObject bg = new JsonObject();
		bg.addProperty("text", "\uF000\uE000\uF001\uF002");
		bg.addProperty("font", "coletor:gui");
		bg.addProperty("color", "white"); // o título vanilla é cinza escuro e escureceria o desenho
		JsonObject text = new JsonObject();
		text.addProperty("text", NAME);
		text.addProperty("color", "#303030");
		JsonArray parts = new JsonArray();
		parts.add(new JsonPrimitive("")); // raiz sem estilo: o nome não herda a fonte do fundo
		parts.add(bg);
		parts.add(text);
		return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, parts).result().orElse(Component.literal(NAME));
	}

	static void say(Player player, String text, ChatFormatting color) {
		if (player instanceof ServerPlayer sp) sp.sendSystemMessage(Component.literal(text).withStyle(color));
	}
}
