package io.github.ivanils0n.itemtimer;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

public final class DespawnTimer {
	private static final int LIFETIME = 6000; // ItemEntity some com idade 6000 ticks (5 min)
	private static final int NO_DESPAWN = -32768; // idade especial de itens que nunca somem
	private static final double RANGE_SQR = 64 * 64; // o jogo só desenha nomes até 64 blocos
	static final double NEAR_SQR = 1.5 * 1.5; // itens a até 1,5 bloco um do outro (em cadeia) contam como o mesmo lugar
	static final int SAME_DROP = 40; // e dropados com até 2 s de diferença contam como "juntos"

	// Singleplayer: idade real vinda do servidor integrado (escrita na thread do servidor, lida aqui).
	public static final Map<Integer, Integer> SERVER_AGE = new ConcurrentHashMap<>();

	// Cronômetro de cada grupo, desenhado só no item "líder": id do líder -> ticks restantes.
	private static final Int2IntOpenHashMap LABELS = new Int2IntOpenHashMap();
	private static ClientLevel groupedLevel;
	private static long groupedTick = Long.MIN_VALUE;

	private DespawnTimer() {}

	public static void apply(Entity entity, EntityRenderState state, float partialTick) {
		if (!(entity instanceof ItemEntity)) return;
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		Player self = mc.player;
		if (level == null || self == null) return;

		regroup(mc, level, self);
		int remaining = LABELS.getOrDefault(entity.getId(), -1);
		if (remaining < 0) return;

		Component timer = format(remaining);
		// Item renomeado já tem nome: o cronômetro vai ao lado.
		state.nameTag = state.nameTag == null ? timer : state.nameTag.copy().append(" ").append(timer);
		if (state.nameTagAttachment == null) {
			state.nameTagAttachment = entity.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, entity.getYRot(partialTick));
		}
	}

	// Agrupa os itens uma vez por tick (não por frame).
	private static void regroup(Minecraft mc, ClientLevel level, Player self) {
		long tick = level.getGameTime();
		if (level == groupedLevel && tick == groupedTick) return;
		if (level != groupedLevel) SERVER_AGE.clear();
		groupedLevel = level;
		groupedTick = tick;

		boolean singleplayer = mc.hasSingleplayerServer();
		List<ItemEntity> items = new ArrayList<>();
		List<Integer> remaining = new ArrayList<>();
		for (Entity e : level.entitiesForRendering()) {
			if (!(e instanceof ItemEntity item) || !item.isAlive() || item.distanceToSqr(self) > RANGE_SQR) continue;
			// Multiplayer: o cliente só sabe a idade desde que o item apareceu para ele.
			int age = singleplayer ? SERVER_AGE.getOrDefault(item.getId(), item.getAge()) : item.getAge();
			if (age == NO_DESPAWN) continue;
			items.add(item);
			remaining.add(Math.max(LIFETIME - age, 0));
		}
		// Esquece itens que já sumiram.
		if (singleplayer && tick % 100 == 0) SERVER_AGE.keySet().removeIf(id -> level.getEntity(id) == null);

		int n = items.size();
		double[] x = new double[n], y = new double[n], z = new double[n];
		int[] rem = new int[n], ids = new int[n];
		for (int i = 0; i < n; i++) {
			ItemEntity item = items.get(i);
			x[i] = item.getX();
			y[i] = item.getY();
			z[i] = item.getZ();
			rem[i] = remaining.get(i);
			ids[i] = item.getId();
		}
		LABELS.clear();
		int[] group = group(x, y, z, rem, ids);
		for (int i = 0; i < n; i++) if (group[i] >= 0) LABELS.put(ids[i], group[i]);
	}

	/**
	 * Para cada item devolve o tempo restante do grupo (o do primeiro item a sumir) se ele for o líder
	 * do grupo (menor id, para o rótulo não pular entre itens), ou -1 se não for.
	 * shortcut: O(n²) por tick nos itens até 64 blocos; trocar por grade espacial se fazendas com milhares de itens pesarem.
	 */
	static int[] group(double[] x, double[] y, double[] z, int[] rem, int[] ids) {
		int n = rem.length;
		int[] out = new int[n];
		java.util.Arrays.fill(out, -1);
		boolean[] done = new boolean[n];
		int[] queue = new int[n];
		for (int seed = 0; seed < n; seed++) {
			if (done[seed]) continue;
			done[seed] = true;
			int head = 0, tail = 0, leader = seed, min = rem[seed];
			queue[tail++] = seed;
			while (head < tail) {
				int a = queue[head++];
				for (int b = 0; b < n; b++) {
					if (done[b] || Math.abs(rem[b] - rem[seed]) > SAME_DROP) continue;
					double dx = x[a] - x[b], dy = y[a] - y[b], dz = z[a] - z[b];
					if (dx * dx + dy * dy + dz * dz > NEAR_SQR) continue;
					done[b] = true;
					queue[tail++] = b;
					if (ids[b] < ids[leader]) leader = b;
					min = Math.min(min, rem[b]);
				}
			}
			out[leader] = min;
		}
		return out;
	}

	static Component format(int ticks) {
		int s = (ticks + 19) / 20;
		ChatFormatting color = s <= 10 ? ChatFormatting.RED : s <= 60 ? ChatFormatting.YELLOW : ChatFormatting.WHITE;
		return Component.literal(String.format("%d:%02d", s / 60, s % 60)).withStyle(color);
	}
}
