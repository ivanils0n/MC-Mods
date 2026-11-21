package io.github.ivanils0n.chunkmap.gui;

import io.github.ivanils0n.chunkmap.ChunkMap;
import io.github.ivanils0n.chunkmap.Config;
import io.github.ivanils0n.chunkmap.Waypoint;
import io.github.ivanils0n.chunkmap.WorldSession;
import io.github.ivanils0n.chunkmap.map.MapRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Os waypoints vistos no mundo: alfinete, nome e distância por cima do jogo, na direção do ponto. */
public final class WaypointMarkers {
	private static final Matrix4f MATRIX = new Matrix4f();
	private static final Vector4f VEC = new Vector4f();

	private WaypointMarkers() {}

	public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		WorldSession session = ChunkMap.session();
		if (!Config.global.inWorldWaypoints || session == null || mc.level == null || mc.player == null || mc.gui.screen() != null) return;
		Camera camera = mc.gameRenderer.mainCamera();
		if (camera == null || !camera.isInitialized()) return;
		String dim = WorldSession.dimId(mc.level.dimension());
		Vec3 cam = camera.position();
		camera.getViewRotationProjectionMatrix(MATRIX);
		Font font = mc.font;
		int sw = g.guiWidth(), sh = g.guiHeight();
		int shown = 0;
		for (Waypoint w : session.data.waypoints) {
			if (!w.visible || !w.dim.equals(dim) || shown >= 64) continue;
			double dx = w.x + 0.5 - cam.x, dy = w.y + 1.0 - cam.y, dz = w.z + 0.5 - cam.z;
			double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (dist < 4) continue; // em cima do ponto: não atrapalha a visão
			// Longe: aponta na mesma direção, mas a 64 blocos (não some atrás do limite da projeção).
			double k = dist > 64 ? 64 / dist : 1;
			VEC.set((float) (dx * k), (float) (dy * k), (float) (dz * k), 1f).mul(MATRIX);
			if (VEC.w <= 0.05f) continue; // atrás da câmera
			float nx = VEC.x / VEC.w, ny = VEC.y / VEC.w;
			if (nx < -1.1f || nx > 1.1f || ny < -1.1f || ny > 1.1f) continue;
			int sx = Mth.floor((nx + 1) / 2 * sw), sy = Mth.floor((1 - ny) / 2 * sh);
			shown++;
			// Perto do centro da tela mostra nome e distância; longe dele só o alfinete (não polui a visão).
			boolean focus = Math.abs(sx - sw / 2) < sw / 6 && Math.abs(sy - sh / 2) < sh / 5;
			if (w.death) g.blit(RenderPipelines.GUI_TEXTURED, MapRenderer.SKULL, sx - 6, sy - 12, 0f, 0f, 12, 12, 16, 16, 16, 16);
			else {
				g.blit(RenderPipelines.GUI_TEXTURED, MapRenderer.PIN, sx - 6, sy - 12, 0f, 0f, 12, 12, 16, 16, 16, 16, w.color | 0xFF000000);
				g.blit(RenderPipelines.GUI_TEXTURED, MapRenderer.PIN_TOP, sx - 6, sy - 12, 0f, 0f, 12, 12, 16, 16, 16, 16);
			}
			String label = focus ? w.name + " (" + Math.round(dist) + " m)" : Math.round(dist) + " m";
			int tw = font.width(label);
			g.fill(sx - tw / 2 - 2, sy + 1, sx + tw / 2 + 2, sy + 11, 0x80000000);
			g.text(font, label, sx - tw / 2, sy + 2, focus ? 0xFFFFFFFF : 0xFFC8C8C8, false);
		}
	}
}
