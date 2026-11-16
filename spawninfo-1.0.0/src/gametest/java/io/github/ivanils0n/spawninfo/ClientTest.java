package io.github.ivanils0n.spawninfo;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Só no CI (runClientGameTest com xvfb). Abre o jogo de verdade e:
 * <ul>
 * <li>compara a chance calculada pelo mod (cliente) com as regras de verdade do servidor, sorteadas milhares de vezes
 * no mesmo bloco (sala escura num slime chunk, a mesma sala iluminada, sala fora de slime chunk, grama de dia e de noite);</li>
 * <li>confere a contagem do limite, os mobs à frente, a tecla de mostrar/esconder e a seed no multiplayer;</li>
 * <li>tira prints do painel e da tela de ajustes.</li>
 * </ul>
 * O log de cada conferência começa com "SPAWNINFO TESTE" e termina com OK ou FALHA.
 */
public class ClientTest implements FabricClientGameTest {
	private static final String SEED = "spawninfo";
	private static final int TRIES = 40000;
	private final List<String> report = new ArrayList<>();
	private int failures;

	private void log(String line) {
		report.add(line);
		SpawnInfo.LOG.info("SPAWNINFO TESTE {}", line);
	}

	private void check(String what, boolean ok, String detail) {
		if (!ok) failures++;
		log((ok ? "OK    " : "FALHA ") + what + (detail.isEmpty() ? "" : " | " + detail));
	}

