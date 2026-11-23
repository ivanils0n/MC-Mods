package io.github.ivanils0n.interfacedinamica.client;

import io.github.ivanils0n.interfacedinamica.client.mixin.ContainerScreenAccess;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Estado da animação: quando uma tela abre, "progress" vai de 0 a 1 (em ticks, conforme a velocidade escolhida) e a
 * câmera sai da 1ª pessoa, dá a volta no personagem e para de frente para ele, com a tela ao lado. Ao fechar, volta.
 */
public final class Anim {
	private static Screen screen;
	private static Theme theme = Theme.OTHER;
	/** +1: tela à direita (câmera deslocada para a esquerda do personagem); -1: tela à esquerda. */
	private static int side = 1;
	private static boolean target;
	private static float progress, prevProgress;
	private static float baseYaw, basePitch;
	private static float distance = 3.2f;
	/** Progresso suavizado do quadro atual (calculado pela câmera, usado pelas poses e pela tela). */
	public static float frameT;
	/** Câmera do quadro atual, para projetar a mão do personagem na tela (item voando). */
	static Vec3 camPos = Vec3.ZERO;
	static float camYaw, camPitch;
	public static int clientTicks;

	private static final float SIDE_SHIFT = 1.0f;

	private Anim() {}

	public static boolean active() {
		return progress > 0 || prevProgress > 0 || target;
	}

	public static Theme theme() {
		return theme;
	}

	public static int side() {
		return side;
	}

	public static boolean isFor(Screen s) {
		return s != null && s == screen && target;
	}

	/** Tela iniciada (ou redimensionada): liga a animação e põe a tela ao lado do personagem. */
	public static void onScreenInit(Minecraft mc, Screen s) {
		if (!(s instanceof AbstractContainerScreen<?>) || mc.player == null || mc.level == null) return;
		Config c = Config.INSTANCE;
		if (s != screen) {
			Theme t = Theme.of(s);
			if (!t.enabled(c)) return;
			boolean wasActive = active();
			screen = s;
			theme = t;
			target = true;
			if (!wasActive) {
				baseYaw = mc.player.getYRot();
				basePitch = mc.player.getXRot();
			}
			distance = c.cameraDistance();
			side = chooseSide(mc.player, c);
		}
		placeScreen((AbstractContainerScreen<?>) s);
	}

	public static void onScreenRemoved(Screen s) {
		if (s == screen) {
			screen = null;
			target = false;
		}
	}

	private static int chooseSide(LocalPlayer p, Config c) {
		if (c.side == 1) return 1;
		if (c.side == 2) return -1;
		double right = freeDistance(p, 1), left = freeDistance(p, -1);
		return right + 0.3 >= left ? 1 : -1;
	}

	/** Quanto a câmera consegue chegar perto do ponto final de cada lado antes de bater num bloco. */
	private static double freeDistance(LocalPlayer p, int s) {
		Vec3 eye = p.getEyePosition(1f);
		Vec3 want = cameraTarget(eye, baseYaw, 1f, s);
		return clip(p, eye, want).distanceTo(eye);
	}

	private static void placeScreen(AbstractContainerScreen<?> s) {
		ContainerScreenAccess a = (ContainerScreenAccess) s;
		int w = s.width, iw = a.interfacedinamica$imageWidth();
		if (w < iw * 1.55f) return; // tela pequena demais: fica no meio
		float cx = side > 0 ? w * 0.70f : w * 0.30f;
		int left = Mth.clamp(Math.round(cx - iw / 2f), 2, w - iw - 2);
		int dx = left - a.interfacedinamica$leftPos();
		if (dx == 0) return;
		a.interfacedinamica$setLeftPos(left);
		for (AbstractWidget wd : Screens.getWidgets(s)) wd.setX(wd.getX() + dx);
	}

	public static void tick(Minecraft mc) {
		clientTicks++;
		prevProgress = progress;
		if (mc.player == null) {
			progress = prevProgress = 0;
			target = false;
			screen = null;
			return;
		}
		if (target && mc.gui.screen() != screen) target = false;
		float step = 1f / Config.INSTANCE.durationTicks();
		progress = Mth.clamp(progress + (target ? step : -step), 0f, 1f);
		Effects.tick(mc);
	}

