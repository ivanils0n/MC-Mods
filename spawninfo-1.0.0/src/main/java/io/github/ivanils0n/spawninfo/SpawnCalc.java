package io.github.ivanils0n.spawninfo;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.valueproviders.IntProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * As regras de spawn natural do jogo (NaturalSpawner, SpawnPlacements e as regras de cada mob), refeitas no cliente
 * para um bloco: em vez de sortear, calcula a chance de cada sorteio dar certo.
 * <p>
 * A cada tentativa o jogo sorteia um mob da lista do bioma (pelo peso) e confere as regras dele no bloco. Aqui:
 * chance do mob = peso / soma dos pesos × chance de as regras dele passarem.
 */
public final class SpawnCalc {
	private SpawnCalc() {}

	/** Um mob da lista do bioma. */
	public record Row(EntityType<?> type, int weight, double rule, double chance) {}

	/** Um grupo (hostis, pacíficos, ambiente). */
	public static final class Group {
		public final MobCategory category;
		public final List<Row> rows = new ArrayList<>();
		/** Mobs desse grupo que contam para o limite (perto de você). */
		public int count;
		public int cap;
		/** Chance de uma tentativa dar algum mob (0 a 1), sem o limite. */
		public double total;
		/** Fator do limite: 1 com o limite vazio, 0 cheio. */
		public double free = 1;
		/** O grupo inteiro não nasce agora (dificuldade Pacífico). */
		public boolean disabled;

		Group(MobCategory category) {
			this.category = category;
			this.cap = category.getMaxInstancesPerChunk();
		}

		/** Chance mostrada: com o ajuste do limite, se ligado. */
		public double shown(Row r) {
			return Config.get.capAdjust ? r.chance * free : r.chance;
		}

		public double shownTotal() {
			return Config.get.capAdjust ? total * free : total;
		}
	}

	/** Situação do bloco analisado. */
	public enum Problem {NONE, SOLID, NO_ROOM, BAD_FLOOR, TOO_CLOSE, NEAR_SPAWN, TOO_FAR}

	public static final class Result {
		public BlockPos pos;
		public boolean looking;
		public double distance;
		public Holder<Biome> biome;
		public int blockLight, skyLight, raw;
		/** null: sem seed (não dá para saber). */
		public Boolean slimeChunk;
		public boolean seedFromWorld;
		public Problem problem = Problem.NONE;
		/** A lista de mobs do bioma ainda está carregando (multiplayer, primeira vez). */
		public boolean loading;
		public Group hostile, passive, ambient;
		/** Chance do slime se o chunk for de slime (quando não há seed). */
		public double slimeIfChunk;
	}

	/** O que o cálculo precisa saber do mundo. */
	public record Context(ClientLevel level, BlockPos pos, Holder<Biome> biome, Boolean slimeChunk, double surfaceSlimeChance) {}

	/** Os ajustes de cada mob que não segue a regra comum de monstro. */
	private enum Rule {MONSTER, SURFACE, ANY_LIGHT, SLIME, MAGMA, GHAST, NO_WART, DROWNED, ANIMAL, BAT, OTHER}

	private static final Map<String, Rule> RULES = new HashMap<>();
	private static final Map<String, String> ANIMAL_TAG = new HashMap<>();

	static {
		for (String id : List.of("zombie", "skeleton", "creeper", "spider", "cave_spider", "enderman", "witch", "zombie_villager",
				"bogged", "wither_skeleton", "creaking", "giant", "evoker", "vindicator", "ravager", "warden", "vex", "illusioner",
				"zombie_horse", "wither")) RULES.put(id, Rule.MONSTER);
		for (String id : List.of("husk", "stray", "parched", "camel_husk")) RULES.put(id, Rule.SURFACE);
		for (String id : List.of("blaze", "breeze", "zoglin")) RULES.put(id, Rule.ANY_LIGHT);
		RULES.put("slime", Rule.SLIME);
		RULES.put("magma_cube", Rule.MAGMA);
		RULES.put("ghast", Rule.GHAST);
		for (String id : List.of("piglin", "hoglin", "zombified_piglin")) RULES.put(id, Rule.NO_WART);
		RULES.put("drowned", Rule.DROWNED);
		RULES.put("bat", Rule.BAT);
		for (String id : List.of("pig", "cow", "sheep", "chicken", "horse", "donkey", "mule", "llama", "cat", "panda", "trader_llama",
				"happy_ghast", "mooshroom", "goat", "rabbit", "fox", "wolf", "armadillo", "polar_bear")) RULES.put(id, Rule.ANIMAL);
		ANIMAL_TAG.put("mooshroom", "mooshrooms_spawnable_on");
		ANIMAL_TAG.put("goat", "goats_spawnable_on");
		ANIMAL_TAG.put("rabbit", "rabbits_spawnable_on");
		ANIMAL_TAG.put("fox", "foxes_spawnable_on");
		ANIMAL_TAG.put("wolf", "wolves_spawnable_on");
		ANIMAL_TAG.put("armadillo", "armadillo_spawnable_on");
	}

