package xpsaver.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import xpsaver.client.config.XpSaverConfig;
import xpsaver.core.EnchantInfo;
import xpsaver.core.Guide;
import xpsaver.core.Piece;
import xpsaver.core.Planner;
import xpsaver.core.Upgrade;

/**
 * Two dropdowns left of the anvil (clear of item-list mods on the right), one open at a time:
 * "Recommended enchantments" for the item in the left slot (same list as the enchanting table panel), and
 * "Cheapest order": tick enchanted books, see the cheapest order (every step within Planner.maxStepCost), and press
 * "Next step" to move that step's two inputs into the anvil. The player still takes each result, which is when XP is charged.
 * Drawn as vanilla container panels (see Panel). Long content scrolls with the mouse wheel. On narrow screens the buttons move to a
 * row above the anvil and the panel draws its text smaller rather than squeezing it. Which dropdowns show, their side, offsets,
 * width and button layout come from XpSaverConfig and apply live.
 * Hooks (Fabric API 0.165.2+26.3): Screens.getWidgets, ScreenEvents.afterForeground, ScreenMouseEvents.allowMouseClick /
 * allowMouseScroll. Item moves use MultiPlayerGameMode#handleContainerInput (26.3), the same packets as clicking by hand.
 */
final class AnvilPlanner {
	/** AnvilMenu (26.3): slots 0 and 1 are inputs, 2 is the result, 3..38 are the player's inventory and hotbar. */
	private static final int FIRST_INVENTORY_SLOT = 3;
	private static final int BUTTON_H = 16;
	/** Narrowest side column that still holds the buttons stacked; below it they go in a row above the anvil (button layout Auto). */
	private static final int STACK_MIN = 100;
	private static final String TICKED = "[✔] ";
	private static final String UNTICKED = "[  ] ";
	private static final String BLOCKED = "[\u2718] ";

	private enum Open { NONE, RECOMMENDED, ORDER }

	private final AnvilScreen screen;
	private final Minecraft mc = Minecraft.getInstance();
	private final Set<Integer> checked = new HashSet<>(); // menu slot indices of ticked books
	private final Button recToggle;
	private final Button orderToggle;
	private final Button next;
	private Open open = Open.NONE;

	private List<Piece> planKey = List.of(); // inputs the cached plan was made for
	private int planBudget; // Planner.maxStepCost the cached plan was made for
	private Planner.Plan plan;
	private List<String> droppedLabels = List.of();
	// Planning runs on the game's background executor so ticking books never stalls a frame; the result is picked up per frame.
	private CompletableFuture<Planner.Plan> pending;
	private List<String> pendingLabels = List.of();
	private boolean planFailed;
	private boolean running; // plan locked, steps being loaded
	private int nextStep;
	private Component message;

	// The open dropdown, kept from the last frame for mouse handling. Row tags are inventory slots of book rows.
	private final Panel panel = new Panel();
	private boolean stacked; // buttons stacked left of the anvil (wide screens) rather than in a row above it
	private List<Integer> bookSlots = List.of();
	// What each inventory book would do to the item in the left slot, judged before the steps start.
	private Piece targetPiece;
	private final Map<Integer, List<Upgrade.Effect>> effects = new HashMap<>();
	private final Set<Integer> blocked = new HashSet<>(); // books that can only waste levels on this item

	private AnvilPlanner(AnvilScreen screen) {
		this.screen = screen;
		this.recToggle = Button.builder(Component.empty(), b -> toggle(Open.RECOMMENDED)).bounds(0, 0, 10, BUTTON_H).build();
		this.orderToggle = Button.builder(Component.empty(), b -> toggle(Open.ORDER)).bounds(0, 0, 10, BUTTON_H).build();
		this.next = Button.builder(Component.literal("Next step \u25B6"), b -> loadNextStep()).bounds(0, 0, 10, BUTTON_H).build();
	}

