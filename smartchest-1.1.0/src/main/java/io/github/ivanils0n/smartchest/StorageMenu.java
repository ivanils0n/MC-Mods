package io.github.ivanils0n.smartchest;

import io.github.ivanils0n.smartchest.ChestStore.Chest;
import io.github.ivanils0n.smartchest.Storage.Entry;
import io.github.ivanils0n.smartchest.Storage.Key;
import io.github.ivanils0n.smartchest.Storage.Linked;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.Vec3;

/**
 * Tela do Baú Inteligente (baú duplo vanilla): 5 fileiras com os itens de todos os baús ligados, somados por tipo, e
 * a última fileira com os botões [◀ página ▶] [pesquisar ordenar] [guardar vínculos]. Os espaços de cima são só
 * vitrine (nunca guardam item): cada clique é tratado aqui e mexe direto nos baús ligados.
 */
final class StorageMenu extends ChestMenu {
	static final int GRID = 45;
	static final int PREV = 45, INFO = 46, NEXT = 47, SEARCH = 49, SORT = 50, DEPOSIT = 52, LINKS = 53;
	private static final String[] SORTS = {"Quantidade", "Nome", "Mod / id"};
	private static final NumberFormat NUM = NumberFormat.getIntegerInstance(Locale.forLanguageTag("pt-BR"));

	private final ServerPlayer player;
	final Chest chest;
	private final SimpleContainer box;
	final String query;
	private final Function<String, String> tr;
	private final Map<Key, String> names = new HashMap<>();
	private final Map<Key, String> haystacks = new HashMap<>();
	private List<Entry> view = List.of();
	private int page, pages = 1, ticks, unloaded;

	private StorageMenu(int id, Inventory inv, SimpleContainer box, ServerPlayer player, Chest chest, String query, int page) {
		super(MenuType.GENERIC_9x6, id, inv, box, 6);
		this.player = player;
		this.chest = chest;
		this.box = box;
		this.query = query;
		this.page = page;
		this.tr = Lang.forPlayer(player.level().getServer(), player);
		// Espaços do baú: nada entra nem sai pelo caminho normal (o clique é tratado em clicked()).
		for (int i = 0; i < 54; i++) {
			Slot old = slots.get(i);
			Slot slot = new Slot(box, old.getContainerSlot(), old.x, old.y) {
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
		refresh();
	}

	static void open(ServerPlayer p, Chest chest, String query, int page) {
		SimpleContainer box = new SimpleContainer(54);
		p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new StorageMenu(id, inv, box, p, chest, query, page), SmartChest.title(p, query)));
	}

	/** A mesma tela sem abrir no cliente: prévia da pesquisa na bigorna e teste automático. */
	static StorageMenu detached(ServerPlayer p, Chest chest, String query) {
		return new StorageMenu(0, p.getInventory(), new SimpleContainer(54), p, chest, query, 0);
	}

	private int sortMode() {
		return SmartChest.SORT.getOrDefault(player.getUUID(), 0);
	}

	private Linked linked() {
		return Storage.linked(player.level(), chest, SmartChest.store);
	}

	List<Entry> view() {
		return view;
	}

	/** Baús ligados que não estão carregados agora (fora da distância de visão). */
	int unloaded() {
		return unloaded;
	}

