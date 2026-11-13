package io.github.ivanils0n.mochilas.backpack;

import io.github.ivanils0n.mochilas.Mochilas;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/** Registro dos itens e leitura/escrita da identificação de cada mochila (custom_data "mochilas_id"). */
public final class Backpacks {
	public static final String ID_KEY = "mochilas_id";
	private static final Map<Tier, BackpackItem> ITEMS = new EnumMap<>(Tier.class);

	private Backpacks() {}

	public static void register() {
		for (Tier t : Tier.values()) {
			Identifier id = Mochilas.id(t.itemPath());
			Item.Properties props = new Item.Properties()
					.setId(ResourceKey.create(Registries.ITEM, id))
					.stacksTo(1)
					.component(DataComponents.ITEM_NAME, Component.literal("Mochila de " + t.nome).withStyle(t.cor));
			ITEMS.put(t, Registry.register(BuiltInRegistries.ITEM, id, new BackpackItem(props, t)));
		}
	}

	public static BackpackItem item(Tier t) {
		return ITEMS.get(t);
	}

	public static boolean isBackpack(ItemStack stack) {
		return stack.getItem() instanceof BackpackItem;
	}

	public static Tier tier(ItemStack stack) {
		return stack.getItem() instanceof BackpackItem b ? b.tier : null;
	}

	/** Mochila nova (sem identificação ainda: ela ganha uma na primeira vez que for equipada ou aberta). */
	public static ItemStack create(Tier t) {
		return new ItemStack(item(t));
	}

	public static UUID getId(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) return null;
		String s = data.copyTag().getStringOr(ID_KEY, "");
		try {
			return s.isEmpty() ? null : UUID.fromString(s);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	public static UUID ensureId(ItemStack stack) {
		UUID id = getId(stack);
		if (id != null) return id;
		id = UUID.randomUUID();
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
		tag.putString(ID_KEY, id.toString());
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return id;
	}

	/**
	 * Item que a entidade virtual mostra nas costas: papel com o modelo 3D do nível. Os outros jogadores veem
	 * mochila_*_costas (completa); o dono vê mochila_*_dono, sem as alças da frente, para não tapar a 1ª pessoa.
	 */
	public static ItemStack visualStack(Tier t, boolean owner) {
		ItemStack s = new ItemStack(Items.PAPER);
		s.set(DataComponents.ITEM_MODEL, Mochilas.id(t.itemPath() + (owner ? "_dono" : "_costas")));
		return s;
	}

	/** Nome mostrado na tela: o nome dado na bigorna, se houver. */
	public static Component displayName(ItemStack stack) {
		Component custom = stack.get(DataComponents.CUSTOM_NAME);
		Tier t = tier(stack);
		return custom != null ? custom : Component.literal("Mochila de " + (t == null ? "?" : t.nome));
	}
}
