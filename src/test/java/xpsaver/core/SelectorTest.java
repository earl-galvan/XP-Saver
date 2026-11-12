package xpsaver.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Selector: which books and item copies to use, and the order. The randomized test compares it with a brute-force referee that tries
 * every subset of the candidates and every merge order (the target always on the left), keeping steps within the budget.
 */
class SelectorTest {
	private static final Map<String, EnchantInfo> R = AllItemsOptimalTest.REGISTRY;
	private static final List<String> SWORD = Guide.priority("sword");

	private static Piece book(String name, int level) {
		return Vanilla.book(0, Map.of("minecraft:" + name, level));
	}

	private static Selector.Result choose(Piece target, List<Piece> candidates, List<String> wanted) {
		List<String> labels = new ArrayList<>();
		for (int i = 0; i < candidates.size(); i++) {
			labels.add("c" + i);
		}
		return Selector.choose(target, "item", candidates, labels, wanted, R);
	}

	@Test
	void reachableMergesEqualLevelsUpward() {
		assertEquals(5, Selector.reachable(0, List.of(4, 4), 5));
		assertEquals(5, Selector.reachable(0, List.of(3, 3, 3, 3), 5));
		assertEquals(4, Selector.reachable(0, List.of(3, 4), 5));
		assertEquals(5, Selector.reachable(4, List.of(4), 5));
		assertEquals(3, Selector.reachable(0, List.of(3, 3), 3)); // capped at max
		assertEquals(0, Selector.reachable(0, List.of(0, 0), 5));
	}

	@Test
	void aBookThatAlreadyHasTheLevelBeatsMergingTwoLowerOnes() {
		Selector.Result r = choose(Vanilla.sword(0, Map.of()), List.of(book("sharpness", 4), book("sharpness", 4), book("sharpness", 5)), SWORD);
		assertEquals(List.of(new Selector.Goal("minecraft:sharpness", 5)), r.goals());
		assertEquals(List.of(2), r.used());
		assertEquals(1, r.plan().steps().size());
	}

	@Test
	void twoEqualBooksAreMergedWhenNoBookHasTheLevel() {
		Selector.Result r = choose(Vanilla.sword(0, Map.of()), List.of(book("sharpness", 4), book("sharpness", 4)), SWORD);
		assertEquals(List.of(new Selector.Goal("minecraft:sharpness", 5)), r.goals());
		assertEquals(List.of(0, 1), r.used());
		assertEquals(5, finalPiece(r).enchants().get("minecraft:sharpness"));
	}

	@Test
	void theItemsOwnLevelCountsTowardAMerge() {
		Selector.Result r = choose(Vanilla.sword(0, Map.of("minecraft:sharpness", 4)), List.of(book("sharpness", 4), book("sharpness", 4)), SWORD);
		assertEquals(List.of(new Selector.Goal("minecraft:sharpness", 5)), r.goals());
		assertEquals(1, r.used().size(), "IV on the item + one IV book is enough");
	}

	@Test
	void anEnchantedCopyOfTheItemCanBeUsed() {
		Piece copy = Vanilla.sword(0, Map.of("minecraft:unbreaking", 3, "minecraft:mending", 1, "minecraft:looting", 3));
		Selector.Result r = choose(Vanilla.sword(0, Map.of()), List.of(copy), SWORD);
		assertEquals(List.of(0), r.used());
		Piece done = finalPiece(r);
		assertEquals(3, done.enchants().get("minecraft:looting"));
		assertEquals(1, done.enchants().get("minecraft:mending"));
	}

	@Test
	void aConflictingWishIsReportedNotPlanned() {
		Selector.Result r = choose(Vanilla.sword(0, Map.of()), List.of(book("sharpness", 5), book("smite", 5)),
				List.of("minecraft:sharpness", "minecraft:smite"));
		assertEquals(List.of(new Selector.Goal("minecraft:sharpness", 5)), r.goals());
		assertEquals(List.of("minecraft:smite"), r.conflicts());
		assertEquals(List.of(0), r.used());
	}

	@Test
	void nothingToDoGivesAnEmptyPlan() {
		Selector.Result r = choose(Vanilla.sword(0, Map.of("minecraft:sharpness", 5)), List.of(book("sharpness", 5)), SWORD);
		assertTrue(r.goals().isEmpty());
		assertTrue(r.plan().steps().isEmpty());
	}

