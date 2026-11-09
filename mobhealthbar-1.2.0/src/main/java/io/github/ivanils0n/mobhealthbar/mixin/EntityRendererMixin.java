package io.github.ivanils0n.mobhealthbar.mixin;

import io.github.ivanils0n.mobhealthbar.HealthBar;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	// Mesmo ponto onde o jogo define o nome: qualquer caminho que mostre o nome (inclusive mods de culling) mostra a vida.
	@Inject(method = "extractNameTags(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FDD)V", at = @At("RETURN"))
	private void mobhealthbar$addHealth(Entity entity, EntityRenderState state, float partialTick, double d1, double d2, CallbackInfo ci) {
		HealthBar.apply(entity, state, partialTick);
	}
}
