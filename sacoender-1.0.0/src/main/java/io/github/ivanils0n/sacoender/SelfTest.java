package io.github.ivanils0n.sacoender;

import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
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

		// 1. Receita: trouxa vazia (de qualquer cor) no centro e 8 olhos do Ender em volta = o saco (igual ao item do código).
		ensure(server.getRecipeManager().getRecipes().stream().anyMatch(r -> r.id().toString().contains("sacoender:saco")), "receita não carregou");
		ItemStack e = new ItemStack(Items.ENDER_EYE);
		ItemStack bag = craft(server, sl, CraftingInput.of(3, 3, List.of(e, e, e, e, new ItemStack(Items.BUNDLE), e, e, e, e)));
		ensure(SacoEnder.isBag(bag), "o craft não deu o saco: " + bag);
		ensure(ItemStack.isSameItemSameComponents(bag, SacoEnder.bagItem()), "item da receita diferente do item do código: " + bag.getComponentsPatch() + " / " + SacoEnder.bagItem().getComponentsPatch());
		ensure(bag.getMaxStackSize() == 1, "o saco empilha");
		ensure(bag.getHoverName().getString().equals(SacoEnder.NAME), "nome: " + bag.getHoverName().getString());
		ensure(SacoEnder.isBag(craft(server, sl, CraftingInput.of(3, 3, List.of(e, e, e, e, new ItemStack(Items.PURPLE_BUNDLE), e, e, e, e)))), "trouxa colorida não serve");
		// Trouxa com itens dentro não serve (os itens se perderiam no craft).
		ItemStack full = new ItemStack(Items.BUNDLE);
		full.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(new ItemStack(Items.DIAMOND, 3))));
		ensure(server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(3, 3, List.of(e, e, e, e, full, e, e, e, e)), sl).isEmpty(), "trouxa com itens dentro virou saco");
		ItemStack o = new ItemStack(Items.OBSIDIAN);
		ItemStack chest = craft(server, sl, CraftingInput.of(3, 3, List.of(o, o, o, o, e, o, o, o, o)));
		ensure(chest.is(Items.ENDER_CHEST), "a receita do baú do fim mudou: " + chest);
		ensure(!SacoEnder.isBag(new ItemStack(Items.PAPER)), "papel comum foi aceito como saco");
		ok("receita (trouxa vazia + 8 olhos do Ender)");

		// 2. Sem o mod: o saco só tem componentes do jogo, é salvo como papel e volta igual.
		for (TypedDataComponent<?> c : bag.getComponents()) {
			var key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(c.type());
			ensure(key != null && key.getNamespace().equals("minecraft"), "componente fora do jogo: " + key);
		}
		var ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		Tag saved = ItemStack.CODEC.encodeStart(ops, bag).getOrThrow();
		ensure(saved.toString().contains("minecraft:paper"), "salvo como: " + saved);
		ItemStack loaded = ItemStack.CODEC.parse(ops, saved).getOrThrow();
		ensure(ItemStack.isSameItemSameComponents(loaded, bag) && SacoEnder.isBag(loaded), "não voltou igual depois de salvo: " + loaded);
		ok("item salvo só com dados do jogo (papel)");

		// 3. Botão direito: o saco abre, papel comum não.
		FakePlayer fp = FakePlayer.get(sl);
		fp.setItemInHand(InteractionHand.MAIN_HAND, bag.copy());
		ensure(SacoEnder.onUse(fp, sl, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS, "botão direito com o saco");
		fp.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.PAPER));
		ensure(SacoEnder.onUse(fp, sl, InteractionHand.OFF_HAND) == InteractionResult.PASS, "papel comum abriu a tela");
		fp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		fp.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		ok("botão direito");

		// 4. A tela mexe direto nos espaços 0-8 do baú do fim e nunca nos 9-26.
		PlayerEnderChestContainer ender = fp.getEnderChestInventory();
		Inventory inv = fp.getInventory();
		ender.clearContent();
		inv.clearContent();
		ender.setItem(0, new ItemStack(Items.DIAMOND, 5));
		ender.setItem(9, new ItemStack(Items.GOLD_INGOT, 3));
		ender.setItem(26, new ItemStack(Items.ELYTRA));
		List<ItemStack> hidden = snapshot(ender, 9, 27);
		BagMenu menu = new BagMenu(1, inv, fp);
		ensure(menu.slots.size() == 9 + 36, "espaços da tela: " + menu.slots.size());
		ensure(menu.getSlot(0).getItem() == ender.getItem(0) && menu.getSlot(0).getItem().getCount() == 5, "a tela não mostra o baú do fim");
		ensure(menu.stillValid(fp), "a tela fechou sozinha");

		menu.getSlot(4).set(new ItemStack(Items.TORCH, 16));
		ensure(ender.getItem(4).is(Items.TORCH) && ender.getItem(4).getCount() == 16, "item posto no saco não foi para o baú do fim");
		menu.getSlot(0).remove(2);
		ensure(ender.getItem(0).getCount() == 3, "item tirado do saco não saiu do baú do fim");
		ender.setItem(1, new ItemStack(Items.EMERALD, 7));
		ensure(menu.getSlot(1).getItem().is(Items.EMERALD), "item posto no baú do fim não aparece no saco");

		// Shift+clique do inventário (hotbar 0 = espaço 36 da tela) vai para o 1º espaço livre do saco.
		inv.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
		menu.quickMoveStack(fp, 36);
		ensure(ender.getItem(2).is(Items.COBBLESTONE) && ender.getItem(2).getCount() == 64 && inv.getItem(0).isEmpty(), "shift+clique para o saco: " + ender.getItem(2));
		// Saco cheio: o resto fica no inventário e o baú do fim (9-26) não muda.
		for (int i = 0; i < 9; i++) if (ender.getItem(i).isEmpty()) ender.setItem(i, new ItemStack(Items.DIRT, 64));
		inv.setItem(0, new ItemStack(Items.STONE, 64));
		menu.quickMoveStack(fp, 36);
		ensure(inv.getItem(0).is(Items.STONE) && inv.getItem(0).getCount() == 64, "com o saco cheio o item saiu do inventário");
		ensure(same(hidden, snapshot(ender, 9, 27)), "o saco mexeu nos espaços 9-26 do baú do fim");
		// Shift+clique do saco para o inventário.
		menu.quickMoveStack(fp, 0);
		ensure(ender.getItem(0).isEmpty(), "shift+clique para o inventário não tirou do baú do fim");
		ensure(count(inv, Items.DIAMOND) == 3, "diamantes no inventário: " + count(inv, Items.DIAMOND));
		ensure(same(hidden, snapshot(ender, 9, 27)), "o saco mexeu nos espaços 9-26 do baú do fim");
		menu.removed(fp);
		ok("tela ligada aos 9 primeiros espaços do baú do fim");

		ender.clearContent();
		inv.clearContent();

		// 5. Resource pack: hash no .jar, modelo do saco e o papel do jogo continua papel quando não é o saco.
		ensure(SacoEnder.PACK_SHA1 != null, "hash do resource pack não está no .jar");
		ensure(SacoEnder.class.getResource("/assets/minecraft/items/paper.json") != null, "assets/minecraft/items/paper.json");
		ensure(SacoEnder.class.getResource("/assets/sacoender/models/item/saco.json") != null, "modelo do saco");
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

	private static int count(Inventory inv, net.minecraft.world.item.Item item) {
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
