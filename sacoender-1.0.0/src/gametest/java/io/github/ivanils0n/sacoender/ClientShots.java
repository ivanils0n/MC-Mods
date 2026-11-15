package io.github.ivanils0n.sacoender;

import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Só no CI (runClientGameTest com xvfb). Abre o jogo de verdade em singleplayer e tira prints do saco na mão (1ª e
 * 3ª pessoa), no chão, no inventário e da tela aberta com o botão direito de verdade (caminho cliente -> servidor).
 */
public class ClientShots implements FabricClientGameTest {
	private static double X, Y, Z;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			context.waitTicks(100);
			cmd(sp, "time set 6000");
			cmd(sp, "weather clear");
			cmd(sp, "gamerule doDaylightCycle false");
			sp.getServer().runOnServer(s -> {
				ServerPlayer p = player(s);
				X = Math.floor(p.getX()) + 0.5;
				Y = p.getY();
				Z = Math.floor(p.getZ()) + 0.5;
			});
			cmd(sp, String.format(Locale.ROOT, "tp @p %.2f %.2f %.2f 0 20", X, Y, Z));
			cmd(sp, String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", (int) X - 4, (int) Y, (int) Z - 4, (int) X + 4, (int) Y + 3, (int) Z + 6));
			sp.getServer().runOnServer(s -> {
				ServerPlayer p = player(s);
				p.getInventory().setItem(0, SacoEnder.bagItem());
				p.getEnderChestInventory().setItem(0, new ItemStack(Items.DIAMOND, 12));
				p.getEnderChestInventory().setItem(4, new ItemStack(Items.ENDER_PEARL, 16));
				p.getEnderChestInventory().setItem(9, new ItemStack(Items.GOLD_INGOT, 5)); // fora do saco
				ItemEntity drop = new ItemEntity(p.level(), X + 0.8, Y, Z + 2.2, SacoEnder.bagItem());
				drop.setNeverPickUp();
				drop.setDeltaMovement(0, 0, 0);
				p.level().addFreshEntity(drop);
			});
			context.waitTicks(40);
			context.takeScreenshot("1_primeira_pessoa");
			context.runOnClient(c -> c.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(10);
			context.takeScreenshot("2_terceira_pessoa");
			context.runOnClient(c -> c.options.setCameraType(CameraType.FIRST_PERSON));
			context.waitTicks(5);

			// Botão direito de verdade com o saco na mão: abre a tela de 1 fileira com o baú do fim.
			context.getInput().holdKey(o -> o.keyUse);
			context.waitTicks(2);
			context.getInput().releaseKey(o -> o.keyUse);
			context.waitTicks(10);
			boolean screen = context.computeOnClient(c -> c.screen instanceof ContainerScreen);
			String menu = sp.getServer().computeOnServer(s -> player(s).containerMenu.getClass().getSimpleName());
			context.takeScreenshot("3_tela_do_saco");
			if (!screen || !menu.equals("BagMenu")) throw new AssertionError("a tela do saco não abriu (tela " + screen + ", menu " + menu + ")");

			// Item posto no baú do fim com a tela aberta aparece no saco.
			sp.getServer().runOnServer(s -> player(s).getEnderChestInventory().setItem(8, new ItemStack(Items.EMERALD, 7)));
			context.waitTicks(5);
			context.takeScreenshot("4_tela_atualizada");
			context.runOnClient(c -> c.player.closeContainer());
			context.waitTicks(5);

			context.runOnClient(c -> c.setScreen(new InventoryScreen(c.player)));
			context.waitTicks(5);
			context.takeScreenshot("5_inventario");
			context.runOnClient(c -> c.setScreen(null));
			context.waitTicks(5);
		}
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().get(0);
	}

	private static void cmd(TestSingleplayerContext sp, String c) {
		sp.getServer().runCommand(c);
	}
}