	static void attach(AnvilScreen screen) {
		AnvilPlanner p = new AnvilPlanner(screen);
		Screens.getWidgets(screen).add(p.recToggle);
		Screens.getWidgets(screen).add(p.orderToggle);
		Screens.getWidgets(screen).add(p.next);
		p.layout();
		p.next.visible = false;
		ScreenEvents.afterForeground(screen).register((s, g, mouseX, mouseY, delta) -> p.draw(g, mouseX, mouseY));
		// While an item is on the cursor, the mod's UI ignores the whole click (press and release). Letting a button take the press
		// but hiding the release leaves the screen mid-click, and the next click on a slot is lost.
		ScreenMouseEvents.allowMouseClick(screen).register((s, event) ->
				screen.getMenu().getCarried().isEmpty() ? !p.clickBook(event.x(), event.y()) : !p.overUi(event.x(), event.y()));
		ScreenMouseEvents.allowMouseScroll(screen).register((s, mouseX, mouseY, h, v) -> !p.panel.scroll(mouseX, mouseY, v));
		// The screen focuses a clicked button after its action runs, so give focus back to the rename box once the click is done.
		ScreenMouseEvents.afterMouseClick(screen).register((s, event, consumed) -> {
			GuiEventListener focused = screen.getFocused();
			if (focused == p.recToggle || focused == p.orderToggle || focused == p.next) {
				p.refocusName();
			}
			return consumed;
		});
		// The screen drops the cursor item when the mouse is released outside its window (AbstractContainerScreen#mouseReleased,
		// slot -999). This mod's buttons and panel sit outside it, so a release over them must not count as outside.
		ScreenMouseEvents.allowMouseRelease(screen).register((s, event) ->
				screen.getMenu().getCarried().isEmpty() || !p.overUi(event.x(), event.y()));
	}

	private void toggle(Open which) {
		open = open == which ? Open.NONE : which;
		panel.scrollToTop();
	}

	/**
	 * Clicking a button gives it keyboard focus, which takes it from the anvil's rename box (typed letters then go nowhere).
	 * Hand focus back, as the anvil has it when opened.
	 */
	private void refocusName() {
		for (GuiEventListener child : screen.children()) {
			if (child instanceof EditBox box) {
				screen.setFocused(box);
				return;
			}
		}
	}

	/** The mod's buttons that are switched on in the config, top to bottom; Next step belongs to Cheapest order. */
	private List<Button> shownButtons() {
		XpSaverConfig cfg = XpSaverConfig.get();
		List<Button> shown = new ArrayList<>();
		if (cfg.recommendedDropdown) {
			shown.add(recToggle);
		}
		if (cfg.cheapestOrderDropdown) {
			shown.add(orderToggle);
			shown.add(next);
		}
		return shown;
	}

