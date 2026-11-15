package io.github.ivanils0n.coletor.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
	// O visual do Coletor nunca vai para o save: sem o mod sobra só um barril comum; com o mod, ele é recriado.
	@Inject(method = "shouldBeSaved", at = @At("HEAD"), cancellable = true)
	private void coletor$neverSave(CallbackInfoReturnable<Boolean> cir) {
		if (((Entity) (Object) this).entityTags().contains("coletor")) cir.setReturnValue(false);
	}
}
