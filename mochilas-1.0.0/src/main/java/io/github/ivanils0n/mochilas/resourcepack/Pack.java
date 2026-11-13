package io.github.ivanils0n.mochilas.resourcepack;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import io.github.ivanils0n.mochilas.Mochilas;
import io.github.ivanils0n.mochilas.config.MochilasConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resource pack: o Polymer junta os modelos e texturas de assets/mochilas com os de outros mods Polymer e o
 * AutoHost do Polymer o envia ao jogador ao entrar (pela mesma porta do servidor). No singleplayer o Polymer
 * carrega os mesmos arquivos direto no cliente.
 */
public final class Pack {
	private Pack() {}

	public static void init() {
		if (!PolymerResourcePackUtils.addModAssets(Mochilas.MOD_ID)) {
			Mochilas.LOG.error("Polymer não aceitou os assets do mod");
		}
		if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER && MochilasConfig.get().ativarAutohost) {
			enableAutoHost();
		}
	}

	/** Liga "enabled" em config/polymer/auto-host.json (o resto do arquivo fica como está). */
	static void enableAutoHost() {
		Path p = FabricLoader.getInstance().getConfigDir().resolve("polymer").resolve("auto-host.json");
		try {
			JsonObject obj = Files.exists(p) ? JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject() : new JsonObject();
			if (obj.has("enabled") && obj.get("enabled").getAsBoolean()) return;
			obj.addProperty("enabled", true);
			Files.createDirectories(p.getParent());
			Files.writeString(p, new GsonBuilder().setPrettyPrinting().create().toJson(obj), StandardCharsets.UTF_8);
			Mochilas.LOG.info("AutoHost do Polymer ligado em {}: o resource pack das mochilas vai ser enviado aos jogadores", p);
		} catch (Exception e) {
			Mochilas.LOG.error("Não consegui ligar o AutoHost do Polymer ({}); ligue \"enabled\" à mão", p, e);
		}
	}
}
