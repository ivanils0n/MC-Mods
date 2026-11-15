package io.github.ivanils0n.chunkmap;

import io.github.ivanils0n.chunkmap.gui.MapScreen;
import io.github.ivanils0n.chunkmap.gui.OptionsScreen;
import io.github.ivanils0n.chunkmap.gui.WaypointListScreen;
import io.github.ivanils0n.chunkmap.gui.WaypointScreen;
import io.github.ivanils0n.chunkmap.seed.SeedWorld;
import io.github.ivanils0n.chunkmap.seed.StructureFinder;
import io.github.ivanils0n.chunkmap.seed.StructureIcon;
import io.github.ivanils0n.chunkmap.seed.VanillaWorldgen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Só no CI (runClientGameTest com xvfb). Abre o jogo de verdade e:
 * <ul>
 * <li>confere as estruturas previstas pela seed com as que o servidor gera de verdade (chunk a chunk, até o estágio
 * de "inícios de estruturas"), pelos dois caminhos do mod: geração do servidor (singleplayer) e geração vanilla
 * carregada pelo próprio mod (multiplayer);</li>
 * <li>confere os biomas dos dois caminhos entre si e com o mundo carregado;</li>
 * <li>tira prints do minimapa, do mapa (perto, longe, terreno, biomas destacados, Nether, End), dos waypoints e das
 * opções, em singleplayer e num servidor dedicado de verdade (multiplayer, seed digitada).</li>
 * </ul>
 */
public class ClientShots implements FabricClientGameTest {
	private static final String SEED = "chunkmap";
	private static final int RADIUS = 4096;
	private final List<String> report = new ArrayList<>();

