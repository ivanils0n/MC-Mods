package io.github.ivanils0n.mobhealthbar;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
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

		// Jogadores: linha extra abaixo do nome (só quando o nome já está visível).
		if (state instanceof AvatarRenderState avatar) {
			if (entity instanceof Player && avatar.nameTag != null) avatar.scoreText = bar(living);
			return;
		}

		if (!(entity instanceof Mob)) return;
		Player player = Minecraft.getInstance().player;
		// Linha de visão evita mostrar mobs através das paredes (nametags atravessam blocos).
		if (player == null || state.distanceToCameraSq > MAX_DISTANCE_SQ
				|| entity.isInvisibleTo(player) || !player.hasLineOfSight(entity)) return;

		Component bar = bar(living);
		state.nameTag = state.nameTag == null ? bar : state.nameTag.copy().append(" ").append(bar);
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
