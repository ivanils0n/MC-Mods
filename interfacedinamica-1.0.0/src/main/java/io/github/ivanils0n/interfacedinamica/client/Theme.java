package io.github.ivanils0n.interfacedinamica.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CrafterScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.DispenserScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.phys.BlockHitResult;

/** Tipo de tela: escolhe a pose, os efeitos e qual ajuste liga/desliga o mod nela. */
public enum Theme {
	INVENTORY, CHEST, ENDER, CRAFTING, FURNACE, ENCHANT, OTHER;

	public static Theme of(Screen screen) {
		String cls = screen.getClass().getSimpleName();
		if (screen instanceof InventoryScreen || cls.equals("CreativeModeInventoryScreen")) return INVENTORY;
		if (screen instanceof AbstractFurnaceScreen<?>) return FURNACE;
		if (screen instanceof CraftingScreen || screen instanceof CrafterScreen) return CRAFTING;
		if (screen instanceof EnchantmentScreen) return ENCHANT;
		if (screen instanceof ContainerScreen && lookingAtEnderChest()) return ENDER;
		if (screen instanceof ContainerScreen || screen instanceof ShulkerBoxScreen || screen instanceof HopperScreen
				|| screen instanceof DispenserScreen) return CHEST;
		return OTHER;
	}

	private static boolean lookingAtEnderChest() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || !(mc.hitResult instanceof BlockHitResult hit)) return false;
		return mc.level.getBlockState(hit.getBlockPos()).getBlock() instanceof EnderChestBlock;
	}

	public boolean enabled(Config c) {
		if (!c.enabled) return false;
		return switch (this) {
			case INVENTORY -> c.inventory;
			case CHEST, ENDER -> c.chests;
			case CRAFTING -> c.crafting;
			case FURNACE -> c.furnaces;
			default -> c.others;
		};
	}
}
