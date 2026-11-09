package io.github.ivanils0n.tpaltar;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Tela de bigorna usada como caixa de texto para o nome do altar (o cliente vanilla já sabe desenhá-la).
 * O jogador digita no campo e clica no item do resultado para confirmar. Nenhum item sai ou entra, e não gasta XP.
 */
final class NameMenu extends AnvilMenu {
	private static final int RESULT_SLOT = 2;

	private final TpAltar.Pending pending;
	private String typed = "";
	private boolean done; // true quando o fechamento é nosso (confirmou ou reabriu), não um Esc do jogador

	private NameMenu(int id, Inventory inv, TpAltar.Pending pending) {
		super(id, inv);
		this.pending = pending;
		// Item só de exibição; nome vazio deixa o campo de texto em branco.
		ItemStack shown = new ItemStack(Items.ENDER_EYE);
		shown.set(DataComponents.CUSTOM_NAME, Component.literal(""));
		getSlot(0).set(shown);
	}

	static void open(ServerPlayer player, TpAltar.Pending pending, String title) {
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new NameMenu(id, inv, pending), Component.literal(title)));
	}

	@Override
	public boolean setItemName(String name) {
		typed = name == null ? "" : name; // o cliente manda o texto a cada letra digitada
		return super.setItemName(name);
	}

	@Override
	public void clicked(int slot, int button, ContainerInput input, Player player) {
		// Nunca chama o super: nada pode ser pego ou movido. Reenvia o estado real ao cliente.
		sendAllDataToRemote();
		if (done || slot != RESULT_SLOT || !(player instanceof ServerPlayer sp) || !(sp.level() instanceof ServerLevel sl)) return;
		done = true;
		String text = typed;
		// Depois do clique terminar de ser processado, para não trocar de tela no meio dele.
		sl.getServer().execute(() -> {
			String retry = TpAltar.finishPlacement(sp, pending, text);
			if (retry != null) open(sp, pending, retry);
			else sp.closeContainer();
		});
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public void removed(Player player) {
		getSlot(0).set(ItemStack.EMPTY); // o item de exibição nunca vai para o inventário
		super.removed(player);
		if (!done) TpAltar.say(player, "Colocação cancelada.", ChatFormatting.YELLOW);
	}
}
