package io.github.ivanils0n.chunkmap.seed;

import io.github.ivanils0n.chunkmap.ChunkMap;
import io.github.ivanils0n.chunkmap.Workers;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Onde ficam as estruturas de uma seed, calculado aos poucos (por blocos de regiões) em segundo plano e guardado.
 * Cada conjunto de estruturas do jogo (vilas, templos, postos...) tem uma grade: em cada região dela há no máximo um
 * chunk candidato, e o SeedWorld confere se a estrutura nasce ali.
 */
public final class StructureFinder {
	/** Acima disso de regiões na tela para um conjunto, ele não é calculado (pede para aproximar). */
	private static final int MAX_REGIONS = 12_000;
	private static final SeedWorld.Found[] NONE = new SeedWorld.Found[0];

	private final SeedWorld world;
	private final List<Spread> spreads = new ArrayList<>();
	private final List<Rings> rings = new ArrayList<>();
	private volatile boolean closed;
	/** Conta as chamadas de collect: um pedido que ninguém mais olha há muito tempo é descartado. */
	private volatile long frame;

	private static final class Spread {
		final Holder<StructureSet> set;
		final RandomSpreadStructurePlacement placement;
		final EnumSet<StructureIcon> icons;
		/** Regiões por bloco de cálculo (cada bloco cobre ~32 chunks de lado). */
		final int block;
		final ConcurrentHashMap<Long, SeedWorld.Found[]> done = new ConcurrentHashMap<>();
		/** Bloco pedido -> quadro em que foi pedido pela última vez. */
		final ConcurrentHashMap<Long, Long> pending = new ConcurrentHashMap<>();

		Spread(Holder<StructureSet> set, RandomSpreadStructurePlacement placement, EnumSet<StructureIcon> icons) {
			this.set = set;
			this.placement = placement;
			this.icons = icons;
			this.block = Math.max(1, 32 / Math.max(1, placement.spacing()));
		}
	}

	private static final class Rings {
		final EnumSet<StructureIcon> icons;
		volatile SeedWorld.Found[] found;

		Rings(EnumSet<StructureIcon> icons) {
			this.icons = icons;
		}
	}

	public StructureFinder(SeedWorld world) {
		this.world = world;
		for (Holder<StructureSet> set : world.state.possibleStructureSets()) {
			EnumSet<StructureIcon> icons = EnumSet.noneOf(StructureIcon.class);
			for (StructureSet.StructureSelectionEntry e : set.value().structures()) {
				StructureIcon icon = StructureIcon.forStructure(SeedWorld.id(e.structure()));
				if (icon != null) icons.add(icon);
			}
			if (icons.isEmpty()) continue;
			StructurePlacement placement = set.value().placement();
			if (placement instanceof RandomSpreadStructurePlacement spread) {
				spreads.add(new Spread(set, spread, icons));
			} else if (placement instanceof ConcentricRingsStructurePlacement ring) {
				Rings r = new Rings(icons);
				rings.add(r);
				Workers.SLOW.execute(() -> computeRings(set, ring, r));
			}
		}
	}

	private void computeRings(Holder<StructureSet> set, ConcentricRingsStructurePlacement ring, Rings out) {
		if (closed) return;
		try {
			List<ChunkPos> positions = world.state.getRingPositionsFor(ring);
			if (positions == null) {
				out.found = NONE;
				return;
			}
			StructureIcon icon = out.icons.iterator().next();
			String id = SeedWorld.id(set.value().structures().getFirst().structure());
			SeedWorld.Found[] f = new SeedWorld.Found[positions.size()];
			for (int i = 0; i < f.length; i++) {
				ChunkPos c = positions.get(i);
				f[i] = new SeedWorld.Found(icon, id, c.getMiddleBlockX(), c.getMiddleBlockZ(), c.x(), c.z());
			}
			out.found = f;
		} catch (RuntimeException e) {
			ChunkMap.LOG.warn("ChunkMap: não deu para calcular as fortalezas", e);
			out.found = NONE;
		}
	}

	public void close() {
		closed = true;
	}

