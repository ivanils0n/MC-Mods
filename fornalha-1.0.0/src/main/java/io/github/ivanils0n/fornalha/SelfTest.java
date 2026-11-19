package io.github.ivanils0n.fornalha;

import io.github.ivanils0n.fornalha.FornalhaStore.Forno;
import io.github.ivanils0n.fornalha.FornalhaStore.Status;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.storage.LevelResource;

/** Roda só com -Dfornalha.selftest=true (CI): monta uma fornalha, funde minério, troca o núcleo e desmonta. */
final class SelfTest {
	private SelfTest() {}

	static void run(MinecraftServer server) {
		try {
			check(server);
			Fornalha.LOG.info("FORNALHA SELFTEST OK");
		} catch (Throwable t) {
			Fornalha.LOG.error("FORNALHA SELFTEST FALHOU", t);
		}
		server.halt(false);
	}

	private static void check(MinecraftServer server) {
		ensure(server.getRecipeManager().getRecipes().stream().anyMatch(r -> r.id().toString().contains("fornalha:casca")), "receita da casca não carregou");
		ensure(server.getRecipeManager().getRecipes().stream().anyMatch(r -> r.id().toString().contains("fornalha:controlador")), "receita do controlador não carregou");
		ensure(Fornalha.isCascaItem(Fornalha.cascaItem()) && Fornalha.isControladorItem(Fornalha.controladorItem()), "itens não reconhecidos");
		ensure(!Fornalha.isCascaItem(new ItemStack(Items.SMOOTH_STONE)) && !Fornalha.isControladorItem(new ItemStack(Items.BLAST_FURNACE)), "item comum aceito");
		ensure(Fornalha.PACK_SHA1 != null, "hash do resource pack não está no .jar");

		ServerLevel sl = server.overworld();
		sl.setChunkForced(0, 0, true);
		BlockPos ctrl = new BlockPos(8, 200, 8);
		clear(sl, ctrl);

		Forno f = Fornalha.colocarControlador(sl, ctrl, Direction.SOUTH);
		ensure(f != null && Fornalha.store.forno(Fornalha.dim(sl), ctrl) == f, "controlador não foi colocado");
		ensure(Fornalha.visuals(sl, ctrl).size() == 1 && !Fornalha.visuals(sl, ctrl).get(0).shouldBeSaved(), "visual do controlador");
		BlockPos centro = f.centro();
		ensure(centro.equals(ctrl.north()), "centro errado: " + centro);

		// Sem estrutura: não monta.
		ensure(Fornalha.tentarFormar(sl, f) != null && !f.formado, "montou sem estrutura");
		BlockPos faltando = null;
		for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
			BlockPos p = centro.offset(dx, dy, dz);
			if (p.equals(ctrl) || p.equals(centro)) continue;
			if (faltando == null) {
				faltando = p;
				continue;
			}
			ensure(Fornalha.colocarCasca(sl, p), "casca não foi colocada em " + p);
		}
		sl.setBlock(centro, Blocks.IRON_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
		String erro = Fornalha.tentarFormar(sl, f);
		ensure(erro != null && erro.contains("Casca"), "montou com casca faltando: " + erro);
		Fornalha.colocarCasca(sl, faltando);
		erro = Fornalha.tentarFormar(sl, f);
		ensure(erro == null && f.formado && f.nivel == 1, "não montou: " + erro);
		ensure(Fornalha.store.donoDe(Fornalha.dim(sl), faltando) == f, "casca sem dono");
		ensure(Fornalha.modelo(f).equals("fornalha_ctrl_1"), "modelo: " + Fornalha.modelo(f));

		// Funde: 8 minérios de ferro brutos com carvão; nível ferro = 4 por ciclo.
		BarrelBlockEntity be = (BarrelBlockEntity) sl.getBlockEntity(ctrl);
		be.setItem(0, new ItemStack(Items.RAW_IRON, 8));
		be.setItem(Fundicao.COMBUSTIVEL, new ItemStack(Items.COAL, 2));
		ensure(Fundicao.fundivel(sl, new ItemStack(Items.RAW_IRON)) && !Fundicao.fundivel(sl, new ItemStack(Items.STICK)), "fundível");
		int ciclo = Fundicao.tempoCiclo(f);
		for (int t = 0; t <= ciclo; t++) Fundicao.tick(sl, f, be, false);
		ensure(f.tanque > 0 && be.getItem(Fundicao.COMBUSTIVEL).isEmpty(), "carvão não foi para o tanque: " + f.tanque);
		ensure(count(be, Items.RAW_IRON) == 4, "deveria sobrar 4 minérios: " + count(be, Items.RAW_IRON));
		ensure(count(be, Items.IRON_INGOT) >= 4 && count(be, Items.IRON_INGOT) <= 8, "lingotes: " + count(be, Items.IRON_INGOT));
		ensure(f.xp > 0, "XP não foi guardado");
		ensure(f.status == Status.TRABALHANDO, "situação: " + f.status);
		ensure(Fornalha.modelo(f).equals("fornalha_ctrl_1_on"), "modelo aceso: " + Fornalha.modelo(f));

		// Redstone pausa.
		int antes = count(be, Items.RAW_IRON);
		for (int t = 0; t <= ciclo; t++) Fundicao.tick(sl, f, be, true);
		ensure(f.status == Status.PAUSADO && count(be, Items.RAW_IRON) == antes, "fundiu pausada");

		// Balde de lava: vai para o tanque e o balde vazio para a saída.
		long tanque = f.tanque;
		be.setItem(Fundicao.COMBUSTIVEL, new ItemStack(Items.LAVA_BUCKET));
		Fundicao.abastecer(sl, f, be);
		ensure(f.tanque == tanque + 20000 && count(be, Items.BUCKET) == 1, "lava: " + f.tanque);

		// Funis: baú em cima do topo abastece, baú embaixo da base recebe a saída.
		sl.setBlock(centro.above(2), Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
		sl.setBlock(centro.below(2), Blocks.HOPPER.defaultBlockState(), Block.UPDATE_ALL);
		Container cima = (Container) sl.getBlockEntity(centro.above(2));
		cima.setItem(0, new ItemStack(Items.RAW_GOLD, 3));
		int saida = count(be, Items.IRON_INGOT) + count(be, Items.BUCKET);
		Fundicao.funis(sl, f, be);
		ensure(count(be, Items.RAW_GOLD) == 1 && count(cima, Items.RAW_GOLD) == 2, "funil de cima");
		ensure(sl.getBlockEntity(centro.below(2)) instanceof HopperBlockEntity h && !h.isEmpty()
			&& count(be, Items.IRON_INGOT) + count(be, Items.BUCKET) == saida - 1, "funil de baixo");

		// Núcleo de diamante: nível 3 (16 por vez).
		sl.setBlock(centro, Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
		Fornalha.conferir(sl, f);
		ensure(f.formado && f.nivel == 3 && Fundicao.paralelo(f.nivel) == 16, "nível diamante: " + f.nivel);

		// Salva e lê de novo.
		Fornalha.store.save();
		FornalhaStore lido = FornalhaStore.load(server.getWorldPath(LevelResource.ROOT).resolve("fornalha.json"));
		Forno f2 = lido.forno(Fornalha.dim(sl), ctrl);
		ensure(f2 != null && f2.formado && f2.nivel == 3 && f2.tanque == f.tanque && lido.isCasca(Fornalha.dim(sl), faltando), "não foi salvo");

		// Tirar uma casca desmonta.
		Fornalha.removerCasca(sl, faltando);
		ensure(!f.formado && f.nivel == 0 && Fornalha.store.donoDe(Fornalha.dim(sl), centro.above()) == null, "não desmontou");
		ensure(Fornalha.visuals(sl, faltando).isEmpty(), "visual da casca ficou");

		// Remove tudo.
		Fornalha.removerForno(sl, f);
		ensure(Fornalha.store.forno(Fornalha.dim(sl), ctrl) == null && Fornalha.visuals(sl, ctrl).isEmpty(), "controlador não foi removido");
		be.clearContent();
		for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
			BlockPos p = centro.offset(dx, dy, dz);
			if (Fornalha.store.isCasca(Fornalha.dim(sl), p)) Fornalha.removerCasca(sl, p);
		}
		((Container) sl.getBlockEntity(centro.above(2))).clearContent();
		((Container) sl.getBlockEntity(centro.below(2))).clearContent();
		clear(sl, ctrl);
		sl.setChunkForced(0, 0, false);
	}

	private static void clear(ServerLevel sl, BlockPos ctrl) {
		for (int dy = 3; dy >= -3; dy--) {
			for (int dx = -2; dx <= 2; dx++) for (int dz = -4; dz <= 1; dz++) sl.setBlock(ctrl.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
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
