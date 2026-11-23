package io.github.ivanils0n.interfacedinamica.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parte do cliente: câmera em 3ª pessoa animada, tela ao lado do personagem, poses, item voando, luz e efeitos.
 * Mod só do cliente: funciona em qualquer servidor.
 */
public class ClientMod implements ClientModInitializer {
	public static final Logger LOG = LoggerFactory.getLogger("Interface Dinâmica");
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("interfacedinamica", "interfacedinamica"));
	public static KeyMapping OPTIONS;

	@Override
	public void onInitializeClient() {
		Config.load();
		OPTIONS = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.interfacedinamica.options", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			Anim.tick(mc);
			while (OPTIONS.consumeClick()) {
				if (mc.gui.screen() == null) mc.gui.setScreen(new OptionsScreen(null));
			}
		});
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?>)) return;
			Anim.onScreenInit(mc, screen);
			ScreenEvents.remove(screen).register(Anim::onScreenRemoved);
			ScreenEvents.afterExtract(screen).register((s, g, mx, my, pt) -> Effects.afterScreen(mc, s, g, pt));
		});
	}
}
