package io.github.ivanils0n.interfacedinamica.client.mixin;

import io.github.ivanils0n.interfacedinamica.client.Anim;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {
	/** Sem a sombra escura atrás da tela: o mundo e o personagem aparecem limpos. */
	@Inject(method = "extractTransparentBackground", at = @At("HEAD"), cancellable = true)
	private void interfacedinamica$noDarkening(GuiGraphicsExtractor g, CallbackInfo ci) {
		if (Anim.isFor((Screen) (Object) this)) ci.cancel();
	}
}
