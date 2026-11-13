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
 * Roda só com -Dsmartchest.selftest=true (CI): num servidor de verdade, monta um Baú Central, liga um baú e um
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
		ItemStack oldName = SmartChest.chestItem();
		oldName.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Baú Inteligente"));
		SmartChest.upgrade(oldName);
		ensure(oldName.getHoverName().getString().equals("Baú Central") && SmartChest.MAX_LINKS == 512, "nome novo no item antigo");
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
		ensure(vis.size() == 2 && !vis.get(0).shouldBeSaved() && !vis.get(1).shouldBeSaved()
			&& vis.stream().filter(e -> e.entityTags().contains("smartchest_chest_lid")).count() == 1, "visual (corpo e tampa): " + vis.size());
		SmartChest.spawnVisual(sl, sc);
		ensure(SmartChest.visuals(sl, base).size() == 2, "recriar o visual duplicou");

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
		// Sem limite de blocos: vale a distância de visão (baú a 40 blocos = 2 chunks do Baú Central).
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
			"ordem por nome (padrão) e total");
		ensure(menu.getSlot(StorageMenu.GRID_START + 1).getItem().is(Items.DIAMOND) && menu.getSlot(StorageMenu.GRID_START + 1).getItem().getCount() == 15, "vitrine do diamante");
		ensure(menu.getSlot(StorageMenu.GRID_START + 1).getItem().get(net.minecraft.core.component.DataComponents.LORE).lines().stream()
			.map(Component::getString).toList().containsAll(List.of("Total: 15", "Em 2 baús", "Categoria: Minérios")), "texto do item: total e baús");
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

		// Rodinha do Mouse Tweaks: botão direito no item (pega metade), botão direito no inventário (põe 1) e clique no
		// item (devolve o resto). Ele clica num espaço vazio; o diamante vai para o monte que já está no inventário.
		fp.getInventory().setItem(3, new ItemStack(Items.DIAMOND, 2));
		int hotbar3 = -1;
		for (int i = 54; i < menu.slots.size(); i++) if (menu.getSlot(i).container == fp.getInventory() && menu.getSlot(i).getContainerSlot() == 3) hotbar3 = i;
		int empty4 = hotbar3 + 1;
		menu.refresh();
		menu.clicked(diamondSlot, 1, ContainerInput.PICKUP, fp);
		menu.clicked(empty4, 1, ContainerInput.PICKUP, fp);
		menu.clicked(diamondSlot, 0, ContainerInput.PICKUP, fp);
		ensure(fp.getInventory().getItem(4).isEmpty() && fp.getInventory().getItem(3).getCount() == 3 && menu.getCarried().isEmpty() && count(List.of(chest, barrel), Items.DIAMOND) == 14,
			"rodinha para tirar: " + fp.getInventory().getItem(3) + " " + menu.getCarried() + " " + count(List.of(chest, barrel), Items.DIAMOND));
		// Guardar: botão direito no inventário (pega metade), botão direito no item (guarda 1), clique no inventário (devolve).
		menu.clicked(hotbar3, 1, ContainerInput.PICKUP, fp);
		menu.clicked(diamondSlot, 1, ContainerInput.PICKUP, fp);
		menu.clicked(hotbar3, 0, ContainerInput.PICKUP, fp);
		ensure(fp.getInventory().getItem(3).getCount() == 2 && menu.getCarried().isEmpty() && count(List.of(chest, barrel), Items.DIAMOND) == 15,
			"rodinha para guardar: " + fp.getInventory().getItem(3) + " " + menu.getCarried() + " " + count(List.of(chest, barrel), Items.DIAMOND));
		fp.getInventory().setItem(3, ItemStack.EMPTY);
		menu.refresh();

		ensure(StorageMenu.detached(fp, sc, "DIAM").view().size() == 1, "pesquisa pelo nome");

		// Abas e filtro pelo item.
		Object[][] cats = {
			{Items.IRON_CHESTPLATE, 2}, {Items.DIAMOND_HORSE_ARMOR, 2}, {Items.WOLF_ARMOR, 2}, {Items.TURTLE_HELMET, 2},
			{Items.DIAMOND_PICKAXE, 5}, {Items.DIAMOND_SWORD, 5}, {Items.ELYTRA, 5}, {Items.SHIELD, 5},
			{Items.OAK_LOG, 1}, {Items.OAK_PLANKS, 1}, {Items.OAK_DOOR, 1}, {Items.CRAFTING_TABLE, 1}, {Items.CHEST, 1},
			{Items.FURNACE, 1}, {Items.COBBLESTONE, 1}, {Items.DIRT, 1}, {Items.BRICKS, 1}, {Items.OAK_LEAVES, 1},
			{Items.IRON_ORE, 3}, {Items.RAW_IRON, 3}, {Items.IRON_INGOT, 3}, {Items.DIAMOND, 3}, {Items.IRON_BLOCK, 3}, {Items.COAL, 3},
			{Items.PISTON, 4}, {Items.OBSERVER, 4}, {Items.DROPPER, 4}, {Items.DISPENSER, 4}, {Items.COMPARATOR, 4},
			{Items.REDSTONE, 4}, {Items.REDSTONE_BLOCK, 4}, {Items.REDSTONE_TORCH, 4}, {Items.HOPPER, 4}, {Items.LEVER, 4},
			{Items.STONE_BUTTON, 4}, {Items.REDSTONE_LAMP, 4},
			{Items.APPLE, 6}, {Items.POTION, 6}, {Items.WHEAT_SEEDS, 6}, {Items.OAK_SAPLING, 6}, {Items.SUGAR_CANE, 6},
			{Items.STRING, 7}, {Items.BONE, 7}, {Items.SLIME_BALL, 7}};
		StringBuilder wrong = new StringBuilder();
		for (Object[] c : cats) {
			int got = Categories.of(new ItemStack((net.minecraft.world.item.Item) c[0]));
			if (got != (int) c[1]) wrong.append(' ').append(c[0]).append('=').append(got).append("(esperado ").append(c[1]).append(')');
		}
		ensure(wrong.isEmpty(), "categorias:" + wrong);
		menu.clicked(StorageMenu.FIRST_TAB + 5, 0, ContainerInput.PICKUP, fp); // aba Ferramentas e combate
		ensure(menu.view().isEmpty(), "aba de ferramentas mostrou " + menu.view().size());
		menu.clicked(StorageMenu.FIRST_TAB + 3, 0, ContainerInput.PICKUP, fp); // aba Minérios
		ensure(menu.view().size() == 1 && menu.view().get(0).proto.is(Items.DIAMOND), "aba de minérios");
		menu.clicked(StorageMenu.FIRST_TAB + 1, 0, ContainerInput.PICKUP, fp); // aba Construção
		ensure(menu.view().size() == 1 && menu.view().get(0).proto.is(Items.COBBLESTONE), "aba de construção");
		menu.clicked(StorageMenu.FIRST_TAB, 0, ContainerInput.PICKUP, fp); // Todos
		ensure(menu.view().size() == 2, "aba Todos");
		menu.setCarried(new ItemStack(Items.COBBLESTONE, 3));
		menu.clicked(StorageMenu.FILTER, 0, ContainerInput.PICKUP, fp); // filtro pelo item na mão
		ensure(menu.view().size() == 1 && menu.view().get(0).proto.is(Items.COBBLESTONE) && menu.getCarried().getCount() == 3,
			"filtro pelo item");
		menu.setCarried(ItemStack.EMPTY);
		menu.clicked(StorageMenu.FILTER, 0, ContainerInput.PICKUP, fp); // sem item: tira o filtro
		ensure(menu.view().size() == 2, "tirar o filtro");
		fp.getInventory().setItem(2, new ItemStack(Items.DIAMOND));
		menu.clicked(StorageMenu.FILTER, 2, ContainerInput.SWAP, fp); // tecla 3: filtra pelo item da hotbar
		ensure(menu.view().size() == 1 && menu.view().get(0).proto.is(Items.DIAMOND) && fp.getInventory().getItem(2).is(Items.DIAMOND),
			"filtro pela tecla da hotbar");
		fp.getInventory().setItem(2, ItemStack.EMPTY);
		menu.clicked(StorageMenu.FILTER, 2, ContainerInput.SWAP, fp); // tecla com espaço vazio: tira o filtro
		ensure(menu.view().size() == 2, "tirar o filtro");
		ensure(StorageMenu.detached(fp, sc, "cobble").view().size() == 1, "pesquisa pelo id");
		ensure(StorageMenu.detached(fp, sc, "xyz").view().isEmpty(), "pesquisa sem resultado");

		// Pesquisa com o inventário como vitrine: o inventário de verdade nunca muda até pegar os separados.
		fp.getInventory().setItem(0, new ItemStack(Items.DIRT, 5));
		SearchMenu search = SearchMenu.detached(fp, sc, "diam");
		int firstResult = -1, firstBasket = -1;
		for (int i = 0; i < search.slots.size(); i++) {
			if (search.slots.get(i).container == fp.getInventory()) throw new IllegalStateException("pesquisa usa o inventário de verdade");
			if (search.slots.get(i).y == 84 && search.slots.get(i).x == 8) firstResult = i;
			if (search.slots.get(i).y == 142 && search.slots.get(i).x == 8) firstBasket = i;
		}
		ensure(search.getSlot(firstResult).getItem().is(Items.DIAMOND) && search.getSlot(firstBasket).getItem().isEmpty(), "vitrine da pesquisa");
		search.clicked(firstResult, 0, ContainerInput.PICKUP, fp); // pega os diamantes na mão (de mentira)
		ensure(search.getCarried().is(Items.DIAMOND) && search.getCarried().getCount() == 15 && count(List.of(chest, barrel), Items.DIAMOND) == 15,
			"pegar na pesquisa não pode tirar do baú");
		search.clicked(firstBasket, 1, ContainerInput.PICKUP, fp); // botão direito: solta 1
		search.clicked(firstBasket, 1, ContainerInput.PICKUP, fp);
		ensure(search.getSlot(firstBasket).getItem().getCount() == 2 && search.getCarried().getCount() == 13, "soltar 1 por vez nos separados");
		search.clicked(firstResult, 0, ContainerInput.PICKUP, fp); // solta o resto nos resultados: volta para o baú
		ensure(search.getCarried().isEmpty(), "devolver o que estava na mão");
		search.clicked(firstResult, 0, ContainerInput.QUICK_MOVE, fp); // Shift+clique: o resto vai direto para a fileira
		ensure(search.getSlot(firstBasket).getItem().is(Items.DIAMOND) && search.getSlot(firstBasket).getItem().getCount() == 15
			&& count(List.of(chest, barrel), Items.DIAMOND) == 15 && fp.getInventory().getItem(0).is(Items.DIRT)
			&& count(fp.getInventory(), Items.DIAMOND) == 0, "separar não pode mexer em nada");
		search.removed(fp); // Esc: pega os separados
		ensure(count(fp.getInventory(), Items.DIAMOND) == 15 && count(List.of(chest, barrel), Items.DIAMOND) == 0
			&& fp.getInventory().getItem(0).getCount() == 5 && count(fp.getInventory(), Items.ARROW) == 0
			&& count(fp.getInventory(), Items.WRITABLE_BOOK) == 0, "Esc não pegou os separados ou mexeu no inventário");
		// Item na mão sem soltar na fileira e seta de voltar: nada sai do baú nem entra no inventário.
		chest.setItem(5, new ItemStack(Items.EMERALD, 7));
		for (int back = 0; back < 3; back++) { // 0: só fechar; 1: seta de voltar; 2: X de limpar
			SearchMenu s2 = SearchMenu.detached(fp, sc, "emerald");
			s2.clicked(firstResult, 0, ContainerInput.PICKUP, fp);
			ensure(s2.getCarried().is(Items.EMERALD), "pesquisa de esmeralda");
			if (back > 0) {
				s2.clicked(firstBasket, 0, ContainerInput.PICKUP, fp);
				s2.clicked(back - 1, 0, ContainerInput.PICKUP, fp); // seta de voltar ou X
			}
			s2.removed(fp);
			ensure(count(fp.getInventory(), Items.EMERALD) == 0 && count(List.of(chest, barrel), Items.EMERALD) == 7
				&& count(fp.getInventory(), Items.ARROW) == 0 && count(fp.getInventory(), Items.BARRIER) == 0, "pesquisa deu ou perdeu item (voltar=" + back + ")");
		}
		chest.setItem(5, ItemStack.EMPTY);
		for (int i = 0; i < fp.getInventory().getContainerSize(); i++) fp.getInventory().setItem(i, ItemStack.EMPTY);
		Storage.insert(List.of(chest, barrel), new ItemStack(Items.DIAMOND, 15));

		// Organizar: uma categoria por baú (Construção no baú, Minérios no barril), juntando as pilhas.
		chest.setItem(10, new ItemStack(Items.DIAMOND, 3));
		List<ItemStack> rest = new java.util.ArrayList<>();
		ensure(Storage.organize(linkedOf(List.of(chest, barrel), 0, 0), rest) && rest.isEmpty() && count(chest, Items.COBBLESTONE) == 64
			&& count(chest, Items.DIAMOND) == 0 && barrel.getItem(0).is(Items.DIAMOND) && barrel.getItem(0).getCount() == 18,
			"organizar: " + chest.getItem(0) + " " + barrel.getItem(0));
		// Mais categorias que baús: em sequência, nada some.
		barrel.setItem(5, new ItemStack(Items.APPLE, 7));
		ensure(!Storage.organize(linkedOf(List.of(chest, barrel), 0, 0), rest) && rest.isEmpty() && chest.getItem(0).is(Items.COBBLESTONE)
			&& chest.getItem(1).getCount() == 18 && chest.getItem(2).getCount() == 7 && count(barrel, Items.APPLE) == 0,
			"organizar em sequência: " + chest.getItem(1) + " " + chest.getItem(2));
		chest.setItem(2, ItemStack.EMPTY);
		// Modos: baú só de Minérios e barril só retirar. Organizar não toca no barril e só põe minérios no baú (o
		// pedregulho, sem outro lugar, fica num espaço livre); guardar pedregulho não acha baú.
		barrel.setItem(4, new ItemStack(Items.APPLE, 2));
		Storage.Linked modes = linkedOf(List.of(chest, barrel), Categories.ORES, ChestStore.INPUT);
		Storage.organize(modes, rest);
		ensure(rest.isEmpty() && chest.getItem(0).is(Items.DIAMOND) && count(chest, Items.COBBLESTONE) == 64
			&& barrel.getItem(4).is(Items.APPLE) && count(barrel, Items.DIAMOND) == 0, "organizar com modos: " + chest.getItem(0));
		ensure(modes.targets(new ItemStack(Items.DIAMOND)).equals(List.of(chest)) && modes.targets(new ItemStack(Items.COBBLESTONE)).isEmpty(),
			"guardar com modos");
		barrel.setItem(4, ItemStack.EMPTY);
		sc.link(barrelPos, ChestStore.INPUT);
		ensure(sc.mode(barrelPos) == ChestStore.INPUT && sc.mode(chestPos) == ChestStore.ANY && sc.links.size() == 2, "modo do vínculo");
		sc.link(barrelPos, ChestStore.ANY);

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

		// Mesa de trabalho ligada: até 33 blocos, visual próprio, livro de receitas puxando dos baús.
		BlockPos tablePos = base.north(3), farTable = base.north(40);
		for (BlockPos p : List.of(tablePos, farTable)) sl.setBlock(p, Blocks.CRAFTING_TABLE.defaultBlockState(), Block.UPDATE_ALL);
		ItemStack lk = SmartChest.linkerItem();
		SmartChest.bind(fp, sl, lk, sc);
		SmartChest.toggleTable(fp, sl, lk, farTable);
		ensure(!sc.hasTable(farTable), "mesa a 40 blocos foi ligada");
		SmartChest.toggleTable(fp, sl, lk, tablePos);
		ensure(sc.hasTable(tablePos) && SmartChest.store.tableOwner(SmartChest.dim(sl), tablePos) == sc, "mesa não foi ligada");
		ensure(SmartChest.visuals(sl, tablePos).size() == 1, "visual da mesa: " + SmartChest.visuals(sl, tablePos).size());
		var stick = server.getRecipeManager().getRecipes().stream().filter(r -> r.id().identifier().toString().equals("minecraft:stick")).findFirst().orElseThrow();
		var pick = server.getRecipeManager().getRecipes().stream().filter(r -> r.id().identifier().toString().equals("minecraft:diamond_pickaxe")).findFirst().orElseThrow();
		fp.getInventory().clearContent();
		chest.setItem(20, new ItemStack(Items.OAK_PLANKS, 5));
		TableMenu table = new TableMenu(0, fp.getInventory(), sl, tablePos, sc, "");
		var none = net.minecraft.world.inventory.RecipeBookMenu.PostPlaceAction.NOTHING;
		ensure(table.handlePlacement(false, false, stick, sl, fp.getInventory()) == none, "receita do graveto");
		ensure(gridCount(table, Items.OAK_PLANKS) == 2 && count(chest, Items.OAK_PLANKS) == 3 && table.getSlot(0).getItem().is(Items.STICK),
			"graveto com tábuas do baú: grade " + gridCount(table, Items.OAK_PLANKS) + ", baú " + count(chest, Items.OAK_PLANKS));
		ensure(table.handlePlacement(true, false, stick, sl, fp.getInventory()) == none, "graveto com Shift");
		ensure(gridCount(table, Items.OAK_PLANKS) == 4 && count(chest, Items.OAK_PLANKS) == 1,
			"graveto com Shift: grade " + gridCount(table, Items.OAK_PLANKS) + ", baú " + count(chest, Items.OAK_PLANKS));
		table.clicked(1 + 4, 0, ContainerInput.PICKUP, fp);
		table.clicked(1 + 4, 0, ContainerInput.QUICK_MOVE, fp);
		ensure(gridCount(table, Items.OAK_PLANKS) == 4 && table.getCarried().isEmpty() && count(fp.getInventory(), Items.OAK_PLANKS) == 0, "tirou item da grade");
		table.clicked(0, 0, ContainerInput.QUICK_MOVE, fp); // Shift no resultado: crafta tudo da grade
		ensure(count(fp.getInventory(), Items.STICK) == 8 && gridCount(table, Items.OAK_PLANKS) == 0, "Shift no resultado: " + count(fp.getInventory(), Items.STICK));
		table.broadcastChanges(); // grade acabou: remonta com o que sobrou (1 tábua, receita incompleta)
		ensure(gridCount(table, Items.OAK_PLANKS) == 1 && count(chest, Items.OAK_PLANKS) == 0, "remontar a grade: " + gridCount(table, Items.OAK_PLANKS));
		table.removed(fp);
		ensure(gridCount(table, Items.OAK_PLANKS) == 0 && count(chest, Items.OAK_PLANKS) == 1 && count(fp.getInventory(), Items.OAK_PLANKS) == 0,
			"fechar a mesa devolve aos baús: baú " + count(chest, Items.OAK_PLANKS) + ", jogador " + count(fp.getInventory(), Items.OAK_PLANKS));
		fp.getInventory().clearContent();
		chest.setItem(20, new ItemStack(Items.OAK_PLANKS, 5));
		ensure(CraftSearchMenu.detached(fp, sc, tablePos, "stick").found().stream().anyMatch(c -> c.result().is(Items.STICK) && c.result().getCount() == 4),
			"pesquisa da mesa não achou o graveto");
		ensure(CraftSearchMenu.detached(fp, sc, tablePos, "").found().stream().noneMatch(c -> c.result().is(Items.DIAMOND_PICKAXE)), "pesquisa mostrou receita sem itens");
		var tableRecipe = server.getRecipeManager().getRecipes().stream().filter(r -> r.id().identifier().toString().equals("minecraft:crafting_table")).findFirst().orElseThrow();
		chest.setItem(20, new ItemStack(Items.OAK_PLANKS, 3));
		table = new TableMenu(0, fp.getInventory(), sl, tablePos, sc, "");
		ensure(table.handlePlacement(false, false, tableRecipe, sl, fp.getInventory()) == none && gridCount(table, Items.OAK_PLANKS) == 3
			&& count(chest, Items.OAK_PLANKS) == 0, "receita incompleta: grade " + gridCount(table, Items.OAK_PLANKS));
		fp.getInventory().setItem(0, new ItemStack(Items.OAK_PLANKS, 10)); // faltando nos baús, completa com o inventário
		ensure(table.handlePlacement(false, false, tableRecipe, sl, fp.getInventory()) == none && gridCount(table, Items.OAK_PLANKS) == 4
			&& count(chest, Items.OAK_PLANKS) == 0 && count(fp.getInventory(), Items.OAK_PLANKS) == 9,
			"baú + inventário: grade " + gridCount(table, Items.OAK_PLANKS) + ", jogador " + count(fp.getInventory(), Items.OAK_PLANKS));
		fp.getInventory().clearContent();
		ensure(table.handlePlacement(false, false, pick, sl, fp.getInventory()) == net.minecraft.world.inventory.RecipeBookMenu.PostPlaceAction.PLACE_GHOST_RECIPE
			&& gridCount(table, Items.OAK_PLANKS) == 0 && count(chest, Items.OAK_PLANKS) == 4, "receita sem itens deveria virar fantasma");
		fp.getInventory().clearContent();
		chest.setItem(20, ItemStack.EMPTY);
		SmartChest.toggleTable(fp, sl, lk, tablePos);
		ensure(!sc.hasTable(tablePos) && SmartChest.visuals(sl, tablePos).isEmpty(), "desligar a mesa");
		for (BlockPos p : List.of(tablePos, farTable)) sl.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

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

	private static Storage.Linked linkedOf(List<Container> cs, int... modes) {
		Storage.Linked l = new Storage.Linked();
		l.containers.addAll(cs);
		for (int m : modes) l.modes.add(m);
		return l;
	}

	private static int gridCount(TableMenu m, net.minecraft.world.item.Item item) {
		int n = 0;
		for (var slot : m.getInputGridSlots()) if (slot.getItem().is(item)) n += slot.getItem().getCount();
		return n;
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
