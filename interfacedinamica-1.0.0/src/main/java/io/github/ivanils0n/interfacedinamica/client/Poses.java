package io.github.ivanils0n.interfacedinamica.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;

/**
 * Animações do próprio personagem enquanto mexe na tela (só no cliente de quem abriu). Os ângulos são do modelo do
 * jogo: xRot negativo levanta o braço para a frente. "time" é o tempo em ticks, para as mãos ficarem se mexendo.
 */
public final class Poses {
	/** Ciclo da martelada na bigorna (ticks) e o momento da batida. */
	public static final int HAMMER_PERIOD = 14;
	public static final float HAMMER_HIT = 10f / HAMMER_PERIOD;

	private Poses() {}

	/** Antes do modelo calcular: encostar (corpo inclinado) e cabeça olhando para o que está fazendo. */
	public static void before(AvatarRenderState s, float t) {
		Theme th = Anim.theme();
		boolean lean = Anim.hasBlock() && (th == Theme.CRAFTING || th == Theme.CHEST || th == Theme.ENDER
				|| th == Theme.FURNACE || th == Theme.BREWING || th == Theme.OTHER);
		if (lean && t > 0.5f) s.isCrouching = true;
		float yaw, pitch;
		if (Anim.hasBlock()) {
			yaw = 0;
			pitch = switch (th) {
				case FURNACE -> 38f;
				case ENCHANT -> -8f;
				case ANVIL -> 30f;
				default -> 35f;
			};
			// olhada de lado de vez em quando, como quem está pensando
			yaw += 10f * Mth.sin(s.ageInTicks * 0.045f);
		} else {
			yaw = 38f * Anim.side();
			pitch = th == Theme.INVENTORY ? 40f : 20f;
		}
		s.yRot = Mth.lerp(t, s.yRot, yaw);
		s.xRot = Mth.lerp(t, s.xRot, pitch);
	}

	public static void arms(PlayerModel m, AvatarRenderState s, float t) {
		float time = s.ageInTicks;
		float rx, ry, lx, ly, rz = 0, lz = 0;
		switch (Anim.theme()) {
			case INVENTORY -> { // olhando as próprias mãos, mexendo nos itens
				rx = -0.95f + 0.08f * Mth.sin(time * 0.35f); ry = -0.35f;
				lx = -0.95f + 0.08f * Mth.cos(time * 0.35f); ly = 0.35f;
			}
			case CRAFTING -> { // mãos apoiadas na mesa, uma mexendo nas peças
				rx = -1.05f + 0.22f * Math.max(0, Mth.sin(time * 0.55f)); ry = -0.2f + 0.12f * Mth.sin(time * 0.27f);
				lx = -1.0f; ly = 0.3f;
			}
			case CHEST, ENDER -> { // braços dentro do baú, procurando
				rx = -1.35f + 0.18f * Mth.sin(time * 0.5f); ry = -0.15f + 0.1f * Mth.sin(time * 0.31f);
				lx = -1.35f + 0.18f * Mth.sin(time * 0.5f + 2.2f); ly = 0.15f - 0.1f * Mth.sin(time * 0.37f);
			}
			case FURNACE -> { // mãos perto do fogo, esfregando
				rx = -0.95f + 0.05f * Mth.sin(time * 0.6f); ry = -0.35f + 0.18f * Mth.sin(time * 0.6f);
				lx = -0.95f + 0.05f * Mth.cos(time * 0.6f); ly = 0.35f - 0.18f * Mth.sin(time * 0.6f);
			}
			case ANVIL -> { // martelando
				float ph = (time % HAMMER_PERIOD) / HAMMER_PERIOD;
				float raise;
				if (ph < 0.6f) raise = smooth(ph / 0.6f);          // levanta devagar
				else if (ph < HAMMER_HIT) raise = 1 - smooth((ph - 0.6f) / (HAMMER_HIT - 0.6f)); // desce rápido
				else raise = 0;                                     // batida, parado
				rx = -0.75f - 1.85f * raise; ry = -0.15f; rz = 0.1f;
				lx = -0.85f; ly = 0.45f; // a outra mão segura a peça
			}
			case ENCHANT -> { // braços erguidos, flutuando
				rx = -2.45f + 0.12f * Mth.sin(time * 0.1f); ry = 0f; rz = 0.35f;
				lx = -2.45f + 0.12f * Mth.cos(time * 0.1f); ly = 0f; lz = -0.35f;
			}
			case BREWING -> { // mexendo a poção em círculos
				rx = -1.2f + 0.2f * Mth.sin(time * 0.4f); ry = -0.1f; rz = 0.2f * Mth.cos(time * 0.4f);
				lx = -0.7f; ly = 0.3f;
			}
			default -> {
				rx = -1.0f + 0.15f * Mth.sin(time * 0.4f); ry = -0.15f;
				lx = -1.0f + 0.15f * Mth.cos(time * 0.4f); ly = 0.15f;
			}
		}
		if (!Anim.hasBlock()) {
			// sem bloco, o braço do lado da tela "mexe" nela
			if (Anim.side() > 0) lx -= 0.15f;
			else rx -= 0.15f;
		}
		pose(m.rightArm, rx, ry, rz, t);
		pose(m.leftArm, lx, ly, lz, t);
	}

	private static float smooth(float x) {
		x = Mth.clamp(x, 0, 1);
		return x * x * (3 - 2 * x);
	}

	private static void pose(ModelPart p, float x, float y, float z, float t) {
		p.xRot = Mth.lerp(t, p.xRot, x);
		p.yRot = Mth.lerp(t, p.yRot, y);
		p.zRot = Mth.lerp(t, p.zRot, z);
	}
}
