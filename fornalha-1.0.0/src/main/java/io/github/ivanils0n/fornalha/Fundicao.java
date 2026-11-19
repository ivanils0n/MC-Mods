package io.github.ivanils0n.fornalha;

import io.github.ivanils0n.fornalha.FornalhaStore.Forno;
import io.github.ivanils0n.fornalha.FornalhaStore.Status;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * O trabalho da fornalha, a cada tick: abastece o tanque com o espaço de combustível, conta o tempo do ciclo e, no
 * fim dele, funde de uma vez até {@link #paralelo} itens da entrada. Usa as receitas do próprio jogo (fornalha,
 * alto-forno ou defumador, conforme o modo), então itens de datapacks funcionam.
 *
 * Espaços do barril do controlador: 0-8 entrada, 9-17 saída, 18 combustível (19-26 não são usados).
 */
final class Fundicao {
	static final int ENTRADA = 0, SAIDA = 9, COMBUSTIVEL = 18, FIM = 27;
	static final long TANQUE_MAX = 64L * 20000; // 64 baldes de lava
	static final int CUSTO = 100; // ticks de queima por item (a fornalha comum gasta 200)
	private static final double[] BONUS = {0, 0.25, 0.33, 0.42, 0.50}; // chance de 1 lingote extra do minério bruto

	private Fundicao() {}

	static int paralelo(int nivel) {
		return switch (nivel) {
			case 4 -> 32;
			case 3 -> 16;
			case 2 -> 8;
			default -> 4;
		};
	}

	static double velocidade(int nivel) {
		return switch (nivel) {
			case 4 -> 4.0;
			case 3 -> 3.0;
			case 2 -> 2.0;
			default -> 1.5;
		};
	}

	/** Ticks de um ciclo: 200 na fornalha, 100 no alto-forno e no defumador (como no jogo), divididos pela velocidade. */
	static int tempoCiclo(Forno f) {
		int base = f.modo == 0 ? 200 : 100;
		return Math.max(5, (int) Math.round(base / velocidade(f.nivel)));
	}

	static AbstractCookingRecipe receita(ServerLevel sl, int modo, ItemStack stack) {
		if (stack.isEmpty()) return null;
		RecipeManager rm = sl.getServer().getRecipeManager();
		SingleRecipeInput in = new SingleRecipeInput(stack);
		return switch (modo) {
			case 1 -> rm.getRecipeFor(RecipeType.BLASTING, in, sl).map(h -> (AbstractCookingRecipe) h.value()).orElse(null);
			case 2 -> rm.getRecipeFor(RecipeType.SMOKING, in, sl).map(h -> (AbstractCookingRecipe) h.value()).orElse(null);
			default -> rm.getRecipeFor(RecipeType.SMELTING, in, sl).map(h -> (AbstractCookingRecipe) h.value()).orElse(null);
		};
	}

	/** Algum dos três tipos de forno funde este item (para aceitar na entrada). */
	static boolean fundivel(ServerLevel sl, ItemStack stack) {
		return receita(sl, 0, stack) != null || receita(sl, 1, stack) != null || receita(sl, 2, stack) != null;
	}

	static boolean combustivel(ServerLevel sl, ItemStack stack) {
		return !stack.isEmpty() && sl.fuelValues().burnDuration(stack) > 0;
	}

	static void tick(ServerLevel sl, Forno f, Container box, boolean redstone) {
		abastecer(sl, f, box);
		if (!f.ligado || redstone) {
			f.status = Status.PAUSADO;
			return;
		}
		f.tempo = tempoCiclo(f);
		int prontos = contar(sl, f, box);
		if (prontos <= 0) {
			if (f.progresso != 0) f.progresso = 0;
			f.status = prontos < 0 ? Status.CHEIO : Status.PARADO;
			return;
		}
		if (f.tanque < CUSTO) {
			f.status = Status.SEM_COMBUSTIVEL;
			return;
		}
		f.status = Status.TRABALHANDO;
		if (++f.progresso < f.tempo) return;
		f.progresso = 0;
		fundir(sl, f, box);
		Fornalha.store.markDirty();
	}

	/** Passa o combustível do espaço 18 para o tanque (um item por tick). O balde vazio vai para a saída. */
	static void abastecer(ServerLevel sl, Forno f, Container box) {
		ItemStack fuel = box.getItem(COMBUSTIVEL);
		if (fuel.isEmpty()) return;
		int burn = sl.fuelValues().burnDuration(fuel);
		if (burn <= 0 || f.tanque + burn > TANQUE_MAX) return;
		if (fuel.is(Items.LAVA_BUCKET)) {
			ItemStack bucket = new ItemStack(Items.BUCKET);
			if (!guardar(box, bucket, SAIDA, COMBUSTIVEL)) return; // sem lugar para o balde: espera
			box.setItem(COMBUSTIVEL, ItemStack.EMPTY);
		} else {
			fuel.shrink(1);
			box.setItem(COMBUSTIVEL, fuel);
		}
		f.tanque += burn;
		box.setChanged();
		Fornalha.store.markDirty();
	}

	/** Quantos itens o próximo ciclo funde (até o paralelo); -1 se há o que fundir mas a saída está cheia. */
	private static int contar(ServerLevel sl, Forno f, Container box) {
		int n = 0, max = paralelo(f.nivel);
		boolean semLugar = false;
		for (int i = ENTRADA; i < SAIDA && n < max; i++) {
			ItemStack s = box.getItem(i);
			AbstractCookingRecipe r = receita(sl, f.modo, s);
			if (r == null) continue;
			ItemStack out = r.assemble(new SingleRecipeInput(s));
			if (out.isEmpty()) continue;
			if (!cabe(box, out)) {
				semLugar = true;
				continue;
			}
			n += Math.min(s.getCount(), max - n);
		}
		return n == 0 && semLugar ? -1 : n;
	}

