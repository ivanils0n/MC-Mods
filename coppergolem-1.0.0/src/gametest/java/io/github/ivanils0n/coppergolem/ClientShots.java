package io.github.ivanils0n.coppergolem;

import io.github.ivanils0n.coppergolem.Links.Link;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Só no CI (runClientGameTest com xvfb): tira prints do cajado na mão e das telas, para conferir como ficam no jogo. */
public class ClientShots implements FabricClientGameTest {
	private static BlockPos CHEST;
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			context.waitTicks(100); // chunks carregando
			sp.getServer().runCommand("time set 6000");
			sp.getServer().runCommand("weather clear");
			sp.getServer().runCommand("gamerule doDaylightCycle false");
			sp.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:warped_fungus_on_a_stick[minecraft:custom_data={coppergolem_wand:1b},minecraft:item_model=\"coppergolem:wand\"]");
			context.waitTicks(40);
			context.takeScreenshot("1_primeira_pessoa");
			sp.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:warped_fungus_on_a_stick[minecraft:custom_data={coppergolem_wand:1b},minecraft:item_model=\"coppergolem:wand\"]");
			context.runOnClient(c -> c.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(10);
			context.takeScreenshot("2_terceira_costas");
			context.runOnClient(c -> c.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(10);
			context.takeScreenshot("3_terceira_frente");
			context.runOnClient(c -> c.options.setCameraType(CameraType.FIRST_PERSON));

			sp.getServer().runOnServer(server -> {
				ServerPlayer p = server.getPlayerList().getPlayers().get(0);
				ServerLevel sl = p.level();
				BlockPos o = p.blockPosition().offset(3, 0, 0);
				@SuppressWarnings("unchecked")
				EntityType<CopperGolem> type = (EntityType<CopperGolem>) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath("minecraft", "copper_golem"));
				CopperGolem g = type.spawn(sl, o, EntitySpawnReason.COMMAND);
				Link l = GolemMod.links.getOrCreate(g.getUUID(), GolemMod.dim(sl));
				BlockPos chest = o.offset(0, 0, 3);
				sl.setBlock(chest, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
				l.refill = Link.arr(chest);
				for (int i = 0; i < 8; i++) l.sources.add(Link.arr(chest));
				for (int i = 0; i < 30; i++) l.dests.add(Link.arr(chest));
				GolemMenu.open(p, g.getUUID());
			});
			context.waitTicks(20);
			context.takeScreenshot("4_tela_golem");
			sp.getServer().runOnServer(server -> {
				ServerPlayer p = server.getPlayerList().getPlayers().get(0);
				GolemMenu.open(p, GolemMod.links.golems().get(0), 1);
			});
			context.waitTicks(20);
			context.takeScreenshot("4b_tela_golem_mais");
			sp.getServer().runOnServer(server -> {
				ServerPlayer p = server.getPlayerList().getPlayers().get(0);
				p.closeContainer();
				LinkMenu.open(p, p.blockPosition().offset(3, 0, 3));
			});
			context.waitTicks(20);
			context.takeScreenshot("5_vincular_bau");

			// baú de recarga oxidado com 2 barras, golem de verdade andando: tem que gastar 1 barra por nível
			sp.getServer().runOnServer(server -> {
				ServerPlayer p = server.getPlayerList().getPlayers().get(0);
				p.closeContainer();
				ServerLevel sl = p.level();
				BlockPos c = p.blockPosition().offset(-4, 0, 2);
				sl.setBlock(c, BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("minecraft", "oxidized_copper_chest")).defaultBlockState(), Block.UPDATE_ALL);
				((net.minecraft.world.Container) sl.getBlockEntity(c)).setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COPPER_INGOT, 2));
				@SuppressWarnings("unchecked")
				EntityType<CopperGolem> type = (EntityType<CopperGolem>) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath("minecraft", "copper_golem"));
				CopperGolem g = type.spawn(sl, c.offset(-3, 0, 0), EntitySpawnReason.COMMAND);
				GolemMod.links.getOrCreate(g.getUUID(), GolemMod.dim(sl)).refill = Link.arr(c);
				CHEST = c;
			});
			context.waitTicks(600);
			sp.getServer().runOnServer(server -> {
				ServerLevel sl = server.overworld();
				int n = Boxes.count(Boxes.containers(sl, CHEST), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COPPER_INGOT));
				GolemMod.LOG.info("COPPERGOLEM BAU {} barras={} (esperado: exposed_copper_chest e 0)", sl.getBlockState(CHEST).getBlock(), n);
			});
		}
	}
}
