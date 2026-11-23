package io.github.ivanils0n.interfacedinamica.client.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import io.github.ivanils0n.interfacedinamica.client.Tilt;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {
	/** Com a tela aberta e a animação ligada, a GUI é desenhada em perspectiva (painel 3D). */
	@Redirect(method = "draw", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lnet/minecraft/client/renderer/Projection;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"))
	private GpuBufferSlice interfacedinamica$perspective(ProjectionMatrixBuffer buffer, Projection projection) {
		if (!Tilt.active()) return buffer.getBuffer(projection);
		return buffer.getBuffer(Tilt.projection(projection.width(), projection.height()));
	}
}
