package io.github.ivanils0n.fornalha;

import io.github.ivanils0n.fornalha.FornalhaStore.Forno;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Tela da fornalha (baú de 6 fileiras do jogo, então funciona sem mod no cliente). Os espaços do menu seguem a grade
 * da tela, então cada célula é um espaço do barril ou um botão:
 * <pre>
 *  E E E . S . O O O     E = entrada (barril 0-8)    O = saída (barril 9-17, só tirar)
 *  E E E P P P O O O     S = situação (chama)        P = barra de progresso (3 pedaços)
 *  E E E . I . O O O     I = nível / núcleo          T = tanque (3 pedaços)
 *  T . . . X . . . .     X = XP guardado (clique para coletar)
 *  T . . . . . . . .     C = combustível (barril 18)
 *  T C . . . . M L .     M = modo (fornalha, alto-forno, defumador)    L = liga/desliga
 * </pre>
 */
final class FornalhaMenu extends AbstractContainerMenu {
	static final int CELLS = 54;
	static final int STATUS = 4, INFO = 22, XP = 31, FUEL = 46, MODO = 51, LIGA = 52;
	static final int[] PROG = {12, 13, 14}, TANQUE = {27, 36, 45};
	private static final String[] MODOS = {"Tudo (fornalha)", "Minérios (alto-forno)", "Comida (defumador)"};

	private final BarrelBlockEntity be;
	private final Forno forno;
	private final ServerLevel sl;
	private final SimpleContainer controls = new SimpleContainer(CELLS);
	private final int[] barril = new int[CELLS]; // célula -> espaço do barril (-1 = botão/decoração)
	private final boolean pack;
	private String visto = "";

