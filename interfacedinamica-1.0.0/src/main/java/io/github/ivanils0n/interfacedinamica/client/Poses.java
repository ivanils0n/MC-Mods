package io.github.ivanils0n.interfacedinamica.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;

/** Pose do próprio personagem enquanto mexe na tela (só no cliente de quem abriu). */
public final class Poses {
	private Poses() {}

	/** Cabeça olhando para a tela (antes do modelo calcular os ângulos). */
	public static void head(AvatarRenderState s, float t) {
		float turn = 38f * Anim.side(); // gira para o lado da tela
		float pitch = switch (Anim.theme()) {
			case INVENTORY -> 40f;
			case CHEST, ENDER -> 28f;
			case CRAFTING -> 32f;
			default -> 18f;
		};
		s.yRot = Mth.lerp(t, s.yRot, turn);
		s.xRot = Mth.lerp(t, s.xRot, pitch);
	}

	public static void arms(PlayerModel m, AvatarRenderState s, float t) {
		float time = s.ageInTicks;
		float rx, ry, lx, ly, rz = 0, lz = 0;
		switch (Anim.theme()) {
			case INVENTORY -> { // olhando as próprias mãos
				rx = -0.95f; ry = -0.35f; lx = -0.95f; ly = 0.35f;
			}
			case CHEST, ENDER -> { // braços para dentro do baú
				rx = -1.25f; ry = -0.1f; lx = -1.25f; ly = 0.1f;
			}
			case CRAFTING -> { // martelando com a direita
				rx = -1.5f + 0.55f * Mth.sin(time * 0.9f); ry = -0.1f; lx = -0.8f; ly = 0.25f;
			}
			case FURNACE -> { // aquecendo as mãos
				rx = -1.35f + 0.04f * Mth.sin(time * 0.3f); ry = -0.3f; lx = -1.35f + 0.04f * Mth.cos(time * 0.3f); ly = 0.3f;
			}
			case ENCHANT -> { // braços erguidos
				rx = -2.4f; ry = 0f; lx = -2.4f; ly = 0f; rz = 0.35f; lz = -0.35f;
			}
			default -> {
				rx = -1.0f; ry = -0.15f; lx = -1.0f; ly = 0.15f;
			}
		}
		// o braço do lado da tela (esquerdo quando a tela fica à direita) "mexe" nela
		if (Anim.side() > 0) lx -= 0.15f;
		else rx -= 0.15f;
		pose(m.rightArm, rx, ry, rz, t);
		pose(m.leftArm, lx, ly, lz, t);
	}

	private static void pose(ModelPart p, float x, float y, float z, float t) {
		p.xRot = Mth.lerp(t, p.xRot, x);
		p.yRot = Mth.lerp(t, p.yRot, y);
		p.zRot = Mth.lerp(t, p.zRot, z);
	}
}
