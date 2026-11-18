package io.github.ivanils0n.lumberjack.mixin;

import io.github.ivanils0n.lumberjack.Lumberjack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
	// Todo bloco colocado com um item (jogador, ou mods que imitam um jogador) passa por aqui: troncos colocados são anotados.
	@Inject(method = "placeBlock", at = @At("RETURN"))
	private void lumberjack$track(BlockPlaceContext context, BlockState state, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) Lumberjack.onPlaced(context.getLevel(), context.getClickedPos());
	}
}
