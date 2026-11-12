package xpsaver.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Picks which books (and other enchanted copies of the same item) to use, and the order, for the cheapest way to give an item the
 * best enchantments it can get from them.
 *
 * <ol>
 * <li><b>Goals.</b> For each wanted enchantment, highest priority first: the highest level the anvil can reach from the item and the
 * candidates, merging equal levels upward as the anvil does (Sharpness IV + IV = V). Wanted enchantments that conflict with the
 * item or with a higher-priority goal are skipped.</li>
 * <li><b>Useful candidates.</b> Every candidate that can contribute to some goal: it has the goal level, or a lower level that can
 * pair up into it. Candidates that can't help any goal are left out, since they could only add cost, and so are ones that only
 * serve a goal a plain one-enchantment book already covers.</li>
 * <li><b>Exact search</b> (Planner.exactTable) over every subset of the useful candidates: the cheapest plan whose finished item
 * reaches every goal, every step within Planner.maxStepCost. If the budget can't be met, goals are dropped from the
 * low-priority end, as before. Ties: lower total, then fewer XP points (Planner.xpPoints), then lower prior work on the item, then
 * fewer candidates used, then the smaller biggest step.</li>
 * </ol>
 * More than Planner.EXACT_LIMIT inputs can't all be checked in reasonable time: then only the cleanest candidate per goal (or the
 * merge chain it needs) is planned, and the result is marked approximate.
 */
public final class Selector {
	private Selector() {}

	/** Reach {@code level} of enchantment {@code id} on the finished item. */
	public record Goal(String id, int level) {}

	/**
	 * @param plan        the cheapest plan reaching every goal in {@code goals} (no steps if none could be reached)
	 * @param used        indices into the candidates that the plan uses, in candidate order
	 * @param goals       goals the plan reaches, highest priority first
	 * @param dropped     goals left out so every step fits Planner.maxStepCost
	 * @param conflicts   wanted ids some candidate has, skipped because they conflict with the item or a higher-priority goal
	 * @param approximate true if there were too many useful candidates to check every combination
	 */
	public record Result(Planner.Plan plan, List<Integer> used, List<Goal> goals, List<Goal> dropped, List<String> conflicts,
			boolean approximate) {}

