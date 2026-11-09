package xpsaver.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The vanilla anvil price for putting {@code right} onto {@code left}.
 * Line-by-line port of net.minecraft.world.inventory.AnvilMenu#createResult (Minecraft 26.2, decompiled with Loom genSources),
 * limited to the enchantment and item-sacrifice paths.
 * shortcut: material repair (e.g. diamonds on a diamond sword) is not modelled; AnvilMark uses the game's own displayed
 * cost for that case and the planner never repairs.
 */
public final class AnvilCost {
	/** AnvilMenu: {@code if (this.cost.get() >= 40 && !player.hasInfiniteMaterials()) result = EMPTY}. */
	public static final int TOO_EXPENSIVE = 40;

	public enum Status { OK, INVALID_NO_CHANGE, INVALID_ITEMS, INVALID_CONFLICT, TOO_EXPENSIVE }

	/**
	 * @param cost   levels the game charges (meaningful for OK and TOO_EXPENSIVE)
	 * @param result the combined piece (null when invalid)
	 */
	public record Result(Status status, int cost, Piece result) {
		public boolean valid() {
			return status == Status.OK;
		}
	}

	private AnvilCost() {}

	/**
	 * @param registry   enchantment id -> data, read from the game registry at runtime
	 * @param renaming   true if the name field differs from the item's name (+1 level)
	 * @param creative   player.hasInfiniteMaterials(): ignores item support and the 40-level cap
	 */
	public static Result combine(Piece left, Piece right, Map<String, EnchantInfo> registry, boolean renaming, boolean creative) {
		long tax = (long) left.repairCost() + right.repairCost();
		int price = 0;
		int damage = left.damage();
		Map<String, Integer> enchants = new LinkedHashMap<>(left.enchants());

		// AnvilMenu: a non-book addition must be the same damageable item.
		if (!right.book() && (!left.itemType().equals(right.itemType()) || !left.damageable())) {
			return new Result(Status.INVALID_ITEMS, 0, null);
		}
		if (left.damageable() && !right.book()) {
			int remaining1 = left.maxDamage() - left.damage();
			int remaining2 = right.maxDamage() - right.damage();
			int additional = remaining2 + left.maxDamage() * 12 / 100;
			int resultDamage = Math.max(0, left.maxDamage() - (remaining1 + additional));
			if (resultDamage < damage) {
				damage = resultDamage;
				price += 2; // COST_REPAIR_SACRIFICE
			}
		}

		boolean anyCompatible = false;
		boolean anyNotCompatible = false;
		for (Map.Entry<String, Integer> entry : right.enchants().entrySet()) {
			EnchantInfo info = registry.get(entry.getKey());
			if (info == null) {
				continue; // unknown id: nothing we can price
			}
			int current = enchants.getOrDefault(info.id(), 0);
			int level = entry.getValue();
			level = current == level ? level + 1 : Math.max(level, current);
			boolean compatible = left.book() || creative || left.supports().contains(info.id());
			for (String other : enchants.keySet()) {
				EnchantInfo otherInfo = registry.get(other);
				if (!other.equals(info.id()) && otherInfo != null && !EnchantInfo.areCompatible(info, otherInfo)) {
					compatible = false;
					price++; // COST_INCOMPATIBLE_PENALTY, once per conflicting enchantment already on the result
				}
			}
			if (!compatible) {
				anyNotCompatible = true;
			} else {
				anyCompatible = true;
				level = Math.min(level, info.maxLevel());
				enchants.put(info.id(), level);
				int fee = info.anvilCost();
				if (right.book()) {
					fee = Math.max(1, fee / 2);
				}
				price += fee * level;
				// shortcut: AnvilMenu also sets price = 40 when the left stack has count > 1. The planner only plans single
				// items and AnvilMark trusts the game's result for the current order, so only its swap estimate could miss this.
			}
		}
		if (anyNotCompatible && !anyCompatible) {
			return new Result(Status.INVALID_CONFLICT, 0, null);
		}

		int naming = renaming ? 1 : 0;
		price += naming;
		if (price <= 0) {
			return new Result(Status.INVALID_NO_CHANGE, 0, null);
		}
		int cost = (int) Math.min(tax + price, Integer.MAX_VALUE);
		boolean onlyRenaming = naming == price;
		if (onlyRenaming && cost >= TOO_EXPENSIVE) {
			cost = TOO_EXPENSIVE - 1;
		}
		int newRepairCost = Math.max(left.repairCost(), right.repairCost());
		if (!onlyRenaming) {
			newRepairCost = (int) Math.min(newRepairCost * 2L + 1L, Integer.MAX_VALUE); // calculateIncreasedRepairCost
		}
		Piece result = new Piece(left.itemType(), left.book(), enchants, newRepairCost, damage, left.maxDamage(), left.supports());
		Status status = cost >= TOO_EXPENSIVE && !creative ? Status.TOO_EXPENSIVE : Status.OK;
		return new Result(status, cost, result);
	}
}