	FornalhaMenu(int id, Inventory inv, BarrelBlockEntity be, Forno forno, boolean pack) {
		super(MenuType.GENERIC_9x6, id);
		this.be = be;
		this.forno = forno;
		this.pack = pack;
		this.sl = (ServerLevel) be.getLevel();
		for (int cell = 0; cell < CELLS; cell++) {
			int r = cell / 9, c = cell % 9, x = 8 + c * 18, y = 18 + r * 18;
			int slot = -1;
			if (r < 3 && c < 3) slot = Fundicao.ENTRADA + r * 3 + c;
			else if (r < 3 && c > 5) slot = Fundicao.SAIDA + r * 3 + (c - 6);
			else if (cell == FUEL) slot = Fundicao.COMBUSTIVEL;
			barril[cell] = slot;
			if (slot < 0) {
				addSlot(new Slot(controls, cell, x, y) {
					@Override
					public boolean mayPlace(ItemStack stack) {
						return false;
					}

					@Override
					public boolean mayPickup(Player player) {
						return false;
					}
				});
			} else if (slot >= Fundicao.SAIDA && slot < Fundicao.COMBUSTIVEL) {
				addSlot(new Slot(be, slot, x, y) {
					@Override
					public boolean mayPlace(ItemStack stack) {
						return false;
					}
				});
			} else if (slot == Fundicao.COMBUSTIVEL) {
				addSlot(new Slot(be, slot, x, y) {
					@Override
					public boolean mayPlace(ItemStack stack) {
						return Fundicao.combustivel(sl, stack);
					}
				});
			} else {
				addSlot(new Slot(be, slot, x, y) {
					@Override
					public boolean mayPlace(ItemStack stack) {
						return Fundicao.fundivel(sl, stack);
					}
				});
			}
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
		}
		for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 8 + col * 18, 198));
		refresh();
	}

	// ---------- botões ----------

	private static int nivel(long v, long max, int n) {
		if (v <= 0) return 0;
		return (int) Math.max(1, Math.min(n, (v * n + max - 1) / max));
	}

	/** Só refaz os botões quando algo que eles mostram mudou. */
	private void refresh() {
		int prog = forno.formado && forno.tempo > 0 ? nivel(forno.progresso, forno.tempo, 24) : 0;
		int tanque = nivel(forno.tanque, Fundicao.TANQUE_MAX, 24);
		String sig = forno.status + "|" + prog + "|" + tanque + "|" + (int) forno.xp + "|" + forno.modo + "|" + forno.ligado + "|" + forno.nivel;
		if (sig.equals(visto)) return;
		visto = sig;
		for (int cell = 0; cell < CELLS; cell++) if (barril[cell] < 0) controls.setItem(cell, ItemStack.EMPTY);

		controls.setItem(STATUS, status());
		for (int i = 0; i < 3; i++) {
			int k = Math.clamp(prog - i * 8, 0, 8);
			ItemStack s = pack ? icon("prog_" + k) : vidro(k == 8 ? Items.GOLD_INGOT : k > 0 ? Items.GOLD_NUGGET : Items.GLASS_PANE);
			nome(s, "Progresso: " + (forno.tempo > 0 ? forno.progresso * 100 / forno.tempo : 0) + "%", ChatFormatting.GOLD,
				"Ciclo de " + fmtSeg(forno.tempo) + ": funde até " + Fundicao.paralelo(forno.nivel) + " itens de uma vez.");
			controls.setItem(PROG[i], s);
		}
		// tanque: de baixo (45) para cima (27)
		long pct = forno.tanque * 100 / Fundicao.TANQUE_MAX;
		for (int i = 0; i < 3; i++) {
			int k = Math.clamp(tanque - (2 - i) * 8, 0, 8);
			ItemStack s = pack ? icon("tanque_" + k) : vidro(k > 0 ? Items.LAVA_BUCKET : Items.BUCKET);
			nome(s, "Combustível: " + pct + "%", ChatFormatting.GOLD,
				"Dá para fundir " + forno.tanque / Fundicao.CUSTO + " itens.",
				"Ponha carvão, lava ou outro combustível no espaço ao lado.",
				"Cabe até 64 baldes de lava.");
			controls.setItem(TANQUE[i], s);
		}

		ItemStack nucleo = new ItemStack(switch (forno.nivel) {
			case 4 -> Items.NETHERITE_BLOCK;
			case 3 -> Items.DIAMOND_BLOCK;
			case 2 -> Items.GOLD_BLOCK;
			default -> Items.IRON_BLOCK;
		});
		nome(nucleo, "Núcleo: " + Fornalha.NIVEIS[forno.nivel], ChatFormatting.AQUA,
			Fundicao.paralelo(forno.nivel) + " itens por vez, " + Fornalha.fmt(Fundicao.velocidade(forno.nivel)) + "x mais rápida",
			"Minério bruto: " + (int) Math.round(new double[] {0, 25, 33, 42, 50}[forno.nivel]) + "% de chance de 1 lingote extra",
			"Troque o bloco do centro para mudar de nível.");
		controls.setItem(INFO, nucleo);

		int pontos = (int) forno.xp;
		ItemStack xp = new ItemStack(Items.EXPERIENCE_BOTTLE, Math.clamp(pontos, 1, 64));
		xp.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, pontos > 0);
		nome(xp, "XP guardado: " + pontos + (pontos == 1 ? " ponto" : " pontos"), ChatFormatting.GREEN,
			pontos > 0 ? "Clique para coletar" : "A fundição guarda o XP aqui.");
		controls.setItem(XP, xp);

		ItemStack modo = new ItemStack(switch (forno.modo) {
			case 1 -> Items.BLAST_FURNACE;
			case 2 -> Items.SMOKER;
			default -> Items.FURNACE;
		});
		nome(modo, "Modo: " + MODOS[forno.modo], ChatFormatting.YELLOW,
			"Clique para trocar",
			"Tudo: qualquer receita de fornalha.",
			"Minérios e Comida: só as receitas do alto-forno",
			"ou do defumador, com o ciclo na metade do tempo.");
		controls.setItem(MODO, modo);

		ItemStack liga = pack ? icon(forno.ligado ? "ligado" : "desligado") : new ItemStack(forno.ligado ? Items.REDSTONE_TORCH : Items.LEVER);
		nome(liga, forno.ligado ? "Ligada" : "Desligada", forno.ligado ? ChatFormatting.GREEN : ChatFormatting.RED,
			"Clique para " + (forno.ligado ? "desligar" : "ligar"),
			"Sinal de redstone no Controlador também pausa.");
		controls.setItem(LIGA, liga);
	}

	private ItemStack status() {
		Item item;
		String titulo, linha;
		ChatFormatting cor;
		switch (forno.status) {
			case TRABALHANDO -> { item = Items.BLAZE_POWDER; titulo = "Fundindo"; linha = "Trabalhando."; cor = ChatFormatting.GREEN; }
			case PAUSADO -> { item = Items.REDSTONE; titulo = "Pausada"; linha = "Desligada no botão ou por sinal de redstone."; cor = ChatFormatting.RED; }
			case SEM_COMBUSTIVEL -> { item = Items.COAL; titulo = "Sem combustível"; linha = "Ponha combustível no espaço embaixo à esquerda."; cor = ChatFormatting.GOLD; }
			case CHEIO -> { item = Items.CHEST; titulo = "Saída cheia"; linha = "Tire itens da saída ou ponha um funil embaixo da estrutura."; cor = ChatFormatting.GOLD; }
			default -> { item = Items.GUNPOWDER; titulo = "Parada"; linha = "Nada para fundir neste modo."; cor = ChatFormatting.GRAY; }
		}
		String modelo = "st_" + forno.status.name().toLowerCase(java.util.Locale.ROOT);
		ItemStack s = pack ? icon(modelo) : new ItemStack(item);
		return nome(s, titulo, cor, linha);
	}

	private ItemStack icon(String model) {
		ItemStack s = new ItemStack(Items.PAPER);
		s.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("fornalha", model));
		return s;
	}

	private static ItemStack vidro(Item item) {
		ItemStack s = new ItemStack(item);
		s.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
		return s;
	}

	private static ItemStack nome(ItemStack s, String titulo, ChatFormatting cor, String... linhas) {
		s.set(DataComponents.CUSTOM_NAME, plain(titulo, cor));
		List<Component> lore = new java.util.ArrayList<>();
		for (String l : linhas) lore.add(plain(l, l.startsWith("Clique") ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
		s.set(DataComponents.LORE, new ItemLore(lore));
		return s;
	}

	private static Component plain(String text, ChatFormatting color) {
		return Component.literal(text).withStyle(st -> st.withItalic(false).withColor(color));
	}

	private static String fmtSeg(int ticks) {
		return Fornalha.fmt(Math.round(ticks / 2.0) / 10.0) + " s";
	}

	// ---------- cliques ----------

	@Override
	public void broadcastChanges() {
		refresh();
		super.broadcastChanges();
	}

	@Override
	public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
		if (slotId >= 0 && slotId < CELLS && barril[slotId] < 0) { // botão: nunca pega nem põe item
			if (clickType == ContainerInput.PICKUP && player instanceof ServerPlayer sp) {
				boolean som = true;
				if (slotId == MODO) {
					forno.modo = (forno.modo + 1) % 3;
					forno.progresso = 0;
				} else if (slotId == LIGA) {
					forno.ligado = !forno.ligado;
				} else if (slotId == XP && forno.xp >= 1) {
					int pontos = (int) forno.xp;
					forno.xp -= pontos;
					sp.giveExperiencePoints(pontos);
					sl.playSound(null, sp.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 1.0f);
					som = false;
				} else {
					som = false;
				}
				if (som) sl.playSound(null, sp.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.4f, 1.2f);
				Fornalha.store.markDirty();
			}
			visto = "";
			refresh();
			broadcastFullState();
			return;
		}
		super.clicked(slotId, button, clickType, player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem() || (index < CELLS && barril[index] < 0)) return ItemStack.EMPTY;
		ItemStack stack = slot.getItem();
		ItemStack copy = stack.copy();
		if (index < CELLS) {
			if (!moveItemStackTo(stack, CELLS, slots.size(), true)) return ItemStack.EMPTY;
		} else {
			boolean moved = false;
			if (Fundicao.combustivel(sl, stack)) moved = moveItemStackTo(stack, FUEL, FUEL + 1, false);
			if (!stack.isEmpty() && Fundicao.fundivel(sl, stack)) {
				for (int cell = 0; cell < CELLS && !stack.isEmpty(); cell++) {
					int b = barril[cell];
					if (b >= Fundicao.ENTRADA && b < Fundicao.SAIDA && moveItemStackTo(stack, cell, cell + 1, false)) moved = true;
				}
			}
			if (!moved) return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
		else slot.setChanged();
		return copy;
	}

	@Override
	public boolean stillValid(Player player) {
		return Fornalha.store != null && !be.isRemoved() && forno.formado
			&& Fornalha.store.forno(forno.dim, forno.pos()) == forno
			&& player.distanceToSqr(Vec3.atCenterOf(forno.pos())) <= 64;
	}

	Container controls() {
		return controls;
	}
}
