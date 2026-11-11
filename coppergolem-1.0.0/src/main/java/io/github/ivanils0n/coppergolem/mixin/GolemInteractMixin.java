package io.github.ivanils0n.coppergolem.mixin;

import io.github.ivanils0n.coppergolem.GolemMod;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CopperGolem.class)
public abstract class GolemInteractMixin {
	// Com o cajado na mão, o clique no golem é do mod (ligar), não da interação normal do jogo.
	@Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
	private void coppergolem$wand(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		InteractionResult r = GolemMod.useOnGolem(player, hand, (CopperGolem) (Object) this);
		if (r != InteractionResult.PASS) cir.setReturnValue(r);
	}
}
