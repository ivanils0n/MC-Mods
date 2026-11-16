package io.github.ivanils0n.spawninfo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.WorldOptions;

import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Ajustes do painel (tecla U): o que mostrar, tamanho, cone de visão e a seed deste mundo/servidor. */
public final class SettingsScreen extends Screen {
	private static final int COL = 124, GAP = 4;
	private final Screen parent;
	private EditBox seedBox;
	private int seedHeader, statusY;
	private Component status = Component.empty();
	private int statusColor = Hud.GRAY;

	public SettingsScreen(Screen parent) {
		super(Component.translatable("spawninfo.settings.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		Config c = Config.get;
		int cols = width >= COL * 3 + GAP * 2 + 8 ? 3 : 2;
		int left = width / 2 - (COL * cols + GAP * (cols - 1)) / 2;
		int top = 30;
		int[] i = {0};
		Runnable next = () -> i[0]++;
		java.util.function.IntUnaryOperator x = n -> left + (n % cols) * (COL + GAP);
		java.util.function.IntUnaryOperator y = n -> top + (n / cols) * 22;

		toggle(x.applyAsInt(i[0]), y.applyAsInt(i[0]), "visible", () -> c.visible, v -> c.visible = v); next.run();
		button(x.applyAsInt(i[0]), y.applyAsInt(i[0]), () -> label("target", Component.translatable("spawninfo.settings.target." + c.target.name().toLowerCase(java.util.Locale.ROOT))),
				() -> c.target = c.target == Config.Target.LOOK ? Config.Target.FEET : Config.Target.LOOK, "target"); next.run();
		toggle(x.applyAsInt(i[0]), y.applyAsInt(i[0]), "spot", () -> c.showSpot, v -> c.showSpot = v); next.run();
		toggle(x.applyAsInt(i[0]), y.applyAsInt(i[0]), "slime", () -> c.showSlime, v -> c.showSlime = v); next.run();
		toggle(x.applyAsInt(i[0]), y.applyAsInt(i[0]), "hostile", () -> c.showHostile, v -> c.showHostile = v); next.run();
		toggle(x.applyAsInt(i[0]), y.applyAsInt(i[0]), "passive", () -> c.showPassive, v -> c.showPassive = v); next.run();
		toggle(x.applyAsInt(i[0]), y.applyAsInt(i[0]), "ambient", () -> c.showAmbient, v -> c.showAmbient = v); next.run();
		toggle(x.applyAsInt(i[0]), y.applyAsInt(i[0]), "look", () -> c.showLook, v -> c.showLook = v); next.run();
		toggle(x.applyAsInt(i[0]), y.applyAsInt(i[0]), "cap_adjust", () -> c.capAdjust, v -> c.capAdjust = v); next.run();
		slider(x.applyAsInt(i[0]), y.applyAsInt(i[0]), "rows", 1, 12, 1, "", () -> c.maxRows, v -> c.maxRows = v); next.run();
		slider(x.applyAsInt(i[0]), y.applyAsInt(i[0]), "range", 8, 128, 8, "", () -> c.lookRange, v -> c.lookRange = v); next.run();
		slider(x.applyAsInt(i[0]), y.applyAsInt(i[0]), "angle", 10, 180, 5, "°", () -> c.lookAngle, v -> c.lookAngle = v); next.run();
		slider(x.applyAsInt(i[0]), y.applyAsInt(i[0]), "scale", 50, 150, 5, "%", () -> c.scale, v -> c.scale = v); next.run();
		slider(x.applyAsInt(i[0]), y.applyAsInt(i[0]), "opacity", 0, 100, 5, "%", () -> c.opacity, v -> c.opacity = v); next.run();

		int rows = (i[0] + cols - 1) / cols;
		seedHeader = top + rows * 22 + 4;
		int sy = seedHeader + 12;
		int total = COL * cols + GAP * (cols - 1);
		seedBox = addRenderableWidget(new EditBox(font, left, sy, total - 2 * (56 + GAP), 20, Component.translatable("spawninfo.settings.seed")));
		seedBox.setMaxLength(64);
		seedBox.setHint(Component.translatable("spawninfo.settings.seed_hint"));
		if (minecraft != null && minecraft.player != null) seedBox.setValue(SpawnInfo.seedText(minecraft));
		addRenderableWidget(Button.builder(Component.translatable("spawninfo.settings.apply"), b -> applySeed())
				.bounds(left + total - 2 * 56 - GAP, sy, 56, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("spawninfo.settings.clear"), b -> {
			seedBox.setValue("");
			applySeed();
		}).bounds(left + total - 56, sy, 56, 20).build());
		statusY = sy + 24;
		updateStatus();
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(width / 2 - 100, Math.max(statusY + 14, height - 26), 200, 20).build());
	}

	private Component label(String key, Component value) {
		return Component.translatable("spawninfo.settings." + key).append(": ").append(value);
	}

	private Button button(int x, int y, Supplier<Component> text, Runnable action, String key) {
		Button b = addRenderableWidget(Button.builder(text.get(), btn -> {
			action.run();
			btn.setMessage(text.get());
			Config.save();
		}).bounds(x, y, COL, 20).build());
		b.setTooltip(Tooltip.create(Component.translatable("spawninfo.settings." + key + ".tip")));
		return b;
	}

	private void toggle(int x, int y, String key, Supplier<Boolean> get, Consumer<Boolean> set) {
		button(x, y, () -> label(key, get.get() ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF), () -> set.accept(!get.get()), key);
	}

	private void slider(int x, int y, String key, int min, int max, int step, String unit, IntSupplier get, IntConsumer set) {
		Slider s = new Slider(x, y, key, min, max, step, unit, get, set);
		s.setTooltip(Tooltip.create(Component.translatable("spawninfo.settings." + key + ".tip")));
		addRenderableWidget(s);
	}

	/** Barra deslizante de números inteiros (com passo). */
	private static final class Slider extends AbstractSliderButton {
		private final String key, unit;
		private final int min, max, step;
		private final IntConsumer set;

		Slider(int x, int y, String key, int min, int max, int step, String unit, IntSupplier get, IntConsumer set) {
			super(x, y, COL, 20, Component.empty(), (get.getAsInt() - min) / (double) (max - min));
			this.key = key;
			this.unit = unit;
			this.min = min;
			this.max = max;
			this.step = step;
			this.set = set;
			updateMessage();
		}

		private int current() {
			int v = (int) Math.round((min + value * (max - min)) / step) * step;
			return Math.clamp(v, min, max);
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.translatable("spawninfo.settings." + key).append(": " + current() + unit));
		}

		@Override
		protected void applyValue() {
			set.accept(current());
		}
	}

