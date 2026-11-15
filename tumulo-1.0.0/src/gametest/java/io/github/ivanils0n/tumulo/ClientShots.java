package io.github.ivanils0n.tumulo;

import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Só no CI (runClientGameTest com xvfb). Abre o jogo de verdade em singleplayer, morre com armadura e itens, renasce e
 * tira prints do corpo (de lado e de cima) e da tela aberta com o botão direito de verdade.
 */
public class ClientShots implements FabricClientGameTest {
	private static double X, Y, Z;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			context.waitTicks(100);
			cmd(sp, "time set 6000");
			cmd(sp, "weather clear");
			sp.getServer().runOnServer(s -> {
				ServerPlayer p = player(s);
				X = Math.floor(p.getX()) + 0.5;
				Y = p.getY();
				Z = Math.floor(p.getZ()) + 0.5;
			});
			cmd(sp, String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", (int) X - 5, (int) Y, (int) Z - 5, (int) X + 5, (int) Y + 4, (int) Z + 5));
			cmd(sp, String.format(Locale.ROOT, "tp @p %.2f %.2f %.2f 0 0", X, Y, Z));
			context.waitTicks(10);
			sp.getServer().runOnServer(s -> {
				ServerPlayer p = player(s);
				p.getInventory().setItem(0, new ItemStack(Items.DIAMOND_SWORD));
				p.getInventory().setItem(3, new ItemStack(Items.TORCH, 32));
				p.getInventory().setItem(15, new ItemStack(Items.GOLD_INGOT, 9));
				p.getInventory().setItem(39, new ItemStack(Items.IRON_HELMET));
				p.getInventory().setItem(38, new ItemStack(Items.DIAMOND_CHESTPLATE));
				p.getInventory().setItem(37, new ItemStack(Items.IRON_LEGGINGS));
				p.getInventory().setItem(36, new ItemStack(Items.GOLDEN_BOOTS));
				p.getInventory().setItem(40, new ItemStack(Items.SHIELD));
			});
			// Parede a oeste: a cabeça iria para dentro dela, então o corpo deita para o norte.
			cmd(sp, String.format(Locale.ROOT, "fill %d %d %d %d %d %d stone", (int) Math.floor(X) - 1, (int) Y, (int) Math.floor(Z) - 1, (int) Math.floor(X) - 1, (int) Y + 1, (int) Math.floor(Z) + 1));
			context.waitTicks(5);
			cmd(sp, "kill @p");
			context.waitTicks(30);
			context.takeScreenshot("0_morte");
			context.runOnClient(c -> c.player.respawn());
			context.waitTicks(20);
			context.setScreen(() -> null);
			context.waitTicks(10);
			int graves = sp.getServer().computeOnServer(s -> Tumulo.store.all().size());
			if (graves != 1) throw new AssertionError("túmulos depois de morrer: " + graves);

			// De lado (a 3 blocos, olhando para o corpo) e de cima.
			cmd(sp, String.format(Locale.ROOT, "tp @p %.2f %.2f %.2f 90 25", X + 3, Y, Z));
			context.waitTicks(40);
			context.takeScreenshot("1_corpo_de_lado");
			cmd(sp, String.format(Locale.ROOT, "tp @p %.2f %.2f %.2f 0 25", X, Y, Z - 4));
			context.waitTicks(20);
			context.takeScreenshot("2_corpo_de_frente");
			cmd(sp, String.format(Locale.ROOT, "tp @p %.2f %.2f %.2f 0 90", X, Y + 5, Z - 0.9));
			context.waitTicks(20);
			context.takeScreenshot("3_corpo_de_cima");

			// Botão direito de verdade no corpo: abre a tela do túmulo.
			cmd(sp, String.format(Locale.ROOT, "tp @p %.2f %.2f %.2f 90 50", X + 1.5, Y, Z - 0.9));
			context.waitTicks(10);
			context.getInput().holdKey(o -> o.keyUse);
			context.waitTicks(2);
			context.getInput().releaseKey(o -> o.keyUse);
			context.waitTicks(10);
			boolean screen = context.computeOnClient(c -> c.player.containerMenu instanceof ChestMenu);
			String menu = sp.getServer().computeOnServer(s -> player(s).containerMenu.getClass().getSimpleName());
			context.takeScreenshot("4_tela_do_tumulo");
			if (!screen || !menu.equals("GraveMenu")) throw new AssertionError("a tela do túmulo não abriu (tela " + screen + ", menu " + menu + ")");
			context.runOnClient(c -> c.player.closeContainer());
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
