package xpsaver.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static xpsaver.core.Vanilla.REGISTRY;
import static xpsaver.core.Vanilla.book;
import static xpsaver.core.Vanilla.priorWork;
import static xpsaver.core.Vanilla.sword;

import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/**
 * Expected values are worked by hand from net.minecraft.world.inventory.AnvilMenu#createResult (Minecraft 26.3, Loom genSources):
 * cost = left REPAIR_COST + right REPAIR_COST + sum(fee * level), fee = anvilCost, halved (min 1) when the right input is a book;
 * +1 per conflicting enchantment; result REPAIR_COST = 2 * max(left, right) + 1; "Too Expensive!" when cost >= 40.
 */
class AnvilCostTest {
	private static final Map<String, Integer> SHARP_5 = Map.of("minecraft:sharpness", 5);

	@ParameterizedTest(name = "fresh sword with {0} prior works + Sharpness V book = {2} levels")
	@CsvSource({
		// priorWorks, swordRepairCost, expectedCost, expectedNewRepairCost
		"0, 0, 5, 1",   // 0 + 0 + max(1, 1/2) * 5
		"1, 1, 6, 3",   // 1 + 0 + 5
		"2, 3, 8, 7",   // 3 + 0 + 5
		"3, 7, 12, 15", // 7 + 0 + 5
	})
	void sharpnessBookOntoSword(int priorWorks, int repairCost, int expectedCost, int expectedNewRepair) {
		assertEquals(repairCost, priorWork(priorWorks));
		AnvilCost.Result r = AnvilCost.combine(sword(repairCost, Map.of()), book(0, SHARP_5), REGISTRY, false, false);
		assertEquals(AnvilCost.Status.OK, r.status());
		assertEquals(expectedCost, r.cost());
		assertEquals(expectedNewRepair, r.result().repairCost());
		assertEquals(5, r.result().enchants().get("minecraft:sharpness"));
	}

	@Test
	void smiteBookOntoSharpnessSwordIsInvalid() {
		// AnvilMenu: Smite and Sharpness share exclusive_set/damage -> compatible=false (+1); no compatible enchantment -> EMPTY.
		AnvilCost.Result r = AnvilCost.combine(sword(0, SHARP_5), book(0, Map.of("minecraft:smite", 5)), REGISTRY, false, false);
		assertEquals(AnvilCost.Status.INVALID_CONFLICT, r.status());
	}

	@Test
	void bookWithSharpnessAndSmiteStillAppliesTheCompatiblePart() {
		// Sword already has Smite V. Book: Sharpness V (conflict, +1) + Unbreaking III (fee max(1, 2/2) = 1, x3).
		// AnvilMenu: some enchantment compatible -> valid; cost = 0 + 1 + 3 = 4.
		var bookEnchants = new java.util.LinkedHashMap<String, Integer>();
		bookEnchants.put("minecraft:sharpness", 5);
		bookEnchants.put("minecraft:unbreaking", 3);
		AnvilCost.Result r = AnvilCost.combine(sword(0, Map.of("minecraft:smite", 5)), book(0, bookEnchants), REGISTRY, false, false);
		assertEquals(AnvilCost.Status.OK, r.status());
		assertEquals(4, r.cost());
	}

	@Test
	void bookMultiplierIsHalfOfItemMultiplier() {
		// Unbreaking anvilCost = 2 (Enchantments#bootstrap). Book: max(1, 2/2) * 3 = 3. Item (undamaged, so no +2 repair): 2 * 3 = 6.
		Map<String, Integer> unb3 = Map.of("minecraft:unbreaking", 3);
		assertEquals(3, AnvilCost.combine(sword(0, Map.of()), book(0, unb3), REGISTRY, false, false).cost());
		assertEquals(6, AnvilCost.combine(sword(0, Map.of()), sword(0, unb3), REGISTRY, false, false).cost());
	}

	@Test
	void bookFeeNeverDropsBelowOne() {
		// Sharpness anvilCost = 1 -> book fee = max(1, 1 / 2) = 1, not 0.
		assertEquals(5, AnvilCost.combine(sword(0, Map.of()), book(0, SHARP_5), REGISTRY, false, false).cost());
	}

	@Test
	void equalLevelsMergeOneHigherButNotPastMax() {
		// AnvilMenu: level = current == level ? level + 1 : max; capped at getMaxLevel(). Two Unbreaking II books -> III, fee 1 x 3.
		AnvilCost.Result up = AnvilCost.combine(book(0, Map.of("minecraft:unbreaking", 2)), book(0, Map.of("minecraft:unbreaking", 2)),
				REGISTRY, false, false);
		assertEquals(3, up.result().enchants().get("minecraft:unbreaking"));
		assertEquals(3, up.cost());
		AnvilCost.Result capped = AnvilCost.combine(book(0, Map.of("minecraft:unbreaking", 3)), book(0, Map.of("minecraft:unbreaking", 3)),
				REGISTRY, false, false);
		assertEquals(3, capped.result().enchants().get("minecraft:unbreaking"));
	}

	@Test
	void damagedSwordRepairedBySacrificeAddsTwo() {
		// AnvilMenu: item sacrifice that reduces damage adds COST_REPAIR_SACRIFICE = 2.
		Piece damaged = new Piece("minecraft:diamond_sword", false, Map.of(), 0, 800, 1561, Vanilla.SWORD_SUPPORTS);
		assertEquals(2, AnvilCost.combine(damaged, sword(0, Map.of()), REGISTRY, false, false).cost());
	}

	@Test
	void renamingAddsOne() {
		assertEquals(6, AnvilCost.combine(sword(0, Map.of()), book(0, SHARP_5), REGISTRY, true, false).cost());
	}

	@Test
	void fortyOrMoreIsTooExpensive() {
		// 31 + 7 + 5 = 43 >= 40 -> AnvilMenu sets result EMPTY in survival ("Too Expensive!").
		AnvilCost.Result r = AnvilCost.combine(sword(priorWork(5), Map.of()), book(priorWork(3), SHARP_5), REGISTRY, false, false);
		assertEquals(AnvilCost.Status.TOO_EXPENSIVE, r.status());
		assertEquals(43, r.cost());
		// Creative (hasInfiniteMaterials) has no cap.
		assertEquals(AnvilCost.Status.OK, AnvilCost.combine(sword(priorWork(5), Map.of()), book(priorWork(3), SHARP_5), REGISTRY, false, true).status());
	}

	@Test
	void thirtyNineIsStillAllowed() {
		// 31 + 3 + 5 = 39 < 40 -> the 26.3 check is cost >= 40, so 39 levels still works.
		AnvilCost.Result r = AnvilCost.combine(sword(priorWork(5), Map.of()), book(priorWork(2), SHARP_5), REGISTRY, false, false);
		assertEquals(AnvilCost.Status.OK, r.status());
		assertEquals(39, r.cost());
	}

	@Test
	void differentItemsCannotCombine() {
		Piece axe = new Piece("minecraft:diamond_axe", false, Map.of(), 0, 0, 1561, Vanilla.SWORD_SUPPORTS);
		assertEquals(AnvilCost.Status.INVALID_ITEMS, AnvilCost.combine(sword(0, Map.of()), axe, REGISTRY, false, false).status());
	}
}
