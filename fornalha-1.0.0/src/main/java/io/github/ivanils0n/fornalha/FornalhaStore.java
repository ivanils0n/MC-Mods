package io.github.ivanils0n.fornalha;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Cascas e controladores do mundo, salvos em <mundo>/fornalha.json, separados do save do Minecraft: se o arquivo der
 * problema, o mapa não é afetado (sobram pedras lisas e um barril com os itens dentro).
 */
final class FornalhaStore {
	enum Status { PARADO, TRABALHANDO, PAUSADO, SEM_COMBUSTIVEL, CHEIO }

	/** Um controlador. A estrutura é o cubo 3x3x3 atrás da frente dele. */
	static final class Forno {
		String dim;
		int x, y, z;
		int facing = 2; // Direction 2D (0 sul, 1 oeste, 2 norte, 3 leste): para onde a frente olha
		boolean formado;
		int nivel; // 1 ferro, 2 ouro, 3 diamante, 4 netherite (0 = sem estrutura)
		long tanque; // combustível guardado, em ticks de queima
		double xp;
		int modo; // 0 tudo (fornalha), 1 minérios (alto-forno), 2 comida (defumador)
		boolean ligado = true;
		int progresso;

		// Só na memória.
		transient Status status = Status.PARADO;
		transient int tempo = 1; // ticks do ciclo atual
		transient int light = -1;
		transient boolean conferido; // estrutura conferida desde que o servidor ligou

		Forno() {}

		Forno(String dim, BlockPos pos, Direction facing) {
			this.dim = dim;
			this.x = pos.getX();
			this.y = pos.getY();
			this.z = pos.getZ();
			this.facing = facing.get2DDataValue();
		}

		BlockPos pos() {
			return new BlockPos(x, y, z);
		}

		Direction frente() {
			return Direction.from2DDataValue(facing);
		}

		/** Centro do cubo (onde fica o núcleo): o bloco atrás do controlador. */
		BlockPos centro() {
			return pos().relative(frente().getOpposite());
		}

		String key() {
			return FornalhaStore.key(dim, x, y, z);
		}
	}

	private static final class Dados {
		List<String> cascas = new ArrayList<>();
		List<Forno> fornos = new ArrayList<>();
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private final Path file;
	private final Set<String> cascas = new LinkedHashSet<>();
	private final Map<String, Forno> fornos = new LinkedHashMap<>();
	private final Map<String, List<BlockPos>> cascasPorDim = new HashMap<>();
	private final Map<String, List<Forno>> fornosPorDim = new HashMap<>();
	/** casca -> forno formado que a usa (só na memória; refeito a cada conferência). */
	final Map<String, Forno> dono = new HashMap<>();
	private boolean dirty;

	private FornalhaStore(Path file) {
		this.file = file;
	}

	static String key(String dim, int x, int y, int z) {
		return dim + "|" + x + "," + y + "," + z;
	}

	static String key(String dim, BlockPos p) {
		return key(dim, p.getX(), p.getY(), p.getZ());
	}

	boolean isCasca(String dim, BlockPos p) {
		return cascas.contains(key(dim, p));
	}

	Forno forno(String dim, BlockPos p) {
		return fornos.get(key(dim, p));
	}

	Forno donoDe(String dim, BlockPos p) {
		return dono.get(key(dim, p));
	}

	List<Forno> fornosEm(String dim) {
		return fornosPorDim.computeIfAbsent(dim, d -> {
			List<Forno> out = new ArrayList<>();
			for (Forno f : fornos.values()) if (f.dim.equals(d)) out.add(f);
			return List.copyOf(out);
		});
	}

	List<BlockPos> cascasEm(String dim) {
		return cascasPorDim.computeIfAbsent(dim, d -> {
			List<BlockPos> out = new ArrayList<>();
			String pre = d + "|";
			for (String k : cascas) {
				if (!k.startsWith(pre)) continue;
				String[] c = k.substring(pre.length()).split(",");
				out.add(new BlockPos(Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2])));
			}
			return List.copyOf(out);
		});
	}

	int fornos() {
		return fornos.size();
	}

	int cascas() {
		return cascas.size();
	}

	void addCasca(String dim, BlockPos p) {
		cascas.add(key(dim, p));
		cascasPorDim.remove(dim);
		save();
	}

	void removeCasca(String dim, BlockPos p) {
		cascas.remove(key(dim, p));
		dono.remove(key(dim, p));
		cascasPorDim.remove(dim);
		save();
	}

	void addForno(Forno f) {
		fornos.put(f.key(), f);
		fornosPorDim.remove(f.dim);
		save();
	}

	void removeForno(Forno f) {
		fornos.remove(f.key());
		fornosPorDim.remove(f.dim);
		dono.values().removeIf(o -> o == f);
		save();
	}

	static FornalhaStore load(Path file) {
		FornalhaStore store = new FornalhaStore(file);
		if (!Files.exists(file)) return store;
		try {
			Dados d = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Dados.class);
			if (d != null) {
				if (d.cascas != null) for (String k : d.cascas) if (k != null) store.cascas.add(k);
				if (d.fornos != null) {
					for (Forno f : d.fornos) {
						if (f == null || f.dim == null) continue;
						f.status = Status.PARADO;
						f.light = -1;
						f.tempo = 1;
						f.modo = Math.clamp(f.modo, 0, 2);
						f.tanque = Math.clamp(f.tanque, 0, Fundicao.TANQUE_MAX);
						store.fornos.put(f.key(), f);
					}
				}
			}
		} catch (Exception e) {
			Path backup = file.resolveSibling(file.getFileName() + ".corrompido-" + System.currentTimeMillis());
			Fornalha.LOG.error("fornalha.json ilegível; cópia salva em {}", backup, e);
			try {
				Files.copy(file, backup);
			} catch (IOException ignored) {
			}
		}
		return store;
	}

	/** Mudança pequena (combustível, XP, progresso): grava no próximo flush (a cada 5 s e ao desligar o servidor). */
	void markDirty() {
		dirty = true;
	}

	void flush() {
		if (dirty) save();
	}

	/** Grava num .tmp e troca de uma vez, para nunca deixar o arquivo pela metade se o servidor cair. */
	void save() {
		dirty = false;
		Dados d = new Dados();
		d.cascas.addAll(cascas);
		d.fornos.addAll(fornos.values());
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		try {
			Files.writeString(tmp, GSON.toJson(d), StandardCharsets.UTF_8);
			try {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			Fornalha.LOG.error("Falha ao salvar {}", file, e);
		}
	}
}
