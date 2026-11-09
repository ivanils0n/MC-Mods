package io.github.ivanils0n.mobhealthbar.mixin;

import io.github.ivanils0n.mobhealthbar.HealthBar;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	// createRenderState roda depois de todos os extractRenderState das subclasses, então nada sobrescreve o nosso texto.
	@Inject(method = "createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;", at = @At("RETURN"))
	private void mobhealthbar$addHealth(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
		HealthBar.apply(entity, cir.getReturnValue(), partialTick);
	}
}
