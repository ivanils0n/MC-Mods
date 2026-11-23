package io.github.ivanils0n.interfacedinamica.client.mixin;

import com.mojang.blaze3d.platform.Window;
import io.github.ivanils0n.interfacedinamica.client.Tilt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	/** O mouse na tela 3D: o ponto onde ele "encosta" no painel girado. */
	@Inject(method = "getScaledXPos(Lcom/mojang/blaze3d/platform/Window;D)D", at = @At("RETURN"), cancellable = true)
	private static void interfacedinamica$x(Window w, double rawX, CallbackInfoReturnable<Double> cir) {
		if (!Tilt.active()) return;
		double sy = Minecraft.getInstance().mouseHandler.ypos() * w.getGuiScaledHeight() / w.getScreenHeight();
		cir.setReturnValue(Tilt.unproject(cir.getReturnValueD(), sy, w.getGuiScaledWidth(), w.getGuiScaledHeight())[0]);
	}

	@Inject(method = "getScaledYPos(Lcom/mojang/blaze3d/platform/Window;D)D", at = @At("RETURN"), cancellable = true)
	private static void interfacedinamica$y(Window w, double rawY, CallbackInfoReturnable<Double> cir) {
		if (!Tilt.active()) return;
		double sx = Minecraft.getInstance().mouseHandler.xpos() * w.getGuiScaledWidth() / w.getScreenWidth();
		cir.setReturnValue(Tilt.unproject(sx, cir.getReturnValueD(), w.getGuiScaledWidth(), w.getGuiScaledHeight())[1]);
	}
}
