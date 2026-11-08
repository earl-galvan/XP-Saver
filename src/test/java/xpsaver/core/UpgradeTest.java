package xpsaver.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static xpsaver.core.Vanilla.REGISTRY;
import static xpsaver.core.Vanilla.book;
import static xpsaver.core.Vanilla.sword;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Items that already carry enchantments: what each book does, and plans that build on what is there. */
class UpgradeTest {
	private static final String SHARPNESS = "minecraft:sharpness";
	private static final String SMITE = "minecraft:smite";
	private static final String UNBREAKING = "minecraft:unbreaking";
	private static final String MENDING = "minecraft:mending";
	private static final String LOOTING = "minecraft:looting";

	private static Upgrade.Effect on(Map<String, Integer> swordHas, String id, int bookLevel) {
		return Upgrade.effect(sword(0, swordHas), id, bookLevel, REGISTRY);
	}

	@Test
	void eachKindFollowsTheAnvilRule() {
		assertEquals(new Upgrade.Effect(LOOTING, Upgrade.Kind.ADDS, 0, 3, null), on(Map.of(), LOOTING, 3));
		// Equal levels merge one higher: Sharpness IV on the sword + Sharpness IV book = V.
		assertEquals(new Upgrade.Effect(SHARPNESS, Upgrade.Kind.RAISES, 4, 5, null), on(Map.of(SHARPNESS, 4), SHARPNESS, 4));
		// A higher book replaces a lower level.
		assertEquals(new Upgrade.Effect(UNBREAKING, Upgrade.Kind.RAISES, 2, 3, null), on(Map.of(UNBREAKING, 2), UNBREAKING, 3));
		// A lower book changes nothing (the anvil would still charge for it).
		assertEquals(new Upgrade.Effect(SHARPNESS, Upgrade.Kind.LOWER, 4, 4, null), on(Map.of(SHARPNESS, 4), SHARPNESS, 3));
		assertEquals(new Upgrade.Effect(MENDING, Upgrade.Kind.MAXED, 1, 1, null), on(Map.of(MENDING, 1), MENDING, 1));
		assertEquals(new Upgrade.Effect(SHARPNESS, Upgrade.Kind.CONFLICTS, 0, 0, SMITE), on(Map.of(SMITE, 5), SHARPNESS, 5));
		Piece pickaxe = new Piece("minecraft:diamond_pickaxe", false, Map.of(), 0, 0, 1561, java.util.Set.of(UNBREAKING));
		assertEquals(Upgrade.Kind.UNSUPPORTED, Upgrade.effect(pickaxe, LOOTING, 3, REGISTRY).kind());
	}

	@Test
	void aBookHelpsIfAnyOfItsEnchantmentsDoes() {
		Piece has = sword(0, Map.of(MENDING, 1, SMITE, 5));
		assertFalse(Upgrade.couldHelp(has, book(0, Map.of(MENDING, 1)), REGISTRY), "Mending is already there");
		assertFalse(Upgrade.couldHelp(has, book(0, Map.of(SHARPNESS, 5)), REGISTRY), "Smite blocks Sharpness");
		assertTrue(Upgrade.helps(has, book(0, Map.of(MENDING, 1, LOOTING, 3)), REGISTRY), "Looting is new");
		// Lower than the item's level: no help alone, but two such books could merge up, so it is not ruled out.
		Piece sharp4 = sword(0, Map.of(SHARPNESS, 4));
		assertFalse(Upgrade.helps(sharp4, book(0, Map.of(SHARPNESS, 3)), REGISTRY));
		assertTrue(Upgrade.couldHelp(sharp4, book(0, Map.of(SHARPNESS, 3)), REGISTRY));
	}

	@Test
	void uselessBookStillCostsLevelsSoItMustBeLeftOut() {
		// Why the mod greys such books out: the anvil charges 3 + 2*1 = 5 levels for Mending on a sword that has it,
		// changes no enchantment, and still raises the prior-work penalty 3 -> 7.
		AnvilCost.Result r = AnvilCost.combine(sword(3, Map.of(MENDING, 1)), book(0, Map.of(MENDING, 1)), REGISTRY, false, false);
		assertTrue(r.valid());
		assertEquals(5, r.cost());
		assertEquals(Map.of(MENDING, 1), r.result().enchants());
		assertEquals(7, r.result().repairCost());
	}

