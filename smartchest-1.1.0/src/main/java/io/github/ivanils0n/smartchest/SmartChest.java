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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
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
import net.minecraft.resources.Identifier;
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
 * Baú Central, 100% no servidor: o baú é um bloco de obsidiana de verdade com um item_display por cima (com o
 * resource pack ele vira o baú "Obsidiana do End"); o Vinculador é uma vara de blaze marcada. Nenhum bloco ou item
 * novo é registrado, então o cliente vanilla entra sem mod e o mundo continua abrindo se o mod for removido.
 *
 * Os itens nunca ficam no Baú Central: a tela mostra a soma dos baús e barris ligados e pegar/guardar mexe neles.
 */
public final class SmartChest implements ModInitializer {
	static final Logger LOG = LoggerFactory.getLogger("smartchest");
	static final String NAME = "Baú Central";
	static final int MAX_LINKS = 512; // baús ligados por Baú Central (cada metade de baú duplo conta 1)
	static final double USE_RANGE_SQR = 8 * 8; // a tela fecha se o jogador se afastar mais que isso
	private static final double ACTIVE_RANGE_SQR = 48 * 48; // sem jogador a essa distância o visual não é conferido
	static final String CHEST_TAG = "smartchest_chest", LINKER_TAG = "smartchest_linker";

	// Resource pack: o zip é publicado pelo CI no release do mod ("smartchest-<versão>") com o hash no nome, e o mesmo hash vai
	// dentro do .jar (smartchest/pack.sha1). Assim cada versão do mod aponta para o seu pack.
	private static final String PACK_URL = "https://github.com/ivanils0n/MC-Mods/releases/download/smartchest-" + modVersion() + "/smartchest-pack-";
	static final String PACK_SHA1 = readPackHash();
	private static final UUID PACK_ID = UUID.nameUUIDFromBytes("smartchest:pack".getBytes(StandardCharsets.UTF_8));
	private static final Set<UUID> PACK_OK = ConcurrentHashMap.newKeySet();

