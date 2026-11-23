package io.github.ivanils0n.interfacedinamica.client.mixin;

import io.github.ivanils0n.interfacedinamica.client.Anim;
import io.github.ivanils0n.interfacedinamica.client.Config;
import io.github.ivanils0n.interfacedinamica.client.Poses;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
	private static boolean interfacedinamica$mine(AvatarRenderState s) {
		Minecraft mc = Minecraft.getInstance();
		return Anim.frameT > 0 && Config.INSTANCE.poses && mc.player != null && s.id == mc.player.getId();
	}

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("HEAD"))
	private void interfacedinamica$before(AvatarRenderState s, CallbackInfo ci) {
		if (interfacedinamica$mine(s)) Poses.before(s, Anim.poseT());
	}

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void interfacedinamica$arms(AvatarRenderState s, CallbackInfo ci) {
		if (interfacedinamica$mine(s)) Poses.arms((PlayerModel) (Object) this, s, Anim.poseT());
	}
}