	private void log(String line) {
		report.add(line);
		ChunkMap.LOG.info("CHUNKMAP TESTE {}", line);
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		long seed = WorldOptions.parseSeed(SEED).orElseThrow();
		try (TestSingleplayerContext sp = context.worldBuilder().setUseConsistentSettings(false)
				.adjustSettings(s -> {
					s.setSeed(SEED);
					s.setAllowCommands(true);
				}).create()) {
			context.waitTicks(40);
			sp.getServer().runCommand("time set 6000");
			sp.getServer().runCommand("weather clear");
			sp.getServer().runCommand("gamerule doDaylightCycle false");
			sp.getConnection().waitForChunksRender();
			context.waitFor(mc -> ChunkMap.session() != null && ChunkMap.session().status(Level.OVERWORLD) == WorldSession.SeedStatus.READY, 600);
			context.waitTicks(120);
			context.takeScreenshot("01_hud_minimapa");

			accuracy(context, sp, seed);

			BlockPos p = context.computeOnClient(mc -> mc.player.blockPosition());
			mapShot(context, Level.OVERWORLD, p.getX(), p.getZ(), 0.5f, "02_mapa_2_blocos_por_pixel", 160);
			mapShot(context, Level.OVERWORLD, p.getX(), p.getZ(), 1 / 16f, "03_mapa_longe_16_blocos", 300);
			mapShot(context, Level.OVERWORLD, p.getX(), p.getZ(), 1 / 64f, "04_mapa_muito_longe_64_blocos", 300);
			mapShot(context, Level.OVERWORLD, p.getX(), p.getZ(), 4f, "05_mapa_perto", 120);
			context.runOnClient(mc -> {
				ChunkMap.session().data.showTerrain = true;
				ChunkMap.session().data.showGrid = true;
				ChunkMap.session().data.filters.add("slime_chunk");
			});
			mapShot(context, Level.OVERWORLD, p.getX(), p.getZ(), 2f, "06_mapa_terreno_grade_slime", 120);
			context.runOnClient(mc -> {
				ChunkMap.session().data.showTerrain = false;
				ChunkMap.session().data.showGrid = false;
				ChunkMap.session().highlight.add("minecraft:plains");
				ChunkMap.session().highlight.add("minecraft:forest");
				ChunkMap.session().data.filters.remove("slime_chunk");
			});
			context.setScreen(() -> {
				MapScreen m = new MapScreen(Level.OVERWORLD, p.getX(), p.getZ(), 1 / 8f);
				m.showBiomePanel();
				return m;
			});
			context.waitTicks(5);
			context.runOnClient(mc -> {
				if (mc.gui.screen() instanceof MapScreen m) m.showBiomePanel();
			});
			context.waitTicks(80);
			context.takeScreenshot("07_mapa_biomas_destacados");
			context.runOnClient(mc -> ChunkMap.session().highlight.clear());
			mapShot(context, Level.NETHER, p.getX() / 8, p.getZ() / 8, 1 / 4f, "08_mapa_nether", 300);
			mapShot(context, Level.END, 0, 0, 1 / 16f, "09_mapa_end", 300);

			// Teclas de verdade: M abre o mapa, M fecha; B abre o novo waypoint.
			context.setScreen(() -> null);
			context.getInput().pressKey(Keys.OPEN_MAP);
			context.waitTicks(5);
			boolean opened = context.computeOnClient(mc -> mc.gui.screen() instanceof MapScreen);
			context.getInput().pressKey(Keys.OPEN_MAP);
			context.waitTicks(5);
			boolean closed = context.computeOnClient(mc -> mc.gui.screen() == null);
			context.getInput().pressKey(Keys.WAYPOINT);
			context.waitTicks(5);
			boolean wp = context.computeOnClient(mc -> mc.gui.screen() instanceof WaypointScreen);
			context.takeScreenshot("10_tecla_b_novo_waypoint");
			log("teclas: M abre=" + opened + " M fecha=" + closed + " B waypoint=" + wp);

			// Waypoint 25 blocos à frente: alfinete no mundo e no minimapa.
			context.setScreen(() -> null);
			context.runOnClient(mc -> {
				var pl = mc.player;
				pl.setYRot(0);
				pl.setXRot(0);
				WorldSession s = ChunkMap.session();
				s.data.waypoints.add(new Waypoint("Base", pl.getBlockX(), pl.getBlockY(), pl.getBlockZ() + 25, "minecraft:overworld", Waypoint.COLORS[0]));
				s.data.waypoints.add(new Waypoint("Mina", pl.getBlockX() + 300, 40, pl.getBlockZ() - 200, "minecraft:overworld", Waypoint.COLORS[1]));
			});
			context.getInput().lookAt(0, 0);
			context.waitTicks(10);
			context.takeScreenshot("11_waypoint_no_mundo");
			context.setScreen(() -> new WaypointListScreen(null));
			context.waitTicks(5);
			context.takeScreenshot("12_lista_waypoints");
			context.setScreen(() -> new OptionsScreen(null));
			context.waitTicks(5);
			context.takeScreenshot("13_opcoes");
			context.setScreen(() -> null);

			// Morte vira waypoint.
			sp.getServer().runCommand("gamemode survival @a");
			sp.getServer().runCommand("kill @a");
			context.waitTicks(20);
			boolean death = context.computeOnClient(mc -> ChunkMap.session().data.waypoints.stream().anyMatch(w -> w.death));
			log("ponto de morte criado=" + death);
			context.runOnClient(mc -> mc.player.respawn());
			context.waitTicks(40);
			context.setScreen(() -> null);
		}

		multiplayer(context, seed);
		reportScreen(context);
	}

	private void mapShot(ClientGameTestContext context, net.minecraft.resources.ResourceKey<Level> dim, double x, double z, float scale, String name, int maxTicks) {
		context.setScreen(() -> new MapScreen(dim, x, z, scale));
		long start = System.nanoTime();
		context.waitTicks(10);
		// Espera os cálculos terminarem (ou o limite), para o print mostrar o mapa pronto.
		for (int i = 0; i < maxTicks / 10; i++) {
			boolean busy = context.computeOnClient(mc -> Workers.MAP.getActiveCount() > 0 || Workers.mapBacklog() > 0);
			if (!busy) break;
			context.waitTicks(10);
		}
		context.waitTicks(10);
		log(String.format(Locale.ROOT, "%s: pronto em %d ms", name, (System.nanoTime() - start) / 1_000_000));
		context.takeScreenshot(name);
	}

