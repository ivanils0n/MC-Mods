package io.github.ivanils0n.smartchest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import net.minecraft.SharedConstants;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Nomes de itens no idioma de cada jogador, para a pesquisa e a ordenação por nome.
 *
 * O servidor só tem o inglês. Em singleplayer o servidor roda dentro do jogo e já usa o idioma escolhido (inclusive
 * de outros mods). No servidor dedicado, o arquivo de idioma do jogador é baixado uma vez dos servidores da Mojang
 * (o mesmo que o launcher baixa, conferido pelo SHA-1) e guardado em <mundo>/smartchest-lang/<versão>/. Enquanto não
 * chega (ou sem internet), a pesquisa usa o inglês e o id do item.
 */
final class Lang {
	private static final String MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
	private static final String OBJECTS = "https://resources.download.minecraft.net/";
	// Código vem do cliente: só letras, números e _ (nunca vira caminho fora da pasta).
	private static final Pattern CODE = Pattern.compile("[a-z0-9_]{2,16}");
	private static final Pattern MARKS = Pattern.compile("\\p{M}+");

	private static final Map<String, Map<String, String>> LOADED = new ConcurrentHashMap<>();
	private static final Map<String, CompletableFuture<Void>> LOADING = new ConcurrentHashMap<>();
	private static Path dir;

	private Lang() {}

	static void init(MinecraftServer server, Path worldDir) {
		dir = worldDir.resolve("smartchest-lang").resolve(SharedConstants.getCurrentVersion().id());
	}

	static void clear() {
		LOADED.clear();
		LOADING.clear();
		dir = null;
	}

	/** Tradutor do jogador: chave de tradução -> texto no idioma dele (ou null se não houver). */
	static Function<String, String> forPlayer(MinecraftServer server, ServerPlayer p) {
		Language base = Language.getInstance();
		Function<String, String> serverLang = k -> base.has(k) ? base.getOrDefault(k) : null;
		if (!server.isDedicatedServer() && server.isSingleplayerOwner(p.nameAndId())) return serverLang; // idioma do próprio jogo
		String code = p.clientInformation().language().toLowerCase(Locale.ROOT);
		if (code.equals("en_us") || !CODE.matcher(code).matches()) return serverLang;
		Map<String, String> map = LOADED.get(code);
		if (map == null) {
			request(code);
			return serverLang;
		}
		return k -> {
			String v = map.get(k);
			return v != null ? v : serverLang.apply(k);
		};
	}

	/** Começa a baixar o idioma em segundo plano (uma vez por idioma). */
	static CompletableFuture<Void> request(String code) {
		if (dir == null || LOADED.containsKey(code)) return CompletableFuture.completedFuture(null);
		Path folder = dir;
		return LOADING.computeIfAbsent(code, c -> CompletableFuture.runAsync(() -> {
			try {
				LOADED.put(c, load(folder, c));
			} catch (Exception e) {
				SmartChest.LOG.warn("Não foi possível obter o idioma {} para a pesquisa: {}", c, e.toString());
				LOADING.remove(c); // tenta de novo na próxima vez que alguém com esse idioma abrir o baú
			}
		}));
	}

