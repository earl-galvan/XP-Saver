package xpsaver.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import xpsaver.client.config.XpSaverConfig;
import xpsaver.core.EnchantInfo;
import xpsaver.core.Piece;
import xpsaver.core.Upgrade;
import xpsaver.core.Recommender;

/**
 * F1: recommendations beside the enchanting table, and which visible offer matches them, in a vanilla-style panel (see Panel).
 * Whether it shows, and whether it lists the table's offers, come from XpSaverConfig and apply live.
 */
final class EnchantPanel {
	private EnchantPanel() {}

	/** Draws the panel every frame and lets the mouse wheel scroll it (long lists on small screens). */
	static void attach(EnchantmentScreen screen) {
		Panel panel = new Panel();
		ScreenEvents.afterForeground(screen).register((s, g, mouseX, mouseY, delta) -> draw(screen, g, panel, mouseX, mouseY));
		ScreenMouseEvents.allowMouseScroll(screen).register((s, mouseX, mouseY, h, v) -> !panel.scroll(mouseX, mouseY, v));
	}

	private static void draw(EnchantmentScreen screen, GuiGraphicsExtractor g, Panel panel, int mouseX, int mouseY) {
		panel.setRows(List.of());
		Minecraft mc = Minecraft.getInstance();
		EnchantmentMenu menu = screen.getMenu();
		ItemStack item = menu.getSlot(0).getItem();
		if (!XpSaverConfig.get().tablePanel || item.isEmpty() || mc.level == null) {
			return;
		}
		List<Component> lines = new ArrayList<>();
		Map<Component, List<String>> ids = new IdentityHashMap<>();
		lines.add(Component.literal("Recommended").withStyle(ChatFormatting.GOLD));
		Registry<Enchantment> registry = Enchants.registry(mc);
		Map<String, Recommender.Group> groupOf = addRecommendations(item, registry, lines, ids);
		if (item.is(Items.BOOK)) {
			show(screen, g, panel, lines, ids, registry, mouseX, mouseY);
			return;
		}
		if (groupOf.isEmpty()) {
			return;
		}
		if (!XpSaverConfig.get().tableOffers) {
			show(screen, g, panel, lines, ids, registry, mouseX, mouseY);
			return;
		}

		// EnchantmentMenu syncs only the clue (one enchantment + level) per offer; nothing else is known client-side.
		lines.add(Component.empty());
		lines.add(Component.literal("Table offers").withStyle(ChatFormatting.GOLD));
		for (int i = 0; i < 3; i++) {
			var clue = registry.get(menu.enchantClue[i]);
			if (menu.costs[i] <= 0 || clue.isEmpty() || menu.levelClue[i] < 0) {
				continue;
			}
			Recommender.Group group = groupOf.get(Enchants.id(clue.get()));
			MutableComponent line = Component.literal((i + 1) + ". ");
			if (group == null) {
				line.append(Component.literal("✘ ").withStyle(ChatFormatting.RED));
			} else {
				line.append(Component.literal("✔ ").withStyle(ChatFormatting.GREEN));
			}
			line.append(Enchantment.getFullname(clue.get(), menu.levelClue[i]));
			if (group != null && group.choice()) {
				line.append(Component.literal(" (choice)").withStyle(ChatFormatting.AQUA));
			}
			lines.add(line);
			ids.put(line, List.of(Enchants.id(clue.get())));
		}
		show(screen, g, panel, lines, ids, registry, mouseX, mouseY);
	}

	/** Beside the table (left by default), level with its window; lines wrap to the room there. Lines in {@code ids} get a "!" icon. */
	private static void show(EnchantmentScreen screen, GuiGraphicsExtractor g, Panel panel, List<Component> lines,
			Map<Component, List<String>> ids, Registry<Enchantment> registry, int mouseX, int mouseY) {
		Font font = Minecraft.getInstance().font;
		int widest = 0;
		for (Component line : lines) {
			widest = Math.max(widest, Panel.lineWidth(font, line, ids.containsKey(line)));
		}
		int top = Panel.topPos(screen.height);
		if (!panel.placeBeside(screen.width, screen.height, top, Panel.bottom(screen.height), Panel.widthFor(widest))) {
			return;
		}
		List<Panel.Row> rows = new ArrayList<>();
		lines.forEach(line -> Panel.wrap(rows, font, line, Panel.textWidth(panel.width()), Panel.text(), -1,
				EnchantTips.of(registry, ids.getOrDefault(line, List.of()))));
		panel.setRows(rows);
		panel.draw(g, font, mouseX, mouseY);
	}

