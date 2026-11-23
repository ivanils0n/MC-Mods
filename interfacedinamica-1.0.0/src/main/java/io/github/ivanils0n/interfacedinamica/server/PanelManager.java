package io.github.ivanils0n.interfacedinamica.server;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Um painel por jogador com uma tela aberta (fora o próprio inventário, que o servidor não vê abrir). */
public final class PanelManager {
	private static final Map<UUID, MenuPanel> PANELS = new HashMap<>();
	private static final Map<UUID, Component> TITLES = new HashMap<>();

	private PanelManager() {}

	public static void rememberTitle(ServerPlayer player, Component title) {
		TITLES.put(player.getUUID(), title);
	}

	public static void tick(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			AbstractContainerMenu menu = p.containerMenu;
			MenuPanel panel = PANELS.get(p.getUUID());
			boolean open = menu != null && menu != p.inventoryMenu && !p.isSpectator() && !p.isRemoved();
			if (!open) {
				if (panel != null) {
					panel.destroy();
					PANELS.remove(p.getUUID());
				}
				continue;
			}
			if (panel != null && (panel.menu != menu || panel.anchor != p)) {
				panel.destroy();
				panel = null;
			}
			if (panel == null) {
				Component title = TITLES.getOrDefault(p.getUUID(), Component.empty());
				PANELS.put(p.getUUID(), MenuPanel.create(p, p, menu, title));
			}
		}
		// Jogadores que saíram
		Iterator<Map.Entry<UUID, MenuPanel>> it = PANELS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, MenuPanel> e = it.next();
			if (server.getPlayerList().getPlayer(e.getKey()) == null) {
				e.getValue().destroy();
				it.remove();
				TITLES.remove(e.getKey());
			}
		}
	}

	public static int count() {
		return PANELS.size();
	}

	public static void clear() {
		PANELS.values().forEach(MenuPanel::destroy);
		PANELS.clear();
		TITLES.clear();
	}
}
