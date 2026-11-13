package io.github.ivanils0n.smartchest;

import io.github.ivanils0n.smartchest.ChestStore.Chest;
import io.github.ivanils0n.smartchest.Storage.Linked;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundRecipeBookSettingsPacket;
import net.minecraft.recipebook.PlaceRecipeHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.RecipeBookSettings;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Mesa de trabalho ligada a um Baú Central: abre pela pesquisa (CraftSearchMenu) já com a receita escolhida montada
 * com os itens dos baús ligados (o máximo que der; faltando algum, monta o que tiver) e remonta sozinha quando acaba.
 * A grade é da mesa: o jogador não põe nem tira nada dela, só pega o resultado; ao fechar, o que sobrou volta para
 * os baús. O livro de receitas fica fechado, e o botão dele (coberto pela seta do fundo) volta para a pesquisa.
 */
final class TableMenu extends CraftingMenu {
	final Chest chest;
	private final BlockPos pos;
	private final String query; // a pesquisa de onde veio, para a seta de voltar
	private RecipeHolder<?> recipe; // remontada quando o jogador usa tudo da grade
	private boolean crafted, placing;
	private RecipeBookSettings.TypeSettings bookBefore; // como o livro do jogador estava, para devolver ao sair

	TableMenu(int id, Inventory inv, ServerLevel sl, BlockPos pos, Chest chest, String query) {
		super(id, inv, ContainerLevelAccess.create(sl, pos));
		this.chest = chest;
		this.pos = pos.immutable();
		this.query = query;
	}

