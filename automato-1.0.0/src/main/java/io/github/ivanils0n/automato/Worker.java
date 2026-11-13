package io.github.ivanils0n.automato;

import io.github.ivanils0n.automato.MachineStore.Machine;
import io.github.ivanils0n.automato.mixin.AttackTickerAccess;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * O trabalho do Autômato a cada tick, como um jogador parado na frente dele segurando a ferramenta:
 * picareta, pá, enxada e tesoura quebram o primeiro bloco à frente (até o alcance escolhido na tela, de 1 a 5 blocos),
 * na velocidade de um jogador com essa ferramenta; espada, maça, tridente e lança golpeiam o mob à frente (até 3
 * blocos) cada vez que a recarga da arma enche; o machado golpeia se houver mob e, se não, corta o bloco à frente.
 * A quebra e o golpe são feitos por um jogador falso, então valem encantamentos, gasto de durabilidade e drops de
 * jogador. Usa a primeira ferramenta dos 9 espaços; quando ela quebra, passa para a próxima.
 */
final class Worker {
	static final int MAX_RANGE = 5, ATTACK_REACH = 3;

	private Worker() {}

	static boolean isWeapon(ItemStack s) {
		// Espada, maça, tridente, lança: arma que não é ferramenta de quebrar (a espada tem "tool" só para teia).
		return s.is(ItemTags.SWORDS) || (s.has(DataComponents.WEAPON) && !s.has(DataComponents.TOOL));
	}

	static boolean isUsable(ItemStack s) {
		return !s.isEmpty() && (isWeapon(s) || s.has(DataComponents.TOOL));
	}

	/** Um tick do Autômato. {@code powered}: recebe sinal de redstone (alavanca nele, fio, tocha, bloco de redstone...). */
	static void tick(ServerLevel sl, Machine m, BlockState state, DispenserBlockEntity be, Direction facing, boolean powered) {
		int slot = -1;
		for (int i = 0; i < be.getContainerSize(); i++) {
			if (isUsable(be.getItem(i))) {
				slot = i;
				break;
			}
		}
		if (slot < 0) {
			m.slot = -1;
			m.item = null;
			stopMining(sl, m);
			return;
		}
		ItemStack tool = be.getItem(slot);
		if (slot != m.slot || tool.getItem() != m.item) { // trocou de ferramenta: a recarga começa do zero
			m.slot = slot;
			m.item = tool.getItem();
			m.charge = 0;
			stopMining(sl, m);
		}
		if (m.charge < 10_000) m.charge++;
		if (!powered) {
			stopMining(sl, m);
			return;
		}
		boolean weapon = isWeapon(tool);
		if (weapon || tool.is(ItemTags.AXES)) {
			LivingEntity target = findTarget(sl, m.pos(), facing);
			if (target != null) {
				stopMining(sl, m);
				if (m.charge >= attackDelay(tool)) {
					attack(sl, m, be, slot, facing, target);
					m.charge = 0;
					m.poke = 4;
				}
				return;
			}
			if (weapon) return;
		}
		mine(sl, m, be, slot, facing);
	}

	// ---------- golpe ----------

	/**
	 * Ticks para a barra de recarga encher com essa arma: o jogador tem 4 golpes/s e a arma soma o seu modificador
	 * (espada -2,4 → 1,6/s → 12,5 ticks; machado de ferro → 0,9/s → 22 ticks). Golpe com a barra cheia = dano total.
	 */
	static int attackDelay(ItemStack tool) {
		double base = 4.0, add = 0, mulBase = 0, mulTotal = 1;
		for (ItemAttributeModifiers.Entry e : tool.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
			if (!e.attribute().equals(Attributes.ATTACK_SPEED) || !e.slot().test(EquipmentSlot.MAINHAND)) continue;
			AttributeModifier mod = e.modifier();
			switch (mod.operation()) {
				case ADD_VALUE -> add += mod.amount();
				case ADD_MULTIPLIED_BASE -> mulBase += mod.amount();
				case ADD_MULTIPLIED_TOTAL -> mulTotal *= 1 + mod.amount();
			}
		}
		double v = base + add;
		double speed = Math.max(0.05, (v + v * mulBase) * mulTotal);
		return Math.max(1, (int) Math.ceil(20.0 / speed - 0.5 - 1e-6)); // 1e-6: 4 - 2,4 dá 1,5999...
	}

