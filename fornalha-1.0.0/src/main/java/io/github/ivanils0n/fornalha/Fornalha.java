package io.github.ivanils0n.fornalha;

import io.github.ivanils0n.fornalha.FornalhaStore.Forno;
import io.github.ivanils0n.fornalha.FornalhaStore.Status;
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
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
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
 * Fornalha Industrial, 100% no servidor. Nada novo é registrado: a Casca Industrial é uma pedra lisa de verdade e o
 * Controlador é um barril de verdade (os itens ficam nele), os dois marcados em <mundo>/fornalha.json e cobertos por
 * um item_display com a textura de aço (resource pack). Sem o mod sobram pedras lisas e um barril com os itens.
 *
 * Estrutura: cubo 3x3x3 com o Controlador no meio de uma face (a frente), um bloco de ferro, ouro, diamante ou
 * netherite no centro (o núcleo, que define o nível e a cor da textura) e Cascas Industriais em todo o resto.
 */
public final class Fornalha implements ModInitializer {
	static final Logger LOG = LoggerFactory.getLogger("fornalha");
	static final String NOME = "Fornalha Industrial";
	static final String CASCA = "Casca Industrial", CONTROLADOR = "Controlador Industrial";
	static final String TAG_CASCA = "fornalha_casca", TAG_CTRL = "fornalha_controlador";
	private static final double VISUAL_RANGE_SQR = 64 * 64;
	static final String[] NIVEIS = {"sem estrutura", "Ferro", "Ouro", "Diamante", "Netherite"};

	private static final String PACK_URL = "https://github.com/ivanils0n/MC-Mods/releases/download/fornalha-" + modVersion() + "/fornalha-pack-";
	static final String PACK_SHA1 = readPackHash();
	private static final UUID PACK_ID = UUID.nameUUIDFromBytes("fornalha:pack".getBytes(StandardCharsets.UTF_8));
	/** Tag desta execução: o visual nunca é salvo (mixin/EntityMixin); um "fornalha" sem ela é sobra antiga. */
	private static final String RUN_TAG = "fornalha_run_" + Long.toHexString(new java.util.Random().nextLong());
	private static final List<Entity> LEFTOVERS = new ArrayList<>();

	private static final Set<UUID> PACK_OK = ConcurrentHashMap.newKeySet();
	/** Visual de cada bloco (chave do bloco -> entidade, modelo e luz atuais). */
	private static final Map<String, Visual> VISUAIS = new HashMap<>();
	private static int selftestIn;
	static MinecraftServer server;
	static FornalhaStore store;

	private static final class Visual {
		Entity entity;
		String modelo;
		int light = -1;
	}

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(s -> {
			server = s;
			store = FornalhaStore.load(s.getWorldPath(LevelResource.ROOT).resolve("fornalha.json"));
			LOG.info("{} fornalhas e {} cascas carregadas", store.fornos(), store.cascas());
			if (Boolean.getBoolean("fornalha.selftest")) {
				s.overworld().setChunkForced(0, 0, true);
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
			VISUAIS.clear();
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains("fornalha") && !entity.entityTags().contains(RUN_TAG)) LEFTOVERS.add(entity);
		});
		ServerTickEvents.END_SERVER_TICK.register(s -> {
			if (selftestIn > 0 && --selftestIn == 0) SelfTest.run(s);
			for (Entity e : LEFTOVERS) if (!e.isRemoved()) e.discard();
			LEFTOVERS.clear();
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> {
			if (PACK_SHA1 == null || !s.isDedicatedServer()) return; // singleplayer: as texturas vêm do .jar
			sender.sendPacket(new ClientboundResourcePackPushPacket(PACK_ID, PACK_URL + PACK_SHA1 + ".zip", PACK_SHA1, false,
				Optional.of(Component.literal("Texturas da Fornalha Industrial"))));
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> PACK_OK.remove(handler.player.getUUID()));
		UseBlockCallback.EVENT.register(Fornalha::onUseBlock);
		PlayerBlockBreakEvents.BEFORE.register(Fornalha::onBreak);
		ServerTickEvents.END_LEVEL_TICK.register(Fornalha::onWorldTick);
	}

	// ---------- eventos ----------

	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (store == null || !(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp) || sp.isSpectator()) {
			return InteractionResult.PASS;
		}
		ItemStack held = sp.getItemInHand(hand);
		BlockPos clicked = hit.getBlockPos();
		String dim = dim(sl);
		boolean usarItem = sp.isSecondaryUseActive() && !held.isEmpty(); // agachado com item: coloca bloco, como num baú
		Forno f = store.forno(dim, clicked);
		if (f == null && !usarItem) {
			Forno dono = store.donoDe(dim, clicked); // clicar em qualquer casca de uma fornalha montada abre a tela
			if (dono != null && dono.formado) f = dono;
		}
		if (f != null && !usarItem) {
			if (hand == InteractionHand.MAIN_HAND) abrir(sp, sl, f);
			return InteractionResult.SUCCESS;
		}
		boolean casca = isCascaItem(held), ctrl = isControladorItem(held);
		if (!casca && !ctrl) return InteractionResult.PASS;
		if (!sp.isSecondaryUseActive() && sl.getBlockState(clicked).getMenuProvider(sl, clicked) != null) return InteractionResult.PASS;
		colocar(sp, sl, hit, held, ctrl);
		return InteractionResult.SUCCESS;
	}

