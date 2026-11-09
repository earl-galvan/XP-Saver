package xpsaver.client;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import xpsaver.core.EnchantInfo;
import xpsaver.core.Guide;
import xpsaver.core.Upgrade;
import xpsaver.core.Piece;
import xpsaver.core.Planner;

/**
 * F3: cheapest combine order for the held item and the inventory's books chosen by the best enchantments guide, in a vanilla-style
 * panel centered on the screen (see Panel). Lines wrap to the screen width and scroll with the mouse wheel when the screen is short.
 */
final class PlannerScreen extends Screen {
	/** Widest the panel gets on large screens, so lines stay easy to read. */
	private static final int MAX_WIDTH = 360;
	private final List<Component> lines;
	private final Panel panel = new Panel();

	PlannerScreen(Minecraft mc) {
		super(Component.literal("XP Saver planner"));
		this.lines = buildLines(mc);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** Runs on open and on every resize, so the panel always fits the window. */
	@Override
	protected void init() {
		int widest = 0;
		for (Component line : lines) {
			widest = Math.max(widest, font.width(line));
		}
		int width = Math.min(Math.min(Panel.widthFor(widest), MAX_WIDTH), this.width - 2 * Panel.GAP);
		List<Panel.Row> rows = new ArrayList<>();
		lines.forEach(line -> Panel.wrap(rows, font, line, Panel.textWidth(width), Panel.TEXT, -1));
		panel.setRows(rows);
		panel.placeCentered(this.width, this.height, width);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		panel.draw(g, font);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		return panel.scroll(x, y, scrollY) || super.mouseScrolled(x, y, scrollX, scrollY);
	}

	private static List<Component> buildLines(Minecraft mc) {
		List<Component> out = new ArrayList<>();
		out.add(Component.literal("Cheapest anvil order").withStyle(ChatFormatting.GOLD));
		Inventory inv = mc.player.getInventory();
		Registry<Enchantment> registry = Enchants.registry(mc);

		ItemStack target = pickTarget(mc.player.getMainHandItem(), inv, registry);
		if (target == null) {
			out.add(Component.literal("Hold the item to enchant (or carry one), plus enchanted books.").withStyle(ChatFormatting.GRAY));
			return out;
		}
		List<ItemStack> books = new ArrayList<>();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).is(Items.ENCHANTED_BOOK)) {
				books.add(inv.getItem(i));
			}
		}

		out.add(Component.literal("Item: ").append(target.getHoverName()));
		String category = Enchants.guideCategory(target);
		if (category == null) {
			out.add(Component.literal("The best enchantments guide has no section for this item.").withStyle(ChatFormatting.GRAY));
			return out;
		}

		// Guide order, highest priority first; for each enchantment use the player's highest-level book, if it improves the item:
		// it adds the enchantment, raises a lower level, or matches the item's level below max (IV + IV = V in the anvil).
		// shortcut: one book per enchantment; two equal-level books (e.g. two Sharpness IV -> V) are not merged first.
		Piece targetPiece = Enchants.piece(target, registry);
		Map<String, EnchantInfo> infos = Enchants.infos(registry);
		List<String> done = new ArrayList<>();
		List<String> blocked = new ArrayList<>();
		List<Planner.Want> wants = new ArrayList<>();
		Map<ItemStack, Piece> pieces = new IdentityHashMap<>(); // one Piece per book, so a book serving two wants is used once
		for (String id : Guide.priority(category)) {
			EnchantInfo info = infos.get(id);
			if (info == null) {
				continue;
			}
			Upgrade.Effect goal = Upgrade.effect(targetPiece, id, info.maxLevel(), infos);
			if (goal.kind() == Upgrade.Kind.MAXED) {
				done.add(Enchants.fullName(registry, id, goal.from()));
				continue;
			}
			if (goal.kind() == Upgrade.Kind.CONFLICTS) {
				blocked.add(Enchants.name(registry, id) + " (" + Enchants.fullName(registry, goal.conflictWith(),
						targetPiece.enchants().get(goal.conflictWith())) + " is on it)");
				continue;
			}
			ItemStack best = null;
			int bestLevel = 0;
			for (ItemStack book : books) {
				int level = storedLevel(book, id);
				if (level > bestLevel || (level == bestLevel && level > 0 && enchantCount(book) < enchantCount(best))) {
					best = book;
					bestLevel = level;
				}
			}
			if (best != null && Upgrade.effect(targetPiece, id, bestLevel, infos).helps()) {
				wants.add(new Planner.Want(id, pieces.computeIfAbsent(best, b -> Enchants.piece(b, registry)), "[" + bookName(best) + "]"));
			}
		}
		if (wants.isEmpty()) {
			out.add(Component.literal("No book in your inventory improves this item further.").withStyle(ChatFormatting.GRAY));
		} else {
			Planner.Plan plan = Planner.planGuided(targetPiece, target.getHoverName().getString(), wants, infos);
			for (Planner.Step s : plan.steps()) {
				out.add(Component.literal(s.number() + ". " + s.left() + " + " + s.right() + ": " + s.cost() + " levels"));
			}
			MutableComponent total = Component.literal("Total: " + plan.total() + " levels (every step \u2264 " + Planner.MAX_STEP_COST + ")")
					.withStyle(ChatFormatting.GREEN);
			if (plan.approximate()) {
				total.append(Component.literal("  (approximate: more than " + Planner.EXACT_LIMIT + " inputs)").withStyle(ChatFormatting.YELLOW));
			}
			out.add(total);
			if (!plan.dropped().isEmpty()) {
				out.add(Component.literal("Left out to stay within " + Planner.MAX_STEP_COST + " levels per step:").withStyle(ChatFormatting.RED));
				for (String id : plan.dropped()) {
					out.add(Component.literal("  " + name(registry, id)).withStyle(ChatFormatting.RED));
				}
			}
		}
		if (!done.isEmpty()) {
			out.add(Component.literal("Already on the item: " + String.join(", ", done)).withStyle(ChatFormatting.DARK_GREEN));
		}
		if (!blocked.isEmpty()) {
			out.add(Component.literal("Can't be added (conflict):").withStyle(ChatFormatting.GRAY));
			blocked.forEach(b -> out.add(Component.literal("  " + b).withStyle(ChatFormatting.GRAY)));
		}
		return out;
	}

	private static String name(Registry<Enchantment> registry, String id) {
		return registry.listElements().filter(h -> Enchants.id(h).equals(id)).findFirst()
				.map(h -> h.value().description().getString()).orElse(id);
	}

	/** Main-hand item if it takes enchantments, else the first such non-book item in the inventory. */
	private static ItemStack pickTarget(ItemStack hand, Inventory inv, Registry<Enchantment> registry) {
		if (isTarget(hand, registry)) {
			return hand;
		}
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (isTarget(inv.getItem(i), registry)) {
				return inv.getItem(i);
			}
		}
		return null;
	}

	private static boolean isTarget(ItemStack stack, Registry<Enchantment> registry) {
		return !stack.isEmpty() && !stack.is(Items.ENCHANTED_BOOK) && !stack.is(Items.BOOK)
				&& !Enchants.recommendable(stack, registry).isEmpty();
	}

	private static int storedLevel(ItemStack stack, String id) {
		for (var entry : EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet()) {
			if (Enchants.id(entry.getKey()).equals(id)) {
				return entry.getIntValue();
			}
		}
		return 0;
	}

	private static int enchantCount(ItemStack stack) {
		return stack == null ? Integer.MAX_VALUE : EnchantmentHelper.getEnchantmentsForCrafting(stack).size();
	}

	private static String bookName(ItemStack book) {
		List<String> names = new ArrayList<>();
		for (var entry : EnchantmentHelper.getEnchantmentsForCrafting(book).entrySet()) {
			Holder<Enchantment> h = entry.getKey();
			names.add(Enchantment.getFullname(h, entry.getIntValue()).getString());
		}
		return String.join(", ", names);
	}
}
