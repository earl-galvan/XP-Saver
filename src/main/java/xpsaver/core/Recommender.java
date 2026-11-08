package xpsaver.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits an item's supported enchantments into groups that conflict with each other.
 * A group of one is a plain recommendation; a larger group is a matter of taste (e.g. Sharpness / Smite / Bane of Arthropods),
 * so every option is shown and labelled. Conflicts come from the registry via EnchantInfo.areCompatible.
 */
public final class Recommender {
	public record Group(List<EnchantInfo> options) {
		public boolean choice() {
			return options.size() > 1;
		}

		/**
		 * The ways to choose within this group: every largest set of options that can sit on one item together.
		 * Usually one option each (Sharpness / Smite / Bane), but Riptide conflicts with Loyalty and with Channeling while those two
		 * go together, so the trident's picks are [Riptide] and [Loyalty, Channeling].
		 */
		public List<List<EnchantInfo>> picks() {
			int n = options.size();
			List<Integer> compatible = new ArrayList<>();
			for (int mask = 1; mask < (1 << n); mask++) {
				if (allCompatible(mask)) {
					compatible.add(mask);
				}
			}
			List<List<EnchantInfo>> out = new ArrayList<>();
			for (int mask : compatible) {
				boolean maximal = compatible.stream().noneMatch(other -> other != mask && (other & mask) == mask);
				if (maximal) {
					List<EnchantInfo> pick = new ArrayList<>();
					for (int i = 0; i < n; i++) {
						if ((mask & (1 << i)) != 0) {
							pick.add(options.get(i));
						}
					}
					out.add(pick);
				}
			}
			out.sort(java.util.Comparator.comparingInt(pick -> options.indexOf(pick.get(0))));
			return out;
		}

		private boolean allCompatible(int mask) {
			for (int i = 0; i < options.size(); i++) {
				for (int j = i + 1; j < options.size(); j++) {
					if ((mask & (1 << i)) != 0 && (mask & (1 << j)) != 0 && !EnchantInfo.areCompatible(options.get(i), options.get(j))) {
						return false;
					}
				}
			}
			return true;
		}
	}

	private Recommender() {}

	/** @param supported the item's supported, non-curse enchantments, in registry order */
	public static List<Group> recommend(List<EnchantInfo> supported) {
		List<Group> groups = new ArrayList<>();
		boolean[] used = new boolean[supported.size()];
		for (int start = 0; start < supported.size(); start++) {
			if (used[start]) {
				continue;
			}
			// Connected component of the conflict graph, so A-B and B-C conflicts land in one choice.
			List<EnchantInfo> group = new ArrayList<>();
			group.add(supported.get(start));
			used[start] = true;
			for (int g = 0; g < group.size(); g++) {
				for (int i = 0; i < supported.size(); i++) {
					if (!used[i] && !EnchantInfo.areCompatible(group.get(g), supported.get(i))) {
						used[i] = true;
						group.add(supported.get(i));
					}
				}
			}
			groups.add(new Group(group));
		}
		return groups;
	}
}
