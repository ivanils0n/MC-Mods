package io.github.ivanils0n.smartchest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import io.github.ivanils0n.smartchest.ChestStore.Chest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
 * Baú Inteligente, 100% no servidor: o baú é um bloco de obsidiana de verdade com um item_display por cima (com o
 * resource pack ele vira o baú "Obsidiana do End"); o Vinculador é uma vara de blaze marcada. Nenhum bloco ou item
 * novo é registrado, então o cliente vanilla entra sem mod e o mundo continua abrindo se o mod for removido.
 *
 * Os itens nunca ficam no Baú Inteligente: a tela mostra a soma dos baús e barris ligados e pegar/guardar mexe neles.
 */
public final class SmartChest implements ModInitializer {
	static final Logger LOG = LoggerFactory.getLogger("smartchest");
	static final String NAME = "Baú Inteligente";
	static final int MAX_LINKS = 64; // baús ligados por Baú Inteligente (cada metade de baú duplo conta 1)
	static final double USE_RANGE_SQR = 8 * 8; // a tela fecha se o jogador se afastar mais que isso
	private static final double ACTIVE_RANGE_SQR = 48 * 48; // sem jogador a essa distância o visual não é conferido
	static final String CHEST_TAG = "smartchest_chest", LINKER_TAG = "smartchest_linker";

	// Resource pack: o zip é publicado pelo CI no release "smartchest-pack" com o hash no nome, e o mesmo hash vai
	// dentro do .jar (smartchest/pack.sha1). Assim cada versão do mod aponta para o seu pack.
	private static final String PACK_URL = "https://github.com/ivanils0n/MC-Mods/releases/download/smartchest-pack/smartchest-pack-";
	static final String PACK_SHA1 = readPackHash();
	private static final UUID PACK_ID = UUID.nameUUIDFromBytes("smartchest:pack".getBytes(StandardCharsets.UTF_8));
	private static final Set<UUID> PACK_OK = ConcurrentHashMap.newKeySet();

