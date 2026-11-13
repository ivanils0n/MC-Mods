package io.github.ivanils0n.mochilas.storage;

import com.mojang.serialization.DynamicOps;
import io.github.ivanils0n.mochilas.Mochilas;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Inventário de cada mochila, guardado pelo ID da mochila (não pelo jogador) em
 * &lt;mundo&gt;/mochilas/&lt;id&gt;.dat. Só arquivos que mudaram são gravados, no fim do tick (gravação atômica).
 */
public final class BackpackStorage {
	private final MinecraftServer server;
	private final Path dir;
	private final Map<UUID, NonNullList<ItemStack>> cache = new HashMap<>();
	private final Set<UUID> dirty = new HashSet<>();
	/** Quem está com a mochila aberta agora: só uma pessoa por vez. */
	private final Map<UUID, UUID> openBy = new HashMap<>();

	public BackpackStorage(MinecraftServer server) {
		this.server = server;
		this.dir = server.getWorldPath(LevelResource.ROOT).resolve("mochilas");
	}

	public Path file(UUID id) {
		return dir.resolve(id + ".dat");
	}

	/** Lista viva de itens (tamanho = maior entre a capacidade pedida e o que já estava guardado). */
	public NonNullList<ItemStack> get(UUID id, int capacity) {
		NonNullList<ItemStack> list = cache.computeIfAbsent(id, this::read);
		if (list.size() < capacity) {
			NonNullList<ItemStack> bigger = NonNullList.withSize(capacity, ItemStack.EMPTY);
			for (int i = 0; i < list.size(); i++) bigger.set(i, list.get(i));
			cache.put(id, bigger);
			list = bigger;
		}
		return list;
	}

	public boolean exists(UUID id) {
		return cache.containsKey(id) || Files.exists(file(id));
	}

	public void markDirty(UUID id) {
		dirty.add(id);
	}

	public boolean tryOpen(UUID id, UUID player) {
		UUID cur = openBy.get(id);
		if (cur != null && !cur.equals(player)) return false;
		openBy.put(id, player);
		return true;
	}

	public void close(UUID id, UUID player) {
		openBy.remove(id, player);
	}

	public boolean isOpen(UUID id) {
		return openBy.containsKey(id);
	}

	/** Esquece o que está na memória (usado no autoteste para provar que lê do disco). */
	public void dropCache(UUID id) {
		flush();
		cache.remove(id);
	}

	public void flush() {
		if (dirty.isEmpty()) return;
		for (UUID id : dirty.toArray(UUID[]::new)) {
			NonNullList<ItemStack> list = cache.get(id);
			if (list != null) write(id, list);
		}
		dirty.clear();
	}

	private DynamicOps<Tag> ops() {
		return server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
	}

	private NonNullList<ItemStack> read(UUID id) {
		Path f = file(id);
		if (!Files.exists(f)) return NonNullList.withSize(0, ItemStack.EMPTY);
		try {
			CompoundTag root = NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap());
			int size = root.getIntOr("tamanho", 0);
			NonNullList<ItemStack> list = NonNullList.withSize(size, ItemStack.EMPTY);
			ListTag items = root.getListOrEmpty("itens");
			for (int i = 0; i < items.size(); i++) {
				CompoundTag e = items.getCompoundOrEmpty(i);
				int slot = e.getIntOr("espaco", -1);
				var parsed = ItemStack.CODEC.parse(ops(), e.getCompoundOrEmpty("item"));
				if (parsed.result().isEmpty()) {
					Mochilas.LOG.error("Item ilegível no espaço {} da mochila {}: {}", slot, id, parsed.error());
					continue;
				}
				ItemStack s = parsed.result().get();
				if (slot >= 0 && slot < size) list.set(slot, s);
			}
			return list;
		} catch (Exception e) {
			Mochilas.LOG.error("Não consegui ler a mochila {} ({}); guardando uma cópia e começando vazia", id, f, e);
			try {
				Files.copy(f, f.resolveSibling(id + ".dat.erro"), StandardCopyOption.REPLACE_EXISTING);
			} catch (Exception ignored) {
			}
			return NonNullList.withSize(0, ItemStack.EMPTY);
		}
	}

	private void write(UUID id, NonNullList<ItemStack> list) {
		try {
			Files.createDirectories(dir);
			CompoundTag root = new CompoundTag();
			root.putInt("tamanho", list.size());
			ListTag items = new ListTag();
			for (int i = 0; i < list.size(); i++) {
				ItemStack s = list.get(i);
				if (s.isEmpty()) continue;
				CompoundTag e = new CompoundTag();
				e.putInt("espaco", i);
				e.put("item", ItemStack.CODEC.encodeStart(ops(), s).getOrThrow());
				items.add(e);
			}
			root.put("itens", items);
			Path tmp = file(id).resolveSibling(id + ".dat.tmp");
			NbtIo.writeCompressed(root, tmp);
			try {
				Files.move(tmp, file(id), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (Exception e) {
				Files.move(tmp, file(id), StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (Exception e) {
			Mochilas.LOG.error("Não consegui salvar a mochila {}", id, e);
		}
	}

	public int used(UUID id, int capacity) {
		int n = 0;
		for (ItemStack s : get(id, capacity)) if (!s.isEmpty()) n++;
		return n;
	}
}
