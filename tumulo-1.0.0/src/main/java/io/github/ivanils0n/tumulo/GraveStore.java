package io.github.ivanils0n.tumulo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;

/** Os túmulos em <mundo>/tumulo.json (fora do save do Minecraft), gravado de forma atômica sempre que muda. */
final class GraveStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private final Path file;
	private final DynamicOps<JsonElement> ops;
	private final Map<UUID, Grave> graves = new LinkedHashMap<>();
	private boolean dirty;

	private GraveStore(Path file, HolderLookup.Provider registries) {
		this.file = file;
		this.ops = registries.createSerializationContext(JsonOps.INSTANCE);
	}

	static GraveStore load(Path file, HolderLookup.Provider registries) {
		GraveStore s = new GraveStore(file, registries);
		if (!Files.exists(file)) return s;
		try {
			JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			for (JsonElement el : root.getAsJsonArray("graves")) {
				JsonObject o = el.getAsJsonObject();
				Grave g = new Grave(UUID.fromString(o.get("id").getAsString()), UUID.fromString(o.get("owner").getAsString()),
					o.get("name").getAsString(), o.get("dim").getAsString(), o.get("x").getAsDouble(), o.get("y").getAsDouble(),
					o.get("z").getAsDouble(), o.get("yaw").getAsFloat(), o.get("expires").getAsLong());
				g.mainhand = o.has("mainhand") ? o.get("mainhand").getAsInt() : -1;
				for (JsonElement it : o.getAsJsonArray("items")) {
					JsonObject io = it.getAsJsonObject();
					int slot = io.get("slot").getAsInt();
					var r = ItemStack.CODEC.parse(s.ops, io.get("item"));
					if (r.result().isPresent() && slot >= 0 && slot < Grave.SIZE) g.items.setItem(slot, r.result().get());
					else Tumulo.LOG.warn("Item do túmulo de {} não pôde ser lido e foi ignorado: {}", g.name, io.get("item"));
				}
				s.graves.put(g.id, g);
			}
		} catch (Exception e) {
			// Não apaga nada: guarda uma cópia do arquivo com problema para não perder os itens.
			Tumulo.LOG.error("Não foi possível ler {}; uma cópia foi guardada como tumulo.json.bad", file, e);
			try {
				Files.copy(file, file.resolveSibling("tumulo.json.bad"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
			}
		}
		return s;
	}

	Grave get(UUID id) {
		return graves.get(id);
	}

	Collection<Grave> all() {
		return graves.values();
	}

	void add(Grave g) {
		graves.put(g.id, g);
		save();
	}

	void remove(UUID id) {
		if (graves.remove(id) != null) save();
	}

	void markDirty() {
		dirty = true;
	}

	void flush() {
		if (dirty) save();
	}

	void save() {
		dirty = false;
		JsonArray arr = new JsonArray();
		for (Grave g : graves.values()) {
			JsonObject o = new JsonObject();
			o.addProperty("id", g.id.toString());
			o.addProperty("owner", g.owner.toString());
			o.addProperty("name", g.name);
			o.addProperty("dim", g.dim);
			o.addProperty("x", g.x);
			o.addProperty("y", g.y);
			o.addProperty("z", g.z);
			o.addProperty("yaw", g.yaw);
			o.addProperty("expires", g.expires);
			o.addProperty("mainhand", g.mainhand);
			JsonArray items = new JsonArray();
			for (int i = 0; i < Grave.SIZE; i++) {
				ItemStack st = g.items.getItem(i);
				if (st.isEmpty()) continue;
				var r = ItemStack.CODEC.encodeStart(ops, st);
				if (r.result().isEmpty()) {
					Tumulo.LOG.warn("Item do túmulo de {} não pôde ser gravado: {}", g.name, st);
					continue;
				}
				JsonObject io = new JsonObject();
				io.addProperty("slot", i);
				io.add("item", r.result().get());
				items.add(io);
			}
			o.add("items", items);
			arr.add(o);
		}
		JsonObject root = new JsonObject();
		root.add("graves", arr);
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling("tumulo.json.tmp");
			Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
			try {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			dirty = true;
			Tumulo.LOG.error("Não foi possível gravar {}", file, e);
		}
	}
}
