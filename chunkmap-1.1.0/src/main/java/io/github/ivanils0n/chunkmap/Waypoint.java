package io.github.ivanils0n.chunkmap;

/** Um ponto marcado no mapa (salvo por mundo/servidor no JSON do mundo). */
public final class Waypoint {
	public static final int[] COLORS = {
			0xFFE04848, 0xFF48A0E0, 0xFF4CC85A, 0xFFF0C030, 0xFFB060E0, 0xFFF08A30, 0xFF40D0D0, 0xFFF0F0F0,
	};

	public String name = "";
	public int x, y, z;
	/** Dimensão, ex.: minecraft:overworld. */
	public String dim = "minecraft:overworld";
	public int color = COLORS[0];
	public boolean visible = true;
	/** Ponto de morte (criado sozinho; só existe um por dimensão). */
	public boolean death;

	public Waypoint() {}

	public Waypoint(String name, int x, int y, int z, String dim, int color) {
		this.name = name;
		this.x = x;
		this.y = y;
		this.z = z;
		this.dim = dim;
		this.color = color;
	}

	public String initial() {
		String n = name == null ? "" : name.strip();
		return n.isEmpty() ? "?" : n.substring(0, n.offsetByCodePoints(0, 1)).toUpperCase(java.util.Locale.ROOT);
	}
}
