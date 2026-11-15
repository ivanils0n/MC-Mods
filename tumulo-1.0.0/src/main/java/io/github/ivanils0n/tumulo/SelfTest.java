package io.github.ivanils0n.tumulo;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;

/** Só com -Dtumulo.selftest=true (CI): mata um jogador falso num servidor real, confere o túmulo e desliga. */
final class SelfTest {
	private SelfTest() {}

	static void run(MinecraftServer server) {
		try {
			check(server);
			Tumulo.LOG.info("TUMULO SELFTEST OK");
		} catch (Throwable t) {
			Tumulo.LOG.error("TUMULO SELFTEST FALHOU", t);
		}
		server.halt(false);
	}

	private static void check(MinecraftServer server) {
		ServerLevel sl = server.overworld();
		sl.setChunkForced(0, 0, true);
		BlockPos base = new BlockPos(3, 150, 3);
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				sl.setBlock(base.offset(dx, 0, dz), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
				for (int dy = 1; dy <= 3; dy++) sl.setBlock(base.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		ensure(Tumulo.store != null, "arquivo de túmulos não carregou");

		// 1. Morte: os itens vão para o túmulo e nada cai no chão (só a "mochila" de outro mod, que também entra).
		FakePlayer fp = FakePlayer.get(sl);
		Grave g = die(server, sl, fp, 3.5, 152.7, 3.5, true);
		ensure(Math.abs(g.y - 151.0) < 1e-6, "o corpo não ficou no chão: y=" + g.y);
		ensure(g.items.getItem(27).is(Items.DIAMOND_SWORD) && g.items.getItem(32).is(Items.DIRT) && g.items.getItem(32).getCount() == 32, "hotbar");
		ensure(g.items.getItem(11).is(Items.GOLD_INGOT) && g.items.getItem(11).getCount() == 7, "inventário");
		ensure(g.items.getItem(Grave.HEAD).is(Items.DIAMOND_HELMET) && g.items.getItem(Grave.FEET).is(Items.IRON_BOOTS), "armadura");
		ensure(g.items.getItem(Grave.OFFHAND).is(Items.SHIELD), "mão secundária");
		ensure(g.items.getItem(Grave.EXTRA[0]).is(Items.SADDLE), "item extra (mochila de outro mod)");
		ensure(g.mainhand == 27, "item da mão: " + g.mainhand);
		ensure(sl.getEntitiesOfClass(ItemEntity.class, new AABB(base).inflate(4)).isEmpty(), "itens caíram no chão");
		ok("morte guarda tudo no túmulo");

		// 2. Corpo deitado com a armadura e a área clicável.
		ensure(Tumulo.body(g.id) instanceof LivingEntity, "corpo não apareceu: " + Tumulo.body(g.id));
		LivingEntity body = (LivingEntity) Tumulo.body(g.id);
		ensure(body.getPose() == Pose.SLEEPING, "corpo não está deitado: " + body.getPose());
		ensure(body.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET) && body.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.DIAMOND_SWORD), "armadura do corpo");
		ensure(body.isInvulnerable() && body.isNoGravity(), "corpo pode apanhar ou cair");
		ensure(body.getCustomName() != null && body.getCustomName().getString().startsWith("Túmulo de "), "nome: " + body.getCustomName());
		ensure(Tumulo.click(g.id) instanceof Interaction, "área clicável não apareceu");
		ok("corpo deitado (" + body.getType() + ")");

		// 3. Arquivo: gravado e lido de volta igual.
		GraveStore reloaded = GraveStore.load(server.getWorldPath(LevelResource.ROOT).resolve("tumulo.json"), server.registryAccess());
		Grave r = reloaded.get(g.id);
		ensure(r != null && r.dim.equals("minecraft:overworld") && r.mainhand == 27, "túmulo não foi gravado");
		for (int i = 0; i < Grave.SIZE; i++) ensure(ItemStack.matches(r.items.getItem(i), g.items.getItem(i)), "item " + i + " mudou no arquivo");
		ok("arquivo tumulo.json");

		// 4. Outro jogador: clique abre, a tela só deixa tirar, shift+clique pega tudo e o túmulo some vazio.
		FakePlayer looter = FakePlayer.get(sl, new GameProfile(UUID.randomUUID(), "Saqueador"));
		looter.snapTo(3.5, 151, 5.5, 180f, 0f);
		looter.getInventory().clearContent();
		InteractionResult res = UseEntityCallback.EVENT.invoker().interact(looter, sl, InteractionHand.MAIN_HAND, Tumulo.click(g.id), null);
		ensure(res == InteractionResult.SUCCESS, "clique no corpo: " + res);
		GraveMenu menu = new GraveMenu(1, looter.getInventory(), g);
		ensure(menu.slots.size() == 54 + 36 && menu.stillValid(looter), "tela");
		ensure(!menu.getSlot(0).mayPlace(new ItemStack(Items.DIRT)), "dá para guardar itens no corpo");
		menu.quickMoveStack(looter, 11);
		ensure(g.items.getItem(11).isEmpty() && count(looter.getInventory(), Items.GOLD_INGOT) == 7, "shift+clique na tela");
		menu.quickMoveStack(looter, 54 + 27);
		Tumulo.processChanges();
		ensure(Tumulo.store.get(g.id) == g, "túmulo sumiu com itens dentro");
		Tumulo.takeAll(looter, g);
		ensure(looter.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET) && looter.getInventory().getItem(0).is(Items.DIAMOND_SWORD), "pegar tudo não devolveu aos mesmos lugares");
		ensure(count(looter.getInventory(), Items.SADDLE) == 1, "item extra não veio");
		Tumulo.processChanges();
		ensure(Tumulo.store.get(g.id) == null, "túmulo vazio não sumiu");
		ensure(Tumulo.body(g.id) == null && entities(sl, g).isEmpty(), "corpo vazio não sumiu");
		ok("pegar itens e corpo some vazio");

