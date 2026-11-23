package io.github.ivanils0n.interfacedinamica.client.mixin;

import io.github.ivanils0n.interfacedinamica.client.Anim;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow private Entity entity;
	@Shadow private boolean detached;

	@Shadow protected abstract void setRotation(float yRot, float xRot);

	@Shadow protected abstract void setPosition(double x, double y, double z);

	/** Depois do jogo posicionar a câmera: durante a animação ela vai para a frente do personagem. */
	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void interfacedinamica$orbit(float partial, CallbackInfo ci) {
		if (entity == null || !Anim.active()) {
			Anim.frameT = 0;
			return;
		}
		double[] c = Anim.camera(entity, partial);
		if (c == null) return;
		setPosition(c[0], c[1], c[2]);
		setRotation((float) c[3], (float) c[4]);
		detached = true;
	}
}
