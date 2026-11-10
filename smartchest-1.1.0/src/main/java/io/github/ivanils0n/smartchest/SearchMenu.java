package io.github.ivanils0n.smartchest;

import io.github.ivanils0n.smartchest.ChestStore.Chest;
import io.github.ivanils0n.smartchest.Storage.Entry;
import io.github.ivanils0n.smartchest.Storage.Key;
import io.github.ivanils0n.smartchest.Storage.Linked;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.Vec3;

/**
 * Pesquisa: a tela da bigorna (o cliente vanilla já sabe desenhar a caixa de texto). O inventário que aparece embaixo
 * vira parte do baú: os 27 espaços mostram os itens dos baús ligados que batem com o texto (a cada letra) e a hotbar
 * é a fileira de "separados". O botão de pegar (espaço de resultado), Esc ou E levam os separados para o inventário
 * e voltam à tela do Baú Inteligente.
 *
 * O inventário de verdade nunca é usado: os espaços de baixo apontam para uma vitrine própria, nada sai dos baús
 * enquanto se separa, e ao sair o servidor reenvia o inventário real ao jogador.
 */
final class SearchMenu extends AnvilMenu {
	private static final int RESULT_SLOT = 2, RESULTS = 27, BASKET = 9;
	private static final int COST_DATA = 0; // único DataSlot da bigorna: o custo em níveis (0 = não mostra)

	/** Um item separado: o tipo, quantos e o nome no idioma do jogador (para a mensagem). */
	private static final class Picked {
		final ItemStack proto;
		final String name;
		int count;

		Picked(ItemStack proto, String name, int count) {
			this.proto = proto;
			this.name = name;
			this.count = count;
		}
	}

	private final ServerPlayer player;
	private final Chest chest;
	private final SimpleContainer shown = new SimpleContainer(RESULTS + BASKET); // 0..26 resultados, 27..35 separados
	private final List<Picked> basket = new ArrayList<>();
	private StorageMenu storage; // mesma busca, filtros e nomes da tela do baú
	private List<Entry> found = List.of();
	private String typed;
	private boolean done; // true depois de pegar/sair: nada roda duas vezes

