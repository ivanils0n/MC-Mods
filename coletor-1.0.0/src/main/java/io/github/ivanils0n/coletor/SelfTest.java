package io.github.ivanils0n.coletor;

import io.github.ivanils0n.coletor.ColetorStore.Machine;
import io.github.ivanils0n.coletor.ColetorStore.Status;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.storage.LevelResource;

/** Roda só com -Dcoletor.selftest=true (CI): monta um Coletor numa plantação de teste, confere colheita, replantio e farinha. */
final class SelfTest {
	private SelfTest() {}

	static void run(MinecraftServer server) {
		try {
			check(server);
			Coletor.LOG.info("COLETOR SELFTEST OK");
		} catch (Throwable t) {
			Coletor.LOG.error("COLETOR SELFTEST FALHOU", t);
		}
		server.halt(false);
	}

	private static void check(MinecraftServer server) {
		ensure(server.getRecipeManager().getRecipes().stream().anyMatch(r -> r.id().toString().contains("coletor:coletor")), "receita não carregou");
		ensure(Coletor.isMachineItem(Coletor.machineItem()), "item do Coletor não é reconhecido");
		ensure(!Coletor.isMachineItem(new ItemStack(Items.BARREL)), "barril comum foi aceito como Coletor");
		ensure(Coletor.PACK_SHA1 != null, "hash do resource pack não está no .jar");

		ServerLevel sl = server.overworld();
		sl.setChunkForced(0, 0, true);
		BlockPos c = new BlockPos(8, 200, 8);
		clear(sl, c);
		for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) sl.setBlock(c.offset(dx, -1, dz), Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_CLIENTS);

		Machine m = Coletor.placeMachine(sl, c, Coletor.machineItem());
		ensure(m != null && Coletor.isMachine(sl, c), "Coletor não foi colocado");
		ensure(Coletor.visuals(sl, c).size() == 1, "visual: " + Coletor.visuals(sl, c).size());
		ensure(!Coletor.visuals(sl, c).get(0).shouldBeSaved(), "visual seria salvo no mapa");
		BarrelBlockEntity be = (BarrelBlockEntity) sl.getBlockEntity(c);
		ensure(m.radius == 4 && m.boneMeal, "padrão: área 9x9 e farinha ligada");
		m.radius = 1;

