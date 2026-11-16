package io.github.ivanils0n.spawninfo;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** O painel no canto superior direito. */
public final class Hud {
	static final int WHITE = 0xFFFFFFFF, GRAY = 0xFFA8B0BA, DIM = 0xFF7C8590, TITLE = 0xFFFFD866, GREEN = 0xFF7BE07A,
			RED = 0xFFFF6B6B, YELLOW = 0xFFFFC94D, BLUE = 0xFF86C5FF, HOSTILE = 0xFFFF8A80, PASSIVE = 0xFF9BE59A, AMBIENT = 0xFFB9A8FF;
	private static final int MARGIN = 4, PAD = 4, LINE = 10;

	/** Uma linha: texto à esquerda, valor à direita (opcional) e, se for cabeçalho de grupo, a barra do limite. */
	record Line(Component left, int leftColor, Component right, int rightColor, double bar, int barColor, int indent) {
		static Line of(Component left, int color) {
			return new Line(left, color, null, 0, -1, 0, 0);
		}

		static Line kv(Component left, int lc, Component right, int rc) {
			return new Line(left, lc, right, rc, -1, 0, 0);
		}

		Line indented() {
			return new Line(left, leftColor, right, rightColor, bar, barColor, indent + 6);
		}
	}

	private Hud() {}

