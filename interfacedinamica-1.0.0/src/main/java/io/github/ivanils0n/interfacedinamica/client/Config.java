package io.github.ivanils0n.interfacedinamica.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.ivanils0n.interfacedinamica.InterfaceDinamica;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Ajustes do cliente (config/interfacedinamica.json). */
public final class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("interfacedinamica.json");
	public static Config INSTANCE = new Config();

	public boolean enabled = true;
	/** 0 lenta, 1 normal, 2 rápida */
	public int speed = 1;
	/** 0 automático, 1 direita, 2 esquerda */
	public int side = 0;
	/** 0 perto, 1 normal, 2 longe */
	public int distance = 1;
	public boolean poses = true;
	public boolean flight = true;
	public boolean light = true;
	public boolean effects = true;
	public boolean inventory = true;
	public boolean chests = true;
	public boolean crafting = true;
	public boolean furnaces = true;
	public boolean others = true;

	public int durationTicks() {
		return switch (speed) {
			case 0 -> 16;
			case 2 -> 6;
			default -> 10;
		};
	}

	public float cameraDistance() {
		return switch (distance) {
			case 0 -> 2.4f;
			case 2 -> 4.2f;
			default -> 3.2f;
		};
	}

	public static void load() {
		try {
			if (Files.exists(FILE)) {
				try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
					Config c = GSON.fromJson(r, Config.class);
					if (c != null) INSTANCE = c;
				}
			}
		} catch (Exception e) {
			InterfaceDinamica.LOG.warn("Não deu para ler {}: {}", FILE, e.toString());
		}
		INSTANCE.speed = Math.clamp(INSTANCE.speed, 0, 2);
		INSTANCE.side = Math.clamp(INSTANCE.side, 0, 2);
		INSTANCE.distance = Math.clamp(INSTANCE.distance, 0, 2);
	}

	public static void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
				GSON.toJson(INSTANCE, w);
			}
		} catch (Exception e) {
			InterfaceDinamica.LOG.warn("Não deu para salvar {}: {}", FILE, e.toString());
		}
	}
}