	/** Progresso suavizado (começa e termina devagar). */
	public static float eased(float partial) {
		float p = Mth.lerp(partial, prevProgress, progress);
		return p * p * (3 - 2 * p);
	}

	/** Ponto final da câmera para um progresso t: gira em volta do personagem de trás (t=0) até a frente (t=1). */
	static Vec3 cameraTarget(Vec3 eye, float yaw, float t, int s) {
		float camYaw = yaw + 180f * t;
		double r = Math.toRadians(camYaw);
		Vec3 fwd = new Vec3(-Math.sin(r), 0, Math.cos(r));
		double br = Math.toRadians(yaw);
		Vec3 left = new Vec3(Math.cos(br), 0, Math.sin(br));
		return eye.subtract(fwd.scale(distance * t)).add(left.scale(SIDE_SHIFT * t * s)).add(0, 0.15 * t, 0);
	}

	static Vec3 clip(Entity e, Vec3 from, Vec3 to) {
		HitResult hit = e.level().clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, e));
		if (hit.getType() == HitResult.Type.MISS) return to;
		Vec3 dir = to.subtract(from);
		double len = dir.length();
		if (len < 1e-4) return from;
		return hit.getLocation().subtract(dir.scale(0.25 / len));
	}

	/**
	 * Chamado pela câmera em cada quadro. Devolve {x, y, z, yaw, pitch} ou null para a câmera normal.
	 */
	public static double[] camera(Entity e, float partial) {
		float t = eased(partial);
		frameT = t;
		if (t <= 0.0001f || !(e instanceof LocalPlayer)) {
			frameT = 0;
			return null;
		}
		Vec3 eye = e.getEyePosition(partial);
		Vec3 want = cameraTarget(eye, baseYaw, t, side);
		Vec3 pos = clip(e, eye, want);
		float yaw = baseYaw + 180f * t;
		float pitch = Mth.lerp(t, basePitch, 10f);
		camPos = pos;
		camYaw = yaw;
		camPitch = pitch;
		return new double[]{pos.x, pos.y, pos.z, yaw, pitch};
	}

	/** Ponto do mundo -> pixel da tela (escala da GUI), com a câmera deste quadro. Null se estiver atrás. */
	static float[] project(Minecraft mc, Vec3 p, int guiW, int guiH) {
		double y = Math.toRadians(camYaw), x = Math.toRadians(camPitch);
		Vec3 f = new Vec3(-Math.sin(y) * Math.cos(x), -Math.sin(x), Math.cos(y) * Math.cos(x));
		Vec3 r = new Vec3(-Math.cos(y), 0, -Math.sin(y));
		Vec3 u = r.cross(f);
		Vec3 v = p.subtract(camPos);
		double depth = v.dot(f);
		if (depth < 0.05) return null;
		double tanV = Math.tan(Math.toRadians(mc.options.fov().get()) / 2);
		double tanH = tanV * guiW / (double) guiH;
		double nx = v.dot(r) / depth / tanH, ny = v.dot(u) / depth / tanV;
		return new float[]{(float) (guiW / 2.0 * (1 + nx)), (float) (guiH / 2.0 * (1 - ny))};
	}

	/** Mão do personagem que fica do lado da tela, em pixels da GUI. */
	static float[] handOnScreen(Minecraft mc, int guiW, int guiH) {
		if (mc.player == null) return null;
		double br = Math.toRadians(baseYaw);
		Vec3 left = new Vec3(Math.cos(br), 0, Math.sin(br));
		Vec3 fwd = new Vec3(-Math.sin(br), 0, Math.cos(br));
		Vec3 hand = mc.player.getEyePosition(1f).add(0, -0.75, 0).add(left.scale(0.38 * side)).add(fwd.scale(0.35));
		return project(mc, hand, guiW, guiH);
	}
}
