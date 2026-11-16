package io.github.ivanils0n.spawninfo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** Opções do mod (config/spawninfo.json). As seeds ficam guardadas por mundo/servidor. */
public final class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/** Qual bloco é analisado: o que você está olhando ou onde você está. */
	public enum Target {LOOK, FEET}

	/** Mostrar o painel (a tecla J liga e desliga). */
	public boolean visible = true;
	public Target target = Target.LOOK;
	public boolean showSpot = true;
	public boolean showSlime = true;
	public boolean showHostile = true;
	public boolean showPassive = true;
	public boolean showAmbient = false;
	public boolean showLook = true;
	/** A chance cai conforme o limite de mobs enche (no limite, 0%). */
	public boolean capAdjust = true;
	/** Quantos mobs listar em cada grupo. */
	public int maxRows = 6;
	/** Até onde contar os mobs para onde você olha (blocos). */
	public int lookRange = 64;
	/** Abertura do cone de visão (graus, total). */
	public int lookAngle = 60;
	/** Escala do painel em %. */
	public int scale = 100;
	/** Opacidade do fundo do painel em %. */
	public int opacity = 60;
	/** Seed digitada em cada mundo/servidor (vazio: sem seed; no singleplayer usa a do mundo). */
	public Map<String, String> seeds = new LinkedHashMap<>();

	public static Config get = new Config();

	void sanitize() {
		if (target == null) target = Target.LOOK;
		if (seeds == null) seeds = new LinkedHashMap<>();
		maxRows = Math.clamp(maxRows, 1, 12);
		lookRange = Math.clamp(lookRange, 8, 128);
		lookAngle = Math.clamp(lookAngle, 10, 180);
		scale = Math.clamp(scale, 50, 150);
		opacity = Math.clamp(opacity, 0, 100);
	}

	static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("spawninfo.json");
	}

	public static void load() {
		Config c = null;
		Path f = file();
		if (Files.isRegularFile(f)) {
			try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
				c = GSON.fromJson(r, Config.class);
			} catch (Exception e) {
				SpawnInfo.LOG.warn("SpawnInfo: não deu para ler {} ({}); usando o padrão", f, e.toString());
			}
		}
		get = c == null ? new Config() : c;
		get.sanitize();
	}

	/** Grava num arquivo temporário e troca no fim: um travamento no meio nunca deixa o JSON pela metade. */
	public static void save() {
		Path f = file();
		try {
			Files.createDirectories(f.getParent());
			Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
			try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(get, w);
			}
			try {
				Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (java.nio.file.AtomicMoveNotSupportedException e) {
				Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			SpawnInfo.LOG.warn("SpawnInfo: não deu para salvar {}: {}", f, e.toString());
		}
	}
}
