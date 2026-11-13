package io.github.ivanils0n.automato;

import io.github.ivanils0n.automato.MachineStore.Machine;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;

/**
 * Tela do Autômato (baú de 2 fileiras do jogo, então funciona sem mod no cliente):
 * 1ª fileira = os 9 espaços do ejetor, que só aceitam ferramentas e armas;
 * 2ª fileira = escolha do alcance da quebra (1 a 5 blocos). Com o resource pack, a tela ganha fundo próprio.
 */
final class ToolMenu extends AbstractContainerMenu {
	static final int FIRST_BUTTON = 11; // espaços 11..15 da tela = alcance 1..5

	private final DispenserBlockEntity be;
	private final Machine machine;
	private final SimpleContainer controls = new SimpleContainer(9);
	private final boolean pack;

	/** {@code pack}: o jogador tem o resource pack (placas com o número desenhado; sem ele, o número é a quantidade). */
	ToolMenu(int id, Inventory inv, DispenserBlockEntity be, Machine machine, boolean pack) {
		super(MenuType.GENERIC_9x2, id);
		this.be = be;
		this.machine = machine;
		this.pack = pack;
		be.startOpen(inv.player);
		for (int i = 0; i < 9; i++) {
			addSlot(new Slot(be, i, 8 + i * 18, 18) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return Worker.isUsable(stack);
				}
			});
		}
		for (int i = 0; i < 9; i++) {
			addSlot(new Slot(controls, i, 8 + i * 18, 36) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return false;
				}

				@Override
				public boolean mayPickup(Player player) {
					return false;
				}
			});
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 67 + row * 18));
		}
		for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 8 + col * 18, 125));
		refreshControls();
	}

	/** Botões de alcance: flechas com a quantidade = número de blocos; a escolhida é a flecha espectral. */
	private void refreshControls() {
		controls.setItem(9 - 9, ItemStack.EMPTY);
		controls.setItem(10 - 9, ItemStack.EMPTY);
		for (int n = 1; n <= Worker.MAX_RANGE; n++) {
			boolean on = n == machine.range;
			ItemStack b = piece(on ? Items.SPECTRAL_ARROW : Items.ARROW, "automato_range_" + n + (on ? "_on" : ""), pack ? 1 : n);
			b.set(DataComponents.CUSTOM_NAME, plain("Alcance: " + n + (n == 1 ? " bloco" : " blocos"), on ? ChatFormatting.GOLD : ChatFormatting.WHITE));
			b.set(DataComponents.LORE, new ItemLore(List.of(
				plain(on ? "Escolhido" : "Clique para escolher", on ? ChatFormatting.GREEN : ChatFormatting.YELLOW),
				plain("Até onde picareta, pá, machado e enxada", ChatFormatting.GRAY),
				plain("procuram o bloco à frente.", ChatFormatting.GRAY),
				plain("Espada e outras armas: 3 blocos, como o jogador.", ChatFormatting.GRAY))));
			controls.setItem(FIRST_BUTTON - 9 + n - 1, b);
		}
		controls.setItem(16 - 9, ItemStack.EMPTY);
		controls.setItem(17 - 9, ItemStack.EMPTY);
	}

	private static ItemStack piece(Item item, String cmd, int count) {
		ItemStack s = new ItemStack(item, count);
		s.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(cmd), List.of()));
		return s;
	}

	private static Component plain(String text, ChatFormatting color) {
		return Component.literal(text).withStyle(st -> st.withItalic(false).withColor(color));
	}

	@Override
	public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
		if (slotId >= 9 && slotId < 18) { // fileira de controle: nunca pega nem põe item
			int n = slotId - FIRST_BUTTON + 1;
			if (n >= 1 && n <= Worker.MAX_RANGE && n != machine.range && clickType == ContainerInput.PICKUP) {
				machine.range = n;
				Automato.store.markDirty();
				if (player instanceof ServerPlayer sp) {
					sp.level().playSound(null, sp.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.4f, 1.2f);
				}
			}
			refreshControls();
			broadcastFullState();
			return;
		}
		super.clicked(slotId, button, clickType, player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem() || (index >= 9 && index < 18)) return ItemStack.EMPTY;
		ItemStack stack = slot.getItem();
		ItemStack copy = stack.copy();
		if (index < 9) {
			if (!moveItemStackTo(stack, 18, slots.size(), true)) return ItemStack.EMPTY;
		} else if (!Worker.isUsable(stack) || !moveItemStackTo(stack, 0, 9, false)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
		else slot.setChanged();
		return copy;
	}

	@Override
	public boolean stillValid(Player player) {
		return Automato.store != null && !be.isRemoved() && Automato.store.get(machine.dim, machine.pos()) == machine && be.stillValid(player);
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		be.stopOpen(player);
	}
}
