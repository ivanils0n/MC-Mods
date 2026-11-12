package io.github.ivanils0n.coppergolem;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;

/** Vínculos de cada golem (coppergolem.json na pasta do mundo, fora do save do Minecraft). */
final class Links {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	static final int MAX_SOURCES = 6, MAX_DESTS = 18;
	/** Origem e guardar só até essa distância (em blocos) do baú de cobre de recarga do golem. */
	static final int RANGE = 33;

	/** Os baús de um golem. Cada posição é {x, y, z}; vale uma dimensão só. */
	static final class Link {
		String dim;
		int[] refill; // baú de cobre com as barras de recarga (ou null)
		List<int[]> sources = new ArrayList<>(); // de onde o golem pega itens
		List<int[]> dests = new ArrayList<>(); // onde o golem guarda

		boolean has(List<int[]> list, BlockPos p) {
			return indexOf(list, p) >= 0;
		}

		static int indexOf(List<int[]> list, BlockPos p) {
			for (int i = 0; i < list.size(); i++) {
				int[] a = list.get(i);
				if (a[0] == p.getX() && a[1] == p.getY() && a[2] == p.getZ()) return i;
			}
			return -1;
		}

		static int[] arr(BlockPos p) {
			return new int[] {p.getX(), p.getY(), p.getZ()};
		}

		static BlockPos pos(int[] a) {
			return new BlockPos(a[0], a[1], a[2]);
		}

		boolean isEmpty() {
			return refill == null && sources.isEmpty() && dests.isEmpty();
		}

		int total() {
			return (refill == null ? 0 : 1) + sources.size() + dests.size();
		}
	}

	private final Path file;
	private final Map<String, Link> byGolem = new LinkedHashMap<>();
	private boolean dirty;

	private Links(Path file) {
		this.file = file;
	}

	Link get(UUID golem) {
		return byGolem.get(golem.toString());
	}

	/** O vínculo do golem, criado se não existir (com a dimensão dada). */
	Link getOrCreate(UUID golem, String dim) {
		Link l = byGolem.get(golem.toString());
		if (l == null || !dim.equals(l.dim)) {
			l = new Link(); // outra dimensão: recomeça (os baús antigos ficam longe)
			l.dim = dim;
			byGolem.put(golem.toString(), l);
		}
		dirty = true;
		return l;
	}

	/** Vínculo em texto (vai dentro do item do golem guardado) e de volta. */
	static String toJson(Link l) {
		return GSON.toJson(l);
	}

	void put(UUID golem, String json) {
		Link l = GSON.fromJson(json, Link.class);
		if (l == null || l.dim == null || l.sources == null || l.dests == null) return;
		byGolem.put(golem.toString(), l);
		dirty = true;
	}

	void remove(UUID golem) {
		if (byGolem.remove(golem.toString()) != null) dirty = true;
	}

	List<UUID> golems() {
		List<UUID> out = new ArrayList<>();
		for (String s : byGolem.keySet()) {
			try {
				out.add(UUID.fromString(s));
			} catch (IllegalArgumentException ignored) {
			}
		}
		return out;
	}

	int size() {
		return byGolem.size();
	}

	void markDirty() {
		dirty = true;
	}

	void saveIfDirty() {
		if (dirty) save();
	}

	static Links load(Path file) {
		Links links = new Links(file);
		if (!Files.exists(file)) return links;
		try {
			Map<?, ?> raw = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Map.class);
			if (raw != null) {
				for (String key : raw.keySet().stream().map(String::valueOf).toList()) {
					Link l = GSON.fromJson(GSON.toJson(raw.get(key)), Link.class);
					if (l == null || l.dim == null) continue;
					if (l.sources == null) l.sources = new ArrayList<>();
					if (l.dests == null) l.dests = new ArrayList<>();
					l.sources.removeIf(a -> a == null || a.length < 3);
					l.dests.removeIf(a -> a == null || a.length < 3);
					if (l.refill != null && l.refill.length < 3) l.refill = null;
					links.byGolem.put(key, l);
				}
			}
		} catch (Exception e) {
			Path backup = file.resolveSibling(file.getFileName() + ".corrompido-" + System.currentTimeMillis());
			GolemMod.LOG.error("coppergolem.json ilegível; cópia salva em {}", backup, e);
			try {
				Files.copy(file, backup);
			} catch (IOException ignored) {
			}
		}
		return links;
	}

	/** Grava num .tmp e troca de uma vez, para nunca deixar o arquivo pela metade se o servidor cair. */
	void save() {
		dirty = false;
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		try {
			Files.writeString(tmp, GSON.toJson(byGolem), StandardCharsets.UTF_8);
			try {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			GolemMod.LOG.error("Falha ao salvar {}", file, e);
		}
	}
}
