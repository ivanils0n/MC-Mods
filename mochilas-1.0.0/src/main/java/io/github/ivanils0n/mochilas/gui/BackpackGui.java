package io.github.ivanils0n.mochilas.gui;

import eu.pb4.sgui.api.gui.SimpleGui;
import io.github.ivanils0n.mochilas.Mochilas;
import io.github.ivanils0n.mochilas.backpack.Backpacks;
import io.github.ivanils0n.mochilas.backpack.Tier;
import io.github.ivanils0n.mochilas.equipment.Equipment;
import io.github.ivanils0n.mochilas.storage.BackpackStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tela da mochila: igual a um baú do jogo, com exatamente a capacidade do nível (9, 18, 27 ou 54 espaços).
 * Os espaços são de verdade (Slot do jogo), então arrastar, shift-clique e teclas numéricas seguem as regras
 * do servidor. Toda mudança marca a mochila para salvar no fim do tick.
 */
public class BackpackGui extends SimpleGui {
	private static final Map<UUID, BackpackGui> OPEN = new HashMap<>();

	private final UUID id;
	private final BackpackContainer container;

	private BackpackGui(ServerPlayer player, UUID id, Tier tier, Component name, NonNullList<ItemStack> items) {
		super(type(tier.capacidade()), player, false);
		this.id = id;
		this.container = new BackpackContainer(items, tier.capacidade(), () -> Mochilas.storage().markDirty(id));
		// Nome (o da bigorna, se houver) e nível: "Mochila de Ouro · 27 espaços" ou "Minha bolsa · Ouro, 27 espaços".
		String info = name.getString().equals("Mochila de " + tier.nome) ? tier.capacidade() + " espaços" : tier.nome + ", " + tier.capacidade() + " espaços";
		this.setTitle(Component.empty().append(name).append(Component.literal(" · " + info).withStyle(ChatFormatting.DARK_GRAY)));
		for (int i = 0; i < tier.capacidade(); i++) {
			this.setSlot(i, new BackpackSlot(container, i));
		}
	}

	private static MenuType<?> type(int slots) {
		return switch (slots / 9) {
			case 1 -> MenuType.GENERIC_9x1;
			case 2 -> MenuType.GENERIC_9x2;
			case 3 -> MenuType.GENERIC_9x3;
			case 4 -> MenuType.GENERIC_9x4;
			case 5 -> MenuType.GENERIC_9x5;
			default -> MenuType.GENERIC_9x6;
		};
	}

	/** /backpack: abre a mochila equipada. */
	public static boolean openEquipped(ServerPlayer player) {
		ItemStack stack = Equipment.equipped(player);
		Tier tier = Backpacks.tier(stack);
		if (tier == null) {
			player.sendSystemMessage(Component.literal("Você não está com uma mochila equipada. Segure uma e clique com o botão direito.").withStyle(ChatFormatting.RED));
			return false;
		}
		UUID id = Backpacks.ensureId(stack);
		BackpackStorage st = Mochilas.storage();
		closeFor(player);
		if (!st.tryOpen(id, player.getUUID())) {
			player.sendSystemMessage(Component.literal("Essa mochila já está aberta por outro jogador.").withStyle(ChatFormatting.RED));
			return false;
		}
		NonNullList<ItemStack> items = st.get(id, tier.capacidade());
		// Capacidade diminuída no config: o que passou do limite volta para o jogador.
		for (int i = tier.capacidade(); i < items.size(); i++) {
			if (!items.get(i).isEmpty()) {
				player.getInventory().placeItemBackInInventory(items.get(i));
				items.set(i, ItemStack.EMPTY);
				st.markDirty(id);
			}
		}
		BackpackGui gui = new BackpackGui(player, id, tier, Backpacks.displayName(stack), items);
		OPEN.put(player.getUUID(), gui);
		if (!gui.open()) {
			OPEN.remove(player.getUUID());
			st.close(id, player.getUUID());
			return false;
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHEST_OPEN, SoundSource.PLAYERS, 0.5f, 1.3f);
		return true;
	}

	public static void closeFor(ServerPlayer player) {
		BackpackGui g = OPEN.get(player.getUUID());
		if (g != null) g.close();
	}

	public static boolean isOpen(ServerPlayer player) {
		return OPEN.containsKey(player.getUUID());
	}

	@Override
	public void onRemoved() {
		finish();
	}

	@Override
	public void onManualClose() {
		finish();
	}

	@Override
	public void onPlayerClose(boolean success) {
		finish();
	}

	private void finish() {
		if (OPEN.get(player.getUUID()) == this) OPEN.remove(player.getUUID());
		Mochilas.storage().markDirty(id);
		Mochilas.storage().flush();
		Mochilas.storage().close(id, player.getUUID());
	}

	/** Container que escreve direto na lista guardada da mochila. */
	static final class BackpackContainer extends SimpleContainer {
		private final NonNullList<ItemStack> backing;
		private final Runnable onChange;

		BackpackContainer(NonNullList<ItemStack> backing, int size, Runnable onChange) {
			super(size);
			this.backing = backing;
			this.onChange = onChange;
		}

		@Override
		public ItemStack getItem(int slot) {
			return backing.get(slot);
		}

		@Override
		public void setItem(int slot, ItemStack stack) {
			backing.set(slot, stack);
			setChanged();
		}

		@Override
		public ItemStack removeItem(int slot, int amount) {
			ItemStack s = backing.get(slot);
			if (s.isEmpty() || amount <= 0) return ItemStack.EMPTY;
			ItemStack out = s.split(amount);
			setChanged();
			return out;
		}

		@Override
		public ItemStack removeItemNoUpdate(int slot) {
			ItemStack s = backing.get(slot);
			backing.set(slot, ItemStack.EMPTY);
			return s;
		}

		@Override
		public boolean isEmpty() {
			for (int i = 0; i < getContainerSize(); i++) if (!backing.get(i).isEmpty()) return false;
			return true;
		}

		@Override
		public void setChanged() {
			onChange.run();
		}

		@Override
		public void clearContent() {
			for (int i = 0; i < getContainerSize(); i++) backing.set(i, ItemStack.EMPTY);
			setChanged();
		}

		@Override
		public boolean canPlaceItem(int slot, ItemStack stack) {
			return !Backpacks.isBackpack(stack);
		}

		@Override
		public boolean stillValid(Player player) {
			return true;
		}
	}

	/** Espaço que recusa mochilas (nenhuma mochila dentro de outra, nem dela mesma). */
	public static final class BackpackSlot extends Slot {
		public BackpackSlot(Container container, int index) {
			super(container, index, 0, 0);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return !Backpacks.isBackpack(stack);
		}
	}
}
