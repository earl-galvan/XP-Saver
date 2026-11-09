package xpsaver.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import xpsaver.client.config.XpSaverConfig;
import xpsaver.core.EnchantInfo;
import xpsaver.core.Piece;
import xpsaver.core.Planner;
import xpsaver.core.Selector;
import xpsaver.core.Upgrade;

/**
 * F3: the cheapest way to give the held item the best enchantments guide's list from what is in the inventory: which enchanted
 * books (and enchanted copies of the item) to use and in what order (Selector), and whether your levels cover it. Planned on a
 * background thread; shown in a vanilla-style panel centered on the screen (see Panel). Lines wrap to the screen width and scroll
 * with the mouse wheel when the screen is short.
 */
final class PlannerScreen extends Screen {
	private List<Component> lines;
	private final Panel panel = new Panel();
	private CompletableFuture<List<Component>> pending; // the full text, once the plan is worked out

	PlannerScreen(Minecraft mc) {
		super(Component.literal("XP Saver planner"));
		this.lines = start(mc);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		if (pending != null && pending.isDone()) {
			try {
				lines = pending.join();
			} catch (RuntimeException e) {
				lines = List.of(Component.literal("Cheapest anvil order").withStyle(ChatFormatting.GOLD),
						Component.literal("Couldn't work out an order.").withStyle(ChatFormatting.RED));
			}
			pending = null;
			init();
		}
	}

	/** Runs on open, on every resize, and when the plan is ready, so the panel always fits the window and the text. */
	@Override
	protected void init() {
		int widest = 0;
		for (Component line : lines) {
			widest = Math.max(widest, font.width(line));
		}
		// Capped (config: planner max width) so lines stay easy to read on large screens.
		int width = Math.min(Math.min(Panel.widthFor(widest), XpSaverConfig.get().plannerMaxWidth), this.width - 2 * Panel.GAP);
		List<Panel.Row> rows = new ArrayList<>();
		lines.forEach(line -> Panel.wrap(rows, font, line, Panel.textWidth(width), Panel.text(), -1));
		panel.setRows(rows);
		panel.placeCentered(this.width, this.height, width);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		panel.draw(g, font, mouseX, mouseY);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		return panel.scroll(x, y, scrollY) || super.mouseScrolled(x, y, scrollX, scrollY);
	}

	/** The lines to show now; starts the plan in the background when there is one to make. */
	private List<Component> start(Minecraft mc) {
		List<Component> out = new ArrayList<>();
		out.add(Component.literal("Cheapest anvil order").withStyle(ChatFormatting.GOLD));
		Inventory inv = mc.player.getInventory();
		Registry<Enchantment> registry = Enchants.registry(mc);
		ItemStack target = pickTarget(mc.player.getMainHandItem(), inv, registry);
		if (target == null) {
			out.add(Component.literal("Hold the item to enchant (or carry one), plus enchanted books.").withStyle(ChatFormatting.GRAY));
			return out;
		}
		out.add(Component.literal("Item: ").append(target.getHoverName()));
		String category = Enchants.guideCategory(target);
		if (category == null) {
			out.add(Component.literal("The best enchantments guide has no section for this item.").withStyle(ChatFormatting.GRAY));
			return out;
		}
		Piece targetPiece = Enchants.piece(target, registry);
		Map<String, EnchantInfo> infos = Enchants.infos(registry);
		List<String> wanted = Enchants.wanted(target, registry, false);
		// What the guide wants that is already done, or can't go on (a conflicting enchantment is on the item).
		List<String> done = new ArrayList<>();
		List<String> blocked = new ArrayList<>();
		for (String id : wanted) {
			EnchantInfo info = infos.get(id);
			if (info == null) {
				continue;
			}
			Upgrade.Effect goal = Upgrade.effect(targetPiece, id, info.maxLevel(), infos);
			if (goal.kind() == Upgrade.Kind.MAXED) {
				done.add(Enchants.fullName(registry, id, goal.from()));
			} else if (goal.kind() == Upgrade.Kind.CONFLICTS) {
				blocked.add(Enchants.name(registry, id) + " (" + Enchants.fullName(registry, goal.conflictWith(),
						targetPiece.enchants().get(goal.conflictWith())) + " is on it)");
			}
		}
		// Every enchanted book and enchanted copy of the item in the inventory, except ones that would add a curse.
		List<Piece> pieces = new ArrayList<>();
		List<String> labels = new ArrayList<>();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (Enchants.isCandidate(stack, target) && Enchants.curseAdded(stack, target) == null) {
				pieces.add(Enchants.piece(stack, registry));
				labels.add(Enchants.label(stack));
			}
		}
		String name = target.getHoverName().getString();
		int level = Xp.level(mc);
		List<Component> header = List.copyOf(out);
		out.add(Component.literal("Working out the cheapest order\u2026").withStyle(ChatFormatting.GRAY));
		pending = CompletableFuture.supplyAsync(() -> {
			Selector.Result result = Selector.choose(targetPiece, name, pieces, labels, wanted, infos);
			return finish(header, result, registry, level, done, blocked);
		}, Util.backgroundExecutor());
		return out;
	}

	/** The full text once the plan is ready. Runs on the background thread: plain strings and components only, no world access. */
	private static List<Component> finish(List<Component> header, Selector.Result result, Registry<Enchantment> registry, int level,
			List<String> done, List<String> blocked) {
		List<Component> out = new ArrayList<>(header);
		Planner.Plan plan = result.plan();
		if (plan.steps().isEmpty()) {
			out.add(Component.literal(result.dropped().isEmpty() ? "No book in your inventory improves this item further."
					: "No order keeps every step at or under " + Planner.maxStepCost + " levels.").withStyle(ChatFormatting.GRAY));
		} else {
			List<Integer> costs = plan.steps().stream().map(Planner.Step::cost).toList();
			int affordable = Xp.affordable(level, costs);
			for (Planner.Step s : plan.steps()) {
				boolean shortOfXp = s.number() > affordable;
				out.add(Component.literal(s.number() + ". " + readable(s.left()) + " + " + readable(s.right()) + ": " + s.cost() + " levels"
						+ (shortOfXp ? " (not enough XP)" : "")).withStyle(shortOfXp ? ChatFormatting.RED : ChatFormatting.WHITE));
			}
			MutableComponent total = Component.literal("Total: " + plan.total() + " levels (every step \u2264 " + Planner.maxStepCost + ")")
					.withStyle(ChatFormatting.GREEN);
			out.add(total);
			if (plan.approximate()) {
				out.add(Component.literal("Approximate: too many books to check every combination, so a cheaper order may exist.")
						.withStyle(ChatFormatting.YELLOW));
			}
			Component xp = Xp.line(level, costs, 1);
			if (xp != null) {
				out.add(xp);
			}
		}
		if (!result.dropped().isEmpty()) {
			out.add(Component.literal("Left out to stay within " + Planner.maxStepCost + " levels per step:").withStyle(ChatFormatting.RED));
			result.dropped().forEach(g -> out.add(Component.literal("  " + Enchants.fullName(registry, g.id(), g.level())).withStyle(ChatFormatting.RED)));
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

	/** Planner labels as short words: "result of step 3" -> "step 3 result". */
	private static String readable(String label) {
		return label.startsWith("result of step ") ? "step " + label.substring("result of step ".length()) + " result" : label;
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
}
