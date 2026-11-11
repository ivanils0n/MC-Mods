package io.github.ivanils0n.smartchest;

import io.github.ivanils0n.smartchest.ChestStore.Chest;
import io.github.ivanils0n.smartchest.Storage.Entry;
import io.github.ivanils0n.smartchest.Storage.Key;
import io.github.ivanils0n.smartchest.Storage.Linked;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.recipebook.PlaceRecipeHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.phys.Vec3;

/**
 * Pesquisa da mesa de trabalho ligada: a mesma tela de digitar da pesquisa do Baú Central (a bigorna), mas os 27
 * espaços de baixo mostram o que dá para craftar com os itens dos baús ligados. Clicar num item abre a mesa
 * (TableMenu) com a receita montada. Tudo aqui é vitrine, como em SearchMenu: nada sai dos baús nesta tela.
 */
final class CraftSearchMenu extends AnvilMenu {
	private static final int CLOSE_SLOT = 0, CLEAR_SLOT = 1, RESULTS = 27;

	/** Um item que dá para fazer: a receita, o resultado e os ingredientes de um craft. */
	record Craft(RecipeHolder<?> recipe, ItemStack result, List<ItemStack> grid) {}

	private final ServerPlayer player;
	private final Chest chest;
	private final BlockPos pos;
	private final SimpleContainer shown = new SimpleContainer(RESULTS + 9);
	private final StorageMenu names; // nomes e busca iguais aos da tela do baú
	private final List<Craft> all;
	private List<Craft> found = List.of();
	private String typed;

