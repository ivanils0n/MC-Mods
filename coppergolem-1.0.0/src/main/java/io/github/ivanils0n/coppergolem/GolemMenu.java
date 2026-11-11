package io.github.ivanils0n.coppergolem;

import io.github.ivanils0n.coppergolem.Links.Link;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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
 * Tela de vínculos do golem (menu "Cobre Polido"), um baú de 3 fileiras só de vitrine: 1ª fileira = [recarga] [até 6
 * origens] ... [ver baús] [desfazer]; 2ª e 3ª = até 18 baús de guardar. Clicar num baú da lista o desliga.
 * Para ligar um baú: botão direito nele com o cajado (abre a escolha origem/guardar).
 */
final class GolemMenu extends ChestMenu {
	static final int REFILL = 0, FIRST_SOURCE = 1, SEE = 7, CLEAR = 8, FIRST_DEST = 9;

	private final ServerPlayer player;
	private final UUID golem;
	private final SimpleContainer box;

	private GolemMenu(int id, Inventory inv, SimpleContainer box, ServerPlayer player, UUID golem) {
		super(MenuType.GENERIC_9x3, id, inv, box, 3);
		this.box = box;
		this.player = player;
		this.golem = golem;
		fill();
	}

	static void open(ServerPlayer sp, UUID golem) {
		SimpleContainer box = new SimpleContainer(27);
		sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new GolemMenu(id, inv, box, sp, golem), GolemMod.title(sp)));
	}

	private Link link() {
		return GolemMod.links == null ? null : GolemMod.links.get(golem);
	}

	private void fill() {
		box.clearContent();
		ServerLevel sl = player.level();
		Link l = link();
		if (l == null) l = new Link();
		ChatFormatting gold = ChatFormatting.GOLD, aqua = ChatFormatting.AQUA, green = ChatFormatting.GREEN;
		box.setItem(REFILL, l.refill == null
			? GolemMod.icon(player, "btn_add", Items.PAPER, "Recarga: sem baú", ChatFormatting.GRAY, "Botão direito num baú de cobre com o cajado.", "As barras de cobre dele recarregam o golem.")
			: GolemMod.icon(player, "btn_refill", Items.COPPER_INGOT, "Recarga (baú de cobre)", gold, where(l.refill), "Barras de cobre: " + coppers(sl, l.refill), "Clique para desligar."));
		for (int i = 0; i < Links.MAX_SOURCES; i++) {
			if (i < l.sources.size()) {
				box.setItem(FIRST_SOURCE + i, GolemMod.icon(player, "btn_source", Items.HOPPER, "Origem", aqua, where(l.sources.get(i)), "O golem pega itens daqui.", "Clique para desligar."));
			} else if (i == l.sources.size()) {
				box.setItem(FIRST_SOURCE + i, GolemMod.icon(player, "btn_add", Items.PAPER, "Origem: livre", ChatFormatting.GRAY, "Botão direito num baú ou barril com o cajado", "e escolha \"Origem\"."));
			}
		}
		for (int i = 0; i < Links.MAX_DESTS; i++) {
			if (i < l.dests.size()) {
				ItemStack f = Boxes.filter(sl, Link.pos(l.dests.get(i)));
				box.setItem(FIRST_DEST + i, GolemMod.icon(player, "btn_dest", Items.CHEST, "Guardar", green, where(l.dests.get(i)),
					f.isEmpty() ? "Sem moldura: qualquer item (por categoria)." : "Só: " + f.getHoverName().getString(), "Clique para desligar."));
			} else if (i == l.dests.size()) {
				box.setItem(FIRST_DEST + i, GolemMod.icon(player, "btn_add", Items.PAPER, "Guardar: livre", ChatFormatting.GRAY, "Botão direito num baú ou barril com o cajado", "e escolha \"Guardar\".", "Moldura com item ao lado: só esse item entra."));
			}
		}
		box.setItem(SEE, GolemMod.icon(player, "btn_see", Items.ENDER_EYE, "Ver baús", ChatFormatting.LIGHT_PURPLE, "Marca os baús vinculados com partículas."));
		box.setItem(CLEAR, GolemMod.icon(player, "btn_clear", Items.BARRIER, "Desfazer tudo", ChatFormatting.RED, "Desliga todos os baús deste golem."));
	}

	private static String where(int[] pos) {
		return "Em " + pos[0] + ", " + pos[1] + ", " + pos[2];
	}

	private static int coppers(ServerLevel sl, int[] refill) {
		return Boxes.count(Boxes.containers(sl, Link.pos(refill)), new ItemStack(Items.COPPER_INGOT));
	}

	@Override
	public void clicked(int slot, int button, ContainerInput input, Player p) {
		if (!(p instanceof ServerPlayer sp)) return;
		if (slot >= 0 && slot < 27 && input == ContainerInput.PICKUP) click(sp, slot);
		sendAllDataToRemote(); // nada sai desta tela
	}

	private void click(ServerPlayer sp, int slot) {
		Link l = link();
		ServerLevel sl = sp.level();
		boolean changed = false;
		if (l == null) {
			return;
		} else if (slot == SEE) {
			if (l.refill != null) GolemMod.particles(sl, sp, Link.pos(l.refill), ParticleTypes.WAX_ON, 20);
			for (int[] s : l.sources) GolemMod.particles(sl, sp, Link.pos(s), ParticleTypes.PORTAL, 30);
			for (int[] d : l.dests) GolemMod.particles(sl, sp, Link.pos(d), ParticleTypes.END_ROD, 15);
		} else if (slot == CLEAR) {
			GolemMod.links.remove(golem);
			changed = true;
		} else if (slot == REFILL && l.refill != null) {
			l.refill = null;
			changed = true;
		} else if (slot >= FIRST_SOURCE && slot < FIRST_SOURCE + Links.MAX_SOURCES && slot - FIRST_SOURCE < l.sources.size()) {
			l.sources.remove(slot - FIRST_SOURCE);
			changed = true;
		} else if (slot >= FIRST_DEST && slot - FIRST_DEST < l.dests.size()) {
			l.dests.remove(slot - FIRST_DEST);
			changed = true;
		}
		if (changed) GolemMod.links.markDirty();
		fill();
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
