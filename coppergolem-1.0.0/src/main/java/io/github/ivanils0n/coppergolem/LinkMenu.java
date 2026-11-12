package io.github.ivanils0n.coppergolem;

import io.github.ivanils0n.coppergolem.GolemMod.Role;
import net.minecraft.ChatFormatting;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
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

/** Escolha para um baú comum clicado com o cajado: origem, guardar ou desligar (1 fileira). */
final class LinkMenu extends ChestMenu {
	static final int SOURCE = 2, DEST = 4, NONE = 6;

	private final ServerPlayer player;
	private final BlockPos pos;
	private final Role now; // papel que o baú já tem (NONE = não vinculado)

	private LinkMenu(int id, Inventory inv, SimpleContainer box, ServerPlayer player, BlockPos pos) {
		super(MenuType.GENERIC_9x1, id, inv, box, 1);
		this.player = player;
		this.pos = pos;
		UUID golem = GolemMod.golemOf(player.getMainHandItem());
		now = GolemMod.roleOf(golem == null ? null : GolemMod.links.get(golem), Boxes.group(player.level(), pos));
		box.setItem(SOURCE, now == Role.SOURCE
			? linked(GolemMod.icon(player, "btn_source", Items.HOPPER, "Origem (já vinculado)", ChatFormatting.AQUA, "Este baú já é de origem deste golem."))
			: GolemMod.icon(player, "btn_source", Items.HOPPER, "Origem", ChatFormatting.AQUA, "O golem pega itens deste baú."));
		box.setItem(DEST, now == Role.DEST
			? linked(GolemMod.icon(player, "btn_dest", Items.CHEST, "Guardar (já vinculado)", ChatFormatting.GREEN, "Este baú já é de guardar deste golem."))
			: GolemMod.icon(player, "btn_dest", Items.CHEST, "Guardar", ChatFormatting.GREEN, "O golem guarda itens neste baú.", "Moldura com item ao lado: só esse item."));
		box.setItem(NONE, GolemMod.icon(player, "btn_clear", Items.BARRIER, "Desligar", ChatFormatting.RED, now == Role.NONE ? "Este baú não está vinculado." : "Tira este baú do golem."));
	}

	/** Brilho de encantamento: marca o papel que o baú já tem. */
	private static ItemStack linked(ItemStack s) {
		s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return s;
	}

	static void open(ServerPlayer sp, BlockPos pos) {
		SimpleContainer box = new SimpleContainer(9);
		sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new LinkMenu(id, inv, box, sp, pos), GolemMod.title(sp, "\uE001", "Vincular baú")));
	}

	@Override
	public void clicked(int slot, int button, ContainerInput input, Player p) {
		if (!(p instanceof ServerPlayer sp)) return;
		Role role = slot == SOURCE ? Role.SOURCE : slot == DEST ? Role.DEST : slot == NONE ? Role.NONE : null;
		if (role != null && role == now && input == ContainerInput.PICKUP) { // já é esse: não vincula de novo
			GolemMod.say(sp, GolemMod.plain(role == Role.NONE ? "Este baú não está vinculado." : "Este baú já está vinculado como " + (role == Role.SOURCE ? "origem" : "guardar") + ".", ChatFormatting.YELLOW));
			sendAllDataToRemote();
			return;
		}
		if (role != null && input == ContainerInput.PICKUP) {
			GolemMod.say(sp, GolemMod.link(sp, sp.level(), sp.getMainHandItem(), pos, role));
			sp.closeContainer();
			return;
		}
		sendAllDataToRemote();
	}

	@Override
	public ItemStack quickMoveStack(Player p, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player p) {
		return GolemMod.links != null && GolemMod.isWand(p.getMainHandItem());
	}
}
