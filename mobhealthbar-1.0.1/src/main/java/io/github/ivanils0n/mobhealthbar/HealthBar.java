package io.github.ivanils0n.mobhealthbar;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

public final class HealthBar {
	private static final int SEGMENTS = 10;
	private static final double MAX_DISTANCE_SQ = 32 * 32;

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
		float ratio = health / max;
		int filled = Mth.ceil(ratio * SEGMENTS);
		ChatFormatting color = ratio > 0.5f ? ChatFormatting.GREEN : ratio > 0.25f ? ChatFormatting.YELLOW : ChatFormatting.RED;

		MutableComponent text = Component.literal("■".repeat(filled)).withStyle(color);
		text.append(Component.literal("■".repeat(SEGMENTS - filled)).withStyle(ChatFormatting.DARK_GRAY));
		text.append(Component.literal(" " + Mth.ceil(health) + "/" + Mth.ceil(max)).withStyle(ChatFormatting.WHITE));
		return text;
	}
}
