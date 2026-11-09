package xpsaver.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import xpsaver.core.EnchantInfo;
import xpsaver.core.Guide;
import xpsaver.core.Piece;
import xpsaver.core.Upgrade;
import net.minecraft.network.chat.Component;

/** Reads enchantment data from the live registry (data-driven, so datapack enchantments are included) into core types. */
final class Enchants {
	private Enchants() {}

	/** Client registry, as EnchantmentScreen#extractRenderState looks it up (26.2): level.registryAccess().lookupOrThrow(ENCHANTMENT). */
	static Registry<Enchantment> registry(Minecraft mc) {
		return mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
	}

	static String id(Holder<Enchantment> holder) {
		return holder.getRegisteredName();
	}

	/** Enchantment record (26.2): definition max level / anvil cost, exclusiveSet HolderSet. */
	static EnchantInfo info(Holder<Enchantment> holder) {
		Enchantment e = holder.value();
		Set<String> exclusive = e.exclusiveSet().stream().map(Holder::getRegisteredName).collect(Collectors.toSet());
		return new EnchantInfo(id(holder), e.getMaxLevel(), e.getAnvilCost(), exclusive);
	}

	static Map<String, EnchantInfo> infos(Registry<Enchantment> registry) {
		Map<String, EnchantInfo> map = new LinkedHashMap<>();
		registry.listElements().forEach(h -> map.put(id(h), info(h)));
		return map;
	}

	/** Enchantments the anvil accepts on this item (Enchantment#canEnchant = supported_items tag), curses left out. */
	static List<Holder.Reference<Enchantment>> recommendable(ItemStack stack, Registry<Enchantment> registry) {
		return registry.listElements()
				.filter(h -> h.value().canEnchant(stack) && !h.is(EnchantmentTags.CURSE))
				.toList();
	}

	/** "Sharpness IV" for an id and level, as plain text so callers choose the colour ("Mending" for max-level-1 enchantments). */
	static String fullName(Registry<Enchantment> registry, String id, int level) {
		return registry.listElements().filter(h -> id(h).equals(id)).findFirst()
				.map(h -> Enchantment.getFullname(h, level).getString()).orElse(id);
	}

	/** Bare name without a level, e.g. "Sharpness". */
	static String name(Registry<Enchantment> registry, String id) {
		return registry.listElements().filter(h -> id(h).equals(id)).findFirst()
				.map(h -> h.value().description().getString()).orElse(id);
	}

	/** Short words for what one enchantment would do to {@code item}, e.g. "\u2192 Sharpness V" or "blocked by Smite". */
	static String describe(Registry<Enchantment> registry, Piece item, Upgrade.Effect e) {
		return switch (e.kind()) {
			case ADDS -> "adds " + fullName(registry, e.id(), e.to());
			case RAISES -> "\u2192 " + fullName(registry, e.id(), e.to()) + " (now " + Component.translatable("enchantment.level." + e.from()).getString() + ")";
			case LOWER -> "item has " + fullName(registry, e.id(), e.from()) + ": no gain alone";
			case MAXED -> "already on the item";
			case CONFLICTS -> "blocked by " + name(registry, e.conflictWith());
			case UNSUPPORTED -> "can't go on this item";
		};
	}

	/**
	 * True if the planner may use {@code stack} on {@code target}: an enchanted book, or another enchanted copy of the same damageable
	 * item (AnvilMenu#createResult combines those; it repairs too).
	 */
	static boolean isCandidate(ItemStack stack, ItemStack target) {
		if (stack.is(Items.ENCHANTED_BOOK)) {
			return true;
		}
		return !target.isEmpty() && stack != target && ItemStack.isSameItem(stack, target) && target.isDamageableItem()
				&& !EnchantmentHelper.getEnchantmentsForCrafting(stack).isEmpty();
	}

	/** A curse {@code candidate} would put on {@code target} (the item doesn't have it and accepts it), e.g. "Curse of Vanishing"; else null. */
	static String curseAdded(ItemStack candidate, ItemStack target) {
		ItemEnchantments on = EnchantmentHelper.getEnchantmentsForCrafting(target);
		for (Object2IntMap.Entry<Holder<Enchantment>> e : EnchantmentHelper.getEnchantmentsForCrafting(candidate).entrySet()) {
			Holder<Enchantment> h = e.getKey();
			if (h.is(EnchantmentTags.CURSE) && on.getLevel(h) == 0 && h.value().canEnchant(target)) {
				return Enchantment.getFullname(h, e.getIntValue()).getString();
			}
		}
		return null;
	}

