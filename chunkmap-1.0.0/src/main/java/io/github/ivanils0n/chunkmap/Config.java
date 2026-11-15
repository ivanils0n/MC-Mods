package io.github.ivanils0n.chunkmap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Opções gerais (config/chunkmap.json) e dados de cada mundo (chunkmap/&lt;mundo&gt;/mundo.json). */
public final class Config {
	static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public enum Layer {AUTO, BIOMES, TERRAIN}

	/** Opções gerais, iguais em todos os mundos. */
	public static final class Global {
		public boolean minimap = true;
		public int minimapSize = 96;
		/** Pixels da tela (GUI) por bloco no minimapa. */
		public float minimapZoom = 1f;
		public Layer minimapLayer = Layer.AUTO;
		public boolean showCoords = true;
		public boolean showBiome = true;
		public boolean showTime = true;
		public boolean showMobs = true;
		public boolean inWorldWaypoints = true;

		void sanitize() {
			minimapSize = Math.clamp(minimapSize, 64, 160);
			if (!(minimapZoom >= 0.25f && minimapZoom <= 4f)) minimapZoom = 1f;
			if (minimapLayer == null) minimapLayer = Layer.AUTO;
		}
	}

	/** O que é salvo por mundo (singleplayer) ou servidor (multiplayer). */
	public static final class WorldData {
		/** Seed digitada pelo jogador (texto, como na tela de criar mundo). Vazio: sem seed (ou a do mundo local). */
		public String seed = "";
		/** No singleplayer, usa a seed do próprio mundo enquanto o jogador não digitar outra. */
		public boolean autoSeed = true;
		/** normal, large_biomes ou amplified. */
		public String preset = "normal";
		/** surface (superfície) ou caves (cavernas, y = 0). */
		public String biomeDepth = "surface";
		public boolean showBiomes = true;
		public boolean showTerrain = false;
		public boolean showGrid = false;
		public Set<String> filters = new LinkedHashSet<>(List.of(
				"village", "desert_pyramid", "jungle_temple", "swamp_hut", "igloo", "ocean_monument", "mansion",
				"stronghold", "ancient_city", "trial_chambers", "pillager_outpost", "spawn", "nether_fortress",
				"bastion", "end_city"));
		public List<Waypoint> waypoints = new ArrayList<>();

		void sanitize() {
			if (seed == null) seed = "";
			if (preset == null) preset = "normal";
			if (biomeDepth == null) biomeDepth = "surface";
			if (filters == null) filters = new LinkedHashSet<>();
			if (waypoints == null) waypoints = new ArrayList<>();
			waypoints.removeIf(w -> w == null || w.dim == null);
		}
	}

	public static Global global = new Global();

	private Config() {}

	static Path globalFile() {
		return ChunkMap.gameDir().resolve("config").resolve("chunkmap.json");
	}

	public static void loadGlobal() {
		Global g = read(globalFile(), Global.class);
		global = g == null ? new Global() : g;
		global.sanitize();
	}

	public static void saveGlobal() {
		write(globalFile(), global);
	}

	static WorldData loadWorld(Path dir) {
		WorldData d = read(dir.resolve("mundo.json"), WorldData.class);
		if (d == null) d = new WorldData();
		d.sanitize();
		return d;
	}

	static void saveWorld(Path dir, WorldData data) {
		write(dir.resolve("mundo.json"), data);
	}

	static <T> T read(Path file, Class<T> type) {
		if (!Files.isRegularFile(file)) return null;
		try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			return GSON.fromJson(r, type);
		} catch (Exception e) {
			ChunkMap.LOG.warn("ChunkMap: não deu para ler {} ({}); usando o padrão", file, e.toString());
			return null;
		}
	}

	/** Grava num arquivo temporário e troca no fim: um travamento no meio nunca deixa o JSON pela metade. */
	static void write(Path file, Object value) {
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(value, w);
			}
			try {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (java.nio.file.AtomicMoveNotSupportedException e) {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			ChunkMap.LOG.warn("ChunkMap: não deu para salvar {}: {}", file, e.toString());
		}
	}
}
