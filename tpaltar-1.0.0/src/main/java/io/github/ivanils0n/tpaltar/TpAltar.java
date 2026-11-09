package io.github.ivanils0n.tpaltar;

import io.github.ivanils0n.tpaltar.AltarStore.Altar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Altar de Teleporte, 100% no servidor: o altar é um bloco de obsidiana de verdade (base) com entidades de exibição
 * vanilla em cima (coluna, tampo, olho do fim) e uma entidade de interação para o clique. Nenhum bloco ou item novo é
 * registrado, então o cliente vanilla entra sem mod e o mundo continua abrindo normalmente se o mod for removido.
 */
public final class TpAltar implements ModInitializer {
	static final Logger LOG = LoggerFactory.getLogger("tpaltar");
	static final int MAX_PEARLS = 64;
	static final int COST = 4; // 64 pérolas = 16 viagens
	static final String ITEM_NAME = "Altar de Teleporte";
	static final int NAME_MAX = 24;
	static final int PLACE_LEVELS = 5; // níveis de XP para nomear e colocar um altar

	/** Onde o altar vai ficar enquanto o jogador digita o nome na tela da bigorna. */
	record Pending(ResourceKey<Level> dim, BlockPos pos, InteractionHand hand) {}

	private static final Map<UUID, Integer> LAST_USE = new ConcurrentHashMap<>();
	private static MinecraftServer server;
	private static AltarStore store;

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(s -> {
			server = s;
			store = AltarStore.load(s.getWorldPath(LevelResource.ROOT).resolve("tpaltar.json"));
			LOG.info("{} altares carregados", store.size());
			if (Boolean.getBoolean("tpaltar.selftest")) SelfTest.run(s);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			server = null;
			store = null;
			LAST_USE.clear();
		});
		UseBlockCallback.EVENT.register(TpAltar::onUseBlock);
		UseEntityCallback.EVENT.register(TpAltar::onUseEntity);
		PlayerBlockBreakEvents.BEFORE.register(TpAltar::onBreak);
		ServerTickEvents.END_LEVEL_TICK.register(TpAltar::onWorldTick);
	}

	// ---------- eventos ----------

	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (store == null || !(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp) || sp.isSpectator()) {
			return InteractionResult.PASS;
		}
		Altar altar = store.get(dim(sl), hit.getBlockPos());
		if (altar != null) {
			if (hand == InteractionHand.MAIN_HAND) useAltar(sp, sl, altar);
			return InteractionResult.SUCCESS;
		}
		ItemStack stack = sp.getItemInHand(hand);
		if (!isAltarItem(stack)) return InteractionResult.PASS;
		askName(sp, sl, hit, hand, stack);
		return InteractionResult.SUCCESS; // nunca deixa a obsidiana "crua" ser colocada
	}

	private static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity entity, EntityHitResult hit) {
		if (store == null || !(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp) || !(entity instanceof Interaction)) {
			return InteractionResult.PASS;
		}
		// A entidade de interação fica um bloco acima da base de obsidiana.
		Altar altar = store.get(dim(sl), entity.blockPosition().below());
		if (altar == null) return InteractionResult.PASS;
		if (hand == InteractionHand.MAIN_HAND) useAltar(sp, sl, altar);
		return InteractionResult.SUCCESS;
	}

	private static boolean onBreak(Level level, Player player, BlockPos pos, BlockState state, BlockEntity be) {
		if (store == null || !(level instanceof ServerLevel sl)) return true;
		Altar altar = store.get(dim(sl), pos);
		if (altar == null) return true;
		ItemStack tool = player.getMainHandItem();
		boolean survival = !player.isCreative();
		boolean keep = survival && (tool.is(Items.DIAMOND_PICKAXE) || tool.is(Items.NETHERITE_PICKAXE));
		removeAltar(sl, altar);
		sl.destroyBlock(pos, false, player);
		if (survival) tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
		if (keep) {
			Block.popResource(sl, pos, altarItem());
			if (altar.pearls > 0) Block.popResource(sl, pos, new ItemStack(Items.ENDER_PEARL, altar.pearls));
			say(player, "Altar \"" + altar.name + "\" removido.", ChatFormatting.YELLOW);
		} else if (survival) {
			say(player, "O altar \"" + altar.name + "\" se despedaçou. Use uma picareta de diamante para recuperá-lo.", ChatFormatting.RED);
		}
		return false; // a quebra vanilla soltaria uma obsidiana comum
	}

	private static void onWorldTick(ServerLevel sl) {
		if (store == null || sl.getGameTime() % 20 != 0) return;
		for (Altar a : store.inDim(dim(sl))) {
			BlockPos pos = a.pos();
			if (!sl.isLoaded(pos)) continue;
			if (!sl.getBlockState(pos).is(Blocks.OBSIDIAN)) {
				// Sumiu por outro meio (Wither, comando, editor de mundo): remove sem dropar nada.
				LOG.info("Altar \"{}\" em {} não existe mais; removido", a.name, pos);
				removeAltar(sl, a);
				continue;
			}
			sl.sendParticles(ParticleTypes.PORTAL, a.x + 0.5, a.y + 2.9, a.z + 0.5, 6, 0.25, 0.3, 0.25, 0.4);
		}
	}

	// ---------- colocar ----------

	private static void askName(ServerPlayer sp, ServerLevel sl, BlockHitResult hit, InteractionHand hand, ItemStack stack) {
		BlockPos clicked = hit.getBlockPos();
		BlockPos pos = sl.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(hit.getDirection());
		if (!sp.mayUseItemAt(pos, hit.getDirection(), stack) || !sl.mayInteract(sp, pos)) return;
		if (!hasRoom(sl, pos)) {
			say(sp, "O altar precisa de 3 blocos livres de altura.", ChatFormatting.RED);
			return;
		}
		if (!hasLevels(sp)) return;
		NameMenu.open(sp, new Pending(sl.dimension(), pos.immutable(), hand), "Nome do altar");
	}

	/**
	 * Chamado quando o jogador confirma o nome na bigorna. Devolve um aviso para reabrir a tela (nome inválido)
	 * ou null quando terminou (colocou o altar ou desistiu com mensagem no chat).
	 */
	static String finishPlacement(ServerPlayer sp, Pending p, String text) {
		ServerLevel sl = server == null ? null : server.getLevel(p.dim());
		if (store == null || sp.isRemoved() || sl == null || sp.level() != sl) return null;
		String name = text.trim();
		if (name.isEmpty()) return "Digite um nome";
		if (name.length() > NAME_MAX) return "Máximo de " + NAME_MAX + " letras";
		if (store.nameTaken(dim(sl), name)) return "\"" + name + "\" já existe";
		ItemStack stack = sp.getItemInHand(p.hand());
		if (!isAltarItem(stack)) {
			say(sp, "O altar não está mais na sua mão. Colocação cancelada.", ChatFormatting.RED);
			return null;
		}
		if (sp.distanceToSqr(Vec3.atCenterOf(p.pos())) > 8 * 8) {
			say(sp, "Você se afastou demais do local. Colocação cancelada.", ChatFormatting.RED);
			return null;
		}
		if (!hasRoom(sl, p.pos()) || !sl.isUnobstructed(Blocks.OBSIDIAN.defaultBlockState(), p.pos(), CollisionContext.empty())) {
			say(sp, "O local ficou ocupado. Colocação cancelada.", ChatFormatting.RED);
			return null;
		}
		if (!hasLevels(sp)) return null;
		if (placeAltar(sl, p.pos(), name) == null) {
			say(sp, "Não foi possível colocar o altar aqui.", ChatFormatting.RED);
			return null;
		}
		if (!sp.isCreative()) {
			stack.shrink(1);
			sp.giveExperienceLevels(-PLACE_LEVELS);
		}
		say(sp, "Altar \"" + name + "\" criado! Clique nele com ender pearls para abastecer: cada viagem até aqui gasta " + COST + ".", ChatFormatting.GREEN);
		return null;
	}

	private static boolean hasLevels(ServerPlayer sp) {
		if (sp.isCreative() || sp.experienceLevel >= PLACE_LEVELS) return true;
		say(sp, "Colocar um altar custa " + PLACE_LEVELS + " níveis de experiência (você tem " + sp.experienceLevel + ").", ChatFormatting.RED);
		return false;
	}

	private static boolean hasRoom(ServerLevel sl, BlockPos pos) {
		return sl.getBlockState(pos).canBeReplaced() && sl.getBlockState(pos.above()).isAir() && sl.getBlockState(pos.above(2)).isAir();
	}

	/** Coloca a base e as entidades e registra o altar. Devolve null se o bloco não puder ser colocado. */
	static Altar placeAltar(ServerLevel sl, BlockPos pos, String name) {
		if (!sl.setBlock(pos, Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL)) return null;
		Altar altar = new Altar(dim(sl), name, pos);
		store.add(altar);
		spawnVisuals(sl, pos);
		sl.playSound(null, pos, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 1f, 1f);
		return altar;
	}

	static void removeAltar(ServerLevel sl, Altar altar) {
		store.remove(altar);
		command(sl, altar.pos(), "kill @e[tag=" + tag(altar.pos()) + ",distance=..5]");
	}

	private static void spawnVisuals(ServerLevel sl, BlockPos pos) {
		String tags = "Tags:[\"tpaltar\",\"" + tag(pos) + "\"]";
		double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;
		// Coluna de pedra lisa e tampo mais largo. São item_display (cubo centrado na entidade) porque o formato
		// de block_state do block_display mudou entre a 26.2 e a 26.3; o de item é igual nas duas.
		summon(sl, pos, "item_display", x, y + 1.5, z, tags + ",item:{id:\"minecraft:smooth_stone\",count:1}," + scale(0.5f, 1f));
		summon(sl, pos, "item_display", x, y + 2.125, z, tags + ",item:{id:\"minecraft:smooth_stone\",count:1}," + scale(0.8f, 0.25f));
		// Olho do fim flutuando, virando para quem olha e sempre iluminado.
		summon(sl, pos, "item_display", x, y + 2.9, z, tags + ",item:{id:\"minecraft:ender_eye\",count:1},"
			+ "billboard:\"vertical\",brightness:{sky:15,block:15}," + scale(0.7f, 0.7f));
		// Área clicável que cobre a coluna e o olho.
		summon(sl, pos, "interaction", x, y + 1, z, tags + ",width:0.9f,height:2.3f,response:1b");
	}

	private static String scale(float side, float height) {
		return String.format(Locale.ROOT, "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],"
			+ "translation:[0f,0f,0f],scale:[%sf,%sf,%sf]}", side, height, side);
	}

	private static void summon(ServerLevel sl, BlockPos pos, String type, double x, double y, double z, String nbt) {
		command(sl, pos, String.format(Locale.ROOT, "summon minecraft:%s %.4f %.4f %.4f {%s}", type, x, y, z, nbt));
	}

	private static void command(ServerLevel sl, BlockPos pos, String cmd) {
		CommandSourceStack source = server.createCommandSourceStack().withLevel(sl).withPosition(Vec3.atCenterOf(pos)).withSuppressedOutput();
		server.getCommands().performPrefixedCommand(source, cmd);
	}

	private static String tag(BlockPos pos) {
		return "tpaltar_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
	}

	// ---------- usar ----------

	private static void useAltar(ServerPlayer sp, ServerLevel sl, Altar altar) {
		// O jogo pode avisar o mesmo clique duas vezes (bloco/entidade); ignora repetições em menos de 5 ticks.
		int now = server.getTickCount();
		Integer last = LAST_USE.put(sp.getUUID(), now);
		if (last != null && now - last < 5) return;

		ItemStack pearls = sp.getMainHandItem().is(Items.ENDER_PEARL) ? sp.getMainHandItem()
			: sp.getOffhandItem().is(Items.ENDER_PEARL) ? sp.getOffhandItem() : ItemStack.EMPTY;
		if (pearls.isEmpty()) {
			List<Altar> others = store.inDim(dim(sl));
			others.remove(altar);
			AltarMenu.open(sp, altar, others);
			return;
		}
		int add = Math.min(pearls.getCount(), MAX_PEARLS - altar.pearls);
		if (add <= 0) {
			say(sp, "O altar \"" + altar.name + "\" já está cheio (" + MAX_PEARLS + "/" + MAX_PEARLS + ").", ChatFormatting.YELLOW);
			return;
		}
		altar.pearls += add;
		store.save();
		if (!sp.isCreative()) pearls.shrink(add);
		sl.playSound(null, altar.pos(), SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 0.7f, 1.3f);
		say(sp, "Altar \"" + altar.name + "\": " + altar.pearls + "/" + MAX_PEARLS + " pérolas (" + altar.pearls / COST + " viagens).", ChatFormatting.LIGHT_PURPLE);
	}

	static void teleport(ServerPlayer sp, Altar from, Altar to) {
		sp.closeContainer();
		if (store == null || !(sp.level() instanceof ServerLevel sl)) return;
		String dim = dim(sl);
		Altar src = store.get(dim, from.pos());
		Altar dst = store.get(dim, to.pos());
		if (src == null || sp.distanceToSqr(Vec3.atCenterOf(src.pos())) > 8 * 8) {
			say(sp, "Você precisa estar perto do altar para viajar.", ChatFormatting.RED);
			return;
		}
		if (dst == null || !sl.getBlockState(dst.pos()).is(Blocks.OBSIDIAN)) {
			if (dst != null) removeAltar(sl, dst);
			say(sp, "Esse altar não existe mais.", ChatFormatting.RED);
			return;
		}
		if (dst.pearls < COST) {
			say(sp, "O altar \"" + dst.name + "\" não tem ender pearls suficientes (" + dst.pearls + "/" + COST + ").", ChatFormatting.RED);
			return;
		}
		Vec3 spot = safeSpot(sl, dst.pos());
		if (spot == null) {
			say(sp, "O altar \"" + dst.name + "\" está cercado de blocos; não há onde chegar.", ChatFormatting.RED);
			return;
		}
		dst.pearls -= COST;
		store.save();
		sl.playSound(null, sp.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 1f);
		sp.stopRiding();
		sp.teleportTo(spot.x, spot.y, spot.z);
		sp.resetFallDistance();
		sl.playSound(null, dst.pos(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 1f);
		say(sp, "Bem-vindo a \"" + dst.name + "\". Restam " + dst.pearls / COST + " viagens para cá.", ChatFormatting.LIGHT_PURPLE);
	}

	/** Um bloco ao lado do altar com chão firme e 2 de altura livres, sem líquido. */
	static Vec3 safeSpot(ServerLevel sl, BlockPos base) {
		for (int dy : new int[] {0, 1, -1}) {
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos feet = base.relative(d).above(dy);
				BlockPos floor = feet.below();
				if (free(sl, feet) && free(sl, feet.above()) && !sl.getBlockState(floor).getCollisionShape(sl, floor).isEmpty()) {
					return new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
				}
			}
		}
		return null;
	}

	private static boolean free(ServerLevel sl, BlockPos pos) {
		return sl.getBlockState(pos).getCollisionShape(sl, pos).isEmpty() && sl.getFluidState(pos).isEmpty();
	}

	// ---------- utilidades ----------

	static String dim(Level level) {
		return level.dimension().identifier().toString();
	}

	static boolean isAltarItem(ItemStack stack) {
		if (!stack.is(Items.OBSIDIAN)) return false;
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().contains("tpaltar");
	}

	/** O mesmo item da receita (data/tpaltar/recipe/altar.json), para devolver ao quebrar. */
	static ItemStack altarItem() {
		ItemStack s = new ItemStack(Items.OBSIDIAN);
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("tpaltar", true);
		s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		s.set(DataComponents.CUSTOM_NAME, plain(ITEM_NAME, ChatFormatting.LIGHT_PURPLE));
		s.set(DataComponents.ITEM_MODEL, Items.ENDER_EYE.components().get(DataComponents.ITEM_MODEL));
		s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return s;
	}

	static MutableComponent plain(String text, ChatFormatting color) {
		return Component.literal(text).withStyle(st -> st.withItalic(false).withColor(color));
	}

	static void say(Player player, String text, ChatFormatting color) {
		if (player instanceof ServerPlayer sp) sp.sendSystemMessage(Component.literal(text).withStyle(color));
	}
}
