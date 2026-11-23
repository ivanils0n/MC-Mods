package io.github.ivanils0n.interfacedinamica.client.mixin;

import io.github.ivanils0n.interfacedinamica.client.Anim;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	/** Com a câmera fora do personagem, a mão da 1ª pessoa não aparece. */
	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void interfacedinamica$noHand(CameraRenderState camera, float partial, Matrix4fc matrix, CallbackInfo ci) {
		if (Anim.frameT > 0) ci.cancel();
	}
}
