package io.github.ivanils0n.tpaltar;

import io.github.ivanils0n.tpaltar.AltarStore.Altar;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;

/** Roda só com -Dtpaltar.selftest=true (CI): monta e desmonta um altar num servidor de verdade e desliga. */
final class SelfTest {
	private SelfTest() {}

	static void run(MinecraftServer server) {
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
		ensure(!TpAltar.ownModels(), "servidor dedicado deveria usar os itens vanilla (o pack do servidor já fica por cima)");
		Component title = TpAltar.themedTitle("\uF000\uE000\uF001\uF002", "Teste");
		ensure(title.getString().endsWith("Teste") && title.getSiblings().size() == 2, "título com fundo: " + title);

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
		ensure(TpAltar.showEye(eye, true) && TpAltar.eyeInUse(eye) && TpAltar.showEye(eye, false) && !TpAltar.eyeInUse(eye), "troca do olho falhou");
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

	private static void ensure(boolean ok, String what) {
		if (!ok) throw new IllegalStateException(what);
	}
}