		// 5. Tempo: vencido, o corpo some com os itens (padrão) ou solta os itens (config).
		Grave a = die(server, sl, fp, 1.5, 151, 1.5, false);
		a.expires = sl.getGameTime() - 1;
		Tumulo.second(server);
		ensure(Tumulo.store.get(a.id) == null && entities(sl, a).isEmpty(), "túmulo vencido não sumiu");
		ensure(sl.getEntitiesOfClass(ItemEntity.class, new AABB(base).inflate(4)).isEmpty(), "itens caíram sem a config");
		Tumulo.config.soltarItensAoSumir = true;
		Grave b = die(server, sl, fp, 5.5, 151, 5.5, false);
		b.expires = sl.getGameTime() - 1;
		Tumulo.second(server);
		ensure(Tumulo.store.get(b.id) == null, "túmulo vencido (soltar) não sumiu");
		List<ItemEntity> dropped = sl.getEntitiesOfClass(ItemEntity.class, new AABB(base).inflate(4));
		ensure(dropped.size() == 6, "itens soltos ao sumir: " + dropped.size());
		for (ItemEntity e : dropped) e.discard();
		Tumulo.config.soltarItensAoSumir = false;
		ok("tempo");

		// 6. /kill no corpo: os itens caem no chão (nada se perde) e o corpo não deixa cópias da armadura.
		Grave k = die(server, sl, fp, 3.5, 151, 1.5, false);
		Entity kb = Tumulo.body(k.id);
		kb.kill(sl);
		ensure(Tumulo.store.get(k.id) == null && entities(sl, k).isEmpty(), "corpo morto não acabou com o túmulo");
		dropped = sl.getEntitiesOfClass(ItemEntity.class, new AABB(base).inflate(4));
		ensure(dropped.size() == 6, "itens soltos com /kill: " + dropped.size());
		for (ItemEntity e : dropped) e.discard();
		ok("/kill no corpo");

		// 7. Sem itens: nenhum túmulo.
		int before = Tumulo.store.all().size();
		fp.getInventory().clearContent();
		fp.setHealth(0f);
		fp.getInventory().dropAll();
		fp.setHealth(20f);
		Tumulo.processPending(server);
		ensure(Tumulo.store.all().size() == before, "túmulo vazio foi criado");
		ok("morte sem itens");
		sl.setChunkForced(0, 0, false);
	}

	/** Dá itens ao jogador falso, "morre" (o mesmo dropAll do jogo) e monta o túmulo. */
	private static Grave die(MinecraftServer server, ServerLevel sl, FakePlayer fp, double x, double y, double z, boolean extra) {
		fp.snapTo(x, y, z, 90f, 0f);
		Inventory inv = fp.getInventory();
		inv.clearContent();
		inv.setItem(0, new ItemStack(Items.DIAMOND_SWORD));
		inv.setItem(5, new ItemStack(Items.DIRT, 32));
		inv.setItem(20, new ItemStack(Items.GOLD_INGOT, 7));
		inv.setItem(39, new ItemStack(Items.DIAMOND_HELMET));
		inv.setItem(36, new ItemStack(Items.IRON_BOOTS));
		inv.setItem(40, new ItemStack(Items.SHIELD));
		ensure(fp.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET), "espaço 39 não é a cabeça");
		if (extra) sl.addFreshEntity(new ItemEntity(sl, x, y + 0.5, z, new ItemStack(Items.SADDLE)));
		int before = Tumulo.store.all().size();
		fp.setHealth(0f);
		inv.dropAll();
		fp.setHealth(20f);
		for (int i = 0; i < inv.getContainerSize(); i++) ensure(inv.getItem(i).isEmpty(), "ficou item no inventário: " + i);
		Tumulo.processPending(server);
		ensure(Tumulo.store.all().size() == before + 1, "túmulo não foi criado");
		Grave g = null;
		for (Grave t : Tumulo.store.all()) g = t;
		return g;
	}

	private static List<Entity> entities(ServerLevel sl, Grave g) {
		return sl.getEntitiesOfClass(Entity.class, new AABB(g.x - 3, g.y - 3, g.z - 3, g.x + 3, g.y + 3, g.z + 3), e -> e.entityTags().contains(g.tag()) && e.isAlive());
	}

	private static int count(Inventory inv, net.minecraft.world.item.Item item) {
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item)) n += inv.getItem(i).getCount();
		return n;
	}

	private static void ok(String what) {
		Tumulo.LOG.info("TUMULO teste OK: {}", what);
	}

	private static void ensure(boolean c, String what) {
		if (!c) throw new IllegalStateException("falhou: " + what);
	}
}
