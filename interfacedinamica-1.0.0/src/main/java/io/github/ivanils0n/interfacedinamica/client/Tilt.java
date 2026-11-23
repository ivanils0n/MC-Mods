package io.github.ivanils0n.interfacedinamica.client;

import io.github.ivanils0n.interfacedinamica.client.mixin.ContainerScreenAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * A tela em 3D: a GUI inteira é desenhada com uma perspectiva, girada em volta do meio da tela do baú/mesa,
 * como um painel flutuando no mundo (o lado de perto maior que o de longe). Ao abrir ela entra girando de longe.
 * O mouse passa pela conta inversa, então clicar e arrastar itens continua certo.
 */
public final class Tilt {
	private static final float FOV = (float) Math.toRadians(50);
	private static boolean active;
	private static float theta, phi, push, bob;
	private static float pivotX, pivotY;
	private static int lastFrame = -1;

	private Tilt() {}

	/** Atualiza os ângulos deste quadro (uma vez por quadro). */
	private static void update() {
		Minecraft mc = Minecraft.getInstance();
		Screen s = mc.gui.screen();
		active = Config.INSTANCE.tilt && Anim.isFor(s) && s instanceof AbstractContainerScreen<?> && Anim.frameT > 0.001f;
		if (!active) return;
		ContainerScreenAccess a = (ContainerScreenAccess) s;
		pivotX = a.interfacedinamica$leftPos() + a.interfacedinamica$imageWidth() / 2f;
		pivotY = a.interfacedinamica$topPos() + a.interfacedinamica$imageHeight() / 2f;
		float t = Anim.frameT;
		float time = Anim.clientTicks + Anim.framePartial;
		int sd = Anim.side();
		// entra de lado (quase de perfil) e vira até ficar de frente para o personagem
		theta = (float) Math.toRadians(Mth.lerp(t, -80f, -21f) * sd + 1.2f * Mth.sin(time * 0.05f));
		phi = (float) Math.toRadians(3f + 0.9f * Mth.sin(time * 0.07f));
		push = (1 - t) * (1 - t);
		bob = 1.6f * Mth.sin(time * 0.09f) * t;
	}

	public static boolean active() {
		int frame = Anim.frameId;
		if (frame != lastFrame) {
			lastFrame = frame;
			update();
		}
		return active;
	}

	/** Da GUI (pixels, y para baixo) para o espaço da câmera da perspectiva. */
	private static Matrix4f view(float w, float h) {
		float d = h / (2f * (float) Math.tan(FOV / 2));
		float cx = w / 2f, cy = h / 2f;
		float px = pivotX - cx, py = cy - pivotY;
		return new Matrix4f()
				.translate(0, bob, -d - push * d * 1.4f)
				.translate(px, py, 0)
				.rotateY(theta)
				.rotateX(phi)
				.translate(-px, -py, 0)
				.scale(1, -1, 0.0001f)
				.translate(-cx, -cy, 0);
	}

	/** Matriz de projeção que substitui a ortográfica da GUI. */
	public static Matrix4f projection(float w, float h) {
		float d = h / (2f * (float) Math.tan(FOV / 2));
		return new Matrix4f().perspective(FOV, w / h, d * 0.05f, d * 8f).mul(view(w, h));
	}

	/** Ponto da GUI no painel -> pixel da tela (o contrário de unproject). */
	public static double[] project(double gx, double gy, float w, float h) {
		Vector3f p = view(w, h).transformPosition(new Vector3f((float) gx, (float) gy, 0));
		double t2 = Math.tan(FOV / 2), a = w / (double) h;
		double nx = p.x / -p.z / (a * t2), ny = p.y / -p.z / t2;
		return new double[]{(nx + 1) / 2 * w, (1 - ny) / 2 * h};
	}

	/** Ponto da tela (pixels da GUI) -> ponto da GUI no painel girado (onde o mouse está "de verdade"). */
	public static double[] unproject(double sx, double sy, float w, float h) {
		Matrix4f v = view(w, h);
		Vector3f o = v.transformPosition(new Vector3f(0, 0, 0));
		Vector3f u = v.transformPosition(new Vector3f(1, 0, 0)).sub(o);
		Vector3f vv = v.transformPosition(new Vector3f(0, 1, 0)).sub(o);
		double t2 = Math.tan(FOV / 2), a = w / (double) h;
		double nx = 2 * sx / w - 1, ny = 1 - 2 * sy / h;
		double dx = nx * a * t2, dy = ny * t2, dz = -1;
		// o + gx*u + gy*vv = s*dir  ->  [u vv -dir] (gx, gy, s) = -o
		double[][] m = {{u.x, vv.x, -dx}, {u.y, vv.y, -dy}, {u.z, vv.z, -dz}};
		double[] r = {-o.x, -o.y, -o.z};
		double det = det(m);
		if (Math.abs(det) < 1e-9) return new double[]{sx, sy};
		double gx = det(col(m, 0, r)) / det, gy = det(col(m, 1, r)) / det, s = det(col(m, 2, r)) / det;
		if (s <= 0) return new double[]{sx, sy};
		return new double[]{gx, gy};
	}

	private static double[][] col(double[][] m, int c, double[] r) {
		double[][] n = {m[0].clone(), m[1].clone(), m[2].clone()};
		for (int i = 0; i < 3; i++) n[i][c] = r[i];
		return n;
	}

	private static double det(double[][] m) {
		return m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1])
				- m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0])
				+ m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0]);
	}
}
