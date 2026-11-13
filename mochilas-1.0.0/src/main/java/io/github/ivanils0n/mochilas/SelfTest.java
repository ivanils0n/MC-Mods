package io.github.ivanils0n.mochilas;

import io.github.ivanils0n.mochilas.backpack.BackpackItem;
import io.github.ivanils0n.mochilas.backpack.Backpacks;
import io.github.ivanils0n.mochilas.backpack.Tier;
import io.github.ivanils0n.mochilas.config.MochilasConfig;
import io.github.ivanils0n.mochilas.equipment.Equipment;
import io.github.ivanils0n.mochilas.gui.BackpackGui;
import io.github.ivanils0n.mochilas.recipes.Recipes;
import io.github.ivanils0n.mochilas.storage.BackpackStorage;
import io.github.ivanils0n.mochilas.visual.Visuals;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.NonNullList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Só com -Dmochilas.selftest=true (CI): servidor dedicado de verdade.
 * 1ª vez: testa itens, receitas, melhoria preservando itens, inventário, trava, equipar/retirar e morte; grava uma
 * mochila no disco e pede reinício ("MOCHILAS SELFTEST RESTART"). 2ª vez: confere que ela voltou do disco.
 */
final class SelfTest {
	private SelfTest() {}

	/** Autoteste: outro "mod" tenta trocar o shader e uma textura das mochilas no resource pack. */
	static void simulateConflict() {
		eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils.RESOURCE_PACK_CREATION_EVENT.register(b -> {
			b.addData("assets/minecraft/shaders/core/item.vsh", "// shader de outro mod".getBytes(StandardCharsets.UTF_8));
			b.addData("assets/mochilas/textures/item/mochila_couro.png", new byte[]{1, 2, 3});
		});
		eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(b ->
				b.addData("assets/minecraft/shaders/core/entity.fsh", "// outro mod depois".getBytes(StandardCharsets.UTF_8)));
	}

	static void run(MinecraftServer server) {
		Path marker = server.getWorldPath(LevelResource.ROOT).resolve("mochilas-selftest.txt");
		try {
			if (Files.exists(marker)) {
				afterRestart(server, marker);
				Mochilas.LOG.info("MOCHILAS SELFTEST OK");
			} else {
				firstRun(server, marker);
				Mochilas.LOG.info("MOCHILAS SELFTEST RESTART");
			}
		} catch (Throwable t) {
			Mochilas.LOG.error("MOCHILAS SELFTEST FALHOU", t);
		}
		server.halt(false);
	}

	private static void firstRun(MinecraftServer server, Path marker) throws Exception {
		ServerLevel sl = server.overworld();
		BackpackStorage st = Mochilas.storage();

		// 1. Itens e capacidades
		int[] caps = {9, 18, 27, 54};
		for (Tier t : Tier.values()) {
			ensure(Backpacks.item(t) != null, "item " + t.id + " registrado");
			ensure(t.capacidade() == caps[t.ordinal()], "capacidade de " + t.id + ": " + t.capacidade());
			ensure(Backpacks.create(t).getMaxStackSize() == 1, "mochila não empilha");
		}
		ensure(MochilasConfig.get().capacidade(Tier.COURO) == 9, "config");
		ok("itens e capacidades (9/18/27/54)");

		// 2. Receitas carregadas
		for (String r : Recipes.IDS) {
			ensure(server.getRecipeManager().getRecipes().stream().anyMatch(h -> h.id().toString().contains(r)), "receita " + r);
		}
		ItemStack L = new ItemStack(Items.LEATHER), S = new ItemStack(Items.STRING), C = new ItemStack(Items.CHEST);
		ItemStack crafted = craft(server, sl, CraftingInput.of(3, 3, List.of(L, S, L, L, C, L, L, L, L)));
		ensure(Backpacks.tier(crafted) == Tier.COURO, "craft da mochila de couro: " + crafted);
		ok("receitas (couro + 3 melhorias)");

		// 3. Inventário guardado pelo ID + melhoria preservando os itens
		ItemStack bag = crafted.copy();
		UUID id = Backpacks.ensureId(bag);
		NonNullList<ItemStack> items = st.get(id, Tier.COURO.capacidade());
		items.set(0, new ItemStack(Items.DIAMOND, 7));
		items.set(8, new ItemStack(Items.TORCH, 64));
		st.markDirty(id);
		ItemStack[] blocks = {new ItemStack(Items.IRON_BLOCK), new ItemStack(Items.GOLD_BLOCK), new ItemStack(Items.DIAMOND_BLOCK)};
		for (int i = 0; i < 3; i++) {
			Tier from = Tier.values()[i], to = Tier.values()[i + 1];
			ItemStack up = craft(server, sl, CraftingInput.of(2, 1, List.of(bag, blocks[i])));
			ensure(Backpacks.tier(up) == to, "melhoria " + from.id + " -> " + to.id + ": " + up);
			ensure(id.equals(Backpacks.getId(up)), "a melhoria manteve o ID da mochila");
			bag = up;
		}
		NonNullList<ItemStack> big = st.get(id, Tier.DIAMANTE.capacidade());
		ensure(big.size() >= 54, "a lista cresceu para 54");
		ensure(big.get(0).is(Items.DIAMOND) && big.get(0).getCount() == 7 && big.get(8).getCount() == 64, "itens preservados após melhorar");
		ok("melhoria couro→ferro→ouro→diamante preservando os itens");

		// 4. Persistência no disco (relendo do arquivo)
		big.set(53, new ItemStack(Items.ELYTRA));
		st.markDirty(id);
		st.flush();
		ensure(Files.exists(st.file(id)), "arquivo da mochila gravado");
		st.dropCache(id);
		NonNullList<ItemStack> reread = st.get(id, 54);
		ensure(reread.get(0).getCount() == 7 && reread.get(53).is(Items.ELYTRA), "relido do disco");
		ok("gravação e leitura do disco");

		// 5. Regras da tela: mochila não entra em mochila
		SimpleContainer c = new SimpleContainer(9);
		BackpackGui.BackpackSlot slot = new BackpackGui.BackpackSlot(c, 0);
		ensure(!slot.mayPlace(Backpacks.create(Tier.COURO)) && !slot.mayPlace(bag), "espaço recusa mochila");
		ensure(slot.mayPlace(new ItemStack(Items.DIRT)), "espaço aceita itens comuns");
		ok("mochila dentro de mochila bloqueada");

		// 6. Trava contra acesso simultâneo
		UUID a = UUID.randomUUID(), b = UUID.randomUUID();
		ensure(st.tryOpen(id, a) && !st.tryOpen(id, b), "segundo jogador não abre a mesma mochila");
		st.close(id, a);
		ensure(st.tryOpen(id, b), "abre depois que o primeiro fecha");
		st.close(id, b);
		ok("trava de acesso simultâneo");

		// 7. Equipar com o botão direito, retirar, morrer
		FakePlayer fp = FakePlayer.get(sl);
		fp.setItemInHand(InteractionHand.MAIN_HAND, bag.copy());
		((BackpackItem) bag.getItem()).use(sl, fp, InteractionHand.MAIN_HAND);
		ensure(fp.getMainHandItem().isEmpty(), "a mochila saiu da mão");
		ensure(id.equals(Backpacks.getId(Equipment.equipped(fp))), "equipada com o mesmo ID");
		ensure(fp.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty(), "não ocupa o peitoral");
		ensure(Visuals.has(fp), "visual criado ao equipar");
		ensure(Equipment.unequip(fp), "retirar");
		ensure(!Equipment.hasEquipped(fp) && !Visuals.has(fp), "retirada some das costas");
		ItemStack back = ItemStack.EMPTY;
		for (int i = 0; i < fp.getInventory().getContainerSize(); i++) {
			if (Backpacks.isBackpack(fp.getInventory().getItem(i))) back = fp.getInventory().getItem(i);
		}
		ensure(id.equals(Backpacks.getId(back)), "voltou ao inventário com o mesmo ID");
		ensure(st.get(id, 54).get(0).getCount() == 7, "itens continuam na mochila retirada");
		Equipment.equip(fp, back.copy());
		Equipment.onDeath(fp);
		ensure(!Equipment.hasEquipped(fp), "ao morrer (sem keepInventory) a mochila cai");
		ok("equipar, retirar e morte");

		Files.writeString(marker, id.toString(), StandardCharsets.UTF_8);
	}

