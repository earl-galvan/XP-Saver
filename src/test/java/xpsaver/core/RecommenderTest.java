package xpsaver.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Conflicts from data/minecraft/tags/enchantment/exclusive_set/damage.json (Minecraft 26.2). */
class RecommenderTest {
	@Test
	void swordDamageEnchantsFormOneChoiceAndTheRestStandAlone() {
		List<Recommender.Group> groups = Recommender.recommend(Vanilla.SWORD_SUPPORTED);
		assertEquals(List.of(Vanilla.SHARPNESS, Vanilla.SMITE, Vanilla.BANE), groups.get(0).options());
		assertEquals(7, groups.size()); // damage choice + knockback, fire aspect, looting, sweeping edge, unbreaking, mending
		assertEquals(1, groups.stream().filter(Recommender.Group::choice).count());
		// All three damage enchantments exclude each other, so each is its own pick.
		assertEquals(List.of(List.of(Vanilla.SHARPNESS), List.of(Vanilla.SMITE), List.of(Vanilla.BANE)), groups.get(0).picks());
	}

	@Test
	void tridentPicksAreRiptideOrLoyaltyWithChanneling() {
		// riptide's exclusive_set is #exclusive_set/riptide = [loyalty, channeling]; loyalty and channeling don't exclude each other.
		Map<String, EnchantInfo> r = AllItemsOptimalTest.REGISTRY;
		List<EnchantInfo> trident = List.of(r.get("minecraft:loyalty"), r.get("minecraft:riptide"), r.get("minecraft:channeling"),
				r.get("minecraft:impaling"), r.get("minecraft:unbreaking"), r.get("minecraft:mending"));
		List<Recommender.Group> groups = Recommender.recommend(trident);
		Recommender.Group choice = groups.stream().filter(Recommender.Group::choice).findFirst().orElseThrow();
		assertEquals(List.of(List.of(r.get("minecraft:loyalty"), r.get("minecraft:channeling")), List.of(r.get("minecraft:riptide"))),
				choice.picks());
	}
}
