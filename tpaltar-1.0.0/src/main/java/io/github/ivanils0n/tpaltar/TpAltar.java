package io.github.ivanils0n.tpaltar;

import io.github.ivanils0n.tpaltar.AltarStore.Altar;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Altar de Teleporte, 100% no servidor: o altar é um bloco de obsidiana de verdade (base) com entidades de exibição
 * vanilla em cima (coluna, tampo, olho do fim) e uma entidade de interação para o clique. Nenhum bloco ou item novo é
 * registrado, então o cliente vanilla entra sem mod e o mundo continua abrindo normalmente se o mod for removido.
 */
public final class TpAltar implements ModInitializer {
	static final Logger LOG = LoggerFactory.getLogger("tpaltar");
	static final int MAX_PEARLS = 64;
	static final int COST = 2; // 64 pérolas = 32 viagens
	static final String ITEM_NAME = "Altar de Teleporte";
	static final int NAME_MAX = 24;
	static final int PLACE_LEVELS = 5; // níveis de XP para nomear e colocar um altar

	/** Onde o altar vai ficar enquanto o jogador digita o nome na tela da bigorna. */
	record Pending(ResourceKey<Level> dim, BlockPos pos, InteractionHand hand) {}

	private static final double EYE_Y = 3.0, RING_Y = 2.125; // altura das peças acima da base
	private static final double ACTIVE_RANGE_SQR = 48 * 48; // sem jogador a essa distância o altar fica parado
	private static final int ANIM_TICKS = 40; // cada passo de animação (giro dos cristais, flutuar do olho) dura 2 s
	private static final float BOB = 0.06f;

	// Resource pack: o zip é publicado pelo CI no release "tpaltar-pack" com o hash no nome, e o mesmo hash vai
	// dentro do .jar (tpaltar/pack.sha1). Assim cada versão do mod aponta para o seu pack e o jogo só baixa de novo
	// quando as texturas mudam.
	private static final String PACK_URL = "https://github.com/ivanils0n/MC-Mods/releases/download/tpaltar-pack/tpaltar-pack-";
	static final String PACK_SHA1 = readPackHash();
	private static final UUID PACK_ID = UUID.nameUUIDFromBytes("tpaltar:pack".getBytes(StandardCharsets.UTF_8));

	/** Jogadores do servidor dedicado que carregaram o pack (só eles recebem os fundos de tela, que dependem dele). */
	private static final Set<UUID> PACK_OK = ConcurrentHashMap.newKeySet();
	private static final Map<UUID, Integer> LAST_USE = new ConcurrentHashMap<>();
	/** Quem acabou de viajar -> tick do servidor em que tocam o som e as partículas da chegada. */
	private static final Map<UUID, Integer> ARRIVALS = new HashMap<>();
	private static final int ARRIVAL_DELAY = 4;
	/** Quem está com o menu de destinos de cada altar aberto (chave do altar -> jogador). */
	private static final Map<String, UUID> USERS = new HashMap<>();
	/** Entidades de cada altar já encontradas, para não procurá-las no mundo a cada tick. */
	private static final Map<String, Visuals> VIS = new HashMap<>();

	private static final class Visuals {
		final Display.ItemDisplay eye, ring;
		int step;

		Visuals(Display.ItemDisplay eye, Display.ItemDisplay ring) {
			this.eye = eye;
			this.ring = ring;
		}

