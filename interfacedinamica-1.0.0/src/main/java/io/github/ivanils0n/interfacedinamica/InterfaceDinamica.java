package io.github.ivanils0n.interfacedinamica;

import io.github.ivanils0n.interfacedinamica.server.PanelManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parte do servidor (também roda no singleplayer): quando um jogador abre um baú, fornalha, mesa etc., os outros
 * jogadores veem a tela flutuando ao lado dele, com os itens. Tudo é feito com entidades virtuais (só pacotes),
 * nada é salvo no mundo: tirar o mod não deixa sobras.
 */
public class InterfaceDinamica implements ModInitializer {
	public static final String MOD_ID = "interfacedinamica";
	public static final Logger LOG = LoggerFactory.getLogger("Interface Dinâmica");

	@Override
	public void onInitialize() {
		ServerTickEvents.END_SERVER_TICK.register(PanelManager::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> PanelManager.clear());
	}
}
