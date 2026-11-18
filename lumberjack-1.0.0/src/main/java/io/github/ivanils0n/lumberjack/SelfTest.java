package io.github.ivanils0n.lumberjack;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Só com -Dlumberjack.selftest=true (CI): corta árvores com um jogador falso num servidor real, confere tudo e desliga. */
final class SelfTest {
	private static final int Y = 150; // chão de pedra; árvores de Y+1 para cima

	private SelfTest() {}

	static void run(MinecraftServer server) {
		try {
			check(server);
			Lumberjack.LOG.info("LUMBERJACK SELFTEST OK");
		} catch (Throwable t) {
			Lumberjack.LOG.error("LUMBERJACK SELFTEST FALHOU", t);
		}
		server.halt(false);
	}

	private static void check(MinecraftServer server) {
		ServerLevel sl = server.overworld();
		sl.setChunkForced(0, 0, true);
		sl.setChunkForced(1, 0, true);
		for (int x = 0; x < 32; x++) {
			for (int z = 0; z < 16; z++) {
				sl.setBlock(new BlockPos(x, Y, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
				for (int y = Y + 1; y <= Y + 12; y++) sl.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		ensure(Lumberjack.placed != null, "marcações não carregaram");
		FakePlayer fp = FakePlayer.get(sl);
		fp.snapTo(16.5, Y + 1, 14.5, 0f, 0f);
		ensure(!fp.getAbilities().instabuild, "jogador de teste no criativo");

		// 1. Árvore natural com galho na diagonal: tudo cai, os drops juntam no tronco quebrado e o machado gasta 1 por tronco.
		List<BlockPos> t1 = tree(sl, 2, 2, 6);
		BlockPos branch = new BlockPos(3, Y + 6, 3);
		sl.setBlock(branch, Blocks.OAK_LOG.defaultBlockState(), Block.UPDATE_ALL);
		t1.add(branch);
		ItemStack axe = new ItemStack(Items.DIAMOND_AXE);
		hold(fp, axe);
		ensure(fp.gameMode.destroyBlock(t1.get(0)), "não quebrou o tronco");
		Lumberjack.processPending();
		for (BlockPos p : t1) ensure(sl.getBlockState(p).isAir(), "tronco ficou em " + p);
		int logs = 0;
		for (ItemEntity e : items(sl)) {
			ensure(e.getItem().is(Items.OAK_LOG), "drop estranho: " + e.getItem());
			ensure(e.position().distanceTo(Vec3.atCenterOf(t1.get(0))) < 1.5, "drop longe do tronco: " + e.position());
			logs += e.getItem().getCount();
		}
		ensure(logs == t1.size(), "troncos soltos: " + logs + " de " + t1.size());
		ensure(fp.getMainHandItem().getDamageValue() == t1.size(), "gasto do machado: " + fp.getMainHandItem().getDamageValue());
		clearItems(sl);
		ok("árvore natural cai inteira (" + t1.size() + " troncos)");

		// 2. Agachado: só um.
		List<BlockPos> t2 = tree(sl, 6, 2, 5);
		hold(fp, new ItemStack(Items.IRON_AXE));
		fp.setShiftKeyDown(true);
		cut(fp, t2.get(0));
		fp.setShiftKeyDown(false);
		ensure(sl.getBlockState(t2.get(0)).isAir() && sl.getBlockState(t2.get(1)).is(Blocks.OAK_LOG), "agachado derrubou a árvore");
		// 3. Sem machado: só um.
		hold(fp, new ItemStack(Items.STICK));
		cut(fp, t2.get(1));
		ensure(sl.getBlockState(t2.get(1)).isAir() && sl.getBlockState(t2.get(2)).is(Blocks.OAK_LOG), "sem machado derrubou a árvore");
		clearItems(sl);
		ok("agachado e sem machado quebram só um");

		// 4. Coluna de troncos sem folhas (construção antiga, vila): só um.
		List<BlockPos> t4 = new ArrayList<>();
		for (int y = 1; y <= 4; y++) {
			BlockPos p = new BlockPos(10, Y + y, 2);
			sl.setBlock(p, Blocks.SPRUCE_LOG.defaultBlockState(), Block.UPDATE_ALL);
			t4.add(p);
		}
		hold(fp, new ItemStack(Items.IRON_AXE));
		cut(fp, t4.get(0));
		ensure(sl.getBlockState(t4.get(1)).is(Blocks.SPRUCE_LOG), "troncos sem folhas caíram");
		clearItems(sl);
		ok("sem folhas naturais não é árvore");

		// 5. Troncos colocados pelo jogador (de verdade, pelo item), mesmo encostados em folhas naturais: só um.
		hold(fp, new ItemStack(Items.OAK_LOG, 16));
		BlockPos c0 = placeOn(fp, sl, new BlockPos(14, Y, 2), Direction.UP);
		BlockPos c1 = placeOn(fp, sl, c0, Direction.UP);
		BlockPos c2 = placeOn(fp, sl, c1, Direction.UP);
		for (BlockPos p : List.of(c0, c1, c2)) ensure(Lumberjack.placed.contains(sl, p), "tronco colocado não foi marcado: " + p);
		leaves(sl, c2);
		hold(fp, new ItemStack(Items.DIAMOND_AXE));
		cut(fp, c0);
		ensure(sl.getBlockState(c0).isAir() && sl.getBlockState(c1).is(Blocks.OAK_LOG) && sl.getBlockState(c2).is(Blocks.OAK_LOG), "troncos colocados caíram em cadeia");
		ensure(!Lumberjack.placed.contains(sl, c0), "marcação ficou depois de quebrar");
		cut(fp, c2);
		ensure(sl.getBlockState(c1).is(Blocks.OAK_LOG), "troncos colocados caíram em cadeia (de cima)");
		clearItems(sl);
		ok("troncos colocados não caem em cadeia");

		// 6. Tronco colocado encostado numa árvore natural: a árvore cai, o colocado fica.
		List<BlockPos> t6 = tree(sl, 18, 2, 5);
		hold(fp, new ItemStack(Items.OAK_LOG, 4));
		BlockPos side = placeOn(fp, sl, t6.get(1), Direction.EAST);
		ensure(Lumberjack.placed.contains(sl, side), "tronco do lado não marcado");
		hold(fp, new ItemStack(Items.DIAMOND_AXE));
		cut(fp, t6.get(0));
		for (BlockPos p : t6) ensure(sl.getBlockState(p).isAir(), "tronco natural ficou em " + p);
		ensure(sl.getBlockState(side).is(Blocks.OAK_LOG) && Lumberjack.placed.contains(sl, side), "o tronco colocado caiu junto");
		clearItems(sl);
		ok("árvore cai e o tronco colocado ao lado fica");

		// 7. Machado quase quebrando: para com 1 de durabilidade.
		List<BlockPos> t7 = tree(sl, 22, 2, 6);
		ItemStack worn = new ItemStack(Items.DIAMOND_AXE);
		worn.setDamageValue(worn.getMaxDamage() - 3);
		hold(fp, worn);
		cut(fp, t7.get(0));
		ItemStack after = fp.getMainHandItem();
		ensure(after.is(Items.DIAMOND_AXE) && after.getMaxDamage() - after.getDamageValue() == 1, "machado: " + after + " " + after.getDamageValue());
		ensure(sl.getBlockState(t7.get(1)).isAir() && sl.getBlockState(t7.get(2)).is(Blocks.OAK_LOG), "não parou com 1 de durabilidade");
		clearItems(sl);
		ok("machado não quebra");

		// 8. Árvore do Nether (caule + verruga).
		List<BlockPos> t8 = new ArrayList<>();
		for (int y = 1; y <= 5; y++) {
			BlockPos p = new BlockPos(26, Y + y, 2);
			sl.setBlock(p, Blocks.CRIMSON_STEM.defaultBlockState(), Block.UPDATE_ALL);
			t8.add(p);
		}
		sl.setBlock(t8.get(4).above(), Blocks.NETHER_WART_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
		hold(fp, new ItemStack(Items.IRON_AXE));
		cut(fp, t8.get(0));
		for (BlockPos p : t8) ensure(sl.getBlockState(p).isAir(), "caule ficou em " + p);
		clearItems(sl);
		ok("fungo gigante do Nether");

		// 9. Arquivo: as marcações voltam iguais depois de gravar e ler.
		Lumberjack.placed.save();
		PlacedLogs reloaded = PlacedLogs.load(server.getWorldPath(LevelResource.ROOT).resolve("lumberjack.dat"));
		ensure(reloaded.contains(sl, side) && reloaded.contains(sl, c1) && !reloaded.contains(sl, c0), "lumberjack.dat");
		ensure(reloaded.size() == Lumberjack.placed.size(), "tamanho do arquivo");
		ok("arquivo lumberjack.dat");

		sl.setChunkForced(0, 0, false);
		sl.setChunkForced(1, 0, false);
	}

	/** Tronco de carvalho natural com copa de folhas naturais nas duas últimas alturas. */
	private static List<BlockPos> tree(ServerLevel sl, int x, int z, int height) {
		List<BlockPos> logs = new ArrayList<>();
		for (int y = 1; y <= height; y++) {
			BlockPos p = new BlockPos(x, Y + y, z);
			sl.setBlock(p, Blocks.OAK_LOG.defaultBlockState(), Block.UPDATE_ALL);
			logs.add(p);
		}
		leaves(sl, logs.get(logs.size() - 1));
		return logs;
	}

	/** Folhas naturais (não persistentes) em volta do topo, só onde há ar. */
	private static void leaves(ServerLevel sl, BlockPos top) {
		for (int dy = -1; dy <= 1; dy++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					BlockPos p = top.offset(dx, dy, dz);
					if (sl.getBlockState(p).isAir()) sl.setBlock(p, Blocks.OAK_LEAVES.defaultBlockState(), Block.UPDATE_ALL);
				}
			}
		}
		BlockState l = sl.getBlockState(top.above());
		ensure(l.is(Blocks.OAK_LEAVES) && !l.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.PERSISTENT), "folha de teste persistente");
	}

	/** Coloca o item da mão como um jogador, clicando na face do bloco. */
	private static BlockPos placeOn(FakePlayer fp, ServerLevel sl, BlockPos on, Direction face) {
		BlockPos target = on.relative(face);
		Vec3 hit = Vec3.atCenterOf(on).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
		fp.getMainHandItem().useOn(new UseOnContext(fp, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, on, false)));
		ensure(sl.getBlockState(target).is(net.minecraft.tags.BlockTags.LOGS), "não colocou o tronco em " + target);
		return target;
	}

	/** Quebra como o jogador e roda o fim do tick (onde a árvore cai). */
	private static void cut(FakePlayer fp, BlockPos pos) {
		fp.gameMode.destroyBlock(pos);
		Lumberjack.processPending();
	}

	private static void hold(FakePlayer fp, ItemStack stack) {
		fp.setItemInHand(InteractionHand.MAIN_HAND, stack);
	}

	private static List<ItemEntity> items(ServerLevel sl) {
		return sl.getEntitiesOfClass(ItemEntity.class, new AABB(0, Y - 2, 0, 32, Y + 20, 16));
	}

	private static void clearItems(ServerLevel sl) {
		for (ItemEntity e : items(sl)) e.discard();
	}

	private static void ok(String what) {
		Lumberjack.LOG.info("SELFTEST ok: {}", what);
	}

	private static void ensure(boolean cond, String msg) {
		if (!cond) throw new AssertionError(msg);
	}
}
