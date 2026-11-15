package io.github.ivanils0n.chunkmap.seed;

import java.util.HashMap;
import java.util.Map;

/** Cores dos biomas no estilo do Chunkbase/Amidst. Biomas de outros mods ganham uma cor fixa tirada do id. */
public final class BiomeColors {
	private static final Map<String, Integer> COLORS = new HashMap<>();

	static {
		put("ocean", 0x5656C8);
		put("deep_ocean", 0x262670);
		put("warm_ocean", 0x4A6AD8);
		put("lukewarm_ocean", 0x4F5ED0);
		put("deep_lukewarm_ocean", 0x2E2E86);
		put("cold_ocean", 0x7676DC);
		put("deep_cold_ocean", 0x34348C);
		put("frozen_ocean", 0xA4A4EE);
		put("deep_frozen_ocean", 0x5050A8);
		put("river", 0x2020FF);
		put("frozen_river", 0xA0A0FF);
		put("beach", 0xFADE55);
		put("snowy_beach", 0xFAF0C0);
		put("stony_shore", 0xA2A284);
		put("plains", 0x8DB360);
		put("sunflower_plains", 0xB5DB88);
		put("snowy_plains", 0xFFFFFF);
		put("ice_spikes", 0xB4DCDC);
		put("desert", 0xFA9418);
		put("swamp", 0x2F7A5E);
		put("mangrove_swamp", 0x2CCC8E);
		put("forest", 0x056621);
		put("flower_forest", 0x2D8E49);
		put("birch_forest", 0x307444);
		put("old_growth_birch_forest", 0x589C6C);
		put("dark_forest", 0x40511A);
		put("pale_garden", 0x8E9188);
		put("taiga", 0x0B6659);
		put("snowy_taiga", 0x31554A);
		put("old_growth_pine_taiga", 0x596651);
		put("old_growth_spruce_taiga", 0x818E79);
		put("savanna", 0xBDB25F);
		put("savanna_plateau", 0xA79D64);
		put("windswept_savanna", 0xE5DA87);
		put("jungle", 0x537B09);
		put("sparse_jungle", 0x628B17);
		put("bamboo_jungle", 0x768E14);
		put("badlands", 0xD94515);
		put("eroded_badlands", 0xFF6D3D);
		put("wooded_badlands", 0xB09765);
		put("meadow", 0x60A445);
		put("cherry_grove", 0xFF91C8);
		put("grove", 0xACD3C4);
		put("snowy_slopes", 0xC4C4C4);
		put("frozen_peaks", 0xB0B3CE);
		put("jagged_peaks", 0xDCDCC8);
		put("stony_peaks", 0x7B8F74);
		put("windswept_hills", 0x606060);
		put("windswept_gravelly_hills", 0x789878);
		put("windswept_forest", 0x5B7352);
		put("mushroom_fields", 0xFF00FF);
		put("dripstone_caves", 0x4E3012);
		put("lush_caves", 0x283C00);
		put("deep_dark", 0x031F29);
		put("nether_wastes", 0xBF3B3B);
		put("soul_sand_valley", 0x522921);
		put("crimson_forest", 0xDD0808);
		put("warped_forest", 0x49907B);
		put("basalt_deltas", 0x403636);
		put("the_end", 0x8080FF);
		put("small_end_islands", 0x4B4BAB);
		put("end_midlands", 0xC9C959);
		put("end_highlands", 0xB5B536);
		put("end_barrens", 0x7070CC);
		put("the_void", 0x000000);
	}

	private static void put(String id, int rgb) {
		COLORS.put("minecraft:" + id, 0xFF000000 | rgb);
	}

	private BiomeColors() {}

	/** Cor ARGB opaca do bioma pelo id (ex.: minecraft:plains). */
	public static int of(String id) {
		Integer c = COLORS.get(id);
		if (c != null) return c;
		// Bioma desconhecido (outro mod): cor estável e não muito escura derivada do id.
		int h = id.hashCode() * 0x9E3779B9;
		int r = 70 + (h & 0x9F), g = 70 + (h >>> 8 & 0x9F), b = 70 + (h >>> 16 & 0x9F);
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	/** Escurece a cor (para biomas fora do destaque). */
	public static int dim(int argb) {
		int r = (argb >> 16 & 0xFF) / 4 + 20, g = (argb >> 8 & 0xFF) / 4 + 20, b = (argb & 0xFF) / 4 + 24;
		return 0xFF000000 | r << 16 | g << 8 | b;
	}
}
