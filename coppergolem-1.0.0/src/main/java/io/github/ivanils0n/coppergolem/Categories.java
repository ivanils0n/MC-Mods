package io.github.ivanils0n.coppergolem;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Abas do Golem de Cobre, montadas uma vez quando o servidor liga. A base são as abas do modo criativo do próprio jogo
 * (itens novos de cada versão já caem no lugar certo), com estes ajustes: armaduras numa aba só; plantas e sementes
 * junto da comida; madeira, blocos naturais e funcionais em Construção; Redstone só com o que liga ou recebe sinal;
 * minérios, minério bruto, lingotes, gemas e seus blocos em Minérios. O resto fica em "Ingredientes e outros".
 */
final class Categories {
	static final String[] KEYS = {"todos", "construcao", "armaduras", "minerios", "redstone", "ferramentas", "comida", "ingredientes"};
	static final String[] NAMES = {"Todos", "Construção", "Armaduras", "Minérios", "Redstone", "Ferramentas e combate",
		"Comidas, sementes e poções", "Ingredientes e outros"};
	/** Ícone de cada aba: o item do próprio jogo (segue o resource pack do jogador). */
	static final Item[] VANILLA = {Items.CHEST, Items.BRICKS, Items.IRON_CHESTPLATE, Items.EMERALD, Items.REDSTONE,
		Items.IRON_SWORD, Items.APPLE, Items.BONE};
	static final int ALL = 0, BUILDING = 1, ARMOR = 2, ORES = 3, REDSTONE = 4, TOOLS = 5, FOOD = 6, OTHER = 7;

	private static final List<String> TABS = List.of("building_blocks", "colored_blocks", "natural_blocks", "functional_blocks",
		"redstone_blocks", "tools_and_utilities", "combat", "food_and_drinks", "ingredients");
	private static final Set<String> BLOCK_TABS = Set.of("building_blocks", "colored_blocks", "natural_blocks", "functional_blocks");
	private static final List<TagKey<Item>> WOOD = tags("minecraft", "wooden_doors", "wooden_trapdoors", "wooden_buttons",
		"wooden_pressure_plates", "fence_gates", "wooden_shelves");
	// Tags comuns do Fabric (pegam também minérios de outros mods).
	private static final List<TagKey<Item>> MINERALS = tags("c", "ores", "raw_materials", "ingots", "gems", "nuggets",
		"storage_blocks/iron", "storage_blocks/gold", "storage_blocks/copper", "storage_blocks/diamond", "storage_blocks/emerald",
		"storage_blocks/lapis", "storage_blocks/coal", "storage_blocks/netherite", "storage_blocks/raw_iron",
		"storage_blocks/raw_gold", "storage_blocks/raw_copper");
	private static final Set<Item> MORE_MINERALS = Set.of(Items.COAL, Items.CHARCOAL, Items.NETHERITE_SCRAP);
	private static final Set<Item> MORE_PLANTS = Set.of(Items.SUGAR_CANE, Items.CACTUS, Items.BAMBOO, Items.KELP,
		Items.COCOA_BEANS, Items.CHORUS_FLOWER);
	private static final Map<Item, Integer> BY_ITEM = new HashMap<>();

	private Categories() {}

	static void build(MinecraftServer server) {
		BY_ITEM.clear();
		CreativeModeTabs.tryRebuildTabContents(server.getWorldData().enabledFeatures(), true, server.registryAccess());
		Map<Item, Set<String>> in = new HashMap<>();
		for (String t : TABS) {
			CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(Identifier.fromNamespaceAndPath("minecraft", t));
			if (tab == null) continue;
			for (ItemStack s : tab.getDisplayItems()) in.computeIfAbsent(s.getItem(), k -> new HashSet<>()).add(t);
		}
		int[] count = new int[KEYS.length];
		for (Item item : BuiltInRegistries.ITEM) {
			if (item == Items.AIR) continue;
			int cat = classify(new ItemStack(item), in.getOrDefault(item, Set.of()));
			BY_ITEM.put(item, cat);
			count[cat]++;
		}
		StringBuilder log = new StringBuilder();
		for (int i = 1; i < KEYS.length; i++) log.append(i > 1 ? ", " : "").append(NAMES[i]).append(' ').append(count[i]);
		GolemMod.LOG.info("Categorias do Golem de Cobre: {}", log);
	}

	static void clear() {
		BY_ITEM.clear();
	}

	static int of(ItemStack s) {
		return BY_ITEM.getOrDefault(s.getItem(), OTHER);
	}

	/** A ordem decide quem ganha quando o item aparece em mais de uma aba do criativo. */
	private static int classify(ItemStack s, Set<String> in) {
		if (armor(s)) return ARMOR;
		if (in.contains("tools_and_utilities") || in.contains("combat")) return TOOLS;
		if (in.contains("food_and_drinks") || plant(s)) return FOOD;
		if (any(s, WOOD)) return BUILDING; // portas, botões e afins de madeira também estão na aba de redstone
		if (redstone(s, in)) return REDSTONE;
		if (MORE_MINERALS.contains(s.getItem()) || any(s, MINERALS)) return ORES;
		for (String t : in) if (BLOCK_TABS.contains(t)) return BUILDING;
		return OTHER;
	}

	/** Dá proteção: armaduras de jogador, de cavalo, de lobo e afins (abóbora, cabeças e élitro não). */
	private static boolean armor(ItemStack s) {
		ItemAttributeModifiers mods = s.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		for (ItemAttributeModifiers.Entry e : mods.modifiers()) if (e.attribute().value() == Attributes.ARMOR.value()) return true;
		return false;
	}

	/** Coisas de plantar: sementes, mudas, flores, cogumelos, cana, cacto... */
	private static boolean plant(ItemStack s) {
		return MORE_PLANTS.contains(s.getItem()) || s.getItem() instanceof BlockItem b && b.getBlock() instanceof VegetationBlock;
	}

	/**
	 * Só na aba de redstone do criativo (pistão, TNT, blocos de slime...), ou um bloco que dá ou recebe sinal (alavanca,
	 * observador, ejetor, funil, lâmpada...). Baús, fornalhas, lã e afins que o criativo também põe lá ficam em Construção.
	 */
	private static boolean redstone(ItemStack s, Set<String> in) {
		if (s.is(Items.STRING)) return false; // vira fio de armadilha, mas é ingrediente
		if (in.contains("redstone_blocks") && !in.contains("ingredients") && in.stream().noneMatch(BLOCK_TABS::contains)) return true;
		if (!(s.getItem() instanceof BlockItem b)) return false;
		BlockState st = b.getBlock().defaultBlockState();
		return st.isSignalSource() || st.hasProperty(BlockStateProperties.POWERED) || st.hasProperty(BlockStateProperties.POWER)
			|| st.hasProperty(BlockStateProperties.TRIGGERED) || st.hasProperty(BlockStateProperties.ENABLED)
			|| st.hasProperty(BlockStateProperties.EXTENDED) || BuiltInRegistries.ITEM.getKey(s.getItem()).getPath().contains("redstone");
	}

	private static boolean any(ItemStack s, List<TagKey<Item>> tags) {
		for (TagKey<Item> t : tags) if (s.is(t)) return true;
		return false;
	}

	private static List<TagKey<Item>> tags(String ns, String... paths) {
		return Arrays.stream(paths).map(p -> TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(ns, p))).toList();
	}
}
