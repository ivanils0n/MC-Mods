package io.github.ivanils0n.coletor;

import io.github.ivanils0n.coletor.ColetorStore.Machine;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;

/**
 * Tela do Coletor (baú de 4 fileiras do jogo, então funciona sem mod no cliente):
 * fileiras 1 a 3 = os 27 espaços do barril (a colheita, sementes e a farinha de osso);
 * fileira 4 = situação, área (3×3 a 11×11) e liga/desliga a farinha de osso.
 */
final class ColetorMenu extends AbstractContainerMenu {
	static final int BOX = 27;
	static final int STATUS = 27, FIRST_AREA = 29, BONE_MEAL = 35; // espaços 29..33 = raio 1..5

	private final BarrelBlockEntity be;
	private final Machine machine;
	private final SimpleContainer controls = new SimpleContainer(9);
	private final boolean pack; // o jogador tem o resource pack: botões com ícone próprio (item_model coletor:*)
	private ColetorStore.Status shownStatus;

	ColetorMenu(int id, Inventory inv, BarrelBlockEntity be, Machine machine, boolean pack) {
		super(MenuType.GENERIC_9x4, id);
		this.pack = pack;
		this.be = be;
		this.machine = machine;
		be.startOpen(inv.player);
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) addSlot(new Slot(be, col + row * 9, 8 + col * 18, 18 + row * 18));
		}
		for (int i = 0; i < 9; i++) {
			addSlot(new Slot(controls, i, 8 + i * 18, 72) {
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
			for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 103 + row * 18));
		}
		for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 8 + col * 18, 161));
		refreshControls();
	}

	private void refreshControls() {
		shownStatus = machine.status;
		controls.setItem(STATUS - BOX, statusIcon());
		for (int r = 1; r <= Harvester.MAX_RADIUS; r++) {
			boolean on = r == machine.radius;
			int side = r * 2 + 1;
			ItemStack b = icon(new ItemStack(Items.GLASS_PANE, side), "area_" + side + (on ? "_on" : ""));
			b.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, on && !pack); // sem o pack, a escolhida brilha
			b.set(DataComponents.CUSTOM_NAME, plain("Área: " + side + "×" + side, on ? ChatFormatting.GREEN : ChatFormatting.WHITE));
			b.set(DataComponents.LORE, new ItemLore(List.of(
				plain(on ? "Escolhida" : "Clique para escolher", on ? ChatFormatting.GREEN : ChatFormatting.YELLOW),
				plain(r + (r == 1 ? " bloco" : " blocos") + " para cada lado do Coletor,", ChatFormatting.GRAY),
				plain("da altura dele, 1 abaixo e 1 acima.", ChatFormatting.GRAY))));
			controls.setItem(FIRST_AREA - BOX + r - 1, b);
		}
		ItemStack meal = icon(new ItemStack(machine.boneMeal ? Items.BONE_MEAL : Items.BONE), machine.boneMeal ? "meal_on" : "meal_off");
		meal.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, machine.boneMeal && !pack);
		meal.set(DataComponents.CUSTOM_NAME, plain("Farinha de osso: " + (machine.boneMeal ? "usar" : "não usar"),
			machine.boneMeal ? ChatFormatting.GREEN : ChatFormatting.RED));
		meal.set(DataComponents.LORE, new ItemLore(List.of(
			plain("Clique para " + (machine.boneMeal ? "parar de usar" : "usar"), ChatFormatting.YELLOW),
			plain("Com farinha de osso guardada, o Coletor", ChatFormatting.GRAY),
			plain("aduba uma planta da área a cada meio segundo.", ChatFormatting.GRAY))));
		controls.setItem(BONE_MEAL - BOX, meal);
	}

	private ItemStack statusIcon() {
		Item item;
		String title, line, model = "status_" + machine.status.name().toLowerCase(java.util.Locale.ROOT);
		ChatFormatting color;
		switch (machine.status) {
			case WORKING -> { item = Items.WHEAT; title = "Colhendo"; line = "Colhe e replanta o que estiver maduro."; color = ChatFormatting.GREEN; }
			case PAUSED -> { item = Items.REDSTONE; title = "Pausado"; line = "Desliga com sinal de redstone (alavanca, botão...)."; color = ChatFormatting.RED; }
			case FULL -> { item = Items.BARREL; title = "Cheio"; line = "A colheita não cabe: tire itens ou ligue um funil embaixo."; color = ChatFormatting.GOLD; }
			default -> { item = Items.WHEAT_SEEDS; title = "Esperando crescer"; line = "Nada maduro na área agora."; color = ChatFormatting.YELLOW; }
		}
		ItemStack s = icon(new ItemStack(item), model);
		s.set(DataComponents.CUSTOM_NAME, plain(title, color));
		s.set(DataComponents.LORE, new ItemLore(List.of(plain(line, ChatFormatting.GRAY))));
		return s;
	}

	/** Com o pack: o ícone desenhado (arte/tela.py), 1 só (o número já está na placa). Sem: o item do jogo. */
	private ItemStack icon(ItemStack s, String model) {
		if (!pack) return s;
		s.setCount(1);
		s.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("coletor", model));
		return s;
	}

	private static Component plain(String text, ChatFormatting color) {
		return Component.literal(text).withStyle(st -> st.withItalic(false).withColor(color));
	}

	@Override
	public void broadcastChanges() {
		if (machine.status != shownStatus) refreshControls(); // a situação muda com a tela aberta
		super.broadcastChanges();
	}

	@Override
	public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
		if (slotId >= BOX && slotId < BOX + 9) { // fileira de controle: nunca pega nem põe item
			if (clickType == ContainerInput.PICKUP) {
				int r = slotId - FIRST_AREA + 1;
				boolean changed = false;
				if (r >= 1 && r <= Harvester.MAX_RADIUS && r != machine.radius) {
					machine.radius = r;
					changed = true;
				} else if (slotId == BONE_MEAL) {
					machine.boneMeal = !machine.boneMeal;
					changed = true;
				}
				if (changed) {
					Coletor.store.markDirty();
					if (player instanceof ServerPlayer sp) {
						sp.level().playSound(null, sp.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.4f, 1.2f);
					}
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
		if (!slot.hasItem() || (index >= BOX && index < BOX + 9)) return ItemStack.EMPTY;
		ItemStack stack = slot.getItem();
		ItemStack copy = stack.copy();
		if (index < BOX) {
			if (!moveItemStackTo(stack, BOX + 9, slots.size(), true)) return ItemStack.EMPTY;
		} else if (!moveItemStackTo(stack, 0, BOX, false)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
		else slot.setChanged();
		return copy;
	}

	@Override
	public boolean stillValid(Player player) {
		return Coletor.store != null && !be.isRemoved() && Coletor.store.get(machine.dim, machine.pos()) == machine && be.stillValid(player);
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		be.stopOpen(player);
	}
}
