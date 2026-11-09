package io.github.ivanils0n.mobhealthbar;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

public final class HealthBar {
	private static final int SEGMENTS = 10;
	private static final int CACHE_LIMIT = 1024;
	// [hardcore][cheio, meio, vazio]
	private static final AtlasSprite[][] HEARTS = {
			{heart("full"), heart("half"), heart("container")},
			{heart("hardcore_full"), heart("hardcore_half"), heart("container_hardcore")},
	};

	// Mesma vida/máxima/modo gera sempre o mesmo texto: monta uma vez e reaproveita entre frames e mobs.
	private static final Long2ObjectOpenHashMap<Component> BARS = new Long2ObjectOpenHashMap<>();
	// Linha de visão (raycast) calculada no máximo uma vez por tick por mob: id -> tick*2 + visível.
	private static final Int2LongOpenHashMap SIGHT = new Int2LongOpenHashMap();

	private HealthBar() {}

	public static void apply(Entity entity, EntityRenderState state, float partialTick) {
		if (!(entity instanceof LivingEntity living) || !living.isAlive()) return;
		Minecraft mc = Minecraft.getInstance();
		Player self = mc.player;
		ClientLevel level = mc.level;
		if (self == null || level == null || entity == self) return;

		if (entity instanceof Player) {
			// Outros jogadores: a vida segue o nome (atravessa paredes e some junto com ele).
			if (state.nameTag == null) return;
		} else if (!(entity instanceof Mob) || entity.isInvisibleTo(self) || !canSee(self, entity, level.getGameTime())) {
			// Mobs: até a distância de renderização do jogo, mas só com linha de visão.
			return;
		}

		// scoreText é a linha desenhada logo abaixo do nome.
		Component bar = bar(living, level.getLevelData().isHardcore());
		state.scoreText = state.scoreText == null ? bar : state.scoreText.copy().append(" ").append(bar);
		if (state.nameTagAttachment == null) {
			state.nameTagAttachment = entity.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, entity.getYRot(partialTick));
		}
	}

	private static boolean canSee(Player self, Entity entity, long tick) {
		long cached = SIGHT.getOrDefault(entity.getId(), -1L);
		if (cached >= 0 && cached >> 1 == tick) return (cached & 1) == 1;
		if (SIGHT.size() > CACHE_LIMIT) SIGHT.clear();
		boolean visible = self.hasLineOfSight(entity);
		SIGHT.put(entity.getId(), tick << 1 | (visible ? 1 : 0));
		return visible;
	}

	static Component bar(LivingEntity living, boolean hardcore) {
		int max = Math.max(Mth.ceil(living.getMaxHealth()), 1);
		float health = Mth.clamp(living.getHealth(), 0, max);
		// 10 corações = 100% (cada meio coração vale 5%), igual à barra de vida do jogador.
		int halves = Mth.ceil(health / max * SEGMENTS * 2);
		int shown = Mth.ceil(health);

		long key = (long) max << 33 | (long) shown << 6 | (long) halves << 1 | (hardcore ? 1 : 0);
		Component cached = BARS.get(key);
		if (cached != null) return cached;
		if (BARS.size() > CACHE_LIMIT) BARS.clear();

		AtlasSprite[] sprites = HEARTS[hardcore ? 1 : 0];
		MutableComponent text = Component.empty();
		for (int i = 0; i < SEGMENTS; i++) {
			int left = halves - i * 2;
			text.append(Component.object(sprites[left >= 2 ? 0 : left == 1 ? 1 : 2]));
		}
		text.append(Component.literal(" " + shown + "/" + max).withStyle(ChatFormatting.WHITE));
		BARS.put(key, text);
		return text;
	}

	private static AtlasSprite heart(String name) {
		return new AtlasSprite(AtlasIds.GUI, Identifier.withDefaultNamespace("hud/heart/" + name));
	}
}
