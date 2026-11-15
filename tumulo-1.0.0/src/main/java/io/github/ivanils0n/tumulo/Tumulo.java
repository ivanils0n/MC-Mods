package io.github.ivanils0n.tumulo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Túmulo: quando um jogador morre (sem keepInventory), os itens não caem no chão. Eles ficam guardados no túmulo
 * (<mundo>/tumulo.json) e o corpo do jogador fica deitado no lugar (um mannequin do jogo, com a skin e a armadura
 * dele). Clicar no corpo abre os itens; Shift+clique pega tudo. O corpo some quando fica vazio ou quando o tempo acaba.
 * Só no servidor: o corpo, a área clicável e a tela são coisas do próprio jogo, o cliente não precisa do mod.
 */
public final class Tumulo implements ModInitializer {
	static final Logger LOG = LoggerFactory.getLogger("tumulo");
	static final String TAG = "tumulo";
	static final boolean SELFTEST = Boolean.getBoolean("tumulo.selftest");

	static MinecraftServer server;
	static GraveStore store;
	static TumuloConfig config = new TumuloConfig();

	/** Mortes deste tick: o túmulo é montado no fim do tick (assim entram também itens que outros mods soltam na morte). */
	private static final List<Pending> PENDING = new ArrayList<>();
	private static final Map<UUID, Entity> BODIES = new HashMap<>();
	private static final Map<UUID, Entity> CLICKS = new HashMap<>();
	private static final List<Entity> LEFTOVERS = new ArrayList<>();
	private static final Set<UUID> CHANGED = new LinkedHashSet<>();
	/** Último lugar com chão de cada jogador (para quem cai no vazio). */
	private static final Map<UUID, Spot> GROUND = new HashMap<>();
	private static final Map<UUID, Integer> LAST_USE = new HashMap<>();

	record Pending(ServerLevel level, UUID owner, String name, Vec3 pos, float yaw, ItemStack[] items, int mainhand) {}