	/** Estruturas previstas x geradas de verdade, e biomas dos dois caminhos. */
	private void accuracy(ClientGameTestContext context, TestSingleplayerContext sp, long seed) {
		long t0 = System.nanoTime();
		SeedWorld server = sp.getServer().computeOnServer(s -> SeedWorld.fromServer(s.overworld()));
		SeedWorld vanilla = WorldSession.vanilla(VanillaWorldgen.get().join(), "normal", Level.OVERWORLD, seed);
		log(String.format(Locale.ROOT, "geração vanilla pronta em %d ms (seed %d)", (System.nanoTime() - t0) / 1_000_000, seed));

		// Biomas: os dois caminhos têm que ser idênticos.
		Random r = new Random(1);
		int same = 0, n = 4000;
		for (int i = 0; i < n; i++) {
			int x = r.nextInt(12000) - 6000, z = r.nextInt(12000) - 6000;
			if (SeedWorld.id(server.biomeAt(x, z, false)).equals(SeedWorld.id(vanilla.biomeAt(x, z, false)))) same++;
		}
		log(String.format(Locale.ROOT, "biomas singleplayer x multiplayer: %d/%d iguais", same, n));
		// Biomas da superfície x mundo carregado (bioma real no bloco do chão).
		int[] real = sp.getServer().computeOnServer(s -> {
			ServerLevel level = s.overworld();
			BlockPos c = level.getRespawnData().pos();
			int ok = 0, total = 0;
			for (int dx = -48; dx <= 48; dx += 6) {
				for (int dz = -48; dz <= 48; dz += 6) {
					int x = c.getX() + dx, z = c.getZ() + dz;
					int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
					String realId = SeedWorld.id(level.getBiome(new BlockPos(x, y, z)));
					if (realId.equals(SeedWorld.id(server.biomeAt(x, z, false)))) ok++;
					total++;
				}
			}
			return new int[]{ok, total};
		});
		log(String.format(Locale.ROOT, "biomas previstos x mundo de verdade (perto do spawn): %d/%d", real[0], real[1]));

		long t1 = System.nanoTime();
		int tiles = 0;
		for (int i = 0; i < 16384 * 4; i++) server.biomeAt(i % 1024 * 4, i / 1024 * 4, false);
		tiles = 4;
		log(String.format(Locale.ROOT, "velocidade: %d ms por pedaço de 128x128 pixels", (System.nanoTime() - t1) / 1_000_000 / tiles));

		// Estruturas: cada chunk candidato de cada conjunto, previsto x gerado.
		StructureFinder fs = new StructureFinder(server), fv = new StructureFinder(vanilla);
		StructureIcon[] icons = {StructureIcon.VILLAGE, StructureIcon.DESERT_PYRAMID, StructureIcon.JUNGLE_TEMPLE, StructureIcon.SWAMP_HUT,
				StructureIcon.IGLOO, StructureIcon.OCEAN_MONUMENT, StructureIcon.MANSION, StructureIcon.PILLAGER_OUTPOST, StructureIcon.SHIPWRECK,
				StructureIcon.RUINED_PORTAL, StructureIcon.ANCIENT_CITY, StructureIcon.TRIAL_CHAMBERS};
		for (StructureIcon icon : icons) {
			long ti = System.nanoTime();
			Set<Long> ps = chunks(fs.computeNow(icon, -RADIUS, -RADIUS, RADIUS, RADIUS));
			long ms = (System.nanoTime() - ti) / 1_000_000;
			Set<Long> pv = chunks(fv.computeNow(icon, -RADIUS, -RADIUS, RADIUS, RADIUS));
			Set<Long> truth = sp.getServer().computeOnServer(s -> generated(s, icon));
			log(String.format(Locale.ROOT, "%-16s reais %3d | sp: certos %3d, a mais %2d, faltando %2d | mp: certos %3d, a mais %2d, faltando %2d | %d ms",
					icon.id, truth.size(), inter(ps, truth), minus(ps, truth), minus(truth, ps), inter(pv, truth), minus(pv, truth), minus(truth, pv), ms));
		}
		fs.close();
		fv.close();
		// Diagnóstico: o /locate do jogo para alguns tipos, a partir de (0, 0).
		for (StructureIcon icon : new StructureIcon[]{StructureIcon.SHIPWRECK, StructureIcon.VILLAGE, StructureIcon.PILLAGER_OUTPOST, StructureIcon.DESERT_PYRAMID}) {
			String loc = sp.getServer().computeOnServer(s -> {
				ServerLevel level = s.overworld();
				var reg = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
				List<Holder<Structure>> list = new ArrayList<>();
				reg.listElements().forEach(h -> {
					if (StructureIcon.forStructure(SeedWorld.id(h)) == icon) list.add(h);
				});
				var found = level.getChunkSource().getGenerator().findNearestMapStructure(level, net.minecraft.core.HolderSet.direct(list), BlockPos.ZERO, 100, false);
				return found == null ? "nada" : found.getFirst().toShortString();
			});
			log("locate " + icon.id + " -> " + loc);
		}
		// Diagnóstico: quantos candidatos passam em cada etapa (navios).
		int[] c = new int[3];
		for (Holder<StructureSet> set : server.state.possibleStructureSets()) {
			if (!SeedWorld.id(set).contains("shipwreck")) continue;
			var spread = (RandomSpreadStructurePlacement) set.value().placement();
			int rb = spread.spacing() * 16;
			for (int rx = Math.floorDiv(-RADIUS, rb); rx <= Math.floorDiv(RADIUS, rb); rx++) {
				for (int rz = Math.floorDiv(-RADIUS, rb); rz <= Math.floorDiv(RADIUS, rb); rz++) {
					ChunkPos cp = spread.getPotentialStructureChunk(server.state.getLevelSeed(), rx, rz);
					c[0]++;
					if (spread.isStructureChunk(server.state, cp.x(), cp.z())) c[1]++;
					if (server.check(set, cp.x(), cp.z()) != null) c[2]++;
				}
			}
		}
		// O navio do /locate: é candidato? o servidor tem início lá? o que o mod acha?
		for (Holder<StructureSet> set : server.state.possibleStructureSets()) {
			if (!SeedWorld.id(set).contains("shipwreck")) continue;
			var spread = (RandomSpreadStructurePlacement) set.value().placement();
			ChunkPos lc = new ChunkPos(-752 >> 4, -656 >> 4);
			ChunkPos cand = spread.getPotentialStructureChunk(server.state.getLevelSeed(), Math.floorDiv(lc.x(), spread.spacing()), Math.floorDiv(lc.z(), spread.spacing()));
			String starts = sp.getServer().computeOnServer(s -> {
				ServerLevel level = s.overworld();
				ChunkAccess ch = level.getChunk(lc.x(), lc.z(), ChunkStatus.STRUCTURE_STARTS, true);
				StringBuilder b = new StringBuilder(ch.getPersistedStatus() + " ");
				ch.getAllStarts().forEach((k, v) -> b.append(level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(k)).append(v.isValid() ? "+" : "-").append(' '));
				return b.toString();
			});
			var biome = server.biomeAt(lc.getMiddleBlockX(), lc.getMiddleBlockZ(), false);
			boolean inSet = set.value().structures().getFirst().structure().value().biomes().contains(biome);
			log("navio do locate: chunk " + lc + " candidato " + cand + " inícios: " + starts + " | mod: " + server.check(set, lc.x(), lc.z())
					+ " | bioma " + SeedWorld.id(biome) + " serve=" + inSet + " spacing " + spread.spacing() + "/" + spread.separation());
		}
		log("navios: candidatos " + c[0] + ", na grade " + c[1] + ", aceitos " + c[2] + "; conjuntos: " + server.state.possibleStructureSets().size());
	}

