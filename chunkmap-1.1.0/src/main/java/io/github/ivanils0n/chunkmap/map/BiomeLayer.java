package io.github.ivanils0n.chunkmap.map;

import io.github.ivanils0n.chunkmap.ChunkMap;
import io.github.ivanils0n.chunkmap.Workers;
import io.github.ivanils0n.chunkmap.seed.BiomeColors;
import io.github.ivanils0n.chunkmap.seed.SeedWorld;
import net.minecraft.resources.Identifier;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Os biomas da seed em pedaços de 128x128 pixels. No nível de zoom z cada pixel vale 2^z blocos (z = 2: um pixel por
 * "quart", a menor unidade de bioma do jogo). O cálculo é feito em segundo plano; a tela só desenha o que já está pronto.
 */
public final class BiomeLayer {
	public static final int MIN_Z = 2, MAX_Z = 7;
	private static final int MAX_TILES = 320;
	private static final int SIZE = TileTexture.SIZE;

	private final SeedWorld world;
	private final boolean caves;
	/** Só a thread do jogo mexe aqui. */
	private final LinkedHashMap<Long, Tile> tiles = new LinkedHashMap<>(256, 0.75f, true);
	private volatile boolean closed;
	private volatile long frame;

	private int[] palette = new int[0];
	private int paletteVersion;
	private Set<String> highlight = Set.of();

	private static final class Tile {
		final int z, tx, tz;
		volatile short[] ids;
		volatile boolean pending;
		volatile long wanted;
		final TileTexture texture = new TileTexture();
		int painted = -1;

		Tile(int z, int tx, int tz) {
			this.z = z;
			this.tx = tx;
			this.tz = tz;
		}
	}

	public BiomeLayer(SeedWorld world, boolean caves) {
		this.world = world;
		this.caves = caves;
	}

	public SeedWorld world() {
		return world;
	}

	public boolean caves() {
		return caves;
	}

	public static long key(int z, int tx, int tz) {
		return (long) z << 58 | (tx & 0x1FFFFFFFL) << 29 | tz & 0x1FFFFFFFL;
	}

	/** Chamado uma vez por quadro de desenho, antes dos pedidos de textura. */
	public void beginFrame(long frame, Set<String> highlight) {
		this.frame = frame;
		if (!highlight.equals(this.highlight)) {
			this.highlight = Set.copyOf(highlight);
			paletteVersion++;
			palette = new int[0];
		}
		int n = world.biomeCount();
		if (palette.length < n) {
			int[] p = java.util.Arrays.copyOf(palette, n);
			for (int i = palette.length; i < n; i++) {
				String id = SeedWorld.id(world.biome(i));
				int c = BiomeColors.of(id);
				p[i] = this.highlight.isEmpty() || this.highlight.contains(id) ? c : BiomeColors.dim(c);
			}
			palette = p;
		}
	}

	/** Textura pronta do pedaço (z, tx, tz), ou null (e o cálculo é pedido). */
	public Identifier texture(int z, int tx, int tz, boolean request) {
		long k = key(z, tx, tz);
		Tile t = tiles.get(k);
		if (t == null) {
			if (!request) return null;
			t = new Tile(z, tx, tz);
			tiles.put(k, t);
			trim();
		}
		t.wanted = frame;
		short[] ids = t.ids;
		if (ids == null) {
			if (request && !t.pending) compute(t);
			return null;
		}
		if (t.painted != paletteVersion || !t.texture.ready()) {
			if (!TileTexture.canUpload(frame)) return t.texture.ready() ? t.texture.id : null;
			int[] argb = new int[SIZE * SIZE];
			int[] p = palette;
			for (int i = 0; i < argb.length; i++) {
				int id = ids[i];
				if (id >= p.length) {
					// Bioma novo achado por outra thread depois do começo do quadro: aumenta a paleta agora.
					beginFrame(frame, highlight);
					p = palette;
				}
				argb[i] = id < p.length ? p[id] : 0xFF000000;
			}
			t.texture.paint(argb);
			t.painted = paletteVersion;
		}
		return t.texture.id;
	}

	/** Índice do bioma num pixel já calculado (para mostrar o nome sob o mouse), ou -1. */
	public int biomeIndexAt(int blockX, int blockZ) {
		for (int z = MIN_Z; z <= MAX_Z; z++) {
			int tileBlocks = SIZE << z;
			int tx = Math.floorDiv(blockX, tileBlocks), tz = Math.floorDiv(blockZ, tileBlocks);
			Tile t = tiles.get(key(z, tx, tz));
			if (t == null || t.ids == null) continue;
			int px = (blockX - tx * tileBlocks) >> z, pz = (blockZ - tz * tileBlocks) >> z;
			return t.ids[pz * SIZE + px];
		}
		return -1;
	}

	private void compute(Tile t) {
		t.pending = true;
		Workers.map(Workers.BIOMES, () -> {
			try {
				// Saiu da tela faz tempo: deixa para quando voltar a aparecer.
				if (closed || frame - t.wanted > 240) return;
				short[] ids = new short[SIZE * SIZE];
				int step = 1 << t.z, half = step >> 1;
				int x0 = t.tx * SIZE * step, z0 = t.tz * SIZE * step;
				for (int pz = 0, i = 0; pz < SIZE; pz++) {
					int bz = z0 + pz * step + half;
					for (int px = 0; px < SIZE; px++, i++) {
						ids[i] = (short) world.indexOf(world.biomeAt(x0 + px * step + half, bz, caves));
					}
					if (closed) return;
				}
				t.ids = ids;
			} catch (RuntimeException e) {
				ChunkMap.LOG.warn("ChunkMap: erro calculando biomas", e);
			} finally {
				t.pending = false;
			}
		});
	}

	private void trim() {
		if (tiles.size() <= MAX_TILES) return;
		Iterator<Map.Entry<Long, Tile>> it = tiles.entrySet().iterator();
		while (tiles.size() > MAX_TILES * 3 / 4 && it.hasNext()) {
			Tile t = it.next().getValue();
			if (t.wanted >= frame - 2) break; // o resto está na tela
			t.texture.release();
			it.remove();
		}
	}

	public void close() {
		closed = true;
		for (Tile t : tiles.values()) t.texture.release();
		tiles.clear();
	}
}