	@Test
	void planBuildsOnWhatTheSwordAlreadyHas() {
		// Sword used twice (REPAIR_COST 3) with Sharpness IV, Unbreaking III, Mending; books Sharpness IV and Looting III.
		// 1. Looting III + Sharpness IV: 0 + 0 + 1*4 = 4                                  -> book 1 use
		// 2. sword(3) + [Looting III, Sharpness IV](1): 3 + 1 + 2*3 + 1*5 (IV + IV = V) = 15 -> sword REPAIR_COST 7
		// Total 19. Putting the books on one at a time costs 8 + 13 = 21 (Sharpness first) or 9 + 12 = 21 (Looting first).
		Map<String, Integer> has = new LinkedHashMap<>();
		has.put(SHARPNESS, 4);
		has.put(UNBREAKING, 3);
		has.put(MENDING, 1);
		Piece target = sword(3, has);
		List<Piece> books = List.of(book(0, Map.of(SHARPNESS, 4)), book(0, Map.of(LOOTING, 3)));
		Planner.Plan plan = Planner.plan(List.of(target, books.get(0), books.get(1)), List.of("sword", "Sharpness IV", "Looting III"), REGISTRY);
		assertNotNull(plan);
		assertEquals(List.of(4, 15), plan.steps().stream().map(Planner.Step::cost).toList());
		assertEquals(19, plan.total());
		Planner.Step last = plan.steps().get(1);
		Piece result = AnvilCost.combine(last.leftPiece(), last.rightPiece(), REGISTRY, false, false).result();
		assertEquals(Map.of(SHARPNESS, 5, UNBREAKING, 3, MENDING, 1, LOOTING, 3), result.enchants());
	}

	@Test
	void everyItemAlreadyPartlyEnchantedMatchesTheReferee() {
		// For each guide item: the top enchantment already at max, the second one level below max (or absent if max 1),
		// earlier anvil use(s), and books only for what still helps (including an equal-level book that raises the second).
		Map<String, EnchantInfo> registry = AllItemsOptimalTest.REGISTRY;
		for (String category : List.of("helmet", "chestplate", "leggings", "boots", "sword", "axe", "bow", "crossbow", "trident",
				"mace", "spear", "pickaxe", "shovel", "hoe", "fishing_rod", "shears", "other")) {
			List<String> ids = Guide.priority(category);
			for (int repairCost : new int[] {1, 3, 7}) {
				Map<String, Integer> has = new LinkedHashMap<>();
				has.put(ids.get(0), registry.get(ids.get(0)).maxLevel());
				EnchantInfo second = registry.get(ids.get(1));
				if (second.maxLevel() > 1) {
					has.put(second.id(), second.maxLevel() - 1);
				}
				Piece target = new Piece("minecraft:" + category, false, has, repairCost, 0, 100, java.util.Set.copyOf(ids));
				List<Piece> inputs = new ArrayList<>(List.of(target));
				List<String> labels = new ArrayList<>(List.of("item"));
				for (String id : ids) {
					int level = has.containsKey(id) ? has.get(id) : registry.get(id).maxLevel(); // equal level for the second
					Piece b = book(0, Map.of(id, level));
					if (Upgrade.helps(target, b, registry)) {
						inputs.add(b);
						labels.add(id);
					}
				}
				assertFalse(inputs.stream().skip(1).anyMatch(b -> b.enchants().containsKey(ids.get(0))), "maxed one is left out");
				String what = category + " with " + has + ", REPAIR_COST " + repairCost;
				int[] referee = AllItemsOptimalTest.referee(inputs);
				Planner.Plan plan = Planner.plan(inputs, labels, registry);
				if (referee == null) {
					assertEquals(null, plan, what);
					continue;
				}
				assertNotNull(plan, what);
				int[] got = AllItemsOptimalTest.score(plan);
				assertEquals(List.of(referee[0], referee[1], referee[2]), List.of(got[0], got[1], got[2]), what);
				// The finished item keeps what it had and gains every book's enchantment at the expected level.
				Planner.Step last = plan.steps().get(plan.steps().size() - 1);
				Map<String, Integer> end = AnvilCost.combine(last.leftPiece(), last.rightPiece(), registry, false, false).result().enchants();
				for (String id : ids) {
					assertEquals(registry.get(id).maxLevel(), end.get(id), what + ": " + id);
				}
			}
		}
	}
}