	/**
	 * Places the buttons: stacked beside the anvil window when the side column is wide enough (or the config says so), else in a
	 * row above the window, then moved by the configured offset. Done on attach too, so they never flash at the screen corner.
	 */
	private void layout() {
		XpSaverConfig cfg = XpSaverConfig.get();
		recToggle.visible = cfg.recommendedDropdown;
		orderToggle.visible = cfg.cheapestOrderDropdown;
		if ((open == Open.RECOMMENDED && !cfg.recommendedDropdown) || (open == Open.ORDER && !cfg.cheapestOrderDropdown)) {
			open = Open.NONE; // switched off in the config while open
		}
		int top = Panel.topPos(screen.height);
		int room = Panel.sideRoom(screen.width);
		stacked = cfg.buttonLayout == XpSaverConfig.ButtonLayout.STACKED
				|| (cfg.buttonLayout == XpSaverConfig.ButtonLayout.AUTO && room >= STACK_MIN);
		List<Button> shown = shownButtons();
		if (stacked) {
			int w = Math.max(40, Math.min(cfg.panelWidth, room));
			int bx = onScreen(Panel.sideX(screen.width, w) + cfg.offsetX, w);
			for (int i = 0; i < shown.size(); i++) {
				shown.get(i).setRectangle(w, BUTTON_H, bx, Math.max(0, top + cfg.offsetY + i * (BUTTON_H + 2)));
			}
		} else if (!shown.isEmpty()) {
			int total = Math.min(screen.width - 2 * Panel.GAP, 110 * shown.size());
			int cell = (total - 2 * (shown.size() - 1)) / shown.size();
			int bx = onScreen((screen.width - total) / 2 + cfg.offsetX, total);
			int by = Math.max(2, top - BUTTON_H - 2 + cfg.offsetY);
			for (int i = 0; i < shown.size(); i++) {
				shown.get(i).setRectangle(cell, BUTTON_H, bx + i * (cell + 2), by);
			}
		}
		// Longest label that fits; a button scrolls text that doesn't, which is hard to read.
		String recArrow = open == Open.RECOMMENDED ? " \u25B2" : " \u25BC";
		String orderArrow = open == Open.ORDER ? " \u25B2" : " \u25BC";
		recToggle.setMessage(label(recToggle, "Recommended enchantments" + recArrow, "Recommended" + recArrow, "Enchants" + recArrow));
		orderToggle.setMessage(label(orderToggle, "Cheapest order" + orderArrow, "Cheapest" + orderArrow));
		next.setMessage(label(next, "Next step \u25B6", "Next \u25B6"));
	}

	/** Keeps something {@code width} wide inside the screen horizontally. */
	private int onScreen(int x, int width) {
		return Math.max(0, Math.min(screen.width - width, x));
	}

	private Component label(Button button, String... options) {
		for (String option : options) {
			if (mc.font.width(option) + 8 <= button.getWidth()) {
				return Component.literal(option);
			}
		}
		return Component.literal(options[options.length - 1]);
	}

	private void draw(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		layout();
		next.visible = false;
		panel.setRows(List.of());
		if (open == Open.NONE || mc.level == null) {
			return;
		}
		Registry<Enchantment> registry = Enchants.registry(mc);
		AnvilMenu menu = screen.getMenu();
		int y = Panel.topPos(screen.height);
		int toggles = (recToggle.visible ? 1 : 0) + (orderToggle.visible ? 1 : 0);
		int top = stacked ? y + toggles * (BUTTON_H + 2) : y;
		if (open == Open.ORDER) {
			refreshPlan(menu, registry);
			next.visible = plan != null && nextStep < plan.steps().size();
			if (stacked && next.visible) {
				top += BUTTON_H + 2;
			}
		}
		// By default end level with the bottom of the anvil window and scroll the rest: the screen corner below it is where
		// JEI (and other mods) put their buttons, which would cover the last lines.
		if (!panel.placeBeside(screen.width, screen.height, top, Panel.bottom(screen.height), XpSaverConfig.get().panelWidth)) {
			return;
		}
		int textWidth = Panel.textWidth(panel.width());
		panel.setRows(open == Open.RECOMMENDED ? recommendedRows(menu, registry, mc.font, textWidth) : orderRows(menu, mc.font, textWidth));
		panel.draw(g, mc.font, mouseX, mouseY);
	}

	private List<Panel.Row> recommendedRows(AnvilMenu menu, Registry<Enchantment> registry, Font font, int textWidth) {
		ItemStack item = menu.getSlot(AnvilMenu.INPUT_SLOT).getItem();
		List<Component> lines = new ArrayList<>();
		Map<Component, List<String>> ids = new IdentityHashMap<>();
		if (item.isEmpty()) {
			lines.add(Component.literal("Put an item in the left slot to see its recommended enchantments.").withStyle(ChatFormatting.GRAY));
		} else {
			lines.add(Component.literal("For: ").withStyle(ChatFormatting.GOLD).append(item.getHoverName()));
			if (EnchantPanel.addRecommendations(item, registry, lines, ids).isEmpty() && !item.is(Items.ENCHANTED_BOOK)) {
				lines.add(Component.literal("This item can't be enchanted.").withStyle(ChatFormatting.GRAY));
			}
		}
		List<Panel.Row> out = new ArrayList<>();
		lines.forEach(line -> Panel.wrap(out, font, line, textWidth, Panel.text(), -1,
				EnchantTips.of(registry, ids.getOrDefault(line, List.of()))));
		return out;
	}