	@Test
	void randomInventoriesMatchTheBruteForceReferee() {
		Random rnd = new Random(26_3);
		List<String> categories = List.of("helmet", "boots", "sword", "axe", "bow", "trident", "pickaxe", "mace", "fishing_rod");
		int checked = 0;
		for (int round = 0; round < 1500; round++) {
			String category = categories.get(rnd.nextInt(categories.size()));
			List<String> supports = Guide.priority(category);
			Map<String, Integer> has = randomEnchants(rnd, supports, 3);
			int[] work = {0, 0, 1, 3};
			Piece item = new Piece("minecraft:" + category, false, has, work[rnd.nextInt(work.length)], 0, 100, Set.copyOf(supports));
			List<Piece> candidates = new ArrayList<>();
			int n = 1 + rnd.nextInt(6);
			for (int c = 0; c < n; c++) {
				if (rnd.nextInt(5) == 0 && !candidates.isEmpty()) {
					candidates.add(candidates.get(rnd.nextInt(candidates.size()))); // an exact duplicate
				} else if (rnd.nextInt(6) == 0) {
					candidates.add(new Piece(item.itemType(), false, randomEnchants(rnd, supports, 2), rnd.nextInt(2), 0, 100, item.supports()));
				} else {
					candidates.add(new Piece("minecraft:enchanted_book", true, randomEnchants(rnd, supports, 1 + rnd.nextInt(2)),
							rnd.nextInt(4) == 0 ? 1 : 0, 0, 0, Set.of()));
				}
			}
			Selector.Result r = choose(item, candidates, supports);
			String what = "round " + round + ": " + category + " " + has + " rc " + item.repairCost() + " + "
					+ candidates.stream().map(p -> (p.book() ? "B" : "I") + p.enchants() + "/" + p.repairCost()).toList() + " -> " + r;
			if (r.goals().isEmpty()) {
				assertTrue(r.plan().steps().isEmpty(), what);
				if (!r.dropped().isEmpty()) {
					assertEquals(Long.MAX_VALUE, referee(item, candidates, r.dropped().subList(0, 1)), what);
				}
				continue;
			}
			replay(r, item, candidates, what);
			long best = referee(item, candidates, r.goals());
			int xp = r.plan().steps().stream().mapToInt(s -> Planner.xpPoints(s.cost())).sum();
			assertEquals(best / XP_SCALE, r.plan().total(), what);
			assertEquals(best % XP_SCALE, xp, what + ": fewest XP points among the cheapest orders");
			if (!r.dropped().isEmpty()) {
				List<Selector.Goal> more = new ArrayList<>(r.goals());
				more.add(r.dropped().get(0));
				assertEquals(Long.MAX_VALUE, referee(item, candidates, more), what + ": the next goal was droppable only if it can't fit");
			}
			checked++;
		}
		System.out.println("selector checked against the referee: " + checked);
		assertTrue(checked > 900);
	}

	@Test
	void elevenUsefulBooksStayFast() {
		// The largest exact case: the item + 11 useful books (Planner.EXACT_LIMIT inputs), including a pair that must merge (IV + IV)
		// and books with two enchantments, so there are many different partial results.
		List<String> boots = Guide.priority("boots");
		Piece item = new Piece("minecraft:boots", false, Map.of(), 0, 0, 100, Set.copyOf(boots));
		List<Piece> books = new ArrayList<>();
		for (String id : boots) {
			books.add(Vanilla.book(0, Map.of(id, R.get(id).maxLevel())));
		}
		books.set(1, Vanilla.book(0, Map.of("minecraft:protection", 3)));
		books.add(Vanilla.book(0, Map.of("minecraft:protection", 3)));
		books.add(Vanilla.book(0, Map.of("minecraft:feather_falling", 4, "minecraft:unbreaking", 1)));
		books.add(Vanilla.book(0, Map.of("minecraft:feather_falling", 3, "minecraft:depth_strider", 3)));
		books.add(Vanilla.book(1, Map.of("minecraft:thorns", 3, "minecraft:unbreaking", 3)));
		assertEquals(Planner.EXACT_LIMIT - 1, books.size());
		long worst = 0;
		for (int round = 0; round < 4; round++) {
			long start = System.nanoTime();
			Selector.Result r = choose(item, books, boots);
			long ms = (System.nanoTime() - start) / 1_000_000;
			if (round > 0) {
				worst = Math.max(worst, ms); // round 0 includes JVM warm-up
			}
			assertTrue(!r.approximate() && !r.plan().steps().isEmpty());
		}
		System.out.println("selector, 11 useful books: worst " + worst + " ms");
		assertTrue(worst < 3000, "took " + worst + " ms"); // runs on a background thread in game, with "Working out..." shown
	}

