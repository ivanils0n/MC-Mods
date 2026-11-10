package io.github.ivanils0n.smartchest;

import io.github.ivanils0n.smartchest.ChestStore.Chest;
import io.github.ivanils0n.smartchest.Storage.Entry;
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
 * é a fileira de "separados". O jogador pega o item com o mouse e solta na fileira, como num baú comum (botão direito
 * pega metade / solta 1; Shift+clique manda o pacote direto). O botão de pegar (espaço de resultado), Esc ou E levam
 * os separados para o inventário e voltam à tela do Baú Central; a seta (1º espaço da bigorna) volta sem levar nada.
 *
 * Tudo aqui é vitrine: os espaços de baixo apontam para um inventário de mentira, o item "na mão" é só um desenho e
 * nada sai dos baús enquanto se separa. Ao sair o servidor reenvia o inventário real ao jogador.
 */
final class SearchMenu extends AnvilMenu {
	private static final int BACK_SLOT = 0, CLEAR_SLOT = 1, RESULT_SLOT = 2, RESULTS = 27, BASKET = 9;
	private static final int COST_DATA = 0; // único DataSlot da bigorna: o custo em níveis (0 = não mostra)

	/** Um espaço da fileira de separados: o tipo e quantos. */
	private static final class Picked {
		final ItemStack proto;
		int count;

		Picked(ItemStack proto, int count) {
			this.proto = proto;
			this.count = count;
		}
	}

	private final ServerPlayer player;
	final Chest chest;
	private final SimpleContainer shown = new SimpleContainer(RESULTS + BASKET); // 0..26 resultados, 27..35 separados
	private final Picked[] basket = new Picked[BASKET];
	private StorageMenu storage; // mesma busca, filtros e nomes da tela do baú
	private List<Entry> found = List.of();
	private String typed;
	private boolean done; // true depois de pegar/sair: nada roda duas vezes

	private SearchMenu(int id, Inventory inv, ServerPlayer player, Chest chest, String query, Picked[] kept) {
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
		// Sem item no 1º espaço o jogo não deixa digitar, e a caixa de texto começa com o nome dele (a pesquisa atual).
		// Esse item é a seta de voltar.
		ItemStack field = icon(Items.ARROW, "btn_prev", "", "Clique: voltar ao Baú Central,", "sem a pesquisa e sem os separados.");
		field.set(DataComponents.CUSTOM_NAME, Component.literal(query));
		getSlot(BACK_SLOT).set(field);
		if (kept != null) System.arraycopy(kept, 0, basket, 0, BASKET); // voltando de "limpar a pesquisa"
		getSlot(CLEAR_SLOT).set(icon(Items.BARRIER, "btn_clear", "Limpar a pesquisa", "Os separados continuam na fileira."));
		refresh();
	}

	/** Para o teste automático: a mesma tela, sem abrir no cliente. */
	static SearchMenu detached(ServerPlayer player, Chest chest, String query) {
		return new SearchMenu(0, player.getInventory(), player, chest, query, null);
	}

	static void open(ServerPlayer player, Chest chest, String query) {
		open(player, chest, query, null);
	}