	/** Lê os baús de novo e redesenha a página. */
	void refresh() {
		Linked linked = linked();
		unloaded = linked.unloaded;
		List<Entry> all = Storage.scan(linked.containers);
		String q = Lang.normalize(query.trim());
		List<Entry> list = new ArrayList<>();
		for (Entry e : all) if (q.isEmpty() || haystack(e).contains(q)) list.add(e);
		Comparator<Entry> byName = Comparator.comparing(e -> Lang.normalize(name(e)));
		list.sort(switch (sortMode()) {
			case 1 -> byName;
			case 2 -> Comparator.comparing((Entry e) -> id(e)).thenComparing(byName);
			default -> Comparator.comparingLong((Entry e) -> e.total).reversed().thenComparing(byName);
		});
		view = list;
		pages = Math.max(1, (list.size() + GRID - 1) / GRID);
		page = Math.clamp(page, 0, pages - 1);
		for (int i = 0; i < GRID; i++) {
			int idx = page * GRID + i;
			box.setItem(i, idx < list.size() ? display(list.get(idx)) : ItemStack.EMPTY);
		}
		long items = 0;
		for (Entry e : all) items += e.total;
		int links = chest.links.size();
		List<String> info = new ArrayList<>();
		info.add(SmartChest.count(all.size(), "tipo de item", "tipos de item") + (q.isEmpty() ? "" : " (" + list.size() + " na pesquisa)"));
		info.add("Itens: " + NUM.format(items));
		info.add(SmartChest.count(links, "baú ligado", "baús ligados") + (linked.unloaded > 0 ? " (" + linked.unloaded + " fora da distância de visão)" : ""));
		if (linked.totalSlots > 0) {
			info.add("Espaço usado: " + linked.usedSlots + "/" + linked.totalSlots + " (" + linked.usedSlots * 100 / linked.totalSlots + "%)");
		}
		ItemStack book = button(Items.BOOK, "smartchest_info", "Página " + (page + 1) + " de " + pages, info.toArray(String[]::new));
		book.set(DataComponents.MAX_STACK_SIZE, 99);
		book.setCount(Math.min(page + 1, 99));
		box.setItem(INFO, book);
		box.setItem(PREV, button(Items.ARROW, "smartchest_prev", "Página anterior", page > 0 ? "Clique: voltar" : "Esta é a primeira página"));
		box.setItem(NEXT, button(Items.ARROW, "smartchest_next", "Próxima página", page < pages - 1 ? "Clique: avançar" : "Esta é a última página"));
		box.setItem(SEARCH, button(Items.WRITABLE_BOOK, "smartchest_search", query.isEmpty() ? "Pesquisar" : "Pesquisa: " + query,
			"Clique: digitar o nome do item", "Botão direito: limpar a pesquisa"));
		String[] sortLines = new String[SORTS.length + 1];
		for (int i = 0; i < SORTS.length; i++) sortLines[i] = (i == sortMode() ? "▶ " : "   ") + SORTS[i];
		sortLines[SORTS.length] = "Clique: trocar";
		box.setItem(SORT, button(Items.COMPARATOR, "smartchest_sort", "Ordenar: " + SORTS[sortMode()], sortLines));
		box.setItem(DEPOSIT, button(Items.HOPPER, "smartchest_deposit", "Guardar inventário",
			"Guarda tudo do inventário nos baús ligados,", "menos a hotbar.", "Shift+clique num item guarda só ele."));
		box.setItem(LINKS, button(Items.LEAD, "smartchest_links", SmartChest.count(links, "baú ligado", "baús ligados"),
			"Ligue ou desligue baús com o Vinculador.", "Clique: mostrar no mundo"));
	}

	/** Nome do item no idioma do jogador. */
	String name(Entry e) {
		return names.computeIfAbsent(new Key(e.proto), k -> Lang.resolve(e.proto.getHoverName(), tr));
	}

	private static String id(Entry e) {
		return BuiltInRegistries.ITEM.getKey(e.proto.getItem()).toString();
	}

	/** Tudo por onde o item pode ser achado: nome no idioma do jogador, no idioma do servidor e o id. */
	private String haystack(Entry e) {
		return haystacks.computeIfAbsent(new Key(e.proto), k -> {
			Language base = Language.getInstance();
			String serverName = Lang.resolve(e.proto.getHoverName(), key -> base.has(key) ? base.getOrDefault(key) : null);
			return Lang.normalize(name(e) + " | " + serverName + " | " + Lang.idWords(id(e)) + " | " + id(e));
		});
	}

	private static ItemStack display(Entry e) {
		ItemStack s = e.proto.copy();
		int max = Storage.maxStack(s);
		if (max > 1) {
			s.set(DataComponents.MAX_STACK_SIZE, 99); // mostra até 99 no espaço; o total exato vai no texto
			s.setCount((int) Math.min(e.total, 99));
		}
		List<Component> lore = new ArrayList<>();
		ItemLore old = s.get(DataComponents.LORE);
		if (old != null) lore.addAll(old.lines().subList(0, Math.min(old.lines().size(), 200)));
		int pack = (int) Math.min(max, e.total);
		lore.add(SmartChest.plain("Total: " + NUM.format(e.total), ChatFormatting.GRAY));
		lore.add(SmartChest.plain("Em " + SmartChest.count(e.chests, "baú", "baús"), ChatFormatting.GRAY));
		lore.add(Component.empty());
		lore.add(SmartChest.plain("Clique: pegar " + pack, ChatFormatting.DARK_GRAY));
		lore.add(SmartChest.plain("Botão direito: pegar " + (pack + 1) / 2, ChatFormatting.DARK_GRAY));
		lore.add(SmartChest.plain("Shift+clique: para o inventário", ChatFormatting.DARK_GRAY));
		s.set(DataComponents.LORE, new ItemLore(lore));
		return s;
	}

