package io.github.ivanils0n.smartchest;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Abas do Baú Inteligente. A categoria de cada item vem das abas do modo criativo do próprio jogo (montadas uma vez
 * quando o servidor liga), então itens novos de cada versão já caem no lugar certo. Itens que não estão em nenhuma
 * aba (de outros mods, por exemplo) ficam em "Ingredientes e outros".
 */
final class Categories {
	static final String[] KEYS = {"todos", "construcao", "natureza", "funcionais", "redstone", "ferramentas", "comida", "ingredientes"};
	static final String[] NAMES = {"Todos", "Construção", "Natureza", "Funcionais", "Redstone", "Ferramentas e combate",
		"Comida e poções", "Ingredientes e outros"};
	/** Ícone de cada aba para quem está sem o resource pack. */
	static final Item[] VANILLA = {Items.CHEST, Items.BRICKS, Items.GRASS_BLOCK, Items.CRAFTING_TABLE, Items.REDSTONE,
		Items.IRON_PICKAXE, Items.APPLE, Items.IRON_INGOT};
	static final int ALL = 0, OTHER = 7;

	// Abas do criativo -> aba do baú, na ordem de prioridade (um item que aparece em duas fica na primeira).
	private static final String[][] TABS = {
		{"tools_and_utilities", "5"}, {"combat", "5"}, {"food_and_drinks", "6"}, {"redstone_blocks", "4"},
		{"functional_blocks", "3"}, {"natural_blocks", "2"}, {"building_blocks", "1"}, {"colored_blocks", "1"},
		{"ingredients", "7"}, {"spawn_eggs", "7"}, {"op_blocks", "7"}};
	private static final Map<Item, Integer> BY_ITEM = new HashMap<>();

	private Categories() {}

	static void build(MinecraftServer server) {
		BY_ITEM.clear();
		CreativeModeTabs.tryRebuildTabContents(server.getWorldData().enabledFeatures(), true, server.registryAccess());
		for (String[] t : TABS) {
			CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(Identifier.fromNamespaceAndPath("minecraft", t[0]));
			if (tab == null) continue;
			int cat = Integer.parseInt(t[1]);
			for (ItemStack s : tab.getDisplayItems()) BY_ITEM.putIfAbsent(s.getItem(), cat);
		}
		SmartChest.LOG.info("Categorias do Baú Inteligente: {} itens", BY_ITEM.size());
	}

	static void clear() {
		BY_ITEM.clear();
	}

	static int of(ItemStack s) {
		return BY_ITEM.getOrDefault(s.getItem(), OTHER);
	}
}
