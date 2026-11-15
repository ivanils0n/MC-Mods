package io.github.ivanils0n.chunkmap.seed;

import io.github.ivanils0n.chunkmap.ChunkMap;
import io.github.ivanils0n.chunkmap.Workers;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.TagLoader;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Os registros de geração de mundo do próprio jogo (biomas, ruídos, estruturas, predefinições), lidos do data pack
 * vanilla que vem dentro do Minecraft, do mesmo jeito que o servidor faz ao criar um mundo. Usado no multiplayer, onde
 * o cliente não recebe a geração do servidor. Carrega uma vez só (1 a 3 s), em segundo plano.
 */
public final class VanillaWorldgen {
	private static CompletableFuture<RegistryAccess.Frozen> future;

	private VanillaWorldgen() {}

	public static synchronized CompletableFuture<RegistryAccess.Frozen> get() {
		if (future == null || future.isCompletedExceptionally()) {
			future = CompletableFuture.supplyAsync(VanillaWorldgen::load, Workers.SLOW);
		}
		return future;
	}

	private static RegistryAccess.Frozen load() {
		long start = System.nanoTime();
		VanillaPackResources pack = ServerPacksSource.createVanillaPackSource();
		try (MultiPackResourceManager resources = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(pack))) {
			LayeredRegistryAccess<RegistryLayer> layers = RegistryLayer.createRegistryAccess();
			List<Registry.PendingTags<?>> staticTags = TagLoader.loadTagsForExistingRegistries(resources, layers.getLayer(RegistryLayer.STATIC));
			List<HolderLookup.RegistryLookup<?>> lookups = TagLoader.buildUpdatedLookups(layers.getAccessForLoading(RegistryLayer.WORLDGEN), staticTags);
			RegistryAccess.Frozen worldgen = RegistryDataLoader.load(resources, lookups, RegistryDataLoader.WORLDGEN_REGISTRIES, Runnable::run).join();
			ChunkMap.LOG.info("ChunkMap: geração de mundo vanilla carregada em {} ms", (System.nanoTime() - start) / 1_000_000);
			return worldgen;
		} catch (RuntimeException e) {
			ChunkMap.LOG.error("ChunkMap: não deu para carregar a geração de mundo vanilla", e);
			throw e;
		}
	}
}
