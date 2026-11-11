package io.github.ivanils0n.tpaltar;

import io.github.ivanils0n.tpaltar.AltarStore.Altar;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/**
 * Baú virtual do altar: em cima os outros altares da dimensão (clique para viajar; mais de 45 viram páginas), na última fileira 4 espaços
 * de ender pearls que são o estoque do próprio altar (4 × 16 = 64). Só esses 4 espaços aceitam itens.
 */
final class AltarMenu extends ChestMenu {
	private static final MenuType<?>[] TYPES = {
		MenuType.GENERIC_9x1, MenuType.GENERIC_9x2, MenuType.GENERIC_9x3,
		MenuType.GENERIC_9x4, MenuType.GENERIC_9x5, MenuType.GENERIC_9x6
	};
	private static final int PAGE = 45; // 5 fileiras de destinos + 1 de estoque; acima disso, páginas
	private static final int PEARL_SLOTS = 4;
	private static final int FIRST_PEARL = 2; // posição na fileira de estoque: [vidro vidro P P P P vidro vidro vidro]

	private final Altar from;
	private final List<Altar> targets;
	private final Box box;
	private final int storeStart; // índice do primeiro espaço da fileira de estoque
	private boolean loading;
	private int page;

	/** Baú que avisa o menu a cada mudança (o SimpleContainer da 26.x não tem ouvintes). */
	private static final class Box extends SimpleContainer {
		Runnable onChange;

		Box(int size) {
			super(size);
		}

		@Override
		public void setChanged() {
			super.setChanged();
			if (onChange != null) onChange.run();
		}
	}

	private AltarMenu(int id, Inventory inv, Box box, int rows, Altar from, List<Altar> targets) {
		super(TYPES[rows - 1], id, inv, box, rows);
		this.from = from;
		this.targets = targets;
		this.box = box;
		this.storeStart = (rows - 1) * 9;
		// Troca os espaços do baú: estoque só aceita ender pearl; o resto não deixa pegar nem colocar nada.
		for (int i = 0; i < rows * 9; i++) {
			Slot old = slots.get(i);
			Slot slot = isPearlSlot(i)
				? new Slot(box, old.getContainerSlot(), old.x, old.y) {
					@Override
					public boolean mayPlace(ItemStack stack) {
						return stack.is(Items.ENDER_PEARL);
					}
				}
				: new Slot(box, old.getContainerSlot(), old.x, old.y) {
					@Override
					public boolean mayPlace(ItemStack stack) {
						return false;
					}

					@Override
					public boolean mayPickup(Player player) {
						return false;
					}
				};
			slot.index = i;
			slots.set(i, slot);
		}
		showPage(0);
		loadPearls();
		box.onChange = this::pearlsMoved;
	}

	private int pages() {
		return Math.max(1, (targets.size() + PAGE - 1) / PAGE);
	}

	/** Preenche os destinos da página e a fileira de baixo (vidros e, com mais de uma página, as setas nas pontas). */
	private void showPage(int p) {
		page = p;
		loading = true;
		for (int i = 0; i < storeStart; i++) {
			int t = page * PAGE + i;
			// Vazios ficam ocupados: senão o shift-clique numa ender pearl aparece neles no cliente.
			box.setItem(i, t < targets.size() ? icon(targets.get(t)) : blank("Sem altar", "Outro altar nesta dimensão aparece aqui."));
		}
		for (int i = storeStart; i < storeStart + 9; i++) if (!isPearlSlot(i)) box.setItem(i, blank("Ender pearls deste altar", "Guarde até " + TpAltar.MAX_PEARLS + " nos espaços do meio.", "Cada viagem até aqui gasta " + TpAltar.COST + "."));
		if (pages() > 1) {
			box.setItem(storeStart, arrow("◀ Página anterior"));
			box.setItem(storeStart + 8, arrow("Próxima página ▶"));
		}
		loading = false;
	}

	private ItemStack arrow(String name) {
		ItemStack s = new ItemStack(Items.ARROW);
		s.set(DataComponents.CUSTOM_NAME, TpAltar.plain(name, ChatFormatting.LIGHT_PURPLE));
		s.set(DataComponents.LORE, new ItemLore(List.of(TpAltar.plain("Página " + (page + 1) + " de " + pages(), ChatFormatting.GRAY))));
		return s;
	}

