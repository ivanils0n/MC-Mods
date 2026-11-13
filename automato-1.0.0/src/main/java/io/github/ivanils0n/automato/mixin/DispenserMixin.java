package io.github.ivanils0n.automato.mixin;

import io.github.ivanils0n.automato.Automato;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DispenserBlock.class)
public abstract class DispenserMixin {
	// O Autômato é um ejetor de verdade por baixo: com redstone ele usa as ferramentas em vez de atirá-las.
	@Inject(method = "dispenseFrom", at = @At("HEAD"), cancellable = true)
	private void automato$noDispense(ServerLevel level, BlockState state, BlockPos pos, CallbackInfo ci) {
		if (Automato.isMachine(level, pos)) ci.cancel();
	}
}
