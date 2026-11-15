package io.github.ivanils0n.chunkmap;

import io.github.ivanils0n.chunkmap.map.BiomeLayer;
import io.github.ivanils0n.chunkmap.map.TerrainLayer;
import io.github.ivanils0n.chunkmap.seed.SeedWorld;
import io.github.ivanils0n.chunkmap.seed.StructureFinder;
import io.github.ivanils0n.chunkmap.seed.StructureIcon;
import io.github.ivanils0n.chunkmap.seed.VanillaWorldgen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Tudo do mundo/servidor em que o jogador está: dados salvos, terreno explorado e a geração da seed. */
public final class WorldSession {
	public enum SeedStatus {NONE, LOADING, READY, UNSUPPORTED, ERROR}

	public final Path dir;
	public final Config.WorldData data;
	public final boolean singleplayer;
	private final Map<String, TerrainLayer> terrain = new HashMap<>();
	private final Map<String, TerrainLayer> caves = new HashMap<>();
	/** Modo caverna valendo agora (calculado a cada tick). */
	public boolean caveActive;
	private int caveCursor, caveY = Integer.MIN_VALUE;
	private long caveChunk = Long.MIN_VALUE;
	/** Chunks em volta do jogador, do mais perto para o mais longe (raio 6). */
	private static final int[][] SPIRAL;

