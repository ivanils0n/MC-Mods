package io.github.ivanils0n.mochilas.visual;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import io.github.ivanils0n.mochilas.backpack.Backpacks;
import io.github.ivanils0n.mochilas.config.MochilasConfig;
import io.github.ivanils0n.mochilas.backpack.Tier;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Mochila nas costas: um item_display virtual montado no jogador (passageiro), então o cliente o leva junto
 * com o jogador sem nenhum pacote de posição. O servidor só manda a rotação/pose quando o corpo vira mais de
 * 2 graus ou a pose muda (agachar, nadar, planar...), com interpolação curta para ficar suave.
 *
 * Medidas: o passageiro fica no topo da caixa de colisão do jogador. O modelo do jogador é desenhado com escala
 * 0,9375, então 1 pixel dele = 0,9375/16 bloco (o tronco vai de 12 a 24 px acima dos pés).
 */
public class BackpackVisual extends ElementHolder {
	private static final float PX = 0.9375f / 16f;
	/** O modelo da mochila usa 1/16 de bloco por pixel: escala para o pixel do jogador. */
	private static final float SCALE = PX * 16f;
	/** Centro do modelo (z=8) até o centro do corpo: o fundo da mochila (z=6) encosta nas costas (2 px atrás). */
	private static final float BACK_FROM_CENTER = 4 * PX + 0.012f;

	/** Quem usa a mochila: o jogador (ou, nos testes, um manequim). */
	final LivingEntity player;
	final ItemDisplayElement element;
	/**
	 * true: cópia só para o próprio dono (sem as alças da frente, e escondida quando ele olha para baixo, senão
	 * apareceria na 1ª pessoa). false: cópia para todos os outros jogadores.
	 */
	final boolean ownerCopy;
	/** Centro do modelo (y=8) até o ombro: a alça de cima (y=11 no couro, 12 nos outros) apoia no ombro. */
	private final float downFromShoulder;
	private float lastYaw = Float.NaN;
	private float lastPitch = Float.NaN;
	private Pose lastPose = null;
	private boolean lastHidden;

	/** Olhando mais para baixo que isto o dono não vê a própria mochila (na 1ª pessoa ela entraria na tela). */
	static final float OWNER_HIDE_PITCH = 40f;

	public BackpackVisual(LivingEntity player, Tier tier, boolean ownerCopy) {
		this.player = player;
		this.ownerCopy = ownerCopy;
		this.downFromShoulder = ((tier == Tier.COURO ? 11 : 12) - 8) * PX;
		this.element = new ItemDisplayElement(Backpacks.visualStack(tier, ownerCopy));
		this.element.setItemDisplayContext(ItemDisplayContext.NONE);
		this.element.ignorePositionUpdates();
		this.element.setInterpolationDuration(2);
		this.element.setViewRange(1.5f);
		this.update(true);
		this.addPassengerElement(this.element);
	}

	/** Sem o resource pack o cliente desenharia um cubo roxo e preto: quem recusou o pack não vê a mochila. */
	@Override
	public boolean startWatching(ServerGamePacketListenerImpl conn) {
		boolean isOwner = conn.player == player;
		if (isOwner != ownerCopy) return false;
		// No singleplayer/LAN as texturas vêm do próprio mod no cliente do dono do mundo.
		boolean dedicated = conn.player.level().getServer().isDedicatedServer();
		if (dedicated && MochilasConfig.get().visualSoComPack && !PolymerResourcePackUtils.hasMainPack((ServerCommonPacketListenerImpl) conn)) return false;
		return super.startWatching(conn);
	}

	@Override
	protected void onTick() {
		update(false);
	}

	private void update(boolean force) {
		Pose pose = player.getPose();
		boolean hidden = player.isInvisible() || player.isSpectator() || player.isSleeping() || player.isDeadOrDying();
		float yaw = player.yBodyRot;
		boolean prone = pose == Pose.SWIMMING || pose == Pose.FALL_FLYING || player.isFallFlying() || pose == Pose.SPIN_ATTACK;
		float pitch = prone ? player.getXRot() : 0;
		// Cópia do dono: some quando ele olha para baixo e nas poses deitadas (nadando, planando), em que a câmera
		// fica colada na mochila; os outros jogadores continuam vendo a mochila nessas poses.
		if (ownerCopy && (prone || player.getXRot() > OWNER_HIDE_PITCH)) hidden = true;
		if (!force && pose == lastPose && hidden == lastHidden && Math.abs(Mth.wrapDegrees(yaw - lastYaw)) < 2f && Math.abs(pitch - lastPitch) < 3f) {
			return;
		}
		lastPose = pose;
		lastHidden = hidden;
		lastYaw = yaw;
		lastPitch = pitch;

		float th = (float) Math.toRadians(yaw);
		// No jogo, "para trás" do corpo com yaw θ é (sen θ, 0, -cos θ).
		Vector3f back = new Vector3f((float) Math.sin(th), 0, (float) -Math.cos(th));
		Vector3f up = new Vector3f(0, 1, 0);
		float height = player.getBbHeight();
		float tilt; // inclinação do tronco para a frente (rad)
		Vector3f shoulder; // ombro relativo ao topo da caixa de colisão
		if (prone) {
			// nadando / planando: tronco deitado na direção do olhar
			tilt = (float) Math.toRadians(90 + pitch);
			Vector3f fwd = new Vector3f(back).negate();
			shoulder = new Vector3f(0, 0.3f - height, 0).add(new Vector3f(fwd).mul(0.35f));
		} else if (pose == Pose.CROUCHING) {
			tilt = 0.5f;
			// agachado o corpo desce 3,2 px e o modelo inteiro mais 2 px (como no jogo)
			shoulder = new Vector3f(0, (24 - 3.2f - 2f) * PX - height, 0);
		} else {
			tilt = 0;
			shoulder = new Vector3f(0, 24 * PX - height, 0);
		}
		// eixos do tronco já inclinado: "cima" vai para a frente, "costas" vai para cima
		float c = (float) Math.cos(tilt), s = (float) Math.sin(tilt);
		Vector3f torsoUp = new Vector3f(up).mul(c).add(new Vector3f(back).mul(-s));
		Vector3f torsoBack = new Vector3f(back).mul(c).add(new Vector3f(up).mul(s));
		Vector3f translation = new Vector3f(shoulder)
				.add(new Vector3f(torsoUp).mul(-downFromShoulder))
				.add(new Vector3f(torsoBack).mul(BACK_FROM_CENTER + (ownerCopy ? 0.04f : 0f)));
		// O item_display já gira o item 180° em Y; o +z do modelo (frente da mochila) deve apontar para "costas".
		Quaternionf rot = new Quaternionf().rotateY((float) Math.PI - th).rotateX(-tilt).rotateY((float) -Math.PI);

		element.setTranslation(translation);
		element.setLeftRotation(rot);
		float sc = hidden ? 0 : SCALE;
		element.setScale(new Vector3f(sc, sc, sc));
		if (!force) element.startInterpolation();
	}
}
