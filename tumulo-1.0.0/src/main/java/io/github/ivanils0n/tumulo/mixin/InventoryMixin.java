package io.github.ivanils0n.tumulo.mixin;

import io.github.ivanils0n.tumulo.Tumulo;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Inventory.class)
public abstract class InventoryMixin {
	// Na morte (sem keepInventory) o jogo chama dropAll para jogar tudo no chão: os itens vão para o túmulo.
	@Inject(method = "dropAll", at = @At("HEAD"), cancellable = true)
	private void tumulo$grave(CallbackInfo ci) {
		if (Tumulo.capture((Inventory) (Object) this)) ci.cancel();
	}
}