		// Trigo maduro: colhe, guarda o trigo e replanta com uma das sementes colhidas.
		CropBlock wheat = (CropBlock) Blocks.WHEAT;
		var ripe = Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7);
		BlockPos w = c.east();
		sl.setBlock(w, ripe, Block.UPDATE_CLIENTS);
		// Fora da área (raio 1): fica.
		BlockPos far = c.east(2);
		sl.setBlock(far, ripe, Block.UPDATE_CLIENTS);
		Harvester.work(sl, m, be);
		ensure(sl.getBlockState(w).is(Blocks.WHEAT) && !wheat.isMaxAge(sl.getBlockState(w)), "trigo não foi replantado: " + sl.getBlockState(w));
		ensure(count(be, Items.WHEAT) >= 1, "trigo não foi guardado");
		ensure(wheat.isMaxAge(sl.getBlockState(far)), "colheu fora da área");
		ensure(m.status == Status.WORKING, "situação: " + m.status);

		// Redstone pausa.
		sl.setBlock(w, ripe, Block.UPDATE_CLIENTS);
		for (int t = 0; t < Harvester.PERIOD * 2; t++) Harvester.tick(sl, m, be, true);
		ensure(wheat.isMaxAge(sl.getBlockState(w)) && m.status == Status.PAUSED, "colheu com redstone");

		// Farinha de osso guardada faz a cenoura crescer.
		BlockPos carrot = c.west();
		sl.setBlock(carrot, Blocks.CARROTS.defaultBlockState(), Block.UPDATE_CLIENTS);
		sl.setBlock(w, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		be.setItem(20, new ItemStack(Items.BONE_MEAL, 16));
		for (int i = 0; i < 3; i++) Harvester.work(sl, m, be);
		ensure(count(be, Items.BONE_MEAL) < 16, "farinha de osso não foi usada");
		ensure(!sl.getBlockState(carrot).equals(Blocks.CARROTS.defaultBlockState()) || count(be, Items.CARROT) > 0, "cenoura não cresceu");
		// Farinha desligada: não gasta.
		m.boneMeal = false;
		sl.setBlock(carrot, Blocks.CARROTS.defaultBlockState(), Block.UPDATE_CLIENTS);
		int meal = count(be, Items.BONE_MEAL);
		Harvester.work(sl, m, be);
		ensure(count(be, Items.BONE_MEAL) == meal, "gastou farinha com a opção desligada");
		m.boneMeal = true;

		// Fungo do Nether e frutas silvestres.
		BlockPos wart = c.north();
		sl.setBlock(wart.below(), Blocks.SOUL_SAND.defaultBlockState(), Block.UPDATE_CLIENTS);
		sl.setBlock(wart, Blocks.NETHER_WART.defaultBlockState().setValue(NetherWartBlock.AGE, 3), Block.UPDATE_CLIENTS);
		BlockPos berry = c.south();
		sl.setBlock(berry.below(), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
		sl.setBlock(berry, Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 3), Block.UPDATE_CLIENTS);
		Harvester.work(sl, m, be);
		ensure(sl.getBlockState(wart).is(Blocks.NETHER_WART) && sl.getBlockState(wart).getValue(NetherWartBlock.AGE) == 0, "fungo: " + sl.getBlockState(wart));
		ensure(count(be, Items.NETHER_WART) >= 1, "fungo não foi guardado");
		ensure(sl.getBlockState(berry).getValue(SweetBerryBushBlock.AGE) == 1 && count(be, Items.SWEET_BERRIES) >= 2, "frutas silvestres");

		// Cana: a base fica, o resto é colhido (mesmo o bloco acima da área).
		BlockPos cane = c.offset(1, 0, 1);
		sl.setBlock(cane.below(), Blocks.SAND.defaultBlockState(), Block.UPDATE_CLIENTS);
		for (int i = 0; i < 3; i++) sl.setBlock(cane.above(i), Blocks.SUGAR_CANE.defaultBlockState(), Block.UPDATE_CLIENTS);
		int before = count(be, Items.SUGAR_CANE);
		Harvester.work(sl, m, be);
		ensure(sl.getBlockState(cane).is(Blocks.SUGAR_CANE) && sl.getBlockState(cane.above()).isAir() && sl.getBlockState(cane.above(2)).isAir(), "cana");
		ensure(count(be, Items.SUGAR_CANE) == before + 2, "cana guardada: " + (count(be, Items.SUGAR_CANE) - before));

		// Barril cheio: a planta fica no pé e a situação vira "cheio".
		for (int i = 0; i < be.getContainerSize(); i++) be.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		sl.setBlock(w, ripe, Block.UPDATE_CLIENTS);
		Harvester.work(sl, m, be);
		ensure(wheat.isMaxAge(sl.getBlockState(w)) && m.status == Status.FULL, "colheu com o barril cheio: " + m.status);

		ColetorStore reloaded;
		Coletor.store.save();
		reloaded = ColetorStore.load(server.getWorldPath(LevelResource.ROOT).resolve("coletor.json"));
		ensure(reloaded.get(Coletor.dim(sl), c) != null && reloaded.get(Coletor.dim(sl), c).radius == 1, "Coletor não foi salvo no arquivo");

		Coletor.removeMachine(sl, m);
		ensure(!Coletor.isMachine(sl, c) && Coletor.visuals(sl, c).isEmpty(), "Coletor não foi removido");
		be.clearContent();
		clear(sl, c);
		sl.setChunkForced(0, 0, false);
	}

	private static void clear(ServerLevel sl, BlockPos c) {
		for (int dy = 3; dy >= -1; dy--) {
			for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) sl.setBlock(c.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	private static int count(Container box, Item item) {
		int n = 0;
		for (int i = 0; i < box.getContainerSize(); i++) if (box.getItem(i).is(item)) n += box.getItem(i).getCount();
		return n;
	}

	private static void ensure(boolean ok, String what) {
		if (!ok) throw new IllegalStateException(what);
	}
}
