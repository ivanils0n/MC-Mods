package io.github.ivanils0n.chunkmap.gui;

import io.github.ivanils0n.chunkmap.ChunkMap;
import io.github.ivanils0n.chunkmap.Config;
import io.github.ivanils0n.chunkmap.WorldSession;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Opções do minimapa (valem em todos os mundos) e da seed deste mundo. */
public final class OptionsScreen extends Screen {
	private final Screen parent;
	private int worldHeader;

	public OptionsScreen(Screen parent) {
		super(Component.translatable("chunkmap.options.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		Config.Global c = Config.global;
		int left = width / 2 - 155, right = width / 2 + 5, y = 40;
		toggle(left, y, "minimap", () -> c.minimap, v -> c.minimap = v);
		button(right, y, () -> Component.translatable("chunkmap.options.size", c.minimapSize), () -> c.minimapSize = c.minimapSize >= 160 ? 64 : c.minimapSize + 16);
		y += 24;
		button(left, y, () -> Component.translatable("chunkmap.options.layer", Component.translatable("chunkmap.options.layer." + c.minimapLayer.name().toLowerCase(java.util.Locale.ROOT))),
				() -> c.minimapLayer = Config.Layer.values()[(c.minimapLayer.ordinal() + 1) % Config.Layer.values().length]);
		button(right, y, () -> Component.translatable("chunkmap.options.zoom", zoomText(c.minimapZoom)),
				() -> c.minimapZoom = c.minimapZoom >= 4f ? 0.25f : c.minimapZoom * 2);
		y += 24;
		toggle(left, y, "coords", () -> c.showCoords, v -> c.showCoords = v);
		toggle(right, y, "biome", () -> c.showBiome, v -> c.showBiome = v);
		y += 24;
		toggle(left, y, "time", () -> c.showTime, v -> c.showTime = v);
		toggle(right, y, "mobs", () -> c.showMobs, v -> c.showMobs = v);
		y += 24;
		toggle(left, y, "in_world", () -> c.inWorldWaypoints, v -> c.inWorldWaypoints = v);
		button(right, y, () -> Component.translatable("chunkmap.options.cave", Component.translatable("chunkmap.options.cave." + c.caveMode.name().toLowerCase(java.util.Locale.ROOT))),
				() -> c.caveMode = Config.CaveMode.values()[(c.caveMode.ordinal() + 1) % Config.CaveMode.values().length]);
		y += 24;
		toggle(left, y, "passive", () -> c.showPassive, v -> c.showPassive = v);
		toggle(right, y, "items", () -> c.showItems, v -> c.showItems = v);
		y += 24;
		worldHeader = y + 4;
		y += 16;

		WorldSession s = ChunkMap.session();
		if (s != null) {
			Button preset = button(left, y, () -> Component.translatable("chunkmap.options.preset", Component.translatable("chunkmap.options.preset." + s.data.preset)), () -> {
				s.data.preset = switch (s.data.preset) {
					case "normal" -> "large_biomes";
					case "large_biomes" -> "amplified";
					default -> "normal";
				};
				s.resetSeed();
			});
			preset.setTooltip(Tooltip.create(Component.translatable(s.singleplayer ? "chunkmap.options.preset.tip_sp" : "chunkmap.options.preset.tip")));
			button(right, y, () -> Component.translatable("chunkmap.options.depth", Component.translatable("chunkmap.options.depth." + s.data.biomeDepth)), () -> {
				s.data.biomeDepth = "caves".equals(s.data.biomeDepth) ? "surface" : "caves";
				s.markDirty();
			});
		}
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(width / 2 - 100, height - 28, 200, 20).build());
	}

	private static String zoomText(float z) {
		return z >= 1 ? (int) z + "x" : "1/" + Math.round(1 / z) + "x";
	}

	private Button button(int x, int y, Supplier<Component> text, Runnable action) {
		Button[] self = new Button[1];
		self[0] = addRenderableWidget(Button.builder(text.get(), b -> {
			action.run();
			b.setMessage(text.get());
		}).bounds(x, y, 150, 20).build());
		return self[0];
	}

	private void toggle(int x, int y, String key, Supplier<Boolean> get, Consumer<Boolean> set) {
		button(x, y, () -> Component.translatable("chunkmap.options." + key).append(": ")
				.append(get.get() ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF), () -> set.accept(!get.get()));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
		super.extractRenderState(g, mx, my, pt);
		g.centeredText(font, title, width / 2, 16, 0xFFFFFFFF);
		if (ChunkMap.session() != null) g.centeredText(font, Component.translatable("chunkmap.options.world"), width / 2, worldHeader, 0xFFA0A0A0);
	}

	@Override
	public void onClose() {
		Config.saveGlobal();
		WorldSession s = ChunkMap.session();
		if (s != null) s.save();
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
