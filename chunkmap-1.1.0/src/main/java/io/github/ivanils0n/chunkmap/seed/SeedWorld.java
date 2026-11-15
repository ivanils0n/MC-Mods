package io.github.ivanils0n.chunkmap.seed;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A geração de uma dimensão para uma seed: o mesmo código que o jogo usa para escolher biomes e onde nascem as
 * estruturas. Tudo aqui pode ser chamado de várias threads ao mesmo tempo (a geração do jogo também é).
 */
public final class SeedWorld {
	public final RegistryAccess access;
	public final ChunkGenerator generator;
	public final BiomeSource biomeSource;
	public final RandomState randomState;
	public final Climate.Sampler sampler;
	public final ChunkGeneratorStructureState state;
	public final long seed;
	public final LevelHeightAccessor height;
	/** Moldes das estruturas (servidor no singleplayer, jogo no multiplayer); null = só biomas nas de quebra-cabeça. */
	private final StructureTemplateManager templates;
	private final int surfaceQuart;
	private final int caveQuart;

	private static final java.util.Set<String> WARNED = ConcurrentHashMap.newKeySet();

	/** Índice de cada bioma (para os blocos de mapa guardarem só um número por pixel). */
	private final ConcurrentHashMap<Holder<Biome>, Integer> biomeIndex = new ConcurrentHashMap<>();
	private final List<Holder<Biome>> biomes = new CopyOnWriteArrayList<>();

	private SeedWorld(RegistryAccess access, ChunkGenerator generator, RandomState randomState, ChunkGeneratorStructureState state,
			long seed, LevelHeightAccessor height, StructureTemplateManager templates) {
		this.access = access;
		this.generator = generator;
		this.biomeSource = generator.getBiomeSource();
		this.randomState = randomState;
		this.sampler = randomState.sampler();
		this.state = state;
		this.seed = seed;
		this.height = height;
		this.templates = templates;
		int top = height.getMinY() + height.getHeight() - 1;
		// Bem acima do chão o "depth" do clima é negativo: dá o bioma da superfície (sem cavernas), o mesmo que o
		// jogo usa no chão, sem precisar calcular a altura do terreno em cada ponto.
		this.surfaceQuart = QuartPos.fromBlock(Math.min(top, Math.max(height.getMinY(), 256)));
		this.caveQuart = QuartPos.fromBlock(Math.clamp(0, height.getMinY(), top));
	}

	/** O mundo do singleplayer com a própria seed: usa a geração do servidor integrado (exata, inclusive data packs). */
	public static SeedWorld fromServer(ServerLevel level) {
		var source = level.getChunkSource();
		return new SeedWorld(level.registryAccess(), source.getGenerator(), source.randomState(), source.getGeneratorState(),
				level.getSeed(), LevelHeightAccessor.create(level.getMinY(), level.getHeight()), level.getServer().getStructureManager());
	}

	/** Outra seed com uma geração conhecida. Null se a dimensão não usa a geração por ruído (ex.: mundo plano). */
	public static SeedWorld create(RegistryAccess access, ChunkGenerator generator, long seed, LevelHeightAccessor height,
			StructureTemplateManager templates) {
		if (!(generator instanceof NoiseBasedChunkGenerator noise)) return null;
		RandomState rs = RandomState.create(noise.generatorSettings().value(), access.lookupOrThrow(Registries.NOISE), seed);
		ChunkGeneratorStructureState st = generator.createState(access.lookupOrThrow(Registries.STRUCTURE_SET), rs, seed);
		return new SeedWorld(access, generator, rs, st, seed, height, templates);
	}

	public Holder<Biome> biomeAt(int blockX, int blockZ, boolean caves) {
		return biomeSource.getNoiseBiome(QuartPos.fromBlock(blockX), caves ? caveQuart : surfaceQuart, QuartPos.fromBlock(blockZ), sampler);
	}

	public int indexOf(Holder<Biome> biome) {
		Integer i = biomeIndex.get(biome);
		if (i != null) return i;
		synchronized (biomes) {
			return biomeIndex.computeIfAbsent(biome, b -> {
				biomes.add(b);
				return biomes.size() - 1;
			});
		}
	}

	public Holder<Biome> biome(int index) {
		return biomes.get(index);
	}

	public int biomeCount() {
		return biomes.size();
	}

	public static String id(Holder<?> holder) {
		return holder.unwrapKey().map(k -> k.identifier().toString()).orElse("?");
	}

	/** Achado de uma estrutura: posição para mostrar e o id dela. */
	public record Found(StructureIcon icon, String structure, int x, int z, int chunkX, int chunkZ) {}

