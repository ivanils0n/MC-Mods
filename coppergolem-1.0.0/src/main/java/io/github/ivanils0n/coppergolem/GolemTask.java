package io.github.ivanils0n.coppergolem;

import io.github.ivanils0n.coppergolem.Links.Link;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.golem.CopperGolemState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * O "cérebro" de um golem vinculado: recarga com barras de cobre quando começa a enferrujar, senão pega itens nos baús
 * de origem e guarda nos baús de destino (a moldura escolhe o item; sem moldura vale a categoria do criativo).
 * O cérebro do jogo é esvaziado para o golem não voltar ao transporte entre baús de cobre; a ferrugem continua normal.
 */
final class GolemTask {
	enum Phase {IDLE, REFILL, SOURCE, DEST}

	static final int CARRY = 16; // itens por viagem (como o golem do jogo)
	private static final int GIVE_UP_TICKS = 900; // 45 s sem chegar: desiste e tenta outra coisa

	Phase phase = Phase.IDLE;
	private BlockPos target;
	private ItemStack want = ItemStack.EMPTY;
	private int ticks, wait, cooldown;
	/** Para o teste: considera que o golem já chegou (o teste não espera ele andar). */
	boolean teleportForTest;

	/** Chamado a cada 2 ticks pelo servidor para cada golem vinculado e carregado. */
	void step(ServerLevel sl, CopperGolem g, Link link) {
		g.getBrain().removeAllBehaviors();
		if (wait > 0) {
			wait -= 2;
			if (wait <= 0) {
				g.setState(CopperGolemState.IDLE);
				g.clearOpenedChestPos();
			}
			return;
		}
		if (cooldown > 0) cooldown -= 2;
		if (phase == Phase.IDLE) {
			decide(sl, g, link);
			return;
		}
		ticks += 2;
		if (ticks > GIVE_UP_TICKS) {
			abort(60);
			return;
		}
		double range = Math.max(2.0, Math.min(4.0, g.getContainerInteractionRange()));
		if (g.distanceToSqr(Vec3.atCenterOf(target)) <= range * range) {
			g.getNavigation().stop();
			act(sl, g, link);
		} else if (teleportForTest) {
			g.setPos(target.getX() + 0.5, target.getY(), target.getZ() + 1.5);
		} else if (ticks == 2 || g.getNavigation().isDone()) {
			Path p = g.getNavigation().createPath(target, 1);
			if (p == null || !g.getNavigation().moveTo(p, 1.0)) abort(200); // sem caminho: tenta outra coisa depois
		}
	}

	private void abort(int cool) {
		phase = Phase.IDLE;
		target = null;
		cooldown = cool;
		ticks = 0;
	}

	private void go(Phase p, BlockPos t) {
		phase = p;
		target = t;
		ticks = 0;
	}

	private void decide(ServerLevel sl, CopperGolem g, Link link) {
		WeatherState weather = g.getWeatherState();
		if (weather != WeatherState.UNAFFECTED && link.refill != null && cooldown <= 0) { // começou a enferrujar: recarrega
			BlockPos refill = Link.pos(link.refill);
			if (Boxes.count(Boxes.containers(sl, refill), new ItemStack(Items.COPPER_INGOT)) >= weather.ordinal()) {
				go(Phase.REFILL, refill);
				return;
			}
		}
		ItemStack held = g.getItemBySlot(EquipmentSlot.MAINHAND);
		if (!held.isEmpty()) {
			BlockPos dest = pickDest(sl, link, held, null);
			if (dest != null) go(Phase.DEST, dest);
			else cooldown = 100; // ninguém aceita o que ele carrega agora: espera
			return;
		}
		if (cooldown > 0) return;
		for (int[] s : link.sources) {
			BlockPos src = Link.pos(s);
			for (Container c : Boxes.containers(sl, src)) {
				for (int i = 0; i < c.getContainerSize(); i++) {
					ItemStack in = c.getItem(i);
					if (in.isEmpty() || pickDest(sl, link, in, src) == null) continue;
					want = in.copyWithCount(1);
					go(Phase.SOURCE, src);
					return;
				}
			}
		}
		cooldown = 60; // nada para levar
	}