	private List<Panel.Row> orderRows(AnvilMenu menu, Font font, int textWidth) {
		List<Panel.Row> out = new ArrayList<>();
		Panel.wrap(out, font, Component.literal("Tick the books to use:").withStyle(ChatFormatting.GOLD), textWidth, Panel.text(), -1);
		if (bookSlots.isEmpty()) {
			Panel.wrap(out, font, Component.literal("No enchanted books in your inventory.").withStyle(ChatFormatting.GRAY), textWidth, Panel.text(), -1);
		}
		int widestBox = Math.max(font.width(BLOCKED), Math.max(font.width(TICKED), font.width(UNTICKED)));
		Registry<Enchantment> registry = Enchants.registry(mc);
		for (int slot : bookSlots) {
			boolean off = blocked.contains(slot);
			boolean on = checked.contains(slot);
			String box = off ? BLOCKED : on ? TICKED : UNTICKED;
			Component line = Component.literal(box + bookName(menu.getSlot(slot).getItem()));
			// Wrap as if the box were always the widest one, so ticking never re-wraps a row and moves the rows below it.
			int wrapWidth = textWidth - (widestBox - font.width(box));
			List<String> ids = net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantmentsForCrafting(menu.getSlot(slot).getItem())
					.keySet().stream().map(Enchants::id).toList();
			Panel.wrap(out, font, line, wrapWidth, off ? Panel.dimmed() : on ? Panel.green() : Panel.text(), off ? -1 : slot,
					EnchantTips.of(registry, ids));
			if (!running && targetPiece != null) {
				Component why = effectLine(registry, effects.getOrDefault(slot, List.of()));
				if (why != null) {
					Panel.indented(out, font, why, textWidth, widestBox, off ? -1 : slot); // lined up under the book name
				}
			}
		}
		out.add(new Panel.Row(FormattedCharSequence.EMPTY, Panel.text(), -1));

		List<Component> lines = new ArrayList<>();
		if (plan == null) {
			String hint = pending != null ? "Working out the cheapest order\u2026"
					: planFailed ? "Couldn't work out an order for these books."
					: !menu.getSlot(AnvilMenu.INPUT_SLOT).hasItem() ? "Put the item to enchant in the left slot."
					: menu.getSlot(AnvilMenu.INPUT_SLOT).getItem().is(Items.ENCHANTED_BOOK)
							? "A book is in the left slot: put the item to enchant there instead."
					: !bookSlots.isEmpty() && blocked.containsAll(bookSlots) ? "None of these books can improve this item."
					: "Tick at least one book.";
			lines.add(Component.literal(hint).withStyle(ChatFormatting.GRAY));
		} else {
			lines.add(Component.literal("Cheapest order (at most " + Planner.maxStepCost + " levels per step):").withStyle(ChatFormatting.GOLD));
			boolean finished = running && nextStep == plan.steps().size() && !menu.getSlot(AnvilMenu.INPUT_SLOT).hasItem()
					&& !menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).hasItem(); // last result taken
			if (finished) {
				message = Component.literal("Done: all " + plan.steps().size() + " steps applied.").withStyle(ChatFormatting.GREEN);
			}
			int current = finished ? plan.steps().size() : running ? nextStep - 1 : -1; // step now in the anvil
			for (Planner.Step s : plan.steps()) {
				int i = s.number() - 1;
				if (i > 0) {
					lines.add(Component.empty()); // a blank line between steps, so each reads as its own block
				}
				String mark = i < current ? "\u2714 " : i == current ? "\u25B6 " : "";
				ChatFormatting style = i < current ? ChatFormatting.DARK_GRAY : i == current ? ChatFormatting.YELLOW : ChatFormatting.WHITE;
				lines.add(Component.literal(mark + "Step " + s.number() + ": " + s.cost() + " levels").withStyle(style));
				lines.add(Component.literal("   " + readable(s.left()) + " + " + readable(s.right()))
						.withStyle(i < current ? ChatFormatting.DARK_GRAY : ChatFormatting.GRAY));
			}
			lines.add(Component.literal("Total: " + plan.total() + " levels").withStyle(ChatFormatting.GREEN));
			if (!droppedLabels.isEmpty()) {
				lines.add(Component.literal("Left out to keep every step at or under " + Planner.maxStepCost + " levels:")
						.withStyle(ChatFormatting.RED));
				droppedLabels.forEach(l -> lines.add(Component.literal("   " + l).withStyle(ChatFormatting.RED)));
			}
			if (!running && !plan.steps().isEmpty()) {
				lines.add(Component.literal("Press Next step to put step 1 in the anvil.").withStyle(ChatFormatting.GRAY));
			}
		}
		if (message != null) {
			lines.add(message);
		}
		lines.forEach(line -> Panel.wrap(out, font, line, textWidth, Panel.text(), -1));
		return out;
	}

	/**
	 * Under a book row: what it does to the item, when that isn't simply "adds": upgrades in aqua, "nothing on its own" in yellow,
	 * reasons it can't be used in grey. Null for books that only add new enchantments.
	 */
	private Component effectLine(Registry<Enchantment> registry, List<Upgrade.Effect> list) {
		List<Upgrade.Effect> notable = list.stream().filter(e -> e.kind() != Upgrade.Kind.ADDS).toList();
		if (notable.isEmpty()) {
			return null;
		}
		List<String> parts = new ArrayList<>();
		for (Upgrade.Effect e : notable) {
			String text = Enchants.describe(registry, targetPiece, e);
			// Name the enchantment when a book holds several and the description doesn't already say which.
			parts.add(list.size() > 1 && e.kind() != Upgrade.Kind.RAISES ? Enchants.name(registry, e.id()) + ": " + text : text);
		}
		boolean useless = list.stream().noneMatch(Upgrade.Effect::couldHelp);
		ChatFormatting color = useless ? ChatFormatting.GRAY
				: notable.stream().anyMatch(Upgrade.Effect::helps) ? ChatFormatting.AQUA : ChatFormatting.YELLOW;
		return Component.literal(String.join("; ", parts)).withStyle(color);
	}

	/** Planner labels as words: "result of step 3" -> "the result of step 3". */
	private static String readable(String label) {
		return label.startsWith("result of step ") ? "the " + label : label;
	}

	/** True over the toggle buttons, the Next step button, or the open dropdown's panel. */
	private boolean overUi(double mouseX, double mouseY) {
		return panel.contains(mouseX, mouseY) || (recToggle.visible && recToggle.isMouseOver(mouseX, mouseY))
				|| (orderToggle.visible && orderToggle.isMouseOver(mouseX, mouseY)) || (next.visible && next.isMouseOver(mouseX, mouseY));
	}

	/** Takes a finished background plan, if it is still for the current item and ticked books. */
	private void collectPlan() {
		if (pending == null || !pending.isDone()) {
			return;
		}
		CompletableFuture<Planner.Plan> done = pending;
		pending = null;
		try {
			plan = done.join();
			droppedLabels = pendingLabels.subList(pendingLabels.size() - plan.dropped().size(), pendingLabels.size());
		} catch (RuntimeException e) {
			plan = null;
			planFailed = true;
		}
	}

	/** Re-plans only when the left-slot item or the ticked books change (planning takes a few ms, not per frame). */
	private void refreshPlan(AnvilMenu menu, Registry<Enchantment> registry) {
		collectPlan();
		List<Integer> slots = new ArrayList<>();
		for (int i = FIRST_INVENTORY_SLOT; i < menu.slots.size(); i++) {
			if (menu.getSlot(i).getItem().is(Items.ENCHANTED_BOOK)) {
				slots.add(i);
			}
		}
		bookSlots = slots;
		if (running) {
			return; // plan locked; ticked books move into the anvil as steps run
		}
		checked.retainAll(slots);

		ItemStack target = menu.getSlot(AnvilMenu.INPUT_SLOT).getItem();
		Map<String, EnchantInfo> infos = Enchants.infos(registry);
		// Judge every book against the item as it is now: a book that can only waste levels can't be ticked.
		targetPiece = target.isEmpty() || target.is(Items.ENCHANTED_BOOK) ? null : Enchants.piece(target, registry);
		effects.clear();
		blocked.clear();
		if (targetPiece != null) {
			for (int slot : slots) {
				List<Upgrade.Effect> list = Upgrade.of(targetPiece, Enchants.piece(menu.getSlot(slot).getItem(), registry), infos);
				effects.put(slot, list);
				if (list.stream().noneMatch(Upgrade.Effect::couldHelp)) {
					blocked.add(slot);
				}
			}
		}
		checked.removeAll(blocked);
		List<Integer> chosen = slots.stream().filter(checked::contains).toList();
		if (target.isEmpty() || target.is(Items.ENCHANTED_BOOK) || chosen.isEmpty()) {
			plan = null;
			pending = null;
			planKey = List.of();
			return;
		}
		// Ticked books in the guide's priority order, so if they don't all fit, the guide's lowest priorities are left out.
		String category = Enchants.guideCategory(target);
		List<String> priority = category == null ? List.of() : Guide.priority(category);
		List<Planner.Want> wants = new ArrayList<>();
		for (int slot : chosen) {
			ItemStack book = menu.getSlot(slot).getItem();
			Piece piece = Enchants.piece(book, registry);
			String best = piece.enchants().keySet().stream()
					.min(Comparator.comparingInt(id -> priority.contains(id) ? priority.indexOf(id) : Integer.MAX_VALUE)).orElse("");
			wants.add(new Planner.Want(best, piece, bookName(book) + " book"));
		}
		wants.sort(Comparator.comparingInt(w -> priority.contains(w.enchantId()) ? priority.indexOf(w.enchantId()) : Integer.MAX_VALUE));

		List<Piece> key = new ArrayList<>(List.of(targetPiece));
		wants.forEach(w -> key.add(w.book()));
		if (key.equals(planKey) && planBudget == Planner.maxStepCost) {
			return;
		}
		planKey = key;
		planBudget = Planner.maxStepCost; // a new budget from the config screen re-plans
		plan = null;
		planFailed = false;
		nextStep = 0;
		message = null;
		Piece t = targetPiece;
		String name = target.getHoverName().getString();
		List<Planner.Want> ws = List.copyOf(wants);
		pendingLabels = ws.stream().map(Planner.Want::label).toList();
		pending = CompletableFuture.supplyAsync(() -> Planner.planGuided(t, name, ws, infos), Util.backgroundExecutor());
	}

	/** Ticks or unticks a book row. Returns true for any click inside an open panel, so the screen never sees it as an outside click. */
	private boolean clickBook(double mouseX, double mouseY) {
		if (!panel.contains(mouseX, mouseY)) {
			return false;
		}
		int slot = open == Open.ORDER ? panel.tagAt(mouseX, mouseY) : -1; // the Recommended panel is text only
		if (slot < 0) {
			return true; // inside the panel but not a book: swallow it, or the screen treats it as a click outside and drops the cursor item
		}
		if (!checked.remove(slot)) {
			checked.add(slot);
		}
		running = false; // a new selection starts a new plan
		planKey = List.of();
		return true;
	}

	/** Puts the next step's two inputs into the anvil, as if the player clicked them there. */
	private void loadNextStep() {
		if (plan == null || nextStep >= plan.steps().size() || mc.player == null || mc.gameMode == null) {
			return;
		}
		AnvilMenu menu = screen.getMenu();
		if (!menu.getCarried().isEmpty()) {
			message = Component.literal("Put down the item on your cursor first.").withStyle(ChatFormatting.RED);
			return;
		}
		Registry<Enchantment> registry = Enchants.registry(mc);
		// Clear the input slots back into the inventory, then find a step's inputs there.
		for (int slot : new int[] {AnvilMenu.INPUT_SLOT, AnvilMenu.ADDITIONAL_SLOT}) {
			if (menu.getSlot(slot).hasItem()) {
				click(menu, slot, ContainerInput.QUICK_MOVE);
			}
		}
		// The planned step if its inputs are here; otherwise the earliest step whose inputs are all here. That recovers when a result
		// wasn't taken: its inputs came back, and a later step that needs the result can't be loaded until that step is redone.
		int index = -1;
		int[] found = null;
		for (int i : stepOrder()) {
			int l = find(menu, plan.steps().get(i).leftPiece(), -1, registry);
			int r = l < 0 ? -1 : find(menu, plan.steps().get(i).rightPiece(), l, registry);
			if (l >= 0 && r >= 0) {
				index = i;
				found = new int[] {l, r};
				break;
			}
		}
		if (index < 0) {
			Planner.Step step = plan.steps().get(Math.min(nextStep, plan.steps().size() - 1));
			message = Component.literal("Step " + step.number() + ": inputs not found. Take the result of the previous step "
					+ "(shift-click it) and keep the ticked books in your inventory.").withStyle(ChatFormatting.RED);
			return;
		}
		Planner.Step step = plan.steps().get(index);
		int left = found[0];
		int right = found[1];
		nextStep = index;
		if (menu.getSlot(AnvilMenu.INPUT_SLOT).hasItem() || menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).hasItem()) {
			message = Component.literal("Make room in your inventory: the anvil's input slots couldn't be emptied.")
					.withStyle(ChatFormatting.RED);
			return;
		}
		move(menu, left, AnvilMenu.INPUT_SLOT);
		move(menu, right, AnvilMenu.ADDITIONAL_SLOT);
		running = true;
		nextStep++;
		message = Component.literal("Step " + step.number() + " loaded: take the result (" + step.cost() + " levels).")
				.withStyle(ChatFormatting.YELLOW);
	}

	/** Step indices to try: the planned next step first, then every step from the start. */
	private List<Integer> stepOrder() {
		List<Integer> order = new ArrayList<>();
		if (nextStep < plan.steps().size()) {
			order.add(nextStep);
		}
		for (int i = 0; i < plan.steps().size(); i++) {
			if (i != nextStep) {
				order.add(i);
			}
		}
		return order;
	}

	private int find(AnvilMenu menu, Piece piece, int skip, Registry<Enchantment> registry) {
		for (int i = FIRST_INVENTORY_SLOT; i < menu.slots.size(); i++) {
			if (i != skip && Enchants.matches(menu.getSlot(i).getItem(), piece, registry)) {
				return i;
			}
		}
		return -1;
	}

	private void move(AnvilMenu menu, int from, int to) {
		click(menu, from, ContainerInput.PICKUP);
		click(menu, to, ContainerInput.PICKUP);
	}

	private void click(AnvilMenu menu, int slot, ContainerInput input) {
		mc.gameMode.handleContainerInput(menu.containerId, slot, 0, input, mc.player);
	}

	private static String bookName(ItemStack book) {
		List<String> names = new ArrayList<>();
		for (var entry : net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantmentsForCrafting(book).entrySet()) {
			names.add(Enchantment.getFullname(entry.getKey(), entry.getIntValue()).getString());
		}
		return String.join(", ", names);
	}
}
