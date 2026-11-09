package xpsaver.core;

import java.util.Set;

/**
 * One enchantment as the game registry describes it, copied out so the cost math needs no Minecraft classes.
 * Source: net.minecraft.world.item.enchantment.Enchantment (26.2 decompiled): getMaxLevel(), getAnvilCost(), exclusiveSet().
 *
 * @param id        registry id, e.g. "minecraft:sharpness"
 * @param maxLevel  definition.maxLevel
 * @param anvilCost definition.anvilCost
 * @param exclusive ids in this enchantment's exclusive_set
 */
public record EnchantInfo(String id, int maxLevel, int anvilCost, Set<String> exclusive) {

	/** Mirrors Enchantment.areCompatible (26.2): different, and neither lists the other in its exclusive set. */
	public static boolean areCompatible(EnchantInfo a, EnchantInfo b) {
		return !a.id.equals(b.id) && !a.exclusive.contains(b.id) && !b.exclusive.contains(a.id);
	}
}
