package xpsaver.core;

import java.util.List;
import java.util.Map;

/**
 * Enchantments per item type in the priority order of the Minecraft Wiki "Best enchantments guide"
 * (https://minecraft.wiki/w/Tutorial:Best_enchantments_guide, read 2026-11-09). Optional entries keep their list position.
 * For mutually exclusive options only the guide's default is listed: stated by the guide where it names one
 * (Sharpness "for general use", Protection IV, Depth Strider "typically preferred", Silk Touch on axes, Loyalty + Channeling),
 * otherwise the option the guide lists first (bow Infinity, crossbow Multishot, pickaxe/shovel Fortune, hoe Silk Touch, mace Density).
 * Max levels, anvil costs and conflicts are not copied here; they still come from the game registry.
 */
public final class Guide {
	private static final Map<String, List<String>> PRIORITY = Map.ofEntries(
			Map.entry("helmet", List.of("mending", "protection", "unbreaking", "respiration", "aqua_affinity", "thorns")),
			Map.entry("chestplate", List.of("mending", "protection", "unbreaking", "thorns")),
			Map.entry("leggings", List.of("mending", "protection", "unbreaking", "thorns", "swift_sneak")),
			Map.entry("boots", List.of("mending", "protection", "feather_falling", "unbreaking", "depth_strider", "soul_speed", "thorns")),
			Map.entry("sword", List.of("unbreaking", "mending", "sharpness", "sweeping_edge", "looting", "fire_aspect", "knockback")),
			Map.entry("axe", List.of("unbreaking", "mending", "efficiency", "sharpness", "silk_touch")),
			Map.entry("bow", List.of("power", "unbreaking", "infinity", "flame", "punch")),
			Map.entry("crossbow", List.of("quick_charge", "multishot", "unbreaking", "mending")),
			Map.entry("trident", List.of("mending", "unbreaking", "loyalty", "channeling", "impaling")),
			Map.entry("mace", List.of("unbreaking", "density", "mending", "wind_burst", "fire_aspect")),
			Map.entry("spear", List.of("unbreaking", "mending", "sharpness", "looting", "fire_aspect", "knockback", "lunge")),
			Map.entry("pickaxe", List.of("unbreaking", "mending", "fortune", "efficiency")),
			Map.entry("shovel", List.of("unbreaking", "mending", "efficiency", "fortune")),
			Map.entry("hoe", List.of("unbreaking", "mending", "efficiency", "silk_touch")),
			Map.entry("fishing_rod", List.of("unbreaking", "mending", "luck_of_the_sea", "lure")),
			Map.entry("shears", List.of("unbreaking", "mending", "efficiency")),
			// "Other tools": flint and steel, shield, carrot/warped fungus on a stick, brush, elytra.
			Map.entry("other", List.of("unbreaking", "mending")));

	private Guide() {}

	/** Enchantment ids (e.g. "minecraft:mending") for a category, highest priority first; empty if the guide has no section. */
	public static List<String> priority(String category) {
		return PRIORITY.getOrDefault(category, List.of()).stream().map(id -> "minecraft:" + id).toList();
	}
}
