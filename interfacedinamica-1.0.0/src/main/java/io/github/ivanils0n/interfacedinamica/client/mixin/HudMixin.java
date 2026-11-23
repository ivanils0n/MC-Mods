package io.github.ivanils0n.interfacedinamica.client.mixin;

import io.github.ivanils0n.interfacedinamica.client.Anim;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class HudMixin {
	/** Durante a animação a barra de itens, corações etc. somem (visual de cinema); voltam ao fechar a tela. */
	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"), cancellable = true)
	private void interfacedinamica$hide(GuiGraphicsExtractor g, DeltaTracker dt, CallbackInfo ci) {
		if (Anim.frameT > 0.02f) ci.cancel();
	}
}
