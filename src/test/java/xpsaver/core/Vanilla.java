package xpsaver.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Vanilla enchantment data for tests, copied from Minecraft 26.3 (identical to 26.2):
 * max level and anvil cost from net.minecraft.world.item.enchantment.Enchantments#bootstrap (Enchantment.definition args),
 * exclusive sets from data/minecraft/tags/enchantment/exclusive_set/{damage,bow}.json.
 */
final class Vanilla {
	static final Set<String> DAMAGE = Set.of("minecraft:sharpness", "minecraft:smite", "minecraft:bane_of_arthropods",
			"minecraft:impaling", "minecraft:density", "minecraft:breach");

	static final EnchantInfo SHARPNESS = new EnchantInfo("minecraft:sharpness", 5, 1, DAMAGE);
	static final EnchantInfo SMITE = new EnchantInfo("minecraft:smite", 5, 2, DAMAGE);
	static final EnchantInfo BANE = new EnchantInfo("minecraft:bane_of_arthropods", 5, 2, DAMAGE);
	static final EnchantInfo UNBREAKING = new EnchantInfo("minecraft:unbreaking", 3, 2, Set.of());
	static final EnchantInfo MENDING = new EnchantInfo("minecraft:mending", 1, 4, Set.of());
	static final EnchantInfo LOOTING = new EnchantInfo("minecraft:looting", 3, 4, Set.of());
	static final EnchantInfo FIRE_ASPECT = new EnchantInfo("minecraft:fire_aspect", 2, 4, Set.of());
	static final EnchantInfo KNOCKBACK = new EnchantInfo("minecraft:knockback", 2, 2, Set.of());
	static final EnchantInfo SWEEPING_EDGE = new EnchantInfo("minecraft:sweeping_edge", 3, 4, Set.of());

	static final List<EnchantInfo> SWORD_SUPPORTED = List.of(SHARPNESS, SMITE, BANE, KNOCKBACK, FIRE_ASPECT, LOOTING,
			SWEEPING_EDGE, UNBREAKING, MENDING);

	static final Map<String, EnchantInfo> REGISTRY = new LinkedHashMap<>();
	static {
		for (EnchantInfo e : SWORD_SUPPORTED) {
			REGISTRY.put(e.id(), e);
		}
	}

	static final Set<String> SWORD_SUPPORTS = REGISTRY.keySet();

	/** Undamaged diamond sword (max damage 1561) with the given prior-work value and enchantments. */
	static Piece sword(int repairCost, Map<String, Integer> enchants) {
		return new Piece("minecraft:diamond_sword", false, enchants, repairCost, 0, 1561, SWORD_SUPPORTS);
	}

	static Piece book(int repairCost, Map<String, Integer> enchants) {
		return new Piece("minecraft:enchanted_book", true, enchants, repairCost, 0, 0, Set.of());
	}

	/** REPAIR_COST after n anvil uses: 0 -> 1 -> 3 -> 7 (AnvilMenu#calculateIncreasedRepairCost: 2x + 1). */
	static int priorWork(int n) {
		return (1 << n) - 1;
	}

	private Vanilla() {}
}