	private static Set<Long> chunks(List<SeedWorld.Found> list) {
		Set<Long> out = new HashSet<>();
		for (SeedWorld.Found f : list) out.add(ChunkPos.pack(f.chunkX(), f.chunkZ()));
		return out;
	}

	private static int inter(Set<Long> a, Set<Long> b) {
		int n = 0;
		for (Long k : a) if (b.contains(k)) n++;
		return n;
	}

	private static int minus(Set<Long> a, Set<Long> b) {
		int n = 0;
		for (Long k : a) if (!b.contains(k)) n++;
		return n;
	}

	/** Chunks candidatos onde o servidor gerou de verdade uma estrutura do filtro (até o estágio de inícios). */
	private static Set<Long> generated(MinecraftServer s, StructureIcon icon) {
		ServerLevel level = s.overworld();
		Set<Long> out = new HashSet<>();
		var state = level.getChunkSource().getGeneratorState();
		for (Holder<StructureSet> set : state.possibleStructureSets()) {
			boolean mine = false;
			for (StructureSet.StructureSelectionEntry e : set.value().structures()) {
				if (StructureIcon.forStructure(SeedWorld.id(e.structure())) == icon) mine = true;
			}
			if (!mine || !(set.value().placement() instanceof RandomSpreadStructurePlacement spread)) continue;
			int rb = spread.spacing() * 16;
			for (int rx = Math.floorDiv(-RADIUS, rb); rx <= Math.floorDiv(RADIUS, rb); rx++) {
				for (int rz = Math.floorDiv(-RADIUS, rb); rz <= Math.floorDiv(RADIUS, rb); rz++) {
					ChunkPos c = spread.getPotentialStructureChunk(state.getLevelSeed(), rx, rz);
					ChunkAccess chunk = level.getChunk(c.x(), c.z(), ChunkStatus.STRUCTURE_STARTS, true);
					for (Map.Entry<Structure, StructureStart> e : chunk.getAllStarts().entrySet()) {
						String id = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(e.getKey()).toString();
						if (e.getValue().isValid() && StructureIcon.forStructure(id) == icon) out.add(c.pack());
					}
				}
			}
		}
		return out;
	}

