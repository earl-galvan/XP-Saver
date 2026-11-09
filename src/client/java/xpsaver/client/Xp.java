package xpsaver.client;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Whether the player's levels cover a plan. The anvil only lets you take a result with at least its cost in levels
 * (AnvilMenu#mayPickup, 26.2: {@code player.hasInfiniteMaterials() || player.experienceLevel >= cost}) and takes that many levels,
 * so steps are paid one after another from the current level.
 */
final class Xp {
	private Xp() {}

	/** The player's level, or -1 in creative mode, where nothing is charged. */
	static int level(Minecraft mc) {
		return mc.player == null || mc.player.hasInfiniteMaterials() ? -1 : mc.player.experienceLevel;
	}

	/** How many of {@code costs}, paid in order from {@code level}, the player can afford before running short (all of them in creative). */
	static int affordable(int level, List<Integer> costs) {
		if (level < 0) {
			return costs.size();
		}
		int left = level;
		for (int i = 0; i < costs.size(); i++) {
			if (left < costs.get(i)) {
				return i;
			}
			left -= costs.get(i);
		}
		return costs.size();
	}

	/**
	 * "You have 30 levels: enough." in green, or how far they go in red: "You have 5 levels: not enough for step 1. Need 44 more."
	 * {@code firstStep} is the number of the first step in {@code costs} (later than 1 once some steps are done). Null in creative
	 * mode or with no steps left.
	 */
	static Component line(int level, List<Integer> costs, int firstStep) {
		if (level < 0 || costs.isEmpty()) {
			return null;
		}
		int ok = affordable(level, costs);
		int need = costs.stream().mapToInt(Integer::intValue).sum();
		String have = "You have " + level + (level == 1 ? " level" : " levels");
		if (ok == costs.size()) {
			return Component.literal(have + ": enough.").withStyle(ChatFormatting.GREEN);
		}
		String covers = ok == 0 ? "not enough for step " + firstStep : "enough up to step " + (firstStep + ok - 1);
		return Component.literal(have + ": " + covers + ". Need " + (need - level) + " more.").withStyle(ChatFormatting.RED);
	}
}
