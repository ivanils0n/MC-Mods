package io.github.ivanils0n.chunkmap;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Threads do mod: nada de cálculo pesado na thread do jogo. */
public final class Workers {
	/**
	 * Cálculo de biomas e estruturas. A fila é uma pilha (o pedido mais novo sai primeiro): ao arrastar o mapa, o que
	 * está na tela agora é calculado antes do que já saiu dela.
	 */
	public static final ThreadPoolExecutor MAP;
	/** Tarefas longas e raras (carregar a geração vanilla, achar as fortalezas). */
	public static final ExecutorService SLOW = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, new LinkedBlockingDeque<>(), factory("ChunkMap-lento"));
	/** Leitura e gravação de arquivos, em ordem. */
	public static final ExecutorService IO = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, new LinkedBlockingDeque<>(), factory("ChunkMap-disco"));

	static {
		int n = Math.clamp(Runtime.getRuntime().availableProcessors() - 2, 1, 3);
		MAP = new ThreadPoolExecutor(n, n, 30, TimeUnit.SECONDS, new LinkedBlockingDeque<>() {
			@Override
			public boolean offer(Runnable r) {
				return offerFirst(r);
			}
		}, factory("ChunkMap-mapa"));
		MAP.allowCoreThreadTimeOut(true);
		((ThreadPoolExecutor) SLOW).allowCoreThreadTimeOut(true);
		((ThreadPoolExecutor) IO).allowCoreThreadTimeOut(true);
	}

	private Workers() {}

	private static ThreadFactory factory(String name) {
		AtomicInteger n = new AtomicInteger();
		return r -> {
			Thread t = new Thread(r, name + "-" + n.incrementAndGet());
			t.setDaemon(true);
			t.setPriority(Thread.NORM_PRIORITY - 2);
			t.setUncaughtExceptionHandler((th, e) -> ChunkMap.LOG.error("ChunkMap: erro em {}", th.getName(), e));
			return t;
		};
	}

	/** Pedidos de cálculo ainda na fila (para não empilhar sem fim). */
	public static int mapBacklog() {
		return MAP.getQueue().size();
	}
}
