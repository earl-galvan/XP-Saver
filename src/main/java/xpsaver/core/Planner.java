package xpsaver.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cheapest order of anvil combines that merges a target item with a set of books, never letting one anvil step cost more than
 * {@link #MAX_STEP_COST}. Up to {@link #EXACT_LIMIT} inputs: exact search over every merge tree (book + book merges included),
 * memoized per subset of inputs and per resulting prior-work/enchantments, since only those decide later prices.
 * Above that: greedy, marked approximate. Every price comes from {@link AnvilCost#combine}.
 *
 * <p>Among plans with the lowest total, the exact search prefers (1) the lowest prior-work penalty on the finished item, so
 * later repairs and renames stay cheap, then (2) the smallest single step, so fewer levels are needed on hand at once.
 * Both only break ties: they never raise the total.
 */
public final class Planner {
	/** Most levels any single anvil step may cost. Vanilla refuses 40+; this is a stricter, player-chosen budget. */
	public static final int MAX_STEP_COST = 29;
	/**
	 * The budget plans use: {@link #MAX_STEP_COST} unless the player changes it in the config screen (1 to 39). Read once per
	 * step check; planning runs on a background thread, hence volatile.
	 */
	public static volatile int maxStepCost = MAX_STEP_COST;
	/** Most inputs (item included) searched exactly: 12 takes about half a second on one core (SelectorTest), on a background thread. */
	public static final int EXACT_LIMIT = 12;

	/** @param leftPiece / rightPiece the exact inputs of this step (book, item, or the result of an earlier step) */
	public record Step(int number, String left, String right, int cost, Piece leftPiece, Piece rightPiece) {}

	/** @param dropped enchantment ids left out so every step fits {@link #maxStepCost}, lowest priority first dropped */
	public record Plan(List<Step> steps, int total, boolean approximate, List<String> dropped) {}

	/** One wanted enchantment and the book that provides it. */
	public record Want(String enchantId, Piece book, String label) {}

	/**
	 * @param xp      XP points the subtree's steps cost if each is paid from level 0 ({@link #xpPoints}), summed
	 * @param maxStep the most expensive single step in this subtree
	 */
	record Node(Piece piece, int total, int xp, int maxStep, Node left, Node right, int stepCost, String label, boolean hasTarget) {}

	/**
	 * Experience points needed to go from level 0 to {@code levels}: the sum of Player#getXpNeededForNextLevel (26.3) over those levels.
	 * Ties in total levels are broken by these, as enchantmenttable.com's calculator does: of two orders with the same levels, the
	 * one with smaller steps costs less experience to earn (30 levels at once cost far more than three times 10).
	 */
	public static int xpPoints(int levels) {
		int points = 0;
		for (int level = 0; level < levels; level++) {
			points += level >= 30 ? 112 + (level - 30) * 9 : level >= 15 ? 37 + (level - 15) * 5 : 7 + level * 2;
		}
		return points;
	}

	/** Within one subset and one resulting piece: lower total, then fewer XP points, then smaller biggest step. Exact, since all add up. */
	private static boolean cheaper(Node a, Node b) {
		if (a.total != b.total) {
			return a.total < b.total;
		}
		return a.xp != b.xp ? a.xp < b.xp : a.maxStep < b.maxStep;
	}

	/** Final choice: lower total, then fewer XP points, then lower prior work on the finished item, then smaller biggest step. */
	static int finalOrder(Node a, Node b) {
		if (a.total != b.total) {
			return Integer.compare(a.total, b.total);
		}
		if (a.xp != b.xp) {
			return Integer.compare(a.xp, b.xp);
		}
		if (a.piece.repairCost() != b.piece.repairCost()) {
			return Integer.compare(a.piece.repairCost(), b.piece.repairCost());
		}
		return Integer.compare(a.maxStep, b.maxStep);
	}

	/** What a subtree leaves behind, for memoizing; the hash is computed once (enchantment maps are hashed entry by entry). */
	record Key(int repairCost, Map<String, Integer> enchants, int hash) {
		Key(int repairCost, Map<String, Integer> enchants) {
			this(repairCost, enchants, 31 * repairCost + enchants.hashCode());
		}

		@Override
		public int hashCode() {
			return hash;
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Key k && k.hash == hash && k.repairCost == repairCost && k.enchants.equals(enchants);
		}
	}

	private Planner() {}

	/**
	 * @param inputs index 0 is the target item, the rest are books
	 * @param labels display name for each input
	 * @return the cheapest plan with every step within {@link #maxStepCost}, or null if none exists
	 */
	public static Plan plan(List<Piece> inputs, List<String> labels, Map<String, EnchantInfo> registry) {
		boolean approximate = inputs.size() > EXACT_LIMIT;
		Node root = approximate ? greedy(inputs, labels, registry) : exact(inputs, labels, registry);
		return root == null ? null : toPlan(root, approximate, List.of());
	}

	/**
	 * Plans {@code wants} (highest priority first) onto {@code target}. If no plan keeps every step within {@link #maxStepCost},
	 * drops wanted enchantments from the low-priority end, one at a time, until one does.
	 * A book shared by several wants stays in while any of its kept wants needs it.
	 *
	 * @return the plan for the kept wants (no steps if nothing could be applied), with the dropped enchantment ids
	 */
	public static Plan planGuided(Piece target, String targetLabel, List<Want> wants, Map<String, EnchantInfo> registry) {
		// Inputs for every want; index 0 is the target. A book shared by several wants is one input.
		List<Piece> inputs = new ArrayList<>(List.of(target));
		List<String> labels = new ArrayList<>(List.of(targetLabel));
		int[] inputOf = new int[wants.size()];
		for (int w = 0; w < wants.size(); w++) {
			int found = -1;
			for (int i = 1; i < inputs.size(); i++) {
				if (inputs.get(i) == wants.get(w).book()) {
					found = i;
				}
			}
			if (found < 0) {
				inputs.add(wants.get(w).book());
				labels.add(wants.get(w).label());
				found = inputs.size() - 1;
			}
			inputOf[w] = found;
		}
		// One exact search covers every "keep the first k wants" case: it already holds the best plan for each subset of inputs.
		List<Map<Key, Node>> table = inputs.size() <= EXACT_LIMIT ? exactTable(inputs, labels, registry) : null;
		for (int keep = wants.size(); keep > 0; keep--) {
			Plan plan;
			if (table != null) {
				int mask = 1;
				for (int w = 0; w < keep; w++) {
					mask |= 1 << inputOf[w];
				}
				Node root = table.get(mask).values().stream().min(Planner::finalOrder).orElse(null);
				plan = root == null ? null : toPlan(root, false, List.of());
			} else {
				List<Piece> kept = new ArrayList<>(List.of(target));
				List<String> keptLabels = new ArrayList<>(List.of(targetLabel));
				for (int w = 0; w < keep; w++) {
					Piece book = inputs.get(inputOf[w]);
					if (kept.stream().noneMatch(p -> p == book)) { // shared book once
						kept.add(book);
						keptLabels.add(labels.get(inputOf[w]));
					}
				}
				plan = plan(kept, keptLabels, registry);
			}
			if (plan != null) {
				List<String> dropped = wants.subList(keep, wants.size()).stream().map(Want::enchantId).toList();
				return new Plan(plan.steps(), plan.total(), plan.approximate(), dropped);
			}
		}
		return new Plan(List.of(), 0, false, wants.stream().map(Want::enchantId).toList());
	}

	private static Node exact(List<Piece> inputs, List<String> labels, Map<String, EnchantInfo> registry) {
		int full = (1 << inputs.size()) - 1;
		return exactTable(inputs, labels, registry).get(full).values().stream().min(Planner::finalOrder).orElse(null);
	}

	/** Best subtree per subset of inputs (bit i = input i) and per resulting piece. Bit 0, the target, is never a right input. */
	static List<Map<Key, Node>> exactTable(List<Piece> inputs, List<String> labels, Map<String, EnchantInfo> registry) {
		int n = inputs.size();
		int full = (1 << n) - 1;
		List<Map<Key, Node>> best = new ArrayList<>(full + 1);
		for (int mask = 0; mask <= full; mask++) {
			best.add(new HashMap<>());
		}
		for (int i = 0; i < n; i++) {
			Piece p = inputs.get(i);
			best.get(1 << i).put(new Key(p.repairCost(), p.enchants()), new Node(p, 0, 0, 0, null, null, 0, labels.get(i), i == 0));
		}
		// Submasks are numerically smaller than their mask, so increasing order visits parts before wholes.
		// Two subtrees with the same inputs and the same resulting piece behave identically from here on,
		// so keeping only the cheaper one per Key loses no plan.
		for (int mask = 1; mask <= full; mask++) {
			if (Integer.bitCount(mask) < 2) {
				continue;
			}
			Map<Key, Node> out = best.get(mask);
			for (int sub = (mask - 1) & mask; sub > 0; sub = (sub - 1) & mask) {
				int rest = mask ^ sub;
				if ((rest & 1) != 0) {
					continue; // the target item must stay on the left; a book cannot take an item
				}
				for (Node l : best.get(sub).values()) {
					for (Node r : best.get(rest).values()) {
						Node merged = merge(l, r, registry);
						if (merged != null) {
							Key key = new Key(merged.piece.repairCost(), merged.piece.enchants());
							Node old = out.get(key);
							if (old == null || cheaper(merged, old)) {
								out.put(key, merged);
							}
						}
					}
				}
			}
			prune(out);
		}
		return best;
	}

	/**
	 * Drops subtrees that can never win: same inputs and enchantments as another, but higher prior work with at least its total,
	 * XP points and biggest step. Higher prior work only raises every later price and the final penalty, so the other one is never
	 * worse.
	 */
	private static void prune(Map<Key, Node> nodes) {
		if (nodes.size() < 2) {
			return;
		}
		List<Node> list = new ArrayList<>(nodes.values());
		for (Node a : list) {
			for (Node b : list) {
				if (a != b && b.piece.repairCost() > a.piece.repairCost() && b.total >= a.total && b.xp >= a.xp && b.maxStep >= a.maxStep
						&& b.piece.enchants().equals(a.piece.enchants())) {
					nodes.remove(new Key(b.piece.repairCost(), b.piece.enchants()));
				}
			}
		}
	}

	private static Node greedy(List<Piece> inputs, List<String> labels, Map<String, EnchantInfo> registry) {
		List<Node> pool = new ArrayList<>();
		for (int i = 0; i < inputs.size(); i++) {
			Piece p = inputs.get(i);
			pool.add(new Node(p, 0, 0, 0, null, null, 0, labels.get(i), i == 0));
		}
		while (pool.size() > 1) {
			Node pick = null;
			int pickL = -1;
			int pickR = -1;
			for (int i = 0; i < pool.size(); i++) {
				for (int j = 0; j < pool.size(); j++) {
					if (i == j || pool.get(j).hasTarget) {
						continue;
					}
					Node m = merge(pool.get(i), pool.get(j), registry);
					if (m != null && (pick == null || better(m, pick))) {
						pick = m;
						pickL = i;
						pickR = j;
					}
				}
			}
			if (pick == null) {
				return null;
			}
			pool.remove(Math.max(pickL, pickR));
			pool.remove(Math.min(pickL, pickR));
			pool.add(pick);
		}
		return pool.get(0);
	}

	/**
	 * Greedy choice, shaped like the exact plans: lowest resulting prior work first (balanced merges keep penalties small),
	 * then a merge into the target item (it absorbs book bundles of matching prior work: 1 book, 2, 4, ...), then the cheaper step.
	 * Picking the cheapest step first chains everything onto one piece and dead-ends under the step budget.
	 */
	private static boolean better(Node a, Node b) {
		if (a.piece.repairCost() != b.piece.repairCost()) {
			return a.piece.repairCost() < b.piece.repairCost();
		}
		if (a.hasTarget != b.hasTarget) {
			return a.hasTarget;
		}
		return a.stepCost < b.stepCost;
	}

	/** A step is allowed only if the game accepts it and it costs at most {@link #maxStepCost}. */
	private static Node merge(Node l, Node r, Map<String, EnchantInfo> registry) {
		int budget = maxStepCost;
		// Any valid step costs at least both prior-work penalties plus 1, so skip pairs that are over budget before pricing them.
		if ((long) l.piece.repairCost() + r.piece.repairCost() + 1 > budget) {
			return null;
		}
		AnvilCost.Result res = AnvilCost.combine(l.piece, r.piece, registry, false, false);
		if (!res.valid() || res.cost() > budget) {
			return null;
		}
		return new Node(res.result(), l.total + r.total + res.cost(), l.xp + r.xp + xpPoints(res.cost()),
				Math.max(res.cost(), Math.max(l.maxStep, r.maxStep)), l, r,
				res.cost(), null, l.hasTarget || r.hasTarget);
	}

	static Plan toPlan(Node root, boolean approximate, List<String> dropped) {
		List<Step> steps = new ArrayList<>();
		walk(root, steps);
		return new Plan(steps, root.total, approximate, dropped);
	}

	/** Post-order: both inputs of a step are ready before it. Returns the label for this node's output. */
	private static String walk(Node node, List<Step> steps) {
		if (node.left == null) {
			return node.label;
		}
		String left = walk(node.left, steps);
		String right = walk(node.right, steps);
		int number = steps.size() + 1;
		steps.add(new Step(number, left, right, node.stepCost, node.left.piece, node.right.piece));
		return "result of step " + number;
	}
}
