package io.github.ivanils0n.interfacedinamica.client;

import io.github.ivanils0n.interfacedinamica.client.mixin.ContainerScreenAccess;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Animação de cinema ao abrir uma tela. "progress" vai de 0 a 1 (em ticks) e, com o bloco usado (mesa, fornalha,
 * bigorna, baú...), o personagem anda até ficar encostado nele e a câmera sai da 1ª pessoa para um plano de lado,
 * enquadrando o personagem e o bloco, com a tela do outro lado. Sem bloco (inventário), a câmera gira para a frente.
 * Tudo é só visual, no cliente: o jogador de verdade não sai do lugar.
 */
public final class Anim {
	private static Screen screen;
	private static Theme theme = Theme.OTHER;
	/** +1: tela à direita; -1: tela à esquerda. */
	private static int side = 1;
	private static boolean target;
	private static float progress, prevProgress;
	private static float baseYaw, basePitch;
	private static float distance = 3.2f;

	// Cena com bloco
	private static boolean hasBlock;
	/** Onde o personagem fica (pés), para onde olha e o centro do bloco. */
	private static Vec3 stand = Vec3.ZERO, blockCenter = Vec3.ZERO;
	private static float standYaw;
	/** Lado da câmera em relação à linha personagem-bloco (+1/-1). */
	private static int camSide = 1;

	/** Progresso suavizado do quadro atual. */
	public static float frameT;
	static Vec3 camPos = Vec3.ZERO;
	static float camYaw, camPitch;
	public static int clientTicks;
	/** Conta os quadros (para a tela 3D calcular os ângulos uma vez por quadro). */
	public static int frameId;
	public static float framePartial;

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

	public static boolean hasBlock() {
		return hasBlock;
	}

	public static Vec3 blockCenter() {
		return blockCenter;
	}

	public static boolean isFor(Screen s) {
		return s != null && s == screen && target;
	}

