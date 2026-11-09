package io.github.ivanils0n.tpaltar;

import io.github.ivanils0n.tpaltar.AltarStore.Altar;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;

/**
 * Só no CI, com -Dtpaltar.selftest=...:
 * true: monta e desmonta um altar num servidor de verdade e desliga.
 * vitrine: cria um mundo com dois altares para fotografar e desliga.
 * cliente: no singleplayer desse mundo, põe o jogador de frente para os altares e deixa um deles "em uso".
 */
final class SelfTest {
	static final Set<String> FORCED_IN_USE = new HashSet<>();

	private SelfTest() {}

	static void run(MinecraftServer server, String mode) {
		if (mode.equals("vitrine")) {
			ServerLevel sl = server.overworld();
			TpAltar.setPearls(TpAltar.placeAltar(sl, ground(sl, -2, 6), "Base"), 64);
			TpAltar.setPearls(TpAltar.placeAltar(sl, ground(sl, 2, 6), "Mina"), 64);
			TpAltar.LOG.info("TPALTAR VITRINE OK");
			server.halt(false);
			return;
		}
		if (mode.equals("cliente")) {
			ServerLevel sl = server.overworld();
			// Na hora em que o jogador entra o terreno já está carregado (antes disso a altura vem errada).
			ServerPlayConnectionEvents.JOIN.register((h, sender, s) -> {
				FORCED_IN_USE.add(AltarStore.key(TpAltar.dim(sl), 2, ground(sl, 2, 6).getY() - 1, 6)); // o chão ali é o altar
				h.player.teleportTo(sl, 0.5, ground(sl, 0, 0).getY(), 0.5, Set.<Relative>of(), 0f, -8f, false);
			});
			return;
		}
		try {
			check(server);
			TpAltar.LOG.info("TPALTAR SELFTEST OK");
		} catch (Throwable t) {
			TpAltar.LOG.error("TPALTAR SELFTEST FALHOU", t);
		}
		server.halt(false);
	}

	private static void check(MinecraftServer server) {
		ensure(server.getRecipeManager().getRecipes().stream().anyMatch(r -> r.id().toString().contains("tpaltar:altar")), "receita não carregou");
		ensure(TpAltar.isAltarItem(TpAltar.altarItem()), "item do altar não é reconhecido");

		ServerLevel sl = server.overworld();
		BlockPos pos = new BlockPos(0, 200, 0);
		for (int dy = 0; dy < 3; dy++) sl.setBlock(pos.above(dy), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

		Altar altar = TpAltar.placeAltar(sl, pos, "Teste");
		ensure(altar != null && sl.getBlockState(pos).is(Blocks.OBSIDIAN), "base não foi colocada");
		AABB area = new AABB(pos).inflate(1, 4, 1);
		ensure(sl.getEntitiesOfClass(Display.class, area).size() == 3, "entidades de exibição: " + sl.getEntitiesOfClass(Display.class, area).size());
		ensure(sl.getEntitiesOfClass(Interaction.class, area).size() == 1, "entidade de interação não apareceu");
		Display.ItemDisplay eye = TpAltar.findEye(sl, altar);
		ensure(eye != null, "olho não encontrado");
		ensure(TpAltar.showEye(sl, altar, eye, true) && TpAltar.showEye(sl, altar, eye, false), "troca do olho falhou");
		ensure(!eye.shouldBeSaved(), "decoração seria salva no mapa");
		ensure(TpAltar.animateOnce(sl, altar), "animação falhou");
		ensure(TpAltar.PACK_SHA1 != null, "hash do resource pack não está no .jar");
		TpAltar.spawnVisuals(sl, pos); // recriar não pode duplicar
		ensure(sl.getEntitiesOfClass(Display.class, area).size() == 3, "recriar duplicou: " + sl.getEntitiesOfClass(Display.class, area).size());
		ensure(sl.getEntitiesOfClass(Interaction.class, area).size() == 1, "recriar duplicou a interação");

		ensure(TpAltar.safeSpot(sl, pos) == null, "achou lugar seguro no ar");
		sl.setBlock(pos.east().below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
		ensure(TpAltar.safeSpot(sl, pos) != null, "não achou lugar seguro com chão");

		AltarStore reloaded = AltarStore.load(server.getWorldPath(LevelResource.ROOT).resolve("tpaltar.json"));
		ensure(reloaded.get(TpAltar.dim(sl), pos) != null, "altar não foi salvo no arquivo");

		TpAltar.removeAltar(sl, altar);
		ensure(sl.getEntitiesOfClass(Display.class, area).isEmpty() && sl.getEntitiesOfClass(Interaction.class, area).isEmpty(), "entidades não foram removidas");
		sl.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		sl.setBlock(pos.east().below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
	}

	private static BlockPos ground(ServerLevel sl, int x, int z) {
		return new BlockPos(x, sl.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
	}

	private static void ensure(boolean ok, String what) {
		if (!ok) throw new IllegalStateException(what);
	}
}
