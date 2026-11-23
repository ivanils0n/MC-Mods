package io.github.ivanils0n.interfacedinamica.client.mixin;

import io.github.ivanils0n.interfacedinamica.client.Anim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import io.github.ivanils0n.interfacedinamica.client.Poses;
import net.minecraft.client.renderer.item.ItemModelResolver;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingRendererMixin {
	@Shadow @Final protected ItemModelResolver itemModelResolver;

	/**
	 * O modelo do próprio jogador anda até o bloco e fica encostado nele (só o desenho: o jogador de verdade não se
	 * move, então nada muda no servidor).
	 */
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
	private void interfacedinamica$walkTo(LivingEntity entity, LivingEntityRenderState s, float partial, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (entity != mc.player || Anim.frameT <= 0 || !Anim.hasBlock()) return;
		Poses.resolver = itemModelResolver;
		Vec3 real = new Vec3(s.x, s.y, s.z);
		Vec3 p = Anim.modelPos(real);
		s.x = p.x;
		s.y = p.y;
		s.z = p.z;
		s.bodyRot = Anim.modelYaw(s.bodyRot);
		float w = Anim.walkT();
		if (w > 0 && w < 1 && real.distanceTo(p) > 0.05) {
			// passos enquanto anda até o bloco
			s.walkAnimationSpeed = Math.max(s.walkAnimationSpeed, 0.8f * Mth.sin(w * Mth.PI));
			s.walkAnimationPos = s.ageInTicks * 0.7f;
		}
		if (real.distanceTo(p) > 0.2) {
			s.shadowRadius = 0;
			s.shadowPieces.clear();
		}
	}
}