	private static Map<String, Integer> randomEnchants(Random rnd, List<String> ids, int count) {
		Map<String, Integer> e = new LinkedHashMap<>();
		for (int tries = 0; tries < 10 && e.size() < count; tries++) {
			String id = ids.get(rnd.nextInt(ids.size()));
			if (!e.containsKey(id) && e.keySet().stream().allMatch(o -> EnchantInfo.areCompatible(R.get(o), R.get(id)))) {
				e.put(id, 1 + rnd.nextInt(R.get(id).maxLevel()));
			}
		}
		return e;
	}

	private static Piece finalPiece(Selector.Result r) {
		Planner.Step last = r.plan().steps().get(r.plan().steps().size() - 1);
		return AnvilCost.combine(last.leftPiece(), last.rightPiece(), R, false, false).result();
	}

	/** Steps use exactly the item and the used candidates, each once; prices match; the finished item reaches every goal. */
	private static void replay(Selector.Result r, Piece item, List<Piece> candidates, String what) {
		List<Piece> pool = new ArrayList<>(List.of(item));
		r.used().forEach(i -> pool.add(candidates.get(i)));
		int total = 0;
		for (Planner.Step s : r.plan().steps()) {
			assertTrue(pool.remove(s.leftPiece()) && pool.remove(s.rightPiece()), what + ": step " + s.number());
			AnvilCost.Result res = AnvilCost.combine(s.leftPiece(), s.rightPiece(), R, false, false);
			assertTrue(res.valid() && res.cost() == s.cost() && s.cost() <= Planner.maxStepCost, what);
			total += s.cost();
			pool.add(res.result());
		}
		assertEquals(1, pool.size(), what);
		assertEquals(r.plan().total(), total, what);
		for (Selector.Goal g : r.goals()) {
			assertTrue(pool.get(0).enchants().getOrDefault(g.id(), 0) >= g.level(), what + ": " + g);
		}
	}

	private static final long XP_SCALE = 1_000_000_000L;

	/**
	 * Cheapest order over every subset of candidates and every merge order whose finished item reaches the goals, as
	 * total * XP_SCALE + XP points (fewest levels, then fewest XP points); Long.MAX_VALUE if none.
	 */
	private static long referee(Piece item, List<Piece> candidates, List<Selector.Goal> goals) {
		long best = Long.MAX_VALUE;
		for (int mask = 0; mask < (1 << candidates.size()); mask++) {
			List<Piece> pool = new ArrayList<>(List.of(item));
			List<Boolean> target = new ArrayList<>(List.of(true));
			for (int i = 0; i < candidates.size(); i++) {
				if ((mask & (1 << i)) != 0) {
					pool.add(candidates.get(i));
					target.add(false);
				}
			}
			if (pool.size() < 2) {
				continue;
			}
			best = Math.min(best, search(pool, target, goals, new HashMap<>()));
		}
		return best;
	}

	private static long search(List<Piece> pool, List<Boolean> target, List<Selector.Goal> goals, Map<String, Long> memo) {
		if (pool.size() == 1) {
			for (Selector.Goal g : goals) {
				if (pool.get(0).enchants().getOrDefault(g.id(), 0) < g.level()) {
					return Long.MAX_VALUE;
				}
			}
			return 0;
		}
		List<String> parts = new ArrayList<>();
		for (int i = 0; i < pool.size(); i++) {
			Piece p = pool.get(i);
			parts.add((target.get(i) ? "T" : p.book() ? "B" : "I") + p.repairCost() + new TreeMap<>(p.enchants()));
		}
		parts.sort(null);
		String key = String.join("|", parts);
		Long known = memo.get(key);
		if (known != null) {
			return known;
		}
		long best = Long.MAX_VALUE;
		for (int i = 0; i < pool.size(); i++) {
			for (int j = 0; j < pool.size(); j++) {
				if (i == j || target.get(j)) {
					continue; // the target never goes on the right
				}
				AnvilCost.Result res = AnvilCost.combine(pool.get(i), pool.get(j), R, false, false);
				if (!res.valid() || res.cost() > Planner.maxStepCost) {
					continue;
				}
				List<Piece> next = new ArrayList<>(pool);
				List<Boolean> nextTarget = new ArrayList<>(target);
				boolean t = target.get(i);
				next.remove(Math.max(i, j));
				next.remove(Math.min(i, j));
				nextTarget.remove(Math.max(i, j));
				nextTarget.remove(Math.min(i, j));
				next.add(res.result());
				nextTarget.add(t);
				long rest = search(next, nextTarget, goals, memo);
				if (rest != Long.MAX_VALUE) {
					best = Math.min(best, res.cost() * XP_SCALE + Planner.xpPoints(res.cost()) + rest);
				}
			}
		}
		memo.put(key, best);
		return best;
	}
}