	/** Mob vivo nos blocos livres à frente (até 3, parando na primeira parede). Jogadores e suportes de armadura não. */
	static LivingEntity findTarget(ServerLevel sl, BlockPos pos, Direction f) {
		int free = 0;
		for (int i = 1; i <= ATTACK_REACH; i++) {
			BlockPos p = pos.relative(f, i);
			if (!sl.getBlockState(p).getCollisionShape(sl, p).isEmpty()) break;
			free = i;
		}
		if (free == 0) return null;
		AABB box = new AABB(pos.relative(f)).expandTowards(f.getStepX() * (free - 1), f.getStepY() * (free - 1), f.getStepZ() * (free - 1));
		Vec3 c = Vec3.atCenterOf(pos);
		LivingEntity best = null;
		for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box,
			e -> e.isAlive() && e.isAttackable() && !(e instanceof Player) && !(e instanceof ArmorStand))) {
			if (best == null || e.distanceToSqr(c) < best.distanceToSqr(c)) best = e;
		}
		return best;
	}

	private static void attack(ServerLevel sl, Machine m, DispenserBlockEntity be, int slot, Direction f, LivingEntity target) {
		FakePlayer fp = holding(sl, m.pos(), f, be.getItem(slot));
		// Atributos da arma (dano, velocidade): o jogador falso não roda o tick que os aplicaria ao segurar o item.
		List<Runnable> undo = new ArrayList<>();
		ItemStack tool = fp.getMainHandItem();
		for (ItemAttributeModifiers.Entry e : tool.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
			if (!e.slot().test(EquipmentSlot.MAINHAND)) continue;
			AttributeInstance inst = fp.getAttributes().getInstance(e.attribute());
			if (inst == null) continue;
			inst.addOrUpdateTransientModifier(e.modifier());
			undo.add(() -> inst.removeModifier(e.modifier().id()));
		}
		try {
			((AttackTickerAccess) fp).automato$setAttackStrengthTicker(10_000); // barra cheia (o mod já esperou a recarga)
			fp.attack(target);
		} finally {
			undo.forEach(Runnable::run);
			giveBack(fp, be, slot);
		}
	}

	// ---------- quebra ----------

	/** Primeiro bloco à frente que dá para quebrar (pula ar e líquidos; para em blocos inquebráveis como a bedrock). */
	static BlockPos findBlock(ServerLevel sl, BlockPos pos, Direction f, int range) {
		for (int i = 1; i <= range; i++) {
			BlockPos p = pos.relative(f, i);
			BlockState s = sl.getBlockState(p);
			if (s.isAir() || s.getBlock() instanceof LiquidBlock) continue;
			return s.getDestroySpeed(sl, p) < 0 ? null : p;
		}
		return null;
	}

	private static void mine(ServerLevel sl, Machine m, DispenserBlockEntity be, int slot, Direction f) {
		if (m.pause > 0) {
			m.pause--;
			return;
		}
		BlockPos target = findBlock(sl, m.pos(), f, m.range);
		if (target == null) {
			stopMining(sl, m);
			return;
		}
		BlockState s = sl.getBlockState(target);
		if (!target.equals(m.breaking) || s != m.breakingState) {
			stopMining(sl, m);
			m.breaking = target;
			m.breakingState = s;
		}
		m.progress += progressPerTick(sl, be.getItem(slot), s, target);
		if (m.progress >= 1) {
			stopMining(sl, m);
			FakePlayer fp = holding(sl, m.pos(), f, be.getItem(slot));
			try {
				fp.gameMode.destroyBlock(target); // drops, encantamentos, gasto da ferramenta e eventos de proteção
			} finally {
				giveBack(fp, be, slot);
			}
			m.pause = 5;
			m.poke = 4;
			return;
		}
		int stage = (int) (m.progress * 10);
		if (stage != m.crack) {
			m.crack = stage;
			sl.destroyBlockProgress(crackId(m), target, stage);
		}
		if (sl.getGameTime() % 6 == 0 && m.poke == 0) m.poke = 4; // cavando: batidas seguidas
		if (sl.getGameTime() % 4 == 0) {
			SoundType st = s.getSoundType();
			sl.playSound(null, target, st.getHitSound(), SoundSource.BLOCKS, (st.getVolume() + 1) / 8, st.getPitch() * 0.5f);
		}
	}

	/**
	 * Fração do bloco quebrada por tick, como a de um jogador em pé no chão: velocidade da ferramenta (+ eficiência)
	 * ÷ dureza ÷ 30 com a ferramenta certa (÷ 100 com a errada, que também não dá drop).
	 */
	static float progressPerTick(ServerLevel sl, ItemStack tool, BlockState s, BlockPos pos) {
		float hardness = s.getDestroySpeed(sl, pos);
		if (hardness < 0) return 0;
		if (hardness == 0) return 1;
		float speed = tool.getDestroySpeed(s);
		if (speed > 1) {
			Holder<Enchantment> eff = sl.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY);
			int lvl = EnchantmentHelper.getItemEnchantmentLevel(eff, tool);
			if (lvl > 0) speed += lvl * lvl + 1;
		}
		boolean correct = !s.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(s);
		return speed / hardness / (correct ? 30 : 100);
	}

	static void stopMining(ServerLevel sl, Machine m) {
		if (m.crack >= 0 && m.breaking != null) sl.destroyBlockProgress(crackId(m), m.breaking, -1);
		m.crack = -1;
		m.breaking = null;
		m.breakingState = null;
		m.progress = 0;
	}

	/** Id da animação de rachadura (o cliente separa as rachaduras por esse número; negativo para não bater com entidades). */
	private static int crackId(Machine m) {
		return -1 - (m.key().hashCode() & 0x3FFFFFFF);
	}

	// ---------- jogador falso ----------

	/** Jogador falso no centro do Autômato, olhando para a frente dele, segurando a ferramenta (o mesmo objeto do espaço). */
	private static FakePlayer holding(ServerLevel sl, BlockPos pos, Direction f, ItemStack tool) {
		FakePlayer fp = FakePlayer.get(sl);
		fp.setPos(pos.getX() + 0.5, pos.getY() + 0.5 - fp.getEyeHeight(), pos.getZ() + 0.5);
		float yaw = f.getAxis().isHorizontal() ? f.toYRot() : 0;
		float pitch = f == Direction.UP ? -90 : f == Direction.DOWN ? 90 : 0;
		fp.setYRot(yaw);
		fp.setYHeadRot(yaw);
		fp.setXRot(pitch);
		fp.setOnGround(true);
		fp.setItemSlot(EquipmentSlot.MAINHAND, tool);
		return fp;
	}

	/** Devolve ao espaço o que sobrou da ferramenta (vazio se quebrou) e esvazia a mão do jogador falso. */
	private static void giveBack(FakePlayer fp, DispenserBlockEntity be, int slot) {
		ItemStack after = fp.getMainHandItem();
		fp.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		be.setItem(slot, after.isEmpty() ? ItemStack.EMPTY : after);
		be.setChanged();
	}
}
