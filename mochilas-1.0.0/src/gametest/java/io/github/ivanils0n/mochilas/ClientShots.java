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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
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
			cmd(sp, "time set 6000");
			cmd(sp, "weather clear");
			sp.getServer().runOnServer(s -> {
				ServerPlayer p = player(s);
				X = Math.floor(p.getX()) + 0.5;
				Y = p.getY();
				Z = Math.floor(p.getZ()) + 0.5;
			});
			cmd(sp, String.format(Locale.ROOT, "tp @p %.2f %.2f %.2f 0 0", X, Y, Z));
			cmd(sp, String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", (int) X - 6, (int) Y, (int) Z - 6, (int) X + 6, (int) Y + 4, (int) Z + 10));

			// Manequim (avatar com modelo de jogador) usando a mochila; a câmera é o jogador, então é a mochila
			// "vista por outro jogador" (o caminho do rastreio do Polymer, não o do dono).
			cmd(sp, "gamemode spectator @p");
			for (Tier t : Tier.values()) {
				mannequin(context, sp, t, "standing");
				view(context, sp, 0, -3.0, t.id + "_1_costas");
				view(context, sp, 3.0, -0.5, t.id + "_2_lado");
				view(context, sp, 2.2, 2.2, t.id + "_3_frente");
			}
			for (String pose : new String[]{"crouching", "swimming", "fall_flying"}) {
				mannequin(context, sp, Tier.DIAMANTE, pose);
				view(context, sp, 3.0, -0.5, "pose_" + pose + "_lado");
				view(context, sp, -1.6, -2.6, "pose_" + pose + "_costas");
			}
			clearMannequin(sp);
			cmd(sp, "gamemode creative @p");
			cmd(sp, String.format(Locale.ROOT, "tp @p %.2f %.2f %.2f 0 0", X, Y, Z));

			// O próprio jogador com a mochila (visão do dono, 3ª pessoa): andando, correndo, pulando...
			sp.getServer().runOnServer(s -> {
				ServerPlayer p = player(s);
				Equipment.equip(p, Backpacks.create(Tier.DIAMANTE));
				BAG = Backpacks.getId(Equipment.equipped(p));
			});
			context.waitTicks(10);
			context.runOnClient(c -> c.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(5);
			context.takeScreenshot("dono_parado_costas");
			context.runOnClient(c -> c.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(5);
			context.takeScreenshot("dono_parado_frente");
			context.getInput().holdKey(o -> o.keyShift);
			context.waitTicks(10);
			context.takeScreenshot("dono_agachado_frente");
			context.getInput().releaseKey(o -> o.keyShift);
			context.waitTicks(5);

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
			sp.getServer().runOnServer(s -> note("MOCHILAS CLIENTE élitro: planando={}", player(s).isFallFlying()));
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
				note("MOCHILAS CLIENTE tela: shift-clique para fora={} mochila recusada={} shift-clique para dentro={} trava={}",
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
			sp.getServer().runOnServer(s -> note("MOCHILAS CLIENTE nether: visual={} equipada={}",
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
				note("MOCHILAS CLIENTE reconexão: equipada={} mesmo ID={} esmeraldas={} visual={}",
						Backpacks.tier(eq), same, items.get(3).getCount(), Visuals.has(p));
			});
			context.runOnClient(c -> c.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(10);
			context.takeScreenshot("reconexao_costas");
		}
		for (String n : NOTES) Mochilas.LOG.info("RESUMO {}", n);
	}

	/** Resultados repetidos no fim do teste (o CI só mostra as últimas linhas do log do cliente). */
	private static final java.util.List<String> NOTES = new java.util.ArrayList<>();

	private static void note(String fmt, Object... args) {
		String msg = fmt;
		for (Object a : args) msg = msg.replaceFirst("\\{\\}", java.util.regex.Matcher.quoteReplacement(String.valueOf(a)));
		NOTES.add(msg);
		Mochilas.LOG.info(msg);
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().get(0);
	}

	private static void cmd(TestSingleplayerContext sp, String c) {
		sp.getServer().runCommand(c);
	}

	private static net.minecraft.world.entity.Entity MANNEQUIN;
	private static io.github.ivanils0n.mochilas.visual.BackpackVisual MANNEQUIN_BAG;
	private static double MX, MY, MZ;

	/** Manequim de frente para o sul, 5 blocos ao sul do jogador, com a mochila do nível. */
	private static void mannequin(ClientGameTestContext context, TestSingleplayerContext sp, Tier t, String pose) {
		clearMannequin(sp);
		MX = X;
		MY = Y;
		MZ = Z + 5;
		cmd(sp, String.format(Locale.ROOT, "summon minecraft:mannequin %.2f %.2f %.2f {Rotation:[0f,0f],immovable:1b,pose:\"%s\",Tags:[\"mochila_teste\"]}", MX, MY, MZ, pose));
		context.waitTicks(5);
		sp.getServer().runOnServer(s -> {
			for (net.minecraft.world.entity.Entity e : player(s).level().getAllEntities()) {
				if (e.entityTags().contains("mochila_teste") && e instanceof net.minecraft.world.entity.LivingEntity le) {
					MANNEQUIN = e;
					MANNEQUIN_BAG = Visuals.attach(le, t);
				}
			}
			if (MANNEQUIN_BAG == null) note("MOCHILAS CLIENTE manequim não encontrado ({})", pose);
		});
		context.waitTicks(10);
	}

	private static void clearMannequin(TestSingleplayerContext sp) {
		sp.getServer().runOnServer(s -> {
			if (MANNEQUIN_BAG != null) MANNEQUIN_BAG.destroy();
			MANNEQUIN_BAG = null;
			MANNEQUIN = null;
		});
		cmd(sp, "kill @e[tag=mochila_teste]");
	}

	/** O jogador (câmera, 1ª pessoa) vai para (dx, dz) em volta do manequim e olha para ele. */
	private static void view(ClientGameTestContext context, TestSingleplayerContext sp, double dx, double dz, String name) {
		double cx = MX + dx, cz = MZ + dz, feet = MY + 0.8, eye = feet + 1.62, ty = MY + 0.9;
		float yaw = (float) Math.toDegrees(Math.atan2(-(MX - cx), MZ - cz));
		float pitch = (float) Math.toDegrees(Math.atan2(eye - ty, Math.hypot(dx, dz)));
		cmd(sp, String.format(Locale.ROOT, "tp @p %.2f %.2f %.2f %.1f %.1f", cx, feet, cz, yaw, pitch));
		context.waitTicks(8);
		context.takeScreenshot(name);
	}
}