	private static final Map<String, TagKey<Block>> TAGS = new HashMap<>();

	private static TagKey<Block> blockTag(String path) {
		return TAGS.computeIfAbsent(path, p -> TagKey.create(Registries.BLOCK, Identifier.withDefaultNamespace(p)));
	}

	public static String id(EntityType<?> type) {
		Identifier key = EntityType.getKey(type);
		return key == null ? "?" : key.getPath();
	}

	/** O chunk é de slime? (a mesma conta do jogo) */
	public static boolean isSlimeChunk(long seed, int chunkX, int chunkZ) {
		return WorldgenRandom.seedSlimeChunk(chunkX, chunkZ, seed, 987234911L).nextInt(10) == 0;
	}

	/** Chance de as regras de um mob passarem nesse bloco (0 a 1). Público para o teste comparar com o jogo. */
	public static double rule(EntityType<?> type, Context c) {
		ClientLevel level = c.level();
		BlockPos pos = c.pos();
		if (type.getCategory() == MobCategory.MISC) return 0;
		// Lugar certo para o tipo (chão, água ou lava) e espaço livre do tamanho do mob.
		if (!SpawnPlacements.isSpawnPositionOk(type, level, pos)) return 0;
		AABB box = type.getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		if (!level.noCollision(box)) return 0;
		SpawnPlacementType placement = SpawnPlacements.getPlacementType(type);
		if (placement == SpawnPlacementTypes.ON_GROUND && level.containsAnyLiquid(box)) return 0;

		Rule rule = RULES.getOrDefault(id(type), Rule.OTHER);
		BlockPos below = pos.below();
		BlockState floor = level.getBlockState(below);
		boolean validFloor = floor.isValidSpawn(level, below, type);
		return switch (rule) {
			case MONSTER -> validFloor ? dark(c) * walkable(c) : 0;
			case SURFACE -> validFloor && level.canSeeSky(pos) ? dark(c) * walkable(c) : 0;
			case ANY_LIGHT -> validFloor ? 1 : 0;
			case SLIME -> {
				if (!validFloor) yield 0;
				double surface = 0;
				if (c.biome().is(BiomeTags.ALLOWS_SURFACE_SLIME_SPAWNS) && pos.getY() > 50 && pos.getY() < 70) {
					surface = c.surfaceSlimeChance() * below(level.getMaxLocalRawBrightness(pos), 0, 7);
				}
				double chunk = Boolean.TRUE.equals(c.slimeChunk()) && pos.getY() < 40 ? 0.1 : 0;
				yield surface + (1 - surface) * chunk;
			}
			case MAGMA -> 1;
			case GHAST -> validFloor ? 1 / 20.0 : 0;
			case NO_WART -> floor.is(net.minecraft.world.level.block.Blocks.NETHER_WART_BLOCK) ? 0 : 1;
			case DROWNED -> {
				if (!level.getFluidState(below).is(net.minecraft.tags.FluidTags.WATER)) yield 0;
				if (c.biome().is(BiomeTags.MORE_FREQUENT_DROWNED_SPAWNS)) yield dark(c) / 15.0;
				yield pos.getY() < level.getSeaLevel() - 5 ? dark(c) / 40.0 : 0;
			}
			case ANIMAL -> {
				// Cada animal tem o chão dele (cogumelo no micélio, cabra na pedra...); os outros, grama.
				String tag = ANIMAL_TAG.get(id(type));
				boolean ground = tag != null ? floor.is(blockTag(tag)) : floor.is(blockTag("animals_spawnable_on"))
						|| (id(type).equals("polar_bear") && floor.is(blockTag("polar_bears_spawnable_on_alternate")));
				yield ground && level.getRawBrightness(pos, 0) > 8 ? 1 : 0;
			}
			case BAT -> {
				if (pos.getY() >= level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ())) yield 0;
				if (!floor.is(blockTag("bats_spawnable_on")) || !validFloor) yield 0;
				yield 0.5 * below(level.getMaxLocalRawBrightness(pos), 0, 3);
			}
			case OTHER -> validFloor ? 1 : 0;
		};
	}

	/** Chance de um sorteio uniforme entre lo e hi (inclusive) dar pelo menos "value". */
	private static double below(int value, int lo, int hi) {
		if (value <= lo) return 1;
		if (value > hi) return 0;
		return (hi - value + 1) / (double) (hi - lo + 1);
	}

	/** Monster.isDarkEnoughToSpawn: luz do céu, luz de blocos e o teste de luz da dimensão. */
	static double dark(Context c) {
		ClientLevel level = c.level();
		BlockPos pos = c.pos();
		DimensionType dt = level.dimensionType();
		int limit = dt.monsterSpawnBlockLightLimit();
		if (limit < 15 && level.getBrightness(LightLayer.BLOCK, pos) > limit) return 0;
		int sky = level.getBrightness(LightLayer.SKY, pos);
		double pSky = Math.max(0, 32 - sky) / 32.0;
		int raw = level.isThundering() ? level.getMaxLocalRawBrightness(pos, 10) : level.getMaxLocalRawBrightness(pos);
		IntProvider test = dt.monsterSpawnLightTest();
		return pSky * below(raw, test.minInclusive(), test.maxInclusive());
	}

	/** Monstros andam só onde a luz deixa (o mesmo valor que eles usam para escolher o caminho). */
	private static double walkable(Context c) {
		return c.level().getPathfindingCostFromLightLevels(c.pos()) > 0 ? 0 : 1;
	}

	/** Monta um grupo com a lista do bioma. */
	static Group group(MobCategory category, MobSpawnSettings settings, Context c) {
		Group g = new Group(category);
		if (settings == null) return g;
		List<Weighted<MobSpawnSettings.SpawnerData>> list = settings.getMobs(category).unwrap();
		int sum = 0;
		for (Weighted<MobSpawnSettings.SpawnerData> w : list) sum += w.weight();
		if (sum <= 0) return g;
		// Mesmo mob com duas entradas (pesos diferentes): soma numa linha só.
		Map<EntityType<?>, int[]> weights = new java.util.LinkedHashMap<>();
		for (Weighted<MobSpawnSettings.SpawnerData> w : list) weights.computeIfAbsent(w.value().type(), t -> new int[1])[0] += w.weight();
		for (Map.Entry<EntityType<?>, int[]> e : weights.entrySet()) {
			double r = rule(e.getKey(), c);
			double chance = e.getValue()[0] / (double) sum * r;
			g.rows.add(new Row(e.getKey(), e.getValue()[0], r, chance));
			g.total += chance;
		}
		g.rows.sort((a, b) -> a.chance() != b.chance() ? Double.compare(b.chance(), a.chance()) : Integer.compare(b.weight(), a.weight()));
		return g;
	}

	/** O cálculo completo para um bloco. */
	public static Result compute(ClientLevel level, BlockPos pos, boolean looking, MobSpawnSettings settings, boolean loading,
			Boolean slimeChunk, boolean seedFromWorld, double surfaceSlimeChance, double distance, Map<MobCategory, Integer> counts) {
		Result r = new Result();
		r.pos = pos;
		r.looking = looking;
		r.distance = distance;
		r.biome = level.getBiome(pos);
		r.blockLight = level.getBrightness(LightLayer.BLOCK, pos);
		r.skyLight = level.getBrightness(LightLayer.SKY, pos);
		r.raw = level.getMaxLocalRawBrightness(pos);
		r.slimeChunk = slimeChunk;
		r.seedFromWorld = seedFromWorld;
		r.loading = loading;
		Context c = new Context(level, pos, r.biome, slimeChunk, surfaceSlimeChance);
		r.hostile = group(MobCategory.MONSTER, settings, c);
		r.passive = group(MobCategory.CREATURE, settings, c);
		r.ambient = group(MobCategory.AMBIENT, settings, c);
		if (slimeChunk == null && settings != null) {
			Context yes = new Context(level, pos, r.biome, Boolean.TRUE, surfaceSlimeChance);
			Group g = group(MobCategory.MONSTER, settings, yes);
			for (Row row : g.rows) if (id(row.type()).equals("slime")) r.slimeIfChunk = row.chance();
		}
		boolean peaceful = level.getDifficulty() == Difficulty.PEACEFUL;
		r.hostile.disabled = peaceful;
		for (Group g : List.of(r.hostile, r.passive, r.ambient)) {
			g.count = counts.getOrDefault(g.category, 0);
			g.free = g.disabled ? 0 : Math.clamp(1 - g.count / (double) g.cap, 0, 1);
		}
		r.problem = problem(level, pos, distance);
		return r;
	}

	/** O motivo mais comum de nada nascer no bloco (o primeiro que valer). */
	private static Problem problem(ClientLevel level, BlockPos pos, double distance) {
		BlockState here = level.getBlockState(pos);
		if (here.isRedstoneConductor(level, pos)) return Problem.SOLID;
		if (distance >= 0 && distance <= 24) return Problem.TOO_CLOSE;
		var spawn = level.getLevelData().getRespawnData();
		if (spawn.dimension() == level.dimension() && spawn.pos().closerToCenterThan(new net.minecraft.world.phys.Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5), 24.0)) {
			return Problem.NEAR_SPAWN;
		}
		if (distance > 128) return Problem.TOO_FAR;
		EntityType<?> zombie = EntityTypes.ZOMBIE;
		BlockPos below = pos.below();
		if (!level.getBlockState(below).isValidSpawn(level, below, zombie) && level.getFluidState(pos).isEmpty()) return Problem.BAD_FLOOR;
		if (!level.noCollision(zombie.getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5)) && level.getFluidState(pos).isEmpty()) return Problem.NO_ROOM;
		return Problem.NONE;
	}

	/** Chunk do bloco. */
	public static ChunkPos chunk(BlockPos pos) {
		return ChunkPos.containing(pos);
	}
}
