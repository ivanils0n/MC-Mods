package io.github.ivanils0n.spawninfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Conta os mobs dentro de um cone à frente da câmera (atravessa paredes), separando hostis e pacíficos. */
public final class LookCounter {
	private LookCounter() {}

	public record Counts(int hostile, int passive, List<Map.Entry<EntityType<?>, Integer>> hostileTypes,
			List<Map.Entry<EntityType<?>, Integer>> passiveTypes, int range, int angle) {}

	public static Counts count(Minecraft mc) {
		LocalPlayer player = mc.player;
		Vec3 eye = player.getEyePosition(1f);
		Vec3 view = player.getViewVector(1f).normalize();
		int range = Config.get.lookRange, angle = Config.get.lookAngle;
		double cos = Math.cos(Math.toRadians(angle / 2.0));
		Map<EntityType<?>, Integer> hostile = new LinkedHashMap<>(), passive = new LinkedHashMap<>();
		int h = 0, p = 0;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e == player || !(e instanceof Mob) || !e.isAlive()) continue;
			if (!inCone(eye, view, e.getBoundingBox().getCenter(), range, cos)) continue;
			if (isHostile(e)) {
				h++;
				hostile.merge(e.getType(), 1, Integer::sum);
			} else {
				p++;
				passive.merge(e.getType(), 1, Integer::sum);
			}
		}
		return new Counts(h, p, sorted(hostile), sorted(passive), range, angle);
	}

	/** Dentro do alcance e do ângulo (o mob bem colado em você sempre conta). */
	static boolean inCone(Vec3 eye, Vec3 view, Vec3 target, double range, double cos) {
		Vec3 d = target.subtract(eye);
		double len = d.length();
		if (len > range) return false;
		if (len < 1.0) return true;
		return d.dot(view) / len >= cos;
	}

	public static boolean isHostile(Entity e) {
		return e instanceof Enemy || e.getType().getCategory() == MobCategory.MONSTER;
	}

	private static List<Map.Entry<EntityType<?>, Integer>> sorted(Map<EntityType<?>, Integer> m) {
		List<Map.Entry<EntityType<?>, Integer>> l = new ArrayList<>(m.entrySet());
		l.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
		return l;
	}
}
