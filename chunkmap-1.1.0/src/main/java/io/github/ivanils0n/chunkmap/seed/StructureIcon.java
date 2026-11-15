package io.github.ivanils0n.chunkmap.seed;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/** Os filtros da barra do mapa (na ordem da barra) e quais estruturas do jogo cada um mostra. */
public enum StructureIcon {
	VILLAGE("village", "village_plains", "village_desert", "village_savanna", "village_snowy", "village_taiga"),
	DESERT_PYRAMID("desert_pyramid", "desert_pyramid"),
	JUNGLE_TEMPLE("jungle_temple", "jungle_pyramid"),
	SWAMP_HUT("swamp_hut", "swamp_hut"),
	IGLOO("igloo", "igloo"),
	OCEAN_MONUMENT("ocean_monument", "monument"),
	MANSION("mansion", "mansion"),
	STRONGHOLD("stronghold", "stronghold"),
	ANCIENT_CITY("ancient_city", "ancient_city"),
	TRIAL_CHAMBERS("trial_chambers", "trial_chambers"),
	SHIPWRECK("shipwreck", "shipwreck", "shipwreck_beached"),
	RUINED_PORTAL("ruined_portal", "ruined_portal", "ruined_portal_desert", "ruined_portal_jungle", "ruined_portal_swamp",
			"ruined_portal_mountain", "ruined_portal_ocean", "ruined_portal_nether"),
	PILLAGER_OUTPOST("pillager_outpost", "pillager_outpost"),
	BURIED_TREASURE("buried_treasure", "buried_treasure"),
	MINESHAFT("mineshaft", "mineshaft", "mineshaft_mesa"),
	/** Não é estrutura: pinta os chunks de slime. */
	SLIME_CHUNK("slime_chunk"),
	/** Não é estrutura: o ponto de nascimento do mundo. */
	SPAWN("spawn"),
	NETHER_FORTRESS("nether_fortress", "fortress"),
	BASTION("bastion", "bastion_remnant"),
	END_CITY("end_city", "end_city");

	private static final Map<String, StructureIcon> BY_STRUCTURE = new HashMap<>();
	private static final Map<String, StructureIcon> BY_ID = new HashMap<>();

	static {
		for (StructureIcon i : values()) {
			BY_ID.put(i.id, i);
			for (String s : i.structures) BY_STRUCTURE.put("minecraft:" + s, i);
		}
	}

	public final String id;
	public final Identifier texture;
	private final String[] structures;

	StructureIcon(String id, String... structures) {
		this.id = id;
		this.texture = Identifier.fromNamespaceAndPath("chunkmap", "textures/gui/icons/" + id + ".png");
		this.structures = structures;
	}

	public Component label() {
		return Component.translatable("chunkmap.icon." + id);
	}

	/** Filtro de uma estrutura do jogo (ex.: minecraft:village_plains), ou null se não houver. */
	public static StructureIcon forStructure(String structureId) {
		return BY_STRUCTURE.get(structureId);
	}

	public static StructureIcon byId(String id) {
		return BY_ID.get(id);
	}
}
