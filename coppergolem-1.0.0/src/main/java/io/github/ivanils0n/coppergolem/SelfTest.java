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
		Component msg = GolemMod.link(fp, sl, fp.getMainHandItem(), refill, true);
		ensure(GolemMod.links.get(g.getUUID()) != null && GolemMod.links.get(g.getUUID()).refill != null, "baú de recarga não ligou pelo cajado: " + msg.getString());
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

		g.setWeatherState(WeatheringCopper.WeatherState.EXPOSED);
		for (int i = 0; i < 400; i++) t.step(sl, g, l);
		ensure(g.getWeatherState() == WeatheringCopper.WeatherState.UNAFFECTED, "golem não se recarregou: " + g.getWeatherState());
		ensure(Boxes.count(Boxes.containers(sl, refill), new ItemStack(Items.COPPER_INGOT)) < 10, "barras de cobre não foram gastas");

		GolemMod.links.remove(g.getUUID());
		g.discard();
	}

	private static void ensure(boolean ok, String msg) {
		if (!ok) throw new IllegalStateException(msg);
	}
}