	private void act(ServerLevel sl, CopperGolem g, Link link) {
		switch (phase) {
			case SOURCE -> {
				ItemStack got = Boxes.take(Boxes.containers(sl, target), want, CARRY);
				if (!got.isEmpty()) {
					g.setItemSlot(EquipmentSlot.MAINHAND, got);
					g.setState(CopperGolemState.GETTING_ITEM);
					g.setOpenedChestPos(target);
					sl.playSound(null, target, SoundEvents.COPPER_CHEST_OPEN, SoundSource.BLOCKS, 0.5f, 1f);
				}
				finish(20);
			}
			case DEST -> {
				ItemStack held = g.getItemBySlot(EquipmentSlot.MAINHAND);
				BlockPos dest = pickDest(sl, link, held, null);
				if (dest != null && !dest.equals(target)) { // o baú escolhido encheu no caminho: vai para outro
					go(Phase.DEST, dest);
					return;
				}
				ItemStack rest = dest == null ? held : Boxes.insert(Boxes.containers(sl, target), held);
				g.setItemSlot(EquipmentSlot.MAINHAND, rest);
				g.setState(CopperGolemState.DROPPING_ITEM);
				g.setOpenedChestPos(target);
				sl.playSound(null, target, SoundEvents.COPPER_CHEST_CLOSE, SoundSource.BLOCKS, 0.5f, 1f);
				finish(20);
			}
			case REFILL -> {
				int need = g.getWeatherState().ordinal();
				List<Container> boxes = Boxes.containers(sl, target);
				ItemStack got = Boxes.take(boxes, new ItemStack(Items.COPPER_INGOT), need);
				if (got.getCount() >= need && need > 0) {
					g.setWeatherState(WeatherState.UNAFFECTED); // enferrujar de novo segue o tempo normal do jogo
					g.setState(CopperGolemState.GETTING_ITEM);
					g.setOpenedChestPos(target);
					sl.sendParticles(ParticleTypes.WAX_OFF, g.getX(), g.getY() + 0.9, g.getZ(), 12, 0.25, 0.4, 0.25, 0.02);
					sl.playSound(null, g.blockPosition(), SoundEvents.AXE_WAX_OFF, SoundSource.NEUTRAL, 0.8f, 1.2f);
				} else if (!got.isEmpty()) {
					Boxes.insert(boxes, got); // faltou: devolve (não gasta nada)
				}
				finish(20);
			}
			default -> abort(20);
		}
	}

	private void finish(int waitTicks) {
		wait = waitTicks;
		abort(0);
	}

	/**
	 * O baú de destino para esse item: com moldura do mesmo item vale ela (e só ela); baú com moldura de outro item nunca;
	 * sem moldura, o que já tem mais itens da mesma categoria do criativo. Só conta baú com espaço. Nunca o próprio baú de origem.
	 */
	static BlockPos pickDest(ServerLevel sl, Link link, ItemStack stack, BlockPos not) {
		BlockPos best = null;
		int bestScore = -1;
		for (int[] d : link.dests) {
			BlockPos pos = Link.pos(d);
			if (not != null && Boxes.group(sl, not).contains(pos)) continue;
			List<Container> boxes = Boxes.containers(sl, pos);
			if (boxes.isEmpty() || Boxes.room(boxes, stack) <= 0) continue;
			ItemStack f = Boxes.filter(sl, pos);
			if (!f.isEmpty() && !ItemStack.isSameItem(f, stack)) continue;
			int score = !f.isEmpty() ? 100000 : 1 + Boxes.sameCategory(boxes, stack);
			if (score > bestScore) {
				bestScore = score;
				best = pos;
			}
		}
		return best;
	}
}
