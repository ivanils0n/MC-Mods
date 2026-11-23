package io.github.ivanils0n.interfacedinamica;

import io.github.ivanils0n.interfacedinamica.client.Effects;
import io.github.ivanils0n.interfacedinamica.client.mixin.ContainerScreenAccess;
import io.github.ivanils0n.interfacedinamica.server.MenuPanel;
import io.github.ivanils0n.interfacedinamica.server.PanelManager;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Só no CI (runClientGameTest com xvfb): abre o jogo de verdade e tira prints do próprio jogador com baú, fornalha,
 * mesa e inventário abertos, e de "outro jogador" (um manequim) com a tela flutuando ao lado.
 */
public class ClientShots implements FabricClientGameTest {
	private static int X, Y, Z;
	private static final List<String> NOTES = new ArrayList<>();
	private static MenuPanel DEMO;

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
			cmd(sp, String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", X - 8, Y, Z - 8, X + 8, Y + 5, Z + 12));
			// um cenário simples: algumas árvores e flores para dar profundidade
			for (int[] t : new int[][]{{-5, 7}, {5, 8}, {-6, -3}, {6, -4}}) {
				cmd(sp, String.format(Locale.ROOT, "fill %d %d %d %d %d %d oak_log", X + t[0], Y, Z + t[1], X + t[0], Y + 3, Z + t[1]));
				cmd(sp, String.format(Locale.ROOT, "fill %d %d %d %d %d %d oak_leaves", X + t[0] - 2, Y + 3, Z + t[1] - 2, X + t[0] + 2, Y + 5, Z + t[1] + 2));
			}
			cmd(sp, String.format(Locale.ROOT, "fill %d %d %d %d %d %d poppy", X - 3, Y, Z + 4, X - 3, Y, Z + 4));
			cmd(sp, String.format(Locale.ROOT, "fill %d %d %d %d %d %d dandelion", X + 3, Y, Z + 5, X + 3, Y, Z + 5));
			cmd(sp, "gamemode survival @p");
			cmd(sp, String.format(Locale.ROOT, "tp @p %d.5 %d %d.5 0 20", X, Y, Z));
			cmd(sp, "clear @p");
			for (String g : new String[]{"diamond_sword", "diamond_pickaxe", "torch 24", "bread 12", "oak_log 16", "cobblestone 64", "apple 5", "oak_planks 48"}) {
				cmd(sp, "give @p minecraft:" + g);
			}
			context.waitTicks(20);

			// 1) baú
			BlockPos chest = new BlockPos(X - 1, Y, Z + 1);
			cmd(sp, String.format(Locale.ROOT, "setblock %d %d %d chest[facing=north]{Items:[" +
					"{Slot:0b,id:\"minecraft:diamond\",count:7},{Slot:2b,id:\"minecraft:iron_ingot\",count:32},{Slot:3b,id:\"minecraft:gold_ingot\",count:18}," +
					"{Slot:10b,id:\"minecraft:coal\",count:64},{Slot:14b,id:\"minecraft:bread\",count:20},{Slot:16b,id:\"minecraft:oak_log\",count:64}," +
					"{Slot:21b,id:\"minecraft:torch\",count:40},{Slot:26b,id:\"minecraft:apple\",count:9},{Slot:6b,id:\"minecraft:emerald\",count:11}]}",
					chest.getX(), chest.getY(), chest.getZ()));
			open(context, sp, chest);
			context.waitTicks(20);
			context.takeScreenshot("1_bau");
			note("painéis no servidor com o baú aberto: {}", PanelManager.count());
			// item voando (como ao clicar num espaço)
			context.runOnClient(c -> {
				if (c.gui.screen() instanceof AbstractContainerScreen<?> cs) {
					ContainerScreenAccess a = (ContainerScreenAccess) cs;
					Slot s = cs.getMenu().slots.get(0);
					Effects.startFlight(s.getItem(), a.interfacedinamica$leftPos() + s.x, a.interfacedinamica$topPos() + s.y);
				}
			});
			context.waitTicks(4);
			context.takeScreenshot("1b_bau_item_voando");
			// meio da animação (câmera girando)
			close(context, sp);
			context.waitTicks(20);
			open(context, sp, chest);
			context.waitTicks(5);
			context.takeScreenshot("1c_bau_animacao_meio");
			context.waitTicks(15);
			close(context, sp);
			context.waitTicks(20);

			// 2) fornalha (acesa)
			BlockPos furnace = new BlockPos(X - 1, Y, Z + 1);
			cmd(sp, String.format(Locale.ROOT, "setblock %d %d %d furnace[facing=north]{Items:[" +
					"{Slot:0b,id:\"minecraft:raw_iron\",count:12},{Slot:1b,id:\"minecraft:coal\",count:9},{Slot:2b,id:\"minecraft:iron_ingot\",count:4}]}",
					furnace.getX(), furnace.getY(), furnace.getZ()));
			context.waitTicks(60);
			open(context, sp, furnace);
			context.waitTicks(25);
			context.takeScreenshot("2_fornalha");
			close(context, sp);
			context.waitTicks(20);

			// 3) mesa de trabalho
			BlockPos table = new BlockPos(X - 1, Y, Z + 1);
			cmd(sp, String.format(Locale.ROOT, "setblock %d %d %d crafting_table", table.getX(), table.getY(), table.getZ()));
			open(context, sp, table);
			context.waitTicks(22);
			context.takeScreenshot("3_mesa");
			close(context, sp);
			context.waitTicks(20);

			// 4) inventário
			context.runOnClient(c -> c.gui.setScreen(new InventoryScreen(c.player)));
			context.waitTicks(22);
			context.takeScreenshot("4_inventario");
			context.runOnClient(c -> c.gui.setScreen(null));
			context.waitTicks(20);

			// 5) noite / caverna: a tela fica mais escura (luz do ambiente)
			cmd(sp, "time set 18000");
			cmd(sp, String.format(Locale.ROOT, "setblock %d %d %d chest[facing=north]{Items:[{Slot:4b,id:\"minecraft:diamond\",count:3}]}", chest.getX(), chest.getY(), chest.getZ()));
			context.waitTicks(10);
			open(context, sp, chest);
			context.waitTicks(22);
			context.takeScreenshot("5_bau_noite");
			close(context, sp);
			cmd(sp, "time set 6000");
			context.waitTicks(20);

			// 6) outro jogador vendo: manequim com a tela do baú ao lado
			cmd(sp, String.format(Locale.ROOT, "setblock %d %d %d air", X - 1, Y, Z + 1));
			cmd(sp, String.format(Locale.ROOT, "summon minecraft:mannequin %d.5 %d %d.5 {Rotation:[180f,0f],immovable:1b,Tags:[\"interface_teste\"]}", X, Y, Z + 5));
			cmd(sp, String.format(Locale.ROOT, "setblock %d %d %d chest[facing=south]", X + 1, Y, Z + 4));
			context.waitTicks(10);
			sp.getServer().runOnServer(s -> {
				ServerPlayer p = player(s);
				Entity m = null;
				for (Entity e : p.level().getAllEntities()) if (e.entityTags().contains("interface_teste")) m = e;
				if (m == null) {
					note("manequim não encontrado");
					return;
				}
				SimpleContainer box = new SimpleContainer(27);
				box.setItem(0, new ItemStack(Items.DIAMOND, 7));
				box.setItem(2, new ItemStack(Items.IRON_INGOT, 32));
				box.setItem(3, new ItemStack(Items.GOLD_INGOT, 18));
				box.setItem(10, new ItemStack(Items.COAL, 64));
				box.setItem(14, new ItemStack(Items.BREAD, 20));
				box.setItem(16, new ItemStack(Items.OAK_LOG, 64));
				box.setItem(21, new ItemStack(Items.TORCH, 40));
				box.setItem(26, new ItemStack(Items.APPLE, 9));
				ChestMenu menu = ChestMenu.threeRows(900, p.getInventory(), box);
				DEMO = MenuPanel.create(m, null, menu, Component.literal("Baú"));
				note("painel de demonstração: {} entidades", DEMO.elementCount());
			});
			context.waitTicks(10);
			view(context, sp, X + 2.2, Z + 1.6, X - 0.4, Y + 1.3, Z + 5.0, "6_outro_jogador_frente");
			view(context, sp, X - 3.2, Z + 2.0, X - 0.6, Y + 1.3, Z + 5.0, "6b_outro_jogador_lado");
			view(context, sp, X + 0.5, Z - 1.0, X - 0.4, Y + 1.3, Z + 5.0, "6c_outro_jogador_longe");
			sp.getServer().runOnServer(s -> {
				if (DEMO != null) DEMO.destroy();
			});
		}
		for (String n : NOTES) InterfaceDinamica.LOG.info("RESUMO {}", n);
	}

	private static void open(ClientGameTestContext context, TestSingleplayerContext sp, BlockPos pos) {
		cmd(sp, String.format(Locale.ROOT, "tp @p %d.5 %d %d.5 0 20", X, Y, Z));
		context.waitTicks(3);
		sp.getServer().runOnServer(s -> {
			ServerPlayer p = player(s);
			MenuProvider provider = p.level().getBlockState(pos).getMenuProvider(p.level(), pos);
			if (provider == null && p.level().getBlockEntity(pos) instanceof MenuProvider mp) provider = mp;
			if (provider == null) note("sem tela em {}", pos);
			else p.openMenu(provider);
		});
	}

	private static void close(ClientGameTestContext context, TestSingleplayerContext sp) {
		sp.getServer().runOnServer(s -> player(s).closeContainer());
		context.runOnClient(c -> {
			if (c.player != null) c.player.closeContainer();
		});
	}

	/** O jogador (câmera) vai para (cx, cz) e olha para o ponto (tx, ty, tz). */
	private static void view(ClientGameTestContext context, TestSingleplayerContext sp, double cx, double cz, double tx, double ty, double tz, String name) {
		double eye = Y + 1.62;
		double dx = tx - cx, dz = tz - cz;
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) Math.toDegrees(Math.atan2(eye - ty, Math.hypot(dx, dz)));
		cmd(sp, String.format(Locale.ROOT, "tp @p %.2f %d %.2f %.1f %.1f", cx, Y, cz, yaw, pitch));
		context.waitTicks(12);
		context.takeScreenshot(name);
	}

	private static void note(String fmt, Object... args) {
		String msg = fmt;
		for (Object a : args) msg = msg.replaceFirst("\\{\\}", java.util.regex.Matcher.quoteReplacement(String.valueOf(a)));
		NOTES.add(msg);
		InterfaceDinamica.LOG.info(msg);
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().get(0);
	}

	private static void cmd(TestSingleplayerContext sp, String c) {
		sp.getServer().runCommand(c);
	}
}