	private static ItemStack button(Item item, String model, String name, String... lines) {
		ItemStack s = new ItemStack(item);
		s.set(DataComponents.CUSTOM_NAME, SmartChest.plain(name, ChatFormatting.LIGHT_PURPLE));
		s.set(DataComponents.CUSTOM_MODEL_DATA, SmartChest.model(model));
		List<Component> lore = new ArrayList<>();
		for (String l : lines) lore.add(SmartChest.plain(l, ChatFormatting.GRAY));
		s.set(DataComponents.LORE, new ItemLore(lore));
		return s;
	}

	// ---------- cliques ----------

	@Override
	public void clicked(int slot, int button, ContainerInput input, Player p) {
		if (!(p instanceof ServerPlayer sp)) return;
		if (slot >= 0 && slot < 54) {
			if (input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE) {
				if (slot < GRID) clickItem(sp, slot, button, input);
				else clickButton(sp, slot, button);
			}
			sendAllDataToRemote(); // o cliente "previu" o clique: manda o estado real
			return;
		}
		if (input == ContainerInput.QUICK_MOVE && slot >= 54 && slot < slots.size()) {
			storeSlot(sp, slots.get(slot)); // Shift+clique no próprio inventário: guarda o item
			sendAllDataToRemote();
			return;
		}
		super.clicked(slot, button, input, p); // o resto do inventário do jogador funciona normalmente
	}

	private void clickItem(ServerPlayer sp, int slot, int button, ContainerInput input) {
		ItemStack carried = getCarried();
		if (!carried.isEmpty()) {
			if (input == ContainerInput.PICKUP) storeCarried(sp, button == 1); // soltar um item na grade = guardar
			return;
		}
		int idx = page * GRID + slot;
		if (idx >= view.size()) return;
		Entry e = view.get(idx);
		Linked linked = linked();
		int max = Storage.maxStack(e.proto);
		if (input == ContainerInput.QUICK_MOVE) {
			int n = Math.min(max, Storage.space(sp.getInventory(), e.proto));
			if (n <= 0) {
				sp.sendSystemMessage(SmartChest.plain("Seu inventário está cheio.", ChatFormatting.RED), true);
				return;
			}
			ItemStack got = Storage.take(linked.containers, e.proto, n);
			sp.getInventory().add(got);
			if (!got.isEmpty()) Storage.insert(linked.containers, got); // não coube (raro): volta para os baús
			if (!got.isEmpty()) setCarried(got); // nem lá: fica na mão, nunca some
		} else {
			int pack = (int) Math.min(max, e.total);
			setCarried(Storage.take(linked.containers, e.proto, button == 1 ? Math.max(1, (pack + 1) / 2) : max));
		}
		refresh();
	}

	private void clickButton(ServerPlayer sp, int slot, int button) {
		switch (slot) {
			case PREV -> {
				if (page > 0) page--;
				refresh();
			}
			case NEXT -> {
				if (page < pages - 1) page++;
				refresh();
			}
			case SEARCH -> later(sp, button == 1 ? () -> open(sp, chest, "", 0) : () -> SearchMenu.open(sp, chest, query));
			case SORT -> {
				SmartChest.SORT.put(sp.getUUID(), (sortMode() + 1) % SORTS.length);
				page = 0;
				refresh();
			}
			case DEPOSIT -> storeInventory(sp);
			case LINKS -> later(sp, () -> {
				sp.closeContainer();
				ServerLevel sl = sp.level();
				for (BlockPos l : chest.linkPositions()) SmartChest.particles(sl, sp, l, ParticleTypes.END_ROD, 20);
				SmartChest.particles(sl, sp, chest.pos(), ParticleTypes.PORTAL, 40);
				sp.sendSystemMessage(SmartChest.plain(SmartChest.count(chest.links.size(), "baú ligado", "baús ligados")
					+ " (marcados com partículas). Use o Vinculador para ligar ou desligar.", ChatFormatting.AQUA));
			});
			default -> {}
		}
	}

