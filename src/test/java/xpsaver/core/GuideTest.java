package xpsaver.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Expected lists copied from https://minecraft.wiki/w/Tutorial:Best_enchantments_guide (read 2026-11-09). */
class GuideTest {
	@Test
	void swordListFollowsTheGuideOrderWithSharpnessAsDefault() {
		assertEquals(List.of("minecraft:unbreaking", "minecraft:mending", "minecraft:sharpness", "minecraft:sweeping_edge",
				"minecraft:looting", "minecraft:fire_aspect", "minecraft:knockback"), Guide.priority("sword"));
	}

	@Test
	void mutuallyExclusiveOptionsKeepOnlyTheDefault() {
		assertFalse(Guide.priority("sword").contains("minecraft:smite"));
		assertTrue(Guide.priority("boots").contains("minecraft:depth_strider"));
		assertFalse(Guide.priority("boots").contains("minecraft:frost_walker"));
		assertTrue(Guide.priority("bow").contains("minecraft:infinity"));
		assertFalse(Guide.priority("bow").contains("minecraft:mending"));
		assertFalse(Guide.priority("helmet").contains("minecraft:blast_protection"));
	}

	@Test
	void unknownCategoryIsEmpty() {
		assertEquals(List.of(), Guide.priority("carrot"));
	}
}
