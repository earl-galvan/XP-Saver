package xpsaver.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What a book's enchantments would do to an item that may already be enchanted, by the anvil's own rule
 * (AnvilMenu#createResult, 26.2): a book level equal to the item's goes up by one (Sharpness IV + IV = V), a higher one replaces it,
 * a lower one changes nothing, and nothing goes past the max level. The anvil still charges for an enchantment that changes nothing.
 */
public final class Upgrade {
	public enum Kind {
		/** The item doesn't have it yet. */
		ADDS,
		/** The item has a lower level, or the same level below max (equal levels merge one higher). */
		RAISES,
		/** The book's level is below the item's: changes nothing on its own (only after merging books up). */
		LOWER,
		/** The item already has it at max level: can never help. */
		MAXED,
		/** Conflicts with an enchantment already on the item: can never be added. */
		CONFLICTS,
		/** This item can't take it at all. */
		UNSUPPORTED
	}

	/**
	 * @param from         level on the item now (0 if absent)
	 * @param to           level after the anvil step (equal to {@code from} unless ADDS or RAISES)
	 * @param conflictWith for CONFLICTS: the enchantment on the item that blocks it
	 */
	public record Effect(String id, Kind kind, int from, int to, String conflictWith) {
		public boolean helps() {
			return kind == Kind.ADDS || kind == Kind.RAISES;
		}

		/** True if this book could still help after merging with other books (lower levels can be merged up). */
		public boolean couldHelp() {
			return helps() || kind == Kind.LOWER;
		}
	}

	private Upgrade() {}

	/** One effect per enchantment on {@code book}, in the book's order, judged against {@code item} as it is now. */
	public static List<Effect> of(Piece item, Piece book, Map<String, EnchantInfo> registry) {
		List<Effect> out = new ArrayList<>();
		for (Map.Entry<String, Integer> e : book.enchants().entrySet()) {
			out.add(effect(item, e.getKey(), e.getValue(), registry));
		}
		return out;
	}

	/** What {@code id} at {@code level} would do to {@code item}. */
	public static Effect effect(Piece item, String id, int level, Map<String, EnchantInfo> registry) {
		EnchantInfo info = registry.get(id);
		int current = item.enchants().getOrDefault(id, 0);
		if (info == null || !(item.book() || item.supports().contains(id))) {
			return new Effect(id, Kind.UNSUPPORTED, current, current, null);
		}
		for (String other : item.enchants().keySet()) {
			EnchantInfo otherInfo = registry.get(other);
			if (!other.equals(id) && otherInfo != null && !EnchantInfo.areCompatible(info, otherInfo)) {
				return new Effect(id, Kind.CONFLICTS, current, current, other);
			}
		}
		if (current >= info.maxLevel()) {
			return new Effect(id, Kind.MAXED, current, current, null);
		}
		int to = Math.min(current == level ? level + 1 : Math.max(level, current), info.maxLevel());
		if (current == 0) {
			return new Effect(id, Kind.ADDS, 0, to, null);
		}
		return to > current ? new Effect(id, Kind.RAISES, current, to, null) : new Effect(id, Kind.LOWER, current, current, null);
	}

	/** True if the book adds or raises at least one enchantment on the item by itself. */
	public static boolean helps(Piece item, Piece book, Map<String, EnchantInfo> registry) {
		return of(item, book, registry).stream().anyMatch(Effect::helps);
	}

	/** True if the book could add something, alone or after merging with other books; false means it can only waste levels. */
	public static boolean couldHelp(Piece item, Piece book, Map<String, EnchantInfo> registry) {
		return of(item, book, registry).stream().anyMatch(Effect::couldHelp);
	}
}
