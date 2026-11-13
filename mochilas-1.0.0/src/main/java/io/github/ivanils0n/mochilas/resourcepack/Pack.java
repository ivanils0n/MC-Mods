package io.github.ivanils0n.mochilas.resourcepack;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import io.github.ivanils0n.mochilas.Mochilas;
import io.github.ivanils0n.mochilas.config.MochilasConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;

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
		// Prioridade: no fim da montagem do pack (depois de todos os mods), grava de novo os arquivos das mochilas
		// por cima, então texturas, modelos e os shaders item/entity delas sempre prevalecem.
		PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(b -> b.addPreFinishTask(Pack::forceOurs));
		if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER && MochilasConfig.get().ativarAutohost) {
			enableAutoHost();
		}
	}

	/** Pastas do .jar que são das mochilas e precisam vencer qualquer outro mod. */
	private static final String[] OURS = {"assets/mochilas", "assets/minecraft/shaders/core"};

	static void forceOurs(ResourcePackBuilder builder) {
		var mod = FabricLoader.getInstance().getModContainer(Mochilas.MOD_ID).orElse(null);
		if (mod == null) return;
		int n = 0;
		for (String dir : OURS) {
			var root = mod.findPath(dir).orElse(null);
			if (root == null) continue;
			try (Stream<Path> files = Files.walk(root)) {
				for (Path f : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
					String rel = root.relativize(f).toString().replace('\\', '/');
					String target = dir + "/" + rel;
					byte[] ours = Files.readAllBytes(f);
					byte[] cur = builder.getData(target);
					if (cur != null && !Arrays.equals(cur, ours)) {
						Mochilas.LOG.warn("Outro mod/pack mudou {}; usando o das mochilas", target);
					}
					builder.addData(target, ours);
					n++;
				}
			} catch (Exception e) {
				Mochilas.LOG.error("Não consegui pôr os arquivos das mochilas no resource pack ({})", dir, e);
			}
		}
		Mochilas.LOG.info("Resource pack: {} arquivos das mochilas com prioridade", n);
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
