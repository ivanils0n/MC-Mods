package io.github.ivanils0n.coppergolem;

import io.github.ivanils0n.coppergolem.Links.Link;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;

/** Só com -Dcoppergolem.selftest=true (CI): servidor real, golem de verdade, baús de verdade. */
final class SelfTest {
	private SelfTest() {}

	static void run(MinecraftServer server) {
		try {
			check(server);
			GolemMod.LOG.info("COPPERGOLEM SELFTEST OK");
		} catch (Throwable t) {
			GolemMod.LOG.error("COPPERGOLEM SELFTEST FALHOU", t);
		}
		server.halt(false);
	}

	private static void check(MinecraftServer server) throws Exception {
		ensure(server.getRecipeManager().getRecipes().stream().anyMatch(r -> r.id().toString().contains("coppergolem:wand")), "receita do cajado");
		ensure(GolemMod.isWand(GolemMod.wandItem()) && !GolemMod.isWand(new ItemStack(Items.WARPED_FUNGUS_ON_A_STICK)), "item do cajado");
		ensure(GolemMod.PACK_SHA1 != null, "hash do resource pack não está no .jar");
		ensure(GolemMod.themedTitle("", "x").getString().endsWith("x"), "título com fundo");

		ServerLevel sl = server.overworld();
		BlockPos o = new BlockPos(0, 200, 0);
		BlockPos src = o.east(3), dst = o.west(3), dst2 = o.north(3), refill = o.south(3);
		for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) {
			sl.setBlock(o.offset(x, -1, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
			for (int y = 0; y < 3; y++) sl.setBlock(o.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
		sl.setBlock(src, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
		sl.setBlock(dst, Blocks.BARREL.defaultBlockState(), Block.UPDATE_ALL);
		sl.setBlock(dst2, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
		sl.setBlock(refill, BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("minecraft", "copper_chest")).defaultBlockState(), Block.UPDATE_ALL);
		ensure(Boxes.isCopperChest(sl, refill) && !Boxes.isCopperChest(sl, src) && Boxes.isBox(sl.getBlockEntity(dst)), "tipos de baú");
		BaseContainerBlockEntity s = (BaseContainerBlockEntity) sl.getBlockEntity(src);
		s.setItem(0, new ItemStack(Items.DIAMOND, 5));
		((BaseContainerBlockEntity) sl.getBlockEntity(refill)).setItem(0, new ItemStack(Items.COPPER_INGOT, 10));

		@SuppressWarnings("unchecked")
		EntityType<CopperGolem> type = (EntityType<CopperGolem>) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath("minecraft", "copper_golem"));
		CopperGolem g = type.spawn(sl, o.above(), EntitySpawnReason.COMMAND);
		ensure(g != null, "golem não nasceu");
		Link l = GolemMod.links.getOrCreate(g.getUUID(), GolemMod.dim(sl));
		l.refill = Link.arr(refill);
		l.sources.add(Link.arr(src));
		l.dests.add(Link.arr(dst));
		l.dests.add(Link.arr(dst2));
		// Clique do jogador com o cajado no golem: tem que ligar o cajado a ele e abrir a tela.
		FakePlayer fp = FakePlayer.get(sl);
		fp.setItemInHand(InteractionHand.MAIN_HAND, GolemMod.wandItem());
		InteractionResult r = UseEntityCallback.EVENT.invoker().interact(fp, sl, InteractionHand.MAIN_HAND, g, null);
		ensure(r == InteractionResult.SUCCESS, "clique com o cajado no golem: " + r);
		ensure(g.getUUID().equals(GolemMod.golemOf(fp.getMainHandItem())), "cajado não ficou ligado ao golem");
		ensure(g.mobInteract(fp, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS, "mobInteract do golem com o cajado");
		GolemMod.links.remove(g.getUUID());
		Component far = GolemMod.link(fp, sl, fp.getMainHandItem(), src, GolemMod.Role.SOURCE);
		ensure(GolemMod.links.get(g.getUUID()) == null || GolemMod.links.get(g.getUUID()).sources.isEmpty(), "origem sem baú de recarga: " + far.getString());
		Component msg = GolemMod.link(fp, sl, fp.getMainHandItem(), refill, GolemMod.Role.REFILL);
		ensure(GolemMod.links.get(g.getUUID()) != null && GolemMod.links.get(g.getUUID()).refill != null, "baú de recarga não ligou pelo cajado: " + msg.getString());
		GolemMod.link(fp, sl, fp.getMainHandItem(), src, GolemMod.Role.SOURCE);
		GolemMod.link(fp, sl, fp.getMainHandItem(), dst, GolemMod.Role.DEST);
		Link chosen = GolemMod.links.get(g.getUUID());
		ensure(chosen.sources.size() == 1 && chosen.dests.size() == 1, "escolha de origem/guardar");
		GolemMod.link(fp, sl, fp.getMainHandItem(), dst, GolemMod.Role.SOURCE);
		ensure(chosen.sources.size() == 2 && chosen.dests.isEmpty(), "mudar o papel do baú");
		GolemMod.link(fp, sl, fp.getMainHandItem(), dst, GolemMod.Role.NONE);
		ensure(chosen.sources.size() == 1, "desligar baú");
		BlockPos away = refill.east(40);
		sl.setBlock(away, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
		GolemMod.link(fp, sl, fp.getMainHandItem(), away, GolemMod.Role.DEST);
		ensure(chosen.dests.isEmpty(), "baú a mais de 33 blocos não pode ligar");
		sl.setBlock(away, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		GolemMod.links.remove(g.getUUID());
		l = GolemMod.links.getOrCreate(g.getUUID(), GolemMod.dim(sl));
		l.refill = Link.arr(refill);
		l.sources.add(Link.arr(src));
		l.dests.add(Link.arr(dst));
		l.dests.add(Link.arr(dst2));
		GolemTask t = new GolemTask();
		t.teleportForTest = true;
		for (int i = 0; i < 400; i++) t.step(sl, g, l);
		ensure(Boxes.count(Boxes.containers(sl, dst), new ItemStack(Items.DIAMOND)) + Boxes.count(Boxes.containers(sl, dst2), new ItemStack(Items.DIAMOND)) == 5
			&& Boxes.count(Boxes.containers(sl, src), new ItemStack(Items.DIAMOND)) == 0, "golem não moveu os diamantes");

		g.setWeatherState(WeatheringCopper.WeatherState.WEATHERED);
		for (int i = 0; i < 400; i++) t.step(sl, g, l);
		ensure(g.getWeatherState() == WeatheringCopper.WeatherState.UNAFFECTED, "golem não se recarregou: " + g.getWeatherState());
		ensure(Boxes.count(Boxes.containers(sl, refill), new ItemStack(Items.COPPER_INGOT)) == 8, "2 níveis de ferrugem = 2 barras");

		// barra de cobre na mão: um nível a menos por clique
		g.setWeatherState(WeatheringCopper.WeatherState.OXIDIZED);
		fp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COPPER_INGOT, 5));
		ensure(GolemMod.useOnGolem(fp, InteractionHand.MAIN_HAND, g) == InteractionResult.SUCCESS, "barra de cobre no golem");
		ensure(g.getWeatherState() == WeatheringCopper.WeatherState.WEATHERED && fp.getMainHandItem().getCount() == 4, "barra de cobre tira um nível");

		t.release(sl, g); // devolve o cérebro do jogo sem erro

		// guardar o golem no inventário e soltar de novo: mesmo golem, mesma ferrugem, mesmos baús
		java.util.UUID id = g.getUUID();
		GolemMod.links.remove(id);
		Link keep = GolemMod.links.getOrCreate(id, GolemMod.dim(sl));
		keep.refill = Link.arr(refill);
		keep.dests.add(Link.arr(dst));
		g.setWeatherState(WeatheringCopper.WeatherState.EXPOSED);
		ItemStack stash = Stash.collect(fp, g);
		ensure(Stash.isStash(stash) && g.isRemoved() && GolemMod.links.get(id) == null, "guardar o golem");
		CopperGolem back = Stash.place(fp, sl, stash, o.above());
		ensure(back != null && back.getUUID().equals(id) && back.getWeatherState() == WeatheringCopper.WeatherState.EXPOSED, "soltar o golem");
		ensure(GolemMod.links.get(id) != null && GolemMod.links.get(id).dests.size() == 1 && stash.isEmpty(), "vínculos do golem solto");
		g = back;
		GolemMod.links.remove(g.getUUID());
		g.discard();
	}

	private static void ensure(boolean ok, String msg) {
		if (!ok) throw new IllegalStateException(msg);
	}
}
