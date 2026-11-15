package io.github.ivanils0n.sacoender;

import java.util.ArrayList;
import java.util.List;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;

/** Só com -Dsacoender.selftest=true (CI): testa receita, item e a ligação com o baú do fim num servidor real e desliga. */
final class SelfTest {
	private SelfTest() {}

	static void run(MinecraftServer server) {
		try {
			check(server);
			SacoEnder.LOG.info("SACOENDER SELFTEST OK");
		} catch (Throwable t) {
			SacoEnder.LOG.error("SACOENDER SELFTEST FALHOU", t);
		}
		server.halt(false);
	}

	private static void check(MinecraftServer server) {
		ServerLevel sl = server.overworld();

		// 1. Receita: trouxa vazia (de qualquer cor) no centro e 8 obsidianas em volta = o saco (igual ao item do código).
		ensure(server.getRecipeManager().getRecipes().stream().anyMatch(r -> r.id().toString().contains("sacoender:saco")), "receita não carregou");
		ItemStack e = new ItemStack(Items.OBSIDIAN), o2 = e;
		ItemStack bag = craft(server, sl, CraftingInput.of(3, 3, List.of(e, e, e, e, new ItemStack(Items.BUNDLE), e, e, e, e)));
		ensure(SacoEnder.isBag(bag), "o craft não deu o saco: " + bag);
		ensure(ItemStack.isSameItemSameComponents(bag, SacoEnder.bagItem()), "item da receita diferente do item do código: " + bag.getComponentsPatch() + " / " + SacoEnder.bagItem().getComponentsPatch());
		ensure(bag.getMaxStackSize() == 1, "o saco empilha");
		ensure(bag.getHoverName().getString().equals(SacoEnder.NAME), "nome: " + bag.getHoverName().getString());
		int colors = 0;
		for (Holder<Item> h : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, Identifier.withDefaultNamespace("bundles")))) {
			ensure(SacoEnder.isBag(craft(server, sl, CraftingInput.of(3, 3, List.of(e, e, e, e, new ItemStack(h), e, e, e, e)))), "trouxa não serve: " + h);
			colors++;
		}
		ensure(colors >= 1, "nenhuma trouxa na tag minecraft:bundles");
		// Trouxa com itens dentro não serve (os itens se perderiam no craft).
		ItemStack full = ItemStack.CODEC.parse(server.registryAccess().createSerializationContext(JsonOps.INSTANCE), JsonParser.parseString(
			"{\"id\":\"minecraft:bundle\",\"components\":{\"minecraft:bundle_contents\":[{\"id\":\"minecraft:diamond\",\"count\":3}]}}")).getOrThrow();
		ensure(!full.isEmpty() && full.has(DataComponents.BUNDLE_CONTENTS), "trouxa cheia do teste: " + full);
		ensure(server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(3, 3, List.of(e, e, e, e, full, e, e, e, e)), sl).isEmpty(), "trouxa com itens dentro virou saco");
		ItemStack o = new ItemStack(Items.OBSIDIAN);
		ItemStack chest = craft(server, sl, CraftingInput.of(3, 3, List.of(o, o, o, o, new ItemStack(Items.ENDER_EYE), o, o, o, o)));
		ensure(chest.is(Items.ENDER_CHEST), "a receita do baú do fim mudou: " + chest);
		ensure(!SacoEnder.isBag(new ItemStack(Items.BUNDLE)) && !SacoEnder.isBag(new ItemStack(Items.PAPER)), "trouxa comum foi aceita como saco");
		ensure(!bag.has(DataComponents.BUNDLE_CONTENTS), "o saco tem espaço de trouxa");
		// O saco não serve de trouxa para a própria receita (não tem o espaço vazio de trouxa).
		ensure(server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(3, 3, List.of(o2, o2, o2, o2, bag.copy(), o2, o2, o2, o2)), sl).isEmpty(), "saco virou saco de novo");
		// Saco tingido continua sendo o saco (sem espaço de trouxa).
		ItemStack dyed = craft(server, sl, CraftingInput.of(2, 1, List.of(bag.copy(), new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("purple_dye"))))));
		ensure(SacoEnder.isBag(dyed) && !dyed.is(Items.BUNDLE) && !dyed.has(DataComponents.BUNDLE_CONTENTS), "saco tingido: " + dyed);
		ok("receita (trouxa vazia + 8 obsidianas; " + colors + " cores de trouxa; saco tingido continua saco)");

		// 2. Sem o mod: o saco só tem componentes do jogo, é salvo como trouxa e volta igual.
		for (TypedDataComponent<?> c : bag.getComponents()) {
			var key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(c.type());
			ensure(key != null && key.getNamespace().equals("minecraft"), "componente fora do jogo: " + key);
		}
		var ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		Tag saved = ItemStack.CODEC.encodeStart(ops, bag).getOrThrow();
		ensure(saved.toString().contains("minecraft:bundle"), "salvo como: " + saved);
		ItemStack loaded = ItemStack.CODEC.parse(ops, saved).getOrThrow();
		ensure(ItemStack.isSameItemSameComponents(loaded, bag) && SacoEnder.isBag(loaded), "não voltou igual depois de salvo: " + loaded);
		ok("item salvo só com dados do jogo (trouxa)");

		// 3. Botão direito: o saco abre, trouxa comum não.
		FakePlayer fp = FakePlayer.get(sl);
		fp.setItemInHand(InteractionHand.MAIN_HAND, bag.copy());
		ensure(SacoEnder.onUse(fp, sl, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS, "botão direito com o saco");
		fp.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.BUNDLE));
		ensure(SacoEnder.onUse(fp, sl, InteractionHand.OFF_HAND) == InteractionResult.PASS, "trouxa comum abriu a tela");
		fp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		fp.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		ok("botão direito");

		// 4. A tela mexe direto nos espaços 18-26 (a última fileira) do baú do fim e nunca nos 0-17.
		int F = BagMenu.FIRST;
		ensure(F == 18, "primeiro espaço do saco: " + F);
		PlayerEnderChestContainer ender = fp.getEnderChestInventory();
		Inventory inv = fp.getInventory();
		ender.clearContent();
		inv.clearContent();
		ender.setItem(F, new ItemStack(Items.DIAMOND, 5));
		ender.setItem(0, new ItemStack(Items.GOLD_INGOT, 3));
		ender.setItem(17, new ItemStack(Items.ELYTRA));
		List<ItemStack> hidden = snapshot(ender, 0, 18);
		BagMenu menu = new BagMenu(1, inv, fp);
		ensure(menu.slots.size() == 9 + 36, "espaços da tela: " + menu.slots.size());
		ensure(menu.getSlot(0).getItem() == ender.getItem(F) && menu.getSlot(0).getItem().getCount() == 5, "a tela não mostra a última fileira do baú do fim");
		ensure(menu.stillValid(fp), "a tela fechou sozinha");

		menu.getSlot(4).set(new ItemStack(Items.TORCH, 16));
		ensure(ender.getItem(F + 4).is(Items.TORCH) && ender.getItem(F + 4).getCount() == 16, "item posto no saco não foi para o baú do fim");
		menu.getSlot(0).remove(2);
		ensure(ender.getItem(F).getCount() == 3, "item tirado do saco não saiu do baú do fim");
		ender.setItem(F + 1, new ItemStack(Items.EMERALD, 7));
		ensure(menu.getSlot(1).getItem().is(Items.EMERALD), "item posto no baú do fim não aparece no saco");
		ensure(ender.getItem(26) == menu.getSlot(8).getItem(), "último espaço do saco não é o último do baú do fim");

		// Shift+clique do inventário (hotbar 0 = espaço 36 da tela) vai para o 1º espaço livre do saco.
		inv.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
		menu.quickMoveStack(fp, 36);
		ensure(ender.getItem(F + 2).is(Items.COBBLESTONE) && ender.getItem(F + 2).getCount() == 64 && inv.getItem(0).isEmpty(), "shift+clique para o saco: " + ender.getItem(F + 2));
		// Saco cheio: o resto fica no inventário e o baú do fim (0-17) não muda.
		for (int i = F; i < 27; i++) if (ender.getItem(i).isEmpty()) ender.setItem(i, new ItemStack(Items.DIRT, 64));
		inv.setItem(0, new ItemStack(Items.STONE, 64));
		menu.quickMoveStack(fp, 36);
		ensure(inv.getItem(0).is(Items.STONE) && inv.getItem(0).getCount() == 64, "com o saco cheio o item saiu do inventário");
		ensure(same(hidden, snapshot(ender, 0, 18)), "o saco mexeu nos espaços 0-17 do baú do fim");
		// Shift+clique do saco para o inventário.
		menu.quickMoveStack(fp, 0);
		ensure(ender.getItem(F).isEmpty(), "shift+clique para o inventário não tirou do baú do fim");
		ensure(count(inv, Items.DIAMOND) == 3, "diamantes no inventário: " + count(inv, Items.DIAMOND));
		ensure(same(hidden, snapshot(ender, 0, 18)), "o saco mexeu nos espaços 0-17 do baú do fim");
		menu.removed(fp);
		ok("tela ligada aos 9 últimos espaços do baú do fim");

		// 4b. Clicar com um item em cima do saco (e o saco em cima de um item) não guarda nada dentro dele.
		inv.clearContent();
		inv.setItem(0, bag.copy());
		inv.setItem(1, new ItemStack(Items.DIRT, 10));
		var im = fp.inventoryMenu;
		im.setCarried(new ItemStack(Items.DIAMOND, 4));
		im.clicked(36, 0, ContainerInput.PICKUP, fp);
		ensure(!im.getCarried().isEmpty() && SacoEnder.isBag(im.getCarried()) && !im.getCarried().has(DataComponents.BUNDLE_CONTENTS), "item foi posto dentro do saco: " + im.getCarried());
		ensure(inv.getItem(0).is(Items.DIAMOND) && inv.getItem(0).getCount() == 4, "o clique não trocou o saco pelo diamante");
		im.clicked(37, 1, ContainerInput.PICKUP, fp);
		// Como item comum, só troca de lugar: a terra vem inteira para o cursor e o saco continua sem nada dentro.
		ensure(im.getCarried().is(Items.DIRT) && im.getCarried().getCount() == 10, "o saco puxou a terra para dentro: " + im.getCarried());
		ensure(SacoEnder.isBag(inv.getItem(1)) && !inv.getItem(1).has(DataComponents.BUNDLE_CONTENTS), "saco depois da troca: " + inv.getItem(1));
		im.setCarried(ItemStack.EMPTY);
		ok("o saco não guarda itens como trouxa");

		ender.clearContent();
		inv.clearContent();

		// 5. Resource pack: hash no .jar, modelo do saco e o papel do jogo continua papel quando não é o saco.
		ensure(SacoEnder.PACK_SHA1 != null, "hash do resource pack não está no .jar");
		ensure(SacoEnder.class.getResource("/assets/minecraft/items/bundle.json") != null && SacoEnder.class.getResource("/assets/minecraft/items/purple_bundle.json") != null, "definições das trouxas");
		ensure(SacoEnder.class.getResource("/assets/sacoender/models/item/saco.json") != null, "modelo do saco");
		ensure(SacoEnder.class.getResource("/assets/sacoender/font/gui.json") != null && SacoEnder.class.getResource("/assets/sacoender/textures/gui/ender_row.png") != null, "desenho dos espaços do saco");
		// Sem o pack (jogador que recusou), o título do baú do Ender continua o do jogo.
		var title = net.minecraft.network.chat.Component.translatable("container.enderchest");
		ensure(SacoEnder.enderTitle(title, fp) == title, "título mudou para quem não tem o pack");
		ok("resource pack");
	}

	private static List<ItemStack> snapshot(PlayerEnderChestContainer c, int from, int to) {
		List<ItemStack> out = new ArrayList<>();
		for (int i = from; i < to; i++) out.add(c.getItem(i).copy());
		return out;
	}

	private static boolean same(List<ItemStack> a, List<ItemStack> b) {
		if (a.size() != b.size()) return false;
		for (int i = 0; i < a.size(); i++) {
			if (!ItemStack.matches(a.get(i), b.get(i))) return false;
		}
		return true;
	}

	private static int count(Inventory inv, Item item) {
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item)) n += inv.getItem(i).getCount();
		return n;
	}

	private static ItemStack craft(MinecraftServer server, ServerLevel sl, CraftingInput input) {
		var r = server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, sl);
		ensure(r.isPresent(), "nenhuma receita para " + input);
		return r.get().value().assemble(input);
	}

	private static void ok(String what) {
		SacoEnder.LOG.info("SACOENDER teste OK: {}", what);
	}

	private static void ensure(boolean c, String what) {
		if (!c) throw new IllegalStateException("falhou: " + what);
	}
}