		boolean alive() {
			return !eye.isRemoved() && !ring.isRemoved();
		}
	}
	/** Tag única desta execução do servidor. As decorações nunca são salvas no mapa (mixin/EntityMixin), então uma
	 * entidade "tpaltar" que chega do disco sem essa tag é sobra de versão antiga e é apagada. */
	private static final String RUN_TAG = "tpaltar_run_" + Long.toHexString(new java.util.Random().nextLong());
	private static final List<Entity> LEFTOVERS = new java.util.ArrayList<>();
	private static MinecraftServer server;
	private static AltarStore store;

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(s -> {
			server = s;
			store = AltarStore.load(s.getWorldPath(LevelResource.ROOT).resolve("tpaltar.json"));
			LOG.info("{} altares carregados", store.size());
			if (Boolean.getBoolean("tpaltar.selftest")) SelfTest.run(s);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
			if (store != null) store.saveIfDirty();
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			server = null;
			store = null;
			LAST_USE.clear();
			ARRIVALS.clear();
			PACK_OK.clear();
			USERS.clear();
			VIS.clear();
			LEFTOVERS.clear();
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains("tpaltar") && !entity.entityTags().contains(RUN_TAG)) LEFTOVERS.add(entity);
		});
		ServerTickEvents.END_SERVER_TICK.register(s -> {
			// Apaga fora do evento de carregamento, para não mexer na lista de entidades enquanto o jogo a percorre.
			for (Entity e : LEFTOVERS) if (!e.isRemoved()) e.discard();
			LEFTOVERS.clear();
			if (!ARRIVALS.isEmpty()) {
				ARRIVALS.entrySet().removeIf(e -> {
					if (s.getTickCount() < e.getValue()) return false;
					ServerPlayer p = s.getPlayerList().getPlayer(e.getKey());
					if (p != null) arrive(p);
					return true;
				});
			}
			// Mudanças de pérolas são gravadas no máximo uma vez por segundo (e ao desligar), não a cada clique.
			if (store != null && s.getTickCount() % 20 == 0) store.saveIfDirty();
			if (ownModels() && s.getTickCount() % 20 == 0) {
				for (ServerPlayer p : s.getPlayerList().getPlayers()) {
					for (int i = 0; i < p.getInventory().getContainerSize(); i++) upgrade(p.getInventory().getItem(i));
				}
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> {
			// O dono do mundo singleplayer já tem as texturas no próprio .jar; os outros (servidor ou LAN) recebem o pack.
			if (PACK_SHA1 == null || s.isSingleplayerOwner(handler.player.nameAndId())) return;
			sender.sendPacket(new ClientboundResourcePackPushPacket(PACK_ID, PACK_URL + PACK_SHA1 + ".zip", PACK_SHA1, false,
				Optional.of(Component.literal("Texturas do Altar de Teleporte"))));
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> {
			PACK_OK.remove(handler.player.getUUID());
			LAST_USE.remove(handler.player.getUUID());
		});
		UseBlockCallback.EVENT.register(TpAltar::onUseBlock);
		UseEntityCallback.EVENT.register(TpAltar::onUseEntity);
		PlayerBlockBreakEvents.BEFORE.register(TpAltar::onBreak);
		ServerTickEvents.END_LEVEL_TICK.register(TpAltar::onWorldTick);
	}

	// ---------- eventos ----------

	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (store == null || !(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp) || sp.isSpectator()) {
			return InteractionResult.PASS;
		}
		Altar altar = store.get(dim(sl), hit.getBlockPos());
		if (altar != null) {
			if (hand == InteractionHand.MAIN_HAND) useAltar(sp, sl, altar);
			return InteractionResult.SUCCESS;
		}
		ItemStack stack = sp.getItemInHand(hand);
		if (!isAltarItem(stack)) return InteractionResult.PASS;
		askName(sp, sl, hit, hand, stack);
		if (sp.containerMenu == sp.inventoryMenu) sp.inventoryMenu.sendAllDataToRemote(); // o jogo já descontou o item por conta própria
		return InteractionResult.SUCCESS; // nunca deixa a obsidiana "crua" ser colocada
	}

	private static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity entity, EntityHitResult hit) {
		if (store == null || !(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp) || !(entity instanceof Interaction)) {
			return InteractionResult.PASS;
		}
		// A entidade de interação fica um bloco acima da base de obsidiana.
		Altar altar = store.get(dim(sl), entity.blockPosition().below());
		if (altar == null) return InteractionResult.PASS;
		if (hand == InteractionHand.MAIN_HAND) useAltar(sp, sl, altar);
		return InteractionResult.SUCCESS;
	}

	private static boolean onBreak(Level level, Player player, BlockPos pos, BlockState state, BlockEntity be) {
		if (store == null || !(level instanceof ServerLevel sl)) return true;
		Altar altar = store.get(dim(sl), pos);
		if (altar == null) return true;
		ItemStack tool = player.getMainHandItem();
		boolean survival = !player.isCreative();
		boolean keep = survival && (tool.is(Items.DIAMOND_PICKAXE) || tool.is(Items.NETHERITE_PICKAXE));
		removeAltar(sl, altar);
		sl.destroyBlock(pos, false, player);
		if (survival) tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
		if (keep) {
			Block.popResource(sl, pos, altarItem());
			if (altar.pearls > 0) Block.popResource(sl, pos, new ItemStack(Items.ENDER_PEARL, altar.pearls));
			say(player, "Altar \"" + altar.name + "\" removido.", ChatFormatting.YELLOW);
		} else if (survival) {
			say(player, "O altar \"" + altar.name + "\" se despedaçou. Use uma picareta de diamante para recuperá-lo.", ChatFormatting.RED);
		}
		return false; // a quebra vanilla soltaria uma obsidiana comum
	}

	private static void onWorldTick(ServerLevel sl) {
		long time = sl.getGameTime();
		if (store == null || time % 2 != 0) return;
		boolean everySecond = time % 20 == 0;
		boolean animate = time % ANIM_TICKS == 0;
		List<ServerPlayer> players = sl.players();
		if (players.isEmpty()) return;
		for (Altar a : store.inDimUnsorted(dim(sl))) {
			// Sem ninguém por perto não confere, não gira, não anima e não solta partículas: ninguém veria.
			if (!anyPlayerNear(players, a)) continue;
			BlockPos pos = a.pos();
			if (!sl.isLoaded(pos)) continue;
			if (everySecond && !sl.getBlockState(pos).is(Blocks.OBSIDIAN)) {
				// Sumiu por outro meio (Wither, comando, editor de mundo): remove sem dropar nada.
				LOG.info("Altar \"{}\" em {} não existe mais; removido", a.name, pos);
				removeAltar(sl, a);
				continue;
			}
			Visuals v = visuals(sl, a, everySecond);
			if (v == null) continue;
			if (everySecond) sl.sendParticles(ParticleTypes.PORTAL, a.x + 0.5, a.y + EYE_Y, a.z + 0.5, 6, 0.25, 0.3, 0.25, 0.4);
			// Rede de segurança: o olho já é trocado na hora em lock/unlock.
			boolean inUse = user(a) != null;
			if (inUse != eyeInUse(v.eye)) showEye(v.eye, inUse);
			lookAtNearest(sl, v.eye);
			if (animate) animate(sl, a, v);
		}
	}

	private static boolean anyPlayerNear(List<ServerPlayer> players, Altar a) {
		for (ServerPlayer p : players) if (p.distanceToSqr(a.x + 0.5, a.y + 1.5, a.z + 0.5) < ACTIVE_RANGE_SQR) return true;
		return false;
	}

	/** Entidades do altar (do cache). Se sumiram, recria — no máximo uma vez por segundo e só com o lugar ativo. */
	private static Visuals visuals(ServerLevel sl, Altar a, boolean mayRespawn) {
		Visuals v = VIS.get(a.key());
		if (v != null && v.alive()) return v;
		Display.ItemDisplay eye = findEye(sl, a), ring = findDisplay(sl, a, RING_Y);
		if (eye != null && ring != null) {
			v = new Visuals(eye, ring);
			VIS.put(a.key(), v);
			return v;
		}
		VIS.remove(a.key());
		// Decoração não é salva no mapa: recria quando o lugar volta a ser carregado com jogadores por perto.
		if (mayRespawn && sl.isPositionEntityTicking(a.pos())) spawnVisuals(sl, a.pos());
		return null;
	}

	static Display.ItemDisplay findEye(ServerLevel sl, Altar a) {
		return findDisplay(sl, a, EYE_Y);
	}

	/** A peça do altar que está naquela altura (cada peça tem a sua). */
	private static Display.ItemDisplay findDisplay(ServerLevel sl, Altar a, double dy) {
		String tag = tag(a.pos());
		List<Display.ItemDisplay> found = sl.getEntitiesOfClass(Display.ItemDisplay.class,
			new AABB(a.x, a.y + dy - 0.25, a.z, a.x + 1, a.y + dy + 0.25, a.z + 1), e -> e.entityTags().contains(tag));
		return found.isEmpty() ? null : found.get(0);
	}

	/** Pérola parada; vira olho do fim enquanto alguém escolhe o destino (com o pack: olho 3D verde / roxo). */
	static boolean showEye(Display.ItemDisplay eye, boolean inUse) {
		// Direto na entidade: um comando não acha a entidade enquanto o chunk dela ainda está carregando.
		return eye.getSlot(0).set(piece(inUse ? Items.ENDER_EYE : Items.ENDER_PEARL, "tpaltar_eye", inUse ? "eye_active" : "eye_idle"));
	}

	static boolean eyeInUse(Display.ItemDisplay eye) {
		return eye.getSlot(0).get().is(Items.ENDER_EYE);
	}

	/** Item com a marca do pack (cmd) e, no singleplayer, com o modelo próprio do mod (ver ownModels). */
	static ItemStack piece(Item item, String cmd, String own) {
		ItemStack s = new ItemStack(item);
		s.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(cmd), List.of()));
		if (ownModels()) s.set(DataComponents.ITEM_MODEL, model(own));
		return s;
	}

	/** Gira o olho (só na horizontal) para encarar o jogador mais próximo em até 16 blocos. */
	private static void lookAtNearest(ServerLevel sl, Display.ItemDisplay eye) {
		Player p = sl.getNearestPlayer(eye.getX(), eye.getY(), eye.getZ(), 16, false);
		if (p == null) return;
		float yaw = (float) Math.toDegrees(Math.atan2(p.getZ() - eye.getZ(), p.getX() - eye.getX())) - 90f;
		// Ignora mudanças pequenas: cada giro vira um pacote para os jogadores.
		if (Math.abs(Mth.wrapDegrees(yaw - eye.getYRot())) > 2f) eye.setYRot(yaw);
	}

	/** Um passo de animação: cristais giram 90° e o olho sobe ou desce. O cliente interpola suavemente. */
	private static boolean animate(ServerLevel sl, Altar a, Visuals v) {
		v.step++;
		double half = Math.toRadians(45 * (v.step % 8)); // meio ângulo do quatérnio (90° por passo)
		String start = "{start_interpolation:0,interpolation_duration:" + ANIM_TICKS + ",";
		boolean ok = command(sl, a.pos(), "data merge entity " + v.ring.getUUID() + " " + start
			+ transform(0f, Math.sin(half), Math.cos(half), 0.8f, 0.25f) + "}");
		return command(sl, a.pos(), "data merge entity " + v.eye.getUUID() + " " + start
			+ transform(v.step % 2 == 0 ? BOB : -BOB, 0, 1, 0.7f, 0.7f) + "}") && ok;
	}

	/** Para o teste automático: roda um passo de animação no altar. */
	static boolean animateOnce(ServerLevel sl, Altar a) {
		Visuals v = visuals(sl, a, false);
		return v != null && animate(sl, a, v);
	}

	/** Jogador que está com o menu de destinos deste altar aberto, ou null. Limpa travas de quem saiu ou fechou. */
	static ServerPlayer user(Altar a) {
		UUID id = USERS.get(a.key());
		if (id == null) return null;
		ServerPlayer p = server.getPlayerList().getPlayer(id);
		if (p != null && p.containerMenu instanceof AltarMenu menu && menu.isFor(a)) return p;
		USERS.remove(a.key());
		return null;
	}

	static void lock(Altar a, ServerPlayer p) {
		USERS.put(a.key(), p.getUUID());
		refreshEye(a, true);
	}

	static void unlock(Altar a, Player p) {
		if (USERS.remove(a.key(), p.getUUID())) refreshEye(a, false);
	}

	/** Troca o olho na hora de abrir/fechar o menu: depois de viajar não há mais ninguém perto para o tick trocar. */
	private static void refreshEye(Altar a, boolean inUse) {
		Visuals v = VIS.get(a.key());
		if (v != null && v.alive()) showEye(v.eye, inUse);
	}

	// ---------- colocar ----------

	private static void askName(ServerPlayer sp, ServerLevel sl, BlockHitResult hit, InteractionHand hand, ItemStack stack) {
		BlockPos clicked = hit.getBlockPos();
		BlockPos pos = sl.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(hit.getDirection());
		if (!sp.mayUseItemAt(pos, hit.getDirection(), stack) || !sl.mayInteract(sp, pos)) return;
		if (!hasRoom(sl, pos)) {
			say(sp, "O altar precisa de 3 blocos livres de altura.", ChatFormatting.RED);
			return;
		}
		if (!hasLevels(sp)) return;
		NameMenu.open(sp, new Pending(sl.dimension(), pos.immutable(), hand), "Nome do altar");
	}

	/**
	 * Chamado quando o jogador confirma o nome na bigorna. Devolve um aviso para reabrir a tela (nome inválido)
	 * ou null quando terminou (colocou o altar ou desistiu com mensagem no chat).
	 */
	static String finishPlacement(ServerPlayer sp, Pending p, String text) {
		ServerLevel sl = server == null ? null : server.getLevel(p.dim());
		if (store == null || sp.isRemoved() || sl == null || sp.level() != sl) return null;
		String name = text.trim();
		if (name.isEmpty()) return "Digite um nome";
		if (name.length() > NAME_MAX) return "Máximo de " + NAME_MAX + " letras";
		if (store.nameTaken(dim(sl), name)) return "\"" + name + "\" já existe";
		ItemStack stack = sp.getItemInHand(p.hand());
		if (!isAltarItem(stack)) {
			say(sp, "O altar não está mais na sua mão. Colocação cancelada.", ChatFormatting.RED);
			return null;
		}
		if (sp.distanceToSqr(Vec3.atCenterOf(p.pos())) > 8 * 8) {
			say(sp, "Você se afastou demais do local. Colocação cancelada.", ChatFormatting.RED);
			return null;
		}
		if (!hasRoom(sl, p.pos()) || !sl.isUnobstructed(Blocks.OBSIDIAN.defaultBlockState(), p.pos(), CollisionContext.empty())) {
			say(sp, "O local ficou ocupado. Colocação cancelada.", ChatFormatting.RED);
			return null;
		}
		if (!hasLevels(sp)) return null;
		if (placeAltar(sl, p.pos(), name) == null) {
			say(sp, "Não foi possível colocar o altar aqui.", ChatFormatting.RED);
			return null;
		}
		if (!sp.isCreative()) {
			stack.shrink(1);
			sp.giveExperienceLevels(-PLACE_LEVELS);
		}
		say(sp, "Altar \"" + name + "\" criado! Abra o altar e guarde ender pearls nele: cada viagem até aqui gasta " + COST + ".", ChatFormatting.GREEN);
		return null;
	}

	private static boolean hasLevels(ServerPlayer sp) {
		if (sp.isCreative() || sp.experienceLevel >= PLACE_LEVELS) return true;
		say(sp, "Colocar um altar custa " + PLACE_LEVELS + " níveis de experiência (você tem " + sp.experienceLevel + ").", ChatFormatting.RED);
		return false;
	}

	private static boolean hasRoom(ServerLevel sl, BlockPos pos) {
		return sl.getBlockState(pos).canBeReplaced() && sl.getBlockState(pos.above()).isAir() && sl.getBlockState(pos.above(2)).isAir();
	}

	/** Coloca a base e as entidades e registra o altar. Devolve null se o bloco não puder ser colocado. */
	static Altar placeAltar(ServerLevel sl, BlockPos pos, String name) {
		if (!sl.setBlock(pos, Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL)) return null;
		Altar altar = new Altar(dim(sl), name, pos);
		store.add(altar);
		spawnVisuals(sl, pos);
		sl.playSound(null, pos, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 1f, 1f);
		return altar;
	}

	static void removeAltar(ServerLevel sl, Altar altar) {
		ServerPlayer u = user(altar);
		if (u != null) u.closeContainer(); // ninguém mexe no estoque de um altar que não existe mais
		store.remove(altar);
		USERS.remove(altar.key());
		VIS.remove(altar.key());
		killVisuals(sl, altar.pos());
	}

	private static void killVisuals(ServerLevel sl, BlockPos pos) {
		String tag = tag(pos);
		for (Entity e : sl.getEntitiesOfClass(Entity.class, new AABB(pos).inflate(1, 4, 1), e -> e.entityTags().contains(tag))) e.discard();
	}

	/** Cria (ou recria, apagando a anterior) a decoração do altar. */
	static void spawnVisuals(ServerLevel sl, BlockPos pos) {
		killVisuals(sl, pos);
		String tags = "Tags:[\"tpaltar\",\"" + tag(pos) + "\",\"" + RUN_TAG + "\"]";
		double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;
		// Sem resource pack: coluna e tampo de pedra lisa e pérola flutuando. Com o pack (custom_model_data), as mesmas
		// entidades viram obelisco rúnico, anel de cristais e olho 3D; os modelos compensam estas escalas
		// (arte/gerar.py). São item_display porque o formato do block_display mudou entre a 26.2 e a 26.3.
		summon(sl, pos, "item_display", x, y + 1.5, z, tags + ",item:" + item("smooth_stone", "tpaltar_obelisk", "obelisk") + ","
			+ transform(0f, 0, 1, 0.5f, 1f));
		summon(sl, pos, "item_display", x, y + RING_Y, z, tags + ",item:" + item("smooth_stone", "tpaltar_ring", "shards") + ","
			+ transform(0f, 0, 1, 0.8f, 0.25f));
		summon(sl, pos, "item_display", x, y + EYE_Y, z, tags + ",item:" + item("ender_pearl", "tpaltar_eye", "eye_idle") + ","
			+ "teleport_duration:2,brightness:{sky:15,block:15}," + transform(0f, 0, 1, 0.7f, 0.7f));
		// Área clicável que cobre a coluna e o olho.
		summon(sl, pos, "interaction", x, y + 1, z, tags + ",width:0.9f,height:2.3f,response:1b");
	}

	/** Peça com a marca do pack (cmd) e, no singleplayer, com o modelo próprio do mod (ver ownModels). */
	private static String item(String id, String cmd, String own) {
		String model = ownModels() ? ",\"minecraft:item_model\":\"tpaltar:" + own + "\"" : "";
		return "{id:\"minecraft:" + id + "\",count:1,components:{\"minecraft:custom_model_data\":{strings:[\"" + cmd + "\"]}" + model + "}}";
	}

	/** Deslocamento vertical, giro em Y (quatérnio 0,qy,0,qw) e escala. */
	private static String transform(float ty, double qy, double qw, float side, float height) {
		return String.format(Locale.ROOT, "transformation:{left_rotation:[0f,%.5ff,0f,%.5ff],right_rotation:[0f,0f,0f,1f],"
			+ "translation:[0f,%sf,0f],scale:[%sf,%sf,%sf]}", qy, qw, ty, side, height, side);
	}

	private static void summon(ServerLevel sl, BlockPos pos, String type, double x, double y, double z, String nbt) {
		command(sl, pos, String.format(Locale.ROOT, "summon minecraft:%s %.4f %.4f %.4f {%s}", type, x, y, z, nbt));
	}

	/** Roda um comando como o próprio servidor (funciona com cheats desligados). Avisa no log se falhar. */
	private static boolean command(ServerLevel sl, BlockPos pos, String cmd) {
		boolean[] ok = {false};
		CommandSourceStack source = server.createCommandSourceStack().withLevel(sl).withPosition(Vec3.atCenterOf(pos))
			.withSuppressedOutput().withCallback((success, result) -> ok[0] = success);
		server.getCommands().performPrefixedCommand(source, cmd);
		if (!ok[0]) LOG.warn("Comando do altar falhou: {}", cmd);
		return ok[0];
	}

	private static String tag(BlockPos pos) {
		return "tpaltar_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
	}

	// ---------- usar ----------

	private static void useAltar(ServerPlayer sp, ServerLevel sl, Altar altar) {
		// O jogo pode avisar o mesmo clique duas vezes (bloco/entidade); ignora repetições em menos de 5 ticks.
		int now = server.getTickCount();
		Integer last = LAST_USE.put(sp.getUUID(), now);
		if (last != null && now - last < 5) return;

		ServerPlayer busy = user(altar);
		if (busy != null && busy != sp) {
			say(sp, "O altar \"" + altar.name + "\" está sendo usado por " + busy.getName().getString() + ". Aguarde.", ChatFormatting.YELLOW);
			return;
		}
		List<Altar> others = store.inDim(dim(sl));
		others.remove(altar);
		AltarMenu.open(sp, altar, others);
	}

	/** Estoque mudou pelo menu do altar. Ignora se o altar já foi removido. */
	static void setPearls(Altar a, int pearls) {
		if (store == null || store.get(a.dim, a.pos()) != a) return;
		a.pearls = Math.clamp(pearls, 0, MAX_PEARLS);
		store.markDirty();
	}

	static void teleport(ServerPlayer sp, Altar from, Altar to) {
		sp.closeContainer();
		if (store == null || !(sp.level() instanceof ServerLevel sl)) return;
		String dim = dim(sl);
		Altar src = store.get(dim, from.pos());
		Altar dst = store.get(dim, to.pos());
		if (src == null || sp.distanceToSqr(Vec3.atCenterOf(src.pos())) > 8 * 8) {
			say(sp, "Você precisa estar perto do altar para viajar.", ChatFormatting.RED);
			return;
		}
		if (dst == null || !sl.getBlockState(dst.pos()).is(Blocks.OBSIDIAN)) {
			if (dst != null) removeAltar(sl, dst);
			say(sp, "Esse altar não existe mais.", ChatFormatting.RED);
			return;
		}
		if (dst.pearls < COST) {
			say(sp, "O altar \"" + dst.name + "\" não tem ender pearls suficientes (" + dst.pearls + "/" + COST + ").", ChatFormatting.RED);
			return;
		}
		Vec3 spot = safeSpot(sl, dst.pos());
		if (spot == null) {
			say(sp, "O altar \"" + dst.name + "\" está cercado de blocos; não há onde chegar.", ChatFormatting.RED);
			return;
		}
		dst.pearls -= COST;
		store.markDirty();
		// Se alguém está com o menu do altar de destino aberto, atualiza o estoque mostrado.
		ServerPlayer atDst = user(dst);
		if (atDst != null && atDst.containerMenu instanceof AltarMenu menu) menu.loadPearls();
		// Saída: quem viaja ouve na hora (ainda está aqui); os outros jogadores por perto também.
		sl.playSound(null, sp.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 1f);
		portalBurst(sl, sp.position());
		sp.stopRiding();
		sp.teleportTo(spot.x, spot.y, spot.z);
		sp.resetFallDistance();
		// Chegada: som e partículas mandados junto com o teleporte chegam ao jogo antes de ele estar no destino
		// (longe demais para ouvir); espera uns ticks.
		ARRIVALS.put(sp.getUUID(), server.getTickCount() + ARRIVAL_DELAY);
		say(sp, "Bem-vindo a \"" + dst.name + "\". Restam " + dst.pearls / COST + " viagens para cá.", ChatFormatting.LIGHT_PURPLE);
	}

	private static void arrive(ServerPlayer p) {
		if (!(p.level() instanceof ServerLevel sl)) return;
		sl.playSound(null, p.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 1f);
		portalBurst(sl, p.position());
	}

	/** Nuvem de partículas roxas na altura do corpo do jogador. */
	private static void portalBurst(ServerLevel sl, Vec3 feet) {
		sl.sendParticles(ParticleTypes.PORTAL, feet.x, feet.y + 1, feet.z, 60, 0.4, 0.8, 0.4, 0.6);
	}

	/** Um bloco ao lado do altar com chão firme e 2 de altura livres, sem líquido. */
	static Vec3 safeSpot(ServerLevel sl, BlockPos base) {
		for (int dy : new int[] {0, 1, -1}) {
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos feet = base.relative(d).above(dy);
				BlockPos floor = feet.below();
				if (free(sl, feet) && free(sl, feet.above()) && !sl.getBlockState(floor).getCollisionShape(sl, floor).isEmpty()) {
					return new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
				}
			}
		}
		return null;
	}

	private static boolean free(ServerLevel sl, BlockPos pos) {
		return sl.getBlockState(pos).getCollisionShape(sl, pos).isEmpty() && sl.getFluidState(pos).isEmpty();
	}

	// ---------- utilidades ----------

	static String dim(Level level) {
		return level.dimension().identifier().toString();
	}

	static boolean isAltarItem(ItemStack stack) {
		if (!stack.is(Items.OBSIDIAN)) return false;
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().contains("tpaltar");
	}

	/** O mesmo item da receita (data/tpaltar/recipe/altar.json), para devolver ao quebrar. */
	static ItemStack altarItem() {
		ItemStack s = new ItemStack(Items.OBSIDIAN);
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("tpaltar", true);
		s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		s.set(DataComponents.CUSTOM_NAME, plain(ITEM_NAME, ChatFormatting.LIGHT_PURPLE));
		s.set(DataComponents.ITEM_MODEL, ownModels() ? model("altar") : Items.ENDER_EYE.components().get(DataComponents.ITEM_MODEL));
		s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return s;
	}

	static Identifier model(String name) {
		return Identifier.fromNamespaceAndPath("tpaltar", name);
	}

	/**
	 * Singleplayer: os resource packs do jogador ficam acima das texturas do .jar e podem trocar o olho do fim, a
	 * pérola ou a pedra lisa; então as peças do altar usam modelos do próprio mod (tpaltar:*), que nenhum pack troca.
	 * Servidor dedicado: o pack enviado pelo servidor já fica acima dos do jogador, e quem o recusa vê as peças vanilla.
	 */
	static boolean ownModels() {
		return server != null && !server.isDedicatedServer();
	}

	/** Altar craftado (a receita dá o modelo de olho do fim) ou de versão antiga: ganha o modelo próprio no singleplayer. */
	static void upgrade(ItemStack stack) {
		if (isAltarItem(stack) && !model("altar").equals(stack.get(DataComponents.ITEM_MODEL))) stack.set(DataComponents.ITEM_MODEL, model("altar"));
	}

	private static String readPackHash() {
		try (InputStream in = TpAltar.class.getResourceAsStream("/tpaltar/pack.sha1")) {
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

	/** Dono do mundo singleplayer: as texturas vêm do .jar. Os outros: só quem carregou o pack. */
	static boolean hasPack(ServerPlayer p) {
		return server.isSingleplayerOwner(p.nameAndId()) || PACK_OK.contains(p.getUUID());
	}

	/**
	 * Título de tela com o fundo "Pedra rúnica": o fundo é um caractere da fonte tpaltar:gui (arte/gerar.py), cercado
	 * de espaços negativos que o levam ao canto da tela e voltam para onde o texto começa. Sem o pack, título comum
	 * (senão apareceriam quadradinhos de caractere desconhecido).
	 */
	static Component guiTitle(ServerPlayer p, String background, String text) {
		return hasPack(p) ? themedTitle(background, text) : Component.literal(text);
	}

	static Component themedTitle(String background, String text) {
		JsonObject bg = new JsonObject();
		bg.addProperty("text", background);
		bg.addProperty("font", "tpaltar:gui");
		bg.addProperty("color", "white"); // o título vanilla é cinza escuro e escureceria o desenho
		JsonObject name = new JsonObject();
		name.addProperty("text", text);
		name.addProperty("color", "#E0C8FF");
		JsonArray parts = new JsonArray();
		parts.add(new JsonPrimitive("")); // raiz sem estilo: o nome não herda a fonte do fundo
		parts.add(bg);
		parts.add(name);
		return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, parts).result().orElse(Component.literal(text));
	}

	static MutableComponent plain(String text, ChatFormatting color) {
		return Component.literal(text).withStyle(st -> st.withItalic(false).withColor(color));
	}

	static void say(Player player, String text, ChatFormatting color) {
		if (player instanceof ServerPlayer sp) sp.sendSystemMessage(Component.literal(text).withStyle(color));
	}
}
