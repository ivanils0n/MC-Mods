package io.github.ivanils0n.smartchest;

import io.github.ivanils0n.smartchest.ChestStore.Chest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * O "estoque" de um Baú Central é só a soma dos baús ligados: nada é guardado no Baú Central. Pegar e guardar
 * mexem direto nos baús de verdade, então nenhum item pode duplicar ou sumir se o mod for removido.
 */
final class Storage {
	private Storage() {}

	/** Item igual (mesmo tipo e componentes: encantamentos, nome, dano...) para agrupar e procurar. */
	record Key(ItemStack stack) {
		@Override
		public boolean equals(Object o) {
			return o instanceof Key k && ItemStack.isSameItemSameComponents(stack, k.stack);
		}

		@Override
		public int hashCode() {
			return ItemStack.hashItemAndComponents(stack);
		}
	}

	/** Um tipo de item com o total somado de todos os baús. */
	static final class Entry {
		final ItemStack proto; // cópia com 1 unidade, só para comparar e mostrar
		long total;
		int chests;
		private int lastContainer = -1;

		Entry(ItemStack proto) {
			this.proto = proto;
		}
	}

	/** Baús ligados que existem e estão carregados (não carrega chunks). Remove vínculos de blocos que sumiram. */
	static final class Linked {
		final List<Container> containers = new ArrayList<>();
		int unloaded;
		int usedSlots, totalSlots;
	}

	static boolean isLinkable(BlockEntity be) {
		return be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity;
	}

	/** O baú clicado e, se for metade de um baú duplo, a outra metade (as duas entram juntas). */
	static List<BlockPos> group(ServerLevel sl, BlockPos pos) {
		BlockState st = sl.getBlockState(pos);
		if (st.getBlock() instanceof ChestBlock && st.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
			BlockPos other = ChestBlock.getConnectedBlockPos(pos, st);
			if (isLinkable(sl.getBlockEntity(other))) return List.of(pos.immutable(), other.immutable());
		}
		return List.of(pos.immutable());
	}

	static Linked linked(ServerLevel sl, Chest chest, ChestStore store) {
		Linked out = new Linked();
		boolean pruned = false;
		for (BlockPos p : chest.linkPositions()) {
			if (!sl.isLoaded(p)) {
				out.unloaded++;
				continue;
			}
			BlockEntity be = sl.getBlockEntity(p);
			if (!isLinkable(be)) {
				chest.unlink(p); // quebrado por explosão, comando etc.
				pruned = true;
				continue;
			}
			Container c = (Container) be;
			out.containers.add(c);
			out.totalSlots += c.getContainerSize();
		}
		if (pruned && store != null) store.markDirty();
		return out;
	}

	/**
	 * Todos os tipos de item dos baús, com o total de cada um (ordem: como aparecem nos baús). Na mesma passada conta
	 * os espaços ocupados ({@code usedSlots}).
	 */
	static List<Entry> scan(Linked linked) {
		List<Container> containers = linked.containers;
		linked.usedSlots = 0;
		Map<Key, Entry> map = new LinkedHashMap<>();
		for (int ci = 0; ci < containers.size(); ci++) {
			Container c = containers.get(ci);
			for (int i = 0; i < c.getContainerSize(); i++) {
				ItemStack s = c.getItem(i);
				if (s.isEmpty()) continue;
				linked.usedSlots++;
				Entry e = map.computeIfAbsent(new Key(s), k -> new Entry(s.copyWithCount(1)));
				e.total += s.getCount();
				if (e.lastContainer != ci) {
					e.lastContainer = ci;
					e.chests++;
				}
			}
		}
		return new ArrayList<>(map.values());
	}

	static int maxStack(ItemStack s) {
		return Math.max(1, s.getOrDefault(DataComponents.MAX_STACK_SIZE, 1));
	}

	/** Tira até {@code amount} itens iguais a {@code proto} dos baús. Devolve o que conseguiu tirar. */
	static ItemStack take(List<Container> containers, ItemStack proto, int amount) {
		int got = 0;
		for (Container c : containers) {
			for (int i = 0; i < c.getContainerSize() && got < amount; i++) {
				ItemStack s = c.getItem(i);
				if (s.isEmpty() || !ItemStack.isSameItemSameComponents(s, proto)) continue;
				got += c.removeItem(i, Math.min(amount - got, s.getCount())).getCount();
			}
			if (got >= amount) break;
		}
		return got == 0 ? ItemStack.EMPTY : proto.copyWithCount(got);
	}