	/**
	 * @param target     the item to enchant; it stays in the left slot throughout
	 * @param candidates enchanted books, and enchanted copies of the same item, that may be used
	 * @param wanted     enchantment ids, highest priority first
	 */
	public static Result choose(Piece target, String targetLabel, List<Piece> candidates, List<String> labels, List<String> wanted,
			Map<String, EnchantInfo> registry) {
		List<Integer> usable = new ArrayList<>();
		for (int i = 0; i < candidates.size(); i++) {
			Piece c = candidates.get(i);
			if (c.book() || (c.itemType().equals(target.itemType()) && target.damageable())) { // AnvilCost: what can go on the item
				usable.add(i);
			}
		}

		// 1. Goals.
		List<Goal> goals = new ArrayList<>();
		List<String> conflicts = new ArrayList<>();
		for (String id : wanted) {
			EnchantInfo info = registry.get(id);
			if (info == null || !(target.book() || target.supports().contains(id)) || goals.stream().anyMatch(g -> g.id().equals(id))) {
				continue;
			}
			boolean offered = usable.stream().anyMatch(i -> candidates.get(i).enchants().containsKey(id));
			boolean clash = target.enchants().keySet().stream().anyMatch(o -> clash(registry, id, o))
					|| goals.stream().anyMatch(g -> clash(registry, id, g.id()));
			if (clash) {
				if (offered && !conflicts.contains(id)) {
					conflicts.add(id);
				}
				continue;
			}
			int current = target.enchants().getOrDefault(id, 0);
			List<Integer> levels = new ArrayList<>();
			for (int i : usable) {
				levels.add(candidates.get(i).enchants().getOrDefault(id, 0));
			}
			int reach = reachable(current, levels, info.maxLevel());
			if (reach > current) {
				goals.add(new Goal(id, reach));
			}
		}
		if (goals.isEmpty()) {
			return new Result(new Planner.Plan(List.of(), 0, false, List.of()), List.of(), List.of(), List.of(), conflicts, false);
		}

		// 2. Useful candidates: every one that can contribute to some goal (contributes(...)), so the search below can find the
		// cheapest mix. Only exact duplicates beyond what a merge could use are left out.
		Set<String> goalIds = new java.util.HashSet<>();
		goals.forEach(g -> goalIds.add(g.id()));
		Comparator<Integer> cleaner = Comparator.comparingInt((Integer i) -> extras(candidates.get(i), goalIds, target))
				.thenComparingInt(i -> candidates.get(i).enchants().size()).thenComparingInt(i -> candidates.get(i).repairCost());
		Set<Integer> useful = new LinkedHashSet<>();
		Set<Integer> cleanest = new LinkedHashSet<>(); // fallback when there are too many: one per goal, or a merge chain
		Map<Integer, Set<String>> helpsWith = new HashMap<>(); // candidate -> goals it can contribute to
		for (Goal g : goals) {
			int max = registry.get(g.id()).maxLevel();
			int current = target.enchants().getOrDefault(g.id(), 0);
			List<Integer> levels = new ArrayList<>();
			usable.forEach(i -> levels.add(candidates.get(i).enchants().getOrDefault(g.id(), 0)));
			boolean[] helps = contributes(current, levels, g.level(), max);
			Map<Piece, Integer> copies = new HashMap<>();
			for (int k = 0; k < usable.size(); k++) {
				int level = levels.get(k);
				int i = usable.get(k);
				if (level > 0 && helps[Math.min(level, max)]) {
					helpsWith.computeIfAbsent(i, x -> new java.util.HashSet<>()).add(g.id());
					int allowed = level >= g.level() ? 1 : 1 << Math.min(20, g.level() - level); // copies a merge chain could use
					if (copies.merge(candidates.get(i), 1, Integer::sum) <= allowed) {
						useful.add(i);
					}
				}
			}
			List<Integer> high = new ArrayList<>(usable.stream().filter(i -> candidates.get(i).enchants().getOrDefault(g.id(), 0) >= g.level()).toList());
			high.sort(cleaner);
			if (!high.isEmpty()) {
				cleanest.add(high.get(0));
			} else {
				cleanest.addAll(chain(g, target, candidates, usable, cleaner));
			}
		}

		// A book that has a goal's level and helps no other goal is never needed when a plain book (that enchantment only, same
		// level, since the goal is the highest reachable) with no more prior work is there: wherever the first one sits in a plan,
		// the plain one costs the same for that enchantment and less or nothing for the rest. Lower-level books stay: merging them
		// up can be cheaper (Knockback I onto an item that has I costs no more than II, SelectorTest round 179).
		for (Goal g : goals) {
			Integer plain = null;
			for (int i : useful) {
				Piece c = candidates.get(i);
				if (c.book() && c.enchants().size() == 1 && c.enchants().getOrDefault(g.id(), 0) >= g.level()
						&& (plain == null || c.repairCost() < candidates.get(plain).repairCost())) {
					plain = i;
				}
			}
			if (plain != null) {
				int keep = plain;
				int rc = candidates.get(plain).repairCost();
				useful.removeIf(i -> i != keep && candidates.get(i).book() && candidates.get(i).enchants().getOrDefault(g.id(), 0) >= g.level()
						&& helpsWith.get(i).equals(Set.of(g.id())) && candidates.get(i).repairCost() >= rc);
			}
		}

		// 3. Exact search over every subset of the useful candidates.
		// Too many to check every combination: plan the cleanest ones per goal instead, and say the result may not be the cheapest.
		List<Integer> pool = new ArrayList<>(useful);
		boolean approximate = pool.size() + 1 > Planner.EXACT_LIMIT;
		if (approximate) {
			pool = new ArrayList<>(cleanest);
		}
		pool.sort(null);
		List<Piece> inputs = new ArrayList<>(List.of(target));
		List<String> inputLabels = new ArrayList<>(List.of(targetLabel));
		for (int i : pool) {
			inputs.add(candidates.get(i));
			inputLabels.add(labels.get(i));
		}
		if (pool.size() + 1 > Planner.EXACT_LIMIT) {
			return approximateResult(target, targetLabel, goals, pool, candidates, labels, registry, conflicts);
		}
		List<Map<Planner.Key, Planner.Node>> table = Planner.exactTable(inputs, inputLabels, registry);
		for (int keep = goals.size(); keep > 0; keep--) {
			List<Goal> kept = goals.subList(0, keep);
			Planner.Node best = null;
			int bestMask = 0;
			for (int mask = 1; mask < table.size(); mask += 2) { // odd masks: the item is in
				if (mask == 1) {
					continue;
				}
				for (Planner.Node node : table.get(mask).values()) {
					if (reaches(node.piece(), kept) && (best == null || better(node, mask, best, bestMask))) {
						best = node;
						bestMask = mask;
					}
				}
			}
			if (best != null) {
				List<Integer> used = new ArrayList<>();
				for (int b = 1; b < inputs.size(); b++) {
					if ((bestMask & (1 << b)) != 0) {
						used.add(pool.get(b - 1));
					}
				}
				Planner.Plan plan = Planner.toPlan(best, approximate, List.of());
				return new Result(plan, used, List.copyOf(kept), List.copyOf(goals.subList(keep, goals.size())), conflicts, approximate);
			}
		}
		return new Result(new Planner.Plan(List.of(), 0, approximate, List.of()), List.of(), List.of(), List.copyOf(goals), conflicts,
				approximate);
	}

