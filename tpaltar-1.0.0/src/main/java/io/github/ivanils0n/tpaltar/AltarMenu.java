package io.github.ivanils0n.tpaltar;

import io.github.ivanils0n.tpaltar.AltarStore.Altar;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/** Baú virtual com os outros altares da dimensão. Nenhum item pode ser tirado ou colocado. */
final class AltarMenu extends ChestMenu {
	private static final MenuType<?>[] TYPES = {
		MenuType.GENERIC_9x1, MenuType.GENERIC_9x2, MenuType.GENERIC_9x3,
		MenuType.GENERIC_9x4, MenuType.GENERIC_9x5, MenuType.GENERIC_9x6
	};

	private final Altar from;
	private final List<Altar> targets;

	private AltarMenu(int id, Inventory inv, SimpleContainer box, int rows, Altar from, List<Altar> targets) {
		super(TYPES[rows - 1], id, inv, box, rows);
		this.from = from;
		this.targets = targets;
	}

	static void open(ServerPlayer player, Altar from, List<Altar> all) {
		if (all.isEmpty()) {
			TpAltar.say(player, "Não há outros altares nesta dimensão.", ChatFormatting.YELLOW);
			return;
		}
		// shortcut: mostra só os 54 primeiros (um baú grande); paginar se algum servidor passar disso por dimensão.
		List<Altar> targets = List.copyOf(all.subList(0, Math.min(all.size(), 54)));
		int rows = (targets.size() + 8) / 9;
		SimpleContainer box = new SimpleContainer(rows * 9);
		for (int i = 0; i < targets.size(); i++) box.setItem(i, icon(targets.get(i)));
		Component title = Component.literal(from.name + " · " + from.pearls + "/" + TpAltar.MAX_PEARLS + " pérolas");
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new AltarMenu(id, inv, box, rows, from, targets), title));
	}

	private static ItemStack icon(Altar a) {
		boolean charged = a.pearls >= TpAltar.COST;
		ItemStack s = new ItemStack(charged ? Items.ENDER_EYE : Items.ENDER_PEARL);
		s.set(DataComponents.CUSTOM_NAME, TpAltar.plain(a.name, charged ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY));
		s.set(DataComponents.LORE, new ItemLore(List.of(
			TpAltar.plain(a.x + ", " + a.y + ", " + a.z, ChatFormatting.GRAY),
			TpAltar.plain("Carga: " + a.pearls + "/" + TpAltar.MAX_PEARLS + " (" + a.pearls / TpAltar.COST + " viagens)", ChatFormatting.GRAY),
			charged
				? TpAltar.plain("Clique para viajar", ChatFormatting.GREEN)
				: TpAltar.plain("Sem ender pearls suficientes", ChatFormatting.RED))));
		return s;
	}

	@Override
	public void clicked(int slot, int button, ContainerInput type, Player player) {
		// Nunca chama o super: bloqueia pegar, soltar, shift-clique e arrastar. Depois reenvia o estado real ao cliente.
		sendAllDataToRemote();
		if (player instanceof ServerPlayer sp && type == ContainerInput.PICKUP && slot >= 0 && slot < targets.size()) {
			TpAltar.teleport(sp, from, targets.get(slot));
		}
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slot) {
		return ItemStack.EMPTY;
	}
}
