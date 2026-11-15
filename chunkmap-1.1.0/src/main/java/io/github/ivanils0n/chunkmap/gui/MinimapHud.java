package io.github.ivanils0n.chunkmap.gui;

import io.github.ivanils0n.chunkmap.ChunkMap;
import io.github.ivanils0n.chunkmap.Config;
import io.github.ivanils0n.chunkmap.WorldSession;
import io.github.ivanils0n.chunkmap.map.MapRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** O minimapa no canto superior esquerdo, com moldura azul, "N" em cima e coordenadas/bioma/horário embaixo. */
public final class MinimapHud {
	static final int OUTER = 0xFF3D6FA8, LIGHT = 0xFFCFE0F2, INNER = 0xFF8FB3D9;
	private static final int MARGIN = 6, BORDER = 4;
	private static final MapRenderer.View VIEW = new MapRenderer.View();

	private MinimapHud() {}

	public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		WorldSession session = ChunkMap.session();
		LocalPlayer player = mc.player;
		if (!Config.global.minimap || session == null || player == null || mc.level == null) return;
		if (mc.gui.screen() instanceof MapScreen) return; // o mapa grande já está aberto
		if (mc.debugEntries.isOverlayVisible()) return; // F3: as informações do jogo ficam no lugar do minimapa
		float pt = delta.getGameTimeDeltaPartialTick(false);
		int size = Config.global.minimapSize;
		int x = MARGIN, y = MARGIN, outer = size + BORDER * 2;

		frame(g, x, y, outer, outer);
		MapRenderer.View v = VIEW;
		v.x = x + BORDER;
		v.y = y + BORDER;
		v.w = v.h = size;
		v.cx = Mth.lerp(pt, player.xo, player.getX());
		v.cz = Mth.lerp(pt, player.zo, player.getZ());
		v.scale = Config.global.minimapZoom;
		v.minimap = true;
		v.dim = mc.level.dimension();
		v.partialTick = pt;
		v.grid = false;
		v.caves = "caves".equals(session.data.biomeDepth);
		v.filters = session.filters();
		boolean seed = session.seedLayers(v.dim) != null;
		switch (Config.global.minimapLayer) {
			case BIOMES -> {
				v.biomes = true;
				v.terrain = false;
			}
			case TERRAIN -> {
				v.biomes = false;
				v.terrain = true;
			}
			default -> {
				v.biomes = seed;
				v.terrain = !seed;
			}
		}
		v.caveMap = session.caveActive;
		// TAB (lista de jogadores) apertado: modo detalhado, com a cabeça de cada mob e a sua.
		v.detailed = mc.options.keyPlayerList.isDown();
		MapRenderer.draw(g, v, null);

		// "N" (norte) em cima, no meio.
		int tabX = x + outer / 2 - 6, tabY = y - 5;
		g.blit(RenderPipelines.GUI_TEXTURED, MapRenderer.COMPASS, tabX, tabY, 0f, 0f, 12, 10, 12, 10);
		g.text(mc.font, "N", tabX + 6 - mc.font.width("N") / 2, tabY + 1, OUTER, false);

		info(g, mc, session, player, x, y + outer + 2, outer);
	}

	/** Moldura de 4 pixels: azul escuro, azul claro e uma linha azul média por dentro, com cantos arredondados. */
	static void frame(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x + 1, y, x + w - 1, y + h, OUTER);
		g.fill(x, y + 1, x + w, y + h - 1, OUTER);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, LIGHT);
		g.fill(x + 3, y + 3, x + w - 3, y + h - 3, INNER);
	}

	private static void info(GuiGraphicsExtractor g, Minecraft mc, WorldSession session, LocalPlayer player, int x, int y, int w) {
		Font font = mc.font;
		List<Component> lines = new ArrayList<>(3);
		List<Integer> colors = new ArrayList<>(3);
		if (Config.global.showCoords) {
			BlockPos p = player.blockPosition();
			lines.add(Component.literal(String.format(Locale.ROOT, "X: %d Y: %d Z: %d", p.getX(), p.getY(), p.getZ())));
			colors.add(0xFFFFFFFF);
		}
		if (Config.global.showBiome) {
			Holder<Biome> biome = mc.level.getBiome(player.blockPosition());
			lines.add(biome.unwrapKey()
					.map(k -> (Component) Component.translatable("biome." + k.identifier().getNamespace() + "." + k.identifier().getPath()))
					.orElse(Component.literal("?")));
			colors.add(0xFF8DE07A);
		}
		if (Config.global.showTime) {
			long t = mc.level.getOverworldClockTime();
			long day = t / 24000L + 1;
			int hours = (int) ((t / 1000L + 6) % 24), minutes = (int) (t % 1000L * 60 / 1000);
			lines.add(Component.translatable("chunkmap.hud.time", day, String.format(Locale.ROOT, "%02d:%02d", hours, minutes)));
			colors.add(0xFFD0D8E0);
		}
		if (lines.isEmpty()) return;
		int h = lines.size() * 10 + 4;
		g.fill(x, y, x + w, y + h, 0xAA141C28);
		for (int i = 0; i < lines.size(); i++) {
			Component c = lines.get(i);
			int tw = font.width(c);
			// Texto mais largo que o minimapa: encolhe em vez de cortar.
			if (tw > w - 6) {
				float s = (w - 6f) / tw;
				g.pose().pushMatrix();
				g.pose().translate(x + 3, y + 3 + i * 10 + (1 - s) * 4);
				g.pose().scale(s, s);
				g.text(font, c, 0, 0, colors.get(i), true);
				g.pose().popMatrix();
			} else {
				g.text(font, c, x + 3, y + 3 + i * 10, colors.get(i), true);
			}
		}
	}
}
