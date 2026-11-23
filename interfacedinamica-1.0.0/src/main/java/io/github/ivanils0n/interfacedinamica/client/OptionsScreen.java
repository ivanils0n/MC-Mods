package io.github.ivanils0n.interfacedinamica.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Ajustes (tecla I, muda em Opções > Controles). */
public final class OptionsScreen extends Screen {
	private static final String[] SPEED = {"slow", "normal", "fast"};
	private static final String[] SIDE = {"auto", "right", "left"};
	private static final String[] DIST = {"near", "normal", "far"};
	private final Screen parent;

	public OptionsScreen(Screen parent) {
		super(Component.translatable("interfacedinamica.options.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		Config c = Config.INSTANCE;
		int left = width / 2 - 155, right = width / 2 + 5, y = 36;
		toggle(left, y, "enabled", () -> c.enabled, v -> c.enabled = v);
		button(right, y, () -> Component.translatable("interfacedinamica.options.speed", Component.translatable("interfacedinamica.options.speed." + SPEED[c.speed])), () -> c.speed = (c.speed + 1) % 3);
		y += 24;
		button(left, y, () -> Component.translatable("interfacedinamica.options.side", Component.translatable("interfacedinamica.options.side." + SIDE[c.side])), () -> c.side = (c.side + 1) % 3);
		button(right, y, () -> Component.translatable("interfacedinamica.options.distance", Component.translatable("interfacedinamica.options.distance." + DIST[c.distance])), () -> c.distance = (c.distance + 1) % 3);
		y += 24;
		toggle(left, y, "poses", () -> c.poses, v -> c.poses = v);
		toggle(right, y, "flight", () -> c.flight, v -> c.flight = v);
		y += 24;
		toggle(left, y, "light", () -> c.light, v -> c.light = v);
		toggle(right, y, "effects", () -> c.effects, v -> c.effects = v);
		y += 24;
		toggle(left, y, "tilt", () -> c.tilt, v -> c.tilt = v);
		y += 32;
		toggle(left, y, "inventory", () -> c.inventory, v -> c.inventory = v);
		toggle(right, y, "chests", () -> c.chests, v -> c.chests = v);
		y += 24;
		toggle(left, y, "crafting", () -> c.crafting, v -> c.crafting = v);
		toggle(right, y, "furnaces", () -> c.furnaces, v -> c.furnaces = v);
		y += 24;
		toggle(left, y, "others", () -> c.others, v -> c.others = v);
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(width / 2 - 100, height - 28, 200, 20).build());
	}

	private void button(int x, int y, Supplier<Component> text, Runnable action) {
		addRenderableWidget(Button.builder(text.get(), b -> {
			action.run();
			b.setMessage(text.get());
		}).bounds(x, y, 150, 20).build());
	}

	private void toggle(int x, int y, String key, Supplier<Boolean> get, Consumer<Boolean> set) {
		button(x, y, () -> Component.translatable("interfacedinamica.options." + key).append(": ")
				.append(get.get() ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF), () -> set.accept(!get.get()));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
		super.extractRenderState(g, mx, my, pt);
		g.centeredText(font, title, width / 2, 14, 0xFFFFFFFF);
	}

	@Override
	public void onClose() {
		Config.save();
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
