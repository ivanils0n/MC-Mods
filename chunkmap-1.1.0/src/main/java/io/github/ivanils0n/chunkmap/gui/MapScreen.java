package io.github.ivanils0n.chunkmap.gui;

import io.github.ivanils0n.chunkmap.ChunkMap;
import io.github.ivanils0n.chunkmap.Keys;
import io.github.ivanils0n.chunkmap.Waypoint;
import io.github.ivanils0n.chunkmap.WorldSession;
import io.github.ivanils0n.chunkmap.map.MapRenderer;
import io.github.ivanils0n.chunkmap.seed.BiomeColors;
import io.github.ivanils0n.chunkmap.seed.SeedWorld;
import io.github.ivanils0n.chunkmap.seed.StructureIcon;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;

/** O mapa em tela cheia (tecla M), no estilo do Chunkbase: filtros em cima, seed e opções embaixo. */
public final class MapScreen extends Screen {
	static final int BG = 0xFFCFE0F2, PANEL = 0xFFE6EFF9, LINE = 0xFF5B8FC7, TEXT = 0xFF2A4A70;
	private static final float MIN_SCALE = 1 / 64f, MAX_SCALE = 8f;
	private static final List<ResourceKey<Level>> DIMS = List.of(Level.OVERWORLD, Level.NETHER, Level.END);

	/** Zoom e dimensão da última vez (o centro volta para o jogador ao abrir). */
	private static float lastScale = 1f;

	private final MapRenderer.View view = new MapRenderer.View();
	private final List<MapRenderer.Hit> hits = new ArrayList<>();
	private final List<Label> labels = new ArrayList<>();
	private ResourceKey<Level> dim;
	private int toolbarBottom, bottomTop, iconStep, iconRows, iconsPerRow;
	private EditBox seedBox, xBox, zBox;
	private Button dimButton, biomesButton, optionsButton;
	private static final net.minecraft.resources.Identifier GEAR = net.minecraft.resources.Identifier.fromNamespaceAndPath("chunkmap", "textures/gui/gear.png");
	private boolean dragging;
	private double mouseX, mouseY;
	private boolean biomePanel;
	/** TAB apertado com o mapa aberto: cabeças dos mobs e jogadores (se "Mobs no mapa" estiver ligado). */
	private boolean tabHeld;
	/** Mostra o mapa das cavernas (começa ligado se o modo caverna estiver valendo). */
	private boolean caveMap;
	private int biomeScroll;
	private List<Holder<Biome>> biomeList = List.of();

	private record Label(Component text, int x, int y) {}

	public MapScreen() {
		super(Component.translatable("chunkmap.map.title"));
		var mc = net.minecraft.client.Minecraft.getInstance();
		this.dim = mc.level != null ? mc.level.dimension() : Level.OVERWORLD;
		if (mc.player != null) {
			view.cx = mc.player.getX();
			view.cz = mc.player.getZ();
		}
		view.scale = lastScale;
		WorldSession s = ChunkMap.session();
		caveMap = s != null && s.caveActive;
	}

	/** Abre numa dimensão, centro e zoom escolhidos (usado pelo teste de prints). */
	public MapScreen(ResourceKey<Level> dim, double cx, double cz, float scale) {
		this();
		this.dim = dim;
		view.cx = cx;
		view.cz = cz;
		view.scale = Mth.clamp(scale, MIN_SCALE, MAX_SCALE);
	}

	/** Abre/fecha a lista de biomas (teste). */
	public void showBiomePanel() {
		if (!biomePanel) toggleBiomePanel();
	}

	private WorldSession session() {
		return ChunkMap.session();
	}