	/** Too many candidates even after trimming: plan the cleanest one per goal with the planner's greedy fallback. */
	private static Result approximateResult(Piece target, String targetLabel, List<Goal> goals, List<Integer> pool, List<Piece> candidates,
			List<String> labels, Map<String, EnchantInfo> registry, List<String> conflicts) {
		List<Planner.Want> wants = new ArrayList<>();
		for (Goal g : goals) {
			pool.stream().filter(i -> candidates.get(i).enchants().containsKey(g.id())).findFirst()
					.ifPresent(i -> wants.add(new Planner.Want(g.id(), candidates.get(i), labels.get(i))));
		}
		Planner.Plan plan = Planner.planGuided(target, targetLabel, wants, registry);
		List<Integer> used = new ArrayList<>();
		for (Planner.Want w : wants) {
			if (!plan.dropped().contains(w.enchantId())) {
				int i = candidates.indexOf(w.book());
				if (!used.contains(i)) {
					used.add(i);
				}
			}
		}
		used.sort(null);
		List<Goal> kept = goals.stream().filter(g -> !plan.dropped().contains(g.id())).toList();
		List<Goal> dropped = goals.stream().filter(g -> plan.dropped().contains(g.id())).toList();
		return new Result(new Planner.Plan(plan.steps(), plan.total(), true, List.of()), used, kept, dropped, conflicts, true);
	}

	/** Lower total, then fewer XP points (as enchantmenttable.com ranks), then lower prior work, fewer candidates used, smaller biggest step. */
	private static boolean better(Planner.Node a, int aMask, Planner.Node b, int bMask) {
		if (a.total() != b.total()) {
			return a.total() < b.total();
		}
		if (a.xp() != b.xp()) {
			return a.xp() < b.xp();
		}
		if (a.piece().repairCost() != b.piece().repairCost()) {
			return a.piece().repairCost() < b.piece().repairCost();
		}
		if (Integer.bitCount(aMask) != Integer.bitCount(bMask)) {
			return Integer.bitCount(aMask) < Integer.bitCount(bMask);
		}
		return a.maxStep() < b.maxStep();
	}

