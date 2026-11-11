package io.github.ivanils0n.smartchest.mixin;

import io.github.ivanils0n.smartchest.SmartChest;
import net.minecraft.network.protocol.game.ServerboundRecipeBookChangeSettingsPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.RecipeBookType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class RecipeBookButtonMixin {
	// Na mesa ligada o botão do livro de receitas (coberto pela seta do fundo) volta para a pesquisa. No fim do método
	// o pacote já está na thread do servidor.
	@Inject(method = "handleRecipeBookChangeSettingsPacket", at = @At("TAIL"))
	private void smartchest$bookButton(ServerboundRecipeBookChangeSettingsPacket packet, CallbackInfo ci) {
		if (packet.getBookType() == RecipeBookType.CRAFTING) SmartChest.recipeBookButton(((ServerGamePacketListenerImpl) (Object) this).player);
	}
}