	/** Ordenação escolhida por jogador (0 quantidade, 1 nome, 2 id/mod); vale até o servidor reiniciar. */
	static final Map<UUID, Integer> SORT = new ConcurrentHashMap<>();
	/** Visual de cada baú já encontrado, para não procurar no mundo a cada segundo. */
	private static final Map<String, Entity> VIS = new HashMap<>();
	/** Tag desta execução: o visual nunca é salvo (mixin/EntityMixin); um "smartchest" sem ela é sobra antiga. */
	private static final String RUN_TAG = "smartchest_run_" + Long.toHexString(new java.util.Random().nextLong());
	private static final List<Entity> LEFTOVERS = new ArrayList<>();
	static MinecraftServer server;
	static ChestStore store;

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(s -> {
			server = s;
			store = ChestStore.load(s.getWorldPath(LevelResource.ROOT).resolve("smartchest.json"));
			Lang.init(s, s.getWorldPath(LevelResource.ROOT));
			LOG.info("{} baús inteligentes carregados", store.size());
			if (Boolean.getBoolean("smartchest.selftest")) SelfTest.run(s);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
			if (store != null) store.saveIfDirty();
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			server = null;
			store = null;
			SORT.clear();
			VIS.clear();
			LEFTOVERS.clear();
			PACK_OK.clear();
			Lang.clear();
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains("smartchest") && !entity.entityTags().contains(RUN_TAG)) LEFTOVERS.add(entity);
		});
		ServerTickEvents.END_SERVER_TICK.register(s -> {
			for (Entity e : LEFTOVERS) if (!e.isRemoved()) e.discard();
			LEFTOVERS.clear();
			if (store != null && s.getTickCount() % 20 == 0) store.saveIfDirty();
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> {
			if (PACK_SHA1 == null || !s.isDedicatedServer()) return; // singleplayer: as texturas vêm do .jar
			sender.sendPacket(new ClientboundResourcePackPushPacket(PACK_ID, PACK_URL + PACK_SHA1 + ".zip", PACK_SHA1, false,
				Optional.of(Component.literal("Texturas do Baú Inteligente"))));
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> PACK_OK.remove(handler.player.getUUID()));
		UseBlockCallback.EVENT.register(SmartChest::onUseBlock);
		PlayerBlockBreakEvents.BEFORE.register(SmartChest::onBreak);
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
			// Baú ligado quebrado por um jogador: sai da lista na hora (outros meios são limpos ao abrir a tela).
			if (store != null && Storage.isLinkable(be)) store.unlinkEverywhere(dim(level), pos);
		});
		ServerTickEvents.END_LEVEL_TICK.register(SmartChest::onWorldTick);
	}

	// ---------- eventos ----------

	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (store == null || !(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp) || sp.isSpectator()) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		Chest chest = store.get(dim(sl), pos);
		ItemStack held = sp.getItemInHand(hand);
		if (hand == InteractionHand.MAIN_HAND && isLinker(held)) {
			if (chest != null) bind(sp, sl, held, chest);
			else if (Storage.isLinkable(sl.getBlockEntity(pos))) say(sp, toggleLink(sp, sl, held, pos));
			else return InteractionResult.PASS;
			return InteractionResult.SUCCESS;
		}
		if (chest != null) {
			if (sp.isSecondaryUseActive() && !held.isEmpty()) return InteractionResult.PASS; // agachado: coloca blocos encostados
			if (hand == InteractionHand.MAIN_HAND) {
				StorageMenu.open(sp, chest, "", 0);
				if (sp.containerMenu instanceof StorageMenu menu && menu.unloaded() > 0) {
					say(sp, count(menu.unloaded(), "baú ligado está", "baús ligados estão") + " fora da distância de visão e não aparece"
						+ (menu.unloaded() == 1 ? "" : "m") + " agora. Volta" + (menu.unloaded() == 1 ? "" : "m")
						+ " quando a área estiver carregada (aumente a distância de visão).", ChatFormatting.YELLOW);
				}
			}
			return InteractionResult.SUCCESS;
		}
		if (isChestItem(held)) {
			place(sp, sl, hit, hand, held);
			return InteractionResult.SUCCESS; // nunca deixa a obsidiana "crua" ser colocada
		}
		return InteractionResult.PASS;
	}

	private static boolean onBreak(Level level, Player player, BlockPos pos, BlockState state, BlockEntity be) {
		if (store == null || !(level instanceof ServerLevel sl)) return true;
		Chest chest = store.get(dim(sl), pos);
		if (chest == null) return true;
		boolean survival = !player.isCreative();
		boolean drop = survival && player.hasCorrectToolForDrops(state);
		removeChest(sl, chest);
		sl.destroyBlock(pos, false, player);
		if (survival) player.getMainHandItem().hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
		if (drop) Block.popResource(sl, pos, chestItem());
		if (drop || !survival) {
			say(player, "Baú Inteligente removido. Os vínculos foram apagados: ao colocá-lo de novo, vincule os baús outra vez.", ChatFormatting.YELLOW);
		} else {
			say(player, "O Baú Inteligente se perdeu: como a obsidiana, ele só volta quebrado com picareta de diamante ou netherite.", ChatFormatting.RED);
		}
		return false; // a quebra vanilla soltaria uma obsidiana comum
	}

	private static void onWorldTick(ServerLevel sl) {
		if (store == null || sl.getGameTime() % 20 != 0) return;
		String dim = dim(sl);
		List<ServerPlayer> players = sl.players();
		for (Chest c : store.inDim(dim)) {
			BlockPos pos = c.pos();
			if (!sl.isLoaded(pos)) continue;
			if (!sl.getBlockState(pos).is(Blocks.OBSIDIAN)) {
				// Sumiu por outro meio (comando, editor de mundo): remove sem dropar nada.
				LOG.info("Baú Inteligente em {} não existe mais; removido", pos);
				removeChest(sl, c);
				continue;
			}
			if (anyPlayerNear(players, pos) && sl.isPositionEntityTicking(pos)) ensureVisual(sl, c);
		}
		// Quem segura um Vinculador ligado vê partículas nos baús ligados (só ele vê).
		for (ServerPlayer p : players) {
			ItemStack held = p.getMainHandItem();
			if (!isLinker(held)) continue;
			Chest c = target(held);
			if (c == null || !c.dim.equals(dim) || p.distanceToSqr(Vec3.atCenterOf(c.pos())) > ACTIVE_RANGE_SQR) continue;
			particles(sl, p, c.pos(), ParticleTypes.PORTAL, 6);
			for (BlockPos l : c.linkPositions()) particles(sl, p, l, ParticleTypes.END_ROD, 2);
		}
	}

	private static boolean anyPlayerNear(List<ServerPlayer> players, BlockPos pos) {
		for (ServerPlayer p : players) if (p.distanceToSqr(Vec3.atCenterOf(pos)) < ACTIVE_RANGE_SQR) return true;
		return false;
	}

	static void particles(ServerLevel sl, ServerPlayer p, BlockPos pos, ParticleOptions type, int count) {
		sl.sendParticles(p, type, true, false, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, count, 0.3, 0.35, 0.3, 0.02);
	}

	// ---------- colocar e remover ----------

	private static void place(ServerPlayer sp, ServerLevel sl, BlockHitResult hit, InteractionHand hand, ItemStack stack) {
		BlockPos clicked = hit.getBlockPos();
		BlockPos pos = sl.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(hit.getDirection());
		if (!sp.mayUseItemAt(pos, hit.getDirection(), stack) || !sl.mayInteract(sp, pos)) return;
		if (!sl.getBlockState(pos).canBeReplaced() || !sl.isUnobstructed(Blocks.OBSIDIAN.defaultBlockState(), pos, CollisionContext.empty())) return;
		if (placeChest(sl, pos, sp.getDirection().getOpposite()) == null) {
			say(sp, "Não foi possível colocar o Baú Inteligente aqui.", ChatFormatting.RED);
			return;
		}
		if (!sp.isCreative()) stack.shrink(1);
		say(sp, "Baú Inteligente colocado! Clique nele com um Vinculador e depois nos baús ou barris que quer ligar.", ChatFormatting.LIGHT_PURPLE);
	}

	/** Coloca a obsidiana e o visual e registra o baú (sem vínculos). Devolve null se o bloco não puder ser colocado. */
	static Chest placeChest(ServerLevel sl, BlockPos pos, Direction facing) {
		if (!sl.setBlock(pos, Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL)) return null;
		Chest c = new Chest(dim(sl), pos, facing);
		store.add(c);
		spawnVisual(sl, c);
		sl.playSound(null, pos, SoundEvents.ENDER_CHEST_OPEN, SoundSource.BLOCKS, 0.6f, 1.2f);
		return c;
	}

	/** Apaga o baú e os vínculos dele. As telas abertas fecham sozinhas (StorageMenu.stillValid). */
	static void removeChest(ServerLevel sl, Chest c) {
		store.remove(c);
		VIS.remove(c.key());
		killVisual(sl, c.pos());
	}

	static boolean exists(Chest c) {
		return store != null && store.get(c.dim, c.pos()) == c;
	}

	// ---------- vínculos ----------

	private static void bind(ServerPlayer sp, ServerLevel sl, ItemStack linker, Chest c) {
		CustomData data = linker.get(DataComponents.CUSTOM_DATA);
		CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
		tag.putBoolean(LINKER_TAG, true);
		tag.putString("dim", c.dim);
		tag.putIntArray("pos", new int[] {c.x, c.y, c.z});
		linker.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		linker.set(DataComponents.LORE, new ItemLore(List.of(
			plain("Ligado ao Baú Inteligente em " + c.x + ", " + c.y + ", " + c.z, ChatFormatting.GRAY),
			plain("Clique em baús ou barris para ligar ou desligar.", ChatFormatting.DARK_GRAY))));
		sl.playSound(null, c.pos(), SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 1f, 1f);
		particles(sl, sp, c.pos(), ParticleTypes.PORTAL, 30);
		for (BlockPos l : c.linkPositions()) particles(sl, sp, l, ParticleTypes.END_ROD, 8);
		say(sp, "Vinculador pronto (" + count(c.links.size(), "baú ligado", "baús ligados") + "). Agora clique nos baús ou barris, até "
			+ count(viewDistance(sp), "chunk", "chunks") + " daqui (sua distância de visão).", ChatFormatting.AQUA);
	}

	/** Baú Inteligente que o Vinculador está mapeando, ou null. */
	static Chest target(ItemStack linker) {
		CustomData data = linker.get(DataComponents.CUSTOM_DATA);
		if (data == null || store == null) return null;
		CompoundTag tag = data.copyTag();
		Optional<String> dim = tag.getString("dim");
		Optional<int[]> pos = tag.getIntArray("pos");
		if (dim.isEmpty() || pos.isEmpty() || pos.get().length != 3) return null;
		int[] p = pos.get();
		return store.get(dim.get(), new BlockPos(p[0], p[1], p[2]));
	}

	/** Liga ou desliga o baú clicado (e a outra metade, se for duplo). Devolve a mensagem para o jogador. */
	static Component toggleLink(ServerPlayer sp, ServerLevel sl, ItemStack linker, BlockPos pos) {
		if (!sl.mayInteract(sp, pos)) return plain("Você não pode mexer nesse baú.", ChatFormatting.RED);
		CustomData data = linker.get(DataComponents.CUSTOM_DATA);
		boolean bound = data != null && data.copyTag().contains("pos");
		Chest c = target(linker);
		if (c == null) {
			return plain(bound ? "O Baú Inteligente deste Vinculador não existe mais. Clique num Baú Inteligente para mapear outro."
				: "Primeiro clique num Baú Inteligente com o Vinculador.", ChatFormatting.RED);
		}
		String err = linkProblem(c, sl, pos);
		if (err != null) return plain(err, ChatFormatting.RED);
		List<BlockPos> group = Storage.group(sl, pos);
		if (c.isLinked(pos)) {
			for (BlockPos p : group) c.unlink(p);
			store.markDirty();
			for (BlockPos p : group) particles(sl, sp, p, ParticleTypes.WAX_ON, 10);
			sl.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1f, 0.6f);
			return plain("Baú desligado (" + count(c.links.size(), "ligado", "ligados") + ").", ChatFormatting.YELLOW);
		}
		for (BlockPos p : group) {
			String far = rangeProblem(c, p, viewDistance(sp)); // desligar sempre pode; ligar só dentro da distância de visão
			if (far != null) return plain(far, ChatFormatting.RED);
		}
		int adding = 0;
		for (BlockPos p : group) if (!c.isLinked(p)) adding++;
		if (c.links.size() + adding > MAX_LINKS) {
			return plain("Limite de " + MAX_LINKS + " baús por Baú Inteligente atingido.", ChatFormatting.RED);
		}
		for (BlockPos p : group) c.link(p);
		store.markDirty();
		for (BlockPos p : group) particles(sl, sp, p, ParticleTypes.HAPPY_VILLAGER, 12);
		sl.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1f, 1.4f);
		return plain((group.size() > 1 ? "Baú duplo ligado" : "Baú ligado") + " (" + count(c.links.size(), "ligado", "ligados") + ").", ChatFormatting.GREEN);
	}

	/** Motivo para não ligar este bloco ao baú, ou null se pode. */
	static String linkProblem(Chest c, ServerLevel sl, BlockPos pos) {
		if (!c.dim.equals(dim(sl))) return "Só dá para ligar baús que estão na mesma dimensão do Baú Inteligente.";
		if (!Storage.isLinkable(sl.getBlockEntity(pos))) return "Só baús e barris podem ser ligados.";
		return null;
	}

	/**
	 * Sem limite de blocos: o baú só precisa estar na área que a distância de visão carrega em volta do Baú
	 * Inteligente (o mod nunca carrega chunks). Devolve o aviso, ou null se está dentro.
	 */
	static String rangeProblem(Chest c, BlockPos pos, int viewDistance) {
		int chunks = Math.max(Math.abs((pos.getX() >> 4) - (c.x >> 4)), Math.abs((pos.getZ() >> 4) - (c.z >> 4)));
		if (chunks <= viewDistance) return null;
		return "Baú muito longe: ele fica a " + chunks + " chunks do Baú Inteligente, e a distância de visão alcança só "
			+ viewDistance + ". Aumente a distância de visão ou ligue um baú mais perto.";
	}

	/** Distância de visão (em chunks) que vale para o jogador: a menor entre a do servidor e a dele. */
	static int viewDistance(ServerPlayer sp) {
		return Math.max(2, Math.min(server.getPlayerList().getViewDistance(), sp.clientInformation().viewDistance()));
	}

	// ---------- visual ----------

	private static void ensureVisual(ServerLevel sl, Chest c) {
		Entity e = VIS.get(c.key());
		if (e != null && !e.isRemoved()) return;
		List<Entity> found = sl.getEntitiesOfClass(Entity.class, new AABB(c.pos()).inflate(0, 1, 0), x -> x.entityTags().contains(tag(c.pos())));
		if (!found.isEmpty()) {
			VIS.put(c.key(), found.get(0));
			return;
		}
		spawnVisual(sl, c); // o visual não é salvo no mapa: volta quando o lugar é carregado com jogadores por perto
	}

	/**
	 * Cubo do baú um pouco maior que a obsidiana, virado para quem o colocou. Fica um bloco acima (e desce meio bloco
	 * no modelo) porque a luz da entidade é a do bloco onde ela está: dentro da obsidiana ficaria preto.
	 */
	static void spawnVisual(ServerLevel sl, Chest c) {
		killVisual(sl, c.pos());
		BlockPos pos = c.pos();
		double half = Math.toRadians(switch (c.facing()) {
			case SOUTH -> 180;
			case EAST -> -90;
			case WEST -> 90;
			default -> 0;
		}) / 2;
		String nbt = "Tags:[\"smartchest\",\"" + tag(pos) + "\",\"" + RUN_TAG + "\"],item:{id:\"minecraft:obsidian\",count:1,components:"
			+ "{\"minecraft:custom_model_data\":{strings:[\"" + CHEST_TAG + "\"]}}},"
			+ String.format(Locale.ROOT, "transformation:{left_rotation:[0f,%.5ff,0f,%.5ff],right_rotation:[0f,0f,0f,1f],"
				+ "translation:[0f,-0.5f,0f],scale:[1.002f,1.002f,1.002f]}", Math.sin(half), Math.cos(half));
		command(sl, pos, String.format(Locale.ROOT, "summon minecraft:item_display %.4f %.4f %.4f {%s}",
			pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, nbt));
	}

	private static void killVisual(ServerLevel sl, BlockPos pos) {
		String tag = tag(pos);
		for (Entity e : sl.getEntitiesOfClass(Entity.class, new AABB(pos).inflate(0, 1, 0), e -> e.entityTags().contains(tag))) e.discard();
	}

	static List<Entity> visuals(ServerLevel sl, BlockPos pos) {
		String tag = tag(pos);
		return sl.getEntitiesOfClass(Entity.class, new AABB(pos).inflate(0, 1, 0), e -> e.entityTags().contains(tag));
	}

	private static String tag(BlockPos pos) {
		return "smartchest_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
	}

	/** Roda um comando como o próprio servidor (funciona com cheats desligados). Avisa no log se falhar. */
	private static boolean command(ServerLevel sl, BlockPos pos, String cmd) {
		boolean[] ok = {false};
		CommandSourceStack source = server.createCommandSourceStack().withLevel(sl).withPosition(Vec3.atCenterOf(pos))
			.withSuppressedOutput().withCallback((success, result) -> ok[0] = success);
		server.getCommands().performPrefixedCommand(source, cmd);
		if (!ok[0]) LOG.warn("Comando do Baú Inteligente falhou: {}", cmd);
		return ok[0];
	}

	// ---------- itens ----------

	static String dim(Level level) {
		return level.dimension().identifier().toString();
	}

	private static boolean hasTag(ItemStack stack, String tag) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().contains(tag);
	}

	static boolean isChestItem(ItemStack stack) {
		return stack.is(Items.OBSIDIAN) && hasTag(stack, CHEST_TAG);
	}

	static boolean isLinker(ItemStack stack) {
		return stack.is(Items.BLAZE_ROD) && hasTag(stack, LINKER_TAG);
	}

	/** O mesmo item da receita (data/smartchest/recipe/chest.json), para devolver ao quebrar. */
	static ItemStack chestItem() {
		ItemStack s = new ItemStack(Items.OBSIDIAN);
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(CHEST_TAG, true);
		s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		s.set(DataComponents.CUSTOM_NAME, plain(NAME, ChatFormatting.LIGHT_PURPLE));
		s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		s.set(DataComponents.CUSTOM_MODEL_DATA, model(CHEST_TAG));
		return s;
	}

	/** O mesmo item da receita (data/smartchest/recipe/linker.json). */
	static ItemStack linkerItem() {
		ItemStack s = new ItemStack(Items.BLAZE_ROD);
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(LINKER_TAG, true);
		s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		s.set(DataComponents.CUSTOM_NAME, plain("Vinculador", ChatFormatting.AQUA));
		s.set(DataComponents.MAX_STACK_SIZE, 1);
		s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		s.set(DataComponents.CUSTOM_MODEL_DATA, model(LINKER_TAG));
		return s;
	}

	static CustomModelData model(String name) {
		return new CustomModelData(List.of(), List.of(), List.of(name), List.of());
	}

	// ---------- resource pack e título da tela ----------

	private static String readPackHash() {
		try (InputStream in = SmartChest.class.getResourceAsStream("/smartchest/pack.sha1")) {
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
	 * Título da tela. Com o pack: o fundo inteiro é um caractere da fonte smartchest:gui (arte/gerar.py) e o texto
	 * do título é a pesquisa, levado para dentro da caixa de busca (o nome "Baú Inteligente" já está desenhado no
	 * fundo). Sem o pack, título comum (senão apareceriam quadradinhos de caractere desconhecido).
	 */
	static Component title(ServerPlayer p, String query) {
		if (!hasPack(p)) return Component.literal(query.isEmpty() ? NAME : NAME + ": " + query);
		return themedTitle(query);
	}

	static Component themedTitle(String query) {
		JsonObject bg = new JsonObject();
		bg.addProperty("text", "\uF000\uE000\uF001\uF002");
		bg.addProperty("font", "smartchest:gui");
		bg.addProperty("color", "white"); // o título vanilla é cinza escuro e escureceria o desenho
		JsonObject text = new JsonObject();
		// A caixa tem ~60 px (uns 9 caracteres): mostra o fim do que foi digitado.
		text.addProperty("text", query.isEmpty() ? "Pesquisar..." : query.length() > 9 ? ".." + query.substring(query.length() - 7) : query);
		text.addProperty("color", query.isEmpty() ? "#9D8CC9" : "#E3D8FF");
		JsonArray parts = new JsonArray();
		parts.add(new JsonPrimitive("")); // raiz sem estilo: o texto não herda a fonte do fundo
		parts.add(bg);
		parts.add(text);
		return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, parts).result().orElse(Component.literal(NAME));
	}

	// ---------- utilidades ----------

	static String count(int n, String one, String many) {
		return n + " " + (n == 1 ? one : many);
	}

	static MutableComponent plain(String text, ChatFormatting color) {
		return Component.literal(text).withStyle(st -> st.withItalic(false).withColor(color));
	}

	static void say(Player player, String text, ChatFormatting color) {
		say(player, Component.literal(text).withStyle(color));
	}

	static void say(Player player, Component text) {
		if (player instanceof ServerPlayer sp) sp.sendSystemMessage(text);
	}
}