	/** Ordenação escolhida por jogador (0 quantidade, 1 nome, 2 id/mod); vale até o servidor reiniciar. */
	static final Map<UUID, Integer> SORT = new ConcurrentHashMap<>();
	/** Aba e filtro pelo item de cada jogador; zeram ao abrir o baú de novo (valem na volta da pesquisa). */
	static final Map<UUID, Integer> CATEGORY = new ConcurrentHashMap<>();
	static final Map<UUID, ItemStack> FILTER = new ConcurrentHashMap<>();
	/** Visual de cada baú já encontrado, para não procurar no mundo a cada segundo. */
	private static final Map<String, Entity> VIS = new HashMap<>();
	/** Tag desta execução: o visual nunca é salvo (mixin/EntityMixin); um "smartchest" sem ela é sobra antiga. */
	private static final String RUN_TAG = "smartchest_run_" + Long.toHexString(new java.util.Random().nextLong());
	private static final List<Entity> LEFTOVERS = new ArrayList<>();
	/** Baús com a tampa aberta agora (alguém está com a tela ou a pesquisa deles aberta). */
	private static Set<Chest> open = new HashSet<>();
	static MinecraftServer server;
	static ChestStore store;

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(s -> {
			server = s;
			store = ChestStore.load(s.getWorldPath(LevelResource.ROOT).resolve("smartchest.json"));
			Lang.init(s, s.getWorldPath(LevelResource.ROOT));
			Categories.build(s);
			LOG.info("{} baús centrais carregados", store.size());
			if (Boolean.getBoolean("smartchest.selftest")) SelfTest.run(s);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
			if (store != null) store.saveIfDirty();
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			server = null;
			store = null;
			SORT.clear();
			CATEGORY.clear();
			FILTER.clear();
			Categories.clear();
			VIS.clear();
			open.clear();
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
			if (store != null) updateLids(s);
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> {
			if (PACK_SHA1 == null || !s.isDedicatedServer()) return; // singleplayer: as texturas vêm do .jar
			sender.sendPacket(new ClientboundResourcePackPushPacket(PACK_ID, PACK_URL + PACK_SHA1 + ".zip", PACK_SHA1, false,
				Optional.of(Component.literal("Texturas do Baú Central"))));
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> {
			UUID id = handler.player.getUUID();
			PACK_OK.remove(id);
			CATEGORY.remove(id);
			FILTER.remove(id);
		});
		UseBlockCallback.EVENT.register(SmartChest::onUseBlock);
		// Botão esquerdo com o Vinculador num baú: escolher o tipo (categoria, qualquer item ou só retirar) em vez de quebrar.
		AttackBlockCallback.EVENT.register((player, level, hand, pos, dir) -> {
			if (store == null || !(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp) || sp.isSpectator()
				|| hand != InteractionHand.MAIN_HAND || !isLinker(sp.getMainHandItem()) || !Storage.isLinkable(sl.getBlockEntity(pos))) {
				return InteractionResult.PASS;
			}
			Component err = linkProblem(sp, sl, sp.getMainHandItem(), pos);
			if (err != null) say(sp, err);
			else LinkMenu.open(sp, target(sp.getMainHandItem()), pos);
			return InteractionResult.SUCCESS;
		});
		// O cliente não sabe que a obsidiana é um baú: junto com o clique ele também manda "usar o item da mão" (comer,
		// levantar o escudo, jogar a pérola). Com a tela do baú aberta isso é recusado e o inventário é reenviado.
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!(player instanceof ServerPlayer sp) || !(sp.containerMenu instanceof StorageMenu)) return InteractionResult.PASS;
			sp.containerMenu.sendAllDataToRemote();
			return InteractionResult.FAIL;
		});
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
			if (sp.isSecondaryUseActive() && !held.isEmpty()) { // agachado: coloca blocos encostados
				if (!isChestItem(held)) return InteractionResult.PASS;
				place(sp, sl, hit, hand, held); // outro Baú Central: pelo caminho vanilla viraria obsidiana comum
				return InteractionResult.SUCCESS;
			}
			if (hand == InteractionHand.MAIN_HAND) {
				CATEGORY.remove(sp.getUUID());
				FILTER.remove(sp.getUUID());
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
			say(player, "Baú Central removido. Os vínculos foram apagados: ao colocá-lo de novo, vincule os baús outra vez.", ChatFormatting.YELLOW);
		} else {
			say(player, "O Baú Central se perdeu: como a obsidiana, ele só volta quebrado com picareta de diamante ou netherite.", ChatFormatting.RED);
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
				LOG.info("Baú Central em {} não existe mais; removido", pos);
				removeChest(sl, c);
				continue;
			}
			if (anyPlayerNear(players, pos) && sl.isPositionEntityTicking(pos)) ensureVisual(sl, c);
		}
		// Quem segura um Vinculador ligado vê partículas nos baús ligados (só ele vê).
		for (ServerPlayer p : players) {
			ItemStack held = p.getMainHandItem();
			upgrade(held);
			if (!isLinker(held)) continue;
			Chest c = target(held);
			if (c == null || !c.dim.equals(dim) || p.distanceToSqr(Vec3.atCenterOf(c.pos())) > ACTIVE_RANGE_SQR) continue;
			particles(sl, p, c.pos(), ParticleTypes.PORTAL, 6);
			for (BlockPos l : c.linkPositions()) {
				if (p.distanceToSqr(Vec3.atCenterOf(l)) < ACTIVE_RANGE_SQR) particles(sl, p, l, ParticleTypes.END_ROD, 2);
			}
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
			say(sp, "Não foi possível colocar o Baú Central aqui.", ChatFormatting.RED);
			return;
		}
		if (!sp.isCreative()) stack.shrink(1);
		say(sp, "Baú Central colocado! Clique nele com um Vinculador e depois nos baús ou barris que quer ligar.", ChatFormatting.LIGHT_PURPLE);
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
		upgrade(linker);
		linker.set(DataComponents.LORE, new ItemLore(List.of(
			plain("Ligado ao Baú Central em " + c.x + ", " + c.y + ", " + c.z, ChatFormatting.GRAY),
			plain("Clique em baús ou barris para ligar ou desligar.", ChatFormatting.DARK_GRAY))));
		sl.playSound(null, c.pos(), SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 1f, 1f);
		particles(sl, sp, c.pos(), ParticleTypes.PORTAL, 30);
		for (BlockPos l : c.linkPositions()) particles(sl, sp, l, ParticleTypes.END_ROD, 8);
		say(sp, "Vinculador pronto (" + count(c.links.size(), "baú ligado", "baús ligados") + "). Agora clique nos baús ou barris, até "
			+ count(viewDistance(sp), "chunk", "chunks") + " daqui (sua distância de visão).", ChatFormatting.AQUA);
	}

	/** Baú Central que o Vinculador está mapeando, ou null. */
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

	/** O que impede o Vinculador de mexer nesse baú (mensagem), ou null. */
	static Component linkProblem(ServerPlayer sp, ServerLevel sl, ItemStack linker, BlockPos pos) {
		if (!sl.mayInteract(sp, pos)) return plain("Você não pode mexer nesse baú.", ChatFormatting.RED);
		CustomData data = linker.get(DataComponents.CUSTOM_DATA);
		boolean bound = data != null && data.copyTag().contains("pos");
		Chest c = target(linker);
		if (c == null) {
			return plain(bound ? "O Baú Central deste Vinculador não existe mais. Clique num Baú Central para mapear outro."
				: "Primeiro clique num Baú Central com o Vinculador.", ChatFormatting.RED);
		}
		String err = linkProblem(c, sl, pos);
		return err == null ? null : plain(err, ChatFormatting.RED);
	}

	/** Botão direito: liga (para qualquer item) ou desliga o baú clicado (e a outra metade, se for duplo). */
	static Component toggleLink(ServerPlayer sp, ServerLevel sl, ItemStack linker, BlockPos pos) {
		Component err = linkProblem(sp, sl, linker, pos);
		if (err != null) return err;
		Chest c = target(linker);
		if (!c.isLinked(pos)) return linkWith(sp, sl, linker, pos, ChestStore.ANY);
		for (BlockPos p : Storage.group(sl, pos)) c.unlink(p);
		store.markDirty();
		for (BlockPos p : Storage.group(sl, pos)) particles(sl, sp, p, ParticleTypes.WAX_ON, 10);
		sl.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1f, 0.6f);
		return plain("Baú desligado (" + count(c.links.size(), "ligado", "ligados") + ").", ChatFormatting.YELLOW);
	}

	/** Liga o baú com o modo (ChestStore.ANY, categoria 1-7 ou ChestStore.INPUT), ou só troca o modo se já está ligado. */
	static Component linkWith(ServerPlayer sp, ServerLevel sl, ItemStack linker, BlockPos pos, int mode) {
		Component err = linkProblem(sp, sl, linker, pos);
		if (err != null) return err;
		Chest c = target(linker);
		List<BlockPos> group = Storage.group(sl, pos);
		int adding = 0;
		for (BlockPos p : group) {
			if (c.isLinked(p)) continue;
			adding++;
			String far = rangeProblem(c, p, viewDistance(sp)); // desligar sempre pode; ligar só dentro da distância de visão
			if (far != null) return plain(far, ChatFormatting.RED);
		}
		if (c.links.size() + adding > MAX_LINKS) {
			return plain("Limite de " + MAX_LINKS + " baús por Baú Central atingido.", ChatFormatting.RED);
		}
		for (BlockPos p : group) c.link(p, mode);
		store.markDirty();
		for (BlockPos p : group) particles(sl, sp, p, ParticleTypes.HAPPY_VILLAGER, 12);
		sl.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1f, 1.4f);
		String kind = mode == ChestStore.ANY ? "qualquer item" : mode == ChestStore.INPUT ? "só retirar" : "só " + Categories.NAMES[mode];
		return plain((group.size() > 1 ? "Baú duplo" : "Baú") + (adding > 0 ? " ligado" : " agora") + ": " + kind
			+ " (" + count(c.links.size(), "ligado", "ligados") + ").", ChatFormatting.GREEN);
	}

	/** Motivo para não ligar este bloco ao baú, ou null se pode. */
	static String linkProblem(Chest c, ServerLevel sl, BlockPos pos) {
		if (!c.dim.equals(dim(sl))) return "Só dá para ligar baús que estão na mesma dimensão do Baú Central.";
		if (!Storage.isLinkable(sl.getBlockEntity(pos))) return "Só baús e barris podem ser ligados.";
		return null;
	}

	/**
	 * Sem limite de blocos: o baú só precisa estar na área que a distância de visão carrega em volta do Baú
	 * Central (o mod nunca carrega chunks). Devolve o aviso, ou null se está dentro.
	 */
	static String rangeProblem(Chest c, BlockPos pos, int viewDistance) {
		int chunks = Math.max(Math.abs((pos.getX() >> 4) - (c.x >> 4)), Math.abs((pos.getZ() >> 4) - (c.z >> 4)));
		if (chunks <= viewDistance) return null;
		return "Baú muito longe: ele fica a " + chunks + " chunks do Baú Central, e a distância de visão alcança só "
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
		List<Entity> found = visuals(sl, c.pos());
		if (found.size() == 2) { // corpo e tampa
			VIS.put(c.key(), found.get(0));
			return;
		}
		spawnVisual(sl, c); // o visual não é salvo no mapa: volta quando o lugar é carregado com jogadores por perto
	}

	/**
	 * Duas peças um pouco maiores que a obsidiana, viradas para quem colocou o baú: o corpo e a tampa (que gira ao
	 * abrir). Ficam um bloco acima (e descem no modelo) porque a luz da entidade é a do bloco onde ela está: dentro
	 * da obsidiana ficariam pretas.
	 */
	static void spawnVisual(ServerLevel sl, Chest c) {
		killVisual(sl, c.pos());
		open.remove(c); // nasce fechado; se alguém está com a tela aberta, updateLids abre de novo
		BlockPos pos = c.pos();
		for (String part : List.of("chest_base", "chest_lid")) {
			String nbt = "Tags:[\"smartchest\",\"" + tag(pos) + "\",\"smartchest_" + part + "\",\"" + RUN_TAG + "\"],item:{id:\"minecraft:obsidian\","
				+ "count:1,components:{\"minecraft:item_model\":\"smartchest:" + part + "\"}},"
				+ (part.equals("chest_lid") ? lidTransform(c.facing(), 0) : baseTransform(c.facing()));
			command(sl, pos, String.format(Locale.ROOT, "summon minecraft:item_display %.4f %.4f %.4f {%s}",
				pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, nbt));
		}
	}

	/** Giro (quaternion x, y, z, w) que vira a frente do modelo para o lado do baú. */
	private static double[] yaw(Direction facing) {
		double half = Math.toRadians(switch (facing) {
			case SOUTH -> 180;
			case EAST -> -90;
			case WEST -> 90;
			default -> 0;
		}) / 2;
		return new double[] {0, Math.sin(half), 0, Math.cos(half)};
	}

	private static String baseTransform(Direction facing) {
		double[] q = yaw(facing);
		// O corpo tem 11/16 de altura e o modelo é centrado em 8/16: desce 0.5 como o cubo inteiro descia.
		return String.format(Locale.ROOT, "transformation:{left_rotation:[0f,%.5ff,0f,%.5ff],right_rotation:[0f,0f,0f,1f],"
			+ "translation:[0f,-0.5f,0f],scale:[1.002f,1.002f,1.002f]}", q[1], q[3]);
	}

	/**
	 * Tampa: o centro do modelo é a dobradiça, que fica na aresta de trás a 11/16 da base do bloco. Aberta, a frente
	 * da tampa sobe girando em volta da dobradiça (eixo = frente x cima).
	 */
	static String lidTransform(Direction facing, double degrees) {
		double fx = facing.getStepX(), fz = facing.getStepZ(), h = Math.toRadians(degrees) / 2;
		double[] a = {-fz * Math.sin(h), 0, fx * Math.sin(h), Math.cos(h)}, b = yaw(facing);
		double[] q = { // a x b: primeiro vira para o lado do baú, depois abre
			a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
			a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
			a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
			a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]};
		return String.format(Locale.ROOT, "transformation:{left_rotation:[%.5ff,%.5ff,%.5ff,%.5ff],right_rotation:[0f,0f,0f,1f],"
			+ "translation:[%.4ff,-0.3125f,%.4ff],scale:[1.002f,1.002f,1.002f]}", q[0], q[1], q[2], q[3], -fx * 0.5, -fz * 0.5);
	}

	/** Abre a tampa dos baús que alguém está usando e fecha a dos outros (com som), uma vez por mudança. */
	private static void updateLids(MinecraftServer s) {
		Set<Chest> now = new HashSet<>();
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			Chest c = p.containerMenu instanceof StorageMenu m ? m.chest : p.containerMenu instanceof SearchMenu q ? q.chest : null;
			if (c != null && exists(c)) now.add(c);
		}
		if (now.equals(open)) return;
		for (Chest c : now) if (!open.contains(c)) lid(s, c, true);
		for (Chest c : open) if (!now.contains(c) && exists(c)) lid(s, c, false);
		open = now;
	}

	private static void lid(MinecraftServer s, Chest c, boolean opening) {
		for (ServerLevel sl : s.getAllLevels()) {
			if (!dim(sl).equals(c.dim) || !sl.isLoaded(c.pos())) continue;
			for (Entity e : visuals(sl, c.pos())) {
				if (!e.entityTags().contains("smartchest_chest_lid")) continue;
				command(sl, c.pos(), "data merge entity " + e.getStringUUID() + " {start_interpolation:0,interpolation_duration:6,"
					+ lidTransform(c.facing(), opening ? 75 : 0) + "}");
			}
			sl.playSound(null, c.pos(), opening ? SoundEvents.ENDER_CHEST_OPEN : SoundEvents.ENDER_CHEST_CLOSE, SoundSource.BLOCKS, 0.5f, 1.1f);
		}
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
		if (!ok[0]) LOG.warn("Comando do Baú Central falhou: {}", cmd);
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
		s.set(DataComponents.ITEM_MODEL, model("chest"));
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
		s.set(DataComponents.ITEM_MODEL, model("linker"));
		return s;
	}

	/**
	 * Modelo próprio (assets/smartchest/items/<nome>.json). Nenhum outro resource pack mexe nesse caminho, então a
	 * textura do mod sempre vale para os itens do mod, e os itens vanilla continuam com o pack que o jogador usa.
	 */
	static Identifier model(String name) {
		return Identifier.fromNamespaceAndPath("smartchest", name);
	}

	/** Itens antigos: ganham o modelo próprio (os da 1.0.0 não tinham) e o baú ganha o nome novo. */
	static void upgrade(ItemStack stack) {
		if (isChestItem(stack)) {
			Component name = stack.get(DataComponents.CUSTOM_NAME);
			if (name != null && name.getString().equals("Baú Inteligente")) stack.set(DataComponents.CUSTOM_NAME, plain(NAME, ChatFormatting.LIGHT_PURPLE));
		}
		if (stack.has(DataComponents.ITEM_MODEL) && stack.get(DataComponents.ITEM_MODEL).getNamespace().equals("smartchest")) return;
		if (isChestItem(stack)) stack.set(DataComponents.ITEM_MODEL, model("chest"));
		else if (isLinker(stack)) stack.set(DataComponents.ITEM_MODEL, model("linker"));
		else return;
		stack.remove(DataComponents.CUSTOM_MODEL_DATA);
	}

	// ---------- resource pack e título da tela ----------

	/** Versão do mod sem o sufixo "+mc..." (a do release no GitHub, ex.: 1.0.0). */
	private static String modVersion() {
		String v = FabricLoader.getInstance().getModContainer("smartchest").orElseThrow().getMetadata().getVersion().getFriendlyString();
		int i = v.indexOf('+');
		return i < 0 ? v : v.substring(0, i);
	}

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
	 * Título da tela. Com o pack: o fundo inteiro é um caractere da fonte smartchest:gui (arte/gerar.py), com o nome
	 * "Baú Central" já desenhado; o texto do título só aparece com uma pesquisa ativa ("filtro: ..."). Sem o
	 * pack, título comum (senão apareceriam quadradinhos de caractere desconhecido).
	 */
	static Component title(ServerPlayer p, String query) {
		if (!hasPack(p)) return Component.literal(query.isEmpty() ? NAME : NAME + ": " + query);
		// Espaço para uns 11 caracteres depois de x=100: mostra o fim do que foi digitado.
		String shown = query.length() > 6 ? ".." + query.substring(query.length() - 5) : query;
		return themedTitle("\uF000\uE000\uF001\uF002", query.isEmpty() ? "" : "filtro: " + shown);
	}

	/** Fundo da fonte smartchest:gui + texto. {@code background} é a sequência de caracteres do fundo. */
	static Component themedTitle(String background, String label) {
		JsonObject bg = new JsonObject();
		bg.addProperty("text", background);
		bg.addProperty("font", "smartchest:gui");
		bg.addProperty("color", "white"); // o título vanilla é cinza escuro e escureceria o desenho
		JsonObject text = new JsonObject();
		text.addProperty("text", label);
		text.addProperty("color", "#E3D8FF");
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
