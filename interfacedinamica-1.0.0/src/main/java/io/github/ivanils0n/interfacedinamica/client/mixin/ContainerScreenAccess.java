package io.github.ivanils0n.interfacedinamica.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccess {
	@Accessor("leftPos")
	int interfacedinamica$leftPos();

	@Accessor("leftPos")
	void interfacedinamica$setLeftPos(int v);

	@Accessor("topPos")
	int interfacedinamica$topPos();

	@Accessor("imageWidth")
	int interfacedinamica$imageWidth();

	@Accessor("imageHeight")
	int interfacedinamica$imageHeight();
}
