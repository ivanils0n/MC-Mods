package io.github.ivanils0n.spawninfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A lista de mobs de cada bioma. O servidor não manda essa lista ao cliente, então:
 * <ul>
 * <li>singleplayer: lê do servidor do próprio jogo (exato, inclusive data packs);</li>
 * <li>multiplayer: lê do data pack vanilla que vem dentro do Minecraft (como o servidor faz ao criar um mundo),
 * uma vez só, em segundo plano.</li>
 * </ul>
 */
public final class BiomeSpawns {
	private static CompletableFuture<RegistryAccess.Frozen> vanilla;

	private BiomeSpawns() {}

	/** As regras de spawn do bioma, ou vazio enquanto a lista vanilla carrega (ou se o bioma não é conhecido). */
	public static Optional<MobSpawnSettings> of(Holder<Biome> clientBiome) {
		Optional<ResourceKey<Biome>> key = clientBiome.unwrapKey();
		if (key.isEmpty()) return Optional.empty();
		IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
		RegistryAccess access = server != null ? server.registryAccess() : vanillaNow();
		if (access == null) return Optional.empty();
		return access.lookupOrThrow(Registries.BIOME).get(key.get()).map(h -> h.value().getMobSettings());
	}

	/** Ainda carregando a lista vanilla (só no multiplayer). */
	public static boolean loading() {
		if (Minecraft.getInstance().getSingleplayerServer() != null) return false;
		return vanilla == null || !vanilla.isDone();
	}

	private static RegistryAccess vanillaNow() {
		CompletableFuture<RegistryAccess.Frozen> f = vanilla();
		return f.isDone() && !f.isCompletedExceptionally() ? f.join() : null;
	}

	public static synchronized CompletableFuture<RegistryAccess.Frozen> vanilla() {
		if (vanilla == null || vanilla.isCompletedExceptionally()) {
			vanilla = CompletableFuture.supplyAsync(BiomeSpawns::load, r -> {
				Thread t = new Thread(r, "SpawnInfo biomas");
				t.setDaemon(true);
				t.start();
			});
		}
		return vanilla;
	}

	private static RegistryAccess.Frozen load() {
		long start = System.nanoTime();
		try {
			VanillaPackResources pack = ServerPacksSource.createVanillaPackSource();
			MultiPackResourceManager resources = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(pack));
			LayeredRegistryAccess<RegistryLayer> layers = RegistryLayer.createRegistryAccess();
			List<Registry.PendingTags<?>> staticTags = TagLoader.loadTagsForExistingRegistries(resources, layers.getLayer(RegistryLayer.STATIC));
			List<HolderLookup.RegistryLookup<?>> lookups = TagLoader.buildUpdatedLookups(layers.getAccessForLoading(RegistryLayer.WORLDGEN), staticTags);
			RegistryAccess.Frozen worldgen = RegistryDataLoader.load(resources, lookups, RegistryDataLoader.WORLDGEN_REGISTRIES, Runnable::run).join();
			SpawnInfo.LOG.info("SpawnInfo: listas de mobs dos biomas carregadas em {} ms", (System.nanoTime() - start) / 1_000_000);
			return worldgen;
		} catch (RuntimeException e) {
			SpawnInfo.LOG.error("SpawnInfo: não deu para carregar as listas de mobs dos biomas", e);
			throw e;
		}
	}
}
