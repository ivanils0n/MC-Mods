package io.github.ivanils0n.tpaltar;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;

/**
 * Lista de altares do mundo, salva em <mundo>/tpaltar.json, separada do save do Minecraft:
 * se o arquivo der problema, o mapa não é afetado.
 */
final class AltarStore {
	static final class Altar {
		String dim;
		String name;
		int x, y, z;
		int pearls;

		Altar() {}

		Altar(String dim, String name, BlockPos pos) {
			this.dim = dim;
			this.name = name;
			this.x = pos.getX();
			this.y = pos.getY();
			this.z = pos.getZ();
		}

		BlockPos pos() {
			return new BlockPos(x, y, z);
		}

		String key() {
			return AltarStore.key(dim, x, y, z);
		}
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private final Path file;
	private final Map<String, Altar> byKey = new LinkedHashMap<>();

	private AltarStore(Path file) {
		this.file = file;
	}

	static String key(String dim, int x, int y, int z) {
		return dim + "|" + x + "," + y + "," + z;
	}

	Altar get(String dim, BlockPos pos) {
		return byKey.get(key(dim, pos.getX(), pos.getY(), pos.getZ()));
	}

	/** Altares da dimensão, em ordem alfabética. */
	List<Altar> inDim(String dim) {
		List<Altar> out = new ArrayList<>();
		for (Altar a : byKey.values()) if (a.dim.equals(dim)) out.add(a);
		out.sort(Comparator.comparing(a -> a.name.toLowerCase()));
		return out;
	}

	boolean nameTaken(String dim, String name) {
		for (Altar a : byKey.values()) if (a.dim.equals(dim) && a.name.equalsIgnoreCase(name)) return true;
		return false;
	}

	int size() {
		return byKey.size();
	}

	void add(Altar a) {
		byKey.put(a.key(), a);
		save();
	}

	void remove(Altar a) {
		byKey.remove(a.key());
		save();
	}

	static AltarStore load(Path file) {
		AltarStore store = new AltarStore(file);
		if (!Files.exists(file)) return store;
		try {
			Altar[] list = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Altar[].class);
			if (list != null) {
				for (Altar a : list) {
					if (a == null || a.dim == null || a.name == null) continue;
					a.pearls = Math.clamp(a.pearls, 0, TpAltar.MAX_PEARLS);
					store.byKey.put(a.key(), a);
				}
			}
		} catch (Exception e) {
			// Guarda uma cópia do arquivo ruim antes que o próximo save o substitua.
			Path backup = file.resolveSibling(file.getFileName() + ".corrompido-" + System.currentTimeMillis());
			TpAltar.LOG.error("tpaltar.json ilegível; cópia salva em {}", backup, e);
			try {
				Files.copy(file, backup);
			} catch (IOException ignored) {
			}
		}
		return store;
	}

	/** Grava num .tmp e troca de uma vez, para nunca deixar o arquivo pela metade se o servidor cair. */
	void save() {
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		try {
			Files.writeString(tmp, GSON.toJson(byKey.values()), StandardCharsets.UTF_8);
			try {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			TpAltar.LOG.error("Falha ao salvar {}", file, e);
		}
	}
}