	/**
	 * Guarda o que couber de {@code stack} nos baús: primeiro completa pilhas iguais, depois usa espaços vazios.
	 * Tira de {@code stack} o que foi guardado e devolve quantos foram. 0 = todos os baús estão cheios para esse item.
	 */
	static int insert(List<Container> containers, ItemStack stack) {
		int before = stack.getCount();
		for (Container c : containers) {
			boolean changed = false;
			for (int i = 0; i < c.getContainerSize() && !stack.isEmpty(); i++) {
				ItemStack s = c.getItem(i);
				if (s.isEmpty() || !ItemStack.isSameItemSameComponents(s, stack) || !c.canPlaceItem(i, stack)) continue;
				int n = Math.min(c.getMaxStackSize(s) - s.getCount(), stack.getCount());
				if (n <= 0) continue;
				s.grow(n);
				stack.shrink(n);
				changed = true;
			}
			if (changed) c.setChanged();
		}
		for (Container c : containers) {
			boolean changed = false;
			for (int i = 0; i < c.getContainerSize() && !stack.isEmpty(); i++) {
				if (!c.getItem(i).isEmpty() || !c.canPlaceItem(i, stack)) continue;
				c.setItem(i, stack.split(Math.min(c.getMaxStackSize(stack), stack.getCount())));
				changed = true;
			}
			if (changed) c.setChanged();
		}
		return before - stack.getCount();
	}

	/**
	 * Organiza os baús por categoria (ordem das abas): junta os itens iguais em pilhas cheias e começa cada categoria num
	 * baú novo quando há baús para isso; senão, as categorias ficam em sequência. Devolve se cada categoria ficou em baús
	 * próprios. O que não couber (só com pilhas acima do normal, feitas por comando) vai para {@code leftover}.
	 */
	static boolean organize(List<Container> containers, List<ItemStack> leftover) {
		Map<Key, long[]> totals = new LinkedHashMap<>();
		for (Container c : containers) {
			for (int i = 0; i < c.getContainerSize(); i++) {
				ItemStack s = c.getItem(i);
				if (s.isEmpty()) continue;
				totals.computeIfAbsent(new Key(s.copyWithCount(1)), k -> new long[1])[0] += s.getCount();
				c.setItem(i, ItemStack.EMPTY);
			}
		}
		List<Key> keys = new ArrayList<>(totals.keySet());
		keys.sort(Comparator.comparingInt((Key k) -> Categories.of(k.stack()))
			.thenComparing(k -> BuiltInRegistries.ITEM.getKey(k.stack().getItem()).toString()));
		List<ItemStack> stacks = new ArrayList<>();
		for (Key k : keys) {
			int max = maxStack(k.stack());
			for (long n = totals.get(k)[0]; n > 0; n -= max) stacks.add(k.stack().copyWithCount((int) Math.min(max, n)));
		}
		boolean each = place(containers, stacks, true, null);
		place(containers, stacks, each, leftover);
		for (Container c : containers) c.setChanged();
		return each;
	}

	/** Põe as pilhas em ordem nos baús ({@code out == null}: só confere se cabem). */
	private static boolean place(List<Container> containers, List<ItemStack> stacks, boolean fresh, List<ItemStack> out) {
		int ci = 0, slot = 0, prev = -1;
		for (ItemStack st : stacks) {
			int cat = Categories.of(st);
			if (fresh && cat != prev && slot > 0) {
				ci++;
				slot = 0;
			}
			prev = cat;
			while (ci < containers.size() && slot >= containers.get(ci).getContainerSize()) {
				ci++;
				slot = 0;
			}
			if (ci >= containers.size()) {
				if (out == null) return false;
				out.add(st);
				continue;
			}
			if (out != null) containers.get(ci).setItem(slot, st);
			slot++;
		}
		return true;
	}

	/** Quantos itens iguais a {@code proto} ainda cabem no inventário principal + hotbar do jogador. */
	static int space(Inventory inv, ItemStack proto) {
		int max = Math.min(inv.getMaxStackSize(proto), maxStack(proto));
		int free = 0;
		for (int i = 0; i < 36; i++) {
			ItemStack s = inv.getItem(i);
			if (s.isEmpty()) free += max;
			else if (ItemStack.isSameItemSameComponents(s, proto)) free += Math.max(0, max - s.getCount());
		}
		return free;
	}
}
