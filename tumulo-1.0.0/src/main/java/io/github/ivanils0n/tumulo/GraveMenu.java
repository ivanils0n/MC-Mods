package io.github.ivanils0n.tumulo;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Tela do túmulo: o baú de 6 fileiras do jogo (funciona sem mod no cliente), só para tirar. Os 54 espaços são os
 * próprios itens do túmulo, então dois jogadores com a tela aberta veem a mesma coisa.
 */
final class GraveMenu extends AbstractContainerMenu {
	final Grave grave;

	GraveMenu(int id, Inventory inv, Grave grave) {
		super(MenuType.GENERIC_9x6, id);
		this.grave = grave;
		for (int i = 0; i < Grave.SIZE; i++) {
			addSlot(new Slot(grave.items, i, 8 + i % 9 * 18, 18 + i / 9 * 18) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return false; // só tirar
				}
			});
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
		}
		for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 8 + col * 18, 198));
	}

	/** Shift+clique: do túmulo para o inventário. Do inventário não entra nada. */
	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		if (index >= Grave.SIZE) return ItemStack.EMPTY;
		Slot slot = slots.get(index);
		if (!slot.hasItem()) return ItemStack.EMPTY;
		ItemStack stack = slot.getItem();
		ItemStack copy = stack.copy();
		if (!moveItemStackTo(stack, Grave.SIZE, slots.size(), true)) return ItemStack.EMPTY;
		if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
		else slot.setChanged();
		return copy;
	}

	@Override
	public boolean stillValid(Player player) {
		return Tumulo.store != null && Tumulo.store.get(grave.id) == grave && player.isAlive()
			&& player instanceof ServerPlayer sp && Tumulo.dim(sp.level()).equals(grave.dim)
			&& player.distanceToSqr(grave.x, grave.y, grave.z) < 8 * 8;
	}
}