	private static void fundir(ServerLevel sl, Forno f, Container box) {
		int max = paralelo(f.nivel), feitos = 0;
		RandomSource rnd = sl.getRandom();
		for (int i = ENTRADA; i < SAIDA && feitos < max; i++) {
			while (feitos < max && f.tanque >= CUSTO) {
				ItemStack s = box.getItem(i);
				AbstractCookingRecipe r = receita(sl, f.modo, s);
				if (r == null) break;
				SingleRecipeInput in = new SingleRecipeInput(s);
				ItemStack out = r.assemble(in);
				if (out.isEmpty() || !cabe(box, out)) break;
				String path = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
				boolean bruto = path.startsWith("raw_") && !path.endsWith("_block");
				if (bruto && rnd.nextDouble() < BONUS[Math.clamp(f.nivel, 0, 4)]) out.grow(1);
				ItemStack resto = out.copy();
				guardar(box, resto, SAIDA, COMBUSTIVEL);
				s.shrink(1);
				box.setItem(i, s.isEmpty() ? ItemStack.EMPTY : s);
				f.xp += r.experience();
				f.tanque -= CUSTO;
				feitos++;
			}
		}
		if (feitos > 0) {
			box.setChanged();
			BlockPos p = f.pos();
			sl.playSound(null, p, SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 0.8f, 1.0f);
			var d = f.frente();
			sl.sendParticles(ParticleTypes.FLAME, p.getX() + 0.5 + d.getStepX() * 0.55, p.getY() + 0.5, p.getZ() + 0.5 + d.getStepZ() * 0.55,
				4, 0.2, 0.2, 0.2, 0.01);
		}
	}

	/** O item inteiro cabe na saída? */
	static boolean cabe(Container box, ItemStack stack) {
		int falta = stack.getCount();
		for (int i = SAIDA; i < COMBUSTIVEL && falta > 0; i++) {
			ItemStack s = box.getItem(i);
			if (s.isEmpty()) falta -= stack.getMaxStackSize();
			else if (ItemStack.isSameItemSameComponents(s, stack)) falta -= s.getMaxStackSize() - s.getCount();
		}
		return falta <= 0;
	}

	/** Guarda o item nos espaços [de, ate): primeiro completa pilhas iguais, depois usa espaços vazios. */
	static boolean guardar(Container box, ItemStack stack, int de, int ate) {
		for (int i = de; i < ate && !stack.isEmpty(); i++) {
			ItemStack s = box.getItem(i);
			if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, stack)) {
				int n = Math.min(stack.getCount(), s.getMaxStackSize() - s.getCount());
				if (n > 0) {
					s.grow(n);
					stack.shrink(n);
				}
			}
		}
		for (int i = de; i < ate && !stack.isEmpty(); i++) {
			if (box.getItem(i).isEmpty()) {
				box.setItem(i, stack.copy());
				stack.setCount(0);
			}
		}
		return stack.isEmpty();
	}

	/**
	 * Funis: um baú ou funil em cima da casca do meio do topo abastece a fornalha (itens que ela funde vão para a
	 * entrada, combustível para o espaço de combustível); a saída vai para um baú ou funil embaixo da casca do meio
	 * da base. Roda a cada 8 ticks, um item de cada vez, como um funil.
	 */
	static void funis(ServerLevel sl, Forno f, Container box) {
		BlockPos centro = f.centro();
		BlockEntity cima = sl.getBlockEntity(centro.above(2));
		if (cima instanceof Container de) {
			for (int i = 0; i < de.getContainerSize(); i++) {
				ItemStack s = de.getItem(i);
				if (s.isEmpty()) continue;
				ItemStack um = s.copyWithCount(1);
				boolean ok;
				if (combustivel(sl, um) && (box.getItem(COMBUSTIVEL).isEmpty() || ItemStack.isSameItemSameComponents(box.getItem(COMBUSTIVEL), um))) {
					ok = guardar(box, um, COMBUSTIVEL, COMBUSTIVEL + 1);
				} else if (receita(sl, f.modo, um) != null) {
					ok = guardar(box, um, ENTRADA, SAIDA);
				} else {
					continue;
				}
				if (ok) {
					s.shrink(1);
					de.setItem(i, s.isEmpty() ? ItemStack.EMPTY : s);
					de.setChanged();
					box.setChanged();
					break;
				}
			}
		}
		BlockEntity baixo = sl.getBlockEntity(centro.below(2));
		if (baixo instanceof Container para) {
			for (int i = SAIDA; i < COMBUSTIVEL; i++) {
				ItemStack s = box.getItem(i);
				if (s.isEmpty()) continue;
				ItemStack um = s.copyWithCount(1);
				if (guardarEm(para, um)) {
					s.shrink(1);
					box.setItem(i, s.isEmpty() ? ItemStack.EMPTY : s);
					para.setChanged();
					box.setChanged();
					break;
				}
			}
		}
	}

	private static boolean guardarEm(Container c, ItemStack stack) {
		for (int i = 0; i < c.getContainerSize() && !stack.isEmpty(); i++) {
			ItemStack s = c.getItem(i);
			if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, stack) && s.getCount() < Math.min(s.getMaxStackSize(), c.getMaxStackSize())
				&& c.canPlaceItem(i, stack)) {
				s.grow(1);
				stack.shrink(1);
			}
		}
		for (int i = 0; i < c.getContainerSize() && !stack.isEmpty(); i++) {
			if (c.getItem(i).isEmpty() && c.canPlaceItem(i, stack)) {
				c.setItem(i, stack.copy());
				stack.setCount(0);
			}
		}
		return stack.isEmpty();
	}
}
