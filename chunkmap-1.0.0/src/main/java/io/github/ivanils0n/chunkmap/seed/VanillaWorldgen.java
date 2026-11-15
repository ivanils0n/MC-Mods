package io.github.ivanils0n.chunkmap.seed;

import io.github.ivanils0n.chunkmap.ChunkMap;
import io.github.ivanils0n.chunkmap.Workers;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.LevelStorageSource;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Os registros de geração de mundo do próprio jogo (biomas, ruídos, estruturas, predefinições), lidos do data pack
 * vanilla que vem dentro do Minecraft, do mesmo jeito que o servidor faz ao criar um mundo. Usado no multiplayer, onde
 * o cliente não recebe a geração do servidor. Carrega uma vez só (1 a 3 s), em segundo plano.
 */
public final class VanillaWorldgen {
	/** Os registros e os moldes das estruturas (null se não deu para criar). */
	public record Data(RegistryAccess.Frozen access, StructureTemplateManager templates) {}

	private static CompletableFuture<Data> future;
	/** Fica aberto: os moldes são lidos dele sob demanda. */
	private static MultiPackResourceManager resources;
	private static LevelStorageSource.LevelStorageAccess storage;

	private VanillaWorldgen() {}

	public static synchronized CompletableFuture<Data> get() {
		if (future == null || future.isCompletedExceptionally()) {
			future = CompletableFuture.supplyAsync(VanillaWorldgen::load, Workers.SLOW);
		}
		return future;
	}

	private static Data load() {
		long start = System.nanoTime();
		try {
			if (resources == null) {
				VanillaPackResources pack = ServerPacksSource.createVanillaPackSource();
				resources = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(pack));
			}
			LayeredRegistryAccess<RegistryLayer> layers = RegistryLayer.createRegistryAccess();
			List<Registry.PendingTags<?>> staticTags = TagLoader.loadTagsForExistingRegistries(resources, layers.getLayer(RegistryLayer.STATIC));
			List<HolderLookup.RegistryLookup<?>> lookups = TagLoader.buildUpdatedLookups(layers.getAccessForLoading(RegistryLayer.WORLDGEN), staticTags);
			RegistryAccess.Frozen worldgen = RegistryDataLoader.load(resources, lookups, RegistryDataLoader.WORLDGEN_REGISTRIES, Runnable::run).join();
			ChunkMap.LOG.info("ChunkMap: geração de mundo vanilla carregada em {} ms", (System.nanoTime() - start) / 1_000_000);
			return new Data(worldgen, templates());
		} catch (RuntimeException e) {
			ChunkMap.LOG.error("ChunkMap: não deu para carregar a geração de mundo vanilla", e);
			throw e;
		}
	}

	/**
	 * Gerenciador de moldes das estruturas lendo do data pack vanilla. Ele exige uma "pasta de mundo" (para moldes
	 * salvos com o bloco de estrutura); usa uma vazia em chunkmap/.moldes, que nunca recebe nada.
	 */
	private static StructureTemplateManager templates() {
		try {
			if (storage == null) {
				storage = LevelStorageSource.createDefault(ChunkMap.gameDir().resolve("chunkmap").resolve(".moldes")).createAccess("vanilla");
			}
			return new StructureTemplateManager(resources, storage, DataFixers.getDataFixer(), BuiltInRegistries.BLOCK);
		} catch (Exception | LinkageError e) {
			ChunkMap.LOG.warn("ChunkMap: sem os moldes das estruturas (vilas e afins conferidas só pelo bioma): {}", e.toString());
			return null;
		}
	}
}
