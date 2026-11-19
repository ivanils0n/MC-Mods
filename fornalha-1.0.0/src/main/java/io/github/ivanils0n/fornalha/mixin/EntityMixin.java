package io.github.ivanils0n.fornalha.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
	// O visual da Fornalha nunca vai para o save: sem o mod sobram pedras lisas e um barril; com o mod, ele é recriado.
	@Inject(method = "shouldBeSaved", at = @At("HEAD"), cancellable = true)
	private void fornalha$neverSave(CallbackInfoReturnable<Boolean> cir) {
		if (((Entity) (Object) this).entityTags().contains("fornalha")) cir.setReturnValue(false);
	}
}
