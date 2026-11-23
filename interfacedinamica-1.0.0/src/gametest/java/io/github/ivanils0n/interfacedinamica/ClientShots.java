package io.github.ivanils0n.interfacedinamica;

import io.github.ivanils0n.interfacedinamica.client.ClientMod;
import io.github.ivanils0n.interfacedinamica.client.Effects;
import io.github.ivanils0n.interfacedinamica.client.mixin.ContainerScreenAccess;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.Slot;

import java.util.Locale;

/**
 * Só no CI (runClientGameTest com xvfb): abre o jogo de verdade e tira prints da animação de cinema em cada bloco
 * (mesa, fornalha, bigorna, baú, encantamento, poções), no meio do movimento e parado, e no inventário.
 */
public class ClientShots implements FabricClientGameTest {
	private static int X, Y, Z;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			context.waitTicks(100);
			cmd(sp, "time set 6000");
			cmd(sp, "weather clear");
			sp.getServer().runOnServer(s -> {
				ServerPlayer p = player(s);
				X = (int) Math.floor(p.getX());
				Y = (int) Math.floor(p.getY());
				Z = (int) Math.floor(p.getZ());
			});
			cmd(sp, f("fill %d %d %d %d %d %d air", X - 8, Y, Z - 8, X + 8, Y + 5, Z + 12));
			for (int[] t : new int[][]{{-5, 7}, {5, 8}, {-6, -3}, {6, -4}}) {
				cmd(sp, f("fill %d %d %d %d %d %d oak_log", X + t[0], Y, Z + t[1], X + t[0], Y + 3, Z + t[1]));
				cmd(sp, f("fill %d %d %d %d %d %d oak_leaves", X + t[0] - 2, Y + 3, Z + t[1] - 2, X + t[0] + 2, Y + 5, Z + t[1] + 2));
			}
			cmd(sp, f("setblock %d %d %d poppy", X - 3, Y, Z + 4));
			cmd(sp, f("setblock %d %d %d dandelion", X + 3, Y, Z + 5));
			cmd(sp, "gamemode survival @p");
			cmd(sp, "clear @p");
			for (String g : new String[]{"iron_sword", "iron_pickaxe", "torch 24", "bread 12", "oak_log 16", "cobblestone 64", "apple 5", "oak_planks 48", "iron_ingot 9", "lapis_lazuli 12"}) {
				cmd(sp, "give @p minecraft:" + g);
			}
			cmd(sp, "experience add @p 30 levels");
			context.waitTicks(20);
			BlockPos b = new BlockPos(X, Y, Z + 2);

