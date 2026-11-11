package io.github.ivanils0n.coppergolem;

import io.github.ivanils0n.coppergolem.Links.Link;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/**
 * Tela de vínculos do golem (menu "Cobre Polido"), um baú de 3 fileiras só de vitrine: 1ª fileira = [recarga] [até 3
 * origens] ... [modo] [ver baús] [desfazer]; 2ª e 3ª = até 18 baús de guardar. Clicar num baú da lista o desliga.
 */
final class GolemMenu extends ChestMenu {
	static final int REFILL = 0, FIRST_SOURCE = 1, MODE = 6, SEE = 7, CLEAR = 8, FIRST_DEST = 9;

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
		List<int[]> none = new ArrayList<>();
		box.setItem(REFILL, l.refill == null
			? hint("Recarga: sem baú", "Clique num baú de cobre com o cajado.", "As barras de cobre dele recarregam o golem.")
			: entry(sl, l.refill, "Recarga (baú de cobre)", ChatFormatting.GOLD, "Barras de cobre: " + coppers(sl, l.refill), "Clique para desligar."));
		for (int i = 0; i < Links.MAX_SOURCES; i++) {
			if (i < l.sources.size()) box.setItem(FIRST_SOURCE + i, entry(sl, l.sources.get(i), "Origem", ChatFormatting.AQUA, "O golem pega itens daqui.", "Clique para desligar."));
			else if (i == l.sources.size()) box.setItem(FIRST_SOURCE + i, hint("Origem: livre", "Modo \"Origem\" + clique num baú ou barril.", "O golem pega itens dele."));
		}
		for (int i = 0; i < Links.MAX_DESTS; i++) {
			if (i < l.dests.size()) {
				ItemStack f = Boxes.filter(sl, Link.pos(l.dests.get(i)));
				box.setItem(FIRST_DEST + i, entry(sl, l.dests.get(i), "Guardar", ChatFormatting.GREEN,
					f.isEmpty() ? "Sem moldura: qualquer item (por categoria)." : "Só: " + f.getHoverName().getString(), "Clique para desligar."));
			} else if (i == l.dests.size()) {
				box.setItem(FIRST_DEST + i, hint("Guardar: livre", "Modo \"Guardar\" + clique num baú ou barril.", "Moldura com item ao lado: só esse item entra."));
			}
		}
		boolean origin = mode() == 1;
		box.setItem(MODE, named(Items.COMPARATOR, "Ao clicar num baú: " + (origin ? "ORIGEM" : "GUARDAR"), origin ? ChatFormatting.AQUA : ChatFormatting.GREEN,
			"Clique para trocar o modo do cajado."));
		box.setItem(SEE, named(Items.ENDER_EYE, "Ver baús", ChatFormatting.LIGHT_PURPLE, "Marca os baús vinculados com partículas."));
		box.setItem(CLEAR, named(Items.BARRIER, "Desfazer tudo", ChatFormatting.RED, "Desliga todos os baús deste golem."));
	}

	private int mode() {
		ItemStack wand = player.getMainHandItem();
		return GolemMod.isWand(wand) ? GolemMod.mode(wand) : 0;
	}

	private static int coppers(ServerLevel sl, int[] refill) {
		return Boxes.count(Boxes.containers(sl, Link.pos(refill)), new ItemStack(Items.COPPER_INGOT));
	}

	private static ItemStack hint(String name, String... lines) {
		return named(Items.LIGHT_GRAY_STAINED_GLASS_PANE, name, ChatFormatting.GRAY, lines);
	}

	private static ItemStack named(Item item, String name, ChatFormatting color, String... lines) {
		ItemStack s = new ItemStack(item);
		s.set(DataComponents.CUSTOM_NAME, GolemMod.plain(name, color));
		List<Component> lore = new ArrayList<>();
		for (String line : lines) lore.add(GolemMod.plain(line, ChatFormatting.GRAY));
		s.set(DataComponents.LORE, new ItemLore(lore));
		return s;
	}

	/** O bloco de verdade como ícone (baú, barril, baú de cobre), com a posição na descrição. */
	private static ItemStack entry(ServerLevel sl, int[] pos, String name, ChatFormatting color, String... lines) {
		BlockPos p = Link.pos(pos);
		Item icon = sl.isLoaded(p) ? sl.getBlockState(p).getBlock().asItem() : Items.AIR;
		boolean missing = icon == Items.AIR;
		List<String> all = new ArrayList<>();
		all.add(missing ? "Não carregado ou sumiu." : "Em " + p.getX() + ", " + p.getY() + ", " + p.getZ());
		all.addAll(List.of(lines));
		return named(missing ? Items.BARRIER : icon, name, color, all.toArray(new String[0]));
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
		if (slot == MODE) {
			ItemStack wand = sp.getMainHandItem();
			if (GolemMod.isWand(wand)) GolemMod.setMode(wand, GolemMod.mode(wand) == 1 ? 0 : 1);
			sl.playSound(null, sp.blockPosition(), net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.3f);
		} else if (l == null) {
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
