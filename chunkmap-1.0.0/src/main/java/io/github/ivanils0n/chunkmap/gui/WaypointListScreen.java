package io.github.ivanils0n.chunkmap.gui;

import io.github.ivanils0n.chunkmap.ChunkMap;
import io.github.ivanils0n.chunkmap.Waypoint;
import io.github.ivanils0n.chunkmap.WorldSession;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Locale;

/** Lista dos waypoints: clique para editar, olho para mostrar/esconder. */
public final class WaypointListScreen extends Screen {
	private static final int ROW = 18, W = 300;
	private final Screen parent;
	private int scroll;

	public WaypointListScreen(Screen parent) {
		super(Component.translatable("chunkmap.map.waypoints"));
		this.parent = parent;
	}

	private List<Waypoint> list() {
		WorldSession s = ChunkMap.session();
		return s == null ? List.of() : s.data.waypoints;
	}

	private int top() {
		return 34;
	}

	private int rows() {
		return Math.max(1, (height - top() - 40) / ROW);
	}

	@Override
	protected void init() {
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width / 2 - 100, height - 28, 200, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
		super.extractRenderState(g, mx, my, pt);
		g.centeredText(font, title, width / 2, 14, 0xFFFFFFFF);
		List<Waypoint> list = list();
		var player = minecraft.player;
		String here = player == null ? "" : WorldSession.dimId(player.level().dimension());
		int x = width / 2 - W / 2;
		if (list.isEmpty()) {
			g.centeredText(font, Component.translatable("chunkmap.list.empty"), width / 2, top() + 20, 0xFFA0A0A0);
			return;
		}
		scroll = Mth.clamp(scroll, 0, Math.max(0, list.size() - rows()));
		for (int i = 0; i < rows() && scroll + i < list.size(); i++) {
			Waypoint w = list.get(scroll + i);
			int y = top() + i * ROW;
			boolean hover = mx >= x && mx < x + W && my >= y && my < y + ROW - 2;
			g.fill(x, y, x + W, y + ROW - 2, hover ? 0x60FFFFFF : 0x40000000);
			g.fill(x + 3, y + 3, x + 13, y + 13, w.color | 0xFF000000);
			g.text(font, w.name, x + 18, y + 4, w.visible ? 0xFFFFFFFF : 0xFF808080, true);
			String info;
			if (w.dim.equals(here) && player != null) {
				double d = Math.sqrt(player.distanceToSqr(w.x + 0.5, w.y, w.z + 0.5));
				info = String.format(Locale.ROOT, "%d, %d, %d · %d m", w.x, w.y, w.z, Math.round(d));
			} else {
				info = String.format(Locale.ROOT, "%d, %d, %d · %s", w.x, w.y, w.z, w.dim.substring(w.dim.indexOf(':') + 1));
			}
			g.text(font, info, x + W - 22 - font.width(info), y + 4, 0xFFC0C0C0, false);
			// Olho: mostrar/esconder
			g.fill(x + W - 16, y + 2, x + W - 4, y + 14, w.visible ? 0xFF4CC85A : 0xFF606060);
			g.text(font, w.visible ? "✔" : "-", x + W - 13, y + 4, 0xFFFFFFFF, false);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		if (super.mouseClicked(e, doubleClick)) return true;
		List<Waypoint> list = list();
		int x = width / 2 - W / 2;
		int i = (int) Math.floor((e.y() - top()) / ROW);
		if (e.x() < x || e.x() >= x + W || i < 0 || i >= rows() || scroll + i >= list.size()) return false;
		Waypoint w = list.get(scroll + i);
		if (e.x() >= x + W - 18) {
			w.visible = !w.visible;
			WorldSession s = ChunkMap.session();
			if (s != null) s.markDirty();
		} else {
			minecraft.gui.setScreen(new WaypointScreen(this, w, false));
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		scroll -= (int) Math.signum(sy);
		return true;
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