	static void open(ServerPlayer sp, Chest c, BlockPos pos, RecipeHolder<?> recipe, String query) {
		ServerLevel sl = sp.level();
		RecipeBookSettings book = sp.getRecipeBook().getBookSettings();
		RecipeBookSettings.TypeSettings before = new RecipeBookSettings.TypeSettings(book.isOpen(RecipeBookType.CRAFTING), book.isFiltering(RecipeBookType.CRAFTING));
		RecipeBookSettings closed = book.copy(); // a tela abre com o livro fechado
		closed.setOpen(RecipeBookType.CRAFTING, false);
		sp.connection.send(new ClientboundRecipeBookSettingsPacket(closed));
		Component title = SmartChest.hasPack(sp) ? SmartChest.themedTitle("\uF005\uE002", "") : Component.literal("Mesa do Baú Central");
		sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new TableMenu(id, inv, sl, pos, c, query), title));
		if (sp.containerMenu instanceof TableMenu m) {
			m.bookBefore = before;
			m.handlePlacement(true, false, recipe, sl, sp.getInventory());
		}
	}

	/** O botão do livro (a seta) foi clicado: volta para a pesquisa. Chamado pelo mixin. */
	static void bookButton(ServerPlayer sp) {
		if (!(sp.containerMenu instanceof TableMenu m)) return;
		sp.getRecipeBook().setBookSetting(RecipeBookType.CRAFTING, false, sp.getRecipeBook().getBookSettings().isFiltering(RecipeBookType.CRAFTING));
		CraftSearchMenu.open(sp, m.chest, m.pos, m.query);
	}

	@Override
	public void slotsChanged(Container container) {
		super.slotsChanged(container);
		if (!placing && container == craftSlots) crafted = true;
	}

	/** Roda a cada tick: se a grade acabou depois de um craft, monta a mesma receita de novo com o que tiver nos baús. */
	@Override
	public void broadcastChanges() {
		if (crafted && recipe != null && resultSlots.getItem(0).isEmpty() && owner() instanceof ServerPlayer sp) {
			crafted = false;
			handlePlacement(true, false, recipe, sp.level(), sp.getInventory());
		}
		crafted = false;
		super.broadcastChanges();
	}

	@Override
	public boolean stillValid(Player p) {
		return super.stillValid(p) && SmartChest.exists(chest) && chest.hasTable(pos);
	}

	/** Como o vanilla (ServerPlaceRecipe), mas com os itens dos baús ligados e montando a receita incompleta. */
	@Override
	public PostPlaceAction handlePlacement(boolean useMax, boolean creative, RecipeHolder<?> holder, ServerLevel level, Inventory inv) {
		if (!(holder.value() instanceof CraftingRecipe recipe)) return PostPlaceAction.NOTHING;
		this.recipe = holder;
		Linked linked = Storage.linked(level, chest, SmartChest.store);
		List<Slot> grid = getInputGridSlots();
		beginPlacingRecipe();
		placing = true;
		try {
			int amount = 1;
			if (recipe.matches(craftSlots.asCraftInput(), level)) { // a mesma receita de novo: mais 1 em cada espaço
				amount = Integer.MAX_VALUE;
				for (Slot s : grid) if (s.hasItem()) amount = Math.min(amount, s.getItem().getCount() + 1);
			}
			giveBack(linked, inv);

			StackedItemContents have = new StackedItemContents();
			for (Container c : linked.containers) for (int i = 0; i < c.getContainerSize(); i++) have.accountSimpleStack(c.getItem(i));
			inv.fillStackedContents(have); // e o inventário do jogador
			int biggest = have.getBiggestCraftableStack(recipe, null);
			if (biggest <= 0) { // falta item: monta o que tiver (1 em cada espaço); sem nada, mostra a receita fantasma
				List<Ingredient> ingredients = recipe.placementInfo().ingredients();
				boolean[] any = {false};
				PlaceRecipeHelper.placeRecipe(3, 3, recipe, recipe.placementInfo().slotsToIngredientIndex(), (ingredient, slot, x, y) -> {
					if (ingredient == -1) return;
					ItemStack got = take(linked, inv, ingredients.get(ingredient)::test, 1);
					grid.get(slot).set(got);
					any[0] |= !got.isEmpty();
				});
				return any[0] ? PostPlaceAction.NOTHING : PostPlaceAction.PLACE_GHOST_RECIPE;
			}
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
				if (ingredient != -1) grid.get(slot).set(take(linked, inv, st -> st.is(used.get(ingredient)), n));
			});
			return PostPlaceAction.NOTHING;
		} finally {
			placing = false;
			@SuppressWarnings("unchecked")
			RecipeHolder<CraftingRecipe> typed = (RecipeHolder<CraftingRecipe>) holder;
			finishPlacingRecipe(level, typed);
		}
	}

	/** O que sobrou na grade volta para os baús (o que não couber, para o jogador). */
	private void giveBack(Linked linked, Inventory inv) {
		for (Slot s : getInputGridSlots()) {
			ItemStack st = s.getItem().copy();
			s.set(ItemStack.EMPTY);
			if (st.isEmpty()) continue;
			Storage.insert(linked.targets(st), st);
			if (!st.isEmpty()) inv.placeItemBackInInventory(st);
		}
		resultSlots.clearContent();
	}

	@Override
	public void removed(Player p) {
		if (p instanceof ServerPlayer sp) {
			giveBack(Storage.linked(sp.level(), chest, SmartChest.store), sp.getInventory());
			if (bookBefore != null) { // o livro volta a ser como o jogador deixou nas outras mesas
				sp.getRecipeBook().setBookSetting(RecipeBookType.CRAFTING, bookBefore.open(), bookBefore.filtering());
				sp.connection.send(new ClientboundRecipeBookSettingsPacket(sp.getRecipeBook().getBookSettings().copy()));
			}
		}
		super.removed(p);
	}

	/** Na grade não se põe nem se tira nada (nem com Shift, números, arrastar ou clique duplo): só o resultado sai. */
	@Override
	public void clicked(int slot, int button, ContainerInput input, Player p) {
		if (slot >= 1 && slot < 10) {
			sendAllDataToRemote();
			return;
		}
		super.clicked(slot, button, input, p);
	}

	@Override
	public boolean canTakeItemForPickAll(ItemStack carried, Slot target) {
		return target.container != craftSlots && super.canTakeItemForPickAll(carried, target);
	}

	@Override
	public ItemStack quickMoveStack(Player p, int index) {
		if (index < 10) return super.quickMoveStack(p, index);
		Slot slot = slots.get(index); // Shift no inventário: só entre inventário e hotbar, nunca para a grade
		if (!slot.hasItem()) return ItemStack.EMPTY;
		ItemStack st = slot.getItem(), before = st.copy();
		if (!(index < 37 ? moveItemStackTo(st, 37, 46, false) : moveItemStackTo(st, 10, 37, false))) return ItemStack.EMPTY;
		if (st.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
		else slot.setChanged();
		return before;
	}

	/** Tira {@code n} itens que passam no teste dos baús ligados (só os que o livro aceita: sem dano, encanto ou nome). */
	private static ItemStack take(Linked linked, Inventory inv, Predicate<ItemStack> item, int n) {
		ItemStack[] got = {ItemStack.EMPTY};
		for (Container c : linked.containers) {
			if (n == 0) break;
			List<ItemStack> stacks = new ArrayList<>();
			for (int i = 0; i < c.getContainerSize(); i++) stacks.add(c.getItem(i));
			int before = n;
			n = takeFrom(stacks, item, got, n);
			if (n != before) c.setChanged();
		}
		if (n > 0 && takeFrom(inv.getNonEquipmentItems(), item, got, n) != n) inv.setChanged(); // faltou nos baús: do inventário
		return got[0];
	}

	private static int takeFrom(List<ItemStack> stacks, Predicate<ItemStack> item, ItemStack[] got, int n) {
		for (ItemStack s : stacks) {
			if (n == 0) break;
			if (s.isEmpty() || !item.test(s) || !Inventory.isUsableForCrafting(s) || (!got[0].isEmpty() && !ItemStack.isSameItemSameComponents(got[0], s))) continue;
			int k = Math.min(n, s.getCount());
			if (got[0].isEmpty()) got[0] = s.copyWithCount(k);
			else got[0].grow(k);
			s.shrink(k);
			n -= k;
		}
		return n;
	}
}
