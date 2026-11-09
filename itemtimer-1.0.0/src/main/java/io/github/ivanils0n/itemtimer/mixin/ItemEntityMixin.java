package io.github.ivanils0n.itemtimer.mixin;

import io.github.ivanils0n.itemtimer.DespawnTimer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
	// Só no servidor integrado (singleplayer/LAN): o servidor não envia a idade do item ao cliente,
	// então copiamos a idade real para o cliente ler. Num servidor dedicado o mod nem carrega.
	@Inject(method = "tick", at = @At("HEAD"))
	private void itemtimer$shareAge(CallbackInfo ci) {
		ItemEntity self = (ItemEntity) (Object) this;
		if (self.level() instanceof ServerLevel) DespawnTimer.SERVER_AGE.put(self.getId(), self.getAge());
	}
}
