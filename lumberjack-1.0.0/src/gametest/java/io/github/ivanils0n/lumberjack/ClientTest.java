package io.github.ivanils0n.lumberjack;

import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Só no CI (runClientGameTest com xvfb). Abre o jogo de verdade em singleplayer (servidor interno) e usa o mouse de
 * verdade: corta uma árvore com o machado (cai inteira), coloca um tronco com o botão direito e confere que, cortando
 * esse tronco, os de cima não caem. Tira prints de cada etapa.
 */
public class ClientTest implements FabricClientGameTest {
	private static int X, Y, Z;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			context.waitTicks(100);
			cmd(sp, "time set 6000");
			cmd(sp, "weather clear");
			cmd(sp, "gamemode survival @p");
			sp.getServer().runOnServer(s -> {
				ServerPlayer p = player(s);
				X = (int) Math.floor(p.getX());
				Y = (int) Math.floor(p.getY());
				Z = (int) Math.floor(p.getZ());
			});
			cmd(sp, f("fill %d %d %d %d %d %d air", X - 6, Y, Z - 6, X + 6, Y + 10, Z + 6));
			cmd(sp, f("fill %d %d %d %d %d %d grass_block", X - 6, Y - 1, Z - 6, X + 6, Y - 1, Z + 6));
			cmd(sp, f("tp @p %d.5 %d %d.5", X, Y, Z));

			// 1. Árvore natural: segurando o clique com o machado, ela cai inteira.
			int tx = X + 2;
			cmd(sp, f("fill %d %d %d %d %d %d oak_log", tx, Y, Z, tx, Y + 5, Z));
			cmd(sp, f("fill %d %d %d %d %d %d oak_leaves[persistent=false] keep", tx - 2, Y + 3, Z - 2, tx + 2, Y + 6, Z + 2));
			hand(sp, new ItemStack(Items.DIAMOND_AXE));
			look(context, sp, tx, Y, Z);
			context.takeScreenshot("0_arvore");
			mine(context, new BlockPos(tx, Y, Z));
			context.waitTicks(10);
			context.takeScreenshot("1_arvore_cortada");
			int left = sp.getServer().computeOnServer(s -> {
				int n = 0;
				for (int y = 0; y <= 5; y++) if (player(s).level().getBlockState(new BlockPos(tx, Y + y, Z)).is(Blocks.OAK_LOG)) n++;
				return n;
			});
			if (left != 0) throw new AssertionError("a árvore não caiu inteira: sobraram " + left + " troncos");
			cmd(sp, "kill @e[type=item]");
			cmd(sp, f("fill %d %d %d %d %d %d air", X - 6, Y, Z - 6, X + 6, Y + 10, Z + 6));

			// 2. Tronco colocado com o botão direito (de verdade): fica marcado.
			hand(sp, new ItemStack(Items.OAK_LOG, 8));
			look(context, sp, tx, Y - 1, Z);
			context.getInput().holdKey(o -> o.keyUse);
			context.waitTicks(2);
			context.getInput().releaseKey(o -> o.keyUse);
			context.waitTicks(10);
			boolean marked = sp.getServer().computeOnServer(s -> {
				var lvl = player(s).level();
				return lvl.getBlockState(new BlockPos(tx, Y, Z)).is(Blocks.OAK_LOG) && Lumberjack.placed.contains(lvl, new BlockPos(tx, Y, Z));
			});
			if (!marked) throw new AssertionError("o tronco colocado com o botão direito não foi marcado");

			// Em cima dele, troncos e folhas naturais: cortando o colocado, os de cima não caem.
			cmd(sp, f("fill %d %d %d %d %d %d oak_log", tx, Y + 1, Z, tx, Y + 4, Z));
			cmd(sp, f("fill %d %d %d %d %d %d oak_leaves[persistent=false] keep", tx - 2, Y + 3, Z - 2, tx + 2, Y + 5, Z + 2));
			hand(sp, new ItemStack(Items.DIAMOND_AXE));
			look(context, sp, tx, Y, Z);
			context.takeScreenshot("2_tronco_colocado");
			mine(context, new BlockPos(tx, Y, Z));
			context.waitTicks(10);
			context.takeScreenshot("3_so_o_colocado_saiu");
			boolean above = sp.getServer().computeOnServer(s -> player(s).level().getBlockState(new BlockPos(tx, Y + 1, Z)).is(Blocks.OAK_LOG));
			if (!above) throw new AssertionError("cortar o tronco colocado derrubou os de cima");
		}
	}

	/** Segura o clique esquerdo até o bloco sumir (no máximo 5 s). */
	private static void mine(ClientGameTestContext context, BlockPos pos) {
		context.getInput().holdKey(o -> o.keyAttack);
		for (int i = 0; i < 100; i++) {
			context.waitTicks(1);
			if (context.computeOnClient(c -> c.level.getBlockState(pos).isAir())) break;
		}
		context.getInput().releaseKey(o -> o.keyAttack);
	}

	/**
	 * Olha, a partir dos olhos, para o meio do bloco (ou, se for o chão, para o topo dele) e espera o cliente virar.
	 * (O "tp ... facing" mira a partir dos pés, por isso o ângulo é calculado aqui.)
	 */
	private static void look(ClientGameTestContext context, TestSingleplayerContext sp, int bx, int by, int bz) {
		double dx = bx - X, dz = bz - Z;
		double dy = (by < Y ? by + 0.99 : by + 0.5) - (Y + 1.62);
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		cmd(sp, f("tp @p %d.5 %d %d.5 %.2f %.2f", X, Y, Z, yaw, pitch));
		context.waitTicks(10);
	}

	private static void hand(TestSingleplayerContext sp, ItemStack stack) {
		sp.getServer().runOnServer(s -> player(s).setItemInHand(InteractionHand.MAIN_HAND, stack));
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().get(0);
	}

	private static String f(String fmt, Object... args) {
		return String.format(Locale.ROOT, fmt, args);
	}

	private static void cmd(TestSingleplayerContext sp, String c) {
		sp.getServer().runCommand(c);
	}
}
