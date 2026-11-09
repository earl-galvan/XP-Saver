package xpsaver.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * The tooltip behind an enchantment row's "!" icon (see Panel): name, what it does, max level, what it can't be combined with, and
 * whether the enchanting table offers it. Descriptions are in the lang file under xpsaver.info.namespace.path (written for this
 * mod from https://help.minecraft.net/hc/en-us/articles/360058730912 and checked against Enchantments.java, 26.2); an
 * enchantment with no entry (from a datapack or mod) still gets the lines read from the registry.
 */
final class EnchantTips {
	private EnchantTips() {}

	/** Tooltip lines for the enchantments {@code ids}, one block each, separated by a blank line. Empty if none are known. */
	static List<Component> of(Registry<Enchantment> registry, List<String> ids) {
		List<Component> out = new ArrayList<>();
		for (String id : ids) {
			Optional<Holder.Reference<Enchantment>> found = registry.listElements().filter(h -> Enchants.id(h).equals(id)).findFirst();
			if (found.isEmpty()) {
				continue;
			}
			if (!out.isEmpty()) {
				out.add(Component.empty());
			}
			add(out, registry, found.get());
		}
		return out;
	}

	private static void add(List<Component> out, Registry<Enchantment> registry, Holder.Reference<Enchantment> h) {
		boolean curse = h.is(EnchantmentTags.CURSE);
		out.add(h.value().description().copy().withStyle(curse ? ChatFormatting.RED : ChatFormatting.GOLD));
		String key = "xpsaver.info." + h.key().identifier().getNamespace() + "." + h.key().identifier().getPath();
		if (Language.getInstance().has(key)) {
			out.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
		}
		out.add(Component.translatable("xpsaver.info.max_level",
				Component.translatable("enchantment.level." + h.value().getMaxLevel())).withStyle(ChatFormatting.GRAY));
		// Enchantment.areCompatible (26.2): two enchantments clash when either lists the other in its exclusive set.
		List<String> clashes = new ArrayList<>();
		registry.listElements().forEach(other -> {
			if (!other.key().equals(h.key()) && (h.value().exclusiveSet().contains(other) || other.value().exclusiveSet().contains(h))) {
				clashes.add(other.value().description().getString());
			}
		});
		if (!clashes.isEmpty()) {
			out.add(Component.translatable("xpsaver.info.conflicts", String.join(", ", clashes)).withStyle(ChatFormatting.RED));
		}
		if (!h.is(EnchantmentTags.IN_ENCHANTING_TABLE)) {
			out.add(Component.translatable("xpsaver.info.not_in_table").withStyle(ChatFormatting.DARK_AQUA));
		}
	}
}
