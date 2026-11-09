package io.github.ivanils0n.tpaltar.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
	// A decoração do altar nunca vai para o save: sem o mod, o mundo abre só com a obsidiana; com o mod, é recriada.
	@Inject(method = "shouldBeSaved", at = @At("HEAD"), cancellable = true)
	private void tpaltar$neverSave(CallbackInfoReturnable<Boolean> cir) {
		if (((Entity) (Object) this).getTags().contains("tpaltar")) cir.setReturnValue(false);
	}
}