	public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (!Config.get.visible || mc.player == null || mc.level == null) return;
		if (mc.debugEntries.isOverlayVisible()) return; // F3: as informações do jogo ficam no lugar
		SpawnCalc.Result r = SpawnInfo.result;
		if (r == null) return;
		List<Line> lines = lines(mc, r, SpawnInfo.look);
		draw(g, mc.font, lines);
	}

	static void draw(GuiGraphicsExtractor g, Font font, List<Line> lines) {
		int w = 140;
		for (Line l : lines) {
			int lw = l.indent() + font.width(l.left()) + (l.right() == null ? 0 : 10 + font.width(l.right()));
			w = Math.max(w, lw);
		}
		w = Math.min(w, 260) + PAD * 2;
		int h = lines.size() * LINE + PAD * 2 - 1;
		float s = Config.get.scale / 100f;
		int screenW = g.guiWidth();
		g.pose().pushMatrix();
		// Encosta no canto superior direito, já na escala escolhida.
		g.pose().translate(screenW - MARGIN - w * s, MARGIN);
		g.pose().scale(s, s);
		int alpha = Math.round(Config.get.opacity / 100f * 255) << 24;
		g.fill(0, 0, w, h, alpha | 0x10161E);
		g.fill(0, 0, w, 1, (Math.min(255, (alpha >>> 24) + 60) << 24) | 0xFFD866 & 0x00FFFFFF);
		int y = PAD;
		for (Line l : lines) {
			int x = PAD + l.indent();
			if (l.bar() >= 0) {
				// Barra do limite de mobs atrás do cabeçalho do grupo.
				int bw = w - PAD * 2;
				g.fill(PAD, y + 8, PAD + bw, y + 9, 0x55FFFFFF);
				g.fill(PAD, y + 8, PAD + (int) Math.round(bw * Math.clamp(l.bar(), 0, 1)), y + 9, l.barColor());
			}
			g.text(font, l.left(), x, y, l.leftColor(), true);
			if (l.right() != null) g.text(font, l.right(), w - PAD - font.width(l.right()), y, l.rightColor(), true);
			y += LINE;
		}
		g.pose().popMatrix();
	}

	static String pct(double v) {
		if (v <= 0) return "0%";
		if (v < 0.001) return "<0,1%";
		return String.format(Locale.ROOT, v < 0.1 ? "%.1f%%" : "%.0f%%", v * 100).replace('.', ',');
	}

	private static MutableComponent t(String key, Object... args) {
		return Component.translatable("spawninfo.hud." + key, args);
	}

	static List<Line> lines(Minecraft mc, SpawnCalc.Result r, LookCounter.Counts look) {
		Config c = Config.get;
		List<Line> out = new ArrayList<>();
		BlockPos p = r.pos;
		out.add(Line.kv(t(r.looking ? "title_look" : "title_feet"), TITLE,
				Component.literal(p.getX() + " " + p.getY() + " " + p.getZ()), GRAY));
		if (c.showSpot) {
			out.add(Line.kv(t("biome"), GRAY, biomeName(r), WHITE));
			out.add(Line.kv(t("light"), GRAY, t("light_value", r.blockLight, r.skyLight), r.blockLight > 0 ? YELLOW : WHITE));
			if (r.distance >= 0) {
				out.add(Line.kv(t("distance"), GRAY, t("blocks", Math.round(r.distance)), r.distance <= 24 ? RED : r.distance > 128 ? YELLOW : WHITE));
			}
		}
		if (r.problem != SpawnCalc.Problem.NONE) {
			out.add(Line.of(t("problem." + r.problem.name().toLowerCase(Locale.ROOT)), r.problem == SpawnCalc.Problem.TOO_CLOSE ? YELLOW : RED));
		}
		if (c.showSlime && mc.level.dimension() == Level.OVERWORLD) {
			Component v;
			int color;
			if (r.slimeChunk == null) {
				v = t("slime_unknown");
				color = YELLOW;
			} else if (r.slimeChunk) {
				v = p.getY() < 40 ? t("yes") : t("slime_yes_high");
				color = p.getY() < 40 ? GREEN : YELLOW;
			} else {
				v = t("no");
				color = GRAY;
			}
			out.add(Line.kv(t("slime_chunk"), GRAY, v, color));
		}
		if (r.loading) out.add(Line.of(t("loading"), YELLOW));
		if (c.showHostile) group(out, r, r.hostile, "hostile", HOSTILE, r.slimeChunk == null ? r.slimeIfChunk : -1);
		if (c.showPassive) group(out, r, r.passive, "passive", PASSIVE, -1);
		if (c.showAmbient) group(out, r, r.ambient, "ambient", AMBIENT, -1);
		if (c.showLook && look != null) {
			out.add(Line.kv(t("look", look.range(), look.angle()), BLUE,
					t("look_counts", look.hostile(), look.passive()), WHITE));
			if (!look.hostileTypes().isEmpty()) out.add(Line.of(types(look.hostileTypes()), HOSTILE).indented());
			if (!look.passiveTypes().isEmpty()) out.add(Line.of(types(look.passiveTypes()), PASSIVE).indented());
		}
		return out;
	}

	private static void group(List<Line> out, SpawnCalc.Result r, SpawnCalc.Group g, String name, int color, double slimeIfChunk) {
		Config c = Config.get;
		double fill = g.count / (double) g.cap;
		MutableComponent head = t(name).append(Component.literal(" " + g.count + "/" + g.cap));
		if (g.category == net.minecraft.world.entity.MobCategory.CREATURE) head.append(t("every_20s"));
		int barColor = fill >= 1 ? 0xFFFF5555 : fill >= 0.75 ? 0xFFFFC94D : 0xFF66D17A;
		out.add(new Line(head, color, Component.literal(Hud.pct(g.shownTotal())), WHITE, fill, barColor, 0));
		if (g.disabled) {
			out.add(Line.of(t("peaceful"), DIM).indented());
			return;
		}
		if (g.count >= g.cap) out.add(Line.of(t("cap_full"), RED).indented());
		int shown = 0;
		for (SpawnCalc.Row row : g.rows) {
			if (row.chance() <= 0 || shown >= c.maxRows) continue;
			out.add(Line.kv(row.type().getDescription(), WHITE, Component.literal(pct(g.shown(row))), GRAY).indented());
			shown++;
		}
		if (slimeIfChunk > 0) {
			double v = c.capAdjust ? slimeIfChunk * g.free : slimeIfChunk;
			out.add(Line.kv(t("slime_if_chunk"), YELLOW, Component.literal(pct(v)), YELLOW).indented());
			shown++;
		}
		if (shown == 0 && !r.loading) out.add(Line.of(t("none"), DIM).indented());
	}

	private static Component types(List<Map.Entry<EntityType<?>, Integer>> list) {
		MutableComponent m = Component.empty();
		int n = 0;
		for (Map.Entry<EntityType<?>, Integer> e : list) {
			if (n == 3) {
				m.append(Component.literal(" …"));
				break;
			}
			if (n > 0) m.append(Component.literal(", "));
			m.append(e.getKey().getDescription()).append(Component.literal(" ×" + e.getValue()));
			n++;
		}
		return m;
	}

	private static Component biomeName(SpawnCalc.Result r) {
		return r.biome.unwrapKey()
				.map(k -> (Component) Component.translatable("biome." + k.identifier().getNamespace() + "." + k.identifier().getPath()))
				.orElse(Component.literal("?"));
	}
}