	static void open(ServerPlayer player, Altar from, List<Altar> all) {
		List<Altar> targets = List.copyOf(all);
		int rows = (Math.min(targets.size(), PAGE) + 8) / 9 + 1;
		Box box = new Box(rows * 9);
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new AltarMenu(id, inv, box, rows, from, targets), title(player, rows, from.name)));
		if (!(player.containerMenu instanceof AltarMenu)) return;
		TpAltar.lock(from, player); // trava até teleportar ou fechar
		player.level().playSound(null, new BlockPos(from.x, from.y, from.z), SoundEvents.ENDER_CHEST_OPEN, SoundSource.BLOCKS, 0.6f, 1.2f);
	}

	/** Fundo de baú com o mesmo número de fileiras (caracteres \uE000 a \uE005 da fonte tpaltar:gui). */
	private static Component title(ServerPlayer player, int rows, String name) {
		return TpAltar.guiTitle(player, "\uF000" + (char) (0xE000 + rows - 1) + "\uF001\uF002", name);
	}

	boolean isFor(Altar a) {
		return from.key().equals(a.key());
	}

	private boolean isPearlSlot(int i) {
		int col = i - storeStart;
		return col >= FIRST_PEARL && col < FIRST_PEARL + PEARL_SLOTS;
	}

	/** Preenche os 4 espaços com o estoque atual do altar (ex.: depois que alguém viajou até ele). */
	void loadPearls() {
		loading = true;
		int left = from.pearls;
		for (int i = 0; i < PEARL_SLOTS; i++) {
			int n = Math.min(left, 16);
			box.setItem(storeStart + FIRST_PEARL + i, n > 0 ? new ItemStack(Items.ENDER_PEARL, n) : ItemStack.EMPTY);
			left -= n;
		}
		loading = false;
	}

	/** Qualquer mudança no baú: o estoque do altar passa a ser o que está nos 4 espaços. */
	private void pearlsMoved() {
		if (loading) return;
		int total = 0;
		for (int i = 0; i < PEARL_SLOTS; i++) {
			ItemStack s = box.getItem(storeStart + FIRST_PEARL + i);
			if (s.is(Items.ENDER_PEARL)) total += s.getCount();
		}
		if (total != from.pearls) TpAltar.setPearls(from, total);
	}

	@Override
	public void removed(Player player) {
		super.removed(player); // devolve ao jogador o item que estava no cursor; o estoque fica no altar
		TpAltar.unlock(from, player);
	}

	private static ItemStack icon(Altar a) {
		boolean charged = a.pearls >= TpAltar.COST;
		// Sem o pack: olho do fim / ender pearl. Com o pack: olho rúnico aberto / fechado.
		ItemStack s = charged ? TpAltar.piece(Items.ENDER_EYE, "tpaltar_dest_on", "dest_on") : TpAltar.piece(Items.ENDER_PEARL, "tpaltar_dest_off", "dest_off");
		s.set(DataComponents.CUSTOM_NAME, TpAltar.plain(a.name, charged ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY));
		s.set(DataComponents.LORE, new ItemLore(List.of(
			TpAltar.plain(a.x + ", " + a.y + ", " + a.z, ChatFormatting.GRAY),
			TpAltar.plain("Carga: " + a.pearls + "/" + TpAltar.MAX_PEARLS + " (" + a.pearls / TpAltar.COST + " viagens)", ChatFormatting.GRAY),
			charged
				? TpAltar.plain("Clique para viajar", ChatFormatting.GREEN)
				: TpAltar.plain("Sem ender pearls suficientes", ChatFormatting.RED))));
		return s;
	}

	private static ItemStack blank(String name, String... lore) {
		// Com o pack o vidro fica invisível e aparecem as runas do fundo; sem o pack continua vidro.
		ItemStack s = TpAltar.piece(Items.GLASS_PANE, "tpaltar_blank", "blank");
		s.set(DataComponents.CUSTOM_NAME, TpAltar.plain(name, ChatFormatting.LIGHT_PURPLE));
		s.set(DataComponents.LORE, new ItemLore(java.util.Arrays.stream(lore).<Component>map(l -> TpAltar.plain(l, ChatFormatting.GRAY)).toList()));
		return s;
	}

	@Override
	public void clicked(int slot, int button, ContainerInput type, Player player) {
		if (slot >= 0 && slot < storeStart + 9 && !isPearlSlot(slot)) {
			// Destinos, setas e vidros: nunca passam pelo clique normal.
			if (player instanceof ServerPlayer sp && type == ContainerInput.PICKUP) {
				int target = page * PAGE + slot;
				if (slot < storeStart) {
					if (target < targets.size()) {
						TpAltar.teleport(sp, from, targets.get(target)); // fecha este menu
						return;
					}
				} else if (pages() > 1 && slot == storeStart) showPage(Math.floorMod(page - 1, pages()));
				else if (pages() > 1 && slot == storeStart + 8) showPage(Math.floorMod(page + 1, pages()));
			}
			sendAllDataToRemote(); // reenvia o estado real ao cliente
			return;
		}
		super.clicked(slot, button, type, player); // estoque e inventário do jogador funcionam como um baú normal
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem()) return ItemStack.EMPTY;
		ItemStack stack = slot.getItem();
		ItemStack before = stack.copy();
		int boxEnd = storeStart + 9;
		if (index < boxEnd) {
			if (!isPearlSlot(index) || !moveItemStackTo(stack, boxEnd, slots.size(), true)) return ItemStack.EMPTY;
		} else {
			int first = storeStart + FIRST_PEARL;
			if (!stack.is(Items.ENDER_PEARL) || !moveItemStackTo(stack, first, first + PEARL_SLOTS, false)) return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
		else slot.setChanged();
		return before;
	}
}
