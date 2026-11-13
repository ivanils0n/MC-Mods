package io.github.ivanils0n.mochilas;

import io.github.ivanils0n.mochilas.backpack.Backpacks;
import io.github.ivanils0n.mochilas.commands.BackpackCommand;
import io.github.ivanils0n.mochilas.config.MochilasConfig;
import io.github.ivanils0n.mochilas.equipment.Equipment;
import io.github.ivanils0n.mochilas.gui.BackpackGui;
import io.github.ivanils0n.mochilas.resourcepack.Pack;
import io.github.ivanils0n.mochilas.storage.BackpackStorage;
import io.github.ivanils0n.mochilas.visual.Visuals;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Mochilas implements ModInitializer {
	public static final String MOD_ID = "mochilas";
	public static final Logger LOG = LoggerFactory.getLogger("Mochilas");
	private static BackpackStorage storage;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static BackpackStorage storage() {
		return storage;
	}

	@Override
	public void onInitialize() {
		MochilasConfig.load();
		Backpacks.register();
		Equipment.init();
		Pack.init();
		BackpackCommand.register();

		ServerLifecycleEvents.SERVER_STARTED.register(s -> {
			storage = new BackpackStorage(s);
			if (Boolean.getBoolean("mochilas.selftest")) SelfTest.run(s);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
			if (storage != null) storage.flush();
			Visuals.removeAll();
		});
		ServerTickEvents.END_SERVER_TICK.register(s -> {
			if (storage != null) storage.flush();
		});

		ServerPlayerEvents.JOIN.register(Visuals::refresh);
		ServerPlayerEvents.LEAVE.register(p -> {
			BackpackGui.closeFor(p);
			Visuals.remove(p);
		});
		ServerPlayerEvents.COPY_FROM.register((oldP, newP, alive) -> Equipment.copyFrom(oldP, newP));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldP, newP, alive) -> {
			Visuals.remove(oldP);
			Visuals.refresh(newP);
		});
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((p, from, to) -> Visuals.refresh(p));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p) {
				Equipment.onDeath(p);
				Visuals.remove(p);
			}
		});
	}
}
