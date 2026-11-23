package io.github.ivanils0n.interfacedinamica.client.mixin;

import io.github.ivanils0n.interfacedinamica.client.Anim;
import io.github.ivanils0n.interfacedinamica.client.Effects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenMixin {
	@Shadow protected int leftPos;
	@Shadow protected int topPos;
	@Shadow @Final protected int imageWidth;
	@Shadow @Final protected int imageHeight;

	/** Luz do ambiente: por cima do fundo, antes dos textos e itens (já com a origem no canto da tela). */
	@Inject(method = "extractContents", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;extractLabels(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V"))
	private void interfacedinamica$light(GuiGraphicsExtractor g, int mx, int my, float pt, CallbackInfo ci) {
		if (!Anim.isFor((AbstractContainerScreen<?>) (Object) this)) return;
		int color = Effects.tint(Minecraft.getInstance());
		if (color != 0) g.fill(0, 0, imageWidth, imageHeight, color);
	}

	/** Item pego de um espaço: começa a voar até a mão do personagem. */
	@Inject(method = "slotClicked", at = @At("HEAD"))
	private void interfacedinamica$flight(Slot slot, int slotId, int button, ContainerInput input, CallbackInfo ci) {
		if (slot == null || !slot.hasItem() || !Anim.isFor((AbstractContainerScreen<?>) (Object) this)) return;
		Effects.startFlight(slot.getItem(), leftPos + slot.x, topPos + slot.y);
	}
}