	private void applySeed() {
		if (minecraft == null || minecraft.player == null) return;
		String text = seedBox.getValue().strip();
		// No singleplayer, digitar a própria seed do mundo é o mesmo que deixar vazio.
		var server = minecraft.getSingleplayerServer();
		if (server != null && !text.isEmpty()) {
			OptionalLong own = OptionalLong.of(server.overworld().getSeed());
			if (own.equals(WorldOptions.parseSeed(text))) text = "";
		}
		SpawnInfo.setSeedText(minecraft, text);
		seedBox.setValue(text);
		SpawnInfo.refresh(minecraft);
		updateStatus();
	}

	private void updateStatus() {
		if (minecraft == null || minecraft.player == null) {
			status = Component.translatable("spawninfo.settings.status.no_world");
			statusColor = Hud.GRAY;
			return;
		}
		String typed = SpawnInfo.seedText(minecraft);
		OptionalLong seed = SpawnInfo.seed(minecraft);
		if (!typed.isEmpty() && seed.isPresent()) {
			status = Component.translatable("spawninfo.settings.status.typed", Long.toString(seed.getAsLong()));
			statusColor = Hud.GREEN;
		} else if (seed.isPresent()) {
			status = Component.translatable("spawninfo.settings.status.world");
			statusColor = Hud.GREEN;
		} else {
			status = Component.translatable("spawninfo.settings.status.none");
			statusColor = Hud.YELLOW;
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
		super.extractRenderState(g, mx, my, pt);
		g.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
		g.centeredText(font, Component.translatable("spawninfo.settings.seed_header"), width / 2, seedHeader, 0xFFA0A0A0);
		g.centeredText(font, status, width / 2, statusY, statusColor);
	}

	@Override
	public void onClose() {
		Config.get.sanitize();
		Config.save();
		if (minecraft != null) {
			SpawnInfo.refresh(minecraft);
			minecraft.gui.setScreen(parent);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
