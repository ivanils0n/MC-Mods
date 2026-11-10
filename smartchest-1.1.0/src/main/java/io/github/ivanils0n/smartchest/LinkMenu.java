package io.github.ivanils0n.smartchest;

import io.github.ivanils0n.smartchest.ChestStore.Chest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.phys.Vec3;

/**
 * Botão esquerdo com o Vinculador num baú: uma fileira para escolher o que o Baú Central guarda nele. [qualquer item]
 * [as 7 categorias] [só retirar]. O espaço clicado é o modo (ChestStore.ANY, 1-7, ChestStore.INPUT).
 */
final class LinkMenu extends ChestMenu {
	private final BlockPos pos;

	private LinkMenu(int id, Inventory inv, SimpleContainer box, BlockPos pos) {
		super(MenuType.GENERIC_9x1, id, inv, box, 1);
		this.pos = pos;
	}

	static void open(ServerPlayer sp, Chest c, BlockPos pos) {
		boolean linked = c.isLinked(pos);
		int mode = linked ? c.mode(pos) : -1;
		SimpleContainer box = new SimpleContainer(9);
		box.setItem(ChestStore.ANY, StorageMenu.tab(sp, null, Items.CHEST, mode == ChestStore.ANY, "Qualquer item",
			"O Baú Central guarda e organiza", "qualquer item neste baú."));
		for (int i = 1; i < ChestStore.INPUT; i++) {
			box.setItem(i, StorageMenu.tab(sp, null, Categories.VANILLA[i], mode == i, "Só " + Categories.NAMES[i],
				"Itens desta categoria vão primeiro", "para este baú, e nenhum outro entra nele."));
		}
		box.setItem(ChestStore.INPUT, StorageMenu.tab(sp, null, Items.HOPPER, mode == ChestStore.INPUT, "Só retirar",
			"Você põe itens nele à mão; o Baú Central", "mostra e tira daqui, mas nunca guarda", "nem organiza nada neste baú."));
		sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new LinkMenu(id, inv, box, pos),
			Component.literal(linked ? "Baú ligado: trocar o tipo" : "Ligar baú: escolha o tipo")));
	}

	@Override
	public void clicked(int slot, int button, ContainerInput input, Player p) {
		if (!(p instanceof ServerPlayer sp)) return;
		if (slot >= 0 && slot < 9 && input == ContainerInput.PICKUP) {
			sp.level().getServer().execute(() -> {
				sp.closeContainer();
				ItemStack held = sp.getMainHandItem();
				SmartChest.say(sp, SmartChest.isLinker(held) ? SmartChest.linkWith(sp, sp.level(), held, pos, slot)
					: SmartChest.plain("Segure o Vinculador para ligar o baú.", ChatFormatting.RED));
			});
		}
		sendAllDataToRemote(); // nada sai desta tela
	}

	@Override
	public ItemStack quickMoveStack(Player p, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player p) {
		return Storage.isLinkable(p.level().getBlockEntity(pos)) && p.distanceToSqr(Vec3.atCenterOf(pos)) <= SmartChest.USE_RANGE_SQR;
	}
}