	/** Troca de tela depois que o clique terminar de ser processado. */
	private static void later(ServerPlayer sp, Runnable r) {
		sp.level().getServer().execute(() -> {
			if (!sp.hasDisconnected()) r.run();
		});
	}

	// ---------- guardar ----------

	/** Item que está na mão (cursor): guarda tudo, ou só 1 com o botão direito. */
	private void storeCarried(ServerPlayer sp, boolean one) {
		ItemStack carried = getCarried();
		Linked linked = linked();
		if (noChests(sp, linked)) return;
		ItemStack part = one ? carried.split(1) : carried;
		int moved = Storage.insert(linked.containers, part);
		if (one && !part.isEmpty()) carried.grow(part.getCount()); // não coube: volta para a mão
		setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
		feedback(sp, moved, moved == 0);
		refresh();
	}

	/** Shift+clique num item do próprio inventário. */
	private void storeSlot(ServerPlayer sp, Slot slot) {
		ItemStack st = slot.getItem();
		if (st.isEmpty()) return;
		Linked linked = linked();
		if (noChests(sp, linked)) return;
		int moved = Storage.insert(linked.containers, st);
		if (st.isEmpty()) slot.set(ItemStack.EMPTY);
		else slot.setChanged();
		feedback(sp, moved, !st.isEmpty());
		refresh();
	}

	/** Botão "Guardar inventário": tudo menos a hotbar. */
	private void storeInventory(ServerPlayer sp) {
		Linked linked = linked();
		if (noChests(sp, linked)) return;
		Inventory inv = sp.getInventory();
		int moved = 0;
		boolean left = false;
		for (int i = 9; i < 36; i++) {
			ItemStack st = inv.getItem(i);
			if (st.isEmpty()) continue;
			moved += Storage.insert(linked.containers, st);
			if (st.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
			else left = true;
		}
		inv.setChanged();
		if (moved == 0 && !left) {
			sp.sendSystemMessage(SmartChest.plain("Nada para guardar (a hotbar não é guardada).", ChatFormatting.GRAY), true);
		} else {
			feedback(sp, moved, left);
		}
		refresh();
	}

	private static boolean noChests(ServerPlayer sp, Linked linked) {
		if (!linked.containers.isEmpty()) return false;
		SmartChest.say(sp, "Nenhum baú ligado está ao alcance. Use o Vinculador para ligar baús ou barris.", ChatFormatting.RED);
		return true;
	}

	/** Mensagem e som de quanto foi guardado; {@code full} = algo não coube. */
	private static void feedback(ServerPlayer sp, int moved, boolean full) {
		ServerLevel sl = sp.level();
		if (full) {
			SmartChest.say(sp, moved == 0 ? "Os baús ligados estão cheios: não cabe mais nada."
				: "Guardados " + NUM.format(moved) + " itens; o resto não coube porque os baús ligados encheram.", ChatFormatting.RED);
			sl.playSound(null, sp.blockPosition(), SoundEvents.BUNDLE_INSERT_FAIL, SoundSource.PLAYERS, 0.8f, 1f);
		} else if (moved > 0) {
			sp.sendSystemMessage(SmartChest.plain("Guardados " + NUM.format(moved) + " itens.", ChatFormatting.GREEN), true);
			sl.playSound(null, sp.blockPosition(), SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.8f, 1f);
		}
	}

	// ---------- vanilla ----------

	@Override
	public void broadcastChanges() {
		if (++ticks % 20 == 0) refresh(); // outros jogadores e funis também mexem nos baús
		super.broadcastChanges();
	}

	@Override
	public boolean stillValid(Player p) {
		return SmartChest.exists(chest) && chest.dim.equals(SmartChest.dim(p.level()))
			&& p.distanceToSqr(Vec3.atCenterOf(chest.pos())) <= SmartChest.USE_RANGE_SQR;
	}

	@Override
	public ItemStack quickMoveStack(Player p, int index) {
		return ItemStack.EMPTY; // Shift+clique é tratado em clicked()
	}
}