	/** Its enchantments as words, e.g. "Sharpness V, Mending". */
	static String enchantList(ItemStack stack) {
		List<String> names = new ArrayList<>();
		for (Object2IntMap.Entry<Holder<Enchantment>> e : EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet()) {
			names.add(Enchantment.getFullname(e.getKey(), e.getIntValue()).getString());
		}
		return String.join(", ", names);
	}

	/** How plans name a candidate: "Looting III book", or "Diamond Sword with Unbreaking III, Mending" for an item copy. */
	static String label(ItemStack stack) {
		return stack.is(Items.ENCHANTED_BOOK) ? enchantList(stack) + " book" : stack.getHoverName().getString() + " with " + enchantList(stack);
	}

	/**
	 * Enchantments to aim for on {@code target}, highest priority first: the best enchantments guide's list for it, then, if
	 * {@code everything}, every other enchantment it accepts (curses left out) in registry order.
	 */
	static List<String> wanted(ItemStack target, Registry<Enchantment> registry, boolean everything) {
		String category = guideCategory(target);
		List<String> out = new ArrayList<>(category == null ? List.of() : Guide.priority(category));
		if (everything) {
			recommendable(target, registry).forEach(h -> {
				if (!out.contains(id(h))) {
					out.add(id(h));
				}
			});
		}
		return out;
	}

	/** The fields AnvilMenu#createResult reads from one input (26.2). */
	static Piece piece(ItemStack stack, Registry<Enchantment> registry) {
		Map<String, Integer> enchants = new LinkedHashMap<>();
		// getEnchantmentsForCrafting: STORED_ENCHANTMENTS for books, ENCHANTMENTS otherwise, as AnvilMenu uses it.
		for (Object2IntMap.Entry<Holder<Enchantment>> entry : EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet()) {
			enchants.put(id(entry.getKey()), entry.getIntValue());
		}
		Set<String> supports = registry.listElements().filter(h -> h.value().canEnchant(stack)).map(Enchants::id)
				.collect(Collectors.toSet());
		boolean damageable = stack.isDamageableItem();
		return new Piece(stack.typeHolder().getRegisteredName(), stack.is(Items.ENCHANTED_BOOK), enchants,
				stack.getOrDefault(DataComponents.REPAIR_COST, 0),
				damageable ? stack.getDamageValue() : 0, damageable ? stack.getMaxDamage() : 0, supports);
	}

	/** Section of the best enchantments guide this item belongs to (vanilla item tags, then single items), or null. */
	static String guideCategory(ItemStack stack) {
		if (stack.is(ItemTags.HEAD_ARMOR)) return "helmet";
		if (stack.is(ItemTags.CHEST_ARMOR)) return "chestplate";
		if (stack.is(ItemTags.LEG_ARMOR)) return "leggings";
		if (stack.is(ItemTags.FOOT_ARMOR)) return "boots";
		if (stack.is(ItemTags.SWORDS)) return "sword";
		if (stack.is(ItemTags.AXES)) return "axe";
		if (stack.is(ItemTags.SPEARS)) return "spear";
		if (stack.is(ItemTags.PICKAXES)) return "pickaxe";
		if (stack.is(ItemTags.SHOVELS)) return "shovel";
		if (stack.is(ItemTags.HOES)) return "hoe";
		return switch (stack.typeHolder().getRegisteredName()) {
			case "minecraft:bow" -> "bow";
			case "minecraft:crossbow" -> "crossbow";
			case "minecraft:trident" -> "trident";
			case "minecraft:mace" -> "mace";
			case "minecraft:fishing_rod" -> "fishing_rod";
			case "minecraft:shears" -> "shears";
			case "minecraft:flint_and_steel", "minecraft:shield", "minecraft:carrot_on_a_stick", "minecraft:warped_fungus_on_a_stick",
					"minecraft:brush", "minecraft:elytra" -> "other";
			default -> null;
		};
	}

	/** True if this stack is the piece a plan expects (same item, enchantments and prior work; damage is ignored). */
	static boolean matches(ItemStack stack, Piece piece, Registry<Enchantment> registry) {
		if (stack.isEmpty()) {
			return false;
		}
		Piece p = piece(stack, registry);
		return p.itemType().equals(piece.itemType()) && p.book() == piece.book() && p.repairCost() == piece.repairCost()
				&& p.enchants().equals(piece.enchants());
	}
}
