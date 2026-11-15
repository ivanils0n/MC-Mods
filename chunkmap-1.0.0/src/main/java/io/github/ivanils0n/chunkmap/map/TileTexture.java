package io.github.ivanils0n.chunkmap.map;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.concurrent.atomic.AtomicInteger;

/** Uma textura 128x128 de um pedaço do mapa. Criar, pintar e liberar: só na thread do jogo. */
public final class TileTexture {
	public static final int SIZE = 128;
	private static final AtomicInteger NEXT = new AtomicInteger();
	/** Texturas enviadas à placa de vídeo neste quadro (limite para não engasgar). */
	private static int uploads;
	private static long uploadFrame = -1;

	public final Identifier id;
	private DynamicTexture texture;

	public TileTexture() {
		this.id = Identifier.fromNamespaceAndPath("chunkmap", "tile/" + NEXT.incrementAndGet());
	}

	/** Pode enviar mais uma textura neste quadro? (no máximo 8 por quadro) */
	public static boolean canUpload(long frame) {
		if (frame != uploadFrame) {
			uploadFrame = frame;
			uploads = 0;
		}
		return uploads < 8;
	}

	/** Pinta a textura com os pixels ARGB (SIZE*SIZE) e envia para a placa de vídeo. */
	public void paint(int[] argb) {
		uploads++;
		if (texture == null) {
			NativeImage image = new NativeImage(SIZE, SIZE, false);
			fill(image, argb);
			texture = new DynamicTexture(() -> "chunkmap tile", image);
			Minecraft.getInstance().getTextureManager().register(id, texture);
		} else {
			fill(texture.getPixels(), argb);
		}
		texture.upload();
	}

	private static void fill(NativeImage image, int[] argb) {
		for (int y = 0, i = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++, i++) image.setPixel(x, y, argb[i]);
		}
	}

	public boolean ready() {
		return texture != null;
	}

	public void release() {
		if (texture != null) {
			Minecraft.getInstance().getTextureManager().release(id);
			texture = null;
		}
	}
}
