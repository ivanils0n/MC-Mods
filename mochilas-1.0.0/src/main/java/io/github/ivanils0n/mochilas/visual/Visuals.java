package io.github.ivanils0n.mochilas.visual;

import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import io.github.ivanils0n.mochilas.backpack.Backpacks;
import io.github.ivanils0n.mochilas.backpack.Tier;
import io.github.ivanils0n.mochilas.equipment.Equipment;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Uma mochila visual por jogador. Recriada ao equipar/retirar, entrar, renascer e trocar de dimensão;
 * apagada ao sair do servidor. O Polymer mostra para quem passa a ver o jogador; o próprio dono é
 * adicionado à mão (o jogo não "rastreia" o próprio jogador).
 */
public final class Visuals {
	private static final Map<UUID, BackpackVisual> BY_PLAYER = new HashMap<>();

	private Visuals() {}

	public static void refresh(ServerPlayer p) {
		remove(p);
		ItemStack s = Equipment.equipped(p);
		Tier t = Backpacks.tier(s);
		if (t == null || p.isRemoved()) return;
		BackpackVisual v = attach(p, t);
		BY_PLAYER.put(p.getUUID(), v);
		v.startWatching(p); // o próprio jogador
		v.sendPacket(new ClientboundSetPassengersPacket(p));
	}

	/** Monta a mochila em qualquer ser vivo com modelo de jogador e mostra para quem já o está vendo. */
	public static BackpackVisual attach(LivingEntity e, Tier t) {
		BackpackVisual v = new BackpackVisual(e, t);
		EntityAttachment att = EntityAttachment.ofTicking(v, e);
		att.updateCurrentlyTracking(List.of());
		v.sendPacket(new ClientboundSetPassengersPacket(e));
		return v;
	}

	public static void remove(ServerPlayer p) {
		BackpackVisual v = BY_PLAYER.remove(p.getUUID());
		if (v != null) {
			v.destroy();
			if (!p.isRemoved() && p.connection != null) p.connection.send(new ClientboundSetPassengersPacket(p));
		}
	}

	public static void removeAll() {
		for (BackpackVisual v : BY_PLAYER.values()) v.destroy();
		BY_PLAYER.clear();
	}

	public static boolean has(ServerPlayer p) {
		return BY_PLAYER.containsKey(p.getUUID());
	}

	public static int count() {
		return BY_PLAYER.size();
	}

	public static BackpackVisual get(ServerPlayer p) {
		return BY_PLAYER.get(p.getUUID());
	}
}
