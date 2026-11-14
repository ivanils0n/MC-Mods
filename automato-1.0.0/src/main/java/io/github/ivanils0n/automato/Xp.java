package io.github.ivanils0n.automato;

import io.github.ivanils0n.automato.MachineStore.Machine;
import io.github.ivanils0n.automato.mixin.ExperienceOrbAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * Pote de XP do Autômato: as bolinhas de XP que aparecem à frente dele (dos mobs que ele mata e dos blocos que ele
 * quebra) vão para o pote, até o equivalente a 100 níveis; cheio, elas ficam no chão. O jogador recolhe pela tela.
 * Se a ferramenta em uso tiver Remendo, o XP conserta ela primeiro, como faria com um jogador.
 */
final class Xp {
	static final int MAX = pointsForLevel(100);

	private Xp() {}

	/** Pontos para ir do nível {@code level} ao próximo (a mesma conta do jogo). */
	static int neededForNext(int level) {
		return level >= 30 ? 112 + (level - 30) * 9 : level >= 15 ? 37 + (level - 15) * 5 : 7 + level * 2;
	}

	static int pointsForLevel(int level) {
		int total = 0;
		for (int l = 0; l < level; l++) total += neededForNext(l);
		return total;
	}

	/** Quantos níveis completos dá para tirar de zero com esses pontos. */
	static int levels(int points) {
		int l = 0;
		while (points >= neededForNext(l)) {
			points -= neededForNext(l);
			l++;
		}
		return l;
	}

	/** Recolhe as bolinhas na área de trabalho à frente (até o maior alcance, de quebra ou de golpe, com folga). */
	static void collectOrbs(ServerLevel sl, Machine m, Direction f, DispenserBlockEntity be) {
		ItemStack tool = m.slot >= 0 ? be.getItem(m.slot) : ItemStack.EMPTY;
		boolean mending = !tool.isEmpty() && tool.isDamaged() && EnchantmentHelper.getItemEnchantmentLevel(
			sl.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING), tool) > 0;
		if (m.xp >= MAX && !mending) return;
		int reach = Math.max(m.range, Worker.ATTACK_REACH);
		BlockPos pos = m.pos();
		AABB box = new AABB(pos.relative(f)).expandTowards(f.getStepX() * (reach - 1), f.getStepY() * (reach - 1), f.getStepZ() * (reach - 1)).inflate(1.5);
		boolean got = false;
		for (ExperienceOrb orb : sl.getEntitiesOfClass(ExperienceOrb.class, box, ExperienceOrb::isAlive)) {
			int count = Math.max(1, ((ExperienceOrbAccess) orb).automato$getCount());
			long value = (long) orb.getValue() * count;
			if (mending && tool.isDamaged()) { // Remendo: como no jogador, cada ponto conserta 2 de durabilidade antes
				int fix = (int) Math.min(tool.getDamageValue(), value * 2);
				tool.setDamageValue(tool.getDamageValue() - fix);
				value -= (fix + 1) / 2;
				be.setChanged();
			}
			if (m.xp + value > MAX) break; // não cabe inteira: fica no chão
			m.xp += (int) value;
			orb.discard();
			got = true;
		}
		if (got) {
			Automato.store.markDirty();
			sl.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.15f, 0.8f + sl.getRandom().nextFloat() * 0.4f);
		}
	}

	/** Passa todo o XP do pote para o jogador. */
	static void giveTo(ServerPlayer sp, Machine m) {
		if (m.xp <= 0) return;
		sp.giveExperiencePoints(m.xp);
		m.xp = 0;
		Automato.store.markDirty();
		sp.level().playSound(null, sp.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.2f);
	}
}
