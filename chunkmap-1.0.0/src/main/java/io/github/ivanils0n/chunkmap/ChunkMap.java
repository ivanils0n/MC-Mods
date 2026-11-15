package io.github.ivanils0n.chunkmap;

import io.github.ivanils0n.chunkmap.gui.MapScreen;
import io.github.ivanils0n.chunkmap.gui.MinimapHud;
import io.github.ivanils0n.chunkmap.gui.WaypointMarkers;
import io.github.ivanils0n.chunkmap.gui.WaypointScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/** ChunkMap: minimapa, mapa em tela cheia, waypoints e biomas/estruturas pela seed. Só no cliente. */
public final class ChunkMap implements ClientModInitializer {
	public static final Logger LOG = LoggerFactory.getLogger("chunkmap");
	private static WorldSession session;

	public static WorldSession session() {
		return session;
	}

	public static Path gameDir() {
		return FabricLoader.getInstance().getGameDir();
	}

	@Override
	public void onInitializeClient() {
		Config.loadGlobal();
		Keys.init();
		// Por baixo do chat: primeiro os waypoints no mundo, depois o minimapa (por cima deles).
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath("chunkmap", "waypoints"), WaypointMarkers::extract);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath("chunkmap", "minimap"), MinimapHud::extract);

		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> mc.execute(() -> {
			if (session != null) session.close();
			session = WorldSession.open(mc);
		}));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> mc.execute(ChunkMap::closeSession));
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
			closeSession();
			Config.saveGlobal();
		});
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
			if (session != null) session.chunkLoaded(chunk);
		});
		ClientTickEvents.END_CLIENT_TICK.register(ChunkMap::tick);
	}

	private static void closeSession() {
		if (session != null) {
			session.close();
			session = null;
		}
	}

	private static void tick(Minecraft mc) {
		if (session == null) return;
		session.tick(mc);
		boolean free = mc.gui.screen() == null && mc.player != null;
		while (Keys.OPEN_MAP.consumeClick()) {
			if (free) mc.gui.setScreen(new MapScreen());
		}
		while (Keys.WAYPOINT.consumeClick()) {
			if (free) {
				var p = mc.player;
				int n = session.data.waypoints.size();
				Waypoint w = new Waypoint("", p.getBlockX(), p.getBlockY(), p.getBlockZ(), WorldSession.dimId(p.level().dimension()),
						Waypoint.COLORS[n % Waypoint.COLORS.length]);
				mc.gui.setScreen(new WaypointScreen(null, w, true));
			}
		}
		while (Keys.ZOOM_IN.consumeClick()) zoom(2f);
		while (Keys.ZOOM_OUT.consumeClick()) zoom(0.5f);
		while (Keys.TOGGLE.consumeClick()) {
			Config.global.minimap = !Config.global.minimap;
			Config.saveGlobal();
		}
	}

	private static void zoom(float f) {
		Config.global.minimapZoom = Math.clamp(Config.global.minimapZoom * f, 0.25f, 4f);
		Config.saveGlobal();
	}
}