	private static void open(ServerPlayer player, Chest chest, String query, Picked[] kept) {
		// Com o pack, a bigorna ganha o fundo do Baú Central (o título dela começa em x=60).
		Component title = SmartChest.hasPack(player) ? SmartChest.themedTitle("", "Pesquisar")
			: Component.literal("Pesquisar no Baú Central");
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new SearchMenu(id, inv, player, chest, query, kept), title));
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
		for (int j = 0; j < BASKET; j++) shown.setItem(RESULTS + j, basket[j] == null ? ItemStack.EMPTY : basketItem(basket[j]));
		resultSlots.setItem(0, confirmItem());
		setData(COST_DATA, 0);
	}

	/** Quanto desse item já está reservado: nos separados e na mão. */
	private long picked(ItemStack proto) {
		long n = ItemStack.isSameItemSameComponents(getCarried(), proto) ? getCarried().getCount() : 0;
		for (Picked p : basket) if (p != null && ItemStack.isSameItemSameComponents(p.proto, proto)) n += p.count;
		return n;
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
		lore.add(SmartChest.plain("Em " + SmartChest.count(e.chests, "baú", "baús"), ChatFormatting.GRAY));
		lore.add(SmartChest.plain("Categoria: " + Categories.NAMES[Categories.of(e.proto)], ChatFormatting.DARK_GRAY));
		lore.add(Component.empty());
		if (free <= 0) lore.add(SmartChest.plain("Tudo já foi separado", ChatFormatting.DARK_GRAY));
		else {
			lore.add(SmartChest.plain("Clique: pegar " + pack + " e soltar na fileira de baixo", ChatFormatting.DARK_GRAY));
			lore.add(SmartChest.plain("Botão direito: pegar " + Math.max(1, (pack + 1) / 2), ChatFormatting.DARK_GRAY));
			lore.add(SmartChest.plain("Shift+clique: " + pack + " direto para a fileira", ChatFormatting.DARK_GRAY));
		}
		s.set(DataComponents.LORE, new ItemLore(lore));
		return s;
	}

	private static ItemStack basketItem(Picked p) {
		ItemStack s = p.proto.copyWithCount(p.count);
		s.set(DataComponents.LORE, new ItemLore(List.of(
			SmartChest.plain("Separado: " + p.count, ChatFormatting.GREEN),
			SmartChest.plain("Clique: pegar de volta na mão", ChatFormatting.DARK_GRAY),
			SmartChest.plain("Botão direito: pegar metade", ChatFormatting.DARK_GRAY),
			SmartChest.plain("Shift+clique: devolver ao baú", ChatFormatting.DARK_GRAY))));
		return s;
	}

	/** Separados somados por tipo, na ordem da fileira. */
	private List<Picked> totals() {
		List<Picked> out = new ArrayList<>();
		for (Picked p : basket) {
			if (p == null) continue;
			Picked same = null;
			for (Picked o : out) if (ItemStack.isSameItemSameComponents(o.proto, p.proto)) same = o;
			if (same != null) same.count += p.count;
			else out.add(new Picked(p.proto, p.count));
		}
		return out;
	}

	private ItemStack confirmItem() {
		List<Picked> totals = totals();
		if (totals.isEmpty()) {
			return icon(Items.EMERALD, "btn_confirm", "Ver no Baú Central", "Abre o baú com esta pesquisa.",
				"Leve itens para a fileira de baixo para separar.");
		}
		List<String> lines = new ArrayList<>();
		for (Picked p : totals) lines.add("• " + p.count + " " + storage.name(p.proto));
		lines.add("");
		lines.add("Leva para o seu inventário e volta ao baú.");
		lines.add("Esc também pega.");
		ItemStack s = icon(Items.EMERALD, "btn_confirm", "Pegar os separados", lines.toArray(String[]::new));
		s.setCount(totals.size());
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
			if (slot == BACK_SLOT) {
				back(sp);
				return;
			}
			if (slot == CLEAR_SLOT) {
				clear(sp);
				return;
			}
			boolean shift = input == ContainerInput.QUICK_MOVE, right = button == 1;
			if (slot == -999) setCarried(ItemStack.EMPTY); // soltou fora da tela: o item (de mentira) volta para o baú
			else if (slot >= 0 && slot < slots.size() && slots.get(slot).container == shown) {
				int index = slots.get(slot).getContainerSlot();
				if (index < RESULTS) clickResult(sp, index, shift, right);
				else clickBasket(index - RESULTS, shift, right);
			}
			refresh();
		}
		sendAllDataToRemote(); // o cliente "previu" o clique: manda o estado real
	}

	/** Resultado: com a mão vazia pega (ou manda direto com Shift); com item na mão, devolve ao baú. */
	private void clickResult(ServerPlayer sp, int index, boolean shift, boolean right) {
		ItemStack carried = getCarried();
		if (!carried.isEmpty()) {
			if (right) carried.shrink(1);
			else setCarried(ItemStack.EMPTY);
			return;
		}
		if (index >= found.size() || (index == RESULTS - 1 && found.size() > RESULTS)) return;
		Entry e = found.get(index);
		long free = e.total - picked(e.proto);
		if (free <= 0) return;
		int pack = (int) Math.min(Storage.maxStack(e.proto), free);
		if (!shift) {
			setCarried(e.proto.copyWithCount(right ? Math.max(1, (pack + 1) / 2) : pack));
			return;
		}
		int max = Storage.maxStack(e.proto);
		for (int j = 0; j < BASKET && pack > 0; j++) { // completa montes iguais
			Picked b = basket[j];
			if (b == null || !ItemStack.isSameItemSameComponents(b.proto, e.proto)) continue;
			int n = Math.min(max - b.count, pack);
			b.count += n;
			pack -= n;
		}
		for (int j = 0; j < BASKET && pack > 0; j++) {
			if (basket[j] != null) continue;
			basket[j] = new Picked(e.proto.copyWithCount(1), pack);
			pack = 0;
		}
		if (pack > 0) sp.sendSystemMessage(SmartChest.plain("A fileira de separados está cheia.", ChatFormatting.RED), true);
	}

	/** Fileira de separados: funciona como espaços de um baú comum, só que com itens de mentira. */
	private void clickBasket(int j, boolean shift, boolean right) {
		ItemStack carried = getCarried();
		Picked b = basket[j];
		if (carried.isEmpty()) {
			if (b == null) return;
			int n = shift ? 0 : right ? (b.count + 1) / 2 : b.count; // Shift: devolve ao baú sem passar pela mão
			if (n > 0) setCarried(b.proto.copyWithCount(n));
			b.count = shift ? 0 : b.count - n;
			if (b.count <= 0) basket[j] = null;
		} else if (b == null) {
			int n = right ? 1 : carried.getCount();
			basket[j] = new Picked(carried.copyWithCount(1), n);
			carried.shrink(n);
		} else if (ItemStack.isSameItemSameComponents(b.proto, carried)) {
			int n = Math.min(Storage.maxStack(b.proto) - b.count, right ? 1 : carried.getCount());
			b.count += n;
			carried.shrink(n);
		} else { // item diferente: troca
			basket[j] = new Picked(carried.copyWithCount(1), carried.getCount());
			setCarried(b.proto.copyWithCount(b.count));
		}
	}

	/** X: a caixa de texto é do cliente, então limpar é abrir a pesquisa de novo, vazia, com os mesmos separados. */
	private void clear(ServerPlayer sp) {
		done = true; // esta tela não pega nada ao fechar
		setCarried(ItemStack.EMPTY);
		Picked[] kept = basket.clone();
		sp.level().getServer().execute(() -> {
			if (!sp.hasDisconnected()) open(sp, chest, "", kept);
		});
	}

	/** Seta de voltar: tela do baú sem a pesquisa e sem os separados (nada saiu dos baús). */
	private void back(ServerPlayer sp) {
		done = true;
		setCarried(ItemStack.EMPTY);
		sp.level().getServer().execute(() -> {
			if (sp.hasDisconnected()) return;
			if (sp.isAlive() && stillValid(sp)) StorageMenu.open(sp, chest, "", 0);
			else {
				sp.closeContainer();
				sp.inventoryMenu.sendAllDataToRemote(); // o inventário de verdade volta a aparecer
			}
		});
	}

	/**
	 * Botão de pegar, Esc ou E: leva os separados para o inventário e volta à tela do baú. Só pega se o jogador ainda
	 * está no jogo, vivo e perto do baú; senão os itens simplesmente continuam nos baús.
	 */
	private void finish(ServerPlayer sp, boolean reopen) {
		if (done) return;
		done = true;
		setCarried(ItemStack.EMPTY); // o que estava na mão sem soltar na fileira fica no baú
		boolean canUse = !sp.hasDisconnected() && sp.isAlive() && stillValid(sp);
		if (canUse) takeBasket(sp);
		java.util.Arrays.fill(basket, null);
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

	/**
	 * Tira dos baús o que foi separado: cada espaço da fileira vai para o mesmo espaço da hotbar se ele estiver livre
	 * (ou com o mesmo item), senão para qualquer lugar do inventário. O que não couber volta para o baú; nada é perdido.
	 */
	private void takeBasket(ServerPlayer sp) {
		Linked linked = Storage.linked(sp.level(), chest, SmartChest.store);
		Inventory inv = sp.getInventory();
		boolean left = false;
		List<Picked> got = new ArrayList<>();
		for (int j = 0; j < BASKET; j++) {
			Picked p = basket[j];
			if (p == null) continue;
			int n = Math.min(p.count, Storage.space(inv, p.proto));
			if (n < p.count) left = true;
			if (n <= 0) continue;
			ItemStack taken = Storage.take(linked.containers, p.proto, n);
			int count = taken.getCount();
			if (count < n) left = true; // alguém tirou do baú enquanto você separava
			ItemStack there = inv.getItem(j);
			if (there.isEmpty()) inv.setItem(j, taken.split(Math.min(count, Storage.maxStack(taken))));
			else if (ItemStack.isSameItemSameComponents(there, taken)) {
				int fit = Math.min(Storage.maxStack(there) - there.getCount(), count);
				there.grow(fit);
				taken.shrink(fit);
			}
			if (!taken.isEmpty()) inv.add(taken);
			if (!taken.isEmpty()) Storage.insert(linked.targets(taken), taken); // não coube (raro): volta para o baú
			if (!taken.isEmpty()) sp.drop(taken, false); // nem no baú: cai aos pés do jogador, nunca some
			if (count > 0) got.add(new Picked(p.proto, count));
		}
		if (got.isEmpty() && !left) return;
		inv.setChanged();
		List<String> names = new ArrayList<>();
		for (Picked p : got) names.add(p.count + " " + storage.name(p.proto));
		if (!names.isEmpty()) sp.sendSystemMessage(SmartChest.plain("Pegou: " + String.join(", ", names) + ".", ChatFormatting.GREEN));
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
		getSlot(BACK_SLOT).set(ItemStack.EMPTY); // os itens de exibição nunca vão para o inventário
		getSlot(CLEAR_SLOT).set(ItemStack.EMPTY);
		setCarried(ItemStack.EMPTY); // o item na mão é de mentira
		resultSlots.setItem(0, ItemStack.EMPTY);
		super.removed(p);
		if (p instanceof ServerPlayer sp) finish(sp, false); // Esc, E ou fechada pelo servidor
	}
}
