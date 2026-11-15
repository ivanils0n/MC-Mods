package io.github.ivanils0n.tumulo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** config/tumulo.json: criado na primeira vez com os valores padrão; relido a cada início do servidor. */
final class TumuloConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	@SerializedName("_minutos")
	String notaMinutos = "Quanto tempo (em minutos de jogo, contando mesmo com ninguém por perto) o corpo fica no chão antes de sumir.";
	@SerializedName("minutos")
	int minutos = 30;
	@SerializedName("_soltar_itens_ao_sumir")
	String notaSoltar = "false: quando o tempo acaba, o corpo some com os itens. true: os itens caem no chão (e somem em 5 minutos, como sempre).";
	@SerializedName("soltar_itens_ao_sumir")
	boolean soltarItensAoSumir = false;
	@SerializedName("_somente_dono")
	String notaDono = "false: qualquer jogador pode pegar os itens do corpo. true: só o dono (e jogadores no criativo).";
	@SerializedName("somente_dono")
	boolean somenteDono = false;

	static TumuloConfig load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("tumulo.json");
		TumuloConfig c = null;
		try {
			if (Files.exists(file)) c = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), TumuloConfig.class);
		} catch (Exception e) {
			Tumulo.LOG.error("config/tumulo.json inválido; usando os valores padrão", e);
		}
		if (c == null) c = new TumuloConfig();
		c.minutos = Math.max(1, c.minutos);
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(c), StandardCharsets.UTF_8);
		} catch (Exception e) {
			Tumulo.LOG.warn("Não foi possível gravar config/tumulo.json", e);
		}
		return c;
	}
}
