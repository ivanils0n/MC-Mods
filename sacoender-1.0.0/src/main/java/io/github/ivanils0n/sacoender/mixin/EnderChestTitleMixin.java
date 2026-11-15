package io.github.ivanils0n.sacoender.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.ivanils0n.sacoender.SacoEnder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.EnderChestBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(EnderChestBlock.class)
public abstract class EnderChestTitleMixin {
	// Só o título muda (com o desenho dos 9 espaços do saco); a tela e o baú continuam os do jogo.
	@ModifyArg(method = "useWithoutItem", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/SimpleMenuProvider;<init>(Lnet/minecraft/world/inventory/MenuConstructor;Lnet/minecraft/network/chat/Component;)V"), index = 1)
	private Component sacoender$title(Component title, @Local(argsOnly = true) Player player) {
		return SacoEnder.enderTitle(title, player);
	}
}
