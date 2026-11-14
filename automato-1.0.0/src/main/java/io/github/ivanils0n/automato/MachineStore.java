package io.github.ivanils0n.automato;

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
import net.minecraft.world.item.Item;

/**
 * Lista dos Autômatos do mundo, salva em <mundo>/automato.json, separada do save do Minecraft: se o arquivo der
 * problema, o mapa não é afetado (sobram ejetores comuns com as ferramentas dentro).
 */
final class MachineStore {
	static final class Machine {
		String dim;
		int x, y, z;
		int xp; // pontos de XP guardados (dos mobs mortos e dos blocos quebrados à frente), até Xp.MAX
		int range = 1; // alcance da quebra, de 1 a 5 blocos à frente (golpe de arma: sempre 3, como o jogador)

		// Estado de trabalho: só na memória (recomeça do zero ao reiniciar, como um jogador que acabou de entrar).
		transient int slot = -1;
		transient Item item;
		transient int charge; // ticks desde o último golpe ou troca de ferramenta (a barra de recarga da arma)
		transient BlockPos breaking;
		transient Object breakingState;
		transient float progress;
		transient int crack = -1;
		transient int pause; // depois de quebrar um bloco, 5 ticks parado (igual ao jogador)
		transient boolean shownOn;
		transient Item shownItem; // ferramenta mostrada saindo do arco (null = nenhuma)
		transient boolean shownFoil;
		transient int light = -1; // luz fixada no visual (bloco << 4 | céu); -1 = ainda não fixada
		transient int poke; // "batida" da ferramenta: 4 = avança, 1 = volta

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
			return MachineStore.key(dim, x, y, z);
		}
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private final Path file;
	private final Map<String, Machine> byKey = new LinkedHashMap<>();
	private final Map<String, List<Machine>> byDim = new HashMap<>();

	private MachineStore(Path file) {
		this.file = file;
	}

	static String key(String dim, int x, int y, int z) {
		return dim + "|" + x + "," + y + "," + z;
	}

	Machine get(String dim, BlockPos pos) {
		return byKey.get(key(dim, pos.getX(), pos.getY(), pos.getZ()));
	}

	/** Autômatos da dimensão (lista fixa, refeita só quando um entra ou sai). */
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

	static MachineStore load(Path file) {
		MachineStore store = new MachineStore(file);
		if (!Files.exists(file)) return store;
		try {
			Machine[] list = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Machine[].class);
			if (list != null) {
				for (Machine m : list) {
					if (m == null || m.dim == null) continue;
					m.slot = -1;
					m.crack = -1;
					m.range = Math.clamp(m.range, 1, Worker.MAX_RANGE);
					m.xp = Math.clamp(m.xp, 0, Xp.MAX);
					store.byKey.put(m.key(), m);
				}
			}
		} catch (Exception e) {
			Path backup = file.resolveSibling(file.getFileName() + ".corrompido-" + System.currentTimeMillis());
			Automato.LOG.error("automato.json ilegível; cópia salva em {}", backup, e);
			try {
				Files.copy(file, backup);
			} catch (IOException ignored) {
			}
		}
		return store;
	}

	private boolean dirty;

	/** Mudança pequena (alcance, XP): grava junto, no próximo flush (a cada 5 s e ao desligar o servidor). */
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
			Automato.LOG.error("Falha ao salvar {}", file, e);
		}
	}
}
