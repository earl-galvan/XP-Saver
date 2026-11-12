package xpsaver.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

/**
 * For every item in the guide, checks the planner against an independent referee and against the wiki's own orders.
 * The referee is a different algorithm on purpose: Dijkstra over whole anvil states (the multiset of pieces still on hand),
 * where one move is any allowed anvil step. It shares only {@link AnvilCost} with the planner.
 */
class AllItemsOptimalTest {
	// Max level and anvil cost from net.minecraft.world.item.enchantment.Enchantments#bootstrap (26.2),
	// exclusive sets from data/minecraft/tags/enchantment/exclusive_set/*.json and each enchantment's exclusiveWith.
	private static final Set<String> ARMOR = ids("protection", "blast_protection", "fire_protection", "projectile_protection");
	private static final Set<String> BOOTS = ids("frost_walker", "depth_strider");
	private static final Set<String> BOW = ids("infinity", "mending");
	private static final Set<String> CROSSBOW = ids("multishot", "piercing");
	private static final Set<String> DAMAGE = ids("sharpness", "smite", "bane_of_arthropods", "impaling", "density", "breach");
	private static final Set<String> MINING = ids("fortune", "silk_touch");
	private static final Set<String> RIPTIDE = ids("loyalty", "channeling");

	static final Map<String, EnchantInfo> REGISTRY = new LinkedHashMap<>();
	static {
		add("protection", 4, 1, ARMOR);
		add("fire_protection", 4, 2, ARMOR);
		add("feather_falling", 4, 2, Set.of());
		add("blast_protection", 4, 4, ARMOR);
		add("projectile_protection", 4, 2, ARMOR);
		add("respiration", 3, 4, Set.of());
		add("aqua_affinity", 1, 4, Set.of());
		add("thorns", 3, 8, Set.of());
		add("depth_strider", 3, 4, BOOTS);
		add("frost_walker", 2, 4, BOOTS);
		add("soul_speed", 3, 8, Set.of());
		add("swift_sneak", 3, 8, Set.of());
		add("sharpness", 5, 1, DAMAGE);
		add("smite", 5, 2, DAMAGE);
		add("bane_of_arthropods", 5, 2, DAMAGE);
		add("knockback", 2, 2, Set.of());
		add("fire_aspect", 2, 4, Set.of());
		add("looting", 3, 4, Set.of());
		add("sweeping_edge", 3, 4, Set.of());
		add("efficiency", 5, 1, Set.of());
		add("silk_touch", 1, 8, MINING);
		add("unbreaking", 3, 2, Set.of());
		add("fortune", 3, 4, MINING);
		add("power", 5, 1, Set.of());
		add("punch", 2, 4, Set.of());
		add("flame", 1, 4, Set.of());
		add("infinity", 1, 8, BOW);
		add("luck_of_the_sea", 3, 4, Set.of());
		add("lure", 3, 4, Set.of());
		add("loyalty", 3, 2, Set.of());
		add("impaling", 5, 4, DAMAGE);
		add("riptide", 3, 4, RIPTIDE);
		add("lunge", 3, 2, Set.of());
		add("channeling", 1, 8, Set.of());
		add("multishot", 1, 4, CROSSBOW);
		add("quick_charge", 3, 2, Set.of());
		add("piercing", 4, 1, CROSSBOW);
		add("density", 5, 2, DAMAGE);
		add("breach", 4, 4, DAMAGE);
		add("wind_burst", 3, 4, Set.of());
		add("mending", 1, 4, Set.of());
	}

	/**
	 * The wiki guide's own orders for the same books (Tutorial:Best enchantments guide, read 2026-11-09), with the step costs it prints.
	 * "add X" = item + X book; "combine X Y" = X book + Y book, then item + that book (two printed costs).
	 */
	private static final Map<String, List<String>> WIKI_ORDER = Map.of(
			"helmet", List.of("add thorns", "combine respiration unbreaking", "combine mending aqua_affinity", "add protection"),
			"chestplate", List.of("add thorns", "combine unbreaking mending", "add protection"),
			"leggings", List.of("add swift_sneak", "combine thorns mending", "combine protection unbreaking"),
			"boots", List.of("add soul_speed", "combine thorns mending", "combine depth_strider feather_falling",
					"combine protection unbreaking"),
			"sword", List.of("add looting", "combine sweeping_edge unbreaking", "combine sharpness knockback", "combine fire_aspect mending"),
			"axe", List.of("add sharpness", "combine efficiency mending", "combine silk_touch unbreaking"),
			"bow", List.of("add power", "combine infinity unbreaking", "combine punch flame"),
			"other", List.of("add unbreaking", "add mending"));
	private static final Map<String, List<Integer>> WIKI_PRINTED = Map.of(
			"helmet", List.of(12, 3, 11, 2, 8, 14), "chestplate", List.of(12, 2, 7, 7), "leggings", List.of(12, 2, 16, 3, 11),
			"boots", List.of(12, 2, 16, 4, 14, 3, 15), "sword", List.of(6, 3, 11, 2, 11, 2, 14), "axe", List.of(5, 2, 8, 3, 11),
			"bow", List.of(5, 3, 9, 2, 10), "other", List.of(3, 3));

