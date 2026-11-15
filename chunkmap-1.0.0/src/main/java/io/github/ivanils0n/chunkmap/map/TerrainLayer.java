package io.github.ivanils0n.chunkmap.map;

import io.github.ivanils0n.chunkmap.ChunkMap;
import io.github.ivanils0n.chunkmap.Workers;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * O terreno de verdade por onde o jogador já passou (como no Xaero): a cor do bloco de cima de cada coluna dos chunks
 * carregados, com sombra pelo relevo. Guardado em regiões de 256x256 blocos em chunkmap/&lt;mundo&gt;/terreno/&lt;dimensão&gt;.
 */
public final class TerrainLayer {
	public static final int MAX_Z = 3;
	private static final int REGION = 256;
	private static final int MAX_REGIONS = 160;
	private static final int MAX_TILES = 200;
	private static final int SIZE = TileTexture.SIZE;
	private static final int FILE_MAGIC = 0x43_4D_54_31; // "CMT1"

	private final Path dir;
	/** Só a thread do jogo mexe nos mapas (as regiões em si são sincronizadas). */
	private final LinkedHashMap<Long, Region> regions = new LinkedHashMap<>(64, 0.75f, true);
	private final LinkedHashMap<Long, Tile> tiles = new LinkedHashMap<>(64, 0.75f, true);
	private long frame;

	private static final class Region {
		final int rx, rz;
		final int[] argb = new int[REGION * REGION];
		volatile boolean loaded;
		volatile int version;
		boolean dirty;

		Region(int rx, int rz) {
			this.rx = rx;
			this.rz = rz;
		}
	}

	private static final class Tile {
		final TileTexture texture = new TileTexture();
		long built = Long.MIN_VALUE;
		long checked;
		long wanted;
	}

	/** Regiões que têm arquivo no disco (lista feita uma vez ao abrir). */
	private final java.util.Set<Long> onDisk = java.util.concurrent.ConcurrentHashMap.newKeySet();

	public TerrainLayer(Path dir) {
		this.dir = dir;
		Workers.IO.execute(() -> {
			if (!Files.isDirectory(dir)) return;
			try (var files = Files.list(dir)) {
				files.forEach(f -> {
					String[] p = f.getFileName().toString().split("\\.");
					if (p.length == 4 && p[0].equals("r") && p[3].equals("gz")) {
						try {
							onDisk.add(rkey(Integer.parseInt(p[1]), Integer.parseInt(p[2])));
						} catch (NumberFormatException ignored) {
						}
					}
				});
			} catch (IOException e) {
				ChunkMap.LOG.warn("ChunkMap: não deu para listar {}: {}", dir, e.toString());
			}
		});
	}

	private static long rkey(int rx, int rz) {
		return ChunkPos.pack(rx, rz);
	}

	/** A região (criada vazia e lida do disco em segundo plano, se ainda não estava na memória). */
	private Region region(int rx, int rz) {
		long k = rkey(rx, rz);
		Region r = regions.get(k);
		if (r != null) return r;
		r = new Region(rx, rz);
		regions.put(k, r);
		Region region = r;
		Workers.IO.execute(() -> load(region));
		trimRegions();
		return r;
	}

	private Path file(int rx, int rz) {
		return dir.resolve("r." + rx + "." + rz + ".gz");
	}

