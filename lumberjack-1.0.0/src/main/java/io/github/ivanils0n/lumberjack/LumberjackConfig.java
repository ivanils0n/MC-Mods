package io.github.ivanils0n.lumberjack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** config/lumberjack.json: criado na primeira vez com os valores padrão; relido a cada início do servidor. */
final class LumberjackConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	@SerializedName("_exigir_machado")
	String notaMachado = "true: só derruba a árvore inteira quebrando com machado. false: com qualquer coisa (até a mão).";
	@SerializedName("exigir_machado")
	boolean exigirMachado = true;
	@SerializedName("_agachar_desativa")
	String notaAgachar = "true: agachado (Shift) quebra só um tronco, como no jogo normal.";
	@SerializedName("agachar_desativa")
	boolean agacharDesativa = true;
	@SerializedName("_proteger_ferramenta")
	String notaProteger = "true: para de cortar quando o machado fica com 1 de durabilidade, para não quebrar.";
	@SerializedName("proteger_ferramenta")
	boolean protegerFerramenta = true;
	@SerializedName("_exigir_folhas")
	String notaFolhas = "true: só é árvore se os troncos tocam folhas naturais (as que caem sozinhas) ou blocos de verruga do Nether. Protege casas e construções feitas antes de instalar o mod.";
	@SerializedName("exigir_folhas")
	boolean exigirFolhas = true;
	@SerializedName("_juntar_drops")
	String notaDrops = "true: os troncos caem todos no lugar do tronco que você quebrou (não ficam presos nas folhas).";
	@SerializedName("juntar_drops")
	boolean juntarDrops = true;
	@SerializedName("_limite")
	String notaLimite = "Máximo de troncos derrubados de uma vez (além do que você quebrou).";
	@SerializedName("limite")
	int limite = 256;
	@SerializedName("_raio")
	String notaRaio = "Distância máxima na horizontal (em blocos) do tronco quebrado. Evita juntar árvores vizinhas.";
	@SerializedName("raio")
	int raio = 10;

	static LumberjackConfig load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("lumberjack.json");
		LumberjackConfig c = null;
		try {
			if (Files.exists(file)) c = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), LumberjackConfig.class);
		} catch (Exception e) {
			Lumberjack.LOG.error("config/lumberjack.json inválido; usando os valores padrão", e);
		}
		if (c == null) c = new LumberjackConfig();
		c.limite = Math.clamp(c.limite, 1, 4096);
		c.raio = Math.clamp(c.raio, 1, 32);
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(c), StandardCharsets.UTF_8);
		} catch (Exception e) {
			Lumberjack.LOG.warn("Não foi possível gravar config/lumberjack.json", e);
		}
		return c;
	}
}
