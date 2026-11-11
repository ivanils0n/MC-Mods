package io.github.ivanils0n.coppergolem;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CopperChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;

/** Baús e barris: o que dá para ligar, o outro lado do baú duplo, a moldura que escolhe o item e guardar/tirar itens. */
final class Boxes {
	private Boxes() {}

	static boolean isBox(BlockEntity be) {
		return be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity;
	}

	static boolean isCopperChest(ServerLevel sl, BlockPos pos) {
		return sl.getBlockState(pos).getBlock() instanceof CopperChestBlock;
	}

	/** O bloco clicado e, se for metade de um baú duplo, a outra metade. */
	static List<BlockPos> group(ServerLevel sl, BlockPos pos) {
		BlockState st = sl.getBlockState(pos);
		if (st.getBlock() instanceof ChestBlock && st.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
			BlockPos other = ChestBlock.getConnectedBlockPos(pos, st);
			if (isBox(sl.getBlockEntity(other))) return List.of(pos.immutable(), other.immutable());
		}
		return List.of(pos.immutable());
	}

	/** Os recipientes de um bloco ligado (uma ou duas metades), só se o lugar está carregado. */
	static List<Container> containers(ServerLevel sl, BlockPos pos) {
		List<Container> out = new ArrayList<>();
		if (!sl.isLoaded(pos)) return out;
		for (BlockPos p : group(sl, pos)) if (sl.getBlockEntity(p) instanceof Container c && isBox(sl.getBlockEntity(p))) out.add(c);
		return out;
	}

	/** O item das molduras coladas neste baú (as duas metades, se duplo), ou vazio se não há moldura com item. */
	static ItemStack filter(ServerLevel sl, BlockPos pos) {
		for (BlockPos p : group(sl, pos)) {
			for (ItemFrame f : sl.getEntitiesOfClass(ItemFrame.class, new AABB(p).inflate(1.0))) {
				if (f.getItem().isEmpty()) continue;
				if (f.blockPosition().relative(f.getDirection().getOpposite()).equals(p)) return f.getItem();
			}
		}
		return ItemStack.EMPTY;
	}

	/** Quanto cabe desse item nesses recipientes (pilhas parciais iguais + espaços vazios). */
	static int room(List<Container> boxes, ItemStack stack) {
		int room = 0;
		for (Container c : boxes) {
			for (int i = 0; i < c.getContainerSize(); i++) {
				ItemStack in = c.getItem(i);
				if (in.isEmpty()) room += Math.min(stack.getMaxStackSize(), c.getMaxStackSize());
				else if (ItemStack.isSameItemSameComponents(in, stack)) room += Math.max(0, Math.min(in.getMaxStackSize(), c.getMaxStackSize()) - in.getCount());
			}
		}
		return room;
	}

	/** Guarda o máximo possível (primeiro completa pilhas iguais) e devolve o que sobrou. */
	static ItemStack insert(List<Container> boxes, ItemStack stack) {
		ItemStack rest = stack.copy();
		for (int pass = 0; pass < 2 && !rest.isEmpty(); pass++) {
			for (Container c : boxes) {
				for (int i = 0; i < c.getContainerSize() && !rest.isEmpty(); i++) {
					ItemStack in = c.getItem(i);
					int limit = Math.min(rest.getMaxStackSize(), c.getMaxStackSize());
					if (pass == 0 && !in.isEmpty() && ItemStack.isSameItemSameComponents(in, rest) && in.getCount() < limit) {
						int n = Math.min(rest.getCount(), limit - in.getCount());
						in.grow(n);
						rest.shrink(n);
						c.setChanged();
					} else if (pass == 1 && in.isEmpty()) {
						int n = Math.min(rest.getCount(), limit);
						c.setItem(i, rest.split(n));
						c.setChanged();
					}
				}
			}
		}
		return rest;
	}

	/** Tira até {@code max} itens iguais a {@code like} (ou do primeiro item que achar, se like for vazio). */
	static ItemStack take(List<Container> boxes, ItemStack like, int max) {
		ItemStack out = ItemStack.EMPTY;
		for (Container c : boxes) {
			for (int i = 0; i < c.getContainerSize() && (out.isEmpty() || out.getCount() < max); i++) {
				ItemStack in = c.getItem(i);
				if (in.isEmpty()) continue;
				if (!like.isEmpty() && !ItemStack.isSameItemSameComponents(in, like)) continue;
				if (out.isEmpty()) {
					out = in.split(Math.min(max, in.getCount()));
				} else if (ItemStack.isSameItemSameComponents(in, out)) {
					int n = Math.min(max - out.getCount(), in.getCount());
					in.shrink(n);
					out.grow(n);
				} else {
					continue;
				}
				c.setChanged();
			}
		}
		return out;
	}

	static int count(List<Container> boxes, ItemStack like) {
		int n = 0;
		for (Container c : boxes) {
			for (int i = 0; i < c.getContainerSize(); i++) {
				ItemStack in = c.getItem(i);
				if (!in.isEmpty() && ItemStack.isSameItemSameComponents(in, like)) n += in.getCount();
			}
		}
		return n;
	}

	/** Quantos itens da mesma categoria do criativo já estão nos recipientes (para organizar por categoria). */
	static int sameCategory(List<Container> boxes, ItemStack stack) {
		int cat = Categories.of(stack), n = 0;
		for (Container c : boxes) {
			for (int i = 0; i < c.getContainerSize(); i++) {
				ItemStack in = c.getItem(i);
				if (!in.isEmpty() && Categories.of(in) == cat) n++;
			}
		}
		return n;
	}
}
