package io.github.ivanils0n.smartchest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Baús Inteligentes do mundo e os baús ligados a cada um, salvos em <mundo>/smartchest.json, fora do save do
 * Minecraft: se o arquivo der problema, o mapa não é afetado.
 */
final class ChestStore {
	static final class Chest {
		String dim;
		int x, y, z;
		String facing = "north";
		List<int[]> links = new ArrayList<>();

		Chest() {}

		Chest(String dim, BlockPos pos, Direction facing) {
			this.dim = dim;
			this.x = pos.getX();
			this.y = pos.getY();
			this.z = pos.getZ();
			this.facing = facing.getName();
		}

		BlockPos pos() {
			return new BlockPos(x, y, z);
		}

		String key() {
			return ChestStore.key(dim, x, y, z);
		}

		Direction facing() {
			Direction d = Direction.byName(facing);
			return d == null || d.getAxis().isVertical() ? Direction.NORTH : d;
		}

		boolean isLinked(BlockPos p) {
			for (int[] l : links) if (l[0] == p.getX() && l[1] == p.getY() && l[2] == p.getZ()) return true;
			return false;
		}

		void link(BlockPos p) {
			if (!isLinked(p)) links.add(new int[] {p.getX(), p.getY(), p.getZ()});
		}

		boolean unlink(BlockPos p) {
			return links.removeIf(l -> l[0] == p.getX() && l[1] == p.getY() && l[2] == p.getZ());
		}

		List<BlockPos> linkPositions() {
			List<BlockPos> out = new ArrayList<>(links.size());
			for (int[] l : links) out.add(new BlockPos(l[0], l[1], l[2]));
			return out;
		}
	}

	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	private final Path file;
	private final Map<String, Chest> byKey = new LinkedHashMap<>();
	private boolean dirty;

	private ChestStore(Path file) {
		this.file = file;
	}

	static String key(String dim, int x, int y, int z) {
		return dim + "|" + x + "," + y + "," + z;
	}

	Chest get(String dim, BlockPos pos) {
		return byKey.get(key(dim, pos.getX(), pos.getY(), pos.getZ()));
	}

	Collection<Chest> all() {
		return byKey.values();
	}

	List<Chest> inDim(String dim) {
		List<Chest> out = new ArrayList<>();
		for (Chest c : byKey.values()) if (c.dim.equals(dim)) out.add(c);
		return out;
	}

	int size() {
		return byKey.size();
	}

	void add(Chest c) {
		byKey.put(c.key(), c);
		save();
	}

	void remove(Chest c) {
		byKey.remove(c.key());
		save();
	}

	/** Um baú vinculado foi quebrado: some de todos os Baús Inteligentes da dimensão. */
	void unlinkEverywhere(String dim, BlockPos pos) {
		for (Chest c : byKey.values()) if (c.dim.equals(dim) && c.unlink(pos)) dirty = true;
	}

	static ChestStore load(Path file) {
		ChestStore store = new ChestStore(file);
		if (!Files.exists(file)) return store;
		try {
			Chest[] list = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Chest[].class);
			if (list != null) {
				for (Chest c : list) {
					if (c == null || c.dim == null) continue;
					List<int[]> ok = new ArrayList<>();
					if (c.links != null) for (int[] l : c.links) if (l != null && l.length == 3) ok.add(l);
					c.links = ok;
					c.facing = c.facing().getName();
					store.byKey.put(c.key(), c);
				}
			}
		} catch (Exception e) {
			// Guarda uma cópia do arquivo ruim antes que o próximo save o substitua.
			Path backup = file.resolveSibling(file.getFileName() + ".corrompido-" + System.currentTimeMillis());
			SmartChest.LOG.error("smartchest.json ilegível; cópia salva em {}", backup, e);
			try {
				Files.copy(file, backup);
			} catch (IOException ignored) {
			}
		}
		return store;
	}

	/** Marca para gravar no próximo saveIfDirty (o mod chama a cada segundo e ao desligar). */
	void markDirty() {
		dirty = true;
	}

	void saveIfDirty() {
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
			SmartChest.LOG.error("Falha ao salvar {}", file, e);
		}
	}
}