	/**
	 * Junta em {@code out} as estruturas já calculadas na área (em blocos) com os filtros ligados e pede o cálculo do
	 * que falta. Devolve true se algum filtro ligado ficou de fora por a área ser grande demais.
	 */
	public boolean collect(double minX, double minZ, double maxX, double maxZ, Set<StructureIcon> filters, List<SeedWorld.Found> out) {
		boolean limited = false;
		frame++;
		for (Rings r : rings) {
			if (!intersects(r.icons, filters)) continue;
			SeedWorld.Found[] f = r.found;
			if (f == null) continue;
			for (SeedWorld.Found s : f) {
				if (s.x() >= minX && s.x() <= maxX && s.z() >= minZ && s.z() <= maxZ) out.add(s);
			}
		}
		for (Spread s : spreads) {
			if (!intersects(s.icons, filters)) continue;
			int regionBlocks = s.placement.spacing() * 16;
			int rx0 = Math.floorDiv((int) Math.floor(minX), regionBlocks), rx1 = Math.floorDiv((int) Math.ceil(maxX), regionBlocks);
			int rz0 = Math.floorDiv((int) Math.floor(minZ), regionBlocks), rz1 = Math.floorDiv((int) Math.ceil(maxZ), regionBlocks);
			if ((long) (rx1 - rx0 + 1) * (rz1 - rz0 + 1) > MAX_REGIONS) {
				limited = true;
				continue;
			}
			int b = s.block;
			for (int bx = Math.floorDiv(rx0, b); bx <= Math.floorDiv(rx1, b); bx++) {
				for (int bz = Math.floorDiv(rz0, b); bz <= Math.floorDiv(rz1, b); bz++) {
					long key = ChunkPos.pack(bx, bz);
					SeedWorld.Found[] found = s.done.get(key);
					if (found == null) {
						if (s.pending.containsKey(key)) s.pending.put(key, frame);
						else request(s, bx, bz, key);
						continue;
					}
					for (SeedWorld.Found f : found) {
						if (filters.contains(f.icon()) && f.x() >= minX - 8 && f.x() <= maxX + 8 && f.z() >= minZ - 8 && f.z() <= maxZ + 8) out.add(f);
					}
				}
			}
		}
		return limited;
	}

	/** Ainda há cálculo em andamento (para a tela mostrar "calculando..."). */
	public boolean busy() {
		for (Spread s : spreads) if (!s.pending.isEmpty()) return true;
		for (Rings r : rings) if (r.found == null) return true;
		return false;
	}

	private void request(Spread s, int bx, int bz, long key) {
		if (s.pending.put(key, frame) != null) return;
		Workers.map(Workers.STRUCTURES, () -> {
			try {
				Long asked = s.pending.get(key);
				// Saiu da tela faz tempo (arrastou o mapa para longe): deixa para quando voltar a aparecer.
				if (closed || asked == null || frame - asked > 240) return;
				List<SeedWorld.Found> list = new ArrayList<>(2);
				int b = s.block;
				for (int rx = bx * b; rx < bx * b + b; rx++) {
					for (int rz = bz * b; rz < bz * b + b; rz++) {
						ChunkPos c = s.placement.getPotentialStructureChunk(world.state.getLevelSeed(), rx, rz);
						SeedWorld.Found f = world.check(s.set, c.x(), c.z());
						if (f != null && f.icon() != null) list.add(f);
					}
				}
				s.done.put(key, list.isEmpty() ? NONE : list.toArray(NONE));
			} catch (RuntimeException e) {
				ChunkMap.LOG.warn("ChunkMap: erro calculando estruturas", e);
				s.done.put(key, NONE);
			} finally {
				s.pending.remove(key);
			}
		});
	}

	private static boolean intersects(EnumSet<StructureIcon> a, Set<StructureIcon> b) {
		for (StructureIcon i : a) if (b.contains(i)) return true;
		return false;
	}

	/** Para o teste: todas as estruturas de um filtro numa área, calculadas agora (sem fila). */
	public List<SeedWorld.Found> computeNow(StructureIcon icon, int minX, int minZ, int maxX, int maxZ) {
		List<SeedWorld.Found> out = new ArrayList<>();
		for (Spread s : spreads) {
			if (!s.icons.contains(icon)) continue;
			int rb = s.placement.spacing() * 16;
			for (int rx = Math.floorDiv(minX, rb); rx <= Math.floorDiv(maxX, rb); rx++) {
				for (int rz = Math.floorDiv(minZ, rb); rz <= Math.floorDiv(maxZ, rb); rz++) {
					ChunkPos c = s.placement.getPotentialStructureChunk(world.state.getLevelSeed(), rx, rz);
					SeedWorld.Found f = world.check(s.set, c.x(), c.z());
					if (f != null && f.icon() == icon) out.add(f);
				}
			}
		}
		return out;
	}
}
