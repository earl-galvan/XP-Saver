package xpsaver.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static xpsaver.core.Vanilla.REGISTRY;
import static xpsaver.core.Vanilla.book;
import static xpsaver.core.Vanilla.sword;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Step prices come from AnvilCost, which ports AnvilMenu#createResult (Minecraft 26.2). */
class PlannerTest {
	private static final List<Piece> SIX_BOOKS = List.of(
			sword(0, Map.of()),
			book(0, Map.of("minecraft:sharpness", 5)),
			book(0, Map.of("minecraft:looting", 3)),
			book(0, Map.of("minecraft:fire_aspect", 2)),
			book(0, Map.of("minecraft:unbreaking", 3)),
			book(0, Map.of("minecraft:mending", 1)),
			book(0, Map.of("minecraft:sweeping_edge", 3)));

	private static List<String> labels(int n) {
		List<String> l = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			l.add("input " + i);
		}
		return l;
	}

	@Test
	void sixBookPlanIsNoWorseThanEveryBruteForceOrder() {
		Planner.Plan plan = Planner.plan(SIX_BOOKS, labels(SIX_BOOKS.size()), REGISTRY);
		assertNotNull(plan);
		assertFalse(plan.approximate());
		int bruteMin = bruteForceMin(new ArrayList<>(SIX_BOOKS));
		assertTrue(plan.total() <= bruteMin, "planner " + plan.total() + " > brute force " + bruteMin);
		// The planner only uses real anvil steps, so it can never beat the true minimum either.
		assertEquals(bruteMin, plan.total());
		assertEquals(SIX_BOOKS.size() - 1, plan.steps().size());
		assertEquals(plan.total(), plan.steps().stream().mapToInt(Planner.Step::cost).sum());
	}

	@Test
	void whenTheBudgetBindsThePlanIsStillTheCheapestOrderThatFits() {
		// Same six books on a sword with 3 prior uses (REPAIR_COST 7): cheap one-by-one orders now break the 29 cap.
		List<Piece> inputs = new ArrayList<>(SIX_BOOKS);
		inputs.set(0, sword(7, Map.of()));
		Planner.Plan plan = Planner.plan(inputs, labels(inputs.size()), REGISTRY);
		assertNotNull(plan);
		assertTrue(plan.steps().stream().allMatch(s -> s.cost() <= Planner.MAX_STEP_COST));
		assertEquals(bruteForceMin(new ArrayList<>(inputs)), plan.total());
	}

	@Test
	void singleBookPlanMatchesTheAnvilPrice() {
		Planner.Plan plan = Planner.plan(List.of(sword(0, Map.of()), book(0, Map.of("minecraft:sharpness", 5))), labels(2), REGISTRY);
		assertEquals(5, plan.total()); // same as AnvilCostTest: 0 + 0 + 1 * 5
	}

	@Test
	void noPlanWhenEveryOrderBreaksTheStepBudget() {
		// REPAIR_COST 31 (5 prior uses) + Sharpness V book: 31 + 0 + 5 = 36 > 29. The game would allow it (< 40); the budget does not.
		assertNull(Planner.plan(List.of(sword(31, Map.of()), book(0, Map.of("minecraft:sharpness", 5))), labels(2), REGISTRY));
	}

	@Test
	void conflictingBooksCannotBothBeApplied() {
		// Sharpness then Smite: the second step has no compatible enchantment -> invalid, so no full plan exists.
		assertNull(Planner.plan(List.of(sword(0, Map.of()), book(0, Map.of("minecraft:sharpness", 5)),
				book(0, Map.of("minecraft:smite", 5))), labels(3), REGISTRY));
	}

	/**
	 * Guide sword set on a fresh sword, every step checked by hand with
	 * cost = penalty(left) + penalty(right) + sum(level x book multiplier), penalty = 2^uses - 1.
	 * Book multipliers (anvilCost / 2, min 1): Fire Aspect 2, Looting 2, Knockback 1, Sweeping Edge 2, Mending 2, Sharpness 1, Unbreaking 1.
	 * The wiki guide's own optimal sword order also totals 49.
	 */
	@Test
	void guideSwordSetOnAFreshSword() {
		Planner.Plan plan = Planner.planGuided(sword(0, Map.of()), "sword", guideSwordWants(), REGISTRY);
		assertEquals(List.of(), plan.dropped());
		// Lowest total (49) with the smallest biggest step (14); this is also the order the wiki guide prints.
		assertEquals(List.of(
				6,   // 1. sword(0 uses) + Looting III: 0 + 0 + 2*3                          -> sword 1 use
				3,   // 2. Sweeping Edge III + Unbreaking III: 0 + 0 + 1*3                   -> book 1 use
				11,  // 3. sword(1) + [Sweeping Edge III, Unbreaking III](1): 1 + 1 + 2*3 + 1*3 -> sword 2 uses
				2,   // 4. Sharpness V + Knockback II: 0 + 0 + 1*2                           -> book 1 use
				11,  // 5. sword(2) + [Sharpness V, Knockback II](1): 3 + 1 + 1*5 + 1*2       -> sword 3 uses
				2,   // 6. Fire Aspect II + Mending: 0 + 0 + 2*1                             -> book 1 use
				14), // 7. sword(3) + [Fire Aspect II, Mending](1): 7 + 1 + 2*2 + 2*1        -> sword 4 uses
				plan.steps().stream().map(Planner.Step::cost).toList());
		assertEquals(49, plan.total());
	}

	@Test
	void stepsMayReachButNeverExceedTheBudget() {
		// Sword with 3 prior uses (REPAIR_COST 7): the cheapest fitting plan costs 77 and its last step is exactly 29.
		Planner.Plan plan = Planner.planGuided(sword(7, Map.of()), "sword", guideSwordWants(), REGISTRY);
		assertEquals(List.of(), plan.dropped());
		assertEquals(77, plan.total());
		assertEquals(Planner.MAX_STEP_COST, plan.steps().stream().mapToInt(Planner.Step::cost).max().orElseThrow());
	}

	@Test
	void followsTheBudgetSetInTheConfig() {
		try {
			for (int budget : new int[] {15, 39}) {
				Planner.maxStepCost = budget;
				Planner.Plan plan = Planner.planGuided(sword(7, Map.of()), "sword", guideSwordWants(), REGISTRY);
				assertTrue(plan.steps().stream().allMatch(s -> s.cost() <= budget), "budget " + budget);
			}
			// A looser budget never makes the full plan dearer than the default's 77.
			assertTrue(Planner.planGuided(sword(7, Map.of()), "sword", guideSwordWants(), REGISTRY).total() <= 77);
		} finally {
			Planner.maxStepCost = Planner.MAX_STEP_COST;
		}
	}

	@Test
	void dropsFromTheLowPriorityEndUntilAPlanFits() {
		// Sword with 4 prior uses (REPAIR_COST 15): only Unbreaking, Mending and Sharpness (the guide's top three) still fit.
		// 1. Sharpness V + Unbreaking III: 0 + 0 + 1*3 = 3; 2. that + Mending: 1 + 0 + 2*1 = 3;
		// 3. sword(15) + book(3): 15 + 3 + 5 + 3 + 2 = 28. Total 34 (Sharpness + Mending first, then Unbreaking, is also 34
		// but its biggest book step is 4).
		Planner.Plan plan = Planner.planGuided(sword(15, Map.of()), "sword", guideSwordWants(), REGISTRY);
		assertEquals(List.of("minecraft:sweeping_edge", "minecraft:looting", "minecraft:fire_aspect", "minecraft:knockback"), plan.dropped());
		assertEquals(List.of(3, 3, 28), plan.steps().stream().map(Planner.Step::cost).toList());
		assertEquals(34, plan.total());
	}

	@Test
	void nothingFitsMeansEverythingIsDropped() {
		Planner.Plan plan = Planner.planGuided(sword(31, Map.of()), "sword", guideSwordWants(), REGISTRY);
		assertTrue(plan.steps().isEmpty());
		assertEquals(Guide.priority("sword"), plan.dropped());
	}

	@Test
	void guidedPlanningIsFastEnoughForAKeyPress() {
		Planner.planGuided(sword(0, Map.of()), "sword", guideSwordWants(), REGISTRY); // warm up the JIT once
		long start = System.nanoTime();
		Planner.planGuided(sword(0, Map.of()), "sword", guideSwordWants(), REGISTRY);
		assertTrue((System.nanoTime() - start) / 1_000_000 < 100, "planning took 100 ms or more");
	}

	@Test
	void aboveTheLimitUsesGreedyAndSaysSo() {
		List<Piece> inputs = new ArrayList<>(SIX_BOOKS);
		inputs.add(book(0, Map.of("minecraft:knockback", 2)));
		for (int i = 0; i < 4; i++) {
			inputs.add(book(0, Map.of("minecraft:unbreaking", 1)));
		}
		assertTrue(inputs.size() > Planner.EXACT_LIMIT);
		Planner.Plan plan = Planner.plan(inputs, labels(inputs.size()), REGISTRY);
		assertNotNull(plan);
		assertTrue(plan.approximate());
		assertEquals(inputs.size() - 1, plan.steps().size());
		assertTrue(plan.steps().stream().allMatch(s -> s.cost() <= Planner.MAX_STEP_COST));
	}

	/** One max-level book per enchantment in the guide's sword list, highest priority first. */
	static List<Planner.Want> guideSwordWants() {
		List<Planner.Want> wants = new ArrayList<>();
		for (String id : Guide.priority("sword")) {
			wants.add(new Planner.Want(id, book(0, Map.of(id, REGISTRY.get(id).maxLevel())), id));
		}
		return wants;
	}

	/** Tries every ordered pair at every step, independently of the planner, with the same per-step budget. Item stays left. */
	private static int bruteForceMin(List<Piece> pool) {
		if (pool.size() == 1) {
			return 0;
		}
		int best = Integer.MAX_VALUE;
		for (int i = 0; i < pool.size(); i++) {
			for (int j = 0; j < pool.size(); j++) {
				if (i == j || !pool.get(j).book()) {
					continue; // only books are ever sacrificed here; the sword is the only non-book
				}
				AnvilCost.Result r = AnvilCost.combine(pool.get(i), pool.get(j), REGISTRY, false, false);
				if (!r.valid() || r.cost() > Planner.MAX_STEP_COST) {
					continue;
				}
				List<Piece> next = new ArrayList<>(pool);
				next.remove(Math.max(i, j));
				next.remove(Math.min(i, j));
				next.add(r.result());
				int rest = bruteForceMin(next);
				if (rest != Integer.MAX_VALUE) {
					best = Math.min(best, r.cost() + rest);
				}
			}
		}
		return best;
	}
}
