package io.github.ivanils0n.automato;

import io.github.ivanils0n.automato.MachineStore.Machine;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Autômato, 100% no servidor: é um ejetor de verdade (com os 9 espaços, funil, alavanca e redstone do jogo) marcado em
 * <mundo>/automato.json e coberto por um item_display (com o resource pack ele ganha textura própria; sem o pack
 * continua com cara de ejetor). Nenhum bloco ou item novo é registrado: sem o mod sobra um ejetor comum com as
 * ferramentas dentro.
 */
public final class Automato implements ModInitializer {
	static final Logger LOG = LoggerFactory.getLogger("automato");
	static final String NAME = "Autômato";
	static final String ITEM_TAG = "automato";
	private static final double VISUAL_RANGE_SQR = 64 * 64; // sem jogador a essa distância o visual não é conferido

	private static final String PACK_URL = "https://github.com/ivanils0n/MC-Mods/releases/download/automato-" + modVersion() + "/automato-pack-";
	static final String PACK_SHA1 = readPackHash();
	private static final UUID PACK_ID = UUID.nameUUIDFromBytes("automato:pack".getBytes(StandardCharsets.UTF_8));
	/** Tag desta execução: o visual nunca é salvo (mixin/EntityMixin); um "automato" sem ela é sobra antiga. */
	private static final String RUN_TAG = "automato_run_" + Long.toHexString(new java.util.Random().nextLong());
	private static final List<Entity> LEFTOVERS = new ArrayList<>();

