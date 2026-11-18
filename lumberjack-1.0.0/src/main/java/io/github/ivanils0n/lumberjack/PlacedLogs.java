package io.github.ivanils0n.lumberjack;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Posições dos troncos colocados por jogadores, por dimensão, em <mundo>/lumberjack.dat (fora do save do Minecraft:
 * sem o mod, o mundo continua igual). Formato: "LJ01", nº de dimensões e, para cada uma, nome, quantidade e posições.
 */
final class PlacedLogs {
	private static final int MAGIC = 0x4C4A3031; // "LJ01"

	private final Path file;
	private final Map<String, LongOpenHashSet> byDim = new HashMap<>();
	private boolean dirty;

	private PlacedLogs(Path file) {
		this.file = file;
	}

	static String dim(Level level) {
		return level.dimension().identifier().toString();
	}

	boolean contains(Level level, BlockPos pos) {
		LongOpenHashSet s = byDim.get(dim(level));
		return s != null && s.contains(pos.asLong());
	}

	void add(Level level, BlockPos pos) {
		if (byDim.computeIfAbsent(dim(level), k -> new LongOpenHashSet()).add(pos.asLong())) dirty = true;
	}

	/** Devolve true se a posição estava marcada. */
	boolean remove(Level level, BlockPos pos) {
		LongOpenHashSet s = byDim.get(dim(level));
		if (s == null || !s.remove(pos.asLong())) return false;
		dirty = true;
		return true;
	}

	int size() {
		int n = 0;
		for (LongOpenHashSet s : byDim.values()) n += s.size();
		return n;
	}

	static PlacedLogs load(Path file) {
		PlacedLogs p = new PlacedLogs(file);
		if (!Files.exists(file)) return p;
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
			if (in.readInt() != MAGIC) throw new IOException("arquivo desconhecido");
			int dims = in.readInt();
			for (int d = 0; d < dims; d++) {
				String name = in.readUTF();
				int n = in.readInt();
				LongOpenHashSet s = new LongOpenHashSet(Math.max(16, n));
				for (int i = 0; i < n; i++) s.add(in.readLong());
				p.byDim.put(name, s);
			}
		} catch (Exception e) {
			// Não apaga: guarda uma cópia para não perder as marcações.
			Lumberjack.LOG.error("Não foi possível ler {}; uma cópia foi guardada como lumberjack.dat.bad", file, e);
			try {
				Files.copy(file, file.resolveSibling("lumberjack.dat.bad"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
			}
			p.byDim.clear();
		}
		return p;
	}

	void flush() {
		if (dirty) save();
	}

	void save() {
		dirty = false;
		Path tmp = file.resolveSibling("lumberjack.dat.tmp");
		try {
			try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)))) {
				out.writeInt(MAGIC);
				out.writeInt(byDim.size());
				for (Map.Entry<String, LongOpenHashSet> e : byDim.entrySet()) {
					out.writeUTF(e.getKey());
					out.writeInt(e.getValue().size());
					for (LongIterator it = e.getValue().iterator(); it.hasNext(); ) out.writeLong(it.nextLong());
				}
			}
			try {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException atomicFailed) {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			dirty = true;
			Lumberjack.LOG.error("Não foi possível gravar {}", file, e);
		}
	}
}
