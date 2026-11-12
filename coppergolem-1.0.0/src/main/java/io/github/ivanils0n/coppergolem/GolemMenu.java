package io.github.ivanils0n.coppergolem;

import io.github.ivanils0n.coppergolem.Links.Link;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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

/**
 * Tela de vínculos do golem (menu "Pátina"), um baú de 3 fileiras só de vitrine: 1ª fileira = [recarga] [6
 * origens] ... [ver baús] [desfazer]; 2ª e 3ª = 18 baús de guardar. Com mais baús, a seta no canto abre as telas seguintes. Clicar num baú da lista o desliga.
 * Para ligar um baú: botão direito nele com o cajado (abre a escolha origem/guardar).
 */
final class GolemMenu extends ChestMenu {
	static final int REFILL = 0, FIRST_SOURCE = 1, SEE = 7, CLEAR = 8, FIRST_DEST = 9, PREV = 25, NEXT = 26;
	/** Na 1ª tela cabem 6 origens e 18 de guardar (17 se tem seta); o resto vai para as telas seguintes, 25 por tela. */
	static final int SHOWN_SOURCES = 6, SHOWN_DESTS = 18, PER_PAGE = 25;

	private final ServerPlayer player;
	private final UUID golem;
	private final SimpleContainer box;
	private final int page;
	private final Runnable[] actions = new Runnable[27]; // o que o clique em cada espaço faz

	private GolemMenu(int id, Inventory inv, SimpleContainer box, ServerPlayer player, UUID golem, int page) {
		super(MenuType.GENERIC_9x3, id, inv, box, 3);
		this.box = box;
		this.player = player;
		this.golem = golem;
		this.page = page;
		fill();
	}

	static void open(ServerPlayer sp, UUID golem) {
		open(sp, golem, 0);
	}

