package io.github.ivanils0n.interfacedinamica.client;

import io.github.ivanils0n.interfacedinamica.client.mixin.ContainerScreenAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Item que voa do espaço até a mão do personagem, faíscas em volta da tela e a luz do ambiente sobre ela. */
public final class Effects {
	private static final int FLIGHT_TICKS = 10;
	private static final RandomSource RANDOM = RandomSource.create();
	private static final List<Flight> FLIGHTS = new ArrayList<>();
	private static final List<Spark> SPARKS = new ArrayList<>();

	private record Flight(ItemStack stack, float x0, float y0, int start) {}

	private static final class Spark {
		float x, y, vx, vy;
		int age, life, color;
	}

	private Effects() {}

	/** Item pego de um espaço (posição do espaço na tela, canto de cima à esquerda). */
	public static void startFlight(ItemStack stack, float x, float y) {
		if (!Config.INSTANCE.flight || stack.isEmpty()) return;
		if (FLIGHTS.size() > 6) FLIGHTS.removeFirst();
		FLIGHTS.add(new Flight(stack.copyWithCount(1), x + 8, y + 8, Anim.clientTicks));
	}

	static void tick(Minecraft mc) {
		FLIGHTS.removeIf(f -> Anim.clientTicks - f.start > FLIGHT_TICKS);
		Iterator<Spark> it = SPARKS.iterator();
		while (it.hasNext()) {
			Spark s = it.next();
			s.age++;
			s.x += s.vx;
			s.y += s.vy;
			s.vy += Anim.theme() == Theme.FURNACE ? -0.05f : 0.0f;
			if (s.age > s.life) it.remove();
		}
		Screen screen = mc.gui.screen();
		if (!Config.INSTANCE.effects || !Anim.isFor(screen) || !(screen instanceof AbstractContainerScreen<?> cs)) return;
		ContainerScreenAccess a = (ContainerScreenAccess) cs;
		int l = a.interfacedinamica$leftPos(), t = a.interfacedinamica$topPos();
		int w = a.interfacedinamica$imageWidth(), h = a.interfacedinamica$imageHeight();
		switch (Anim.theme()) {
			case FURNACE -> {
				for (int i = 0; i < 3; i++) spark(l + RANDOM.nextFloat() * w, t + h - 2, (RANDOM.nextFloat() - 0.5f) * 0.8f,
						-0.6f - RANDOM.nextFloat() * 1.2f, 14 + RANDOM.nextInt(14), pick(0xFFFFAA28, 0xFFFFDC5A, 0xFFFF7014));
				for (int i = 0; i < 2; i++) {
					boolean leftEdge = RANDOM.nextBoolean();
					spark(leftEdge ? l : l + w, t + h * (0.4f + RANDOM.nextFloat() * 0.6f), (leftEdge ? -1 : 1) * RANDOM.nextFloat() * 0.6f,
							-0.4f - RANDOM.nextFloat(), 12 + RANDOM.nextInt(10), pick(0xFFFFAA28, 0xFFFFDC5A, 0xFFFF7014));
				}
			}
			case ENDER -> {
				for (int i = 0; i < 3; i++) edgeSpark(l, t, w, h, 0.35f, 18, pick(0xFFB45AF0, 0xFF7A2BD0, 0xFFE0A8FF));
			}
			case ENCHANT -> {
				for (int i = 0; i < 2; i++) edgeSpark(l, t, w, h, 0.3f, 22, pick(0xFF7FE8FF, 0xFFD8F8FF, 0xFFB080FF));
			}
			case CHEST -> {
				// poeirinha dourada só no começo, quando o baú abre
				if (Anim.frameT < 0.95f) for (int i = 0; i < 2; i++) spark(l + RANDOM.nextFloat() * w, t + 2, (RANDOM.nextFloat() - 0.5f) * 0.6f,
						-0.3f - RANDOM.nextFloat() * 0.6f, 16 + RANDOM.nextInt(10), pick(0xFFFFE696, 0xFFE6BE6E));
			}
			default -> {
			}
		}
		if (SPARKS.size() > 220) SPARKS.subList(0, SPARKS.size() - 220).clear();
	}

	private static int pick(int... c) {
		return c[RANDOM.nextInt(c.length)];
	}

