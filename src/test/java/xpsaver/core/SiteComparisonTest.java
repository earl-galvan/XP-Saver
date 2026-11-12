package xpsaver.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Same builds as enchantmenttable.com's worked "enchant order" pages (Java 26.3, read 2026-11-12), one book per enchantment at its
 * level: the planner must reach the site's total levels, with no more XP points and no bigger biggest step, and match its
 * cheapest totals for items with earlier anvil uses. The planner ranks orders by levels, then XP points (Planner.xpPoints),
 * as the site's choices show (shears: 5 + 4 + 5 over 5 + 2 + 7). For boots the planner finds the same 66 levels and biggest
 * step 16 for 1,246 XP points, 4 fewer than the site's 1,250, so the site doesn't always settle ties by XP points.
 */
class SiteComparisonTest {
	private static final Map<String, EnchantInfo> R = AllItemsOptimalTest.REGISTRY;

	/** total levels, XP points, biggest step */
	private static int[] plan(String item, int uses, Object... build) {
		Map<String, Integer> want = new LinkedHashMap<>();
		for (int i = 0; i < build.length; i += 2) {
			want.put("minecraft:" + build[i], (Integer) build[i + 1]);
		}
		Piece target = new Piece("minecraft:" + item, false, Map.of(), (1 << uses) - 1, 0, 100, want.keySet());
		List<Piece> books = new ArrayList<>();
		List<String> labels = new ArrayList<>();
		want.forEach((id, level) -> {
			books.add(Vanilla.book(0, Map.of(id, level)));
			labels.add(id + " " + level);
		});
		Selector.Result r = Selector.choose(target, item, books, labels, new ArrayList<>(want.keySet()), R);
		assertEquals(want.size(), r.goals().size(), item + ": every enchantment planned");
		List<Integer> costs = r.plan().steps().stream().map(Planner.Step::cost).toList();
		return new int[] {r.plan().total(), costs.stream().mapToInt(Planner::xpPoints).sum(), costs.stream().mapToInt(c -> c).max().orElse(0)};
	}

	private static void site(int total, int xp, int maxStep, int[] ours, String item) {
		assertEquals(total, ours[0], item + ": total levels");
		assertTrue(ours[1] <= xp, item + ": " + ours[1] + " XP points, the site needs " + xp);
		assertTrue(ours[2] <= maxStep, item + ": biggest step " + ours[2] + ", the site's is " + maxStep);
		if (ours[1] != xp || ours[2] != maxStep) {
			System.out.println(item + ": " + ours[0] + " levels, " + ours[1] + " XP points, biggest step " + ours[2]
					+ " (site: " + total + ", " + xp + ", " + maxStep + ")");
		}
	}

	@Test
	void xpPointsMatchTheGame() {
		assertEquals(List.of(0, 7, 16, 315, 352, 1395), List.of(Planner.xpPoints(0), Planner.xpPoints(1), Planner.xpPoints(2),
				Planner.xpPoints(15), Planner.xpPoints(16), Planner.xpPoints(30)));
	}

	@Test
	void freshItemsMatchTheSite() {
		site(45, 753, 14, plan("helmet", 0, "protection", 4, "respiration", 3, "aqua_affinity", 1, "thorns", 3, "unbreaking", 3, "mending", 1), "helmet");
		site(28, 414, 12, plan("chestplate", 0, "protection", 4, "thorns", 3, "unbreaking", 3, "mending", 1), "chestplate");
		site(44, 798, 16, plan("leggings", 0, "protection", 4, "swift_sneak", 3, "thorns", 3, "unbreaking", 3, "mending", 1), "leggings");
		site(66, 1250, 16, plan("boots", 0, "protection", 4, "feather_falling", 4, "soul_speed", 3, "depth_strider", 3, "thorns", 3,
				"unbreaking", 3, "mending", 1), "boots");
		site(49, 785, 14, plan("sword", 0, "sharpness", 5, "sweeping_edge", 3, "fire_aspect", 2, "knockback", 2, "looting", 3,
				"unbreaking", 3, "mending", 1), "sword");
		site(32, 462, 11, plan("axe", 0, "sharpness", 5, "fortune", 3, "efficiency", 5, "unbreaking", 3, "mending", 1), "axe");
		site(46, 698, 13, plan("spear", 0, "lunge", 3, "sharpness", 5, "fire_aspect", 2, "knockback", 2, "looting", 3, "unbreaking", 3,
				"mending", 1), "spear");
		site(31, 435, 10, plan("mace", 0, "wind_burst", 3, "density", 5, "fire_aspect", 2, "unbreaking", 3, "mending", 1), "mace");
		site(33, 475, 10, plan("trident", 0, "channeling", 1, "impaling", 5, "loyalty", 3, "unbreaking", 3, "mending", 1), "trident");
		site(26, 334, 9, plan("bow", 0, "power", 5, "flame", 1, "punch", 2, "unbreaking", 3, "mending", 1), "bow");
		site(17, 187, 6, plan("crossbow", 0, "multishot", 1, "quick_charge", 3, "unbreaking", 3, "mending", 1), "crossbow");
		site(23, 291, 8, plan("pickaxe", 0, "fortune", 3, "efficiency", 5, "unbreaking", 3, "mending", 1), "pickaxe, shovel, hoe");
		site(24, 314, 9, plan("fishing_rod", 0, "luck_of_the_sea", 3, "lure", 3, "unbreaking", 3, "mending", 1), "fishing rod");
		site(14, 150, 5, plan("shears", 0, "efficiency", 5, "unbreaking", 3, "mending", 1), "shears");
	}

	@Test
	void usedItemsMatchTheSitesTotals() {
		Object[] sword = {"sharpness", 5, "sweeping_edge", 3, "fire_aspect", 2, "knockback", 2, "looting", 3, "unbreaking", 3, "mending", 1};
		Object[] shovel = {"fortune", 3, "efficiency", 5, "unbreaking", 3, "mending", 1};
		Object[] shears = {"efficiency", 5, "unbreaking", 3, "mending", 1};
		int[][] site = {{56, 65, 77}, {27, 33, 40}, {17, 22, 26}}; // 1, 2 and 3 earlier anvil uses
		for (int uses = 1; uses <= 3; uses++) {
			assertEquals(site[0][uses - 1], plan("sword", uses, sword)[0], "sword, uses " + uses);
			assertEquals(site[1][uses - 1], plan("shovel", uses, shovel)[0], "shovel, uses " + uses);
			assertEquals(site[2][uses - 1], plan("shears", uses, shears)[0], "shears, uses " + uses);
		}
	}
}