	private static boolean reaches(Piece piece, List<Goal> goals) {
		for (Goal g : goals) {
			if (piece.enchants().getOrDefault(g.id(), 0) < g.level()) {
				return false;
			}
		}
		return true;
	}

	private static boolean clash(Map<String, EnchantInfo> registry, String a, String b) {
		EnchantInfo x = registry.get(a);
		EnchantInfo y = registry.get(b);
		return !a.equals(b) && x != null && y != null && !EnchantInfo.areCompatible(x, y);
	}

	/** Enchantments on {@code p} that are neither goals nor already on the item at that level or higher: they would cost extra. */
	private static int extras(Piece p, Set<String> goalIds, Piece target) {
		int n = 0;
		for (Map.Entry<String, Integer> e : p.enchants().entrySet()) {
			if (!goalIds.contains(e.getKey()) && target.enchants().getOrDefault(e.getKey(), 0) < e.getValue()) {
				n++;
			}
		}
		return n;
	}

	/**
	 * Highest level the anvil can reach for one enchantment from the item's level and the candidates' levels (0 = absent): two equal
	 * levels make one level higher, a higher level wins, nothing goes past {@code max}.
	 */
	static int reachable(int current, List<Integer> levels, int max) {
		int[] count = new int[max + 2];
		if (current > 0) {
			count[Math.min(current, max)]++;
		}
		for (int l : levels) {
			if (l > 0) {
				count[Math.min(l, max)]++;
			}
		}
		for (int l = 1; l < max; l++) {
			count[l + 1] += count[l] / 2;
		}
		for (int l = max; l >= 1; l--) {
			if (count[l] > 0) {
				return l;
			}
		}
		return 0;
	}

	/**
	 * Which levels of one enchantment can help reach {@code goal}: the goal level and above, and a lower level only if pieces at it can
	 * pair up (counting the item's own level and pairs made from lower levels) into a level that helps. A piece whose level can't pair
	 * with anything ends up below the goal whatever order is used, so it can only cost levels.
	 */
	static boolean[] contributes(int current, List<Integer> levels, int goal, int max) {
		int[] count = new int[max + 2];
		if (current > 0) {
			count[Math.min(current, max)]++;
		}
		for (int l : levels) {
			if (l > 0) {
				count[Math.min(l, max)]++;
			}
		}
		int[] withCarry = count.clone();
		for (int l = 1; l < max; l++) {
			withCarry[l + 1] += withCarry[l] / 2;
		}
		boolean[] helps = new boolean[max + 2];
		for (int l = max; l >= 1; l--) {
			helps[l] = l >= goal || (helps[l + 1] && withCarry[l] >= 2);
		}
		return helps;
	}

	/**
	 * Candidates a merge chain needs to build {@code g.level()} from lower levels: two at the level below, each missing one made from two
	 * more below that, and so on. The item's own level counts as one piece at its level. Cleanest candidates first at each level.
	 */
	private static List<Integer> chain(Goal g, Piece target, List<Piece> candidates, List<Integer> usable, Comparator<Integer> cleaner) {
		List<Integer> out = new ArrayList<>();
		int current = target.enchants().getOrDefault(g.id(), 0);
		int need = 2;
		for (int level = g.level() - 1; level >= 1 && need > 0; level--) {
			if (current == level) {
				need--;
			}
			int at = level;
			List<Integer> here = new ArrayList<>(usable.stream().filter(i -> candidates.get(i).enchants().getOrDefault(g.id(), 0) == at).toList());
			here.sort(cleaner);
			int take = Math.min(need, here.size());
			out.addAll(here.subList(0, take));
			need = (need - take) * 2;
		}
		return out;
	}
}
