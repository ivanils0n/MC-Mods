package io.github.ivanils0n.smartchest;

import io.github.ivanils0n.smartchest.ChestStore.Chest;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.Vec3;

/**
 * Caixa de texto da pesquisa: a tela da bigorna (o cliente vanilla já sabe desenhá-la). A cada letra digitada o
 * resultado mostra o primeiro item encontrado e quantos são; clicar nele volta ao Baú Inteligente filtrado. Esc volta
 * sem mudar a pesquisa. Nenhum item entra ou sai; não custa experiência.
 */
final class SearchMenu extends AnvilMenu {
	private static final int RESULT_SLOT = 2;
	private static final int COST_DATA = 0; // único DataSlot da bigorna: o custo em níveis (0 = não mostra)

	private final ServerPlayer player;
	private final Chest chest;
	private final String previous;
	private String typed;
	private boolean done; // true quando o fechamento é nosso, não um Esc do jogador

	private SearchMenu(int id, Inventory inv, ServerPlayer player, Chest chest, String query) {
		super(id, inv);
		this.player = player;
		this.chest = chest;
		this.previous = query;
		this.typed = query;
		ItemStack shown = new ItemStack(Items.WRITABLE_BOOK);
		shown.set(DataComponents.CUSTOM_NAME, Component.literal(query)); // o campo de texto começa com a pesquisa atual
		shown.set(DataComponents.CUSTOM_MODEL_DATA, SmartChest.model("smartchest_search"));
		getSlot(0).set(shown);
		preview();
	}

	static void open(ServerPlayer player, Chest chest, String query) {
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new SearchMenu(id, inv, player, chest, query),
			Component.literal("Pesquisar no Baú Inteligente")));
	}

	@Override
	public boolean setItemName(String name) {
		typed = name == null ? "" : name; // o cliente manda o texto a cada letra digitada
		boolean changed = super.setItemName(name);
		preview();
		sendAllDataToRemote(); // o cliente também calcula um resultado de bigorna; manda o nosso
		return changed;
	}

	@Override
	public void createResult() {
		if (player != null) preview(); // a bigorna recalcula ao mudar o item ou o nome
	}

	/** Mostra no espaço de resultado o primeiro item que bate com o texto e quantos são. */
	private void preview() {
		StorageMenu storage = StorageMenu.detached(player, chest, typed); // mesma busca e mesmos nomes da tela do baú
		List<Storage.Entry> found = storage.view();
		ItemStack r;
		if (found.isEmpty()) {
			r = new ItemStack(Items.BARRIER);
			r.set(DataComponents.CUSTOM_NAME, SmartChest.plain("Nada encontrado", ChatFormatting.RED));
			r.set(DataComponents.LORE, new ItemLore(List.of(SmartChest.plain("Clique para ver mesmo assim", ChatFormatting.GRAY))));
		} else {
			r = found.get(0).proto.copyWithCount(1);
			String head = typed.isBlank() ? "Mostrar tudo" : SmartChest.count(found.size(), "item encontrado", "itens encontrados");
			r.set(DataComponents.CUSTOM_NAME, SmartChest.plain(head, ChatFormatting.LIGHT_PURPLE));
			List<Component> lore = new ArrayList<>();
			for (int i = 0; i < Math.min(5, found.size()); i++) {
				lore.add(SmartChest.plain("• " + storage.name(found.get(i)), ChatFormatting.GRAY));
			}
			if (found.size() > 5) lore.add(SmartChest.plain("... e mais " + (found.size() - 5), ChatFormatting.DARK_GRAY));
			lore.add(SmartChest.plain("Clique aqui para ver", ChatFormatting.GREEN));
			r.set(DataComponents.LORE, new ItemLore(lore));
		}
		resultSlots.setItem(0, r);
		setData(COST_DATA, 0);
	}

	@Override
	public void clicked(int slot, int button, ContainerInput input, Player p) {
		// Nunca chama o super: nada pode ser pego ou movido. Reenvia o estado real ao cliente.
		sendAllDataToRemote();
		if (done || slot != RESULT_SLOT || !(p instanceof ServerPlayer sp)) return;
		done = true;
		String q = typed.trim();
		sp.level().getServer().execute(() -> {
			if (!sp.hasDisconnected()) StorageMenu.open(sp, chest, q, 0);
		});
	}

	@Override
	public ItemStack quickMoveStack(Player p, int slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player p) {
		return SmartChest.exists(chest) && chest.dim.equals(SmartChest.dim(p.level()))
			&& p.distanceToSqr(Vec3.atCenterOf(chest.pos())) <= SmartChest.USE_RANGE_SQR;
	}

	@Override
	public void removed(Player p) {
		getSlot(0).set(ItemStack.EMPTY); // o item de exibição nunca vai para o inventário
		resultSlots.setItem(0, ItemStack.EMPTY);
		super.removed(p);
		// Esc: volta para o baú com a pesquisa de antes (se ainda dá para usar o baú).
		if (!done && p instanceof ServerPlayer sp && stillValid(sp)) {
			done = true;
			sp.level().getServer().execute(() -> {
				if (!sp.hasDisconnected() && sp.containerMenu == sp.inventoryMenu) StorageMenu.open(sp, chest, previous, 0);
			});
		}
	}
}
