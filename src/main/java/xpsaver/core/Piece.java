package xpsaver.core;

import java.util.Map;
import java.util.Set;

/**
 * One anvil input (an item or an enchanted book), reduced to what AnvilMenu.createResult reads.
 *
 * @param itemType   registry id of the item; two non-book items combine only if equal
 * @param book       true for an enchanted book (has STORED_ENCHANTMENTS)
 * @param enchants   enchantment id -> level, in the order the game iterates them
 * @param repairCost value of the REPAIR_COST component (0, 1, 3, 7, ... = 2^n - 1 after n anvil uses)
 * @param damage     current damage (0 if not damageable)
 * @param maxDamage  max damage (0 if not damageable)
 * @param supports   enchantment ids this item accepts (Enchantment.canEnchant); ignored for books
 */
public record Piece(String itemType, boolean book, Map<String, Integer> enchants, int repairCost,
		int damage, int maxDamage, Set<String> supports) {

	public boolean damageable() {
		return maxDamage > 0;
	}
}
