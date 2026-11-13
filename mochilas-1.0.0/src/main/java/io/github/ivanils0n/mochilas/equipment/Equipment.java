package io.github.ivanils0n.mochilas.equipment;

import io.github.ivanils0n.mochilas.Mochilas;
import io.github.ivanils0n.mochilas.backpack.Backpacks;
import io.github.ivanils0n.mochilas.config.MochilasConfig;
import io.github.ivanils0n.mochilas.gui.BackpackGui;
import io.github.ivanils0n.mochilas.visual.Visuals;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Mochila equipada: fica guardada no próprio jogador (anexo do Fabric salvo junto com os dados do jogador),
 * fora do inventário e sem ocupar a armadura de peito.
 */
public final class Equipment {
	public static final AttachmentType<ItemStack> EQUIPPED = AttachmentRegistry.create(Mochilas.id("equipada"),
			b -> b.persistent(ItemStack.CODEC));

	private Equipment() {}

	public static void init() {
		// carrega a classe (registra o anexo)
	}

	public static ItemStack equipped(ServerPlayer p) {
		ItemStack s = p.getAttached(EQUIPPED);
		return s == null ? ItemStack.EMPTY : s;
	}

	public static boolean hasEquipped(ServerPlayer p) {
		return !equipped(p).isEmpty();
	}

	/** Botão direito com a mochila na mão. Se já houver uma equipada, as duas trocam de lugar. */
	public static void equipFromHand(ServerPlayer p, InteractionHand hand) {
		ItemStack inHand = p.getItemInHand(hand);
		if (!Backpacks.isBackpack(inHand)) return;
		ItemStack newOne = inHand.split(1);
		Backpacks.ensureId(newOne);
		ItemStack old = equipped(p);
		if (!old.isEmpty()) {
			BackpackGui.closeFor(p);
			if (inHand.isEmpty()) p.setItemInHand(hand, old);
			else p.getInventory().placeItemBackInInventory(old);
		}
		p.setAttached(EQUIPPED, newOne);
		Visuals.refresh(p);
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1f, 1f);
		p.sendSystemMessage(Component.literal("Mochila equipada. Use /backpack para abrir.").withStyle(ChatFormatting.GRAY));
	}

	/** Coloca direto nas costas (comando de teste e autoteste). Devolve a que estava equipada, se houver. */
	public static ItemStack equip(ServerPlayer p, ItemStack stack) {
		Backpacks.ensureId(stack);
		ItemStack old = equipped(p);
		if (!old.isEmpty()) BackpackGui.closeFor(p);
		p.setAttached(EQUIPPED, stack);
		Visuals.refresh(p);
		return old;
	}

	/** /backpack unequip: devolve a mochila (com o mesmo ID, então os itens continuam nela). */
	public static boolean unequip(ServerPlayer p) {
		ItemStack old = equipped(p);
		if (old.isEmpty()) return false;
		BackpackGui.closeFor(p);
		p.removeAttached(EQUIPPED);
		Visuals.refresh(p);
		p.getInventory().placeItemBackInInventory(old);
		return true;
	}

	/** Morte: sem keepInventory a mochila cai no chão (config soltar_ao_morrer). */
	public static void onDeath(ServerPlayer p) {
		ItemStack s = equipped(p);
		if (s.isEmpty()) return;
		BackpackGui.closeFor(p);
		boolean keep = p.level().getGameRules().get(GameRules.KEEP_INVENTORY);
		if (keep || !MochilasConfig.get().soltarAoMorrer) return; // fica para o jogador renascido (copyFrom)
		p.removeAttached(EQUIPPED);
		Visuals.remove(p);
		ItemEntity e = new ItemEntity(p.level(), p.getX(), p.getY() + 0.5, p.getZ(), s);
		e.setDefaultPickUpDelay();
		p.level().addFreshEntity(e);
	}

	/** Renascer / voltar do End: o jogador novo herda a mochila se ela não caiu. */
	public static void copyFrom(ServerPlayer oldP, ServerPlayer newP) {
		ItemStack s = equipped(oldP);
		if (!s.isEmpty() && !hasEquipped(newP)) newP.setAttached(EQUIPPED, s);
	}
}
