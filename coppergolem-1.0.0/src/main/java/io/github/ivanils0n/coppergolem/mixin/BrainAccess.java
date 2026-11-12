package io.github.ivanils0n.coppergolem.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Para devolver o cérebro do jogo a um golem desvinculado (o mod esvazia o cérebro enquanto ele está vinculado). */
@Mixin(LivingEntity.class)
public interface BrainAccess {
	@Accessor("brain")
	void coppergolem$setBrain(Brain<?> brain);

	@Invoker("makeBrain")
	Brain<?> coppergolem$makeBrain(Brain.Packed packed);
}
