package io.github.ivanils0n.chunkmap;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** Teclas do mod (mudam em Opções > Controles > Teclas, categoria ChunkMap). */
public final class Keys {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("chunkmap", "chunkmap"));
	public static final KeyMapping OPEN_MAP = key("open_map", GLFW.GLFW_KEY_M);
	public static final KeyMapping WAYPOINT = key("waypoint", GLFW.GLFW_KEY_B);
	public static final KeyMapping ZOOM_IN = key("zoom_in", GLFW.GLFW_KEY_EQUAL);
	public static final KeyMapping ZOOM_OUT = key("zoom_out", GLFW.GLFW_KEY_MINUS);
	public static final KeyMapping TOGGLE = key("toggle_minimap", GLFW.GLFW_KEY_UNKNOWN);

	private Keys() {}

	private static KeyMapping key(String name, int code) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.chunkmap." + name, InputConstants.Type.KEYSYM, code, CATEGORY));
	}

	/** Carrega a classe (registra as teclas). */
	static void init() {}
}
