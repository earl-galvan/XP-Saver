package xpsaver.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Random anvil situations (fixed seed, so failures repeat): items with or without enchantments and prior work, books with one or two
 * enchantments at any level, including conflicting and unsupported ones. The planner must match the brute-force referee on
 * {total, prior work of the finished item, biggest step}, or both must find nothing.
 */
class RandomizedPlannerTest {
	private static final Map<String, EnchantInfo> R = AllItemsOptimalTest.REGISTRY;
	private static final List<String> CATEGORIES = List.of("helmet", "chestplate", "leggings", "boots", "sword", "axe", "bow",
			"crossbow", "trident", "mace", "spear", "pickaxe", "shovel", "hoe", "fishing_rod", "shears", "other");
	/** Extra enchantments the item really accepts that conflict with guide picks, so conflicts get exercised. */
	private static final Map<String, List<String>> EXTRA = Map.of(
			"sword", List.of("smite", "bane_of_arthropods"), "axe", List.of("smite", "bane_of_arthropods"),
			"spear", List.of("smite", "bane_of_arthropods"), "mace", List.of("smite", "breach"),
			"helmet", List.of("fire_protection", "blast_protection"), "boots", List.of("frost_walker", "projectile_protection"),
			"pickaxe", List.of("silk_touch"), "shovel", List.of("silk_touch"), "trident", List.of("riptide"),
			"crossbow", List.of("piercing"));

	@Test
	void randomSituationsMatchTheReferee() {
		Random rnd = new Random(26_2);
		int planned = 0;
		int none = 0;
		for (int round = 0; round < 400; round++) {
			String category = CATEGORIES.get(rnd.nextInt(CATEGORIES.size()));
			List<String> supports = new ArrayList<>(Guide.priority(category));
			EXTRA.getOrDefault(category, List.of()).forEach(n -> supports.add("minecraft:" + n));

			// Item: a random compatible subset of what it supports, at random levels, with random prior work.
			Map<String, Integer> has = new LinkedHashMap<>();
			for (String id : supports) {
				if (rnd.nextInt(3) == 0 && has.keySet().stream().allMatch(o -> EnchantInfo.areCompatible(R.get(o), R.get(id)))) {
					has.put(id, 1 + rnd.nextInt(R.get(id).maxLevel()));
				}
			}
			int[] work = {0, 0, 1, 1, 3, 7, 15};
			Piece item = new Piece("minecraft:" + category, false, has, work[rnd.nextInt(work.length)], 0, 100, Set.copyOf(supports));

			List<Piece> inputs = new ArrayList<>(List.of(item));
			List<String> labels = new ArrayList<>(List.of("item"));
			int books = 1 + rnd.nextInt(6);
			for (int b = 0; b < books; b++) {
				Map<String, Integer> e = new LinkedHashMap<>();
				int count = rnd.nextInt(4) == 0 ? 2 : 1;
				while (e.size() < count) {
					String id = rnd.nextInt(12) == 0 ? "minecraft:lure" : supports.get(rnd.nextInt(supports.size())); // lure: usually unsupported
					if (e.keySet().stream().allMatch(o -> EnchantInfo.areCompatible(R.get(o), R.get(id)))) {
						e.put(id, 1 + rnd.nextInt(R.get(id).maxLevel()));
					}
				}
				inputs.add(new Piece("minecraft:enchanted_book", true, e, rnd.nextInt(4) == 0 ? 1 : 0, 0, 0, Set.of()));
				labels.add("book " + b);
			}

			String what = "round " + round + ": " + category + " " + has + " rc " + item.repairCost() + " + " + inputs.subList(1, inputs.size())
					.stream().map(p -> p.enchants() + "/" + p.repairCost()).toList();
			int[] referee = AllItemsOptimalTest.referee(inputs);
			Planner.Plan plan = Planner.plan(inputs, labels, R);
			if (referee == null) {
				assertNull(plan, what);
				none++;
				continue;
			}
			assertNotNull(plan, what);
			int[] got = AllItemsOptimalTest.score(plan);
			assertEquals(List.of(referee[0], referee[1], referee[2]), List.of(got[0], got[1], got[2]), what);
			checkSteps(plan, inputs, what);
			planned++;
		}
		System.out.println("planned " + planned + ", no valid plan " + none);
		assertTrue(planned > 200, "the random cases should mostly be plannable");
	}

	/** Every step uses exactly the pieces produced so far, each once, stays within the budget, and the last step holds the item. */
	private static void checkSteps(Planner.Plan plan, List<Piece> inputs, String what) {
		List<Piece> pool = new ArrayList<>(inputs);
		int total = 0;
		Piece last = null;
		for (Planner.Step s : plan.steps()) {
			assertTrue(remove(pool, s.leftPiece()) && remove(pool, s.rightPiece()), what + ": step " + s.number() + " uses a missing piece");
			AnvilCost.Result r = AnvilCost.combine(s.leftPiece(), s.rightPiece(), R, false, false);
			assertTrue(r.valid(), what);
			assertEquals(r.cost(), s.cost(), what);
			assertTrue(s.cost() <= Planner.MAX_STEP_COST, what);
			total += s.cost();
			pool.add(r.result());
			last = r.result();
		}
		assertEquals(1, pool.size(), what + ": every input is used");
		assertEquals(plan.total(), total, what);
		assertTrue(!last.book(), what + ": the item comes out");
	}

	private static boolean remove(List<Piece> pool, Piece p) {
		for (int i = 0; i < pool.size(); i++) {
			if (pool.get(i).equals(p)) {
				pool.remove(i);
				return true;
			}
		}
		return false;
	}

	@Test
	void guidedPlanningStaysFastWithNineBooks() {
		// Worst exact case the anvil dropdown can hit: item + 9 books (10 inputs), each planGuided call may run several searches.
		Random rnd = new Random(7);
		long worst = 0;
		List<Long> times = new ArrayList<>();
		for (int round = 0; round < 6; round++) {
			List<String> ids = new ArrayList<>(new HashSet<>(Guide.priority("boots")));
			ids.addAll(Guide.priority("boots").subList(0, 2)); // two duplicates so 9 books
			List<Planner.Want> wants = new ArrayList<>();
			for (String id : ids) {
				int level = 1 + rnd.nextInt(R.get(id).maxLevel());
				wants.add(new Planner.Want(id, new Piece("minecraft:enchanted_book", true, Map.of(id, level), 0, 0, 0, Set.of()), id));
			}
			Piece boots = new Piece("minecraft:boots", false, Map.of(), rnd.nextInt(2), 0, 100, Set.copyOf(Guide.priority("boots")));
			long start = System.nanoTime();
			Planner.planGuided(boots, "boots", wants, R);
			long ms = (System.nanoTime() - start) / 1_000_000;
			times.add(ms);
			if (round > 0) { // round 0 includes the JVM warming up, which a running game has already done
				worst = Math.max(worst, ms);
			}
		}
		System.out.println("planGuided with 9 books, ms per call: " + times);
		// Regression guard, not the 100 ms target: guide-sized sets (up to 7 books) have their own < 100 ms test in PlannerTest,
		// and the anvil dropdown plans on a background thread, so even this 9-book extreme never stalls a frame.
		assertTrue(worst < 250, "planning 9 books took " + worst + " ms");
	}
}
