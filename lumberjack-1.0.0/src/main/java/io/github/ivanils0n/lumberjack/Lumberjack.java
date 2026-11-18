package io.github.ivanils0n.lumberjack;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lenhador: quebrar um tronco de árvore (no modo sobrevivência, com machado, sem agachar) derruba todos os troncos
 * do mesmo tipo ligados a ele. Troncos colocados por jogadores são anotados (mixin no BlockItem) e nunca caem em
 * cadeia, nem servem de ponte. Cada tronco é quebrado pelo próprio jogador (gameMode.destroyBlock): durabilidade,
 * encantamentos, estatísticas, fome e mods de proteção de terreno valem como num corte normal.
 * Só no servidor: no multiplayer os jogadores não instalam nada; no singleplayer roda no servidor interno do jogo.
 */
public final class Lumberjack implements ModInitializer {
	static final Logger LOG = LoggerFactory.getLogger("lumberjack");
	static final boolean SELFTEST = Boolean.getBoolean("lumberjack.selftest");

	static LumberjackConfig config = new LumberjackConfig();
	static PlacedLogs placed;

	/** Enquanto derruba uma árvore: os blocos quebrados em cadeia não começam outra cadeia e os drops são juntados. */
	private static boolean chaining;
	private static final List<Entity> DROPS = new ArrayList<>();
	private static int saveTimer;

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(s -> {
			config = LumberjackConfig.load();
			placed = PlacedLogs.load(s.getWorldPath(LevelResource.ROOT).resolve("lumberjack.dat"));
			LOG.info("Lenhador: {} troncos colocados por jogadores", placed.size());
			if (SELFTEST) SelfTest.run(s);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
			if (placed != null) placed.flush();
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			placed = null;
			chaining = false;
			DROPS.clear();
		});
		// Grava as marcações no máximo 1 vez por minuto (só se mudou).
		ServerTickEvents.END_SERVER_TICK.register(s -> {
			if (++saveTimer < 20 * 60) return;
			saveTimer = 0;
			if (placed != null) placed.flush();
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (chaining && (entity instanceof ItemEntity || entity instanceof ExperienceOrb)) DROPS.add(entity);
		});
		PlayerBlockBreakEvents.AFTER.register(Lumberjack::afterBreak);
	}

	/** Chamado pelo mixin depois que um item coloca um bloco. */
	public static void onPlaced(Level level, BlockPos pos) {
		if (!(level instanceof ServerLevel sl) || placed == null) return;
		if (sl.getBlockState(pos).is(BlockTags.LOGS)) placed.add(sl, pos);
		else placed.remove(sl, pos); // outro bloco no lugar de um tronco antigo
	}

	private static void afterBreak(Level level, Player player, BlockPos pos, BlockState state, BlockEntity be) {
		if (!(level instanceof ServerLevel sl) || placed == null) return;
		boolean wasPlaced = placed.remove(sl, pos);
		if (chaining || wasPlaced || !state.is(BlockTags.LOGS)) return;
		if (!(player instanceof ServerPlayer sp)) return;
		if (player instanceof FakePlayer && !SELFTEST) return; // máquinas de outros mods (ex.: Autômato) cortam um por vez
		if (sp.getAbilities().instabuild || sp.isSpectator()) return;
		if (config.agacharDesativa && sp.isShiftKeyDown()) return;
		if (config.exigirMachado && !sp.getMainHandItem().is(ItemTags.AXES)) return;
		List<BlockPos> logs = findTree(sl, pos, state.getBlock());
		if (logs != null) chop(sp, sl, pos, state.getBlock(), logs);
	}

	/**
	 * Troncos do mesmo tipo ligados ao quebrado (inclusive na diagonal, para galhos de acácia e árvores grandes), sem
	 * passar por troncos colocados. Devolve null se não for uma árvore (sem folhas naturais) ou se não houver mais nada.
	 */
	static List<BlockPos> findTree(ServerLevel sl, BlockPos origin, Block kind) {
		LongOpenHashSet seen = new LongOpenHashSet();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		List<BlockPos> found = new ArrayList<>();
		seen.add(origin.asLong());
		queue.add(origin);
		boolean leaves = naturalLeavesAround(sl, origin);
		int r = config.raio;
		search:
		while (!queue.isEmpty()) {
			BlockPos p = queue.poll();
			for (int dy = -1; dy <= 1; dy++) {
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (dx == 0 && dy == 0 && dz == 0) continue;
						BlockPos n = p.offset(dx, dy, dz);
						if (!seen.add(n.asLong())) continue;
						if (Math.abs(n.getX() - origin.getX()) > r || Math.abs(n.getZ() - origin.getZ()) > r) continue;
						if (!sl.isLoaded(n) || !sl.getBlockState(n).is(kind) || placed.contains(sl, n)) continue;
						found.add(n);
						queue.add(n);
						if (!leaves) leaves = naturalLeavesAround(sl, n);
						if (found.size() >= config.limite) break search;
					}
				}
			}
		}
		if (found.isEmpty() || (config.exigirFolhas && !leaves)) return null;
		return found;
	}

	/** Folhas que o jogo gerou (as colocadas por jogadores ficam "persistent") ou a copa das árvores do Nether. */
	static boolean naturalLeavesAround(ServerLevel sl, BlockPos pos) {
		for (Direction d : Direction.values()) {
			BlockState s = sl.getBlockState(pos.relative(d));
			if (s.getBlock() instanceof LeavesBlock && s.hasProperty(BlockStateProperties.PERSISTENT) && !s.getValue(BlockStateProperties.PERSISTENT)) return true;
			if (s.is(BlockTags.WART_BLOCKS) || s.is(Blocks.SHROOMLIGHT)) return true;
		}
		return false;
	}

	private static void chop(ServerPlayer sp, ServerLevel sl, BlockPos origin, Block kind, List<BlockPos> logs) {
		chaining = true;
		DROPS.clear();
		try {
			for (BlockPos p : logs) {
				ItemStack tool = sp.getMainHandItem();
				if (config.exigirMachado && !tool.is(ItemTags.AXES)) break; // machado quebrou ou saiu da mão
				if (config.protegerFerramenta && tool.isDamageableItem() && tool.getMaxDamage() - tool.getDamageValue() <= 1) break;
				if (!sl.getBlockState(p).is(kind)) continue;
				sp.gameMode.destroyBlock(p); // pode ser recusado por um mod de proteção: segue para os outros
			}
		} finally {
			chaining = false;
		}
		if (config.juntarDrops) {
			Vec3 at = Vec3.atBottomCenterOf(origin).add(0, 0.25, 0);
			for (Entity e : DROPS) {
				if (!e.isRemoved() && e.level() == sl) {
					e.setPos(at.x, at.y, at.z);
					e.setDeltaMovement(Vec3.ZERO);
				}
			}
		}
		DROPS.clear();
	}
}
