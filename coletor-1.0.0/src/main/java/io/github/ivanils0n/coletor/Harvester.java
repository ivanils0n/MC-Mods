package io.github.ivanils0n.coletor;

import io.github.ivanils0n.coletor.ColetorStore.Machine;
import io.github.ivanils0n.coletor.ColetorStore.Status;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * O trabalho do Coletor, a cada meio segundo: procura na área (quadrado em volta dele, da altura dele um bloco abaixo
 * a um acima) as plantas maduras, guarda tudo o que elas soltariam e replanta com uma das sementes colhidas. Depois usa
 * uma farinha de osso guardada numa planta ainda crescendo. Se a colheita não couber, a planta fica no pé.
 */
final class Harvester {
	static final int MAX_RADIUS = 5;
	static final int PERIOD = 10; // ticks entre uma rodada e outra

	private Harvester() {}

	/** Chamado todo tick; trabalha só a cada {@link #PERIOD} ticks (cada Coletor numa fase, para não somar picos). */
	static void tick(ServerLevel sl, Machine m, Container box, boolean powered) {
		if (powered) {
			m.status = Status.PAUSED;
			return;
		}
		if (m.status == Status.PAUSED) m.status = Status.WAITING;
		if (Math.floorMod(sl.getGameTime() + m.pos().hashCode(), PERIOD) != 0) return;
		work(sl, m, box);
	}