	private static final Set<UUID> PACK_OK = ConcurrentHashMap.newKeySet();
	static MinecraftServer server;
	static MachineStore store;

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(s -> {
			server = s;
			store = MachineStore.load(s.getWorldPath(LevelResource.ROOT).resolve("automato.json"));
			LOG.info("{} autômatos carregados", store.size());
			if (Boolean.getBoolean("automato.selftest")) SelfTest.run(s);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			server = null;
			store = null;
			LEFTOVERS.clear();
			PACK_OK.clear();
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains("automato") && !entity.entityTags().contains(RUN_TAG)) LEFTOVERS.add(entity);
		});
		ServerTickEvents.END_SERVER_TICK.register(s -> {
			for (Entity e : LEFTOVERS) if (!e.isRemoved()) e.discard();
			LEFTOVERS.clear();
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> {
			if (PACK_SHA1 == null || !s.isDedicatedServer()) return; // singleplayer: as texturas vêm do .jar
			sender.sendPacket(new ClientboundResourcePackPushPacket(PACK_ID, PACK_URL + PACK_SHA1 + ".zip", PACK_SHA1, false,
				Optional.of(Component.literal("Texturas do Autômato"))));
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> PACK_OK.remove(handler.player.getUUID()));
		UseBlockCallback.EVENT.register(Automato::onUseBlock);
		PlayerBlockBreakEvents.BEFORE.register(Automato::onBreak);
		ServerTickEvents.END_LEVEL_TICK.register(Automato::onWorldTick);
	}

	/** Usado pelo mixin do ejetor: o Autômato não atira itens com redstone. */
	public static boolean isMachine(ServerLevel sl, BlockPos pos) {
		return store != null && store.get(dim(sl), pos) != null;
	}

	/** Usado pelo mixin do ejetor: funis só põem ferramentas e armas no Autômato. */
	public static boolean isToolItem(ItemStack stack) {
		return Worker.isUsable(stack);
	}

	// ---------- eventos ----------

	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (store == null || !(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp) || sp.isSpectator()) {
			return InteractionResult.PASS;
		}
		ItemStack held = sp.getItemInHand(hand);
		BlockPos clicked = hit.getBlockPos();
		Machine m = store.get(dim(sl), clicked);
		// Clique no Autômato abre a tela dele (agachado com um item na mão: usa o item, como num baú).
		if (m != null && !(sp.isSecondaryUseActive() && !held.isEmpty())) {
			if (hand == InteractionHand.MAIN_HAND && sl.getBlockEntity(clicked) instanceof DispenserBlockEntity be) {
				boolean pack = hasPack(sp);
				sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new ToolMenu(id, inv, be, m, pack), title(pack)));
			}
			return InteractionResult.SUCCESS;
		}
		if (!isMachineItem(held)) return InteractionResult.PASS;
		// Clicar num baú, ejetor etc. sem agachar abre o bloco, como com qualquer item na mão.
		if (!sp.isSecondaryUseActive() && sl.getBlockState(clicked).getMenuProvider(sl, clicked) != null) return InteractionResult.PASS;
		place(sp, sl, hit, held);
		return InteractionResult.SUCCESS;
	}

	private static boolean onBreak(Level level, Player player, BlockPos pos, BlockState state, BlockEntity be) {
		if (store == null || !(level instanceof ServerLevel sl)) return true;
		Machine m = store.get(dim(sl), pos);
		if (m == null) return true;
		boolean survival = !player.isCreative();
		boolean drop = survival && player.hasCorrectToolForDrops(state);
		if (be instanceof DispenserBlockEntity d) { // as ferramentas guardadas sempre voltam
			for (int i = 0; i < d.getContainerSize(); i++) {
				ItemStack s = d.removeItemNoUpdate(i);
				if (!s.isEmpty()) Block.popResource(sl, pos, s);
			}
		}
		removeMachine(sl, m);
		sl.destroyBlock(pos, false, player);
		if (survival) player.getMainHandItem().hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
		if (drop) Block.popResource(sl, pos, machineItem());
		return false; // a quebra vanilla soltaria um ejetor comum
	}

	private static void onWorldTick(ServerLevel sl) {
		if (store == null) return;
		boolean second = sl.getGameTime() % 20 == 0;
		for (Machine m : store.inDim(dim(sl))) {
			BlockPos pos = m.pos();
			if (!sl.isLoaded(pos) || !sl.isPositionEntityTicking(pos)) continue;
			BlockState state = sl.getBlockState(pos);
			if (!state.is(Blocks.DISPENSER) || !(sl.getBlockEntity(pos) instanceof DispenserBlockEntity be)) {
				// Sumiu por outro meio (explosão, comando, editor de mundo): sobra o que o jogo deixou.
				LOG.info("Autômato em {} não existe mais; removido", pos);
				removeMachine(sl, m);
				continue;
			}
			Direction facing = state.getValue(DispenserBlock.FACING);
			boolean powered = sl.hasNeighborSignal(pos);
			Worker.tick(sl, m, state, be, facing, powered);
			boolean on = powered && m.slot >= 0;
			if (second && anyPlayerNear(sl, pos)) {
				ensureVisual(sl, m, facing, on);
				updateLight(sl, m);
			}
			updateVisual(sl, m, facing, on, m.slot >= 0 ? be.getItem(m.slot) : null);
		}
	}

	private static boolean anyPlayerNear(ServerLevel sl, BlockPos pos) {
		for (ServerPlayer p : sl.players()) if (p.distanceToSqr(Vec3.atCenterOf(pos)) < VISUAL_RANGE_SQR) return true;
		return false;
	}

	// ---------- colocar e remover ----------

	/** Coloca virado para onde o jogador olha (a frente fica do lado oposto a ele, como quem trabalha à sua frente). */
	private static void place(ServerPlayer sp, ServerLevel sl, BlockHitResult hit, ItemStack stack) {
		BlockPos clicked = hit.getBlockPos();
		BlockPos pos = sl.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(hit.getDirection());
		if (!sp.mayUseItemAt(pos, hit.getDirection(), stack) || !sl.mayInteract(sp, pos)) return;
		if (!sl.getBlockState(pos).canBeReplaced() || !sl.isUnobstructed(Blocks.DISPENSER.defaultBlockState(), pos, CollisionContext.empty())) return;
		Direction facing = Direction.orderedByNearest(sp)[0];
		if (placeMachine(sl, pos, facing, stack) == null) {
			say(sp, "Não foi possível colocar o " + NAME + " aqui.", ChatFormatting.RED);
			return;
		}
		if (!sp.isCreative()) stack.shrink(1);
		say(sp, NAME + " colocado! Abra-o, guarde uma ferramenta, escolha o alcance e ligue com redstone ou uma alavanca.", ChatFormatting.GOLD);
	}

	static Machine placeMachine(ServerLevel sl, BlockPos pos, Direction facing, ItemStack stack) {
		if (!sl.setBlock(pos, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, facing), Block.UPDATE_ALL)) return null;
		if (sl.getBlockEntity(pos) instanceof DispenserBlockEntity be) be.applyComponentsFromItemStack(stack); // nome na tela
		Machine m = new Machine(dim(sl), pos);
		store.add(m);
		spawnVisual(sl, m, facing, false);
		sl.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.4f, 1.6f);
		return m;
	}

	static void removeMachine(ServerLevel sl, Machine m) {
		Worker.stopMining(sl, m);
		store.remove(m);
		killVisual(sl, m.pos());
	}

	// ---------- visual ----------

	private static void ensureVisual(ServerLevel sl, Machine m, Direction facing, boolean on) {
		List<Entity> found = visuals(sl, m.pos());
		long blocks = found.stream().filter(e -> e.entityTags().contains("automato_block")).count();
		long tools = found.stream().filter(e -> e.entityTags().contains("automato_tool")).count();
		if (blocks != 1 || tools != 1) spawnVisual(sl, m, facing, on);
	}

	/**
	 * Duas peças: o bloco (o modelo cobre o ejetor e todo o relevo fica para fora dele, senão o ejetor o esconderia, girado para a frente dele, no topo do bloco porque a luz
	 * da entidade é a do bloco onde ela está) e a ferramenta guardada, o próprio item, saindo do arco da frente.
	 */
	static void spawnVisual(ServerLevel sl, Machine m, Direction facing, boolean on) {
		BlockPos pos = m.pos();
		killVisual(sl, pos);
		double[] q = rotation(facing);
		String tags = "Tags:[\"automato\",\"" + tag(pos) + "\",\"" + RUN_TAG + "\",";
		String nbt = tags + "\"automato_block\"],item:" + displayItem(on)
			+ String.format(Locale.ROOT, ",transformation:{left_rotation:[%.5ff,%.5ff,%.5ff,%.5ff],right_rotation:[0f,0f,0f,1f],"
				+ "translation:[0f,-0.5f,0f],scale:[1.004f,1.004f,1.004f]}", q[0], q[1], q[2], q[3]);
		command(sl, pos, String.format(Locale.ROOT, "summon minecraft:item_display %.4f %.4f %.4f {%s}",
			pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, nbt));
		Vec3 at = toolSpot(pos, facing);
		command(sl, pos, String.format(Locale.ROOT, "summon minecraft:item_display %.4f %.4f %.4f {%s\"automato_tool\"],%s}",
			at.x, at.y, at.z, tags, toolTransform(facing, 0)));
		m.shownOn = on;
		m.shownItem = null;
		m.shownFoil = false;
		m.poke = 0;
		m.light = -1;
		updateLight(sl, m);
	}

	/**
	 * A entidade do visual pega a luz do bloco onde está; encostada em blocos sólidos ficaria preta. Por isso a luz é
	 * fixada na maior luz (de bloco e de céu) entre os 6 vizinhos, e conferida a cada segundo (dia e noite seguem
	 * normais, porque a luz do céu continua sendo luz do céu).
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
		command(sl, pos, "data merge entity @e[type=minecraft:item_display,tag=" + tag(pos) + ",limit=2] {brightness:{block:" + block + ",sky:" + sky + "}}");
	}

	/** Ponto do meio da ferramenta: um pouco à frente do arco (a luz é a do bloco à frente, normalmente ar). */
	private static Vec3 toolSpot(BlockPos pos, Direction f) {
		return Vec3.atCenterOf(pos).add(f.getStepX() * 0.62, f.getStepY() * 0.62 + (f.getAxis().isHorizontal() ? 0.03 : 0), f.getStepZ() * 0.62);
	}

	/**
	 * A ferramenta fica em pé, de lado: o cabo (canto de baixo à esquerda do desenho do item) entra no arco e a ponta
	 * aponta para a frente. {@code push}: quanto ela avança na "batida" (0 = parada).
	 */
	static String toolTransform(Direction f, double push) {
		// Colunas da rotação: X do item -> -frente (o item_display gira o item 180° antes), Y -> cima, Z -> X × Y.
		double[] a = {-f.getStepX(), -f.getStepY(), -f.getStepZ()};
		double[] b = f.getAxis().isHorizontal() ? new double[] {0, 1, 0} : new double[] {0, 0, -1};
		double[] c = {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
		double[] q = quat(a, b, c);
		return String.format(Locale.ROOT, "transformation:{left_rotation:[%.5ff,%.5ff,%.5ff,%.5ff],right_rotation:[0f,0f,0f,1f],"
			+ "translation:[%.3ff,%.3ff,%.3ff],scale:[0.6f,0.6f,0.6f]}", q[0], q[1], q[2], q[3],
			f.getStepX() * push, f.getStepY() * push, f.getStepZ() * push);
	}

	/** Quatérnio (x, y, z, w) da matriz de rotação com colunas a, b, c. */
	static double[] quat(double[] a, double[] b, double[] c) {
		double m00 = a[0], m10 = a[1], m20 = a[2], m01 = b[0], m11 = b[1], m21 = b[2], m02 = c[0], m12 = c[1], m22 = c[2];
		double tr = m00 + m11 + m22;
		double x, y, z, w;
		if (tr > 0) {
			double s = Math.sqrt(tr + 1) * 2;
			w = 0.25 * s; x = (m21 - m12) / s; y = (m02 - m20) / s; z = (m10 - m01) / s;
		} else if (m00 > m11 && m00 > m22) {
			double s = Math.sqrt(1 + m00 - m11 - m22) * 2;
			w = (m21 - m12) / s; x = 0.25 * s; y = (m01 + m10) / s; z = (m02 + m20) / s;
		} else if (m11 > m22) {
			double s = Math.sqrt(1 + m11 - m00 - m22) * 2;
			w = (m02 - m20) / s; x = (m01 + m10) / s; y = 0.25 * s; z = (m12 + m21) / s;
		} else {
			double s = Math.sqrt(1 + m22 - m00 - m11) * 2;
			w = (m10 - m01) / s; x = (m02 + m20) / s; y = (m12 + m21) / s; z = 0.25 * s;
		}
		return new double[] {x, y, z, w};
	}

	/** Atualiza o visual: modelo ligado/desligado, a ferramenta mostrada e a "batida" (o Worker marca m.poke). */
	private static void updateVisual(ServerLevel sl, Machine m, Direction facing, boolean on, ItemStack tool) {
		boolean foil = tool != null && tool.hasFoil();
		Item item = tool == null ? null : tool.getItem();
		if (on == m.shownOn && item == m.shownItem && foil == m.shownFoil && m.poke == 0) return;
		List<Entity> found = visuals(sl, m.pos());
		if (found.isEmpty()) return; // ninguém por perto: ensureVisual cria quando alguém chegar
		for (Entity e : found) {
			if (e.entityTags().contains("automato_block") && on != m.shownOn) {
				e.getSlot(0).set(dispenserPiece(on));
			} else if (e.entityTags().contains("automato_tool")) {
				if (item != m.shownItem || foil != m.shownFoil) e.getSlot(0).set(tool == null ? ItemStack.EMPTY : tool.copyWithCount(1));
				if (m.poke == 4) poke(sl, m, facing, 0.12);
				else if (m.poke == 1) poke(sl, m, facing, 0);
			}
		}
		m.shownOn = on;
		m.shownItem = item;
		m.shownFoil = foil;
		if (m.poke > 0) m.poke--;
	}

	private static void poke(ServerLevel sl, Machine m, Direction facing, double push) {
		command(sl, m.pos(), "data merge entity @e[type=minecraft:item_display,tag=automato_tool,tag=" + tag(m.pos()) + ",limit=1] "
			+ "{start_interpolation:0,interpolation_duration:2," + toolTransform(facing, push) + "}");
	}

	private static ItemStack dispenserPiece(boolean on) {
		ItemStack s = new ItemStack(Items.DISPENSER);
		s.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(on ? "automato_on" : "automato_idle"), List.of()));
		return s;
	}

	/** Ejetor com a marca do pack: sem o pack, o modelo do ejetor vanilla (assets/minecraft/items/dispenser.json). */
	private static String displayItem(boolean on) {
		return "{id:\"minecraft:dispenser\",count:1,components:{\"minecraft:custom_model_data\":{strings:[\""
			+ (on ? "automato_on" : "automato_idle") + "\"]}}}";
	}

	/** Quatérnio (x, y, z, w) que leva a frente do modelo (sul, +z) para {@code facing}. */
	static double[] rotation(Direction facing) {
		double h = Math.sqrt(0.5);
		return switch (facing) {
			case SOUTH -> new double[] {0, 0, 0, 1};
			case NORTH -> new double[] {0, 1, 0, 0};
			case EAST -> new double[] {0, h, 0, h};
			case WEST -> new double[] {0, -h, 0, h};
			case UP -> new double[] {-h, 0, 0, h};
			case DOWN -> new double[] {h, 0, 0, h};
		};
	}

	static void killVisual(ServerLevel sl, BlockPos pos) {
		for (Entity e : visuals(sl, pos)) e.discard();
	}

	static List<Entity> visuals(ServerLevel sl, BlockPos pos) {
		String tag = tag(pos);
		return sl.getEntitiesOfClass(Entity.class, new AABB(pos).inflate(1), e -> e.entityTags().contains(tag));
	}

	private static String tag(BlockPos pos) {
		return "automato_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
	}

	/** Roda um comando como o próprio servidor (funciona com cheats desligados). Avisa no log se falhar. */
	private static boolean command(ServerLevel sl, BlockPos pos, String cmd) {
		boolean[] ok = {false};
		CommandSourceStack source = server.createCommandSourceStack().withLevel(sl).withPosition(Vec3.atCenterOf(pos))
			.withSuppressedOutput().withCallback((success, result) -> ok[0] = success);
		server.getCommands().performPrefixedCommand(source, cmd);
		if (!ok[0]) LOG.warn("Comando do Autômato falhou: {}", cmd);
		return ok[0];
	}

	// ---------- item ----------

	static String dim(Level level) {
		return level.dimension().identifier().toString();
	}

	static boolean isMachineItem(ItemStack stack) {
		if (!stack.is(Items.DISPENSER)) return false;
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().contains(ITEM_TAG);
	}

	/** O mesmo item da receita (data/automato/recipe/automato.json), para devolver ao quebrar. */
	static ItemStack machineItem() {
		ItemStack s = new ItemStack(Items.DISPENSER);
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(ITEM_TAG, true);
		s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		s.set(DataComponents.CUSTOM_NAME, Component.literal(NAME).withStyle(st -> st.withItalic(false).withColor(ChatFormatting.GOLD)));
		return s;
	}

	// ---------- resource pack ----------

	/** Versão do mod sem o sufixo "+mc..." (a do release no GitHub, ex.: 1.0.0). */
	private static String modVersion() {
		String v = FabricLoader.getInstance().getModContainer("automato").orElseThrow().getMetadata().getVersion().getFriendlyString();
		int i = v.indexOf('+');
		return i < 0 ? v : v.substring(0, i);
	}

	private static String readPackHash() {
		try (InputStream in = Automato.class.getResourceAsStream("/automato/pack.sha1")) {
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
	 * Título da tela. Com o pack: o fundo inteiro é um caractere da fonte automato:gui (arte/final.py) e depois vem o
	 * nome, no x=8 de sempre. Sem o pack, título comum (senão apareceriam quadradinhos de caractere desconhecido).
	 */
	static Component title(boolean pack) {
		if (!pack) return Component.literal(NAME);
		JsonObject bg = new JsonObject();
		bg.addProperty("text", "\uF000\uE000\uF001\uF002");
		bg.addProperty("font", "automato:gui");
		bg.addProperty("color", "white"); // o título vanilla é cinza escuro e escureceria o desenho
		JsonObject text = new JsonObject();
		text.addProperty("text", NAME);
		text.addProperty("color", "#FFE2B0");
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
