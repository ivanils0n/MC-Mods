package io.github.ivanils0n.mochilas.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.ivanils0n.mochilas.Mochilas;
import io.github.ivanils0n.mochilas.backpack.Backpacks;
import io.github.ivanils0n.mochilas.backpack.Tier;
import io.github.ivanils0n.mochilas.config.MochilasConfig;
import io.github.ivanils0n.mochilas.equipment.Equipment;
import io.github.ivanils0n.mochilas.gui.BackpackGui;
import io.github.ivanils0n.mochilas.visual.Visuals;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * /backpack            abre a mochila equipada
 * /backpack unequip    tira a mochila das costas (volta para o inventário)
 * /backpack info       nível, espaços usados e ID
 * /backpack reload     recarrega config/mochilas.json (op 2)
 * /backpack give &lt;jogador&gt; &lt;nível&gt;   dá uma mochila (op 2)
 */
public final class BackpackCommand {
	private BackpackCommand() {}

	private static Predicate<CommandSourceStack> op() {
		return src -> src.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(2)));
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> d) {
		for (String name : new String[]{"backpack", "mochila"}) {
			d.register(Commands.literal(name)
					.executes(BackpackCommand::open)
					.then(Commands.literal("unequip").executes(BackpackCommand::unequip))
					.then(Commands.literal("info").executes(BackpackCommand::info))
					.then(Commands.literal("reload").requires(op()).executes(BackpackCommand::reload))
					.then(Commands.literal("give").requires(op())
							.then(Commands.argument("player", EntityArgument.player())
									.then(Commands.argument("tier", StringArgumentType.word())
											.suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Tier.values()).map(t -> t.id), b))
											.executes(BackpackCommand::give)))));
		}
	}

	private static int open(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return BackpackGui.openEquipped(ctx.getSource().getPlayerOrException()) ? 1 : 0;
	}

	private static int unequip(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = ctx.getSource().getPlayerOrException();
		if (!Equipment.unequip(p)) {
			ctx.getSource().sendFailure(Component.literal("Você não está com uma mochila equipada."));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.literal("Mochila retirada; os itens continuam guardados nela."), false);
		return 1;
	}

	private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = ctx.getSource().getPlayerOrException();
		ItemStack s = Equipment.equipped(p);
		Tier t = Backpacks.tier(s);
		if (t == null) {
			ctx.getSource().sendFailure(Component.literal("Você não está com uma mochila equipada."));
			return 0;
		}
		UUID id = Backpacks.ensureId(s);
		int used = Mochilas.storage().used(id, t.capacidade());
		ctx.getSource().sendSuccess(() -> Component.empty()
				.append(Backpacks.displayName(s))
				.append(Component.literal(" | nível " + t.nome + " | " + used + "/" + t.capacidade() + " espaços usados").withStyle(ChatFormatting.GRAY))
				.append(Component.literal(t.proximo() == null ? " | nível máximo" : " | melhora para " + t.proximo().nome).withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.literal("\nID: " + id).withStyle(ChatFormatting.DARK_GRAY)), false);
		return 1;
	}

	private static int reload(CommandContext<CommandSourceStack> ctx) {
		MochilasConfig cfg = MochilasConfig.load();
		for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers()) {
			BackpackGui.closeFor(p);
			Visuals.refresh(p);
		}
		StringBuilder sb = new StringBuilder("Config recarregado. Capacidades:");
		for (Tier t : Tier.values()) sb.append(' ').append(t.nome).append('=').append(cfg.capacidade(t));
		ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), true);
		return 1;
	}

	private static int give(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
		Tier t = Tier.byId(StringArgumentType.getString(ctx, "tier"));
		if (t == null) {
			ctx.getSource().sendFailure(Component.literal("Nível inválido. Use: couro, ferro, ouro ou diamante."));
			return 0;
		}
		target.getInventory().placeItemBackInInventory(Backpacks.create(t));
		ctx.getSource().sendSuccess(() -> Component.literal("Mochila de " + t.nome + " entregue a " + target.getName().getString() + "."), true);
		return 1;
	}
}