	private SearchMenu(int id, Inventory inv, ServerPlayer player, Chest chest, String query) {
		super(id, inv);
		this.player = player;
		this.chest = chest;
		this.typed = query;
		// Os espaços do inventário da tela passam a apontar para a vitrine, nunca para o inventário de verdade.
		for (int i = 0; i < slots.size(); i++) {
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
		ItemStack field = new ItemStack(Items.WRITABLE_BOOK); // sem item aqui o jogo não deixa digitar
		field.set(DataComponents.CUSTOM_NAME, Component.literal(query)); // a caixa de texto começa com a pesquisa atual
		if (SmartChest.hasPack(player)) field.set(DataComponents.ITEM_MODEL, SmartChest.model("blank")); // invisível
		getSlot(0).set(field);
		refresh();
	}

	/** Para o teste automático: a mesma tela, sem abrir no cliente. */
	static SearchMenu detached(ServerPlayer player, Chest chest, String query) {
		return new SearchMenu(0, player.getInventory(), player, chest, query);
	}

	static void open(ServerPlayer player, Chest chest, String query) {
		// Com o pack, a bigorna ganha o fundo do Baú Inteligente (o título dela começa em x=60).
		Component title = SmartChest.hasPack(player) ? SmartChest.themedTitle("", "Pesquisar")
			: Component.literal("Pesquisar no Baú Inteligente");
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new SearchMenu(id, inv, player, chest, query), title));
	}

	@Override
	public boolean setItemName(String name) {
		typed = name == null ? "" : name; // o cliente manda o texto a cada letra digitada
		boolean changed = super.setItemName(name);
		refresh();
		sendAllDataToRemote(); // o cliente também calcula um resultado de bigorna; manda o nosso
		return changed;
	}

	@Override
	public void createResult() {
		if (player != null) refresh(); // a bigorna recalcula ao mudar o item ou o nome
	}

	// ---------- vitrine ----------

	private void refresh() {
		storage = StorageMenu.detached(player, chest, typed);
		found = storage.view();
		int visible = found.size() > RESULTS ? RESULTS - 1 : found.size();
		for (int i = 0; i < RESULTS; i++) shown.setItem(i, i < visible ? resultItem(found.get(i)) : ItemStack.EMPTY);
		if (found.size() > RESULTS) {
			shown.setItem(RESULTS - 1, icon(Items.PAPER, "btn_more", "... e mais " + (found.size() - visible),
				"Digite mais letras para achar o item."));
		}
		for (int j = 0; j < BASKET; j++) shown.setItem(RESULTS + j, j < basket.size() ? basketItem(basket.get(j)) : ItemStack.EMPTY);
		resultSlots.setItem(0, confirmItem());
		setData(COST_DATA, 0);
	}

	private long picked(ItemStack proto) {
		Picked p = find(proto);
		return p == null ? 0 : p.count;
	}

	private Picked find(ItemStack proto) {
		for (Picked p : basket) if (new Key(p.proto).equals(new Key(proto))) return p;
		return null;
	}

	private static ItemStack withCount(ItemStack proto, long n) {
		ItemStack s = proto.copy();
		if (Storage.maxStack(s) > 1) {
			s.set(DataComponents.MAX_STACK_SIZE, 99); // mostra até 99 no espaço; o número exato vai no texto
			s.setCount((int) Math.max(1, Math.min(n, 99)));
		}
		return s;
	}

	private ItemStack resultItem(Entry e) {
		long free = e.total - picked(e.proto);
		ItemStack s = withCount(e.proto, free);
		int pack = (int) Math.min(Storage.maxStack(e.proto), free);
		List<Component> lore = new ArrayList<>();
		lore.add(SmartChest.plain("No baú: " + e.total + (free < e.total ? " (" + (e.total - free) + " separados)" : ""), ChatFormatting.GRAY));
		lore.add(Component.empty());
		if (free <= 0) lore.add(SmartChest.plain("Tudo já foi separado", ChatFormatting.DARK_GRAY));
		else {
			lore.add(SmartChest.plain("Clique: separar " + pack, ChatFormatting.DARK_GRAY));
			lore.add(SmartChest.plain("Botão direito: separar " + Math.max(1, (pack + 1) / 2), ChatFormatting.DARK_GRAY));
		}
		s.set(DataComponents.LORE, new ItemLore(lore));
		return s;
	}

	private static ItemStack basketItem(Picked p) {
		ItemStack s = withCount(p.proto, p.count);
		s.set(DataComponents.LORE, new ItemLore(List.of(
			SmartChest.plain("Separado: " + p.count, ChatFormatting.GREEN),
			SmartChest.plain("Clique: tirar dos separados", ChatFormatting.DARK_GRAY))));
		return s;
	}

	private ItemStack confirmItem() {
		if (basket.isEmpty()) {
			return icon(Items.LIME_DYE, "btn_confirm", "Ver no Baú Inteligente", "Abre o baú com esta pesquisa.",
				"Clique nos resultados para separar itens.");
		}
		List<String> lines = new ArrayList<>();
		for (Picked p : basket) lines.add("• " + p.count + " " + p.name);
		lines.add("");
		lines.add("Leva para o seu inventário e volta ao baú.");
		lines.add("Esc também pega.");
		ItemStack s = icon(Items.LIME_DYE, "btn_confirm", "Pegar os separados", lines.toArray(String[]::new));
		s.setCount(basket.size());
		return s;
	}

	private ItemStack icon(net.minecraft.world.item.Item item, String model, String name, String... lines) {
		ItemStack s = new ItemStack(item);
		if (SmartChest.hasPack(player)) s.set(DataComponents.ITEM_MODEL, SmartChest.model(model));
		s.set(DataComponents.CUSTOM_NAME, SmartChest.plain(name, ChatFormatting.GREEN));
		List<Component> lore = new ArrayList<>();
		for (String l : lines) lore.add(l.isEmpty() ? Component.empty() : SmartChest.plain(l, ChatFormatting.GRAY));
		s.set(DataComponents.LORE, new ItemLore(lore));
		return s;
	}

	// ---------- cliques ----------

	@Override
	public void clicked(int slot, int button, ContainerInput input, Player p) {
		// Nunca chama o super: nada é movido pelo caminho normal.
		if (!done && p instanceof ServerPlayer sp && (input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE)) {
			if (slot == RESULT_SLOT) {
				finish(sp, true);
				return;
			}
			if (slot >= 0 && slot < slots.size() && slots.get(slot).container == shown) {
				int index = slots.get(slot).getContainerSlot();
				if (index < RESULTS) pick(sp, index, button == 1);
				else if (index - RESULTS < basket.size()) basket.remove(index - RESULTS);
				refresh();
			}
		}
		sendAllDataToRemote(); // o cliente "previu" o clique: manda o estado real
	}

	private void pick(ServerPlayer sp, int index, boolean half) {
		if (index >= found.size() || (index == RESULTS - 1 && found.size() > RESULTS)) return;
		Entry e = found.get(index);
		long free = e.total - picked(e.proto);
		if (free <= 0) return;
		int pack = (int) Math.min(Storage.maxStack(e.proto), free);
		int n = half ? Math.max(1, (pack + 1) / 2) : pack;
		Picked p = find(e.proto);
		if (p != null) p.count += n;
		else if (basket.size() < BASKET) basket.add(new Picked(e.proto.copyWithCount(1), storage.name(e), n));
		else sp.sendSystemMessage(SmartChest.plain("A fileira de separados está cheia (9 itens).", ChatFormatting.RED), true);
	}

	/**
	 * Botão de pegar, Esc ou E: leva os separados para o inventário e volta à tela do baú. Só pega se o jogador ainda
	 * está no jogo, vivo e perto do baú; senão os itens simplesmente continuam nos baús.
	 */
	private void finish(ServerPlayer sp, boolean reopen) {
		if (done) return;
		done = true;
		boolean canUse = !sp.hasDisconnected() && sp.isAlive() && stillValid(sp);
		if (canUse) takeBasket(sp);
		basket.clear();
		if (sp.hasDisconnected()) return;
		sp.inventoryMenu.sendAllDataToRemote(); // o inventário de verdade volta a aparecer
		if (!canUse) return;
		String q = typed.trim();
		sp.level().getServer().execute(() -> {
			if (sp.hasDisconnected()) return;
			sp.inventoryMenu.sendAllDataToRemote();
			if (reopen || sp.containerMenu == sp.inventoryMenu) StorageMenu.open(sp, chest, q, 0);
		});
	}

	/** Tira dos baús só o que cabe no inventário. O que não couber fica no baú; nada é perdido. */
	private void takeBasket(ServerPlayer sp) {
		if (basket.isEmpty()) return;
		Linked linked = Storage.linked(sp.level(), chest, SmartChest.store);
		Inventory inv = sp.getInventory();
		List<String> got = new ArrayList<>();
		boolean left = false;
		for (Picked p : basket) {
			int n = Math.min(p.count, Storage.space(inv, p.proto));
			if (n < p.count) left = true;
			if (n <= 0) continue;
			ItemStack taken = Storage.take(linked.containers, p.proto, n);
			int count = taken.getCount();
			if (count < n) left = true; // alguém tirou do baú enquanto você separava
			inv.add(taken);
			if (!taken.isEmpty()) Storage.insert(linked.containers, taken); // não coube (raro): volta para o baú
			if (!taken.isEmpty()) sp.drop(taken, false); // nem no baú: cai aos pés do jogador, nunca some
			if (count > 0) got.add(count + " " + p.name);
		}
		inv.setChanged();
		if (!got.isEmpty()) sp.sendSystemMessage(SmartChest.plain("Pegou: " + String.join(", ", got) + ".", ChatFormatting.GREEN));
		if (left) {
			sp.sendSystemMessage(SmartChest.plain("Parte dos separados ficou no baú (inventário cheio ou o item acabou).", ChatFormatting.YELLOW));
		}
	}

	// ---------- vanilla ----------

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
		if (p instanceof ServerPlayer sp) finish(sp, false); // Esc, E ou fechada pelo servidor
	}
}