	/**
	 * A mesma escolha do jogo (ChunkGenerator.createStructures) para o chunk candidato de um conjunto de estruturas:
	 * confere a frequência e a distância de outras estruturas, sorteia qual estrutura do conjunto tenta e confere o
	 * bioma (e, quando dá, o terreno) como o jogo faz.
	 */
	public Found check(Holder<StructureSet> set, int chunkX, int chunkZ) {
		StructureSet value = set.value();
		if (!value.placement().isStructureChunk(state, chunkX, chunkZ)) return null;
		List<StructureSet.StructureSelectionEntry> list = value.structures();
		if (list.isEmpty()) return null;
		if (list.size() == 1) return tryStructure(list.get(0).structure(), chunkX, chunkZ);
		ArrayList<StructureSet.StructureSelectionEntry> left = new ArrayList<>(list);
		WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
		random.setLargeFeatureSeed(state.getLevelSeed(), chunkX, chunkZ);
		int total = 0;
		for (StructureSet.StructureSelectionEntry e : left) total += e.weight();
		while (!left.isEmpty() && total > 0) {
			int j = random.nextInt(total);
			int k = 0;
			for (StructureSet.StructureSelectionEntry e : left) {
				j -= e.weight();
				if (j < 0) break;
				k++;
			}
			StructureSet.StructureSelectionEntry chosen = left.get(k);
			Found f = tryStructure(chosen.structure(), chunkX, chunkZ);
			if (f != null) return f;
			left.remove(k);
			total -= chosen.weight();
		}
		return null;
	}

	private Found tryStructure(Holder<Structure> holder, int chunkX, int chunkZ) {
		String id = id(holder);
		StructureIcon icon = StructureIcon.forStructure(id);
		Structure structure = holder.value();
		ChunkPos chunk = new ChunkPos(chunkX, chunkZ);
		boolean jigsaw = structure instanceof JigsawStructure;
		// Filtro rápido (só biomas) antes da conferência do jogo, que calcula a altura do terreno e é ~100x mais lenta:
		// se nenhum bioma em volta do lugar onde a estrutura começaria serve, ela não nasce ali.
		if (!biomeNear(structure, chunk, jigsaw, startY(id))) return null;
		// A conferência do próprio jogo: terreno, bioma no ponto exato e, nas de quebra-cabeça (vilas, postos, cidades...),
		// a peça inicial (precisa dos moldes, que vêm do servidor no singleplayer e do jogo no multiplayer).
		if (templates != null || !jigsaw) {
			try {
				Structure.GenerationContext ctx = new Structure.GenerationContext(access, generator, biomeSource, randomState,
						templates, seed, chunk, height, structure.biomes()::contains);
				Optional<Structure.GenerationStub> stub = structure.findValidGenerationPoint(ctx);
				if (stub.isEmpty()) return null;
				BlockPos p = stub.get().position();
				return new Found(icon, id, p.getX(), p.getZ(), chunkX, chunkZ);
			} catch (RuntimeException e) {
				// Faltou algo (ex.: um molde): fica só a conferência do bioma. Avisa uma vez por tipo.
				if (WARNED.add(id)) io.github.ivanils0n.chunkmap.ChunkMap.LOG.info("ChunkMap: {} conferida só pelo bioma ({})", id, e.toString());
			}
		}
		int x = chunk.getMiddleBlockX(), z = chunk.getMiddleBlockZ();
		Holder<Biome> biome = biomeSource.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(startY(id)), QuartPos.fromBlock(z), sampler);
		return structure.biomes().contains(biome) ? new Found(icon, id, x, z, chunkX, chunkZ) : null;
	}

	/**
	 * Algum bioma da estrutura perto de onde ela começaria? Normais: os 4x4 "quarts" do chunk (umas começam no meio,
	 * outras no canto, como o portal em ruínas). Quebra-cabeça:
	 * a peça inicial gira em volta do canto do chunk e pode passar dele, então uma grade 5x5 a cada 8 blocos em volta do
	 * canto (de -12 a +20).
	 */
	private boolean biomeNear(Structure structure, ChunkPos chunk, boolean jigsaw, int y) {
		int qy = QuartPos.fromBlock(y);
		if (jigsaw) {
			for (int dx = -12; dx <= 20; dx += 8) {
				for (int dz = -12; dz <= 20; dz += 8) {
					int qx = QuartPos.fromBlock(chunk.getMinBlockX() + dx), qz = QuartPos.fromBlock(chunk.getMinBlockZ() + dz);
					if (structure.biomes().contains(biomeSource.getNoiseBiome(qx, qy, qz, sampler))) return true;
				}
			}
			return false;
		}
		int qx = QuartPos.fromBlock(chunk.getMinBlockX()), qz = QuartPos.fromBlock(chunk.getMinBlockZ());
		for (int dx = 0; dx < 4; dx++) {
			for (int dz = 0; dz < 4; dz++) {
				if (structure.biomes().contains(biomeSource.getNoiseBiome(qx + dx, qy, qz + dz, sampler))) return true;
			}
		}
		return false;
	}

	/** Altura em que o jogo confere o bioma das estruturas subterrâneas de quebra-cabeça; as outras ficam no chão. */
	private int startY(String id) {
		return switch (id) {
			case "minecraft:ancient_city" -> -27;
			case "minecraft:trial_chambers" -> -30;
			default -> QuartPos.toBlock(surfaceQuart);
		};
	}
}