	static {
		java.util.List<int[]> l = new java.util.ArrayList<>();
		for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) if (dx * dx + dz * dz <= 40) l.add(new int[]{dx, dz});
		l.sort(java.util.Comparator.comparingInt(a -> a[0] * a[0] + a[1] * a[1]));
		SPIRAL = l.toArray(new int[0][]);
	}
	private final Map<String, SeedLayers> seeds = new HashMap<>();
	/** Biomas destacados no mapa (ids). */
	public final Set<String> highlight = new HashSet<>();
	private final ArrayDeque<Long> chunkQueue = new ArrayDeque<>();
	private final Set<Long> queued = new HashSet<>();
	private long ticks;
	private boolean wasDead;
	private boolean dirty;

	/** A seed de uma dimensão: a geração, os biomas e as estruturas. */
	public static final class SeedLayers {
		final CompletableFuture<SeedWorld> future;
		BiomeLayer surface, caves;
		StructureFinder finder;

		SeedLayers(CompletableFuture<SeedWorld> future) {
			this.future = future;
		}

		public SeedWorld world() {
			return future.isDone() && !future.isCompletedExceptionally() ? future.join() : null;
		}

		public BiomeLayer biomes(boolean cave) {
			SeedWorld w = world();
			if (w == null) return null;
			if (cave) {
				if (caves == null) caves = new BiomeLayer(w, true);
				return caves;
			}
			if (surface == null) surface = new BiomeLayer(w, false);
			return surface;
		}

		public StructureFinder structures() {
			SeedWorld w = world();
			if (w == null) return null;
			if (finder == null) finder = new StructureFinder(w);
			return finder;
		}

		void close() {
			if (surface != null) surface.close();
			if (caves != null) caves.close();
			if (finder != null) finder.close();
			future.cancel(false);
		}
	}

	private WorldSession(Path dir, boolean singleplayer) {
		this.dir = dir;
		this.singleplayer = singleplayer;
		this.data = Config.loadWorld(dir);
	}

	/** Abre a sessão do mundo atual (singleplayer: pasta do mundo; multiplayer: endereço do servidor). */
	static WorldSession open(Minecraft mc) {
		IntegratedServer server = mc.getSingleplayerServer();
		String name;
		if (server != null) {
			Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
			name = "sp_" + root.getFileName();
		} else {
			ServerData sd = mc.getCurrentServer();
			name = "mp_" + (sd == null ? "desconhecido" : sd.isRealm() ? "realms_" + sd.name : sd.ip);
		}
		return new WorldSession(ChunkMap.gameDir().resolve("chunkmap").resolve(safe(name)), server != null);
	}

	private static String safe(String s) {
		String t = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
		return t.length() > 80 ? t.substring(0, 80) : t;
	}

	public static String dimId(ResourceKey<Level> dim) {
		return dim.identifier().toString();
	}

	public TerrainLayer terrain(ResourceKey<Level> dim) {
		return terrain.computeIfAbsent(dimId(dim), id -> new TerrainLayer(dir.resolve("terreno").resolve(safe(id.replace(':', '_')))));
	}

	/** Mapa das cavernas (o que o modo caverna já viu), separado do terreno da superfície. */
	public TerrainLayer caves(ResourceKey<Level> dim) {
		return caves.computeIfAbsent(dimId(dim), id -> new TerrainLayer(dir.resolve("cavernas").resolve(safe(id.replace(':', '_')))));
	}

	// ---------------------------------------------------------------- seed

	/** A seed usada agora (a do mundo local ou a digitada). */
	public OptionalLong seed() {
		if (singleplayer && data.autoSeed) {
			IntegratedServer s = Minecraft.getInstance().getSingleplayerServer();
			if (s != null) return OptionalLong.of(s.overworld().getSeed());
		}
		String t = data.seed == null ? "" : data.seed.strip();
		return t.isEmpty() ? OptionalLong.empty() : WorldOptions.parseSeed(t);
	}

	public SeedLayers seedLayers(ResourceKey<Level> dim) {
		if (seed().isEmpty()) return null;
		return seeds.computeIfAbsent(dimId(dim), id -> new SeedLayers(build(dim)));
	}

	public SeedStatus status(ResourceKey<Level> dim) {
		SeedLayers l = seedLayers(dim);
		if (l == null) return SeedStatus.NONE;
		if (!l.future.isDone()) return SeedStatus.LOADING;
		if (l.future.isCompletedExceptionally()) return SeedStatus.ERROR;
		return l.future.join() == null ? SeedStatus.UNSUPPORTED : SeedStatus.READY;
	}

	/** Seed, tipo de mundo ou altura mudou: recalcula tudo. */
	public void resetSeed() {
		for (SeedLayers l : seeds.values()) l.close();
		seeds.clear();
		highlight.clear();
		markDirty();
	}

	private CompletableFuture<SeedWorld> build(ResourceKey<Level> dim) {
		OptionalLong seed = seed();
		IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
		if (server != null) {
			ServerLevel level = server.getLevel(dim);
			if (level == null) return CompletableFuture.completedFuture(null);
			if (data.autoSeed) return CompletableFuture.supplyAsync(() -> SeedWorld.fromServer(level), Workers.SLOW);
			long s = seed.orElseThrow();
			return CompletableFuture.supplyAsync(() -> SeedWorld.create(level.registryAccess(), level.getChunkSource().getGenerator(), s,
					LevelHeightAccessor.create(level.getMinY(), level.getHeight()), server.getStructureManager()), Workers.SLOW);
		}
		long s = seed.orElseThrow();
		String preset = data.preset;
		return VanillaWorldgen.get().thenApplyAsync(data -> vanilla(data, preset, dim, s), Workers.SLOW);
	}

	/** A geração vanilla (sem servidor) de uma dimensão para a seed: o caminho do multiplayer. */
	public static SeedWorld vanilla(VanillaWorldgen.Data data, String preset, ResourceKey<Level> dim, long seed) {
		RegistryAccess access = data.access();
		ResourceKey<WorldPreset> key = switch (preset) {
			case "large_biomes" -> WorldPresets.LARGE_BIOMES;
			case "amplified" -> WorldPresets.AMPLIFIED;
			default -> WorldPresets.NORMAL;
		};
		WorldDimensions dims = access.lookupOrThrow(Registries.WORLD_PRESET).getValueOrThrow(key).createWorldDimensions();
		LevelStem stem = dims.dimensions().get(ResourceKey.create(Registries.LEVEL_STEM, dim.identifier()));
		if (stem == null) return null;
		DimensionType type = stem.type().value();
		return SeedWorld.create(access, stem.generator(), seed, LevelHeightAccessor.create(type.minY(), type.height()), data.templates());
	}

	public Set<StructureIcon> filters() {
		EnumSet<StructureIcon> set = EnumSet.noneOf(StructureIcon.class);
		for (String id : data.filters) {
			StructureIcon i = StructureIcon.byId(id);
			if (i != null) set.add(i);
		}
		return set;
	}

	// ---------------------------------------------------------------- jogo

	void chunkLoaded(LevelChunk chunk) {
		long k = chunk.getPos().pack();
		if (queued.add(k)) chunkQueue.add(k);
	}

	void tick(Minecraft mc) {
		ticks++;
		ClientLevel level = mc.level;
		if (level == null || mc.player == null) return;
		TerrainLayer t = terrain(level.dimension());
		// Chunks que chegaram do servidor: até 12 por tick.
		for (int i = 0; i < 12 && !chunkQueue.isEmpty(); i++) {
			long k = chunkQueue.poll();
			queued.remove(k);
			LevelChunk c = level.getChunkSource().getChunk(ChunkPos.getX(k), ChunkPos.getZ(k), false);
			if (c != null) t.update(level, c);
		}
		// Perto do jogador o terreno muda (construções, explosões): relê 5x5 chunks a cada 2 s.
		if (ticks % 40 == 0) {
			ChunkPos p = mc.player.chunkPosition();
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					LevelChunk c = level.getChunkSource().getChunk(p.x() + dx, p.z() + dz, false);
					if (c != null) t.update(level, c);
				}
			}
		}
		cave(mc, level);
		// Ponto de morte: um por dimensão, trocado a cada morte.
		boolean dead = mc.player.isDeadOrDying();
		if (dead && !wasDead) addDeathPoint(mc);
		wasDead = dead;
		if (ticks % 1200 == 0) save();
	}

	/** Liga/desliga o modo caverna e lê as cavernas em volta: 6 chunks por tick, do mais perto para o mais longe. */
	private void cave(Minecraft mc, ClientLevel level) {
		var player = mc.player;
		BlockPos p = player.blockPosition();
		// Debaixo da terra: abaixo do chão daquela coluna e sem céu acima (o céu "através da água" conta, para mergulhar no
		// mar não virar caverna; um lago dentro da caverna continua caverna).
		boolean under = level.dimensionType().hasCeiling()
				|| p.getY() < level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ()) - 3
				&& !level.canSeeSkyFromBelowWater(BlockPos.containing(player.getEyePosition()));
		caveActive = switch (Config.global.caveMode) {
			case OFF -> false;
			case ALWAYS -> true;
			case AUTO -> under;
		};
		if (!caveActive) return;
		long chunk = ChunkPos.containing(p).pack();
		// Mudou de chunk ou de altura: recomeça pelos chunks mais perto.
		if (chunk != caveChunk || Math.abs(p.getY() - caveY) >= 3) {
			caveChunk = chunk;
			caveY = p.getY();
			caveCursor = 0;
		}
		TerrainLayer t = caves(level.dimension());
		int cx = ChunkPos.getX(chunk), cz = ChunkPos.getZ(chunk);
		for (int n = 0; n < 6; n++) {
			int[] o = SPIRAL[caveCursor];
			caveCursor = (caveCursor + 1) % SPIRAL.length;
			LevelChunk c = level.getChunkSource().getChunk(cx + o[0], cz + o[1], false);
			if (c != null) t.updateCave(level, c, caveY);
		}
	}

	private void addDeathPoint(Minecraft mc) {
		String dim = dimId(mc.player.level().dimension());
		data.waypoints.removeIf(w -> w.death && w.dim.equals(dim));
		Waypoint w = new Waypoint(net.minecraft.network.chat.Component.translatable("chunkmap.waypoint.death").getString(), mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ(), dim, 0xFF8A1E1E);
		w.death = true;
		data.waypoints.add(w);
		markDirty();
	}

	public void markDirty() {
		dirty = true;
	}

	public void save() {
		for (TerrainLayer t : terrain.values()) t.save();
		for (TerrainLayer t : caves.values()) t.save();
		if (dirty) {
			dirty = false;
			Config.WorldData snapshot = Config.GSON.fromJson(Config.GSON.toJson(data), Config.WorldData.class);
			Workers.IO.execute(() -> Config.saveWorld(dir, snapshot));
		}
	}

	void close() {
		save();
		for (TerrainLayer t : terrain.values()) t.close();
		terrain.clear();
		for (TerrainLayer t : caves.values()) t.close();
		caves.clear();
		for (SeedLayers l : seeds.values()) l.close();
		seeds.clear();
	}
}