	private static void afterRestart(MinecraftServer server, Path marker) throws Exception {
		UUID id = UUID.fromString(Files.readString(marker, StandardCharsets.UTF_8).trim());
		NonNullList<ItemStack> items = Mochilas.storage().get(id, 54);
		ensure(items.get(0).is(Items.DIAMOND) && items.get(0).getCount() == 7, "diamantes após reiniciar");
		ensure(items.get(8).is(Items.TORCH) && items.get(8).getCount() == 64, "tochas após reiniciar");
		ensure(items.get(53).is(Items.ELYTRA), "élitro após reiniciar");
		Files.delete(marker);
		ok("itens da mochila voltaram depois de reiniciar o servidor");

		// Resource pack gerado pelo Polymer: os arquivos das mochilas têm que vencer os de "outro mod"
		Path zip = eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils.getMainPath();
		for (int i = 0; i < 30 && (!Files.exists(zip) || System.currentTimeMillis() - Files.getLastModifiedTime(zip).toMillis() < 1500); i++) {
			Thread.sleep(500); // o Polymer gera o pack em outra thread ao iniciar
		}
		ensure(Files.exists(zip), "resource pack gerado em " + zip);
		try (var fs = new java.util.zip.ZipFile(zip.toFile())) {
			String vsh = new String(fs.getInputStream(fs.getEntry("assets/minecraft/shaders/core/item.vsh")).readAllBytes(), StandardCharsets.UTF_8);
			String fsh = new String(fs.getInputStream(fs.getEntry("assets/minecraft/shaders/core/entity.fsh")).readAllBytes(), StandardCharsets.UTF_8);
			byte[] png = fs.getInputStream(fs.getEntry("assets/mochilas/textures/item/mochila_couro.png")).readAllBytes();
			ensure(vsh.contains("MOCHILAS") && fsh.contains("MOCHILAS"), "shaders das mochilas venceram os de outro mod");
			ensure(png.length > 100 && png[1] == 'P', "textura das mochilas venceu a de outro mod");
			ensure(fs.getEntry("assets/mochilas/items/mochila_diamante_dono.json") != null, "modelo do dono no pack");
		}
		ok("resource pack: texturas e shaders das mochilas prevalecem sobre outros mods");
	}

	private static ItemStack craft(MinecraftServer server, ServerLevel sl, CraftingInput input) {
		var r = server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, sl);
		ensure(r.isPresent(), "nenhuma receita para " + input);
		return r.get().value().assemble(input);
	}

	private static void ok(String what) {
		Mochilas.LOG.info("MOCHILAS teste OK: {}", what);
	}

	private static void ensure(boolean c, String what) {
		if (!c) throw new IllegalStateException("falhou: " + what);
	}
}
