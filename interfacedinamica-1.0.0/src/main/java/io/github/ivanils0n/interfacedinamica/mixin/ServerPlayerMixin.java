package io.github.ivanils0n.interfacedinamica.mixin;

import io.github.ivanils0n.interfacedinamica.server.PanelManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalInt;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
	/** Guarda o título da tela (o servidor não o guarda depois de abrir) para mostrar no painel. */
	@Inject(method = "openMenu", at = @At("HEAD"))
	private void interfacedinamica$title(MenuProvider provider, CallbackInfoReturnable<OptionalInt> cir) {
		if (provider != null) PanelManager.rememberTitle((ServerPlayer) (Object) this, provider.getDisplayName());
	}
}