			// mesa de trabalho: meio do movimento e parado
			scene(context, sp, b, "crafting_table", null, new int[]{7, 30}, "1_mesa");
			// fornalha acesa
			scene(context, sp, b, "furnace[facing=north]", "{Items:[{Slot:0b,id:\"minecraft:raw_iron\",count:12},{Slot:1b,id:\"minecraft:coal\",count:9},{Slot:2b,id:\"minecraft:iron_ingot\",count:4}]}",
					new int[]{30}, "2_fornalha");
			// bigorna: duas fases da martelada
			scene(context, sp, b, "anvil[facing=east]", null, new int[]{28, 4, 4}, "3_bigorna");
			// baú
			scene(context, sp, b, "chest[facing=north]", "{Items:[{Slot:0b,id:\"minecraft:diamond\",count:7},{Slot:2b,id:\"minecraft:iron_ingot\",count:32},{Slot:3b,id:\"minecraft:gold_ingot\",count:18},{Slot:10b,id:\"minecraft:coal\",count:64},{Slot:14b,id:\"minecraft:bread\",count:20},{Slot:21b,id:\"minecraft:torch\",count:40}]}",
					new int[]{30}, "4_bau");
			// item pego voando até a mão
			context.runOnClient(c -> {
				if (c.gui.screen() instanceof AbstractContainerScreen<?> cs) {
					ContainerScreenAccess a = (ContainerScreenAccess) cs;
					Slot s = cs.getMenu().slots.get(0);
					Effects.startFlight(s.getItem(), a.interfacedinamica$leftPos() + s.x, a.interfacedinamica$topPos() + s.y);
				}
			});
			context.waitTicks(4);
			context.takeScreenshot("4b_bau_item_voando");
			close(context, sp);
			context.waitTicks(30);
			// mesa de encantamento
			scene(context, sp, b, "enchanting_table", null, new int[]{30}, "5_encantamento");
			// suporte de poções
			scene(context, sp, b, "brewing_stand", null, new int[]{30}, "6_pocoes");
			// inventário (sem bloco: câmera de frente)
			cmd(sp, f("setblock %d %d %d air", b.getX(), b.getY(), b.getZ()));
			cmd(sp, f("tp @p %d.5 %d %d.5 -30 5", X, Y, Z));
			context.waitTicks(20);
			context.runOnClient(c -> c.gui.setScreen(new InventoryScreen(c.player)));
			context.waitTicks(6);
			context.takeScreenshot("7_inventario_a");
			context.waitTicks(30);
			context.takeScreenshot("7_inventario_b");
			context.runOnClient(c -> c.gui.setScreen(null));
			context.waitTicks(30);
			// inventário do criativo (como a imagem de referência)
			cmd(sp, "gamemode creative @p");
			context.waitTicks(10);
			context.runOnClient(c -> c.gui.setScreen(new InventoryScreen(c.player)));
			context.waitTicks(36);
			context.takeScreenshot("7c_inventario_criativo");
			context.runOnClient(c -> c.gui.setScreen(null));
			cmd(sp, "gamemode survival @p");
			context.waitTicks(30);
			// noite, na mesa
			cmd(sp, "time set 18000");
			cmd(sp, f("setblock %d %d %d torch", X + 1, Y, Z + 2));
			scene(context, sp, b, "crafting_table", null, new int[]{30}, "8_mesa_noite");
			cmd(sp, f("setblock %d %d %d air", X + 1, Y, Z + 2));
			cmd(sp, "time set 6000");
			// mesa encostada numa parede: a câmera tem que achar espaço
			cmd(sp, f("fill %d %d %d %d %d %d stone_bricks", X - 3, Y, Z + 3, X + 3, Y + 3, Z + 3));
			cmd(sp, f("fill %d %d %d %d %d %d stone_bricks", X + 2, Y, Z - 1, X + 2, Y + 3, Z + 3));
			scene(context, sp, b, "crafting_table", null, new int[]{30}, "9_mesa_no_canto");
		}
		ClientMod.LOG.info("RESUMO prints terminados");
	}

	/** Põe o bloco 2 à frente, olha para ele, abre a tela e tira os prints depois de cada espera. */
	private static void scene(ClientGameTestContext context, TestSingleplayerContext sp, BlockPos pos, String block, String nbt, int[] waits, String name) {
		cmd(sp, f("setblock %d %d %d air", pos.getX(), pos.getY(), pos.getZ()));
		cmd(sp, f("setblock %d %d %d %s%s", pos.getX(), pos.getY(), pos.getZ(), block, nbt == null ? "" : nbt));
		cmd(sp, f("tp @p %d.5 %d %d.5 0 30", X, Y, Z));
		context.waitTicks(40);
		sp.getServer().runOnServer(s -> {
			ServerPlayer p = player(s);
			MenuProvider provider = p.level().getBlockState(pos).getMenuProvider(p.level(), pos);
			if (provider == null && p.level().getBlockEntity(pos) instanceof MenuProvider mp) provider = mp;
			if (provider == null) ClientMod.LOG.info("RESUMO sem tela em {}", pos);
			else p.openMenu(provider);
		});
		for (int i = 0; i < waits.length; i++) {
			context.waitTicks(waits[i]);
			context.takeScreenshot(name + (waits.length > 1 ? "_" + (char) ('a' + i) : ""));
		}
		if (!name.startsWith("4_")) {
			close(context, sp);
			context.waitTicks(30);
		}
	}

	private static void close(ClientGameTestContext context, TestSingleplayerContext sp) {
		sp.getServer().runOnServer(s -> player(s).closeContainer());
		context.runOnClient(c -> {
			if (c.player != null) c.player.closeContainer();
		});
	}

	private static String f(String fmt, Object... a) {
		return String.format(Locale.ROOT, fmt, a);
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().get(0);
	}

	private static void cmd(TestSingleplayerContext sp, String c) {
		sp.getServer().runCommand(c);
	}
}
