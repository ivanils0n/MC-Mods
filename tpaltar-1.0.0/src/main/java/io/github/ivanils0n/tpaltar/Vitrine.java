package io.github.ivanils0n.tpaltar;

import java.util.Set;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

/** TEMPORÁRIO: prints no CI. */
final class Vitrine {
	static void run(MinecraftServer server, String mode) {
		ServerLevel sl = server.overworld();
		if (mode.equals("vitrine")) {
			TpAltar.setPearls(TpAltar.placeAltar(sl, ground(sl, 2, 6), "Base"), 64);
			TpAltar.setPearls(TpAltar.placeAltar(sl, ground(sl, -2, 6), "Mina"), 64);
			TpAltar.LOG.info("TPALTAR VITRINE OK");
			server.halt(false);
			return;
		}
		ServerPlayConnectionEvents.JOIN.register((h, sender, s) -> {
			h.player.teleportTo(sl, 0.5, ground(sl, 0, 0).getY(), 0.5, Set.<Relative>of(), 0f, 0f, false);
			ItemStack old = TpAltar.altarItem();
			old.set(net.minecraft.core.component.DataComponents.ITEM_MODEL, Items.ENDER_EYE.components().get(net.minecraft.core.component.DataComponents.ITEM_MODEL));
			h.player.getInventory().setItem(0, old); // como sai da receita: modelo de olho do fim
			h.player.getInventory().setItem(1, new ItemStack(Items.ENDER_EYE)); // olho comum: mostra que o pack rival está ativo
		});
	}

	private static BlockPos ground(ServerLevel sl, int x, int z) {
		sl.getChunk(x >> 4, z >> 4);
		return new BlockPos(x, sl.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
	}
}
