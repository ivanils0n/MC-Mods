package io.github.ivanils0n.mobhealthbar;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

public final class HealthBar {
	private static final int SEGMENTS = 10;
	private static final double MAX_DISTANCE_SQ = 32 * 32;
	private static final AtlasSprite FULL = heart("full");
	private static final AtlasSprite HALF = heart("half");
	private static final AtlasSprite EMPTY = heart("container");

	private HealthBar() {}

	public static void apply(Entity entity, EntityRenderState state, float partialTick) {
		if (!(entity instanceof LivingEntity living) || !living.isAlive()) return;
		Player self = Minecraft.getInstance().player;
		if (self == null || entity == self) return;
		// Outros jogadores: só quando o nome está visível (respeita times/agachar).
		if (!(entity instanceof Mob) && !(entity instanceof Player && state.nameTag != null)) return;
		// Linha de visão evita mostrar a vida através das paredes (nametags atravessam blocos).
		if (state.distanceToCameraSq > MAX_DISTANCE_SQ || entity.isInvisibleTo(self) || !self.hasLineOfSight(entity)) return;

		// scoreText é a linha desenhada logo abaixo do nome.
		Component bar = bar(living);
		state.scoreText = state.scoreText == null ? bar : state.scoreText.copy().append(" ").append(bar);
		if (state.nameTagAttachment == null) {
			state.nameTagAttachment = entity.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, entity.getYRot(partialTick));
		}
	}

	static Component bar(LivingEntity living) {
		float max = Math.max(living.getMaxHealth(), 1);
		float health = Mth.clamp(living.getHealth(), 0, max);
		// 10 corações = 100% (cada meio coração vale 5%), igual à barra de vida do jogador.
		int halves = Mth.ceil(health / max * SEGMENTS * 2);

		MutableComponent text = Component.empty();
		for (int i = 0; i < SEGMENTS; i++) {
			int left = halves - i * 2;
			text.append(Component.object(left >= 2 ? FULL : left == 1 ? HALF : EMPTY));
		}
		text.append(Component.literal(" " + Mth.ceil(health) + "/" + Mth.ceil(max)).withStyle(ChatFormatting.WHITE));
		return text;
	}

	private static AtlasSprite heart(String name) {
		return new AtlasSprite(AtlasIds.GUI, Identifier.withDefaultNamespace("hud/heart/" + name));
	}
}