	private static void abrir(ServerPlayer sp, ServerLevel sl, Forno f) {
		if (!f.formado) {
			String erro = tentarFormar(sl, f);
			if (erro != null) {
				say(sp, erro, ChatFormatting.RED);
				say(sp, "A estrutura é um cubo 3×3×3 atrás do Controlador: núcleo (bloco de ferro, ouro, diamante ou netherite) no centro e Cascas Industriais em todo o resto.", ChatFormatting.GRAY);
				return;
			}
			say(sp, NOME + " montada! Nível " + NIVEIS[f.nivel] + ": " + Fundicao.paralelo(f.nivel) + " itens por vez, "
				+ fmt(Fundicao.velocidade(f.nivel)) + "x mais rápida.", ChatFormatting.GREEN);
			return;
		}
		if (!(sl.getBlockEntity(f.pos()) instanceof BarrelBlockEntity be)) return;
		boolean pack = hasPack(sp);
		sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new FornalhaMenu(id, inv, be, f, pack), title(pack)));
		sl.playSound(null, f.pos(), SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.6f, 0.9f);
	}

	private static boolean onBreak(Level level, Player player, BlockPos pos, BlockState state, BlockEntity be) {
		if (store == null || !(level instanceof ServerLevel sl)) return true;
		String dim = dim(sl);
		boolean survival = !player.isCreative();
		Forno f = store.forno(dim, pos);
		if (f != null) {
			if (be instanceof BarrelBlockEntity b) { // o que estava guardado sempre volta
				for (int i = 0; i < b.getContainerSize(); i++) {
					ItemStack s = b.removeItemNoUpdate(i);
					if (!s.isEmpty()) Block.popResource(sl, pos, s);
				}
			}
			if (f.xp >= 1) ExperienceOrb.award(sl, Vec3.atCenterOf(pos), (int) f.xp);
			removerForno(sl, f);
			sl.destroyBlock(pos, false, player);
			if (survival) {
				player.getMainHandItem().hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
				Block.popResource(sl, pos, controladorItem());
			}
			return false;
		}
		if (store.isCasca(dim, pos)) {
			boolean drop = survival && player.hasCorrectToolForDrops(state);
			removerCasca(sl, pos);
			sl.destroyBlock(pos, false, player);
			if (survival) player.getMainHandItem().hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
			if (drop) Block.popResource(sl, pos, cascaItem());
			return false; // a quebra vanilla soltaria uma pedra lisa comum
		}
		return true;
	}

	private static void onWorldTick(ServerLevel sl) {
		if (store == null) return;
		long t = sl.getGameTime();
		String dim = dim(sl);
		if (t % 100 == 0 && sl == server.overworld()) store.flush();
		for (Forno f : store.fornosEm(dim)) {
			BlockPos pos = f.pos();
			if (!sl.isLoaded(pos) || !sl.isPositionEntityTicking(pos)) continue;
			if (!sl.getBlockState(pos).is(Blocks.BARREL) || !(sl.getBlockEntity(pos) instanceof BarrelBlockEntity be)) {
				LOG.info("Controlador em {} não existe mais; removido", pos);
				removerForno(sl, f);
				continue;
			}
			int fase = Math.floorMod(pos.hashCode(), 20);
			if (f.formado && (!f.conferido || t % 20 == fase)) conferir(sl, f);
			if (f.formado) {
				Fundicao.tick(sl, f, be, sl.hasNeighborSignal(pos));
				if (t % 8 == fase % 8) Fundicao.funis(sl, f, be);
			}
			if (t % 10 == fase % 10 && anyPlayerNear(sl, pos)) visual(sl, f.key(), pos, "blast_furnace", modelo(f));
		}
		if (t % 20 == 0) {
			for (BlockPos p : store.cascasEm(dim)) {
				if (!sl.isLoaded(p) || !sl.isPositionEntityTicking(p)) continue;
				if (!sl.getBlockState(p).is(Blocks.SMOOTH_STONE)) { // sumiu por explosão, pistão, comando...
					removerCasca(sl, p);
					continue;
				}
				if (anyPlayerNear(sl, p)) {
					Forno dono = store.donoDe(dim, p);
					visual(sl, FornalhaStore.key(dim, p), p, "smooth_stone",
						dono != null && dono.formado ? "fornalha_casca_" + dono.nivel : "fornalha_casca");
				}
			}
		}
	}

	private static boolean anyPlayerNear(ServerLevel sl, BlockPos pos) {
		for (ServerPlayer p : sl.players()) if (p.distanceToSqr(Vec3.atCenterOf(pos)) < VISUAL_RANGE_SQR) return true;
		return false;
	}

	// ---------- estrutura ----------

	/** Confere o cubo. Devolve null se está certo (e preenche nivel/cascas), senão o que está errado. */
	static String validar(ServerLevel sl, Forno f, int[] nivel, List<BlockPos> cascas) {
		String dim = dim(sl);
		BlockPos centro = f.centro(), ctrl = f.pos();
		for (int dy = -1; dy <= 1; dy++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					BlockPos p = centro.offset(dx, dy, dz);
					if (p.equals(ctrl)) continue;
					if (dx == 0 && dy == 0 && dz == 0) {
						nivel[0] = nivelDoNucleo(sl.getBlockState(p));
						if (nivel[0] == 0) return "Falta o núcleo no centro da estrutura (" + xyz(p) + "): bloco de ferro, ouro, diamante ou netherite.";
						continue;
					}
					if (!store.isCasca(dim, p) || !sl.getBlockState(p).is(Blocks.SMOOTH_STONE)) return "Falta Casca Industrial em " + xyz(p) + ".";
					Forno dono = store.donoDe(dim, p);
					if (dono != null && dono != f && dono.formado) return "A casca em " + xyz(p) + " já pertence a outra fornalha.";
					cascas.add(p);
				}
			}
		}
		return null;
	}

	static int nivelDoNucleo(BlockState s) {
		if (s.is(Blocks.NETHERITE_BLOCK)) return 4;
		if (s.is(Blocks.DIAMOND_BLOCK)) return 3;
		if (s.is(Blocks.GOLD_BLOCK)) return 2;
		if (s.is(Blocks.IRON_BLOCK)) return 1;
		return 0;
	}

	/** Botão direito num Controlador sem estrutura: confere e, se estiver certo, monta. */
	static String tentarFormar(ServerLevel sl, Forno f) {
		int[] nivel = {0};
		List<BlockPos> cascas = new ArrayList<>();
		String erro = validar(sl, f, nivel, cascas);
		if (erro != null) return erro;
		formar(sl, f, nivel[0], cascas);
		BlockPos p = f.pos();
		sl.playSound(null, p, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.5f, 1.4f);
		sl.sendParticles(ParticleTypes.FLAME, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 20, 0.6, 0.6, 0.6, 0.02);
		return null;
	}

	private static void formar(ServerLevel sl, Forno f, int nivel, List<BlockPos> cascas) {
		String dim = dim(sl);
		store.dono.values().removeIf(o -> o == f);
		for (BlockPos c : cascas) store.dono.put(FornalhaStore.key(dim, c), f);
		boolean mudou = !f.formado || f.nivel != nivel;
		f.formado = true;
		f.nivel = nivel;
		f.conferido = true;
		if (mudou) {
			store.save();
			for (BlockPos c : cascas) atualizarCasca(sl, c);
		}
	}

	/** A cada segundo (e na primeira vez depois de ligar o servidor): a estrutura continua certa? O núcleo mudou? */
	static void conferir(ServerLevel sl, Forno f) {
		int[] nivel = {0};
		List<BlockPos> cascas = new ArrayList<>();
		if (validar(sl, f, nivel, cascas) == null) {
			formar(sl, f, nivel[0], cascas);
		} else {
			desformar(sl, f);
		}
	}

	static void desformar(ServerLevel sl, Forno f) {
		if (!f.formado) return;
		List<BlockPos> eram = new ArrayList<>();
		String dim = dim(sl);
		for (var e : store.dono.entrySet()) if (e.getValue() == f) eram.add(parse(e.getKey()));
		store.dono.values().removeIf(o -> o == f);
		f.formado = false;
		f.nivel = 0;
		f.progresso = 0;
		f.status = Status.PARADO;
		store.save();
		sl.playSound(null, f.pos(), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 1.0f);
		for (BlockPos c : eram) atualizarCasca(sl, c);
		LOG.debug("Fornalha em {} {} desmontada", dim, f.pos());
	}

	private static BlockPos parse(String key) {
		String[] c = key.substring(key.indexOf('|') + 1).split(",");
		return new BlockPos(Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2]));
	}

	private static void atualizarCasca(ServerLevel sl, BlockPos p) {
		String dim = dim(sl);
		Forno dono = store.donoDe(dim, p);
		Visual v = VISUAIS.get(FornalhaStore.key(dim, p));
		if (v != null && v.entity != null && !v.entity.isRemoved()) {
			visual(sl, FornalhaStore.key(dim, p), p, "smooth_stone", dono != null && dono.formado ? "fornalha_casca_" + dono.nivel : "fornalha_casca");
		}
	}

	static String modelo(Forno f) {
		if (!f.formado) return "fornalha_ctrl";
		return "fornalha_ctrl_" + f.nivel + (f.status == Status.TRABALHANDO ? "_on" : "");
	}

	// ---------- colocar e remover ----------

	private static void colocar(ServerPlayer sp, ServerLevel sl, BlockHitResult hit, ItemStack stack, boolean ctrl) {
		BlockPos clicked = hit.getBlockPos();
		BlockPos pos = sl.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(hit.getDirection());
		if (!sp.mayUseItemAt(pos, hit.getDirection(), stack) || !sl.mayInteract(sp, pos)) return;
		BlockState state = ctrl ? Blocks.BARREL.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState();
		if (!sl.getBlockState(pos).canBeReplaced() || !sl.isUnobstructed(state, pos, CollisionContext.empty())) return;
		boolean ok = ctrl ? colocarControlador(sl, pos, sp.getDirection().getOpposite()) != null : colocarCasca(sl, pos);
		if (!ok) {
			say(sp, "Não foi possível colocar aqui.", ChatFormatting.RED);
			return;
		}
		if (!sp.isCreative()) stack.shrink(1);
		if (ctrl) {
			say(sp, CONTROLADOR + " colocado. Monte o cubo 3×3×3 atrás dele (núcleo no centro, cascas no resto) e clique nele.", ChatFormatting.GREEN);
		}
	}

	static boolean colocarCasca(ServerLevel sl, BlockPos pos) {
		if (!sl.setBlock(pos, Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL)) return false;
		store.addCasca(dim(sl), pos);
		visual(sl, FornalhaStore.key(dim(sl), pos), pos, "smooth_stone", "fornalha_casca");
		sl.playSound(null, pos, SoundEvents.NETHERITE_BLOCK_PLACE, SoundSource.BLOCKS, 0.8f, 1.1f);
		return true;
	}

	static Forno colocarControlador(ServerLevel sl, BlockPos pos, Direction frente) {
		if (frente.getAxis() == Direction.Axis.Y) frente = Direction.NORTH;
		if (!sl.setBlock(pos, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, frente), Block.UPDATE_ALL)) return null;
		Forno f = new Forno(dim(sl), pos, frente);
		store.addForno(f);
		visual(sl, f.key(), pos, "blast_furnace", modelo(f));
		sl.playSound(null, pos, SoundEvents.NETHERITE_BLOCK_PLACE, SoundSource.BLOCKS, 0.8f, 0.9f);
		return f;
	}

	static void removerForno(ServerLevel sl, Forno f) {
		List<BlockPos> eram = new ArrayList<>();
		for (var e : store.dono.entrySet()) if (e.getValue() == f) eram.add(parse(e.getKey()));
		f.formado = false;
		store.removeForno(f);
		matarVisual(sl, f.key(), f.pos());
		for (BlockPos c : eram) atualizarCasca(sl, c);
	}

	static void removerCasca(ServerLevel sl, BlockPos p) {
		String dim = dim(sl);
		Forno dono = store.donoDe(dim, p);
		store.removeCasca(dim, p);
		matarVisual(sl, FornalhaStore.key(dim, p), p);
		if (dono != null) desformar(sl, dono);
	}

	// ---------- visual ----------

	/**
	 * Um item_display por bloco com o modelo dele (1,004x o bloco, para não piscar). Com o pack, o item (pedra lisa ou
	 * alto-forno com custom_model_data "fornalha_*") vira o aço; sem o pack, continua pedra lisa/alto-forno.
	 */
	static void visual(ServerLevel sl, String key, BlockPos pos, String item, String modelo) {
		Visual v = VISUAIS.computeIfAbsent(key, k -> new Visual());
		if (v.entity == null || v.entity.isRemoved() || v.entity.level() != sl) {
			for (Entity e : visuals(sl, pos)) e.discard();
			String nbt = "Tags:[\"fornalha\",\"" + tag(pos) + "\",\"" + RUN_TAG + "\"]," + itemNbt(item, modelo) + ","
				+ "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,-0.5f,0f],scale:[1.004f,1.004f,1.004f]}";
			command(sl, pos, String.format(Locale.ROOT, "summon minecraft:item_display %.4f %.4f %.4f {%s}",
				pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, nbt));
			List<Entity> found = visuals(sl, pos);
			v.entity = found.isEmpty() ? null : found.get(0);
			v.modelo = modelo;
			v.light = -1;
		} else if (!modelo.equals(v.modelo)) {
			command(sl, pos, "data merge entity " + v.entity.getStringUUID() + " {" + itemNbt(item, modelo) + "}");
			v.modelo = modelo;
		}
		if (v.entity != null) luz(sl, pos, v);
	}

	private static String itemNbt(String item, String modelo) {
		return "item:{id:\"minecraft:" + item + "\",count:1,components:{\"minecraft:custom_model_data\":{strings:[\"" + modelo + "\"]}}}";
	}

	/** A entidade fica dentro de blocos sólidos (escura): a luz é fixada na maior entre os 6 vizinhos. */
	private static void luz(ServerLevel sl, BlockPos pos, Visual v) {
		int block = 0, sky = 0;
		for (Direction d : Direction.values()) {
			BlockPos p = pos.relative(d);
			block = Math.max(block, sl.getBrightness(LightLayer.BLOCK, p));
			sky = Math.max(sky, sl.getBrightness(LightLayer.SKY, p));
		}
		int packed = block << 4 | sky;
		if (packed == v.light) return;
		v.light = packed;
		command(sl, pos, "data merge entity " + v.entity.getStringUUID() + " {brightness:{block:" + block + ",sky:" + sky + "}}");
	}

	static void matarVisual(ServerLevel sl, String key, BlockPos pos) {
		VISUAIS.remove(key);
		for (Entity e : visuals(sl, pos)) e.discard();
	}

	static List<Entity> visuals(ServerLevel sl, BlockPos pos) {
		String tag = tag(pos);
		return sl.getEntitiesOfClass(Entity.class, new AABB(pos).inflate(1), e -> e.entityTags().contains(tag));
	}

	private static String tag(BlockPos pos) {
		return "fornalha_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
	}

	private static boolean command(ServerLevel sl, BlockPos pos, String cmd) {
		boolean[] ok = {false};
		CommandSourceStack source = server.createCommandSourceStack().withLevel(sl).withPosition(Vec3.atCenterOf(pos))
			.withSuppressedOutput().withCallback((success, result) -> ok[0] = success);
		server.getCommands().performPrefixedCommand(source, cmd);
		if (!ok[0]) LOG.warn("Comando da Fornalha falhou: {}", cmd);
		return ok[0];
	}

	// ---------- itens ----------

	static String dim(Level level) {
		return level.dimension().identifier().toString();
	}

	private static boolean temTag(ItemStack stack, String tag) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().contains(tag);
	}

	static boolean isCascaItem(ItemStack s) {
		return s.is(Items.SMOOTH_STONE) && temTag(s, TAG_CASCA);
	}

	static boolean isControladorItem(ItemStack s) {
		return s.is(Items.BLAST_FURNACE) && temTag(s, TAG_CTRL);
	}

	/** Os mesmos itens das receitas (data/fornalha/recipe), para devolver ao quebrar. */
	static ItemStack cascaItem() {
		return item(new ItemStack(Items.SMOOTH_STONE), TAG_CASCA, "fornalha_casca", CASCA);
	}

	static ItemStack controladorItem() {
		return item(new ItemStack(Items.BLAST_FURNACE), TAG_CTRL, "fornalha_ctrl", CONTROLADOR);
	}

	private static ItemStack item(ItemStack s, String tag, String modelo, String nome) {
		CompoundTag t = new CompoundTag();
		t.putBoolean(tag, true);
		s.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
		s.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(modelo), List.of()));
		s.set(DataComponents.CUSTOM_NAME, Component.literal(nome).withStyle(st -> st.withItalic(false).withColor(ChatFormatting.AQUA)));
		return s;
	}

	// ---------- resource pack ----------

	private static String modVersion() {
		String v = FabricLoader.getInstance().getModContainer("fornalha").orElseThrow().getMetadata().getVersion().getFriendlyString();
		int i = v.indexOf('+');
		return i < 0 ? v : v.substring(0, i);
	}

	private static String readPackHash() {
		try (InputStream in = Fornalha.class.getResourceAsStream("/fornalha/pack.sha1")) {
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

	static boolean hasPack(ServerPlayer p) {
		return server.isDedicatedServer() ? PACK_OK.contains(p.getUUID()) : server.isSingleplayerOwner(p.nameAndId());
	}

	/** Título da tela. Com o pack: o fundo inteiro é um caractere da fonte fornalha:gui (arte/gerar.py) e depois o nome. */
	static Component title(boolean pack) {
		if (!pack) return Component.literal(NOME);
		JsonObject bg = new JsonObject();
		bg.addProperty("text", "");
		bg.addProperty("font", "fornalha:gui");
		bg.addProperty("color", "white");
		JsonObject text = new JsonObject();
		text.addProperty("text", NOME);
		text.addProperty("color", "#303030");
		JsonArray parts = new JsonArray();
		parts.add(new JsonPrimitive(""));
		parts.add(bg);
		parts.add(text);
		return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, parts).result().orElse(Component.literal(NOME));
	}

	static String xyz(BlockPos p) {
		return p.getX() + " " + p.getY() + " " + p.getZ();
	}

	static String fmt(double d) {
		return d == Math.floor(d) ? String.valueOf((int) d) : String.valueOf(d).replace('.', ',');
	}

	static void say(Player player, String text, ChatFormatting color) {
		if (player instanceof ServerPlayer sp) sp.sendSystemMessage(Component.literal(text).withStyle(color));
	}
}