	private void multiplayer(ClientGameTestContext context, long seed) {
		try (TestDedicatedServerContext server = context.worldBuilder().setUseConsistentSettings(false)
				.adjustSettings(s -> s.setSeed(SEED)).createServer();
				TestDedicatedServerConnection conn = server.connect()) {
			conn.waitForChunksRender();
			context.waitTicks(20);
			boolean noSeed = context.computeOnClient(mc -> ChunkMap.session().status(mc.level.dimension()) == WorldSession.SeedStatus.NONE);
			BlockPos p = context.computeOnClient(mc -> mc.player.blockPosition());
			mapShot(context, Level.OVERWORLD, p.getX(), p.getZ(), 0.5f, "20_multiplayer_sem_seed", 40);
			long t = System.nanoTime();
			context.runOnClient(mc -> {
				WorldSession s = ChunkMap.session();
				s.data.seed = SEED;
				s.resetSeed();
			});
			context.waitFor(mc -> ChunkMap.session().status(Level.OVERWORLD) != WorldSession.SeedStatus.LOADING, 1200);
			WorldSession.SeedStatus st = context.computeOnClient(mc -> ChunkMap.session().status(Level.OVERWORLD));
			log(String.format(Locale.ROOT, "multiplayer: sem seed antes=%s, depois de digitar=%s em %d ms", noSeed, st, (System.nanoTime() - t) / 1_000_000));
			context.setScreen(() -> null);
			context.waitTicks(100);
			context.takeScreenshot("21_multiplayer_hud");
			mapShot(context, Level.OVERWORLD, p.getX(), p.getZ(), 1 / 4f, "22_multiplayer_mapa", 300);
			context.setScreen(() -> null);
		}
	}

	private void reportScreen(ClientGameTestContext context) {
		List<String> lines = List.copyOf(report);
		context.setScreen(() -> new Screen(Component.literal("ChunkMap: teste")) {
			@Override
			public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
				g.fill(0, 0, width, height, 0xFF101820);
				int longest = 1;
				for (String l : lines) longest = Math.max(longest, font.width(l));
				float k = Math.min(1f, (width - 8f) / longest);
				g.pose().pushMatrix();
				g.pose().scale(k, k);
				for (int i = 0; i < lines.size(); i++) g.text(font, lines.get(i), 4, 4 + i * 10, 0xFFFFFFFF, false);
				g.pose().popMatrix();
			}
		});
		context.waitTicks(5);
		context.takeScreenshot("99_resultado_dos_testes");
		context.setScreen(() -> null);
	}
}
