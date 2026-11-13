package io.github.ivanils0n.automato.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** O jogador falso não roda o tick: o mod conta a recarga da arma e deixa a barra cheia na hora do golpe. */
@Mixin(LivingEntity.class)
public interface AttackTickerAccess {
	@Accessor("attackStrengthTicker")
	void automato$setAttackStrengthTicker(int ticks);
}
