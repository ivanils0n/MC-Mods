package io.github.ivanils0n.mochilas;

import io.github.ivanils0n.mochilas.backpack.Backpacks;
import io.github.ivanils0n.mochilas.backpack.Tier;
import io.github.ivanils0n.mochilas.equipment.Equipment;
import io.github.ivanils0n.mochilas.gui.BackpackGui;
import io.github.ivanils0n.mochilas.visual.Visuals;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;
import java.util.UUID;

/**
 * Só no CI (runClientGameTest com xvfb). Abre o jogo de verdade em singleplayer (cliente + servidor integrado com o
 * mod) e tira prints da mochila nas costas: de trás, de lado e de frente (câmera num suporte de armadura invisível),
 * agachado, andando, nadando, planando com élitro, depois de trocar de dimensão e depois de sair e voltar ao mundo,
 * além das telas de cada nível. Também confere no servidor o shift-clique e a recusa de mochila dentro de mochila.
 */
public class ClientShots implements FabricClientGameTest {
	private static double X, Y, Z;
	private static UUID BAG;

	@Override
	public void runTest(ClientGameTestContext context) {
		TestWorldSave save;
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			save = sp.getWorldSave();
			context.waitTicks(100);
			context.runOnClient(c -> c.options.hideGui = true);
			cmd(sp, "time set 6000");
			cmd(sp, "weather clear");
			sp.getServer().runOnServer(s -> {
				ServerPlayer p = player(s);
				X = Math.floor(p.getX()) + 0.5;
				Y = p.getY();
				Z = Math.floor(p.getZ()) + 0.5;
			});
			cmd(sp, String.format(Locale.ROOT, "tp @p %.2f %.2f %.2f 0 0", X, Y, Z));
			cmd(sp, String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", (int) X - 6, (int) Y, (int) Z - 6, (int) X + 6, (int) Y + 4, (int) Z + 6));

			for (Tier t : Tier.values()) {
				sp.getServer().runOnServer(s -> {
					ServerPlayer p = player(s);
					ItemStack bag = Backpacks.create(t);
					Equipment.equip(p, bag);
					BAG = Backpacks.getId(Equipment.equipped(p));
				});
				context.waitTicks(20);
				shots(context, sp, t.id);
			}

			// Poses (com a de diamante, a maior)
			setCam(context, sp, 3.0, 0, "side");
			context.getInput().holdKey(o -> o.keyShift);
			context.waitTicks(15);
			context.takeScreenshot("pose_agachado_lado");
			setCam(context, sp, 0, -3.0, "back");
			context.waitTicks(5);
			context.takeScreenshot("pose_agachado_costas");
			context.getInput().releaseKey(o -> o.keyShift);
			freeCam(context);

			context.runOnClient(c -> c.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.getInput().holdKey(o -> o.keyUp);
			context.waitTicks(12);
			context.takeScreenshot("pose_andando");
			context.getInput().holdKey(o -> o.keySprint);
			context.waitTicks(12);
			context.takeScreenshot("pose_correndo");
			context.getInput().releaseKey(o -> o.keySprint);
			context.getInput().releaseKey(o -> o.keyUp);
			context.waitTicks(10);
			context.getInput().holdKey(o -> o.keyJump);
			context.waitTicks(4);
			context.takeScreenshot("pose_pulo");
			context.getInput().releaseKey(o -> o.keyJump);
			context.waitTicks(20);

			// Rastejando sob um teto de 1 bloco: mesma pose de nadar, parado, para ver de lado
			cmd(sp, String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:glass", (int) X - 1, (int) Y + 1, (int) Z - 1, (int) X + 1, (int) Y + 1, (int) Z + 1));
			context.waitTicks(30);
			setCam(context, sp, 3.4, -0.6, "side");
			context.takeScreenshot("pose_rastejando_lado");
			setCam(context, sp, 0, -3.4, "back");
			context.takeScreenshot("pose_rastejando_costas");
			freeCam(context);
			cmd(sp, String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", (int) X - 1, (int) Y + 1, (int) Z - 1, (int) X + 1, (int) Y + 1, (int) Z + 1));
			context.waitTicks(20);

			// Nadando: piscina e correr dentro da água
			cmd(sp, String.format(Locale.ROOT, "fill %d %d %d %d %d %d water", (int) X - 6, (int) Y, (int) Z - 1, (int) X + 6, (int) Y + 2, (int) Z + 30));
			cmd(sp, String.format(Locale.ROOT, "tp @p %.2f %.2f %.2f 0 0", X, Y + 0.2, Z));
			context.waitTicks(20);
			context.getInput().holdKey(o -> o.keyUp);
			context.getInput().holdKey(o -> o.keySprint);
			context.waitTicks(30);
			context.takeScreenshot("pose_nadando");
			context.getInput().releaseKey(o -> o.keySprint);
			context.getInput().releaseKey(o -> o.keyUp);
			cmd(sp, String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", (int) X - 6, (int) Y, (int) Z - 1, (int) X + 6, (int) Y + 2, (int) Z + 30));

			// Élitro: sobe, cai e aperta pular no ar
			cmd(sp, "gamemode survival @p");
			cmd(sp, "item replace entity @p armor.chest with minecraft:elytra");
			cmd(sp, String.format(Locale.ROOT, "tp @p %.2f %.2f %.2f 0 10", X, Y + 40, Z));
			context.waitTicks(8);
			context.getInput().pressKey(o -> o.keyJump);
			context.waitTicks(12);
			context.takeScreenshot("pose_elitro");
			sp.getServer().runOnServer(s -> Mochilas.LOG.info("MOCHILAS CLIENTE élitro: planando={}", player(s).isFallFlying()));
			cmd(sp, "gamemode creative @p");
			cmd(sp, "item replace entity @p armor.chest with minecraft:air");
			cmd(sp, String.format(Locale.ROOT, "tp @p %.2f %.2f %.2f 0 0", X, Y, Z));
			context.runOnClient(c -> c.options.setCameraType(CameraType.FIRST_PERSON));
			context.waitTicks(40);

			// Tela: shift-clique e mochila dentro de mochila (no servidor, com o jogador de verdade)
			sp.getServer().runOnServer(s -> {
				ServerPlayer p = player(s);
				p.getInventory().clearContent();
				var items = Mochilas.storage().get(BAG, 54);
				items.set(0, new ItemStack(Items.DIAMOND, 5));
				items.set(1, new ItemStack(Items.OAK_LOG, 32));
				p.getInventory().setItem(0, Backpacks.create(Tier.COURO));
				p.getInventory().setItem(1, new ItemStack(Items.COBBLESTONE, 64));
				BackpackGui.openEquipped(p);
				var menu = p.containerMenu;
				menu.quickMoveStack(p, 0); // diamantes -> inventário
				boolean dOut = items.get(0).isEmpty() && p.getInventory().countItem(Items.DIAMOND) == 5;
				// espaços do jogador vêm depois dos 54 da mochila; hotbar = últimos 9
				int hotbar0 = 54 + 27;
				menu.quickMoveStack(p, hotbar0); // mochila de couro -> não pode entrar
				boolean bagStayed = Backpacks.isBackpack(p.getInventory().getItem(0));
				menu.quickMoveStack(p, hotbar0 + 1); // pedra -> entra
				boolean cobbleIn = p.getInventory().getItem(1).isEmpty();
				boolean lockOther = !Mochilas.storage().tryOpen(BAG, UUID.randomUUID());
				Mochilas.LOG.info("MOCHILAS CLIENTE tela: shift-clique para fora={} mochila recusada={} shift-clique para dentro={} trava={}",
						dOut, bagStayed, cobbleIn, lockOther);
			});
			context.waitTicks(20);
			context.takeScreenshot("tela_diamante_54");
			for (Tier t : new Tier[]{Tier.COURO, Tier.FERRO, Tier.OURO}) {
				sp.getServer().runOnServer(s -> {
					ServerPlayer p = player(s);
					p.closeContainer();
					ItemStack bag = Backpacks.create(t);
					Equipment.equip(p, bag);
					UUID id = Backpacks.getId(Equipment.equipped(p));
					var items = Mochilas.storage().get(id, t.capacidade());
					items.set(0, new ItemStack(Items.BREAD, 12));
					items.set(t.capacidade() - 1, new ItemStack(Items.IRON_PICKAXE));
					BackpackGui.openEquipped(p);
				});
				context.waitTicks(20);
				context.takeScreenshot("tela_" + t.id + "_" + t.capacidade());
			}
			sp.getServer().runOnServer(s -> {
				ServerPlayer p = player(s);
				p.closeContainer();
				Equipment.equip(p, Backpacks.create(Tier.OURO));
				BAG = Backpacks.getId(Equipment.equipped(p));
				Mochilas.storage().get(BAG, 27).set(3, new ItemStack(Items.EMERALD, 9));
				Mochilas.storage().markDirty(BAG);
			});
			context.waitTicks(10);

			// Troca de dimensão
			cmd(sp, "execute in minecraft:the_nether run forceload add -16 -16 16 16");
			context.waitTicks(60);
			cmd(sp, "execute in minecraft:the_nether run fill -6 97 -6 6 104 6 air");
			cmd(sp, "execute in minecraft:the_nether run fill -6 98 -6 6 98 6 minecraft:glass");
			cmd(sp, "execute in minecraft:the_nether run tp @p 0.5 99 0.5 0 0");
			context.waitTicks(100);
			context.runOnClient(c -> c.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(10);
			context.takeScreenshot("dimensao_nether_costas");
			sp.getServer().runOnServer(s -> Mochilas.LOG.info("MOCHILAS CLIENTE nether: visual={} equipada={}",
					Visuals.has(player(s)), Equipment.hasEquipped(player(s))));
			context.runOnClient(c -> c.options.setCameraType(CameraType.FIRST_PERSON));
			cmd(sp, String.format(Locale.ROOT, "execute in minecraft:overworld run tp @p %.2f %.2f %.2f 0 0", X, Y, Z));
			context.waitTicks(60);
		}

		// Sair e voltar ao mundo: a mochila tem que continuar nas costas e com os itens
		try (TestSingleplayerContext sp = save.open()) {
			context.waitTicks(100);
			sp.getServer().runOnServer(s -> {
				ServerPlayer p = player(s);
				ItemStack eq = Equipment.equipped(p);
				boolean same = BAG.equals(Backpacks.getId(eq));
				var items = Mochilas.storage().get(BAG, 27);
				Mochilas.LOG.info("MOCHILAS CLIENTE reconexão: equipada={} mesmo ID={} esmeraldas={} visual={}",
						Backpacks.tier(eq), same, items.get(3).getCount(), Visuals.has(p));
			});
			context.runOnClient(c -> c.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(10);
			context.takeScreenshot("reconexao_costas");
		}
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().get(0);
	}

	private static void cmd(TestSingleplayerContext sp, String c) {
		sp.getServer().runCommand(c);
	}

	/** Três vistas com câmera fixa (suporte de armadura invisível) olhando para o jogador. */
	private static void shots(ClientGameTestContext context, TestSingleplayerContext sp, String name) {
		setCam(context, sp, 0, -3.4, "back");
		context.takeScreenshot(name + "_1_costas");
		setCam(context, sp, 3.4, -0.6, "side");
		context.takeScreenshot(name + "_2_lado");
		setCam(context, sp, 2.4, 2.4, "front");
		context.takeScreenshot(name + "_3_frente");
		freeCam(context);
	}

	/**
	 * Câmera fixa: suporte de armadura invisível e a câmera de 3ª pessoa dele (que fica 4 blocos atrás do olho, na
	 * direção oposta ao olhar). Assim o próprio jogador aparece no print, visto de onde quisermos.
	 */
	private static void setCam(ClientGameTestContext context, TestSingleplayerContext sp, double dx, double dz, String tag) {
		double len = Math.sqrt(dx * dx + dz * dz), ux = dx / len, uz = dz / len;
		float pitch = 15f;
		double back = 4 * Math.cos(Math.toRadians(pitch)); // quanto a câmera recua na horizontal
		double sx = X + ux * (len - back), sz = Z + uz * (len - back), sy = Y + 1.0;
		float yaw = (float) Math.toDegrees(Math.atan2(ux, -uz));
		cmd(sp, "kill @e[type=minecraft:armor_stand,name=cam]");
		cmd(sp, String.format(Locale.ROOT, "summon minecraft:armor_stand %.3f %.3f %.3f {Invisible:1b,Marker:1b,NoGravity:1b,CustomName:\"cam\",Rotation:[%.1ff,%.1ff]}",
				sx, sy, sz, yaw, pitch));
		context.waitTicks(10);
		context.runOnClient(c -> {
			Entity cam = null;
			for (Entity e : c.level.entitiesForRendering()) {
				if (e.getCustomName() != null && "cam".equals(e.getCustomName().getString())) cam = e;
			}
			if (cam != null) {
				c.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				c.setCameraEntity(cam);
			} else {
				Mochilas.LOG.warn("MOCHILAS CLIENTE câmera {} não encontrada", tag);
			}
		});
		context.waitTicks(5);
	}

	private static void freeCam(ClientGameTestContext context) {
		context.runOnClient((Minecraft c) -> {
			c.setCameraEntity(c.player);
			c.options.setCameraType(CameraType.FIRST_PERSON);
		});
	}
}