	/** Tela iniciada (ou redimensionada): liga a animação e põe a tela ao lado. */
	public static void onScreenInit(Minecraft mc, Screen s) {
		if (!(s instanceof AbstractContainerScreen<?>) || mc.player == null || mc.level == null) return;
		Config c = Config.INSTANCE;
		if (mc.gui.screen() != s) return; // já foi trocada por outra (ex.: inventório -> criativo)
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
				setupScene(mc);
			}
			distance = c.cameraDistance();
			side = chooseSide(mc.player, c);
		}
		placeScreen((AbstractContainerScreen<?>) s);
	}

	/** Bloco que o jogador usou (o que ele está olhando) e onde o personagem vai se encostar. */
	private static void setupScene(Minecraft mc) {
		hasBlock = false;
		if (theme == Theme.INVENTORY || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
		LocalPlayer p = mc.player;
		BlockPos pos = hit.getBlockPos();
		if (Vec3.atCenterOf(pos).distanceTo(p.getEyePosition(1f)) > 6) return;
		Direction face = hit.getDirection();
		if (face.getAxis() == Direction.Axis.Y) {
			// clicou em cima ou embaixo: fica do lado mais perto do jogador
			double dx = p.getX() - Vec3.atCenterOf(pos).x, dz = p.getZ() - Vec3.atCenterOf(pos).z;
			face = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
		}
		blockCenter = Vec3.atCenterOf(pos);
		double gap = theme == Theme.ANVIL ? 0.95 : 0.82;
		double sx = blockCenter.x + face.getStepX() * gap, sz = blockCenter.z + face.getStepZ() * gap;
		stand = new Vec3(sx, p.getY(), sz);
		// olha para o bloco
		standYaw = (float) Math.toDegrees(Math.atan2(-(blockCenter.x - sx), blockCenter.z - sz));
		hasBlock = true;
		camSide = 1;
		Vec3 focus = focus();
		double right = clip(p, focus, sceneCamera(1)).distanceTo(focus), left = clip(p, focus, sceneCamera(-1)).distanceTo(focus);
		camSide = right + 0.2 >= left ? 1 : -1;
	}

	private static Vec3 focus() {
		return stand.add(blockCenter).scale(0.5).with(Direction.Axis.Y, stand.y + 1.0);
	}

	private static Vec3 dir(float yaw) {
		double r = Math.toRadians(yaw);
		return new Vec3(-Math.sin(r), 0, Math.cos(r));
	}

	/** Câmera final da cena: de lado (75° da direção do personagem), um pouco acima, olhando para o foco. */
	private static Vec3 sceneCamera(int s) {
		return focus().add(dir(standYaw + s * 78f).scale(distance)).add(0, 0.75, 0);
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
		if (hasBlock) return 1;
		double right = freeDistance(p, 1), left = freeDistance(p, -1);
		return right + 0.3 >= left ? 1 : -1;
	}

	private static double freeDistance(LocalPlayer p, int s) {
		Vec3 eye = p.getEyePosition(1f);
		return clip(p, eye, frontCamera(eye, baseYaw, 1f, s)).distanceTo(eye);
	}

	private static void placeScreen(AbstractContainerScreen<?> s) {
		ContainerScreenAccess a = (ContainerScreenAccess) s;
		int w = s.width, iw = a.interfacedinamica$imageWidth();
		if (w < iw * 1.55f) return; // tela pequena demais: fica no meio
		float cx = side > 0 ? w * 0.66f : w * 0.34f;
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
		if (progress == 0 && prevProgress == 0) hasBlock = false;
		Effects.tick(mc);
	}

	/** Progresso suavizado (começa e termina devagar). */
	public static float eased(float partial) {
		float p = Mth.lerp(partial, prevProgress, progress);
		return p * p * (3 - 2 * p);
	}

	/** Parte do movimento: o personagem anda até o bloco nos primeiros 70%. */
	public static float walkT() {
		return smooth(Mth.clamp(frameT / 0.7f, 0, 1));
	}

	/** A pose entra a partir de 40%. */
	public static float poseT() {
		return smooth(Mth.clamp((frameT - 0.4f) / 0.6f, 0, 1));
	}

	private static float smooth(float x) {
		return x * x * (3 - 2 * x);
	}

	/**
	 * Sem bloco: câmera por cima do ombro, atrás e ao lado do personagem (do lado da tela), que fica do outro lado
	 * da imagem olhando para a tela flutuando.
	 */
	static Vec3 frontCamera(Vec3 eye, float yaw, float t, int s) {
		Vec3 fwd = dir(yaw);
		double br = Math.toRadians(yaw);
		Vec3 left = new Vec3(Math.cos(br), 0, Math.sin(br));
		// primeiro vai para o lado (para não atravessar a cabeça), depois para trás
		float side = smooth(Math.min(1f, t * 2.2f)), back = smooth(Math.max(0f, (t - 0.15f) / 0.85f));
		return eye.subtract(fwd.scale(distance * 0.8 * back)).subtract(left.scale(SIDE_SHIFT * 1.15 * side * s)).add(0, 0.2 * side, 0);
	}

	static Vec3 clip(Entity e, Vec3 from, Vec3 to) {
		HitResult hit = e.level().clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, e));
		if (hit.getType() == HitResult.Type.MISS) return to;
		Vec3 d = to.subtract(from);
		double len = d.length();
		if (len < 1e-4) return from;
		return hit.getLocation().subtract(d.scale(0.25 / len));
	}

	/** Onde o modelo do personagem é desenhado (pés) neste quadro. */
	public static Vec3 modelPos(Vec3 real) {
		if (!hasBlock) return real;
		return real.lerp(stand, walkT());
	}

	public static float modelYaw(float realBodyYaw) {
		if (!hasBlock) return realBodyYaw;
		return Mth.rotLerp(walkT(), realBodyYaw, standYaw);
	}

	/** Chamado pela câmera em cada quadro. Devolve {x, y, z, yaw, pitch} ou null para a câmera normal. */
	public static double[] camera(Entity e, float partial) {
		float t = eased(partial);
		frameT = t;
		frameId++;
		framePartial = partial;
		if (t <= 0.0001f || !(e instanceof LocalPlayer)) {
			frameT = 0;
			return null;
		}
		Vec3 eye = e.getEyePosition(partial);
		Vec3 pos;
		float yaw, pitch;
		float time = clientTicks + partial;
		if (hasBlock) {
			Vec3 focus = focus();
			Vec3 want = clip(e, focus, sceneCamera(camSide));
			// pequeno balanço de câmera na mão (só quando parada)
			want = want.add(0, 0.04 * Math.sin(time * 0.05) * t, 0);
			Vec3 look = focus.subtract(want);
			float fy = (float) Math.toDegrees(Math.atan2(-look.x, look.z)) + 17f * side + (float) (1.2 * Math.sin(time * 0.03)) * t;
			float fp = (float) Math.toDegrees(Math.atan2(-look.y, Math.hypot(look.x, look.z)));
			pos = eye.lerp(want, t);
			yaw = Mth.rotLerp(t, baseYaw, fy);
			pitch = Mth.lerp(t, basePitch, fp);
		} else {
			pos = clip(e, eye, frontCamera(eye, baseYaw, t, side));
			yaw = baseYaw + (14f * side + (float) (1.0 * Math.sin(time * 0.03))) * t;
			pitch = Mth.lerp(t, basePitch, 9f);
		}
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

	/** Mão do personagem, em pixels da GUI (destino do item voando). */
	static float[] handOnScreen(Minecraft mc, int guiW, int guiH) {
		if (mc.player == null) return null;
		Vec3 hand;
		if (hasBlock) {
			hand = stand.add(dir(standYaw).scale(0.45)).add(0, 1.0, 0);
		} else {
			double br = Math.toRadians(baseYaw);
			Vec3 left = new Vec3(Math.cos(br), 0, Math.sin(br));
			hand = mc.player.getEyePosition(1f).add(0, -0.55, 0).subtract(left.scale(0.38 * side)).add(dir(baseYaw).scale(0.45));
		}
		return project(mc, hand, guiW, guiH);
	}
}