	private void load(Region r) {
		Path f = file(r.rx, r.rz);
		try {
			if (Files.isRegularFile(f)) {
				int[] data = new int[REGION * REGION];
				try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(f))))) {
					if (in.readInt() != FILE_MAGIC) throw new IOException("arquivo de outra versão");
					for (int i = 0; i < data.length; i++) data[i] = in.readInt();
				}
				// O que já chegou do jogo enquanto o arquivo era lido é mais novo: só completa os buracos.
				synchronized (r) {
					for (int i = 0; i < data.length; i++) if (r.argb[i] == 0) r.argb[i] = data[i];
				}
			}
		} catch (IOException | RuntimeException e) {
			ChunkMap.LOG.warn("ChunkMap: terreno {} ilegível, começando de novo ({})", f.getFileName(), e.toString());
		}
		r.loaded = true;
		r.version++;
	}

	/** Grava no disco as regiões alteradas (em segundo plano). */
	public void save() {
		for (Region r : regions.values()) saveRegion(r);
	}

	private void saveRegion(Region r) {
		if (!r.dirty || !r.loaded) return;
		r.dirty = false;
		int[] copy;
		synchronized (r) {
			copy = r.argb.clone();
		}
		Path f = file(r.rx, r.rz);
		Workers.IO.execute(() -> {
			try {
				Files.createDirectories(dir);
				Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
				try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(tmp))))) {
					out.writeInt(FILE_MAGIC);
					for (int c : copy) out.writeInt(c);
				}
				Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
				onDisk.add(rkey(r.rx, r.rz));
			} catch (IOException e) {
				ChunkMap.LOG.warn("ChunkMap: não deu para salvar o terreno {}: {}", f.getFileName(), e.toString());
			}
		});
	}

	private void trimRegions() {
		if (regions.size() <= MAX_REGIONS) return;
		Iterator<Map.Entry<Long, Region>> it = regions.entrySet().iterator();
		while (regions.size() > MAX_REGIONS * 3 / 4 && it.hasNext()) {
			Region r = it.next().getValue();
			if (!r.loaded) continue; // ainda lendo: tirar agora perderia a leitura
			saveRegion(r);
			it.remove();
		}
	}

	/** Lê as cores do chunk (chamado quando ele carrega e de tempos em tempos perto do jogador). */
	public void update(ClientLevel level, LevelChunk chunk) {
		ChunkPos cp = chunk.getPos();
		int bx = cp.getMinBlockX(), bz = cp.getMinBlockZ();
		int minY = level.getMinY();
		boolean ceiling = level.dimensionType().hasCeiling();
		int ceilingStart = Math.min(minY + level.dimensionType().logicalHeight(), minY + level.getHeight()) - 1;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int[] colors = new int[256];
		int[] heights = new int[256];
		boolean any = false;
		for (int z = 0; z < 16; z++) {
			for (int x = 0; x < 16; x++) {
				int y;
				if (ceiling) {
					// Nether: desce do teto até achar ar e depois o chão desse primeiro vão.
					y = ceilingStart;
					while (y > minY && !chunk.getBlockState(pos.set(bx + x, y, bz + z)).isAir()) y--;
					while (y > minY && chunk.getBlockState(pos.set(bx + x, y, bz + z)).isAir()) y--;
				} else {
					y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
				}
				BlockState state = chunk.getBlockState(pos.set(bx + x, y, bz + z));
				MapColor color = state.getMapColor(level, pos);
				int steps = 0;
				while (color == MapColor.NONE && y > minY && steps++ < 32) {
					y--;
					state = chunk.getBlockState(pos.set(bx + x, y, bz + z));
					color = state.getMapColor(level, pos);
				}
				if (color == MapColor.NONE || y <= minY) continue;
				int i = z * 16 + x;
				heights[i] = y;
				if (!state.getFluidState().isEmpty()) {
					// Água: mais funda, mais escura (como nos mapas do jogo).
					int depth = 0;
					while (depth < 16 && y - depth > minY && !chunk.getBlockState(pos.set(bx + x, y - depth - 1, bz + z)).getFluidState().isEmpty()) depth++;
					double d = depth * 0.1 + ((x + z) & 1) * 0.2;
					MapColor.Brightness b = d < 0.5 ? MapColor.Brightness.HIGH : d > 0.9 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
					colors[i] = color.calculateARGBColor(b) | 0xFF000000;
					heights[i] = Integer.MIN_VALUE; // a água não faz sombra
				} else {
					colors[i] = color.calculateARGBColor(MapColor.Brightness.NORMAL) | 0xFF000000;
				}
				any = true;
			}
		}
		if (!any) return;
		// Relevo: mais alto que o vizinho de cima (norte) fica claro, mais baixo fica escuro.
		for (int z = 1; z < 16; z++) {
			for (int x = 0; x < 16; x++) {
				int i = z * 16 + x, n = i - 16;
				if (colors[i] == 0 || heights[i] == Integer.MIN_VALUE || heights[n] == Integer.MIN_VALUE || colors[n] == 0) continue;
				if (heights[i] > heights[n]) colors[i] = shade(colors[i], 1.12f);
				else if (heights[i] < heights[n]) colors[i] = shade(colors[i], 0.82f);
			}
		}
		Region r = region(Math.floorDiv(bx, REGION), Math.floorDiv(bz, REGION));
		int ox = Math.floorMod(bx, REGION), oz = Math.floorMod(bz, REGION);
		boolean changed = false;
		synchronized (r) {
			for (int z = 0; z < 16; z++) {
				for (int x = 0; x < 16; x++) {
					int c = colors[z * 16 + x];
					if (c == 0) continue;
					int j = (oz + z) * REGION + ox + x;
					if (r.argb[j] != c) {
						r.argb[j] = c;
						changed = true;
					}
				}
			}
		}
		if (changed) {
			r.dirty = true;
			r.version++;
		}
	}

	private static int shade(int argb, float f) {
		int rr = Math.min(255, (int) ((argb >> 16 & 0xFF) * f));
		int gg = Math.min(255, (int) ((argb >> 8 & 0xFF) * f));
		int bb = Math.min(255, (int) ((argb & 0xFF) * f));
		return 0xFF000000 | rr << 16 | gg << 8 | bb;
	}

	public void beginFrame(long frame) {
		this.frame = frame;
	}

	/** Textura do pedaço (z, tx, tz) do terreno explorado, ou null se ainda não há nada. */
	public Identifier texture(int z, int tx, int tz) {
		if (z > MAX_Z) return null;
		long k = BiomeLayer.key(z, tx, tz);
		Tile t = tiles.get(k);
		if (t == null) {
			t = new Tile();
			tiles.put(k, t);
			trimTiles();
		}
		t.wanted = frame;
		// Confere se algo mudou no máximo a cada 10 quadros (a soma das versões das regiões cobertas).
		if (frame - t.checked >= 10 || !t.texture.ready()) {
			t.checked = frame;
			int tileBlocks = SIZE << z;
			int x0 = tx * tileBlocks, z0 = tz * tileBlocks;
			int rx0 = Math.floorDiv(x0, REGION), rx1 = Math.floorDiv(x0 + tileBlocks - 1, REGION);
			int rz0 = Math.floorDiv(z0, REGION), rz1 = Math.floorDiv(z0 + tileBlocks - 1, REGION);
			List<Region> rs = new ArrayList<>();
			long version = 0;
			boolean anyLoaded = false;
			for (int rx = rx0; rx <= rx1; rx++) {
				for (int rz = rz0; rz <= rz1; rz++) {
					// Só regiões que existem (na memória ou no disco): o resto é área nunca visitada.
					Region r = regions.get(rkey(rx, rz));
					if (r == null) {
						if (!onDisk.contains(rkey(rx, rz))) continue;
						r = region(rx, rz);
					}
					rs.add(r);
					version = version * 31 + r.version;
					anyLoaded |= r.loaded;
				}
			}
			if (rs.isEmpty()) return t.texture.ready() ? t.texture.id : null;
			if (version != t.built && anyLoaded && TileTexture.canUpload(frame)) {
				int[] argb = new int[SIZE * SIZE];
				int step = 1 << z;
				for (Region r : rs) {
					synchronized (r) {
						int rbx = r.rx * REGION, rbz = r.rz * REGION;
						for (int pz = 0; pz < SIZE; pz++) {
							int wz = z0 + pz * step - rbz;
							if (wz < 0 || wz >= REGION) continue;
							for (int px = 0; px < SIZE; px++) {
								int wx = x0 + px * step - rbx;
								if (wx < 0 || wx >= REGION) continue;
								argb[pz * SIZE + px] = r.argb[wz * REGION + wx];
							}
						}
					}
				}
				t.texture.paint(argb);
				t.built = version;
			}
		}
		return t.texture.ready() ? t.texture.id : null;
	}

	private void trimTiles() {
		if (tiles.size() <= MAX_TILES) return;
		Iterator<Map.Entry<Long, Tile>> it = tiles.entrySet().iterator();
		while (tiles.size() > MAX_TILES * 3 / 4 && it.hasNext()) {
			Tile t = it.next().getValue();
			if (t.wanted >= frame - 2) break;
			t.texture.release();
			it.remove();
		}
	}

	/** Salva e libera tudo (ao sair do mundo). */
	public void close() {
		save();
		for (Tile t : tiles.values()) t.texture.release();
		tiles.clear();
		regions.clear();
	}
}
