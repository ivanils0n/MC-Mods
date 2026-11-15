package io.github.ivanils0n.sacoender;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Tela do saco: o baú de 1 fileira do jogo (funciona sem mod no cliente). Os 9 espaços são os espaços 18 a 26 (a última
 * fileira) do próprio baú do fim do jogador (os mesmos objetos do jogo, sem cópia), então o que entra ou sai aqui
 * aparece no baú do fim na hora e é salvo pelo jogo junto com o jogador. Os espaços 0 a 17 nunca são tocados.
 */
final class BagMenu extends AbstractContainerMenu {
	static final int SLOTS = 9;
	/** Primeiro espaço do baú do fim que é do saco (a última fileira: 18 a 26). */
	static final int FIRST = 18;

	private final ServerPlayer owner;

	BagMenu(int id, Inventory inv, ServerPlayer owner) {
		super(MenuType.GENERIC_9x1, id);
		this.owner = owner;
		PlayerEnderChestContainer ender = owner.getEnderChestInventory();
		for (int i = 0; i < SLOTS; i++) {
			// O próprio saco não entra nos espaços do saco (clique, shift+clique, arrastar e teclas numéricas usam mayPlace).
			addSlot(new Slot(ender, FIRST + i, 8 + i * 18, 18) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return !SacoEnder.isBag(stack);
				}
			});
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 49 + row * 18));
		}
		for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 8 + col * 18, 107));
	}

	/** Shift+clique: do saco para o inventário, ou do inventário só para os 9 espaços do saco. */
	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem()) return ItemStack.EMPTY;
		ItemStack stack = slot.getItem();
		ItemStack copy = stack.copy();
		if (index < SLOTS) {
			if (!moveItemStackTo(stack, SLOTS, slots.size(), true)) return ItemStack.EMPTY;
		} else if (!moveItemStackTo(stack, 0, SLOTS, false)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
		else slot.setChanged();
		return copy;
	}

	/** Só o dono, vivo e conectado, mexe no próprio baú do fim. */
	@Override
	public boolean stillValid(Player player) {
		return player == owner && owner.isAlive() && !owner.hasDisconnected();
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		if (player == owner) {
			owner.getEnderChestInventory().setChanged();
			owner.level().playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 1.0f, 0.9f);
		}
	}
}