	/** One recommendation, marked by what the item already has: ✔ has it, ↑ can be upgraded, ✘ blocked; plain if it's new. */
	private static Component fragment(EnchantInfo e, boolean bullet, Piece current, Map<String, EnchantInfo> all,
			Registry<Enchantment> registry, Map<String, Holder<Enchantment>> holders) {
		Upgrade.Effect effect = Upgrade.effect(current, e.id(), e.maxLevel(), all);
		String best = Enchants.fullName(registry, e.id(), e.maxLevel());
		return switch (effect.kind()) {
			case MAXED -> Component.literal("\u2714 " + best + " (has it)").withStyle(ChatFormatting.GREEN);
			case RAISES -> Component.literal("\u2191 " + Enchants.fullName(registry, e.id(), effect.from()) + " \u2192 "
					+ Component.translatable("enchantment.level." + effect.to()).getString()).withStyle(ChatFormatting.YELLOW);
			case CONFLICTS -> Component.literal("\u2718 " + best + " (blocked by " + Enchants.name(registry, effect.conflictWith()) + ")")
					.withStyle(ChatFormatting.DARK_GRAY);
			default -> Component.literal(bullet ? "\u2022 " : "").append(Enchantment.getFullname(holders.get(e.id()), e.maxLevel()));
		};
	}

	/**
	 * Appends the recommendation lines for {@code item} (shared by this panel and the anvil's "Recommended" dropdown), and puts
	 * each line that names enchantments in {@code ids} (keyed by the line object itself) with their ids, for the "!" tooltips.
	 * @return enchantment id -> its conflict group, empty if the item takes no enchantments
	 */
	static Map<String, Recommender.Group> addRecommendations(ItemStack item, Registry<Enchantment> registry, List<Component> lines,
			Map<Component, List<String>> ids) {
		Map<String, Recommender.Group> groupOf = new HashMap<>();
		if (item.is(Items.BOOK) || item.is(Items.ENCHANTED_BOOK)) {
			lines.add(Component.literal("A book accepts any enchantment.").withStyle(ChatFormatting.GRAY));
			return groupOf;
		}
		Map<String, Holder<Enchantment>> holders = new HashMap<>();
		List<EnchantInfo> infos = new ArrayList<>();
		for (Holder.Reference<Enchantment> h : Enchants.recommendable(item, registry)) {
			holders.put(Enchants.id(h), h);
			infos.add(Enchants.info(h));
		}
		// Judge each recommendation against what the item already has (an enchanting table only takes unenchanted items,
		// so there every line is a plain "adds" line; in the anvil the item may already be enchanted).
		Piece current = Enchants.piece(item, registry);
		Map<String, EnchantInfo> all = Enchants.infos(registry);
		for (Recommender.Group group : Recommender.recommend(infos)) {
			group.options().forEach(e -> groupOf.put(e.id(), group));
			if (!group.choice()) {
				Component line = fragment(group.options().get(0), true, current, all, registry, holders);
				lines.add(line);
				ids.put(line, List.of(group.options().get(0).id()));
				continue;
			}
			lines.add(Component.literal("\u25C6 Pick one (taste):").withStyle(ChatFormatting.AQUA));
			for (List<EnchantInfo> pick : group.picks()) { // a pick may hold several (Loyalty + Channeling)
				MutableComponent line = Component.literal("   ");
				for (int i = 0; i < pick.size(); i++) {
					line.append(i == 0 ? Component.empty() : Component.literal(" + "));
					line.append(fragment(pick.get(i), false, current, all, registry, holders));
				}
				lines.add(line);
				ids.put(line, pick.stream().map(EnchantInfo::id).toList());
			}
		}
		return groupOf;
	}
}