	private static void edgeSpark(int l, int t, int w, int h, float speed, int life, int color) {
		float x, y, vx, vy;
		switch (RANDOM.nextInt(4)) {
			case 0 -> {x = l + RANDOM.nextFloat() * w; y = t; vx = 0; vy = -speed;}
			case 1 -> {x = l + RANDOM.nextFloat() * w; y = t + h; vx = 0; vy = speed;}
			case 2 -> {x = l; y = t + RANDOM.nextFloat() * h; vx = -speed; vy = 0;}
			default -> {x = l + w; y = t + RANDOM.nextFloat() * h; vx = speed; vy = 0;}
		}
		spark(x, y, vx + (RANDOM.nextFloat() - 0.5f) * 0.3f, vy + (RANDOM.nextFloat() - 0.5f) * 0.3f, life + RANDOM.nextInt(10), color);
	}

	private static void spark(float x, float y, float vx, float vy, int life, int color) {
		Spark s = new Spark();
		s.x = x;
		s.y = y;
		s.vx = vx;
		s.vy = vy;
		s.life = life;
		s.color = color;
		SPARKS.add(s);
	}

	/** Depois de desenhar a tela: faíscas e itens voando por cima. */
	public static void afterScreen(Minecraft mc, Screen screen, GuiGraphicsExtractor g, float partial) {
		if (!Anim.isFor(screen) && FLIGHTS.isEmpty() && SPARKS.isEmpty()) return;
		for (Spark s : SPARKS) {
			float k = 1f - s.age / (float) s.life;
			int a = (int) (255 * Mth.clamp(k, 0, 1));
			int col = (a << 24) | (s.color & 0xFFFFFF);
			int size = k > 0.5f ? 2 : 1;
			int x = Math.round(s.x + s.vx * partial), y = Math.round(s.y + s.vy * partial);
			g.fill(x, y, x + size, y + size, col);
		}
		if (FLIGHTS.isEmpty()) return;
		float[] hand = Anim.handOnScreen(mc, screen.width, screen.height);
		for (Flight f : FLIGHTS) {
			float t = Mth.clamp((Anim.clientTicks - f.start + partial) / FLIGHT_TICKS, 0, 1);
			float tx = hand != null ? hand[0] : screen.width * 0.3f;
			float ty = hand != null ? hand[1] : screen.height * 0.6f;
			// curva com um arco para cima
			float mx = (f.x0 + tx) / 2, my = Math.min(f.y0, ty) - 40;
			float u = 1 - t;
			float x = u * u * f.x0 + 2 * u * t * mx + t * t * tx;
			float y = u * u * f.y0 + 2 * u * t * my + t * t * ty;
			float scale = 1f + 0.9f * Mth.sin(t * Mth.PI) - 0.4f * t;
			g.pose().pushMatrix();
			g.pose().translate(x, y);
			g.pose().rotate(t * Mth.TWO_PI);
			g.pose().scale(scale, scale);
			g.item(f.stack, -8, -8);
			g.pose().popMatrix();
		}
	}

	/** Cor por cima do fundo da tela: escurece no escuro e puxa para a cor da dimensão/da fornalha. */
	public static int tint(Minecraft mc) {
		if (!Config.INSTANCE.light || mc.level == null || mc.player == null) return 0;
		BlockPos pos = BlockPos.containing(mc.player.getEyePosition(1f));
		int light = mc.level.getMaxLocalRawBrightness(pos);
		float dark = (1f - light / 15f) * 0.45f;
		int r = 0, gr = 0, b = 0;
		float a = dark;
		if (mc.level.dimension() == Level.NETHER) {
			r = 255; gr = 90; b = 20;
			a = Math.max(a, 0.12f);
		} else if (mc.level.dimension() == Level.END) {
			r = 120; gr = 60; b = 170;
			a = Math.max(a, 0.12f);
		} else if (Anim.theme() == Theme.FURNACE) {
			r = 255; gr = 140; b = 40;
			a = Math.max(a, 0.08f);
		}
		if (a <= 0.01f) return 0;
		// mistura: a parte "escura" puxa para preto, a cor só tinge
		float colorShare = (r + gr + b) > 0 ? 0.6f : 0f;
		int cr = (int) (r * colorShare), cg = (int) (gr * colorShare), cb = (int) (b * colorShare);
		return ((int) (a * Anim.frameT * 255) << 24) | (cr << 16) | (cg << 8) | cb;
	}
}
