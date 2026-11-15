package io.github.ivanils0n.tumulo;

import java.util.UUID;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * Um túmulo: onde está, de quem é, quando some e os itens (54 espaços, na ordem da tela). Os itens ficam aqui e em
 * <mundo>/tumulo.json; o corpo (mannequin deitado) e a área clicável (interaction) são só o visual.
 */
final class Grave {
	static final int SIZE = 54;
	/** Espaços da tela: fileiras 1-3 = inventário (9-35), fileira 4 = hotbar (0-8), fileira 5 = armadura e mão secundária, resto = extras. */
	static final int HEAD = 36, CHEST = 37, LEGS = 38, FEET = 39, OFFHAND = 41;
	/** Onde vão os itens que caíram junto com o jogador mas não eram do inventário (ex.: mochila equipada). */
	static final int[] EXTRA = {42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53};

	final UUID id;
	final UUID owner;
	final String name;
	final String dim;
	final double x, y, z;
	final float yaw;
	long expires; // tempo do mundo (overworld) em que some
	int mainhand = -1; // espaço da tela com o item que estava na mão (para o corpo segurar)
	final SimpleContainer items = new SimpleContainer(SIZE) {
		@Override
		public void setChanged() {
			super.setChanged();
			Tumulo.changed(id);
		}
	};

	Grave(UUID id, UUID owner, String name, String dim, double x, double y, double z, float yaw, long expires) {
		this.id = id;
		this.owner = owner;
		this.name = name;
		this.dim = dim;
		this.x = x;
		this.y = y;
		this.z = z;
		this.yaw = yaw;
		this.expires = expires;
	}

	/** Espaço da tela para o espaço do inventário do jogador (0-40). */
	static int slotFor(int inv) {
		if (inv < 9) return 27 + inv; // hotbar
		if (inv < 36) return inv - 9; // inventário
		return switch (inv) {
			case 36 -> FEET;
			case 37 -> LEGS;
			case 38 -> CHEST;
			case 39 -> HEAD;
			default -> OFFHAND; // 40
		};
	}

	/** Espaço do inventário do jogador para o espaço da tela, ou -1 se é extra. */
	static int invFor(int slot) {
		if (slot < 27) return slot + 9;
		if (slot < 36) return slot - 27;
		return switch (slot) {
			case FEET -> 36;
			case LEGS -> 37;
			case CHEST -> 38;
			case HEAD -> 39;
			case OFFHAND -> 40;
			default -> -1;
		};
	}

	/** Coloca um item extra; devolve o que não coube. */
	ItemStack addExtra(ItemStack s) {
		for (int i : EXTRA) {
			if (items.getItem(i).isEmpty()) {
				items.setItem(i, s);
				return ItemStack.EMPTY;
			}
		}
		for (int i = 0; i < SIZE; i++) {
			if (i != 40 && items.getItem(i).isEmpty()) {
				items.setItem(i, s);
				return ItemStack.EMPTY;
			}
		}
		return s;
	}

	boolean isEmpty() {
		return items.isEmpty();
	}

	String tag() {
		return "tumulo_" + id;
	}
}