	private static void cmd(TestSingleplayerContext sp, String... alternatives) {
		for (String c : alternatives) {
			try {
				sp.getServer().runCommand(c);
			} catch (RuntimeException e) {
				SpawnInfo.LOG.info("SPAWNINFO comando falhou ({}): {}", c, e.toString());
			}
		}
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
			cmd(sp, "time set 6000", "weather clear", "difficulty normal");
			cmd(sp, "gamerule spawn_mobs false", "gamerule doMobSpawning false", "gamerule advance_time false", "gamerule doDaylightCycle false");
			cmd(sp, "gamemode creative @a");
			sp.getConnection().waitForChunksRender();
			context.runOnClient(mc -> {
				Config.get.visible = true;
				Config.get.target = Config.Target.LOOK;
				Config.get.showAmbient = true;
			});

			// O mod usa a seed do mundo sozinho no singleplayer.
			long modSeed = context.computeOnClient(mc -> SpawnInfo.seed(mc).orElse(0));
			check("seed do mundo no singleplayer", modSeed == seed, modSeed + " x " + seed);

			// Um slime chunk e um chunk comum perto do jogador.
			BlockPos start = context.computeOnClient(mc -> mc.player.blockPosition());
			int[] slime = null, plain = null;
			for (int r = 2; r < 12 && (slime == null || plain == null); r++) {
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
						int cx = (start.getX() >> 4) + dx, cz = (start.getZ() >> 4) + dz;
						boolean is = SpawnCalc.isSlimeChunk(seed, cx, cz);
						if (is && slime == null) slime = new int[]{cx, cz};
						if (!is && plain == null && r >= 4) plain = new int[]{cx, cz};
					}
				}
			}
			log("slime chunk " + slime[0] + "," + slime[1] + " · chunk comum " + plain[0] + "," + plain[1]);
			boolean serverAgrees = sp.getServer().computeOnServer(s -> net.minecraft.world.level.levelgen.WorldgenRandom
					.seedSlimeChunk(0, 0, s.overworld().getSeed(), 987234911L).nextInt(10) == 0) == SpawnCalc.isSlimeChunk(seed, 0, 0);
			check("conta do slime chunk igual à do servidor", serverAgrees, "");

			// 1) Sala escura no slime chunk (y 10), com túnel até o jogador a 28 blocos.
			BlockPos room = room(context, sp, slime, false);
			compare(context, sp, room, "sala escura no slime chunk", true);
			context.takeScreenshot("01_sala_escura_slime_chunk");
			double slimeDark = rowChance(context, room, MobCategory.MONSTER, "slime");
			check("slime aparece na sala escura do slime chunk", slimeDark > 0.005, Hud.pct(slimeDark));

			// 2) A mesma sala iluminada: só o slime continua.
			cmd(sp, String.format(Locale.ROOT, "fill %d 14 %d %d 14 %d glowstone", room.getX() - 8, room.getZ() - 8, room.getX() + 7, room.getZ() + 7));
			context.waitTicks(40);
			compare(context, sp, room, "sala iluminada no slime chunk", false);
			context.runOnClient(SpawnInfo::refresh);
			context.waitTicks(5);
			context.takeScreenshot("02_sala_iluminada_so_slime");
			SpawnCalc.Result lit = context.computeOnClient(mc -> SpawnInfo.compute(mc, room, false));
			boolean onlySlime = lit.hostile.rows.stream().allMatch(r -> r.chance() == 0 || SpawnCalc.id(r.type()).equals("slime"));
			check("iluminada: só slime nasce", onlySlime && lit.hostile.total > 0, "total " + Hud.pct(lit.hostile.total));

			// 3) Sala escura fora de slime chunk: slime 0.
			BlockPos other = room(context, sp, plain, true);
			compare(context, sp, other, "sala escura fora do slime chunk", false);
			double slimeOther = rowChance(context, other, MobCategory.MONSTER, "slime");
			check("fora do slime chunk o slime não nasce", slimeOther == 0, Hud.pct(slimeOther));

			// 4) Grama na superfície, de dia (animais) e de noite (monstros).
			BlockPos grass = sp.getServer().computeOnServer(s -> {
				ServerLevel l = s.overworld();
				int x = start.getX() + 40, z = start.getZ() + 40;
				int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				return new BlockPos(x, y, z);
			});
			cmd(sp, String.format(Locale.ROOT, "fill %d %d %d %d %d %d grass_block", grass.getX() - 2, grass.getY() - 1, grass.getZ() - 2, grass.getX() + 2, grass.getY() - 1, grass.getZ() + 2));
			cmd(sp, String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", grass.getX() - 2, grass.getY(), grass.getZ() - 2, grass.getX() + 2, grass.getY() + 3, grass.getZ() + 2));
			cmd(sp, String.format(Locale.ROOT, "tp @p %d %d %d", grass.getX(), grass.getY() + 30, grass.getZ() + 30));
			context.waitTicks(60);
			compareCategory(context, sp, grass, MobCategory.CREATURE, "grama de dia (animais)");
			cmd(sp, "time set 18000");
			context.waitTicks(60);
			compareCategory(context, sp, grass, MobCategory.MONSTER, "grama de noite (monstros)");
			cmd(sp, "time set 6000");

			// Limite: 3 zumbis sem nome contam, 1 com nome não conta.
			cmd(sp, "kill @e[type=!player]");
			context.waitTicks(5);
			for (int i = 0; i < 3; i++) cmd(sp, "execute at @p run summon zombie ~" + (i * 2) + " ~ ~4 {NoAI:1b}");
			cmd(sp, "execute at @p run summon zombie ~-3 ~ ~4 {NoAI:1b,CustomName:\"Bob\",PersistenceRequired:1b}");
			context.waitTicks(50);
			context.runOnClient(SpawnInfo::refresh);
			int count = context.computeOnClient(mc -> SpawnInfo.result == null ? -1 : SpawnInfo.result.hostile.count);
			check("limite conta 3 zumbis (o com nome fica fora)", count == 3, "contou " + count);

			// Mobs à frente: zumbi e vaca na frente, esqueleto atrás.
			cmd(sp, "kill @e[type=!player]");
			cmd(sp, "execute at @p run tp @p ~ ~ ~ 0 0");
			cmd(sp, "execute at @p run summon zombie ~ ~ ~6 {NoAI:1b}", "execute at @p run summon cow ~1 ~ ~8 {NoAI:1b}",
					"execute at @p run summon skeleton ~ ~ ~-6 {NoAI:1b}");
			context.waitTicks(20);
			LookCounter.Counts look = context.computeOnClient(mc -> {
				mc.player.setYRot(0);
				mc.player.setXRot(0);
				return LookCounter.count(mc);
			});
			check("à frente: 1 hostil e 1 pacífico (esqueleto atrás fica fora)", look.hostile() == 1 && look.passive() == 1,
					look.hostile() + " hostis, " + look.passive() + " pacíficos");
			context.runOnClient(mc -> {
				mc.player.setYRot(0);
				mc.player.setXRot(25);
				SpawnInfo.refresh(mc);
			});
			context.waitTicks(5);
			context.takeScreenshot("03_superficie_mobs_a_frente");
			// Com efeitos de poção, o painel desce para não cobrir os ícones.
			cmd(sp, "effect give @p speed 60", "effect give @p weakness 60");
			context.waitTicks(5);
			context.takeScreenshot("03b_painel_abaixo_dos_efeitos");
			cmd(sp, "effect clear @p");

			// Tecla J esconde e mostra.
			context.getInput().pressKey(SpawnInfo.TOGGLE);
			context.waitTicks(3);
			boolean hidden = context.computeOnClient(mc -> !Config.get.visible);
			context.takeScreenshot("04_painel_escondido");
			context.getInput().pressKey(SpawnInfo.TOGGLE);
			context.waitTicks(3);
			boolean shown = context.computeOnClient(mc -> Config.get.visible);
			check("tecla J esconde e mostra", hidden && shown, "");

			// Tecla U abre os ajustes.
			context.getInput().pressKey(SpawnInfo.SETTINGS);
			context.waitTicks(5);
			boolean settings = context.computeOnClient(mc -> mc.gui.screen() instanceof SettingsScreen);
			check("tecla U abre os ajustes", settings, "");
			context.takeScreenshot("05_ajustes");
			context.setScreen(() -> null);

			// Seed digitada: vale para este mundo e some ao limpar.
			context.runOnClient(mc -> SpawnInfo.setSeedText(mc, "123"));
			long typed = context.computeOnClient(mc -> SpawnInfo.seed(mc).orElse(0));
			context.runOnClient(mc -> SpawnInfo.setSeedText(mc, ""));
			long back = context.computeOnClient(mc -> SpawnInfo.seed(mc).orElse(0));
			check("seed digitada e limpa", typed == 123 && back == seed, typed + " / " + back);
		}

		multiplayer(context, seed);
		log(failures == 0 ? "RESULTADO: tudo OK" : "RESULTADO: " + failures + " FALHA(S)");
		reportScreen(context);
	}

	/** Cava uma sala 16x4x16 escura (teto de pedra) em y 10 no chunk, com túnel até o jogador. Volta o bloco do meio. */
	private BlockPos room(ClientGameTestContext context, TestSingleplayerContext sp, int[] chunk, boolean again) {
		int x1 = chunk[0] * 16, z1 = chunk[1] * 16, x2 = x1 + 15, z2 = z1 + 15;
		cmd(sp, String.format(Locale.ROOT, "fill %d 9 %d %d 9 %d stone", x1, z1, x2, z2));
		cmd(sp, String.format(Locale.ROOT, "fill %d 10 %d %d 13 %d air", x1, z1, x2, z2));
		cmd(sp, String.format(Locale.ROOT, "fill %d 14 %d %d 14 %d stone", x1, z1, x2, z2));
		// Túnel até o jogador (fechado, sem luz do céu), a 28 blocos do meio da sala.
		cmd(sp, String.format(Locale.ROOT, "fill %d 9 %d %d 9 %d stone", x1 + 7, z2 + 1, x1 + 9, z1 + 37));
		cmd(sp, String.format(Locale.ROOT, "fill %d 10 %d %d 12 %d air", x1 + 7, z2 + 1, x1 + 9, z1 + 37));
		cmd(sp, String.format(Locale.ROOT, "fill %d 13 %d %d 13 %d stone", x1 + 7, z2 + 1, x1 + 9, z1 + 37));
		cmd(sp, "kill @e[type=!player]");
		BlockPos center = new BlockPos(x1 + 8, 10, z1 + 8);
		cmd(sp, String.format(Locale.ROOT, "tp @p %.1f 10 %.1f 180 4", x1 + 8.5, z1 + 36.5));
		context.waitTicks(again ? 60 : 80);
		context.runOnClient(mc -> {
			// Olha para o chão no meio da sala.
			var p = mc.player;
			double dx = center.getX() + 0.5 - p.getX(), dz = center.getZ() + 0.5 - p.getZ();
			double dy = center.getY() - p.getEyeY();
			p.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
			p.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
			SpawnInfo.refresh(mc);
		});
		context.waitTicks(5);
		BlockPos looked = context.computeOnClient(mc -> SpawnInfo.result == null ? null : SpawnInfo.result.pos);
		check("bloco olhado é o meio da sala", center.equals(looked), String.valueOf(looked));
		return center;
	}

	private double rowChance(ClientGameTestContext context, BlockPos pos, MobCategory cat, String id) {
		return context.computeOnClient(mc -> {
			SpawnCalc.Result r = SpawnInfo.compute(mc, pos, false);
			SpawnCalc.Group g = cat == MobCategory.MONSTER ? r.hostile : cat == MobCategory.CREATURE ? r.passive : r.ambient;
			for (SpawnCalc.Row row : g.rows) if (SpawnCalc.id(row.type()).equals(id)) return row.chance();
			return 0.0;
		});
	}

	private void compare(ClientGameTestContext context, TestSingleplayerContext sp, BlockPos pos, String name, boolean ambient) {
		compareCategory(context, sp, pos, MobCategory.MONSTER, name + " (hostis)");
		if (ambient) compareCategory(context, sp, pos, MobCategory.AMBIENT, name + " (morcegos)");
	}

	/** Chance do mod (cliente) x regras de verdade do servidor sorteadas TRIES vezes. */
	private void compareCategory(ClientGameTestContext context, TestSingleplayerContext sp, BlockPos pos, MobCategory cat, String name) {
		Map<String, Double> mod = context.computeOnClient(mc -> {
			SpawnCalc.Result r = SpawnInfo.compute(mc, pos, false);
			SpawnCalc.Group g = cat == MobCategory.MONSTER ? r.hostile : cat == MobCategory.CREATURE ? r.passive : r.ambient;
			Map<String, Double> m = new HashMap<>();
			for (SpawnCalc.Row row : g.rows) m.merge(SpawnCalc.id(row.type()), row.chance(), Double::sum);
			return m;
		});
		Map<String, Double> real = sp.getServer().computeOnServer(s -> empirical(s, pos, cat));
		double worst = 0;
		StringBuilder sb = new StringBuilder();
		for (String id : new TreeSet<>(union(mod, real))) {
			double a = mod.getOrDefault(id, 0.0), b = real.getOrDefault(id, 0.0);
			worst = Math.max(worst, Math.abs(a - b));
			if (a > 0 || b > 0) sb.append(String.format(Locale.ROOT, "%s %.2f/%.2f%% ", id, a * 100, b * 100));
		}
		check(name + ": mod x servidor (pior diferença " + String.format(Locale.ROOT, "%.2f", worst * 100) + " pontos)", worst < 0.012,
				sb.length() == 0 ? "nada nasce nos dois" : sb.toString().strip());
	}

	private static java.util.Set<String> union(Map<String, Double> a, Map<String, Double> b) {
		java.util.Set<String> s = new java.util.HashSet<>(a.keySet());
		s.addAll(b.keySet());
		return s;
	}

	/** As regras do próprio jogo (as mesmas do NaturalSpawner), sorteadas muitas vezes no bloco. */
	private static Map<String, Double> empirical(MinecraftServer s, BlockPos pos, MobCategory cat) {
		ServerLevel level = s.overworld();
		var list = level.getBiome(pos).value().getMobSettings().getMobs(cat);
		RandomSource random = RandomSource.create(42L);
		Map<String, Integer> hits = new HashMap<>();
		for (int i = 0; i < TRIES; i++) {
			Optional<MobSpawnSettings.SpawnerData> data = list.getRandom(random);
			if (data.isEmpty()) continue;
			EntityType<?> type = data.get().type();
			if (!SpawnPlacements.isSpawnPositionOk(type, level, pos)) continue;
			if (!SpawnPlacements.checkSpawnRules(type, level, EntitySpawnReason.NATURAL, pos, random)) continue;
			if (!level.noCollision(type.getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5))) continue;
			hits.merge(SpawnCalc.id(type), 1, Integer::sum);
		}
		Map<String, Double> out = new HashMap<>();
		hits.forEach((k, v) -> out.put(k, v / (double) TRIES));
		return out;
	}

	private void multiplayer(ClientGameTestContext context, long seed) {
		try (TestDedicatedServerContext server = context.worldBuilder().setUseConsistentSettings(false)
				.adjustSettings(s -> s.setSeed(SEED)).createServer();
				TestDedicatedServerConnection conn = server.connect()) {
			conn.waitForChunksRender();
			context.waitTicks(20);
			boolean noSeed = context.computeOnClient(mc -> SpawnInfo.seed(mc).isEmpty());
			context.waitFor(mc -> !BiomeSpawns.loading(), 1200);
			context.runOnClient(mc -> {
				Config.get.target = Config.Target.FEET;
				SpawnInfo.refresh(mc);
			});
			context.waitTicks(10);
			SpawnCalc.Result before = context.computeOnClient(mc -> SpawnInfo.result);
			context.takeScreenshot("10_multiplayer_sem_seed");
			context.runOnClient(mc -> {
				SpawnInfo.setSeedText(mc, SEED);
				SpawnInfo.refresh(mc);
			});
			context.waitTicks(10);
			SpawnCalc.Result after = context.computeOnClient(mc -> SpawnInfo.result);
			context.takeScreenshot("11_multiplayer_com_seed");
			check("multiplayer: sem seed não sabe o slime chunk", noSeed && before != null && before.slimeChunk == null, "");
			check("multiplayer: com a seed sabe o slime chunk", after != null && after.slimeChunk != null, "");
			check("multiplayer: lista de mobs do bioma carregada", after != null && !after.hostile.rows.isEmpty() && !after.loading,
					after == null ? "" : after.hostile.rows.size() + " mobs");
			context.runOnClient(mc -> {
				SpawnInfo.setSeedText(mc, "");
				Config.get.target = Config.Target.LOOK;
			});
		}
	}

	private void reportScreen(ClientGameTestContext context) {
		List<String> lines = List.copyOf(report);
		context.setScreen(() -> new Screen(Component.literal("Spawn Info: teste")) {
			@Override
			public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
				g.fill(0, 0, width, height, 0xFF101820);
				int longest = 1;
				for (String l : lines) longest = Math.max(longest, font.width(l));
				float k = Math.min(1f, (width - 8f) / longest);
				g.pose().pushMatrix();
				g.pose().scale(k, k);
				for (int i = 0; i < lines.size(); i++) {
					String l = lines.get(i);
					g.text(font, l, 4, 4 + i * 10, l.startsWith("FALHA") ? 0xFFFF6B6B : 0xFFFFFFFF, false);
				}
				g.pose().popMatrix();
			}
		});
		context.waitTicks(5);
		context.takeScreenshot("99_resultado_dos_testes");
		context.setScreen(() -> null);
	}

	@SuppressWarnings("unused")
	private static Minecraft mc() {
		return Minecraft.getInstance();
	}
}