	private static Map<String, String> load(Path folder, String code) throws Exception {
		Path file = folder.resolve(code + ".json");
		if (Files.exists(file)) return parse(Files.readAllBytes(file));
		HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL).build();
		String version = SharedConstants.getCurrentVersion().id();
		String versionUrl = null;
		for (JsonElement v : json(http, MANIFEST).getAsJsonArray("versions")) {
			if (version.equals(v.getAsJsonObject().get("id").getAsString())) versionUrl = v.getAsJsonObject().get("url").getAsString();
		}
		if (versionUrl == null) throw new IllegalStateException("versão " + version + " não está na lista da Mojang");
		String indexUrl = json(http, versionUrl).getAsJsonObject("assetIndex").get("url").getAsString();
		JsonObject entry = json(http, indexUrl).getAsJsonObject("objects").getAsJsonObject("minecraft/lang/" + code + ".json");
		if (entry == null) return Map.of(); // idioma que não existe no jogo: fica no inglês
		String hash = entry.get("hash").getAsString();
		byte[] data = bytes(http, OBJECTS + hash.substring(0, 2) + "/" + hash);
		if (!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data)).equals(hash)) {
			throw new IllegalStateException("arquivo de idioma corrompido");
		}
		Map<String, String> map = parse(data);
		Files.createDirectories(folder);
		Path tmp = folder.resolve(code + ".json.tmp");
		Files.write(tmp, data);
		Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		return map;
	}

	/** Para o teste automático: baixa (ou lê do disco) sem segundo plano. */
	static Map<String, String> loadNow(String code) throws Exception {
		Map<String, String> map = load(dir, code);
		LOADED.put(code, map);
		return map;
	}

	private static Map<String, String> parse(byte[] data) {
		Map<String, String> map = new HashMap<>();
		Language.loadFromJson(new ByteArrayInputStream(data), map::put);
		return map;
	}

	private static JsonObject json(HttpClient http, String url) throws Exception {
		return JsonParser.parseString(new String(bytes(http, url), StandardCharsets.UTF_8)).getAsJsonObject();
	}

	private static byte[] bytes(HttpClient http, String url) throws Exception {
		HttpResponse<byte[]> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).GET().build(),
			HttpResponse.BodyHandlers.ofByteArray());
		if (r.statusCode() != 200) throw new IllegalStateException("HTTP " + r.statusCode() + " em " + url);
		return r.body();
	}

	// ---------- texto ----------

	/** Texto de um Component usando o tradutor dado (o getString() do jogo usaria o idioma do servidor). */
	static String resolve(Component c, Function<String, String> tr) {
		StringBuilder sb = new StringBuilder();
		append(c, tr, sb, 0);
		return sb.toString();
	}

	private static void append(Component c, Function<String, String> tr, StringBuilder sb, int depth) {
		if (depth > 8) return; // nomes com componentes aninhados demais (feitos para travar) param aqui
		if (c.getContents() instanceof TranslatableContents t) {
			String fmt = tr.apply(t.getKey());
			if (fmt == null) fmt = t.getFallback() != null ? t.getFallback() : t.getKey();
			sb.append(format(fmt, t.getArgs(), tr, depth));
		} else {
			c.getContents().visit(s -> {
				sb.append(s);
				return Optional.empty();
			});
		}
		for (Component s : c.getSiblings()) append(s, tr, sb, depth + 1);
	}

	/** %s, %1$s e %% como no jogo. */
	static String format(String fmt, Object[] args, Function<String, String> tr, int depth) {
		StringBuilder out = new StringBuilder();
		int next = 0;
		for (int i = 0; i < fmt.length(); i++) {
			char ch = fmt.charAt(i);
			if (ch != '%' || i + 1 >= fmt.length()) {
				out.append(ch);
				continue;
			}
			if (fmt.charAt(i + 1) == '%') {
				out.append('%');
				i++;
				continue;
			}
			int j = i + 1, n = 0;
			while (j < fmt.length() && Character.isDigit(fmt.charAt(j))) n = n * 10 + (fmt.charAt(j++) - '0');
			int index;
			if (j < fmt.length() && fmt.charAt(j) == '$' && j > i + 1) {
				index = n - 1;
				j++;
			} else {
				index = next++;
				j = i + 1;
			}
			if (j >= fmt.length() || fmt.charAt(j) != 's') {
				out.append(ch);
				continue;
			}
			if (index >= 0 && index < args.length) {
				Object a = args[index];
				if (a instanceof Component comp) append(comp, tr, out, depth + 1);
				else out.append(a);
			}
			i = j;
		}
		return out.toString();
	}

	/** Para comparar: minúsculas e sem acentos ("Pá" -> "pa"). */
	static String normalize(String s) {
		return MARKS.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("").toLowerCase(Locale.ROOT);
	}

	/** O id do item vira palavras também ("diamond_pickaxe" -> "diamond pickaxe"), para achar pelo inglês/id. */
	static String idWords(String id) {
		int colon = id.indexOf(':');
		String path = colon >= 0 ? id.substring(colon + 1) : id;
		return path.replace('_', ' ').replace('/', ' ');
	}
}
