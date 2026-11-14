package io.github.ivanils0n.automato.mixin;

import net.minecraft.world.entity.ExperienceOrb;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Bolinhas iguais se juntam numa só com "count": o pote soma valor × quantidade. */
@Mixin(ExperienceOrb.class)
public interface ExperienceOrbAccess {
	@Accessor("count")
	int automato$getCount();
}
