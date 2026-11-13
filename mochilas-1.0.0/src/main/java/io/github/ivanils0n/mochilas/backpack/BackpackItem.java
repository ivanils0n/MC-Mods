package io.github.ivanils0n.mochilas.backpack;

import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import io.github.ivanils0n.mochilas.equipment.Equipment;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Item da mochila. Para o cliente sem mod ele aparece como papel com o modelo do resource pack (o Polymer troca).
 * Botão direito com ela na mão: equipa (se já houver uma equipada, as duas trocam de lugar).
 */
public class BackpackItem extends SimplePolymerItem {
	public final Tier tier;

	public BackpackItem(Properties settings, Tier tier) {
		super(settings, Items.PAPER, true);
		this.tier = tier;
	}

	@Override
	public InteractionResult use(Level level, Player user, InteractionHand hand) {
		if (user instanceof ServerPlayer player) {
			Equipment.equipFromHand(player, hand);
		}
		return InteractionResult.SUCCESS_SERVER;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay tooltipDisplay, Consumer<Component> tooltipAdder, TooltipFlag flag) {
		tooltipAdder.accept(Component.literal("Nível: " + tier.nome + " (" + tier.capacidade() + " espaços)").withStyle(ChatFormatting.GRAY));
		tooltipAdder.accept(Component.literal("Botão direito: equipar nas costas").withStyle(ChatFormatting.DARK_GRAY));
		tooltipAdder.accept(Component.literal("/backpack: abrir").withStyle(ChatFormatting.DARK_GRAY));
		UUID id = Backpacks.getId(stack);
		if (id != null && flag.isAdvanced()) {
			tooltipAdder.accept(Component.literal("ID: " + id).withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}
