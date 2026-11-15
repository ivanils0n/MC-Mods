package io.github.ivanils0n.sacoender;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Saco do Ender: uma trouxa do jogo marcada (custom_data "sacoender") e sem espaço próprio (sem bundle_contents, então
 * o jogo não deixa pôr itens nela nem a esvazia) que, com o botão direito, abre os 9 últimos espaços do baú do fim de
 * quem o usa. O mod não guarda nada: a tela mexe direto no baú do fim do jogador, que o próprio jogo salva. Sem o mod,
 * o saco vira uma trouxa chamada "Saco do Ender" que não guarda nada, e os itens continuam no baú do fim.
 */
public final class SacoEnder implements ModInitializer {
	static final Logger LOG = LoggerFactory.getLogger("sacoender");
	static final String NAME = "Saco do Ender";
	static final String LORE = "Espaço compartilhado com o baú do Ender";
	static final String TAG = "sacoender";
	static final TagKey<Item> BUNDLES = TagKey.create(Registries.ITEM, Identifier.withDefaultNamespace("bundles"));

	private static final String PACK_URL = "https://github.com/ivanils0n/MC-Mods/releases/download/sacoender-" + modVersion() + "/sacoender-pack-";
	static final String PACK_SHA1 = readPackHash();
	private static final UUID PACK_ID = UUID.nameUUIDFromBytes("sacoender:pack".getBytes(StandardCharsets.UTF_8));
	private static final Set<UUID> PACK_OK = ConcurrentHashMap.newKeySet();
	/** Caractere da fonte sacoender:gui com os 9 espaços do saco (a última fileira) (arte/gerar.py); \uF000/\uF001/\uF002 voltam o cursor. */
	static final String ENDER_ROW = "\uF000\uE000\uF001\uF002";

	@Override
	public void onInitialize() {
		UseItemCallback.EVENT.register(SacoEnder::onUse);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> {
			if (PACK_SHA1 == null || !s.isDedicatedServer()) return; // singleplayer: as texturas vêm do .jar
			sender.sendPacket(new ClientboundResourcePackPushPacket(PACK_ID, PACK_URL + PACK_SHA1 + ".zip", PACK_SHA1, false,
				Optional.of(Component.literal("Texturas do Saco do Ender"))));
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> PACK_OK.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> PACK_OK.clear());
		ServerLifecycleEvents.SERVER_STARTED.register(s -> {
			if (Boolean.getBoolean("sacoender.selftest")) SelfTest.run(s);
		});
	}

	/** Botão direito com o saco (na mão principal ou secundária): abre a tela. */
	static InteractionResult onUse(Player player, Level level, InteractionHand hand) {
		if (!isBag(player.getItemInHand(hand))) return InteractionResult.PASS;
		if (player instanceof ServerPlayer sp && !sp.isSpectator()) open(sp);
		return InteractionResult.SUCCESS;
	}

	public static void open(ServerPlayer player) {
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new BagMenu(id, inv, player), Component.literal(NAME)));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUNDLE_REMOVE_ONE, SoundSource.PLAYERS, 1.0f, 0.9f);
	}

	/** Qualquer trouxa (a tingida continua sendo o saco) com a marca do mod. */
	public static boolean isBag(ItemStack stack) {
		if (!stack.is(BUNDLES)) return false;
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().contains(TAG);
	}

	/** O mesmo item da receita (data/sacoender/recipe/saco.json). Só componentes do jogo: nada some sem o mod. */
	public static ItemStack bagItem() {
		ItemStack s = new ItemStack(Items.BUNDLE);
		s.remove(DataComponents.BUNDLE_CONTENTS);
		s.set(DataComponents.ITEM_NAME, Component.literal(NAME).withStyle(ChatFormatting.LIGHT_PURPLE));
		s.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(LORE).withStyle(st -> st.withItalic(false).withColor(ChatFormatting.GRAY)))));
		s.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(TAG), List.of()));
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(TAG, true);
		s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return s;
	}

	// ---------- resource pack ----------

	/** Chamado pelo mixin quando o jogador responde ao pedido do resource pack. */
	public static void packStatus(ServerPlayer p, UUID id, ServerboundResourcePackPacket.Action action) {
		if (!PACK_ID.equals(id)) return;
		if (action == ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED) PACK_OK.add(p.getUUID());
		else if (action != ServerboundResourcePackPacket.Action.ACCEPTED && action != ServerboundResourcePackPacket.Action.DOWNLOADED) PACK_OK.remove(p.getUUID());
	}

	/** Singleplayer: as texturas vêm do .jar do dono do mundo. Servidor: só de quem carregou o pack. */
	public static boolean hasPack(ServerPlayer p) {
		var server = p.level().getServer();
		return server.isDedicatedServer() ? PACK_OK.contains(p.getUUID()) : server.isSingleplayerOwner(p.nameAndId());
	}

	/**
	 * Título do baú do Ender com os 9 espaços do saco desenhados (só para quem tem o pack): antes do título vai um
	 * caractere da fonte sacoender:gui do tamanho do topo da tela, em branco para não ser escurecido pela cor do título.
	 * A textura do baú do jogo não muda, e sem o pack o título fica igual ao do jogo.
	 */
	public static Component enderTitle(Component title, Player player) {
		if (!(player instanceof ServerPlayer sp) || !hasPack(sp)) return title;
		JsonObject bg = new JsonObject();
		bg.addProperty("text", ENDER_ROW);
		bg.addProperty("font", "sacoender:gui");
		bg.addProperty("color", "white");
		return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, bg).result()
			.<Component>map(c -> Component.empty().append(c).append(title)).orElse(title);
	}

	/** Versão do mod sem o sufixo "+mc..." (a do release no GitHub, ex.: 1.0.0). */
	private static String modVersion() {
		String v = FabricLoader.getInstance().getModContainer("sacoender").orElseThrow().getMetadata().getVersion().getFriendlyString();
		int i = v.indexOf('+');
		return i < 0 ? v : v.substring(0, i);
	}

	private static String readPackHash() {
		try (InputStream in = SacoEnder.class.getResourceAsStream("/sacoender/pack.sha1")) {
			if (in == null) return null;
			String sha = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
			return sha.matches("[0-9a-f]{40}") ? sha : null;
		} catch (IOException e) {
			return null;
		}
	}
}
