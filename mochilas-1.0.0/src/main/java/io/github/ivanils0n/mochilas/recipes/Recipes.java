package io.github.ivanils0n.mochilas.recipes;

import java.util.List;

/**
 * Receitas (data/mochilas/recipe):
 * - Couro: 6 couros, 1 linha e 1 baú.
 * - Melhorias (mochila + 1 bloco no grid, "crafting_transmute"): couro + bloco de ferro = ferro; ferro + bloco de
 *   ouro = ouro; ouro + bloco de diamante = diamante. A transmutação copia todos os componentes do item, então o ID
 *   da mochila (e o nome dado na bigorna) passam para a nova: os itens guardados continuam nela.
 */
public final class Recipes {
	public static final List<String> IDS = List.of("mochilas:mochila_couro", "mochilas:mochila_ferro", "mochilas:mochila_ouro", "mochilas:mochila_diamante");

	private Recipes() {}
}
