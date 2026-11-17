package io.github.ivanils0n.chunkmap.map;

import io.github.ivanils0n.chunkmap.ChunkMap;
import io.github.ivanils0n.chunkmap.Config;
import io.github.ivanils0n.chunkmap.Waypoint;
import io.github.ivanils0n.chunkmap.WorldSession;
import io.github.ivanils0n.chunkmap.seed.SeedWorld;
import io.github.ivanils0n.chunkmap.seed.StructureFinder;
import io.github.ivanils0n.chunkmap.seed.StructureIcon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.storage.LevelData;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Desenha o mapa (minimapa e tela cheia usam o mesmo código). */
public final class MapRenderer {
	public static final Identifier ARROW = tex("player_arrow");
	public static final Identifier PIN = tex("waypoint");
	/** Contorno, miolo e brilho do alfinete (desenhados por cima, sem a cor do waypoint). */
	public static final Identifier PIN_TOP = tex("waypoint_top");
	/** Ponto de morte: caveira (sem tingir). */
	public static final Identifier SKULL = tex("skull");
	public static final Identifier COMPASS = tex("compass_tab");

	private static long frame;

	private MapRenderer() {}

	private static Identifier tex(String name) {
		return Identifier.fromNamespaceAndPath("chunkmap", "textures/gui/" + name + ".png");
	}

	/** O que desenhar e onde. */
	public static final class View {
		public int x, y, w, h;
		/** Centro do mapa (blocos). */
		public double cx, cz;
		/** Pixels da tela (GUI) por bloco. */
		public float scale = 1f;
		public boolean minimap;
		public ResourceKey<Level> dim;
		public boolean biomes = true, terrain, grid, caves;
		/** Mostrar o mapa das cavernas (no lugar dos biomas e do terreno). */
		public boolean caveMap;
		/** Modo detalhado (TAB apertado): cabeça de cada mob e do jogador no lugar dos pontos e da seta. */
		public boolean detailed;
		public Set<StructureIcon> filters = Set.of();
		public float partialTick;
		/** Saídas: a área ficou grande demais para alguns filtros / ainda calculando. */
		public boolean limited, busy;

		public double worldX(double sx) {
			return cx + (sx - x - w / 2.0) / scale;
		}

		public double worldZ(double sy) {
			return cz + (sy - y - h / 2.0) / scale;
		}

		public float screenX(double wx) {
			return (float) (x + w / 2.0 + (wx - cx) * scale);
		}

		public float screenY(double wz) {
			return (float) (y + h / 2.0 + (wz - cz) * scale);
		}

		boolean inside(float sx, float sy, float margin) {
			return sx >= x - margin && sx <= x + w + margin && sy >= y - margin && sy <= y + h + margin;
		}
	}

	/** Algo clicável/com dica no mapa. */
	public record Hit(int x0, int y0, int x1, int y1, List<Component> lines, Waypoint waypoint) {
		public boolean contains(double mx, double my) {
			return mx >= x0 && mx < x1 && my >= y0 && my < y1;
		}
	}

