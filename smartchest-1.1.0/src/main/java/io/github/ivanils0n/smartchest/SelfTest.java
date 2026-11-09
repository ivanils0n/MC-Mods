package io.github.ivanils0n.smartchest;

import io.github.ivanils0n.smartchest.ChestStore.Chest;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Roda só com -Dsmartchest.selftest=true (CI): num servidor de verdade, monta um Baú Inteligente, liga um baú e um
 * barril, pega e guarda itens pela tela, testa baús cheios, outra dimensão, distância, idioma e desmonta.
 */
final class SelfTest {
	private SelfTest() {}

	static void run(MinecraftServer server) {
		try {
			check(server);
			SmartChest.LOG.info("SMARTCHEST SELFTEST OK");
		} catch (Throwable t) {
			SmartChest.LOG.error("SMARTCHEST SELFTEST FALHOU", t);
		}
		server.halt(false);
	}

	private static void check(MinecraftServer server) throws Exception {
		ensure(server.getRecipeManager().getRecipes().stream().anyMatch(r -> r.id().toString().contains("smartchest:chest")), "receita do baú não carregou");
		ensure(server.getRecipeManager().getRecipes().stream().anyMatch(r -> r.id().toString().contains("smartchest:linker")), "receita do vinculador não carregou");
		ensure(SmartChest.isChestItem(SmartChest.chestItem()) && !SmartChest.isChestItem(new ItemStack(Items.OBSIDIAN)), "item do baú");
		ensure(SmartChest.isLinker(SmartChest.linkerItem()) && !SmartChest.isLinker(new ItemStack(Items.BLAZE_ROD)), "item do vinculador");
		ensure(SmartChest.PACK_SHA1 != null, "hash do resource pack não está no .jar");
		Component title = SmartChest.themedTitle("\uF000\uE000\uF001\uF002", "filtro: diam");
		ensure(title.getString().endsWith("diam") && title.getSiblings().size() == 2, "título com fundo: " + title);
		ensure(SmartChest.model("chest").toString().equals("smartchest:chest") && SmartChest.chestItem().has(net.minecraft.core.component.DataComponents.ITEM_MODEL), "modelo do baú");
		ItemStack old = SmartChest.linkerItem();
		old.remove(net.minecraft.core.component.DataComponents.ITEM_MODEL);
		SmartChest.upgrade(old);
		ensure(SmartChest.model("linker").equals(old.get(net.minecraft.core.component.DataComponents.ITEM_MODEL)), "vinculador antigo não ganhou o modelo");
		ItemStack rod = new ItemStack(Items.BLAZE_ROD);
		SmartChest.upgrade(rod);
		ensure(!SmartChest.model("linker").equals(rod.get(net.minecraft.core.component.DataComponents.ITEM_MODEL)), "vara de blaze comum mudou de modelo");

		// Idioma: tradução própria, formato com argumentos, acentos e o download real do português.
		ensure(Lang.resolve(Component.translatable("item.minecraft.diamond"), k -> k.equals("item.minecraft.diamond") ? "Diamante" : null).equals("Diamante"), "tradução");
		ensure(Lang.format("%2$s de %1$s %% %s", new Object[] {"A", "B"}, k -> null, 0).equals("B de A % A"), "formato: " + Lang.format("%2$s de %1$s %% %s", new Object[] {"A", "B"}, k -> null, 0));
		ensure(Lang.normalize("Pá de FERRO").equals("pa de ferro"), "acentos");
		Map<String, String> pt = Lang.loadNow("pt_br");
		ensure("Diamante".equals(pt.get("item.minecraft.diamond")), "pt_br baixado: " + pt.get("item.minecraft.diamond"));
		ensure(Lang.loadNow("pt_br").size() == pt.size(), "pt_br do disco");

		ServerLevel sl = server.overworld();
		BlockPos base = new BlockPos(0, 200, 0);
		BlockPos chestPos = base.east(2), barrelPos = base.south(2), farPos = base.east(40);
		for (BlockPos p : List.of(base, chestPos, barrelPos, farPos, base.above())) sl.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

		Chest sc = SmartChest.placeChest(sl, base, Direction.SOUTH);
		ensure(sc != null && sl.getBlockState(base).is(Blocks.OBSIDIAN), "baú não foi colocado");
		List<Entity> vis = SmartChest.visuals(sl, base);
		ensure(vis.size() == 1 && !vis.get(0).shouldBeSaved(), "visual: " + vis.size());
		SmartChest.spawnVisual(sl, sc);
		ensure(SmartChest.visuals(sl, base).size() == 1, "recriar o visual duplicou");

		sl.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
		sl.setBlock(barrelPos, Blocks.BARREL.defaultBlockState(), Block.UPDATE_ALL);
		sl.setBlock(farPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
		Container chest = (Container) sl.getBlockEntity(chestPos), barrel = (Container) sl.getBlockEntity(barrelPos);
		chest.setItem(0, new ItemStack(Items.DIAMOND, 10));
		chest.setItem(1, new ItemStack(Items.COBBLESTONE, 64));
		barrel.setItem(0, new ItemStack(Items.DIAMOND, 5));

		// Regras de vínculo.
		ensure(SmartChest.linkProblem(sc, sl, chestPos) == null, "baú perto não pode ser ligado");
		ensure(SmartChest.linkProblem(sc, sl, base.west()) != null, "ar foi aceito como baú");
		// Sem limite de blocos: vale a distância de visão (baú a 40 blocos = 2 chunks do Baú Inteligente).
		ensure(SmartChest.linkProblem(sc, sl, farPos) == null, "baú a 40 blocos foi recusado");
		ensure(SmartChest.rangeProblem(sc, farPos, 2) == null, "baú dentro da distância de visão foi recusado");
		ensure(SmartChest.rangeProblem(sc, farPos, 1) != null, "baú fora da distância de visão foi aceito");
		ensure(SmartChest.rangeProblem(sc, base.east(16 * 30), 32) == null, "distância de visão grande");
		ServerLevel nether = server.getLevel(Level.NETHER);
		ensure(nether == null || SmartChest.linkProblem(sc, nether, chestPos) != null, "baú de outra dimensão foi aceito");
		sc.link(chestPos);
		sc.link(barrelPos);

		// Tela: soma dos baús, pegar, guardar, Shift+clique, pesquisa.
		FakePlayer fp = FakePlayer.get(sl);
		fp.setPos(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5);
		StorageMenu menu = StorageMenu.detached(fp, sc, "");
		ensure(menu.view().size() == 2, "tipos na tela: " + menu.view().size());
		ensure(menu.view().get(0).proto.is(Items.COBBLESTONE) && menu.view().get(1).total == 15 && menu.view().get(1).chests == 2,
			"ordem/total por quantidade");
		ensure(menu.getSlot(StorageMenu.GRID_START + 1).getItem().is(Items.DIAMOND) && menu.getSlot(StorageMenu.GRID_START + 1).getItem().getCount() == 15, "vitrine do diamante");
		ensure(menu.unloaded() == 0, "baús carregados contados como fora: " + menu.unloaded());
		ensure(SmartChest.viewDistance(fp) >= 2, "distância de visão do jogador: " + SmartChest.viewDistance(fp));
		// Baú ligado numa área que não está carregada: não aparece, é contado como fora da distância de visão e
		// continua ligado (volta quando a área carregar).
		BlockPos unloadedPos = new BlockPos(1_000_000, 64, 0);
		ensure(!sl.isLoaded(unloadedPos), "área de teste deveria estar descarregada");
		sc.link(unloadedPos);
		StorageMenu far = StorageMenu.detached(fp, sc, "");
		ensure(far.unloaded() == 1 && far.view().size() == 2 && sc.isLinked(unloadedPos), "baú fora da distância de visão");
		sc.unlink(unloadedPos);

		menu.clicked(StorageMenu.GRID_START + 1, 0, ContainerInput.PICKUP, fp); // pega o pacote de diamantes
		ensure(menu.getCarried().is(Items.DIAMOND) && menu.getCarried().getCount() == 15, "pegar: " + menu.getCarried());
		ensure(count(List.of(chest, barrel), Items.DIAMOND) == 0, "diamantes ficaram nos baús");
		menu.clicked(StorageMenu.GRID_START + 5, 0, ContainerInput.PICKUP, fp); // solta na grade = guarda
		ensure(menu.getCarried().isEmpty() && count(List.of(chest, barrel), Items.DIAMOND) == 15, "guardar o que estava na mão");

		int diamondSlot = -1;
		for (int i = StorageMenu.GRID_START; i < StorageMenu.GRID_START + StorageMenu.GRID; i++) if (menu.getSlot(i).getItem().is(Items.DIAMOND)) diamondSlot = i;
		menu.clicked(diamondSlot, 0, ContainerInput.QUICK_MOVE, fp); // Shift+clique: para o inventário
		ensure(count(fp.getInventory(), Items.DIAMOND) == 15 && count(List.of(chest, barrel), Items.DIAMOND) == 0, "Shift+clique para o inventário");
		int invSlot = -1;
		for (int i = 54; i < menu.slots.size(); i++) if (menu.getSlot(i).getItem().is(Items.DIAMOND)) invSlot = i;
		menu.clicked(invSlot, 0, ContainerInput.QUICK_MOVE, fp); // Shift+clique no inventário: guarda
		ensure(count(fp.getInventory(), Items.DIAMOND) == 0 && count(List.of(chest, barrel), Items.DIAMOND) == 15, "Shift+clique para guardar");

		ensure(StorageMenu.detached(fp, sc, "DIAM").view().size() == 1, "pesquisa pelo nome");

		// Abas (categorias do criativo) e filtro pelo item.
		ensure(Categories.of(new ItemStack(Items.DIAMOND_PICKAXE)) == 5 && Categories.of(new ItemStack(Items.APPLE)) == 6
			&& Categories.of(new ItemStack(Items.DIAMOND)) == 7 && Categories.of(new ItemStack(Items.REDSTONE_TORCH)) == 4,
			"categorias: " + Categories.of(new ItemStack(Items.DIAMOND_PICKAXE)) + " " + Categories.of(new ItemStack(Items.APPLE)) + " "
				+ Categories.of(new ItemStack(Items.DIAMOND)) + " " + Categories.of(new ItemStack(Items.REDSTONE_TORCH)));
		menu.clicked(StorageMenu.FIRST_TAB + 5, 0, ContainerInput.PICKUP, fp); // aba Ferramentas e combate
		ensure(menu.view().isEmpty(), "aba de ferramentas mostrou " + menu.view().size());
		menu.clicked(StorageMenu.FIRST_TAB + 7, 0, ContainerInput.PICKUP, fp); // aba Ingredientes e outros
		ensure(menu.view().size() == 1 && menu.view().get(0).proto.is(Items.DIAMOND), "aba de ingredientes");
		menu.clicked(StorageMenu.FIRST_TAB, 0, ContainerInput.PICKUP, fp); // Todos
		ensure(menu.view().size() == 2, "aba Todos");
		menu.setCarried(new ItemStack(Items.COBBLESTONE, 3));
		menu.clicked(StorageMenu.FILTER, 0, ContainerInput.PICKUP, fp); // filtro pelo item na mão
		ensure(menu.view().size() == 1 && menu.view().get(0).proto.is(Items.COBBLESTONE) && menu.getCarried().getCount() == 3,
			"filtro pelo item");
		menu.setCarried(ItemStack.EMPTY);
		menu.clicked(StorageMenu.FILTER, 0, ContainerInput.PICKUP, fp); // sem item: tira o filtro
		ensure(menu.view().size() == 2, "tirar o filtro");
		ensure(StorageMenu.detached(fp, sc, "cobble").view().size() == 1, "pesquisa pelo id");
		ensure(StorageMenu.detached(fp, sc, "xyz").view().isEmpty(), "pesquisa sem resultado");

		// Baús cheios: não guarda nada e o item fica com o jogador.
		for (Container c : List.of(chest, barrel)) for (int i = 0; i < c.getContainerSize(); i++) c.setItem(i, new ItemStack(Items.STONE, 64));
		ItemStack dirt = new ItemStack(Items.DIRT, 5);
		ensure(Storage.insert(List.of(chest, barrel), dirt) == 0 && dirt.getCount() == 5, "guardou em baú cheio");
		menu.refresh();
		menu.setCarried(new ItemStack(Items.DIRT, 5));
		menu.clicked(StorageMenu.GRID_START, 0, ContainerInput.PICKUP, fp);
		ensure(menu.getCarried().is(Items.DIRT) && menu.getCarried().getCount() == 5, "item sumiu ao guardar em baú cheio");
		menu.setCarried(ItemStack.EMPTY);
		chest.setItem(3, ItemStack.EMPTY);
		ensure(Storage.insert(List.of(chest, barrel), dirt) == 5 && dirt.isEmpty(), "não guardou com espaço livre");

		// Arquivo, quebrar e colocar de novo: os vínculos somem.
		SmartChest.store.saveIfDirty();
		SmartChest.store.save();
		ChestStore reloaded = ChestStore.load(server.getWorldPath(LevelResource.ROOT).resolve("smartchest.json"));
		ensure(reloaded.get(SmartChest.dim(sl), base) != null && reloaded.get(SmartChest.dim(sl), base).links.size() == 2, "vínculos não foram salvos");
		SmartChest.removeChest(sl, sc);
		ensure(SmartChest.visuals(sl, base).isEmpty() && SmartChest.store.get(SmartChest.dim(sl), base) == null, "remover");
		sl.setBlock(base, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		Chest again = SmartChest.placeChest(sl, base, Direction.NORTH);
		ensure(again != null && again.links.isEmpty(), "vínculos voltaram depois de quebrar");
		SmartChest.removeChest(sl, again);

		for (BlockPos p : List.of(chestPos, barrelPos, farPos)) {
			((Container) sl.getBlockEntity(p)).clearContent();
			sl.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
		sl.setBlock(base, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
	}

	private static int count(Container c, net.minecraft.world.item.Item item) {
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
		return n;
	}

	private static int count(List<Container> cs, net.minecraft.world.item.Item item) {
		int n = 0;
		for (Container c : cs) n += count(c, item);
		return n;
	}

	private static void ensure(boolean ok, String what) {
		if (!ok) throw new IllegalStateException(what);
	}
}