	private CraftSearchMenu(int id, Inventory inv, ServerPlayer player, Chest chest, BlockPos pos, String query) {
		super(id, inv);
		this.player = player;
		this.chest = chest;
		this.pos = pos.immutable();
		this.typed = query;
		this.names = StorageMenu.detached(player, chest, "");
		this.all = craftable(player.level(), Storage.linked(player.level(), chest, SmartChest.store));
		all.sort(Comparator.comparing(c -> Lang.normalize(names.name(c.result))));
		for (int i = 0; i < slots.size(); i++) { // o inventário da tela vira a vitrine
			Slot old = slots.get(i);
			if (old.container != inv) continue;
			int index = old.y >= 142 ? RESULTS + (old.x - 8) / 18 : (old.y - 84) / 18 * 9 + (old.x - 8) / 18;
			Slot slot = new Slot(shown, index, old.x, old.y) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return false;
				}

				@Override
				public boolean mayPickup(Player p) {
					return false;
				}
			};
			slot.index = i;
			slots.set(i, slot);
		}
		// Sem item no 1º espaço o jogo não deixa digitar; a caixa começa com o nome dele (a pesquisa atual).
		ItemStack field = icon(Items.ARROW, "btn_prev", "", "Clique: fechar a mesa.");
		field.set(DataComponents.CUSTOM_NAME, Component.literal(query));
		getSlot(CLOSE_SLOT).set(field);
		getSlot(CLEAR_SLOT).set(icon(Items.BARRIER, "btn_clear", "Limpar a pesquisa"));
		refresh();
	}

	static void open(ServerPlayer sp, Chest chest, BlockPos pos, String query) {
		Component title = SmartChest.hasPack(sp) ? SmartChest.themedTitle("\uF003\uE003\uF001\uF004", "Craftar") : Component.literal("Craftar com o Baú Central");
		sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new CraftSearchMenu(id, inv, sp, chest, pos, query), title));
	}

	/** Para o teste automático. */
	static CraftSearchMenu detached(ServerPlayer sp, Chest chest, BlockPos pos, String query) {
		return new CraftSearchMenu(0, sp.getInventory(), sp, chest, pos, query);
	}

	List<Craft> found() {
		return found;
	}

	/** Receitas de mesa que os itens dos baús fazem pelo menos uma vez, uma por resultado. */
	static List<Craft> craftable(ServerLevel sl, Linked linked) {
		StackedItemContents have = new StackedItemContents();
		for (Container c : linked.containers) for (int i = 0; i < c.getContainerSize(); i++) have.accountSimpleStack(c.getItem(i));
		Map<Key, Craft> out = new LinkedHashMap<>();
		for (RecipeHolder<?> h : sl.getServer().getRecipeManager().getRecipes()) {
			if (!(h.value() instanceof CraftingRecipe r) || r.placementInfo().isImpossibleToPlace()) continue;
			List<Holder<Item>> used = new ArrayList<>();
			if (!have.canCraft(r, 1, used::add)) continue;
			List<ItemStack> grid = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
			PlaceRecipeHelper.placeRecipe(3, 3, r, r.placementInfo().slotsToIngredientIndex(), (ingredient, slot, x, y) -> {
				if (ingredient != -1) grid.set(slot, new ItemStack(used.get(ingredient)));
			});
			ItemStack result = r.assemble(CraftingInput.of(3, 3, grid));
			if (!result.isEmpty()) out.putIfAbsent(new Key(result.copyWithCount(1)), new Craft(h, result, grid));
		}
		return new ArrayList<>(out.values());
	}

	@Override
	public boolean setItemName(String name) {
		typed = name == null ? "" : name; // o cliente manda o texto a cada letra
		boolean changed = super.setItemName(name);
		refresh();
		sendAllDataToRemote();
		return changed;
	}

	@Override
	public void createResult() {
		if (player != null) refresh();
	}

	private void refresh() {
		String q = Lang.normalize(typed.trim());
		List<Craft> list = new ArrayList<>();
		for (Craft c : all) if (q.isEmpty() || names.haystack(new Entry(c.result.copyWithCount(1))).contains(q)) list.add(c);
		found = list;
		int visible = found.size() > RESULTS ? RESULTS - 1 : found.size();
		for (int i = 0; i < RESULTS; i++) shown.setItem(i, i < visible ? display(found.get(i)) : ItemStack.EMPTY);
		if (found.size() > RESULTS) {
			shown.setItem(RESULTS - 1, icon(Items.PAPER, "btn_more", "... e mais " + (found.size() - visible), "Digite mais letras para achar o item."));
		} else if (found.isEmpty()) {
			shown.setItem(0, icon(Items.BARRIER, "btn_clear", all.isEmpty() ? "Nada para craftar" : "Nada encontrado",
				all.isEmpty() ? "Os baús ligados não têm itens" : "Tente outro nome.", all.isEmpty() ? "para nenhuma receita." : ""));
		}
		resultSlots.setItem(0, ItemStack.EMPTY);
		setData(0, 0); // custo da bigorna: não mostra
	}

	private ItemStack display(Craft c) {
		ItemStack s = c.result.copy();
		Map<Key, Integer> uses = new LinkedHashMap<>();
		for (ItemStack g : c.grid) if (!g.isEmpty()) uses.merge(new Key(g), 1, Integer::sum);
		List<Component> lore = new ArrayList<>();
		lore.add(SmartChest.plain("Usa:", ChatFormatting.GRAY));
		for (Map.Entry<Key, Integer> u : uses.entrySet()) lore.add(SmartChest.plain("• " + u.getValue() + " " + names.name(u.getKey().stack()), ChatFormatting.GRAY));
		lore.add(Component.empty());
		lore.add(SmartChest.plain("Clique: craftar na mesa", ChatFormatting.DARK_GRAY));
		s.set(DataComponents.LORE, new ItemLore(lore));
		return s;
	}

	private ItemStack icon(Item item, String model, String name, String... lines) {
		ItemStack s = new ItemStack(item);
		if (SmartChest.hasPack(player)) s.set(DataComponents.ITEM_MODEL, SmartChest.model(model));
		s.set(DataComponents.CUSTOM_NAME, SmartChest.plain(name, ChatFormatting.GREEN));
		List<Component> lore = new ArrayList<>();
		for (String l : lines) lore.add(l.isEmpty() ? Component.empty() : SmartChest.plain(l, ChatFormatting.GRAY));
		s.set(DataComponents.LORE, new ItemLore(lore));
		return s;
	}

	@Override
	public void clicked(int slot, int button, ContainerInput input, Player p) {
		// Nunca chama o super: nada é movido pelo caminho normal.
		if (p instanceof ServerPlayer sp && (input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE)) {
			if (slot == CLOSE_SLOT) later(sp, sp::closeContainer);
			else if (slot == CLEAR_SLOT) later(sp, () -> open(sp, chest, pos, ""));
			else if (slot >= 0 && slot < slots.size() && slots.get(slot).container == shown) {
				int index = slots.get(slot).getContainerSlot();
				if (index < found.size() && !(index == RESULTS - 1 && found.size() > RESULTS)) {
					Craft c = found.get(index);
					String q = typed.trim();
					later(sp, () -> TableMenu.open(sp, chest, pos, c.recipe, q));
				}
			}
		}
		setCarried(ItemStack.EMPTY);
		sendAllDataToRemote(); // o cliente "previu" o clique: manda o estado real
	}

	private void later(ServerPlayer sp, Runnable r) {
		sp.level().getServer().execute(() -> {
			if (!sp.hasDisconnected() && sp.containerMenu == this) r.run();
		});
	}

	@Override
	public ItemStack quickMoveStack(Player p, int slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player p) {
		return SmartChest.exists(chest) && chest.hasTable(pos) && p.distanceToSqr(Vec3.atCenterOf(pos)) <= SmartChest.USE_RANGE_SQR;
	}

	@Override
	public void removed(Player p) {
		getSlot(CLOSE_SLOT).set(ItemStack.EMPTY); // os itens de exibição nunca vão para o inventário
		getSlot(CLEAR_SLOT).set(ItemStack.EMPTY);
		setCarried(ItemStack.EMPTY);
		resultSlots.setItem(0, ItemStack.EMPTY);
		super.removed(p);
		if (p instanceof ServerPlayer sp && !sp.hasDisconnected()) sp.inventoryMenu.sendAllDataToRemote(); // o inventário real volta
	}
}