	public static void draw(GuiGraphicsExtractor g, View v, List<Hit> hits) {
		Minecraft mc = Minecraft.getInstance();
		WorldSession session = ChunkMap.session();
		ClientLevel level = mc.level;
		if (session == null || level == null || v.dim == null) return;
		frame++;
		g.fill(v.x, v.y, v.x + v.w, v.y + v.h, v.caveMap ? 0xFF0B0C0F : 0xFF1B1F27);
		g.enableScissor(v.x, v.y, v.x + v.w, v.y + v.h);
		try {
			double bpp = 1.0 / v.scale; // blocos por pixel
			int z = bpp <= 1 ? 0 : Mth.floor(Math.log(bpp) / Math.log(2) + 1e-6);

			WorldSession.SeedLayers seed = session.seedLayers(v.dim);
			SeedWorld world = seed == null ? null : seed.world();
			if (v.caveMap) {
				if (z <= TerrainLayer.MAX_Z) {
					TerrainLayer t = session.caves(v.dim);
					t.beginFrame(frame);
					drawTiles(g, v, z, z, (zz, tx, tz, req) -> req ? t.texture(zz, tx, tz) : null);
				}
			} else if (v.biomes && world != null) {
				BiomeLayer layer = seed.biomes(v.caves);
				layer.beginFrame(frame, session.highlight);
				int bz = Math.clamp(z, BiomeLayer.MIN_Z, BiomeLayer.MAX_Z);
				drawTiles(g, v, bz, BiomeLayer.MAX_Z, (zz, tx, tz, req) -> layer.texture(zz, tx, tz, req));
			}
			boolean terrain = !v.caveMap && (v.terrain || world == null || !v.biomes);
			if (terrain && z <= TerrainLayer.MAX_Z) {
				TerrainLayer t = session.terrain(v.dim);
				t.beginFrame(frame);
				drawTiles(g, v, z, z, (zz, tx, tz, req) -> req ? t.texture(zz, tx, tz) : null);
			}
			if (v.grid) drawGrid(g, v);
			if (world != null && v.filters.contains(StructureIcon.SLIME_CHUNK) && v.dim.equals(Level.OVERWORLD)) drawSlime(g, v, world.seed);
			if (world != null) {
				StructureFinder finder = seed.structures();
				List<SeedWorld.Found> found = new ArrayList<>();
				v.limited = finder.collect(v.worldX(v.x - 8), v.worldZ(v.y - 8), v.worldX(v.x + v.w + 8), v.worldZ(v.y + v.h + 8), v.filters, found);
				if (v.minimap) v.limited = false;
				v.busy = finder.busy();
				drawStructures(g, v, found, hits);
			} else {
				v.limited = false;
				v.busy = seed != null && session.status(v.dim) == WorldSession.SeedStatus.LOADING;
			}
			if (v.filters.contains(StructureIcon.SPAWN)) drawSpawn(g, v, level, hits);
		} finally {
			g.disableScissor();
		}
		drawWaypoints(g, v, session, hits);
		if (level.dimension().equals(v.dim)) drawEntities(g, v, mc, level, hits);
	}

	@FunctionalInterface
	private interface TileSource {
		Identifier get(int z, int tx, int tz, boolean request);
	}

	/** Desenha os pedaços visíveis do nível z; os que faltam usam um nível mais grosso já pronto (até maxZ). */
	private static void drawTiles(GuiGraphicsExtractor g, View v, int z, int maxZ, TileSource src) {
		int tileBlocks = TileTexture.SIZE << z;
		int tx0 = Mth.floor(v.worldX(v.x) / tileBlocks), tx1 = Mth.floor(v.worldX(v.x + v.w) / tileBlocks);
		int tz0 = Mth.floor(v.worldZ(v.y) / tileBlocks), tz1 = Mth.floor(v.worldZ(v.y + v.h) / tileBlocks);
		// Primeiro os do meio da tela (a pilha de cálculo atende o último pedido primeiro).
		int cx = (tx0 + tx1) / 2, cz = (tz0 + tz1) / 2;
		for (int ring = Math.max(Math.max(cx - tx0, tx1 - cx), Math.max(cz - tz0, tz1 - cz)); ring >= 0; ring--) {
			for (int tx = cx - ring; tx <= cx + ring; tx++) {
				for (int tz = cz - ring; tz <= cz + ring; tz++) {
					if (Math.max(Math.abs(tx - cx), Math.abs(tz - cz)) != ring) continue;
					if (tx < tx0 || tx > tx1 || tz < tz0 || tz > tz1) continue;
					drawTile(g, v, z, maxZ, tx, tz, src);
				}
			}
		}
	}

