package io.github.ivanils0n.tpaltar;

import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

/** TEMPORÁRIO: mundo de vitrine para prints no CI. */
final class Vitrine {
	static void run(MinecraftServer server, String mode) {
		ServerLevel sl = server.overworld();
		if (mode.equals("vitrine")) {
			TpAltar.setPearls(TpAltar.placeAltar(sl, ground(sl, -2, 6), "Base"), 64);
			TpAltar.setPearls(TpAltar.placeAltar(sl, ground(sl, 2, 6), "Mina"), 48);
			TpAltar.setPearls(TpAltar.placeAltar(sl, ground(sl, 9, 9), "Vila"), 1);
			TpAltar.LOG.info("TPALTAR VITRINE OK");
			server.halt(false);
			return;
		}
		int[] join = {-1};
		ServerPlayConnectionEvents.JOIN.register((h, sender, s) -> {
			ServerPlayer p = h.player;
			p.teleportTo(sl, 0.5, ground(sl, 0, 0).getY(), 0.5, Set.<Relative>of(), 0f, -8f, false);
			p.getInventory().setItem(0, TpAltar.altarItem());
			p.getInventory().setItem(1, new ItemStack(Items.ENDER_PEARL, 16));
			p.giveExperienceLevels(10);
			join[0] = s.getTickCount();
		});
		ServerTickEvents.END_SERVER_TICK.register(s -> {
			if (join[0] < 0 || s.getPlayerList().getPlayers().isEmpty()) return;
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			int t = s.getTickCount() - join[0];
			if (t == 55 * 20) TpAltar.openForTest(p, sl, ground(sl, 2, 6).below());
			if (t == 75 * 20) {
				p.closeContainer();
				NameMenu.open(p, new TpAltar.Pending(sl.dimension(), ground(sl, 0, 3), InteractionHand.MAIN_HAND), "Nome do altar");
			}
		});
	}

	private static BlockPos ground(ServerLevel sl, int x, int z) {
		sl.getChunk(x >> 4, z >> 4);
		return new BlockPos(x, sl.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
	}
}
