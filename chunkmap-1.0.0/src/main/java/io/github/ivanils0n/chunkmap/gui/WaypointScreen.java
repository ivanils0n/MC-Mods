package io.github.ivanils0n.chunkmap.gui;

import io.github.ivanils0n.chunkmap.ChunkMap;
import io.github.ivanils0n.chunkmap.Waypoint;
import io.github.ivanils0n.chunkmap.WorldSession;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Criar ou editar um waypoint: nome, coordenadas, cor e se aparece. */
public final class WaypointScreen extends Screen {
	private final Screen parent;
	private final Waypoint waypoint;
	private final boolean isNew;
	private EditBox name, x, y, z;
	private Checkbox visible;
	private int color;
	private int swatchX, swatchY;

	public WaypointScreen(Screen parent, Waypoint waypoint, boolean isNew) {
		super(Component.translatable(isNew ? "chunkmap.waypoint.new" : "chunkmap.waypoint.edit"));
		this.parent = parent;
		this.waypoint = waypoint;
		this.isNew = isNew;
		this.color = waypoint.color;
	}

	@Override
	protected void init() {
		int cx = width / 2, top = height / 2 - 70;
		name = addRenderableWidget(new EditBox(font, cx - 100, top + 14, 200, 20, Component.translatable("chunkmap.waypoint.name")));
		name.setMaxLength(32);
		name.setValue(waypoint.name);
		name.setHint(Component.translatable("chunkmap.waypoint.name_hint"));
		x = addRenderableWidget(new EditBox(font, cx - 100, top + 50, 64, 20, Component.literal("X")));
		y = addRenderableWidget(new EditBox(font, cx - 32, top + 50, 64, 20, Component.literal("Y")));
		z = addRenderableWidget(new EditBox(font, cx + 36, top + 50, 64, 20, Component.literal("Z")));
		x.setValue(Integer.toString(waypoint.x));
		y.setValue(Integer.toString(waypoint.y));
		z.setValue(Integer.toString(waypoint.z));
		swatchX = cx - 100;
		swatchY = top + 86;
		visible = addRenderableWidget(Checkbox.builder(Component.translatable("chunkmap.waypoint.visible"), font)
				.pos(cx - 100, top + 108).selected(waypoint.visible).build());
		int by = top + 134;
		if (isNew) {
			addRenderableWidget(Button.builder(Component.translatable("chunkmap.waypoint.save"), b -> save()).bounds(cx - 100, by, 98, 20).build());
			addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(cx + 2, by, 98, 20).build());
		} else {
			addRenderableWidget(Button.builder(Component.translatable("chunkmap.waypoint.save"), b -> save()).bounds(cx - 100, by, 64, 20).build());
			addRenderableWidget(Button.builder(Component.translatable("chunkmap.waypoint.delete"), b -> delete()).bounds(cx - 32, by, 64, 20).build());
			addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(cx + 36, by, 64, 20).build());
		}
		setInitialFocus(name);
	}

	private void save() {
		WorldSession s = ChunkMap.session();
		if (s == null) {
			onClose();
			return;
		}
		String n = name.getValue().strip();
		if (n.isEmpty()) n = Component.translatable("chunkmap.waypoint.default_name", s.data.waypoints.size() + 1).getString();
		waypoint.name = n;
		waypoint.x = parse(x, waypoint.x);
		waypoint.y = parse(y, waypoint.y);
		waypoint.z = parse(z, waypoint.z);
		waypoint.color = color;
		waypoint.visible = visible.selected();
		if (isNew) s.data.waypoints.add(waypoint);
		s.markDirty();
		s.save();
		onClose();
	}

	private void delete() {
		WorldSession s = ChunkMap.session();
		if (s != null) {
			s.data.waypoints.remove(waypoint);
			s.markDirty();
			s.save();
		}
		onClose();
	}

	private static int parse(EditBox box, int fallback) {
		try {
			return Integer.parseInt(box.getValue().strip());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
		super.extractRenderState(g, mx, my, pt);
		int cx = width / 2, top = height / 2 - 70;
		g.centeredText(font, title, cx, top - 6, 0xFFFFFFFF);
		g.text(font, Component.translatable("chunkmap.waypoint.name"), cx - 100, top + 4, 0xFFA0A0A0, false);
		g.text(font, "X", cx - 100, top + 40, 0xFFA0A0A0, false);
		g.text(font, "Y", cx - 32, top + 40, 0xFFA0A0A0, false);
		g.text(font, "Z", cx + 36, top + 40, 0xFFA0A0A0, false);
		for (int i = 0; i < Waypoint.COLORS.length; i++) {
			int sx = swatchX + i * 25;
			boolean on = (Waypoint.COLORS[i] | 0xFF000000) == (color | 0xFF000000);
			g.fill(sx, swatchY, sx + 20, swatchY + 16, on ? 0xFFFFFFFF : 0xFF404040);
			g.fill(sx + 2, swatchY + 2, sx + 18, swatchY + 14, Waypoint.COLORS[i]);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		if (e.y() >= swatchY && e.y() < swatchY + 16 && e.x() >= swatchX) {
			int i = (int) ((e.x() - swatchX) / 25);
			if (i < Waypoint.COLORS.length && e.x() - swatchX - i * 25 < 20) {
				color = Waypoint.COLORS[i];
				return true;
			}
		}
		return super.mouseClicked(e, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (e.key() == GLFW.GLFW_KEY_ENTER || e.key() == GLFW.GLFW_KEY_KP_ENTER) {
			save();
			return true;
		}
		return super.keyPressed(e);
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