	record Spot(String dim, Vec3 pos) {}

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(s -> {
			server = s;
			config = TumuloConfig.load();
			store = GraveStore.load(s.getWorldPath(LevelResource.ROOT).resolve("tumulo.json"), s.registryAccess());
			if (SELFTEST) SelfTest.run(s);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
			if (store != null) store.flush();
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			store = null;
			server = null;
			PENDING.clear();
			BODIES.clear();
			CLICKS.clear();
			LEFTOVERS.clear();
			CHANGED.clear();
			GROUND.clear();
			LAST_USE.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(Tumulo::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (!entity.entityTags().contains(TAG)) return;
			UUID id = idOf(entity);
			if (store == null || id == null || store.get(id) == null) LEFTOVERS.add(entity); // túmulo que já acabou
			else register(id, entity);
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			UUID id = entity.entityTags().contains(TAG) ? idOf(entity) : null;
			if (id == null) return;
			BODIES.remove(id, entity);
			CLICKS.remove(id, entity);
		});
		UseEntityCallback.EVENT.register(Tumulo::onUse);
		// /kill no corpo: os itens caem no chão em vez de sumir.
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (store == null || !entity.entityTags().contains(TAG)) return true;
			Grave g = store.get(idOf(entity));
			for (EquipmentSlot slot : EquipmentSlot.values()) entity.setItemSlot(slot, ItemStack.EMPTY); // só visual: nada de cópias
			if (g != null && entity.level() instanceof ServerLevel sl) {
				dropAll(sl, g);
				removeGrave(sl, g);
			}
			return true;
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> {
			GROUND.remove(handler.player.getUUID());
			LAST_USE.remove(handler.player.getUUID());
		});
	}

	// ---------- morte ----------

	/** Chamado pelo mixin no Inventory.dropAll (só acontece na morte sem keepInventory). Devolve true se os itens foram guardados. */
	public static boolean capture(Inventory inv) {
		if (store == null || !(inv.player instanceof ServerPlayer sp) || !sp.isDeadOrDying()) return false;
		if (sp instanceof FakePlayer && !SELFTEST) return false; // jogadores falsos de outros mods continuam como no jogo
		ItemStack held = sp.getMainHandItem();
		ItemStack[] items = new ItemStack[Grave.SIZE];
		Arrays.fill(items, ItemStack.EMPTY);
		int main = -1;
		int size = Math.min(inv.getContainerSize(), 41);
		for (int i = 0; i < size; i++) {
			ItemStack s = inv.getItem(i);
			if (s.isEmpty()) continue;
			if (s == held && main < 0) main = Grave.slotFor(i);
			inv.setItem(i, ItemStack.EMPTY);
			items[Grave.slotFor(i)] = s;
		}
		PENDING.add(new Pending((ServerLevel) sp.level(), sp.getUUID(), sp.getScoreboardName(), sp.position(), sp.getYRot(), items, main));
		return true;
	}

	static void tick(MinecraftServer s) {
		if (store == null) return;
		processPending(s);
		for (Entity e : LEFTOVERS) e.discard();
		LEFTOVERS.clear();
		processChanges();
		if (s.getTickCount() % 10 == 0) {
			for (ServerPlayer p : s.getPlayerList().getPlayers()) {
				if (p.onGround() && !p.isSpectator() && p.isAlive()) GROUND.put(p.getUUID(), new Spot(dim(p.level()), p.position()));
			}
		}
		if (s.getTickCount() % 20 == 0) {
			second(s);
			store.flush();
		}
	}

	static void processPending(MinecraftServer s) {
		if (PENDING.isEmpty()) return;
		List<Pending> list = new ArrayList<>(PENDING);
		PENDING.clear();
		for (Pending p : list) create(s, p);
	}

	static Grave create(MinecraftServer s, Pending p) {
		ServerLevel sl = p.level();
		Vec3 at = groundBelow(sl, p.pos());
		if (at == null) {
			Spot last = GROUND.get(p.owner());
			at = last != null && last.dim().equals(dim(sl)) ? last.pos() : new Vec3(p.pos().x, Math.max(p.pos().y, sl.getMinY() + 1), p.pos().z);
		}
		long now = s.overworld().getGameTime();
		Grave g = new Grave(UUID.randomUUID(), p.owner(), p.name(), dim(sl), at.x, at.y, at.z, p.yaw(), now + config.minutos * 1200L);
		for (int i = 0; i < Grave.SIZE; i++) if (!p.items()[i].isEmpty()) g.items.setItem(i, p.items()[i]);
		g.mainhand = p.mainhand();
		// Itens que caíram junto com o jogador neste tick (ex.: a mochila equipada de outro mod) também vão para o túmulo.
		for (ItemEntity e : sl.getEntitiesOfClass(ItemEntity.class, new AABB(p.pos(), p.pos()).inflate(1.0, 1.5, 1.0), e -> e.isAlive() && e.tickCount <= 1)) {
			ItemStack rest = g.addExtra(e.getItem().copy());
			if (rest.isEmpty()) e.discard();
			else e.setItem(rest);
		}
		CHANGED.remove(g.id);
		if (g.isEmpty()) return null;
		store.add(g);
		spawnVisuals(sl, g);
		ServerPlayer owner = s.getPlayerList().getPlayer(p.owner());
		if (owner != null) {
			owner.sendSystemMessage(Component.literal(String.format(Locale.ROOT, "Seu corpo ficou em %d, %d, %d (%s) com os seus itens. Ele some em %d min.",
				(int) Math.floor(g.x), (int) Math.floor(g.y), (int) Math.floor(g.z), dimName(g.dim), config.minutos)).withStyle(ChatFormatting.GRAY));
		}
		return g;
	}

	/** Chão logo abaixo de onde o jogador morreu (blocos com colisão; água e lava não contam). null = vazio. */
	static Vec3 groundBelow(ServerLevel sl, Vec3 pos) {
		BlockPos b = BlockPos.containing(pos);
		for (int i = 0; i < 3 && sl.getBlockState(b).isCollisionShapeFullBlock(sl, b); i++) b = b.above(); // morreu dentro de um bloco
		for (; b.getY() >= sl.getMinY(); b = b.below()) {
			VoxelShape shape = sl.getBlockState(b).getCollisionShape(sl, b);
			if (!shape.isEmpty()) return new Vec3(pos.x, b.getY() + Math.min(1.0, shape.max(Direction.Axis.Y)), pos.z);
		}
		return null;
	}

	// ---------- visual ----------

	static void spawnVisuals(ServerLevel sl, Grave g) {
		killVisuals(sl, g);
		String tags = "Tags:[\"" + TAG + "\",\"" + g.tag() + "\"]";
		long hi = g.owner.getMostSignificantBits(), lo = g.owner.getLeastSignificantBits();
		String uuid = String.format(Locale.ROOT, "[I;%d,%d,%d,%d]", (int) (hi >> 32), (int) hi, (int) (lo >> 32), (int) lo);
		String name = g.name.replaceAll("[^A-Za-z0-9_]", "");
		if (name.isEmpty() || name.length() > 16) name = "Steve";
		summon(sl, "mannequin", g.x, g.y, g.z, tags + ",profile:{name:\"" + name + "\",id:" + uuid + "},pose:\"sleeping\",immovable:1b,"
			+ "hide_description:1b,Invulnerable:1b,NoGravity:1b,Silent:1b,CustomNameVisible:1b,"
			+ String.format(Locale.ROOT, "Rotation:[%.1ff,0f]", g.yaw));
		summonClick(sl, g);
		for (Entity e : find(sl, g)) register(g.id, e);
		refreshVisual(g);
	}

	/** Área clicável em volta do corpo deitado (o corpo deitado é pequeno demais para clicar). */
	private static void summonClick(ServerLevel sl, Grave g) {
		String tags = "Tags:[\"" + TAG + "\",\"" + g.tag() + "\"]";
		summon(sl, "interaction", g.x, g.y, g.z, tags + ",width:2.0f,height:0.6f,response:1b");
		for (Entity e : find(sl, g)) if (e instanceof Interaction) register(g.id, e);
	}

	private static List<Entity> find(ServerLevel sl, Grave g) {
		String tag = g.tag();
		return sl.getEntitiesOfClass(Entity.class, new AABB(g.x - 2, g.y - 2, g.z - 2, g.x + 2, g.y + 3, g.z + 2), e -> e.entityTags().contains(tag));
	}

	private static void killVisuals(ServerLevel sl, Grave g) {
		Entity b = BODIES.remove(g.id), c = CLICKS.remove(g.id);
		if (b != null) b.discard();
		if (c != null) c.discard();
		if (sl != null) for (Entity e : find(sl, g)) e.discard();
	}

	private static void register(UUID id, Entity e) {
		if (e instanceof Interaction) CLICKS.put(id, e);
		else BODIES.put(id, e);
		Grave g = store == null ? null : store.get(id);
		if (g != null && !(e instanceof Interaction)) CHANGED.add(id); // atualiza armadura e nome ao carregar
	}

	/** O corpo veste a armadura e segura os itens que ainda estão no túmulo (só visual) e mostra o tempo restante. */
	static void refreshVisual(Grave g) {
		if (!(BODIES.get(g.id) instanceof LivingEntity body) || body.isRemoved()) return;
		set(body, EquipmentSlot.HEAD, g.items.getItem(Grave.HEAD));
		set(body, EquipmentSlot.CHEST, g.items.getItem(Grave.CHEST));
		set(body, EquipmentSlot.LEGS, g.items.getItem(Grave.LEGS));
		set(body, EquipmentSlot.FEET, g.items.getItem(Grave.FEET));
		set(body, EquipmentSlot.OFFHAND, g.items.getItem(Grave.OFFHAND));
		set(body, EquipmentSlot.MAINHAND, g.mainhand >= 0 ? g.items.getItem(g.mainhand) : ItemStack.EMPTY);
		if (server != null) body.setCustomName(label(g, server.overworld().getGameTime()));
		body.setCustomNameVisible(true);
	}

	private static void set(LivingEntity body, EquipmentSlot slot, ItemStack s) {
		if (!ItemStack.matches(body.getItemBySlot(slot), s)) body.setItemSlot(slot, s.copy());
	}

	static Component label(Grave g, long now) {
		long secs = Math.max(0, g.expires - now) / 20;
		ChatFormatting color = secs <= 60 ? ChatFormatting.RED : secs <= 300 ? ChatFormatting.YELLOW : ChatFormatting.GRAY;
		return Component.literal("Túmulo de " + g.name + " ").withStyle(ChatFormatting.WHITE)
			.append(Component.literal(String.format(Locale.ROOT, "%d:%02d", secs / 60, secs % 60)).withStyle(color));
	}

	// ---------- uso ----------

	private static InteractionResult onUse(Player player, Level level, InteractionHand hand, Entity entity, EntityHitResult hit) {
		if (!entity.entityTags().contains(TAG)) return InteractionResult.PASS;
		if (store == null || !(level instanceof ServerLevel) || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
		if (hand != InteractionHand.MAIN_HAND || sp.isSpectator()) return InteractionResult.SUCCESS;
		Grave g = store.get(idOf(entity));
		if (g == null) return InteractionResult.SUCCESS;
		// O jogo pode avisar o mesmo clique duas vezes; ignora repetições em menos de 5 ticks.
		int now = server.getTickCount();
		Integer last = LAST_USE.put(sp.getUUID(), now);
		if (last != null && now - last < 5 && !SELFTEST) return InteractionResult.SUCCESS;
		if (config.somenteDono && !sp.getUUID().equals(g.owner) && !sp.isCreative()) {
			sp.sendSystemMessage(Component.literal("Só " + g.name + " pode pegar estes itens.").withStyle(ChatFormatting.RED));
			return InteractionResult.SUCCESS;
		}
		if (sp.isShiftKeyDown()) takeAll(sp, g);
		else open(sp, g);
		return InteractionResult.SUCCESS;
	}

	static void open(ServerPlayer sp, Grave g) {
		sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new GraveMenu(id, inv, g), Component.literal("Túmulo de " + g.name)));
	}

	/** Shift+clique: cada item volta para o mesmo lugar (armadura vestida, hotbar...) se estiver livre; o resto vai para qualquer espaço. */
	static void takeAll(ServerPlayer sp, Grave g) {
		Inventory inv = sp.getInventory();
		for (int i = 0; i < Grave.SIZE; i++) {
			ItemStack s = g.items.getItem(i);
			int to = Grave.invFor(i);
			if (s.isEmpty() || to < 0 || to >= inv.getContainerSize() || !inv.getItem(to).isEmpty()) continue;
			inv.setItem(to, s);
			g.items.setItem(i, ItemStack.EMPTY);
		}
		for (int i = 0; i < Grave.SIZE; i++) {
			ItemStack s = g.items.getItem(i);
			if (s.isEmpty()) continue;
			ItemStack copy = s.copy();
			inv.add(copy);
			g.items.setItem(i, copy.isEmpty() ? ItemStack.EMPTY : copy);
		}
		g.items.setChanged();
		sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6f, 0.8f);
		if (!g.isEmpty()) sp.sendSystemMessage(Component.literal("Inventário cheio: ainda há itens no corpo.").withStyle(ChatFormatting.YELLOW));
	}

	static void changed(UUID id) {
		CHANGED.add(id);
	}

	/** Itens mudaram (alguém pegou): atualiza o corpo; vazio = o túmulo acaba. */
	static void processChanges() {
		if (CHANGED.isEmpty() || store == null) return;
		List<UUID> ids = new ArrayList<>(CHANGED);
		CHANGED.clear();
		for (UUID id : ids) {
			Grave g = store.get(id);
			if (g == null) continue;
			store.markDirty();
			if (g.isEmpty()) {
				ServerLevel sl = level(g.dim);
				if (sl != null) {
					sl.sendParticles(ParticleTypes.SOUL, g.x, g.y + 0.3, g.z, 16, 0.6, 0.15, 0.6, 0.02);
					sl.playSound(null, g.x, g.y, g.z, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8f, 0.5f);
				}
				removeGrave(sl, g);
			} else {
				refreshVisual(g);
			}
		}
	}

	// ---------- tempo ----------

	/** A cada segundo: tempo no nome do corpo, túmulos vencidos e área clicável que sumiu. */
	static void second(MinecraftServer s) {
		long now = s.overworld().getGameTime();
		for (Grave g : new ArrayList<>(store.all())) {
			ServerLevel sl = level(g.dim);
			Entity body = BODIES.get(g.id);
			boolean loaded = body != null && !body.isRemoved() && sl != null;
			if (now >= g.expires) {
				if (loaded) {
					if (config.soltarItensAoSumir) dropAll(sl, g);
					sl.sendParticles(ParticleTypes.SOUL, g.x, g.y + 0.3, g.z, 16, 0.6, 0.15, 0.6, 0.02);
					removeGrave(sl, g);
				} else if (!config.soltarItensAoSumir || sl == null) {
					store.remove(g.id); // o corpo some sozinho quando a área carregar (ENTITY_LOAD)
				}
				continue;
			}
			if (!loaded) continue;
			Entity click = CLICKS.get(g.id);
			if (click == null || click.isRemoved()) summonClick(sl, g);
			if (sl.getNearestPlayer(g.x, g.y, g.z, 24, false) != null) body.setCustomName(label(g, now));
		}
	}

	static void dropAll(ServerLevel sl, Grave g) {
		for (int i = 0; i < Grave.SIZE; i++) {
			ItemStack s = g.items.removeItemNoUpdate(i);
			if (s.isEmpty()) continue;
			ItemEntity e = new ItemEntity(sl, g.x, g.y + 0.3, g.z, s);
			e.setDefaultPickUpDelay();
			sl.addFreshEntity(e);
		}
	}

	static void removeGrave(ServerLevel sl, Grave g) {
		store.remove(g.id);
		CHANGED.remove(g.id);
		killVisuals(sl, g);
	}

	// ---------- util ----------

	static UUID idOf(Entity e) {
		for (String t : e.entityTags()) {
			if (t.startsWith(TAG + "_")) {
				try {
					return UUID.fromString(t.substring(TAG.length() + 1));
				} catch (IllegalArgumentException ignored) {
				}
			}
		}
		return null;
	}

	static Entity body(UUID id) {
		return BODIES.get(id);
	}

	static Entity click(UUID id) {
		return CLICKS.get(id);
	}

	static String dim(Level level) {
		return level.dimension().identifier().toString();
	}

	static ServerLevel level(String dim) {
		if (server == null) return null;
		for (ServerLevel l : server.getAllLevels()) if (dim(l).equals(dim)) return l;
		return null;
	}

	private static String dimName(String dim) {
		return switch (dim) {
			case "minecraft:overworld" -> "Mundo Normal";
			case "minecraft:the_nether" -> "Nether";
			case "minecraft:the_end" -> "End";
			default -> dim;
		};
	}

	private static void summon(ServerLevel sl, String type, double x, double y, double z, String nbt) {
		String cmd = String.format(Locale.ROOT, "summon minecraft:%s %.4f %.4f %.4f {%s}", type, x, y, z, nbt);
		boolean[] ok = {false};
		CommandSourceStack source = server.createCommandSourceStack().withLevel(sl).withPosition(new Vec3(x, y, z))
			.withSuppressedOutput().withCallback((success, result) -> ok[0] = success);
		server.getCommands().performPrefixedCommand(source, cmd);
		if (!ok[0]) LOG.warn("Comando do túmulo falhou: {}", cmd);
	}
}
