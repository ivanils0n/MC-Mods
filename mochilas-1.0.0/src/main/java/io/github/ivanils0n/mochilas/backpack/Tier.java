package io.github.ivanils0n.mochilas.backpack;

import io.github.ivanils0n.mochilas.config.MochilasConfig;
import net.minecraft.ChatFormatting;

/** Os quatro níveis. A capacidade vem do config (múltiplo de 9, de 9 a 54). */
public enum Tier {
	COURO("couro", "Couro", ChatFormatting.GOLD, 9),
	FERRO("ferro", "Ferro", ChatFormatting.GRAY, 18),
	OURO("ouro", "Ouro", ChatFormatting.YELLOW, 27),
	DIAMANTE("diamante", "Diamante", ChatFormatting.AQUA, 54);

	public final String id;
	public final String nome;
	public final ChatFormatting cor;
	public final int padrao;

	Tier(String id, String nome, ChatFormatting cor, int padrao) {
		this.id = id;
		this.nome = nome;
		this.cor = cor;
		this.padrao = padrao;
	}

	public int capacidade() {
		return MochilasConfig.get().capacidade(this);
	}

	public String itemPath() {
		return "mochila_" + id;
	}

	public Tier proximo() {
		return ordinal() + 1 < values().length ? values()[ordinal() + 1] : null;
	}

	public static Tier byId(String s) {
		for (Tier t : values()) if (t.id.equalsIgnoreCase(s)) return t;
		return null;
	}
}
