package io.github.ivanils0n.automato;

import io.github.ivanils0n.automato.MachineStore.Machine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.storage.LevelResource;

/** Roda só com -Dautomato.selftest=true (CI): monta um Autômato, quebra uma pedra, golpeia um porco e desliga. */
final class SelfTest {
	private SelfTest() {}

	static void run(MinecraftServer server) {
		try {
			check(server);
			Automato.LOG.info("AUTOMATO SELFTEST OK");
		} catch (Throwable t) {
			Automato.LOG.error("AUTOMATO SELFTEST FALHOU", t);
		}
		server.halt(false);
	}

	private static void check(MinecraftServer server) {
		ensure(server.getRecipeManager().getRecipes().stream().anyMatch(r -> r.id().toString().contains("automato:automato")), "receita não carregou");
		ensure(Automato.isMachineItem(Automato.machineItem()), "item do Autômato não é reconhecido");
		ensure(!Automato.isMachineItem(new ItemStack(Items.DISPENSER)), "ejetor comum foi aceito como Autômato");
		ensure(Worker.attackDelay(new ItemStack(Items.DIAMOND_SWORD)) == 12, "recarga da espada: " + Worker.attackDelay(new ItemStack(Items.DIAMOND_SWORD)));
		ensure(Worker.attackDelay(new ItemStack(Items.IRON_AXE)) == 22, "recarga do machado: " + Worker.attackDelay(new ItemStack(Items.IRON_AXE)));
		ensure(Worker.isWeapon(new ItemStack(Items.DIAMOND_SWORD)), "espada não é arma");
		ensure(Worker.isWeapon(new ItemStack(Items.MACE)), "maça não é arma");
		ensure(!Worker.isWeapon(new ItemStack(Items.DIAMOND_PICKAXE)) && Worker.isUsable(new ItemStack(Items.DIAMOND_PICKAXE)), "picareta");
		ensure(!Worker.isUsable(new ItemStack(Items.DIRT)), "terra foi aceita como ferramenta");
		// Eficiência acelera a quebra (Fortuna, Pilhagem etc. vêm do próprio jogo, pelo jogador falso com a ferramenta).
		ItemStack fast = new ItemStack(Items.DIAMOND_PICKAXE);
		fast.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY), 5);
		float slow = Worker.progressPerTick(server.overworld(), new ItemStack(Items.DIAMOND_PICKAXE), Blocks.STONE.defaultBlockState(), BlockPos.ZERO);
		ensure(Worker.progressPerTick(server.overworld(), fast, Blocks.STONE.defaultBlockState(), BlockPos.ZERO) > slow * 3, "Eficiência não acelera");
		ensure(Automato.PACK_SHA1 != null, "hash do resource pack não está no .jar");

		ServerLevel sl = server.overworld();
		sl.setChunkForced(0, 0, true);
		BlockPos pos = new BlockPos(0, 200, 0);
		for (int i = 0; i <= 5; i++) sl.setBlock(pos.east(i), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		Machine m = Automato.placeMachine(sl, pos, Direction.EAST, Automato.machineItem());
		ensure(m != null && Automato.isMachine(sl, pos), "Autômato não foi colocado");
		ensure(Automato.visuals(sl, pos).size() == 2, "visual: " + Automato.visuals(sl, pos).size());
		ensure(!Automato.visuals(sl, pos).get(0).shouldBeSaved(), "visual seria salvo no mapa");
		DispenserBlockEntity be = (DispenserBlockEntity) sl.getBlockEntity(pos);

		ensure(!be.canPlaceItem(0, new ItemStack(Items.DIRT)) && be.canPlaceItem(0, new ItemStack(Items.IRON_SHOVEL)), "funil poderia pôr terra no Autômato");

		// Picareta: com alcance 1 não alcança a pedra a 2 blocos; com alcance 2 quebra (pulando o ar), gastando durabilidade.
		sl.setBlock(pos.east(2), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
		be.setItem(4, new ItemStack(Items.DIAMOND_PICKAXE));
		for (int t = 0; t < 10; t++) Worker.tick(sl, m, sl.getBlockState(pos), be, Direction.EAST, false);
		ensure(sl.getBlockState(pos.east(2)).is(Blocks.STONE), "quebrou sem redstone");
		ensure(m.range == 1, "alcance inicial: " + m.range);
		for (int t = 0; t < 20; t++) Worker.tick(sl, m, sl.getBlockState(pos), be, Direction.EAST, true);
		ensure(sl.getBlockState(pos.east(2)).is(Blocks.STONE), "quebrou além do alcance 1");
		m.range = 2;
		int ticks = 0;
		while (sl.getBlockState(pos.east(2)).is(Blocks.STONE) && ticks++ < 60) Worker.tick(sl, m, sl.getBlockState(pos), be, Direction.EAST, true);
		ensure(!sl.getBlockState(pos.east(2)).is(Blocks.STONE), "a pedra não quebrou");
		ensure(ticks >= 5 && ticks <= 10, "picareta de diamante deveria levar ~6 ticks na pedra, levou " + ticks);
		ensure(be.getItem(4).is(Items.DIAMOND_PICKAXE) && be.getItem(4).getDamageValue() == 1, "durabilidade da picareta: " + be.getItem(4));

		// Espada: golpeia o porco só quando a recarga enche.
		be.setItem(4, ItemStack.EMPTY);
		be.setItem(0, new ItemStack(Items.DIAMOND_SWORD));
		Mob pig = (Mob) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath("minecraft", "pig")).create(sl, EntitySpawnReason.COMMAND);
		ensure(pig != null, "porco não foi criado");
		pig.setPos(pos.getX() + 1.5, pos.getY(), pos.getZ() + 0.5);
		pig.setNoAi(true);
		sl.addFreshEntity(pig);
		ensure(Worker.findTarget(sl, pos, Direction.EAST) == pig, "não achou o porco à frente");
		for (int t = 0; t < 11; t++) Worker.tick(sl, m, sl.getBlockState(pos), be, Direction.EAST, true);
		ensure(pig.getHealth() == pig.getMaxHealth(), "golpeou antes da recarga");
		for (int t = 0; t < 3; t++) Worker.tick(sl, m, sl.getBlockState(pos), be, Direction.EAST, true);
		ensure(pig.getHealth() < pig.getMaxHealth() || !pig.isAlive(), "o porco não levou o golpe");
		ensure(be.getItem(0).getDamageValue() == 1, "durabilidade da espada: " + be.getItem(0));
		pig.discard();

		// Pote de XP: bolinha à frente vai para o pote; fora da área fica no chão.
		ensure(Xp.levels(Xp.pointsForLevel(30)) == 30 && Xp.MAX == Xp.pointsForLevel(100), "conta de níveis");
		ExperienceOrb near = new ExperienceOrb(sl, pos.getX() + 2.5, pos.getY() + 0.5, pos.getZ() + 0.5, 7);
		ExperienceOrb far = new ExperienceOrb(sl, pos.getX() - 4.5, pos.getY() + 0.5, pos.getZ() + 0.5, 5);
		sl.addFreshEntity(near);
		sl.addFreshEntity(far);
		int before = m.xp;
		Xp.collectOrbs(sl, m, Direction.EAST, be);
		ensure(m.xp == before + 7 && near.isRemoved() && !far.isRemoved(), "pote de XP: " + m.xp + " (antes " + before + ")");
		far.discard();

		Automato.store.save();
		MachineStore reloaded = MachineStore.load(server.getWorldPath(LevelResource.ROOT).resolve("automato.json"));
		ensure(reloaded.get(Automato.dim(sl), pos) != null, "Autômato não foi salvo no arquivo");

		Automato.removeMachine(sl, m);
		ensure(!Automato.isMachine(sl, pos) && Automato.visuals(sl, pos).isEmpty(), "Autômato não foi removido");
		be.clearContent();
		for (int i = 0; i <= 5; i++) sl.setBlock(pos.east(i), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		sl.setChunkForced(0, 0, false);
	}

	private static void ensure(boolean ok, String what) {
		if (!ok) throw new IllegalStateException(what);
	}
}