	private static void drawTile(GuiGraphicsExtractor g, View v, int z, int maxZ, int tx, int tz, TileSource src) {
		int size = TileTexture.SIZE;
		int tileBlocks = size << z;
		float sx = v.screenX((double) tx * tileBlocks), sy = v.screenY((double) tz * tileBlocks);
		float px = tileBlocks * v.scale;
		Identifier id = src.get(z, tx, tz, true);
		int u = 0, vv = 0, part = size;
		// Enquanto calcula: um pedaço do nível mais grosso (mesma área, menos detalhe).
		for (int up = 1; id == null && z + up <= maxZ && up <= 3; up++) {
			int f = 1 << up;
			int ptx = Math.floorDiv(tx, f), ptz = Math.floorDiv(tz, f);
			id = src.get(z + up, ptx, ptz, false);
			if (id != null) {
				part = size / f;
				u = (tx - ptx * f) * part;
				vv = (tz - ptz * f) * part;
			}
		}
		if (id == null) return;
		// Bordas arredondadas para pixels de verdade da tela: pedaços vizinhos se encostam sem fresta.
		double gs = Minecraft.getInstance().getWindow().getGuiScale();
		float x0 = (float) (Math.round(sx * gs) / gs), x1 = (float) (Math.round((sx + px) * gs) / gs);
		float y0 = (float) (Math.round(sy * gs) / gs), y1 = (float) (Math.round((sy + px) * gs) / gs);
		g.pose().pushMatrix();
		g.pose().translate(x0, y0);
		g.pose().scale((x1 - x0) / size, (y1 - y0) / size);
		g.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, u, vv, size, size, part, part, size, size);
		g.pose().popMatrix();
	}

	private static void drawGrid(GuiGraphicsExtractor g, View v) {
		int step = v.scale >= 0.5f ? 16 : v.scale * 512 >= 16 ? 512 : 0;
		if (step == 0) return;
		int color = step == 16 ? 0x28FFFFFF : 0x40FFFFFF;
		double x0 = Math.floor(v.worldX(v.x) / step) * step, x1 = v.worldX(v.x + v.w);
		for (double wx = x0; wx <= x1; wx += step) {
			int sx = Mth.floor(v.screenX(wx));
			g.fill(sx, v.y, sx + 1, v.y + v.h, color);
		}
		double z0 = Math.floor(v.worldZ(v.y) / step) * step, z1 = v.worldZ(v.y + v.h);
		for (double wz = z0; wz <= z1; wz += step) {
			int sy = Mth.floor(v.screenY(wz));
			g.fill(v.x, sy, v.x + v.w, sy + 1, color);
		}
	}

	private static void drawSlime(GuiGraphicsExtractor g, View v, long seed) {
		if (16 * v.scale < 4) return; // chunk menor que 4 pixels: vira ruído
		int cx0 = Mth.floor(v.worldX(v.x) / 16), cx1 = Mth.floor(v.worldX(v.x + v.w) / 16);
		int cz0 = Mth.floor(v.worldZ(v.y) / 16), cz1 = Mth.floor(v.worldZ(v.y + v.h) / 16);
		for (int cx = cx0; cx <= cx1; cx++) {
			for (int cz = cz0; cz <= cz1; cz++) {
				if (WorldgenRandom.seedSlimeChunk(cx, cz, seed, 987234911L).nextInt(10) != 0) continue;
				int sx0 = Mth.floor(v.screenX(cx * 16.0)), sy0 = Mth.floor(v.screenY(cz * 16.0));
				int sx1 = Mth.floor(v.screenX(cx * 16.0 + 16)), sy1 = Mth.floor(v.screenY(cz * 16.0 + 16));
				g.fill(sx0, sy0, sx1, sy1, 0x7044E04A);
			}
		}
	}

	private static int iconSize(View v) {
		return v.minimap ? 12 : 16;
	}

	private static void drawIcon(GuiGraphicsExtractor g, Identifier texture, float cx, float cy, int size) {
		g.pose().pushMatrix();
		g.pose().translate(cx - size / 2f, cy - size / 2f);
		g.pose().scale(size / 16f, size / 16f);
		g.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0f, 0f, 16, 16, 16, 16);
		g.pose().popMatrix();
	}

	/** Ordem de desenho quando falta espaço: as mais raras primeiro. */
	private static final List<StructureIcon> RARITY = List.of(StructureIcon.STRONGHOLD, StructureIcon.MANSION, StructureIcon.ANCIENT_CITY,
			StructureIcon.END_CITY, StructureIcon.BASTION, StructureIcon.NETHER_FORTRESS, StructureIcon.OCEAN_MONUMENT,
			StructureIcon.DESERT_PYRAMID, StructureIcon.JUNGLE_TEMPLE, StructureIcon.SWAMP_HUT, StructureIcon.IGLOO,
			StructureIcon.PILLAGER_OUTPOST, StructureIcon.VILLAGE, StructureIcon.TRIAL_CHAMBERS, StructureIcon.SHIPWRECK,
			StructureIcon.RUINED_PORTAL, StructureIcon.BURIED_TREASURE, StructureIcon.MINESHAFT);

	private static int rarity(StructureIcon i) {
		int r = RARITY.indexOf(i);
		return r < 0 ? RARITY.size() : r;
	}

	/**
	 * Desenha os ícones sem amontoar: cada ícone ocupa uma casa da tela do tamanho dele, e um ícone que cairia numa casa
	 * já ocupada fica de fora (as estruturas mais raras ganham a vez). Se algum ficou de fora, o mapa pede para aproximar.
	 */
	private static void drawStructures(GuiGraphicsExtractor g, View v, List<SeedWorld.Found> found, List<Hit> hits) {
		int size = iconSize(v);
		found.sort(java.util.Comparator.comparingInt(f -> rarity(f.icon())));
		int n = 0;
		for (SeedWorld.Found f : found) {
			if (f.icon() == null) continue;
			float sx = v.screenX(f.x() + 0.5), sy = v.screenY(f.z() + 0.5);
			if (!v.inside(sx, sy, size)) continue;
			// Casas do tamanho do ícone: basta olhar a própria e as 8 vizinhas para saber se encostaria em outro.
			int cx = Mth.floor(sx / size), cy = Mth.floor(sy / size);
			boolean free = true;
			for (int dx = -1; dx <= 1 && free; dx++) {
				for (int dy = -1; dy <= 1 && free; dy++) free = !near(sx, sy, cx + dx, cy + dy, size);
			}
			if (!free || ++n > 1200) {
				v.limited = true;
				continue;
			}
			centers.put(cell(cx, cy), new float[]{sx, sy});
			drawIcon(g, f.icon().texture, sx, sy, size);
			if (hits != null) {
				int h = size / 2;
				hits.add(new Hit(Mth.floor(sx) - h, Mth.floor(sy) - h, Mth.floor(sx) + h, Mth.floor(sy) + h,
						List.of(f.icon().label(), structureName(f.structure()), coords(f.x(), f.z())), null));
			}
		}
		centers.clear();
	}

	/** Centro do ícone já desenhado em cada casa (só durante drawStructures, na thread do jogo). */
	private static final java.util.Map<Long, float[]> centers = new java.util.HashMap<>();

	private static long cell(int x, int y) {
		return (long) x << 32 | y & 0xFFFFFFFFL;
	}

	private static boolean near(float sx, float sy, int cx, int cy, int size) {
		float[] c = centers.get(cell(cx, cy));
		return c != null && Math.abs(c[0] - sx) < size && Math.abs(c[1] - sy) < size;
	}

	private static Component structureName(String id) {
		String path = id.substring(id.indexOf(':') + 1);
		return Component.literal(path.replace('_', ' ')).withStyle(net.minecraft.ChatFormatting.GRAY);
	}

	public static Component coords(int x, int z) {
		return Component.literal(String.format(Locale.ROOT, "X: %d  Z: %d", x, z)).withStyle(net.minecraft.ChatFormatting.GRAY);
	}

	private static void drawSpawn(GuiGraphicsExtractor g, View v, ClientLevel level, List<Hit> hits) {
		LevelData.RespawnData spawn = level.getRespawnData();
		if (spawn == null || !spawn.dimension().equals(v.dim)) return;
		int x = spawn.pos().getX(), z = spawn.pos().getZ();
		float sx = v.screenX(x + 0.5), sy = v.screenY(z + 0.5);
		int size = iconSize(v);
		if (!v.inside(sx, sy, size)) return;
		drawIcon(g, StructureIcon.SPAWN.texture, sx, sy, size);
		if (hits != null) {
			int h = size / 2;
			hits.add(new Hit(Mth.floor(sx) - h, Mth.floor(sy) - h, Mth.floor(sx) + h, Mth.floor(sy) + h,
					List.of(StructureIcon.SPAWN.label(), coords(x, z)), null));
		}
	}

	private static void drawWaypoints(GuiGraphicsExtractor g, View v, WorldSession session, List<Hit> hits) {
		String dim = WorldSession.dimId(v.dim);
		Font font = Minecraft.getInstance().font;
		int size = v.minimap ? 12 : 16;
		for (Waypoint w : session.data.waypoints) {
			if (!w.visible || !w.dim.equals(dim)) continue;
			float sx = v.screenX(w.x + 0.5), sy = v.screenY(w.z + 0.5);
			boolean inside = v.inside(sx, sy, 0);
			if (!inside) {
				if (!v.minimap) continue;
				// Minimapa: fora da área, o alfinete fica na borda apontando a direção.
				sx = Mth.clamp(sx, v.x + 3, v.x + v.w - 3);
				sy = Mth.clamp(sy, v.y + size - 1, v.y + v.h);
			}
			g.pose().pushMatrix();
			g.pose().translate(sx - size / 2f, sy - size);
			g.pose().scale(size / 16f, size / 16f);
			if (w.death) g.blit(RenderPipelines.GUI_TEXTURED, SKULL, 0, 0, 0f, 0f, 16, 16, 16, 16);
			else {
				g.blit(RenderPipelines.GUI_TEXTURED, PIN, 0, 0, 0f, 0f, 16, 16, 16, 16, w.color | 0xFF000000);
				g.blit(RenderPipelines.GUI_TEXTURED, PIN_TOP, 0, 0, 0f, 0f, 16, 16, 16, 16);
			}
			g.pose().popMatrix();
			if (!v.minimap) {
				int tw = font.width(w.name);
				g.text(font, w.name, Mth.floor(sx) - tw / 2, Mth.floor(sy) - size - 10, 0xFFFFFFFF, true);
			}
			if (hits != null) {
				hits.add(new Hit(Mth.floor(sx) - size / 2, Mth.floor(sy) - size, Mth.floor(sx) + size / 2, Mth.floor(sy),
						List.of(Component.literal(w.name), coords(w.x, w.z)), w));
			}
		}
	}

	private static void drawEntities(GuiGraphicsExtractor g, View v, Minecraft mc, ClientLevel level, List<Hit> hits) {
		Player self = mc.player;
		if (self == null) return;
		float pt = v.partialTick;
		Font font = mc.font;
		g.enableScissor(v.x, v.y, v.x + v.w, v.y + v.h);
		try {
			// Mobs: só os perto da altura do jogador (os das cavernas embaixo não poluem o mapa) e com zoom de perto.
			if (v.detailed) {
				drawMobHeads(g, v, mc, level, self);
			} else if (Config.global.showMobs && v.scale >= 0.5f) {
				int n = 0;
				double py = self.getY();
				for (Entity e : level.entitiesForRendering()) {
					if (!(e instanceof LivingEntity) || e instanceof Player || e.isInvisibleTo(self) || Math.abs(e.getY() - py) > 12) continue;
					float sx = v.screenX(Mth.lerp(pt, e.xo, e.getX())), sy = v.screenY(Mth.lerp(pt, e.zo, e.getZ()));
					if (!v.inside(sx, sy, 2) || ++n > 300) continue;
					int c = e instanceof Enemy ? 0xFFE04040 : 0xFFF0E060;
					int ix = Mth.floor(sx), iy = Mth.floor(sy);
					g.fill(ix - 1, iy - 1, ix + 2, iy + 2, 0xC0000000);
					g.fill(ix, iy, ix + 1, iy + 1, c);
					if (!v.minimap) g.fill(ix - 1, iy, ix + 2, iy + 1, c);
				}
			}
			for (AbstractClientPlayer p : level.players()) {
				if (p == self || p.isInvisibleTo(self)) continue;
				float sx = v.screenX(Mth.lerp(pt, p.xo, p.getX())), sy = v.screenY(Mth.lerp(pt, p.zo, p.getZ()));
				if (!v.inside(sx, sy, 4)) continue;
				int s = v.minimap ? 8 : 10;
				int ix = Mth.floor(sx) - s / 2, iy = Mth.floor(sy) - s / 2;
				g.fill(ix - 1, iy - 1, ix + s + 1, iy + s + 1, 0xFFFFFFFF);
				PlayerFaceExtractor.extractRenderState(g, p.getSkin(), ix, iy, s);
				String name = p.getName().getString();
				if (!v.minimap) {
					g.text(font, name, Mth.floor(sx) - font.width(name) / 2, iy - 11, 0xFFFFFFFF, true);
				}
				if (hits != null) hits.add(new Hit(ix, iy, ix + s, iy + s, List.of(Component.literal(name), coords(p.getBlockX(), p.getBlockZ())), null));
			}
		} finally {
			g.disableScissor();
		}
		// O próprio jogador por cima de tudo (no minimapa fica sempre no meio).
		double px = Mth.lerp(pt, self.xo, self.getX()), pz = Mth.lerp(pt, self.zo, self.getZ());
		float sx = v.screenX(px), sy = v.screenY(pz);
		if (!v.inside(sx, sy, 0)) return;
		float yaw = self.getViewYRot(pt);
		int s = v.minimap ? 9 : 12;
		if (v.detailed && self instanceof AbstractClientPlayer me) {
			// Modo detalhado: a sua cabeça no meio e a seta (menor) logo à frente dela, na direção em que você olha.
			int hs = 10, hx = Mth.floor(sx) - hs / 2, hy = Mth.floor(sy) - hs / 2;
			g.fill(hx - 1, hy - 1, hx + hs + 1, hy + hs + 1, 0xFF4CC8F0);
			PlayerFaceExtractor.extractRenderState(g, me.getSkin(), hx, hy, hs);
			double a = Math.toRadians(yaw);
			sx += (float) (-Math.sin(a) * 9);
			sy += (float) (Math.cos(a) * 9);
			s = 7;
		}
		g.pose().pushMatrix();
		g.pose().translate(sx, sy);
		g.pose().rotate((float) Math.toRadians(yaw) + (float) Math.PI);
		g.pose().scale(s / 16f, s / 16f);
		g.blit(RenderPipelines.GUI_TEXTURED, ARROW, -8, -8, 0f, 0f, 16, 16, 16, 16);
		g.pose().popMatrix();
	}

	/**
	 * Cabeças dos mobs perto da altura do jogador (até 40, os mais perto por cima): o modelo do próprio jogo desenhado de
	 * frente e recortado na cabeça, com borda vermelha (hostis) ou amarela (os outros).
	 */
	private static void drawMobHeads(GuiGraphicsExtractor g, View v, Minecraft mc, ClientLevel level, Player self) {
		float pt = v.partialTick;
		double py = self.getY();
		List<LivingEntity> mobs = new ArrayList<>();
		for (Entity e : level.entitiesForRendering()) {
			if (e instanceof LivingEntity l && !(e instanceof Player) && !e.isInvisibleTo(self) && Math.abs(e.getY() - py) <= 12) mobs.add(l);
		}
		mobs.sort(java.util.Comparator.comparingDouble(e -> -e.distanceToSqr(self)));
		if (mobs.size() > 40) mobs = mobs.subList(mobs.size() - 40, mobs.size());
		int s = v.minimap ? 10 : 12;
		for (LivingEntity e : mobs) {
			float sx = v.screenX(Mth.lerp(pt, e.xo, e.getX())), sy = v.screenY(Mth.lerp(pt, e.zo, e.getZ()));
			int x = Mth.floor(sx) - s / 2, y = Mth.floor(sy) - s / 2;
			// A cabeça é desenhada fora do recorte do mapa: só as que cabem inteiras.
			if (x < v.x + 1 || y < v.y + 1 || x + s > v.x + v.w - 1 || y + s > v.y + v.h - 1) continue;
			mobHead(g, mc, e, x, y, s, e instanceof Enemy ? 0xFFE04040 : 0xFFF0E060);
		}
	}

	private static void mobHead(GuiGraphicsExtractor g, Minecraft mc, LivingEntity e, int x, int y, int s, int border) {
		g.fill(x - 1, y - 1, x + s + 1, y + s + 1, border);
		g.fill(x, y, x + s, y + s, 0xFF20242C);
		try {
			EntityRenderState st = mc.getEntityRenderDispatcher().getRenderer(e).createRenderState(e, 1f);
			st.shadowPieces.clear();
			st.outlineColor = 0;
			if (st instanceof LivingEntityRenderState l) {
				l.bodyRot = 180f;
				l.yRot = 0f;
				l.xRot = 0f;
			}
			// Enquadra a cabeça: o ponto dos olhos no meio e uns 0,4-1,1 bloco de largura na caixa.
			float view = Mth.clamp(e.getBbWidth(), 0.4f, 1f) * 0.9f + 0.15f;
			Quaternionf rot = new Quaternionf().rotateZ((float) Math.PI);
			g.entity(st, s / view, new Vector3f(0f, e.getEyeHeight(), 0f), rot, new Quaternionf(), x, y, x + s, y + s);
		} catch (RuntimeException ex) {
			g.fill(x + 2, y + 2, x + s - 2, y + s - 2, border);
		}
	}

	/** Nome do bioma da seed num ponto (se já calculado) para a barra de informações. */
	public static Component biomeAt(WorldSession session, ResourceKey<Level> dim, boolean caves, int x, int z) {
		WorldSession.SeedLayers seed = session.seedLayers(dim);
		if (seed == null || seed.world() == null) return null;
		BiomeLayer layer = seed.biomes(caves);
		int i = layer.biomeIndexAt(x, z);
		if (i < 0) return null;
		String id = SeedWorld.id(layer.world().biome(i));
		int c = id.indexOf(':');
		return Component.translatable("biome." + id.substring(0, c) + "." + id.substring(c + 1));
	}
}