	/** Replays a wiki order through the game's cost formula; returns the real step costs. */
	static List<Integer> replay(String category, List<String> order) {
		Piece item = inputs(category, 0).get(0);
		List<Integer> costs = new ArrayList<>();
		for (String step : order) {
			String[] w = step.split(" ");
			Piece right = book(w[1]);
			if (w[0].equals("combine")) {
				AnvilCost.Result merged = AnvilCost.combine(book(w[1]), book(w[2]), REGISTRY, false, false);
				assertTrue(merged.valid(), step);
				costs.add(merged.cost());
				right = merged.result();
			}
			AnvilCost.Result r = AnvilCost.combine(item, right, REGISTRY, false, false);
			assertTrue(r.valid(), step);
			costs.add(r.cost());
			item = r.result();
		}
		return costs;
	}

	private static Piece book(String name) {
		String id = "minecraft:" + name;
		return new Piece("minecraft:enchanted_book", true, Map.of(id, REGISTRY.get(id).maxLevel()), 0, 0, 0, Set.of());
	}

	private static final List<String> CATEGORIES = List.of("helmet", "chestplate", "leggings", "boots", "sword", "axe", "bow",
			"crossbow", "trident", "mace", "spear", "pickaxe", "shovel", "hoe", "fishing_rod", "shears", "other");

	@Test
	void everyItemFreshMatchesTheRefereeAndNeverLosesToTheWiki() {
		StringBuilder report = new StringBuilder("item         planner referee finalRC  biggest  steps  wiki order\n");
		for (String category : CATEGORIES) {
			List<String> ids = Guide.priority(category);
			List<Piece> inputs = inputs(category, 0);
			Planner.Plan plan = Planner.plan(inputs, labels(ids), REGISTRY);
			assertNotNull(plan, category);
			int[] referee = referee(inputs);
			assertEquals(List.of(referee[0], referee[1], referee[2]), List.of(score(plan)[0], score(plan)[1], score(plan)[2]),
					category + ": {total, XP points, final prior work} is not the best");
			String wiki = "-";
			if (WIKI_ORDER.containsKey(category)) {
				List<Integer> real = replay(category, WIKI_ORDER.get(category));
				int realTotal = real.stream().mapToInt(Integer::intValue).sum();
				int printed = WIKI_PRINTED.get(category).stream().mapToInt(Integer::intValue).sum();
				assertTrue(plan.total() <= realTotal, category + ": the wiki order is cheaper");
				wiki = realTotal + (real.equals(WIKI_PRINTED.get(category)) ? "" : " (wiki prints " + printed + ": "
						+ WIKI_PRINTED.get(category) + ", formula gives " + real + ")");
			}
			report.append(String.format("%-12s %7d %7d %8d %8d  %s  %s%n", category, plan.total(), referee[0], score(plan)[2], score(plan)[3],
					plan.steps().stream().map(Planner.Step::cost).toList(), wiki));
		}
		System.out.print(report);
	}

	@Test
	void everyGuideItemPlansUnder100Milliseconds() {
		for (String category : CATEGORIES) { // warm up, as a running game already is
			Planner.plan(inputs(category, 0), labels(Guide.priority(category)), REGISTRY);
		}
		for (String category : CATEGORIES) {
			List<String> ids = Guide.priority(category);
			List<Piece> in = inputs(category, 0);
			List<Planner.Want> wants = new ArrayList<>();
			for (int i = 0; i < ids.size(); i++) {
				wants.add(new Planner.Want(ids.get(i), in.get(i + 1), ids.get(i)));
			}
			long start = System.nanoTime();
			Planner.planGuided(in.get(0), category, wants, REGISTRY);
			long ms = (System.nanoTime() - start) / 1_000_000;
			assertTrue(ms < 100, category + " took " + ms + " ms");
		}
	}

	@Test
	void everyItemWithPriorWorkMatchesTheReferee() {
		for (String category : CATEGORIES) {
			for (int uses = 1; uses <= 4; uses++) {
				int repairCost = (1 << uses) - 1;
				List<Piece> inputs = inputs(category, repairCost);
				Planner.Plan plan = Planner.plan(inputs, labels(Guide.priority(category)), REGISTRY);
				int[] referee = referee(inputs);
				String what = category + " with REPAIR_COST " + repairCost;
				if (referee == null) {
					assertEquals(null, plan, what + ": referee finds no plan within the step budget");
				} else {
					assertNotNull(plan, what);
					int[] got = score(plan);
					assertEquals(List.of(referee[0], referee[1], referee[2]), List.of(got[0], got[1], got[2]),
							what + ": {total, XP points, final prior work} is not the best");
				}
			}
		}
	}

