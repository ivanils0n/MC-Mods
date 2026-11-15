package io.github.ivanils0n.coletor.mixin;

import io.github.ivanils0n.coletor.Coletor;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ResourcePackResponseMixin {
	// Guarda quem carregou o pack, para só esses jogadores receberem a tela com fundo próprio.
	@Inject(method = "handleResourcePackResponse", at = @At("HEAD"))
	private void coletor$packStatus(ServerboundResourcePackPacket packet, CallbackInfo ci) {
		if ((Object) this instanceof ServerGamePacketListenerImpl game) Coletor.packStatus(game.player, packet.id(), packet.action());
	}
}
