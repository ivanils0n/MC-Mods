package io.github.ivanils0n.interfacedinamica.client.mixin;

import io.github.ivanils0n.interfacedinamica.client.Anim;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {
	@Shadow public int width;
	@Unique private int interfacedinamica$savedWidth = -1;

	/** Sem a sombra escura atrás da tela: o mundo e o personagem aparecem limpos. */
	@Inject(method = "extractTransparentBackground", at = @At("HEAD"), cancellable = true)
	private void interfacedinamica$noDarkening(GuiGraphicsExtractor g, CallbackInfo ci) {
		if (Anim.isFor((Screen) (Object) this)) ci.cancel();
	}

	/**
	 * Várias telas desenham o fundo no meio, calculado pela largura ((largura - imagem) / 2), e não pela posição da
	 * tela. Enquanto o fundo é desenhado, a largura "finge" ser a que põe o meio exatamente onde a tela foi colocada.
	 */
	@Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/Screen;extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
	private void interfacedinamica$bgBefore(GuiGraphicsExtractor g, int mx, int my, float pt, CallbackInfo ci) {
		Screen self = (Screen) (Object) this;
		if (!Anim.isFor(self) || !(self instanceof AbstractContainerScreen<?>)) return;
		ContainerScreenAccess a = (ContainerScreenAccess) self;
		interfacedinamica$savedWidth = width;
		width = 2 * a.interfacedinamica$leftPos() + a.interfacedinamica$imageWidth();
	}

	@Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
			target = "Lnet/minecraft/client/gui/screens/Screen;extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
	private void interfacedinamica$bgAfter(GuiGraphicsExtractor g, int mx, int my, float pt, CallbackInfo ci) {
		if (interfacedinamica$savedWidth >= 0) {
			width = interfacedinamica$savedWidth;
			interfacedinamica$savedWidth = -1;
		}
	}
}
