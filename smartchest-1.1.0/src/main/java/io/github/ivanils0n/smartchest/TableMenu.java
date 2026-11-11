package io.github.ivanils0n.smartchest;

import io.github.ivanils0n.smartchest.ChestStore.Chest;
import io.github.ivanils0n.smartchest.Storage.Linked;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.recipebook.PlaceRecipeHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Mesa de trabalho ligada a um Baú Central: a tela vanilla da mesa, mas o livro de receitas puxa os ingredientes do
 * inventário do jogador e, faltando, dos baús ligados. O cliente só conhece o inventário dele, então as receitas que
 * dependem dos baús aparecem em vermelho no livro, mas clicar nelas monta a receita.
 */
final class TableMenu extends CraftingMenu {
	final Chest chest;
	private final BlockPos pos;

	TableMenu(int id, Inventory inv, ServerLevel sl, BlockPos pos, Chest chest) {
		super(id, inv, ContainerLevelAccess.create(sl, pos));
		this.chest = chest;
		this.pos = pos.immutable();
	}

	static void open(ServerPlayer sp, Chest c, BlockPos pos) {
		ServerLevel sl = sp.level();
		sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new TableMenu(id, inv, sl, pos, c), Component.literal("Mesa do Baú Central")));
		SmartChest.warnUnloaded(sp, Storage.linked(sl, c, SmartChest.store).unloaded);
	}

	@Override
	public boolean stillValid(Player p) {
		return super.stillValid(p) && SmartChest.exists(chest) && chest.hasTable(pos);
	}

	/** Igual ao vanilla (ServerPlaceRecipe), mas contando e tirando também dos baús ligados. */
	@Override
	public PostPlaceAction handlePlacement(boolean useMax, boolean creative, RecipeHolder<?> holder, ServerLevel level, Inventory inv) {
		if (!(holder.value() instanceof CraftingRecipe recipe)) return PostPlaceAction.NOTHING;
		Linked linked = Storage.linked(level, chest, SmartChest.store);
		List<Slot> grid = getInputGridSlots();
		beginPlacingRecipe();
		try {
			int amount = 1;
			if (recipe.matches(craftSlots.asCraftInput(), level)) { // a mesma receita de novo: mais 1 em cada espaço
				amount = Integer.MAX_VALUE;
				for (Slot s : grid) if (s.hasItem()) amount = Math.min(amount, s.getItem().getCount() + 1);
			}
			for (Slot s : grid) { // a grade volta para o inventário; o que não couber, para os baús
				ItemStack st = s.getItem().copy();
				s.set(ItemStack.EMPTY);
				if (st.isEmpty()) continue;
				inv.add(st);
				if (!st.isEmpty()) Storage.insert(linked.targets(st), st);
				if (!st.isEmpty()) inv.placeItemBackInInventory(st);
			}
			resultSlots.clearContent();

			StackedItemContents have = new StackedItemContents();
			inv.fillStackedContents(have);
			for (Container c : linked.containers) for (int i = 0; i < c.getContainerSize(); i++) have.accountSimpleStack(c.getItem(i));
			int biggest = have.getBiggestCraftableStack(recipe, null);
			if (biggest <= 0) return PostPlaceAction.PLACE_GHOST_RECIPE;
			amount = useMax ? biggest : Math.min(amount, biggest);
			List<Holder<Item>> used = new ArrayList<>();
			if (!have.canCraft(recipe, amount, used::add)) return PostPlaceAction.NOTHING;
			int clamped = amount;
			for (Holder<Item> item : used) clamped = Math.min(clamped, item.components().getOrDefault(DataComponents.MAX_STACK_SIZE, 1));
			if (clamped != amount) {
				used.clear();
				if (!have.canCraft(recipe, clamped, used::add)) return PostPlaceAction.NOTHING;
			}
			int n = clamped;
			PlaceRecipeHelper.placeRecipe(3, 3, recipe, recipe.placementInfo().slotsToIngredientIndex(), (ingredient, slot, x, y) -> {
				if (ingredient != -1) grid.get(slot).set(take(inv, linked, used.get(ingredient), n));
			});
			return PostPlaceAction.NOTHING;
		} finally {
			@SuppressWarnings("unchecked")
			RecipeHolder<CraftingRecipe> typed = (RecipeHolder<CraftingRecipe>) holder;
			finishPlacingRecipe(level, typed);
		}
	}

	/** Tira {@code n} do item: primeiro do inventário do jogador, depois dos baús (só itens que o livro aceita). */
	private static ItemStack take(Inventory inv, Linked linked, Holder<Item> item, int n) {
		ItemStack[] got = {ItemStack.EMPTY};
		n = takeFrom(inv.getNonEquipmentItems(), item, got, n);
		inv.setChanged();
		for (Container c : linked.containers) {
			if (n == 0) break;
			List<ItemStack> stacks = new ArrayList<>();
			for (int i = 0; i < c.getContainerSize(); i++) stacks.add(c.getItem(i));
			int before = n;
			n = takeFrom(stacks, item, got, n);
			if (n != before) c.setChanged();
		}
		return got[0];
	}

	private static int takeFrom(List<ItemStack> stacks, Holder<Item> item, ItemStack[] got, int n) {
		for (ItemStack s : stacks) {
			if (n == 0) break;
			if (s.isEmpty() || !s.is(item) || !Inventory.isUsableForCrafting(s) || (!got[0].isEmpty() && !ItemStack.isSameItemSameComponents(got[0], s))) continue;
			int k = Math.min(n, s.getCount());
			if (got[0].isEmpty()) got[0] = s.copyWithCount(k);
			else got[0].grow(k);
			s.shrink(k);
			n -= k;
		}
		return n;
	}
}
