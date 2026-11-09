package io.github.ivanils0n.tpaltar;

import java.util.Set;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.levelgen.Heightmap;

/** TEMPORÁRIO: prints no CI. */
final class Vitrine {
	static void run(MinecraftServer server, String mode) {
		ServerLevel sl = server.overworld();
		if (mode.equals("vitrine")) {
			TpAltar.LOG.info("TPALTAR VITRINE OK");
			server.halt(false);
			return;
		}
		ServerPlayConnectionEvents.JOIN.register((h, sender, s) -> {
			h.player.teleportTo(sl, 0.5, ground(sl, 0, 0).getY(), 0.5, Set.<Relative>of(), 0f, 10f, false);
			h.player.getInventory().setItem(0, TpAltar.altarItem()); // igual aos altares antigos: sem custom_model_data
		});
	}

	private static BlockPos ground(ServerLevel sl, int x, int z) {
		sl.getChunk(x >> 4, z >> 4);
		return new BlockPos(x, sl.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
	}
}