	static void open(ServerPlayer sp, UUID golem, int page) {
		SimpleContainer box = new SimpleContainer(27);
		Component title = page == 0 ? GolemMod.title(sp) : GolemMod.title(sp, "\uE000", "Mais baús do golem");
		sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new GolemMenu(id, inv, box, sp, golem, page), title));
	}

	private Link link() {
		return GolemMod.links == null ? null : GolemMod.links.get(golem);
	}

	private void fill() {
		box.clearContent();
		java.util.Arrays.fill(actions, null);
		Link l = link();
		if (l == null) l = new Link();
		Link link = l;
		boolean more = l.sources.size() > SHOWN_SOURCES || l.dests.size() > SHOWN_DESTS;
		int destsHere = more ? SHOWN_DESTS - 1 : SHOWN_DESTS;
		// baús que não cabem na 1ª tela: origens a partir da 7ª, guardar a partir da 18ª (ou 19ª)
		List<int[]> extra = new ArrayList<>(); // {0 = origem / 1 = guardar, índice}
		for (int i = SHOWN_SOURCES; i < l.sources.size(); i++) extra.add(new int[] {0, i});
		for (int i = destsHere; i < l.dests.size(); i++) extra.add(new int[] {1, i});
		int pages = 1 + (extra.size() + PER_PAGE - 1) / PER_PAGE;

		if (page == 0) {
			box.setItem(REFILL, l.refill == null
				? GolemMod.icon(player, "btn_add", Items.PAPER, "Recarga: sem baú", ChatFormatting.GRAY, "Botão direito num baú de cobre com o cajado.", "As barras de cobre dele recarregam o golem.")
				: GolemMod.icon(player, "btn_refill", Items.COPPER_INGOT, "Recarga (baú de cobre)", ChatFormatting.GOLD, where(l.refill), "Barras de cobre: " + coppers(player.level(), l.refill), "Clique para desligar."));
			if (l.refill != null) actions[REFILL] = () -> link.refill = null;
			for (int i = 0; i < SHOWN_SOURCES; i++) {
				if (i < l.sources.size()) entry(FIRST_SOURCE + i, l, true, i);
				else if (i == l.sources.size()) box.setItem(FIRST_SOURCE + i, GolemMod.icon(player, "btn_add", Items.PAPER, "Origem: livre", ChatFormatting.GRAY, "Botão direito num baú ou barril com o cajado", "e escolha \"Origem\"."));
			}
			for (int i = 0; i < destsHere; i++) {
				if (i < l.dests.size()) entry(FIRST_DEST + i, l, false, i);
				else if (i == l.dests.size()) box.setItem(FIRST_DEST + i, GolemMod.icon(player, "btn_add", Items.PAPER, "Guardar: livre", ChatFormatting.GRAY, "Botão direito num baú ou barril com o cajado", "e escolha \"Guardar\".", "Moldura com item ao lado: só esse item entra."));
			}
			box.setItem(SEE, GolemMod.icon(player, "btn_see", Items.ENDER_EYE, "Ver baús", ChatFormatting.LIGHT_PURPLE, "Marca os baús vinculados com partículas."));
			actions[SEE] = () -> see(link);
			box.setItem(CLEAR, GolemMod.icon(player, "btn_clear", Items.BARRIER, "Desfazer tudo", ChatFormatting.RED, "Desliga todos os baús deste golem."));
			actions[CLEAR] = () -> GolemMod.links.remove(golem);
		} else {
			int from = (page - 1) * PER_PAGE;
			for (int k = 0; k < PER_PAGE && from + k < extra.size(); k++) {
				int[] e = extra.get(from + k);
				entry(k, l, e[0] == 0, e[1]);
			}
			box.setItem(PREV, GolemMod.icon(player, "btn_prev", Items.ARROW, "Voltar", ChatFormatting.AQUA, "Tela anterior."));
			actions[PREV] = () -> open(player, golem, page - 1);
		}
		if (page + 1 < pages) {
			box.setItem(NEXT, GolemMod.icon(player, "btn_next", Items.ARROW, "Mais baús", ChatFormatting.AQUA,
				(extra.size() - page * PER_PAGE) + " baús vinculados não cabem aqui.", "Clique para ver os outros."));
			actions[NEXT] = () -> open(player, golem, page + 1);
		}
	}

	/** Um baú vinculado no espaço slot; clicar desliga. */
	private void entry(int slot, Link l, boolean source, int index) {
		int[] pos = (source ? l.sources : l.dests).get(index);
		if (source) {
			box.setItem(slot, GolemMod.icon(player, "btn_source", Items.HOPPER, "Origem", ChatFormatting.AQUA, where(pos), "O golem pega itens daqui.", "Clique para desligar."));
		} else {
			ItemStack f = Boxes.filter(player.level(), Link.pos(pos));
			box.setItem(slot, GolemMod.icon(player, "btn_dest", Items.CHEST, "Guardar", ChatFormatting.GREEN, where(pos),
				f.isEmpty() ? "Sem moldura: qualquer item (por categoria)." : "Só: " + f.getHoverName().getString(), "Clique para desligar."));
		}
		actions[slot] = () -> (source ? l.sources : l.dests).remove(index);
	}

	private void see(Link l) {
		ServerLevel sl = player.level();
		if (l.refill != null) GolemMod.particles(sl, player, Link.pos(l.refill), ParticleTypes.WAX_ON, 20);
		for (int[] s : l.sources) GolemMod.particles(sl, player, Link.pos(s), ParticleTypes.PORTAL, 30);
		for (int[] d : l.dests) GolemMod.particles(sl, player, Link.pos(d), ParticleTypes.END_ROD, 15);
	}

	private static String where(int[] pos) {
		return "Em " + pos[0] + ", " + pos[1] + ", " + pos[2];
	}

	private static int coppers(ServerLevel sl, int[] refill) {
		return Boxes.count(Boxes.containers(sl, Link.pos(refill)), new ItemStack(Items.COPPER_INGOT));
	}

	@Override
	public void clicked(int slot, int button, ContainerInput input, Player p) {
		if (!(p instanceof ServerPlayer)) return;
		if (slot >= 0 && slot < 27 && input == ContainerInput.PICKUP && actions[slot] != null && link() != null) {
			actions[slot].run();
			GolemMod.links.markDirty();
			if (player.containerMenu != this) return; // abriu outra tela (setas)
			fill();
		}
		sendAllDataToRemote(); // nada sai desta tela
	}

	@Override
	public ItemStack quickMoveStack(Player p, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player p) {
		return GolemMod.links != null && (GolemMod.isWand(p.getMainHandItem()) || GolemMod.isWand(p.getOffhandItem()));
	}
}
