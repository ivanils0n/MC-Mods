package io.github.ivanils0n.mochilas.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import io.github.ivanils0n.mochilas.Mochilas;
import io.github.ivanils0n.mochilas.backpack.Tier;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** config/mochilas.json. Recarrega com /backpack reload. */
public final class MochilasConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static MochilasConfig current = new MochilasConfig();

	@SerializedName("_capacidades")
	public String notaCapacidades = "Espaços de cada mochila: múltiplo de 9, de 9 a 54. Itens que passarem do novo limite voltam para o jogador ao abrir.";
	@SerializedName("capacidades")
	public Map<String, Integer> capacidades = new LinkedHashMap<>();
	@SerializedName("_soltar_ao_morrer")
	public String notaMorte = "true: a mochila equipada cai no chão quando o jogador morre (os itens continuam guardados nela). Com keepInventory ela fica.";
	@SerializedName("soltar_ao_morrer")
	public boolean soltarAoMorrer = true;
	@SerializedName("_visual_so_com_pack")
	public String notaVisual = "true: quem recusou o resource pack não vê as mochilas nas costas (sem ele apareceria um cubo roxo e preto).";
	@SerializedName("visual_so_com_pack")
	public boolean visualSoComPack = true;
	@SerializedName("_ativar_autohost")
	public String notaAutohost = "true: liga o envio automático do resource pack do Polymer (config/polymer/auto-host.json) ao iniciar o servidor.";
	@SerializedName("ativar_autohost")
	public boolean ativarAutohost = true;

	public MochilasConfig() {
		for (Tier t : Tier.values()) capacidades.put(t.id, t.padrao);
	}

	public static MochilasConfig get() {
		return current;
	}

	public int capacidade(Tier t) {
		int v = capacidades.getOrDefault(t.id, t.padrao);
		v = Math.clamp(v, 9, 54);
		return (v + 8) / 9 * 9;
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("mochilas.json");
	}

	/** Lê o arquivo (cria com os valores padrão se faltar) e regrava com os campos que faltarem. */
	public static MochilasConfig load() {
		MochilasConfig cfg = new MochilasConfig();
		Path p = path();
		try {
			if (Files.exists(p)) {
				MochilasConfig read = GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), MochilasConfig.class);
				if (read != null) {
					for (Tier t : Tier.values()) read.capacidades.putIfAbsent(t.id, t.padrao);
					cfg = read;
				}
			}
			Files.createDirectories(p.getParent());
			Files.writeString(p, GSON.toJson(cfg), StandardCharsets.UTF_8);
		} catch (Exception e) {
			Mochilas.LOG.error("Não consegui ler {}; usando os valores padrão", p, e);
		}
		current = cfg;
		return cfg;
	}
}