	@Override
	protected void init() {
		WorldSession s = session();
		if (s == null) {
			onClose();
			return;
		}
		labels.clear();
		// Barra de filtros: uma ou duas fileiras de ícones, conforme a largura.
		int buttonsW = 74 + 4 + 74 + 4 + 20;
		int available = width - 8 - 12 - buttonsW - 8;
		int n = StructureIcon.values().length;
		iconStep = Mth.clamp(available / n, 17, 20);
		iconsPerRow = Math.max(1, available / iconStep);
		iconRows = (n + iconsPerRow - 1) / iconsPerRow;
		toolbarBottom = 4 + 14 + iconRows * iconStep + 4;
		int by = 4 + (toolbarBottom - 4) / 2 - 10;
		int bx = width - 4 - 6 - buttonsW;
		biomesButton = addRenderableWidget(Button.builder(Component.translatable("chunkmap.map.biomes"), b -> toggleBiomePanel())
				.bounds(bx, by, 74, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("chunkmap.map.waypoints"), b -> minecraft.gui.setScreen(new WaypointListScreen(this)))
				.bounds(bx + 78, by, 74, 20).build());
		// Ícone de engrenagem desenhado por cima (o "⚙" da fonte do jogo parece um sol).
		optionsButton = addRenderableWidget(Button.builder(Component.empty(), b -> minecraft.gui.setScreen(new OptionsScreen(this)))
				.bounds(bx + 156, by, 20, 20).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("chunkmap.options.title"))).build());

		// Barra de baixo: os controles em sequência; quando não cabe, quebra a linha (a barra cresce para cima).
		List<List<Item>> groups = new ArrayList<>();
		seedBox = new EditBox(font, 0, 0, 132, 20, Component.translatable("chunkmap.map.seed"));
		seedBox.setMaxLength(64);
		seedBox.setHint(Component.translatable("chunkmap.map.seed_hint"));
		OptionalLong seed = s.seed();
		seedBox.setValue(s.singleplayer && s.data.autoSeed ? (seed.isPresent() ? Long.toString(seed.getAsLong()) : "") : s.data.seed);
		seedBox.moveCursorToStart(false);
		Button apply = Button.builder(Component.translatable("chunkmap.map.apply"), b -> applySeed()).size(52, 20).build();
		groups.add(List.of(label(Component.translatable("chunkmap.map.seed").append(":")), item(seedBox), item(apply)));
		xBox = new EditBox(font, 0, 0, 48, 20, Component.literal("X"));
		zBox = new EditBox(font, 0, 0, 48, 20, Component.literal("Z"));
		xBox.setValue(Integer.toString(Mth.floor(view.cx)));
		zBox.setValue(Integer.toString(Mth.floor(view.cz)));
		Button go = Button.builder(Component.translatable("chunkmap.map.go"), b -> goTo()).size(28, 20).build();
		groups.add(List.of(label(Component.literal("X:")), item(xBox), label(Component.literal("Z:")), item(zBox), item(go)));
		dimButton = Button.builder(dimName(), b -> cycleDim()).size(84, 20).build();
		Checkbox grid = Checkbox.builder(Component.translatable("chunkmap.map.grid"), font).selected(s.data.showGrid)
				.onValueChange((c, v) -> {
					s.data.showGrid = v;
					s.markDirty();
				}).build();
		Checkbox terrain = Checkbox.builder(Component.translatable("chunkmap.map.terrain"), font).selected(s.data.showTerrain)
				.onValueChange((c, v) -> {
					s.data.showTerrain = v;
					s.markDirty();
				}).build();
		Checkbox caves = Checkbox.builder(Component.translatable("chunkmap.map.caves"), font).selected(caveMap)
				.onValueChange((c, v) -> caveMap = v).build();
		groups.add(List.of(item(dimButton), item(grid), item(terrain), item(caves)));
		Button wp = Button.builder(Component.translatable("chunkmap.map.add_waypoint"), b -> newWaypoint(false)).size(100, 20).build();
		groups.add(List.of(item(wp)));

		List<List<Item>> rows = new ArrayList<>();
		List<Item> row = new ArrayList<>();
		List<Integer> xs = new ArrayList<>();
		List<Integer> rowOf = new ArrayList<>();
		List<Item> all = new ArrayList<>();
		int rowW = 0, maxW = width - 8 - 12;
		for (List<Item> group : groups) {
			int gw = 0;
			for (Item it : group) gw += it.width + 4;
			if (!row.isEmpty() && rowW + 14 + gw > maxW) {
				rows.add(row);
				row = new ArrayList<>();
				rowW = 0;
			}
			if (!row.isEmpty()) rowW += 14;
			for (Item it : group) {
				all.add(it);
				xs.add(rowW);
				rowOf.add(rows.size());
				row.add(it);
				rowW += it.width + 4;
			}
		}
		rows.add(row);
		bottomTop = height - 4 - rows.size() * 24 - 4;
		for (int i = 0; i < all.size(); i++) {
			Item it = all.get(i);
			int x = 4 + 6 + xs.get(i), y = bottomTop + 6 + rowOf.get(i) * 24;
			if (it.widget == null) {
				labels.add(new Label(it.label, x, y + 6));
			} else {
				it.widget.setX(x);
				it.widget.setY(y);
				addRenderableWidget(it.widget);
			}
		}

		// Mapa entre as barras, com +, - e "centralizar" no canto de baixo à direita.
		view.x = 6;
		view.y = toolbarBottom + 6;
		view.w = width - 12;
		view.h = bottomTop - 6 - view.y;
		int zx = view.x + view.w - 24, zy = view.y + view.h - 70;
		addRenderableWidget(Button.builder(Component.literal("+"), b -> zoom(1.5f, view.x + view.w / 2.0, view.y + view.h / 2.0)).bounds(zx, zy, 20, 20).build());
		addRenderableWidget(Button.builder(Component.literal("-"), b -> zoom(1 / 1.5f, view.x + view.w / 2.0, view.y + view.h / 2.0)).bounds(zx, zy + 22, 20, 20).build());
		addRenderableWidget(Button.builder(Component.literal("◎"), b -> centerOnPlayer()).bounds(zx, zy + 44, 20, 20)
				.tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("chunkmap.map.center"))).build());
		updateBiomeList();
	}

	/** Um controle da barra de baixo, ou só um texto (widget null). */
	private record Item(AbstractWidget widget, Component label, int width) {}

	private static Item item(AbstractWidget w) {
		return new Item(w, null, w.getWidth());
	}

	private Item label(Component c) {
		return new Item(null, c, font.width(c) + 2);
	}

	private Component dimName() {
		return Component.translatable("chunkmap.dim." + dim.identifier().getPath());
	}

	private void cycleDim() {
		ResourceKey<Level> old = dim;
		dim = DIMS.get((DIMS.indexOf(dim) + 1) % DIMS.size());
		var player = minecraft.player;
		if (player != null && player.level().dimension().equals(dim)) {
			view.cx = player.getX();
			view.cz = player.getZ();
		} else if (old.equals(Level.OVERWORLD) && dim.equals(Level.NETHER)) {
			view.cx /= 8;
			view.cz /= 8;
		} else if (old.equals(Level.NETHER) && dim.equals(Level.OVERWORLD)) {
			view.cx *= 8;
			view.cz *= 8;
		} else {
			view.cx = view.cz = 0;
		}
		dimButton.setMessage(dimName());
		updateBiomeList();
	}

	private void applySeed() {
		WorldSession s = session();
		String text = seedBox.getValue().strip();
		if (s.singleplayer) {
			OptionalLong own = OptionalLong.of(minecraft.getSingleplayerServer().overworld().getSeed());
			// Vazio ou a própria seed do mundo: volta ao automático (geração exata do mundo local).
			s.data.autoSeed = text.isEmpty() || own.equals(net.minecraft.world.level.levelgen.WorldOptions.parseSeed(text));
			s.data.seed = s.data.autoSeed ? "" : text;
			if (s.data.autoSeed) seedBox.setValue(Long.toString(own.getAsLong()));
		} else {
			s.data.seed = text;
		}
		s.resetSeed();
		updateBiomeList();
	}

	private void goTo() {
		try {
			view.cx = Integer.parseInt(xBox.getValue().strip()) + 0.5;
			view.cz = Integer.parseInt(zBox.getValue().strip()) + 0.5;
		} catch (NumberFormatException ignored) {
		}
	}

	private void centerOnPlayer() {
		var p = minecraft.player;
		if (p == null) return;
		if (!p.level().dimension().equals(dim)) {
			dim = p.level().dimension();
			dimButton.setMessage(dimName());
			updateBiomeList();
		}
		view.cx = p.getX();
		view.cz = p.getZ();
	}

	private void zoom(float factor, double sx, double sy) {
		double wx = view.worldX(sx), wz = view.worldZ(sy);
		view.scale = Mth.clamp(view.scale * factor, MIN_SCALE, MAX_SCALE);
		lastScale = view.scale;
		// O ponto sob o mouse fica parado.
		view.cx = wx - (sx - view.x - view.w / 2.0) / view.scale;
		view.cz = wz - (sy - view.y - view.h / 2.0) / view.scale;
	}

	private boolean overMap(double mx, double my) {
		return mx >= view.x && mx < view.x + view.w && my >= view.y && my < view.y + view.h && !overBiomePanel(mx, my);
	}

	/** Novo waypoint: no ponto do mouse (sobre o mapa) ou onde o jogador está. */
	private void newWaypoint(boolean atMouse) {
		var p = minecraft.player;
		int x, y, z;
		if (atMouse && overMap(mouseX, mouseY)) {
			x = Mth.floor(view.worldX(mouseX));
			z = Mth.floor(view.worldZ(mouseY));
			y = p != null && p.level().dimension().equals(dim) ? p.getBlockY() : 64;
		} else if (p != null && p.level().dimension().equals(dim)) {
			x = p.getBlockX();
			y = p.getBlockY();
			z = p.getBlockZ();
		} else {
			x = Mth.floor(view.cx);
			y = 64;
			z = Mth.floor(view.cz);
		}
		Waypoint w = new Waypoint("", x, y, z, WorldSession.dimId(dim), Waypoint.COLORS[session().data.waypoints.size() % Waypoint.COLORS.length]);
		minecraft.gui.setScreen(new WaypointScreen(this, w, true));
	}

	// ---------------------------------------------------------------- biomas (destaque)

	private void toggleBiomePanel() {
		biomePanel = !biomePanel;
		biomeScroll = 0;
		updateBiomeList();
	}

	private void updateBiomeList() {
		WorldSession s = session();
		if (s == null) return;
		WorldSession.SeedLayers l = s.seedLayers(dim);
		SeedWorld w = l == null ? null : l.world();
		if (w == null) {
			biomeList = List.of();
			return;
		}
		List<Holder<Biome>> list = new ArrayList<>(w.biomeSource.possibleBiomes());
		list.sort(Comparator.comparing(h -> biomeName(h).getString().toLowerCase(Locale.ROOT)));
		biomeList = list;
	}

	private static Component biomeName(Holder<Biome> h) {
		String id = SeedWorld.id(h);
		int c = id.indexOf(':');
		return Component.translatable("biome." + id.substring(0, c) + "." + id.substring(c + 1));
	}

	private int panelX() {
		return view.x + view.w - 158;
	}

	private boolean overBiomePanel(double mx, double my) {
		return biomePanel && mx >= panelX() && mx < panelX() + 152 && my >= view.y + 4 && my < view.y + view.h - 4;
	}

	private int panelRows() {
		return Math.max(1, (view.h - 8 - 16) / 11);
	}

	private void drawBiomePanel(GuiGraphicsExtractor g, int mx, int my) {
		int x = panelX(), y = view.y + 4, h = view.h - 8;
		g.fill(x, y, x + 152, y + h, 0xF0E6EFF9);
		g.outline(x, y, 152, h, LINE);
		WorldSession s = session();
		Component head = s.highlight.isEmpty() ? Component.translatable("chunkmap.map.biomes_hint") : Component.translatable("chunkmap.map.biomes_clear");
		g.text(font, head, x + 4, y + 4, s.highlight.isEmpty() ? 0xFF6A7A8A : 0xFFB03030, false);
		if (biomeList.isEmpty()) {
			g.text(font, Component.translatable("chunkmap.map.no_seed_short"), x + 4, y + 20, TEXT, false);
			return;
		}
		int rows = panelRows();
		biomeScroll = Mth.clamp(biomeScroll, 0, Math.max(0, biomeList.size() - rows));
		for (int i = 0; i < rows && biomeScroll + i < biomeList.size(); i++) {
			Holder<Biome> b = biomeList.get(biomeScroll + i);
			String id = SeedWorld.id(b);
			int ry = y + 16 + i * 11;
			boolean on = s.highlight.contains(id);
			if (on) g.fill(x + 1, ry - 1, x + 151, ry + 10, 0x405B8FC7);
			if (mx >= x && mx < x + 152 && my >= ry - 1 && my < ry + 10) g.fill(x + 1, ry - 1, x + 151, ry + 10, 0x205B8FC7);
			g.fill(x + 4, ry, x + 13, ry + 9, 0xFF000000);
			g.fill(x + 5, ry + 1, x + 12, ry + 8, BiomeColors.of(id));
			g.text(font, biomeName(b), x + 17, ry + 1, on ? 0xFF000000 : TEXT, false);
		}
	}

	private boolean clickBiomePanel(double mx, double my) {
		int x = panelX(), y = view.y + 4;
		WorldSession s = session();
		if (my < y + 14) {
			s.highlight.clear();
			return true;
		}
		int i = (int) ((my - y - 15) / 11);
		if (i >= 0 && i < panelRows() && biomeScroll + i < biomeList.size()) {
			String id = SeedWorld.id(biomeList.get(biomeScroll + i));
			if (!s.highlight.remove(id)) s.highlight.add(id);
		}
		return true;
	}

	// ---------------------------------------------------------------- desenho

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float pt) {
		WorldSession s = session();
		if (s == null) return;
		g.fill(0, 0, width, height, BG);
		// Barra de filtros
		panel(g, 4, 4, width - 8, toolbarBottom - 4);
		g.text(font, Component.translatable("chunkmap.map.filters"), 10, 7, TEXT, false);
		StructureIcon[] icons = StructureIcon.values();
		for (int i = 0; i < icons.length; i++) {
			int ix = 10 + (i % iconsPerRow) * iconStep, iy = 18 + (i / iconsPerRow) * iconStep;
			boolean on = s.data.filters.contains(icons[i].id);
			g.fill(ix - 1, iy - 1, ix + 17, iy + 17, on ? LINE : 0xFFB8C8D8);
			g.fill(ix, iy, ix + 16, iy + 16, 0xFFFFFFFF);
			g.blit(RenderPipelines.GUI_TEXTURED, icons[i].texture, ix, iy, 0f, 0f, 16, 16, 16, 16, on ? 0xFFFFFFFF : 0x55FFFFFF);
		}
		// Barra de baixo
		panel(g, 4, bottomTop, width - 8, height - 4 - bottomTop);
		for (Label l : labels) g.text(font, l.text, l.x, l.y, TEXT, false);
		// Mapa
		view.dim = dim;
		view.minimap = false;
		view.partialTick = pt;
		view.grid = s.data.showGrid;
		view.terrain = s.data.showTerrain;
		view.biomes = true;
		view.caveMap = caveMap;
		view.detailed = tabHeld && io.github.ivanils0n.chunkmap.Config.global.showMobs;
		view.caves = "caves".equals(s.data.biomeDepth);
		view.filters = s.filters();
		hits.clear();
		g.fill(view.x - 2, view.y - 2, view.x + view.w + 2, view.y + view.h + 2, LINE);
		MapRenderer.draw(g, view, hits);
		axes(g);
	}

	private static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, LINE);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, PANEL);
	}

	/** Coordenadas nas bordas do mapa, como no Chunkbase. */
	private void axes(GuiGraphicsExtractor g) {
		double span = 80 / view.scale; // ~80 pixels entre marcas
		double step = Math.pow(2, Math.ceil(Math.log(span) / Math.log(2)));
		double x0 = Math.ceil(view.worldX(view.x + 30) / step) * step;
		for (double wx = x0; wx < view.worldX(view.x + view.w - 30); wx += step) {
			pill(g, String.format(Locale.ROOT, "%,d", (long) wx).replace(',', '.'), Mth.floor(view.screenX(wx)), view.y + 9);
		}
		double z0 = Math.ceil(view.worldZ(view.y + 26) / step) * step;
		for (double wz = z0; wz < view.worldZ(view.y + view.h - 12); wz += step) {
			String t = String.format(Locale.ROOT, "%,d", (long) wz).replace(',', '.');
			pill(g, t, view.x + 6 + font.width(t) / 2 + 3, Mth.floor(view.screenY(wz)));
		}
	}

	private void pill(GuiGraphicsExtractor g, String text, int cx, int cy) {
		int w = font.width(text);
		g.fill(cx - w / 2 - 3, cy - 5, cx + w / 2 + 3, cy + 5, 0xC8282832);
		g.text(font, text, cx - w / 2, cy - 4, 0xFFFFFFFF, false);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
		this.mouseX = mx;
		this.mouseY = my;
		super.extractRenderState(g, mx, my, pt);
		if (optionsButton != null && optionsButton.visible) g.blit(RenderPipelines.GUI_TEXTURED, GEAR, optionsButton.getX() + 2, optionsButton.getY() + 2, 0f, 0f, 16, 16, 16, 16);
		WorldSession s = session();
		if (s == null) return;
		// Avisos no meio/topo do mapa
		WorldSession.SeedStatus status = s.status(dim);
		Component msg = switch (status) {
			case NONE -> Component.translatable(s.singleplayer ? "chunkmap.map.no_seed_sp" : "chunkmap.map.no_seed");
			case LOADING -> Component.translatable("chunkmap.map.loading");
			case UNSUPPORTED -> Component.translatable("chunkmap.map.unsupported");
			case ERROR -> Component.translatable("chunkmap.map.error");
			case READY -> null;
		};
		if (msg != null) banner(g, msg, view.y + view.h / 2 - 6);
		if (view.limited) banner(g, Component.translatable("chunkmap.map.zoom_in"), view.y + 22);
		// Escala e posição do mouse
		String scale = view.scale >= 1 ? String.format(Locale.ROOT, "1 bl = %s px", trim(view.scale)) : String.format(Locale.ROOT, "1 px = %s bl", trim(1 / view.scale));
		if (view.busy) scale = "… " + scale;
		int sw = font.width(scale);
		int sx = view.x + view.w - 30 - sw, sy = view.y + view.h - 16;
		g.fill(sx - 4, sy - 3, sx + sw + 4, sy + 11, 0xDDFFFFFF);
		g.text(font, scale, sx, sy, 0xFF202020, false);
		if (overMap(mx, my)) {
			int wx = Mth.floor(view.worldX(mx)), wz = Mth.floor(view.worldZ(my));
			Component c = MapRenderer.coords(wx, wz).copy().withStyle(net.minecraft.ChatFormatting.WHITE);
			Component biome = MapRenderer.biomeAt(s, dim, view.caves, wx, wz);
			if (biome != null) c = c.copy().append(Component.literal(" · ")).append(biome);
			int cw = font.width(c);
			g.fill(view.x + 4, view.y + view.h - 16, view.x + 12 + cw, view.y + view.h - 4, 0xC8282832);
			g.text(font, c, view.x + 8, view.y + view.h - 14, 0xFFFFFFFF, false);
		}
		if (biomePanel) drawBiomePanel(g, mx, my);
		// Dicas: ícones da barra e coisas no mapa
		StructureIcon[] icons = StructureIcon.values();
		for (int i = 0; i < icons.length; i++) {
			int ix = 10 + (i % iconsPerRow) * iconStep, iy = 18 + (i / iconsPerRow) * iconStep;
			if (mx >= ix - 1 && mx < ix + 17 && my >= iy - 1 && my < iy + 17) g.setTooltipForNextFrame(font, icons[i].label(), mx, my);
		}
		if (overMap(mx, my) && !dragging) {
			for (int i = hits.size() - 1; i >= 0; i--) {
				MapRenderer.Hit h = hits.get(i);
				if (h.contains(mx, my)) {
					g.setComponentTooltipForNextFrame(font, h.lines(), mx, my);
					break;
				}
			}
		}
	}

	private static String trim(float f) {
		return f == Math.floor(f) ? Integer.toString((int) f) : String.format(Locale.ROOT, "%.2f", f).replaceAll("0+$", "");
	}

	private void banner(GuiGraphicsExtractor g, Component msg, int y) {
		// Quebra em linhas se não couber na largura do mapa.
		List<net.minecraft.util.FormattedCharSequence> lines = font.split(msg, Math.max(60, view.w - 40));
		int w = 0;
		for (var l : lines) w = Math.max(w, font.width(l));
		int x = view.x + view.w / 2 - w / 2, h = lines.size() * 10 + 6;
		g.fill(x - 6, y - 4, x + w + 6, y - 4 + h, 0xE0FFF4D0);
		g.outline(x - 6, y - 4, w + 12, h, 0xFFD0A040);
		for (int i = 0; i < lines.size(); i++) g.text(font, lines.get(i), x + (w - font.width(lines.get(i))) / 2, y + i * 10, 0xFF5A4010, false);
	}

	// ---------------------------------------------------------------- entrada

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		double mx = e.x(), my = e.y();
		// A lista de biomas fica por cima dos botões de zoom: ela recebe o clique primeiro.
		if (overBiomePanel(mx, my)) return clickBiomePanel(mx, my);
		if (super.mouseClicked(e, doubleClick)) return true;
		WorldSession s = session();
		if (s == null) return false;
		StructureIcon[] icons = StructureIcon.values();
		for (int i = 0; i < icons.length; i++) {
			int ix = 10 + (i % iconsPerRow) * iconStep, iy = 18 + (i / iconsPerRow) * iconStep;
			if (mx >= ix - 1 && mx < ix + 17 && my >= iy - 1 && my < iy + 17) {
				if (!s.data.filters.remove(icons[i].id)) s.data.filters.add(icons[i].id);
				s.markDirty();
				return true;
			}
		}
		if (!overMap(mx, my)) return false;
		if (e.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			mouseX = mx;
			mouseY = my;
			newWaypoint(true);
			return true;
		}
		if (e.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			for (int i = hits.size() - 1; i >= 0; i--) {
				MapRenderer.Hit h = hits.get(i);
				if (h.waypoint() != null && h.contains(mx, my)) {
					minecraft.gui.setScreen(new WaypointScreen(this, h.waypoint(), false));
					return true;
				}
			}
			dragging = true;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		if (dragging) {
			view.cx -= dx / view.scale;
			view.cz -= dy / view.scale;
			return true;
		}
		return super.mouseDragged(e, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		dragging = false;
		return super.mouseReleased(e);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		if (overBiomePanel(mx, my)) {
			biomeScroll -= (int) Math.signum(sy) * 3;
			return true;
		}
		if (overMap(mx, my) && sy != 0) {
			zoom(sy > 0 ? 1.25f : 0.8f, mx, my);
			return true;
		}
		return super.mouseScrolled(mx, my, sx, sy);
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		boolean typing = getFocused() instanceof EditBox box && box.isFocused();
		if (!typing && minecraft.options.keyPlayerList.matches(e)) {
			tabHeld = true; // (e não passa o foco para o próximo botão, como o TAB faria)
			return true;
		}
		if (!typing) {
			if (Keys.OPEN_MAP.matches(e)) {
				onClose();
				return true;
			}
			if (Keys.WAYPOINT.matches(e)) {
				newWaypoint(true);
				return true;
			}
			double step = 48 / view.scale;
			switch (e.key()) {
				case GLFW.GLFW_KEY_SPACE -> centerOnPlayer();
				case GLFW.GLFW_KEY_LEFT -> view.cx -= step;
				case GLFW.GLFW_KEY_RIGHT -> view.cx += step;
				case GLFW.GLFW_KEY_UP -> view.cz -= step;
				case GLFW.GLFW_KEY_DOWN -> view.cz += step;
				default -> {
					return super.keyPressed(e);
				}
			}
			return true;
		}
		if (e.key() == GLFW.GLFW_KEY_ENTER || e.key() == GLFW.GLFW_KEY_KP_ENTER) {
			if (getFocused() == seedBox) applySeed();
			else if (getFocused() == xBox || getFocused() == zBox) goTo();
			return true;
		}
		return super.keyPressed(e);
	}

	@Override
	public boolean keyReleased(KeyEvent e) {
		if (minecraft.options.keyPlayerList.matches(e)) tabHeld = false;
		return super.keyReleased(e);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void removed() {
		WorldSession s = session();
		if (s != null) s.save();
	}
}
