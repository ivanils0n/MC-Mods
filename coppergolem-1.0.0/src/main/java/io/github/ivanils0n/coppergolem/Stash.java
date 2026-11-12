package io.github.ivanils0n.coppergolem;

import io.github.ivanils0n.coppergolem.Links.Link;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;

/**
 * Golem guardado: shift + botão direito com o cajado recolhe o golem num item (ovo do golem de cobre marcado) com a
 * ferrugem, o nome e os baús vinculados. Botão direito num bloco com o item solta o mesmo golem (mesmo UUID, o cajado continua ligado).
 */
final class Stash {
	static final String TAG = "coppergolem_stash";

	private Stash() {}

	static boolean isStash(ItemStack s) {
		CustomData d = s.get(DataComponents.CUSTOM_DATA);
		return d != null && d.copyTag().contains(TAG);
	}

	/** Recolhe o golem: o item vai para o inventário (ou cai no chão), o que ele segurava cai no chão. Devolve uma cópia do item. */
	static ItemStack collect(ServerPlayer sp, CopperGolem g) {
		ServerLevel sl = (ServerLevel) g.level();
		CompoundTag t = new CompoundTag();
		t.putString("uuid", g.getUUID().toString());
		t.putInt("weather", g.getWeatherState().ordinal());
		Link l = GolemMod.links == null ? null : GolemMod.links.get(g.getUUID());
		if (l != null && !l.isEmpty()) t.putString("link", Links.toJson(l));
		CompoundTag root = new CompoundTag();
		root.put(TAG, t);
		Item egg = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", "copper_golem_spawn_egg"));
		ItemStack s = new ItemStack(egg == Items.AIR ? Items.PAPER : egg);
		s.set(DataComponents.MAX_STACK_SIZE, 1);
		Component name = g.getCustomName();
		s.set(DataComponents.CUSTOM_NAME, name != null ? name.copy().withStyle(st -> st.withItalic(false)) : GolemMod.plain("Golem de Cobre guardado", ChatFormatting.GOLD));
		if (name != null) t.putString("name", name.getString());
		s.set(DataComponents.LORE, new ItemLore(List.of(
			GolemMod.plain("Botão direito no chão para soltar.", ChatFormatting.GRAY),
			GolemMod.plain(l == null || l.isEmpty() ? "Sem baús vinculados." : "Baús vinculados: " + l.total(), ChatFormatting.DARK_GRAY))));
		s.set(DataComponents.CUSTOM_DATA, CustomData.of(root));

		ItemStack held = g.getItemBySlot(EquipmentSlot.MAINHAND);
		if (!held.isEmpty()) g.spawnAtLocation(sl, held.copy());
		g.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		sl.sendParticles(ParticleTypes.WAX_OFF, g.getX(), g.getY() + 0.6, g.getZ(), 15, 0.3, 0.4, 0.3, 0.05);
		sl.playSound(null, g.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1f, 0.8f);
		GolemTask task = GolemMod.TASKS.remove(g.getUUID());
		if (task != null) task.release(sl, g);
		g.discard(); // o vínculo some com ele (ENTITY_UNLOAD) e volta quando for solto
		if (GolemMod.links != null) GolemMod.links.remove(g.getUUID());
		ItemStack copy = s.copy(); // add() esvazia a pilha que entrou no inventário
		if (!sp.getInventory().add(s)) sp.drop(s, false);
		return copy;
	}

	/** Solta o golem guardado no bloco ao lado do clicado. Devolve o golem, ou null se não deu. */
	static CopperGolem place(ServerPlayer sp, ServerLevel sl, ItemStack s, BlockPos at) {
		CompoundTag t = s.get(DataComponents.CUSTOM_DATA).copyTag().getCompoundOrEmpty(TAG);
		@SuppressWarnings("unchecked")
		EntityType<CopperGolem> type = (EntityType<CopperGolem>) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath("minecraft", "copper_golem"));
		CopperGolem g = type.create(sl, EntitySpawnReason.SPAWN_ITEM_USE);
		if (g == null) return null;
		UUID id = null;
		try {
			id = UUID.fromString(t.getStringOr("uuid", ""));
		} catch (IllegalArgumentException ignored) {
		}
		boolean same = id != null && !exists(sl, id); // cópia (criativo) de um golem que ainda existe: golem novo, sem vínculos
		if (same) g.setUUID(id);
		g.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, sp.getYRot() + 180f, 0f);
		g.setWeatherState(WeatherState.values()[Math.clamp(t.getIntOr("weather", 0), 0, 3)]);
		t.getString("name").ifPresent(n -> g.setCustomName(Component.literal(n)));
		if (!sl.addFreshEntity(g)) return null;
		String link = t.getStringOr("link", "");
		if (same && !link.isEmpty() && GolemMod.links != null) {
			GolemMod.links.put(g.getUUID(), link);
			Link l = GolemMod.links.get(g.getUUID());
			if (l != null && !GolemMod.dim(sl).equals(l.dim)) {
				GolemMod.say(sp, GolemMod.plain("Os baús deste golem ficaram em outra dimensão: ele está sem vínculos aqui.", ChatFormatting.YELLOW));
			}
		}
		sl.playSound(null, at, SoundEvents.COPPER_PLACE, SoundSource.PLAYERS, 1f, 1f);
		s.consume(1, sp);
		return g;
	}

	private static boolean exists(ServerLevel sl, UUID id) {
		for (ServerLevel l : sl.getServer().getAllLevels()) if (l.getEntity(id) != null) return true;
		return false;
	}
}
