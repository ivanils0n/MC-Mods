package io.github.ivanils0n.coletor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;

/**
 * Lista dos Coletores do mundo, salva em <mundo>/coletor.json, separada do save do Minecraft: se o arquivo der
 * problema, o mapa não é afetado (sobram barris comuns com a colheita dentro).
 */
final class ColetorStore {
	enum Status { WORKING, WAITING, PAUSED, FULL }

	static final class Machine {
		String dim;
		int x, y, z;
		int radius = 4; // área: 1 a 5 blocos para cada lado (3x3 a 11x11); padrão 9x9, como a água hidrata a terra arada
		boolean boneMeal = true; // usar a farinha de osso guardada

		// Só na memória.
		transient Status status = Status.WAITING;
		transient int light = -1; // luz fixada no visual (bloco << 4 | céu); -1 = ainda não fixada

		Machine() {}

		Machine(String dim, BlockPos pos) {
			this.dim = dim;
			this.x = pos.getX();
			this.y = pos.getY();
			this.z = pos.getZ();
		}

		BlockPos pos() {
			return new BlockPos(x, y, z);
		}

		String key() {
			return ColetorStore.key(dim, x, y, z);
		}
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private final Path file;
	private final Map<String, Machine> byKey = new LinkedHashMap<>();
	private final Map<String, List<Machine>> byDim = new HashMap<>();
	private boolean dirty;

	private ColetorStore(Path file) {
		this.file = file;
	}

	static String key(String dim, int x, int y, int z) {
		return dim + "|" + x + "," + y + "," + z;
	}

	Machine get(String dim, BlockPos pos) {
		return byKey.get(key(dim, pos.getX(), pos.getY(), pos.getZ()));
	}

	List<Machine> inDim(String dim) {
		return byDim.computeIfAbsent(dim, d -> {
			List<Machine> out = new ArrayList<>();
			for (Machine m : byKey.values()) if (m.dim.equals(d)) out.add(m);
			return List.copyOf(out);
		});
	}

	int size() {
		return byKey.size();
	}

	void add(Machine m) {
		byKey.put(m.key(), m);
		byDim.remove(m.dim);
		save();
	}

	void remove(Machine m) {
		byKey.remove(m.key());
		byDim.remove(m.dim);
		save();
	}

	static ColetorStore load(Path file) {
		ColetorStore store = new ColetorStore(file);
		if (!Files.exists(file)) return store;
		try {
			Machine[] list = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Machine[].class);
			if (list != null) {
				for (Machine m : list) {
					if (m == null || m.dim == null) continue;
					m.radius = Math.clamp(m.radius, 1, Harvester.MAX_RADIUS);
					m.status = Status.WAITING;
					m.light = -1;
					store.byKey.put(m.key(), m);
				}
			}
		} catch (Exception e) {
			Path backup = file.resolveSibling(file.getFileName() + ".corrompido-" + System.currentTimeMillis());
			Coletor.LOG.error("coletor.json ilegível; cópia salva em {}", backup, e);
			try {
				Files.copy(file, backup);
			} catch (IOException ignored) {
			}
		}
		return store;
	}

	/** Mudança pequena (área, farinha): grava no próximo flush (a cada 5 s e ao desligar o servidor). */
	void markDirty() {
		dirty = true;
	}

	void flush() {
		if (dirty) save();
	}

	/** Grava num .tmp e troca de uma vez, para nunca deixar o arquivo pela metade se o servidor cair. */
	void save() {
		dirty = false;
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		try {
			Files.writeString(tmp, GSON.toJson(byKey.values()), StandardCharsets.UTF_8);
			try {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			Coletor.LOG.error("Falha ao salvar {}", file, e);
		}
	}
}
