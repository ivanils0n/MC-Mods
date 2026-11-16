package io.github.ivanils0n.spawninfo;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;

/** SpawnInfo: chance de cada mob nascer no bloco que você olha, limite de mobs e mobs à sua frente. Só no cliente. */
public final class SpawnInfo implements ClientModInitializer {
	public static final Logger LOG = LoggerFactory.getLogger("spawninfo");

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("spawninfo", "spawninfo"));
	public static final KeyMapping TOGGLE = key("toggle", GLFW.GLFW_KEY_J);
	public static final KeyMapping SETTINGS = key("settings", GLFW.GLFW_KEY_U);

	/** O último cálculo (atualizado 4 vezes por segundo). */
	public static volatile SpawnCalc.Result result;
	public static volatile LookCounter.Counts look;
	/** Contagem exata do servidor do singleplayer (por categoria), atualizada 1 vez por segundo. */
	private static volatile Map<MobCategory, Integer> serverCounts;
	private static long ticks;

	private static KeyMapping key(String name, int code) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.spawninfo." + name, InputConstants.Type.KEYSYM, code, CATEGORY));
	}

	@Override
	public void onInitializeClient() {
		Config.load();
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath("spawninfo", "panel"), Hud::extract);
		ClientTickEvents.END_CLIENT_TICK.register(SpawnInfo::tick);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
			result = null;
			look = null;
			serverCounts = null;
			// Multiplayer: começa a ler as listas de mobs dos biomas já ao entrar (leva 1 a 3 s, em segundo plano).
			if (mc.getSingleplayerServer() == null) BiomeSpawns.vanilla();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
			result = null;
			look = null;
			serverCounts = null;
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> Config.save());
	}

	private static void tick(Minecraft mc) {
		boolean free = mc.gui.screen() == null && mc.player != null;
		while (TOGGLE.consumeClick()) {
			if (free) {
				Config.get.visible = !Config.get.visible;
				Config.save();
			}
		}
		while (SETTINGS.consumeClick()) {
			if (free) mc.gui.setScreen(new SettingsScreen(null));
		}
		if (mc.player == null || mc.level == null) return;
		ticks++;
		if (!Config.get.visible) return;
		if (ticks % 20 == 0) requestServerCounts(mc);
		if (ticks % 5 == 0) refresh(mc);
	}

	/** Recalcula agora (também usado pelo teste). */
	public static void refresh(Minecraft mc) {
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		if (player == null || level == null) return;
		try {
			BlockPos pos = player.blockPosition();
			boolean looking = false;
			if (Config.get.target == Config.Target.LOOK) {
				HitResult hit = player.pick(Math.max(Config.get.lookRange, 32), 1f, false);
				if (hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK) {
					pos = b.getBlockPos().relative(b.getDirection());
					looking = true;
				}
			}
			result = compute(mc, pos, looking);
			look = Config.get.showLook ? LookCounter.count(mc) : null;
		} catch (RuntimeException e) {
			// Nunca derruba o jogo por causa do painel; avisa uma vez a cada 10 s no máximo.
			if (ticks % 200 < 5) LOG.warn("SpawnInfo: erro no cálculo: {}", e.toString());
		}
	}

	/** O cálculo para um bloco qualquer (o teste chama direto). */
	public static SpawnCalc.Result compute(Minecraft mc, BlockPos pos, boolean looking) {
		ClientLevel level = mc.level;
		OptionalLong seed = seed(mc);
		Boolean slime = null;
		if (seed.isPresent()) {
			var chunk = SpawnCalc.chunk(pos);
			slime = SpawnCalc.isSlimeChunk(seed.getAsLong(), chunk.x(), chunk.z());
		}
		boolean fromWorld = seed.isPresent() && seedText(mc).isEmpty();
		MobSpawnSettings settings = BiomeSpawns.of(level.getBiome(pos)).orElse(null);
		boolean loading = settings == null && BiomeSpawns.loading();
		return SpawnCalc.compute(level, pos, looking, settings, loading, slime, fromWorld, surfaceSlimeChance(mc, pos),
				nearestPlayer(level, pos), counts(mc));
	}

	private static double nearestPlayer(ClientLevel level, BlockPos pos) {
		double best = -1;
		for (Player p : level.players()) {
			if (p.isSpectator()) continue;
			double d = p.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
			if (best < 0 || d < best) best = d;
		}
		return best < 0 ? -1 : Math.sqrt(best);
	}

	/**
	 * Chance do slime de pântano (lua). O valor do jogo não vem para o cliente no multiplayer: lá ele é estimado pela
	 * fase da lua, como era antes (metade do brilho da lua).
	 */
	private static double surfaceSlimeChance(Minecraft mc, BlockPos pos) {
		IntegratedServer server = mc.getSingleplayerServer();
		if (server != null) {
			try {
				ServerLevel sl = server.getLevel(mc.level.dimension());
				if (sl != null) return sl.environmentAttributes().getValue(EnvironmentAttributes.SURFACE_SLIME_SPAWN_CHANCE, pos);
			} catch (RuntimeException ignored) {
				// cai na estimativa
			}
		}
		try {
			int phase = mc.level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, pos).index();
			return DimensionType.MOON_BRIGHTNESS_PER_PHASE[Math.floorMod(phase, DimensionType.MOON_BRIGHTNESS_PER_PHASE.length)] * 0.5;
		} catch (RuntimeException e) {
			return 0.25;
		}
	}

	// ------------------------------------------------------------------ limite de mobs

	/** Mobs que contam para o limite, perto de você. Singleplayer: contagem do servidor; multiplayer: os que o cliente vê. */
	public static Map<MobCategory, Integer> counts(Minecraft mc) {
		Map<MobCategory, Integer> fromServer = serverCounts;
		if (mc.getSingleplayerServer() != null && fromServer != null) return fromServer;
		Map<MobCategory, Integer> out = new EnumMap<>(MobCategory.class);
		LocalPlayer player = mc.player;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof Mob mob) || !mob.isAlive()) continue;
			// Mobs com nome não somem e não contam para o limite (o jogo também tira os "persistentes").
			if (mob.hasCustomName()) continue;
			MobCategory cat = e.getType().getCategory();
			if (cat == MobCategory.MISC || !closeForSpawning(e, player)) continue;
			out.merge(cat, 1, Integer::sum);
		}
		return out;
	}

	/** O chunk do mob está a até 128 blocos de você (é o que o jogo usa para o limite de cada jogador). */
	private static boolean closeForSpawning(Entity e, Entity player) {
		BlockPos p = e.blockPosition();
		double cx = (p.getX() >> 4) * 16 + 8, cz = (p.getZ() >> 4) * 16 + 8;
		double dx = cx - player.getX(), dz = cz - player.getZ();
		return dx * dx + dz * dz < 128 * 128;
	}

	private static void requestServerCounts(Minecraft mc) {
		IntegratedServer server = mc.getSingleplayerServer();
		if (server == null || mc.player == null) {
			serverCounts = null;
			return;
		}
		var uuid = mc.player.getUUID();
		var dim = mc.level.dimension();
		server.execute(() -> {
			ServerLevel sl = server.getLevel(dim);
			Player sp = sl == null ? null : sl.getPlayerByUUID(uuid);
			if (sp == null) return;
			Map<MobCategory, Integer> out = new EnumMap<>(MobCategory.class);
			for (Entity e : sl.getAllEntities()) {
				if (!(e instanceof Mob mob) || !mob.isAlive()) continue;
				if (mob.isPersistenceRequired() || mob.requiresCustomPersistence()) continue;
				MobCategory cat = e.getType().getCategory();
				if (cat == MobCategory.MISC || !closeForSpawning(e, sp)) continue;
				out.merge(cat, 1, Integer::sum);
			}
			serverCounts = out;
		});
	}

	// ------------------------------------------------------------------ mundo e seed

	/** Nome do mundo (singleplayer: pasta) ou servidor (multiplayer: endereço), para guardar a seed. */
	public static String worldKey(Minecraft mc) {
		IntegratedServer server = mc.getSingleplayerServer();
		if (server != null) return "sp_" + server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
		ServerData sd = mc.getCurrentServer();
		return "mp_" + (sd == null ? "desconhecido" : sd.isRealm() ? "realms_" + sd.name : sd.ip).toLowerCase(Locale.ROOT);
	}

	/** Seed digitada para este mundo/servidor ("" se nenhuma). */
	public static String seedText(Minecraft mc) {
		String s = Config.get.seeds.get(worldKey(mc));
		return s == null ? "" : s.strip();
	}

	public static void setSeedText(Minecraft mc, String text) {
		String t = text == null ? "" : text.strip();
		if (t.isEmpty()) Config.get.seeds.remove(worldKey(mc));
		else Config.get.seeds.put(worldKey(mc), t);
		Config.save();
	}

	/** A seed usada: a digitada ou, no singleplayer, a do próprio mundo. */
	public static OptionalLong seed(Minecraft mc) {
		String t = seedText(mc);
		if (!t.isEmpty()) return WorldOptions.parseSeed(t);
		IntegratedServer server = mc.getSingleplayerServer();
		if (server != null) return OptionalLong.of(server.overworld().getSeed());
		return OptionalLong.empty();
	}
}