	/** Uma rodada: colhe tudo o que estiver maduro e usa até uma farinha de osso. */
	static void work(ServerLevel sl, Machine m, Container box) {
		BlockPos center = m.pos();
		int r = m.radius;
		boolean harvested = false, full = false;
		List<BlockPos> growing = new ArrayList<>();
		for (int dy = -1; dy <= 1; dy++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (dx == 0 && dz == 0 && dy == 0) continue;
					BlockPos p = center.offset(dx, dy, dz);
					if (!sl.isLoaded(p)) continue; // nunca carrega chunks
					BlockState st = sl.getBlockState(p);
					switch (harvest(sl, p, st, box)) {
						case DONE -> harvested = true;
						case NO_ROOM -> full = true;
						case GROWING -> growing.add(p.immutable());
						case NONE -> {}
					}
				}
			}
		}
		boolean grew = m.boneMeal && !growing.isEmpty() && useBoneMeal(sl, box, growing);
		m.status = full ? Status.FULL : harvested || grew ? Status.WORKING : Status.WAITING;
	}

	enum Result { NONE, DONE, NO_ROOM, GROWING }

	/** Tenta colher a planta em {@code p}. Pé de cana e cacto: só os blocos de cima (a base fica e volta a crescer). */
	static Result harvest(ServerLevel sl, BlockPos p, BlockState st, Container box) {
		Block b = st.getBlock();
		if (b instanceof CropBlock crop) {
			if (!crop.isMaxAge(st)) return Result.GROWING;
			return collect(sl, p, st, crop.getStateForAge(0), b.asItem(), box);
		}
		if (b instanceof NetherWartBlock) {
			if (st.getValue(NetherWartBlock.AGE) < 3) return Result.NONE; // farinha de osso não serve no fungo do Nether
			return collect(sl, p, st, st.setValue(NetherWartBlock.AGE, 0), Items.NETHER_WART, box);
		}
		if (b instanceof CocoaBlock) {
			if (st.getValue(CocoaBlock.AGE) < 2) return Result.GROWING;
			return collect(sl, p, st, st.setValue(CocoaBlock.AGE, 0), Items.COCOA_BEANS, box);
		}
		if (b instanceof SweetBerryBushBlock) {
			int age = st.getValue(SweetBerryBushBlock.AGE);
			if (age < 3) return Result.GROWING;
			// Como o jogador colhendo com a mão: 2 ou 3 frutas e o arbusto volta à idade 1 (sem quebrar).
			List<ItemStack> drops = List.of(new ItemStack(Items.SWEET_BERRIES, 2 + sl.getRandom().nextInt(2)));
			if (!fits(box, drops)) return Result.NO_ROOM;
			insertAll(box, drops);
			sl.setBlock(p, st.setValue(SweetBerryBushBlock.AGE, 1), Block.UPDATE_CLIENTS);
			sl.levelEvent(2001, p, Block.getId(st));
			return Result.DONE;
		}
		if (b instanceof StemBlock) return Result.GROWING;
		if ((st.is(Blocks.PUMPKIN) || st.is(Blocks.MELON)) && hasAttachedStem(sl, p)) {
			return collect(sl, p, st, Blocks.AIR.defaultBlockState(), null, box);
		}
		if ((st.is(Blocks.SUGAR_CANE) || st.is(Blocks.CACTUS)) && !sl.getBlockState(p.below()).is(b)) {
			return collectColumn(sl, p, b, box);
		}
		return Result.NONE;
	}

	/** Abóbora e melancia só são colhidas se ligadas a um caule (as de enfeite ficam). */
	private static boolean hasAttachedStem(ServerLevel sl, BlockPos fruit) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos s = fruit.relative(d);
			BlockState st = sl.getBlockState(s);
			if ((st.is(Blocks.ATTACHED_PUMPKIN_STEM) || st.is(Blocks.ATTACHED_MELON_STEM))
				&& s.relative(st.getValue(HorizontalDirectionalBlock.FACING)).equals(fruit)) return true;
		}
		return false;
	}

	/**
	 * Guarda o que a planta soltaria e troca por {@code replant}. Uma das sementes colhidas é usada para replantar; se a
	 * colheita não tiver semente, usa uma guardada; sem nenhuma, o lugar fica vazio.
	 */
	private static Result collect(ServerLevel sl, BlockPos p, BlockState st, BlockState replant, Item seed, Container box) {
		List<ItemStack> drops = new ArrayList<>();
		for (ItemStack s : Block.getDrops(st, sl, p, null)) if (!s.isEmpty()) drops.add(s.copy());
		boolean seedFromDrops = false;
		if (seed != null) {
			for (ItemStack s : drops) {
				if (s.is(seed)) {
					s.shrink(1);
					seedFromDrops = true;
					break;
				}
			}
			drops.removeIf(ItemStack::isEmpty);
		}
		if (!fits(box, drops)) return Result.NO_ROOM;
		insertAll(box, drops);
		boolean replanted = seed != null && (seedFromDrops || takeOne(box, seed));
		sl.levelEvent(2001, p, Block.getId(st)); // partículas e som de quebra, como o jogador colhendo
		sl.setBlock(p, replanted ? replant : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		return Result.DONE;
	}

	/** Cana e cacto: quebra de cima para baixo tudo acima da base (assim nada cai solto no chão). */
	private static Result collectColumn(ServerLevel sl, BlockPos base, Block b, Container box) {
		List<BlockPos> above = new ArrayList<>();
		for (BlockPos p = base.above(); sl.getBlockState(p).is(b) && above.size() < 32; p = p.above()) above.add(p);
		if (above.isEmpty()) return Result.NONE;
		List<ItemStack> drops = new ArrayList<>();
		for (BlockPos p : above) for (ItemStack s : Block.getDrops(sl.getBlockState(p), sl, p, null)) if (!s.isEmpty()) drops.add(s.copy());
		if (!fits(box, drops)) return Result.NO_ROOM;
		insertAll(box, drops);
		for (int i = above.size() - 1; i >= 0; i--) {
			BlockPos p = above.get(i);
			sl.levelEvent(2001, p, Block.getId(sl.getBlockState(p)));
			sl.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
		return Result.DONE;
	}

	/** Uma farinha de osso guardada numa planta da área ainda crescendo (escolhida ao acaso). */
	private static boolean useBoneMeal(ServerLevel sl, Container box, List<BlockPos> growing) {
		int slot = -1;
		for (int i = 0; i < box.getContainerSize(); i++) {
			if (box.getItem(i).is(Items.BONE_MEAL)) {
				slot = i;
				break;
			}
		}
		if (slot < 0) return false;
		ItemStack meal = box.getItem(slot);
		// Tenta algumas plantas: uma pode não aceitar (ex.: caule já crescido esperando espaço para o fruto).
		for (int tries = 0; tries < 4 && !growing.isEmpty(); tries++) {
			BlockPos p = growing.remove(sl.getRandom().nextInt(growing.size()));
			if (BoneMealItem.growCrop(meal, sl, p)) {
				sl.levelEvent(1505, p, 15); // partículas verdes e som da farinha
				box.setChanged();
				return true;
			}
		}
		return false;
	}

	// ---------- guardar no barril ----------

	/** Tira 1 item igual a {@code item} do barril (semente para replantar). */
	private static boolean takeOne(Container box, Item item) {
		for (int i = 0; i < box.getContainerSize(); i++) {
			ItemStack s = box.getItem(i);
			if (s.is(item)) {
				s.shrink(1);
				box.setChanged();
				return true;
			}
		}
		return false;
	}

	/** Tudo cabe no barril? (simula numa cópia: completa pilhas iguais, depois usa espaços vazios) */
	static boolean fits(Container box, List<ItemStack> items) {
		ItemStack[] copy = new ItemStack[box.getContainerSize()];
		for (int i = 0; i < copy.length; i++) copy[i] = box.getItem(i).copy();
		for (ItemStack it : items) {
			ItemStack rest = it.copy();
			merge(copy, rest);
			if (!rest.isEmpty()) return false;
		}
		return true;
	}

	/** Guarda de verdade (chame só depois de {@link #fits}): as pilhas iguais crescem no lugar, os vazios recebem pilhas novas. */
	static void insertAll(Container box, List<ItemStack> items) {
		ItemStack[] live = new ItemStack[box.getContainerSize()];
		for (int i = 0; i < live.length; i++) live[i] = box.getItem(i);
		for (ItemStack it : items) merge(live, it.copy());
		for (int i = 0; i < live.length; i++) if (box.getItem(i).isEmpty() && !live[i].isEmpty()) box.setItem(i, live[i]);
		box.setChanged();
	}

	private static void merge(ItemStack[] slots, ItemStack rest) {
		for (int i = 0; i < slots.length && !rest.isEmpty(); i++) if (!slots[i].isEmpty()) mergeInto(slots, i, rest, false);
		for (int i = 0; i < slots.length && !rest.isEmpty(); i++) if (slots[i].isEmpty()) mergeInto(slots, i, rest, true);
	}

	/** Passa o que couber de {@code rest} para o espaço i. Com {@code empty}, o espaço vazio vira uma pilha nova. */
	private static boolean mergeInto(ItemStack[] slots, int i, ItemStack rest, boolean empty) {
		ItemStack s = slots[i];
		if (s.isEmpty()) {
			if (!empty) return false;
			int n = Math.min(rest.getCount(), rest.getMaxStackSize());
			slots[i] = rest.copyWithCount(n);
			rest.shrink(n);
			return true;
		}
		if (!ItemStack.isSameItemSameComponents(s, rest)) return false;
		int n = Math.min(rest.getCount(), s.getMaxStackSize() - s.getCount());
		if (n <= 0) return false;
		s.grow(n);
		rest.shrink(n);
		return true;
	}
}