	// --- inputs ---------------------------------------------------------------------------------------------------------

	static List<Piece> inputs(String category, int targetRepairCost) {
		List<String> ids = Guide.priority(category);
		List<Piece> inputs = new ArrayList<>();
		inputs.add(new Piece("minecraft:" + category, false, Map.of(), targetRepairCost, 0, 100, Set.copyOf(ids)));
		for (String id : ids) {
			EnchantInfo info = REGISTRY.get(id);
			assertNotNull(info, id);
			inputs.add(new Piece("minecraft:enchanted_book", true, Map.of(id, info.maxLevel()), 0, 0, 0, Set.of()));
		}
		return inputs;
	}

	static List<String> labels(List<String> ids) {
		List<String> labels = new ArrayList<>(List.of("item"));
		ids.forEach(labels::add);
		return labels;
	}

	// --- referee --------------------------------------------------------------------------------------------------------

	/**
	 * Best result over every sequence of allowed anvil steps (each at most MAX_STEP_COST) that ends in one piece, as
	 * {total, XP points, prior work of the finished item, biggest step}, compared in that order; null if no sequence fits.
	 * Dijkstra on (total, XP points, biggest step), which only grow along a sequence; every finished state with the lowest total
	 * and XP points is collected before the prior-work tie-break.
	 */
	static int[] referee(List<Piece> inputs) {
		Map<String, int[]> dist = new HashMap<>();
		PriorityQueue<Object[]> queue = new PriorityQueue<>((a, b) -> compare((int[]) a[0], (int[]) b[0]));
		queue.add(new Object[] {new int[] {0, 0, 0}, inputs});
		int[] best = null;
		while (!queue.isEmpty()) {
			Object[] top = queue.poll();
			int[] cost = (int[]) top[0];
			@SuppressWarnings("unchecked")
			List<Piece> pool = (List<Piece>) top[1];
			if (best != null && (cost[0] > best[0] || (cost[0] == best[0] && cost[1] > best[1]))) {
				break;
			}
			int[] known = dist.get(key(pool));
			if (known != null && compare(known, cost) < 0) {
				continue;
			}
			if (pool.size() == 1) {
				int[] done = {cost[0], cost[1], pool.get(0).repairCost(), cost[2]};
				if (best == null || done[2] < best[2] || (done[2] == best[2] && done[3] < best[3])) {
					best = done;
				}
				continue;
			}
			for (int i = 0; i < pool.size(); i++) {
				for (int j = 0; j < pool.size(); j++) {
					if (i == j) {
						continue;
					}
					AnvilCost.Result r = AnvilCost.combine(pool.get(i), pool.get(j), REGISTRY, false, false);
					if (!r.valid() || r.cost() > Planner.MAX_STEP_COST) {
						continue;
					}
					List<Piece> next = new ArrayList<>(pool);
					next.remove(Math.max(i, j));
					next.remove(Math.min(i, j));
					next.add(r.result());
					int[] c = {cost[0] + r.cost(), cost[1] + Planner.xpPoints(r.cost()), Math.max(cost[2], r.cost())};
					String k = key(next);
					int[] old = dist.get(k);
					if (old == null || compare(c, old) < 0) {
						dist.put(k, c);
						queue.add(new Object[] {c, next});
					}
				}
			}
		}
		return best;
	}

	private static int compare(int[] a, int[] b) {
		for (int i = 0; i < 3; i++) {
			if (a[i] != b[i]) {
				return Integer.compare(a[i], b[i]);
			}
		}
		return 0;
	}

	/** {total, XP points, prior work of the finished item, biggest step} of a plan. */
	static int[] score(Planner.Plan plan) {
		Planner.Step last = plan.steps().get(plan.steps().size() - 1);
		int finalRepairCost = AnvilCost.combine(last.leftPiece(), last.rightPiece(), REGISTRY, false, false).result().repairCost();
		return new int[] {plan.total(), plan.steps().stream().mapToInt(s -> Planner.xpPoints(s.cost())).sum(), finalRepairCost,
				plan.steps().stream().mapToInt(Planner.Step::cost).max().orElseThrow()};
	}

	private static String key(List<Piece> pool) {
		List<String> parts = new ArrayList<>();
		for (Piece p : pool) {
			parts.add((p.book() ? "B" : "I") + p.repairCost() + new TreeMap<>(p.enchants()));
		}
		parts.sort(null);
		return String.join("|", parts);
	}

	private static Set<String> ids(String... names) {
		Set<String> out = new java.util.HashSet<>();
		for (String n : names) {
			out.add("minecraft:" + n);
		}
		return Set.copyOf(out);
	}

	private static void add(String name, int maxLevel, int anvilCost, Set<String> exclusive) {
		String id = "minecraft:" + name;
		REGISTRY.put(id, new EnchantInfo(id, maxLevel, anvilCost, exclusive));
	}
}
