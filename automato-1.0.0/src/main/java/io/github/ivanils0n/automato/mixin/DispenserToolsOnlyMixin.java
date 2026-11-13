package io.github.ivanils0n.automato.mixin;

import io.github.ivanils0n.automato.Automato;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(DispenserBlockEntity.class)
public abstract class DispenserToolsOnlyMixin implements Container {
	// Funil ou outro bloco tentando pôr item no Autômato: só ferramentas e armas entram (ejetor comum: tudo, como no jogo).
	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		BlockEntity self = (BlockEntity) (Object) this;
		return !(self.getLevel() instanceof ServerLevel sl) || !Automato.isMachine(sl, self.getBlockPos()) || Automato.isToolItem(stack);
	}
}
