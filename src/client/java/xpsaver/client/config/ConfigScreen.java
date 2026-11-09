package xpsaver.client.config;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * XP Saver settings, built from the same widgets as the game's own option screens (OptionsSubScreen, OptionInstance sliders and
 * cycle buttons, EditBox). Every change is applied at once (features read the config every frame) and saved to
 * config/xpsaver.json when the screen is left. Nothing needs a restart.
 */
public final class ConfigScreen extends OptionsSubScreen {
	private static final String KEY = "xpsaver.config.";
	/** Below this contrast ratio, text is hard to read for many players (WCAG AA for normal text). */
	private static final double MIN_CONTRAST = 4.5;

	public ConfigScreen(Screen parent) {
		super(parent, Minecraft.getInstance().options, Component.translatable(KEY + "title"));
	}

	@Override
	protected void addOptions() {
		XpSaverConfig c = XpSaverConfig.get();
		list.addHeader(Component.translatable(KEY + "live"));

		list.addHeader(Component.translatable(KEY + "features"));
		list.addSmall(
				bool("table_panel", "Shows the recommended enchantments beside the enchanting table (F1).", true,
						c.tablePanel, v -> c.tablePanel = v),
				bool("table_offers", "Lists the table's three offers under the recommendations, marked ✔ when one is recommended.",
						true, c.tableOffers, v -> c.tableOffers = v),
				bool("anvil_mark", "Shows ✔ or ✘ next to the anvil's output slot; hover it for the reason (F2).", true,
						c.anvilMark, v -> c.anvilMark = v),
				bool("recommended_dropdown", "Shows the \"Recommended enchantments\" button and list beside the anvil.", true,
						c.recommendedDropdown, v -> c.recommendedDropdown = v),
				bool("cheapest_order_dropdown", "Shows the \"Cheapest order\" button and list beside the anvil, with Next step.", true,
						c.cheapestOrderDropdown, v -> c.cheapestOrderDropdown = v));

		list.addHeader(Component.translatable(KEY + "position"));
		list.addSmall(
				choice("anchor", "Which side of the window the panels and anvil buttons go on. Left keeps them clear of item-list mods "
						+ "such as JEI and REI, which sit on the right.", XpSaverConfig.Anchor.class, XpSaverConfig.Anchor.LEFT,
						c.anchor, v -> c.anchor = v),
				choice("button_layout", "Auto stacks the anvil buttons beside the window when there is room, otherwise puts them in a "
						+ "row above it. The other two force one layout.", XpSaverConfig.ButtonLayout.class,
						XpSaverConfig.ButtonLayout.AUTO, c.buttonLayout, v -> c.buttonLayout = v),
				slider("offset_x", "Moves the panels and anvil buttons left (minus) or right (plus). Panels stay on screen.",
						XpSaverConfig.OFFSET_MIN, XpSaverConfig.OFFSET_MAX, 0, " px", c.offsetX, v -> c.offsetX = v),
				slider("offset_y", "Moves the panels and anvil buttons up (minus) or down (plus). Panels stay on screen.",
						XpSaverConfig.OFFSET_MIN, XpSaverConfig.OFFSET_MAX, 0, " px", c.offsetY, v -> c.offsetY = v),
				slider("panel_width", "Widest the anvil panel gets when there is room. Narrower screens use less.",
						XpSaverConfig.PANEL_WIDTH_MIN, XpSaverConfig.PANEL_WIDTH_MAX, 170, " px", c.panelWidth, v -> c.panelWidth = v),
				choice("panel_height", "Match window ends the panels level with the bottom of the window, clear of JEI's corner "
						+ "buttons. Full screen height uses the space below too.", XpSaverConfig.PanelHeight.class,
						XpSaverConfig.PanelHeight.MATCH_WINDOW, c.panelHeight, v -> c.panelHeight = v),
				slider("mark_offset_x", "Moves the anvil ✔/✘ mark left or right from its place beside the output slot.",
						XpSaverConfig.MARK_OFFSET_MIN, XpSaverConfig.MARK_OFFSET_MAX, 0, " px", c.markOffsetX, v -> c.markOffsetX = v),
				slider("mark_offset_y", "Moves the anvil ✔/✘ mark up or down from its place beside the output slot.",
						XpSaverConfig.MARK_OFFSET_MIN, XpSaverConfig.MARK_OFFSET_MAX, 0, " px", c.markOffsetY, v -> c.markOffsetY = v),
				slider("planner_width", "Widest the K planner window gets, so its lines stay easy to read on large screens.",
						XpSaverConfig.PLANNER_WIDTH_MIN, XpSaverConfig.PLANNER_WIDTH_MAX, 360, " px", c.plannerMaxWidth,
						v -> c.plannerMaxWidth = v));

		list.addHeader(Component.translatable(KEY + "text"));
		list.addSmall(
				choice("text_size", "Auto draws the panel text smaller (but sharp) when the space beside the window is narrow. "
						+ "Never shrink keeps full size and wraps lines instead.", XpSaverConfig.TextSize.class,
						XpSaverConfig.TextSize.AUTO, c.textSize, v -> c.textSize = v),
				slider("shrink_width", "With Auto text size: how narrow the space may get before the text is drawn smaller.",
						XpSaverConfig.SHRINK_WIDTH_MIN, XpSaverConfig.SHRINK_WIDTH_MAX, 100, " px", c.shrinkWidth, v -> c.shrinkWidth = v),
				slider("line_spacing", "Height of each line in the panels. More space is easier to scan.",
						XpSaverConfig.LINE_MIN, XpSaverConfig.LINE_MAX, 10, " px", c.lineSpacing, v -> c.lineSpacing = v),
				slider("scroll_lines", "How many lines one turn of the mouse wheel scrolls a panel.",
						XpSaverConfig.SCROLL_MIN, XpSaverConfig.SCROLL_MAX, 2, "", c.scrollLines, v -> c.scrollLines = v),
				bool("text_shadow", "Adds a shadow under panel text. Off matches the game's own window labels and reads better on the "
						+ "light panel.", false, c.textShadow, v -> c.textShadow = v));

		list.addHeader(Component.translatable(KEY + "look"));
		list.addSmall(
				choice("frame", "Inventory window draws the game's beveled window edge. Flat draws only the panel color.",
						XpSaverConfig.Frame.class, XpSaverConfig.Frame.INVENTORY, c.frame, v -> c.frame = v),
				slider("opacity", "How solid the panels are. Below 100% the world shows through, which lowers text contrast.",
						XpSaverConfig.OPACITY_MIN, XpSaverConfig.OPACITY_MAX, 100, "%", c.opacity, v -> c.opacity = v));
		color("face", "Panel color, behind all the text.", "C6C6C6", () -> c.faceColor, v -> c.faceColor = v);
		color("text", "Normal text and headings.", "404040", () -> c.textColor, v -> c.textColor = v);
		color("hint", "Hints and enchantment names.", "4C4C4C", () -> c.hintColor, v -> c.hintColor = v);
		color("dimmed", "Steps already done and choices that are blocked (their ✔ or ✘ says which).", "525252",
				() -> c.dimmedColor, v -> c.dimmedColor = v);
		color("green", "Ticked books, totals, the ✔ mark and \"has it\".", "125A12", () -> c.greenColor, v -> c.greenColor = v);
		color("red", "Warnings, books left out, the ✘ mark.", "9A1A1A", () -> c.redColor, v -> c.redColor = v);
		color("amber", "The step in the anvil now, and books that do nothing on their own.", "6B4000",
				() -> c.amberColor, v -> c.amberColor = v);
		color("teal", "Upgrades (↑) and \"pick one\" groups.", "0A5562", () -> c.tealColor, v -> c.tealColor = v);

		list.addHeader(Component.translatable(KEY + "planner"));
		list.addBig(slider("max_step_cost", "Most levels any single anvil step in a plan may cost. The anvil refuses 40 or "
				+ "more (\"Too Expensive!\"); lower values keep each step affordable but may leave enchantments out.",
				XpSaverConfig.STEP_COST_MIN, XpSaverConfig.STEP_COST_MAX, 29, " levels", c.maxStepCost, v -> c.maxStepCost = v));
	}

	@Override
	protected void addFooter() {
		LinearLayout row = layout.addToFooter(LinearLayout.horizontal().spacing(8));
		row.addChild(Button.builder(Component.translatable(KEY + "reset"), b -> confirmReset()).width(150)
				.tooltip(Tooltip.create(Component.literal("Puts every XP Saver setting back to its default. Asks first."))).build());
		row.addChild(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).width(150).build());
	}

	private void confirmReset() {
		minecraft.gui.setScreen(new ConfirmScreen(yes -> {
			if (yes) {
				XpSaverConfig.resetToDefaults();
			}
			minecraft.gui.setScreen(new ConfigScreen(lastScreen)); // rebuilt, so every control shows the current values
		}, Component.translatable(KEY + "reset.title"), Component.translatable(KEY + "reset.message")));
	}

	/** Saves whenever the screen is left (Done, Esc, or the reset question). */
	@Override
	public void removed() {
		super.removed();
		XpSaverConfig.save();
	}

	private static void changed() {
		XpSaverConfig.get().validate(); // recomputes colors and the planner budget; features pick it up next frame
	}

	private static Component tooltip(String text, String defaultValue) {
		return Component.literal(text + "\n\nDefault: " + defaultValue + ". Applies right away.");
	}

	private static OptionInstance<Boolean> bool(String key, String tip, boolean def, boolean value, Consumer<Boolean> set) {
		return OptionInstance.createBoolean(KEY + key, OptionInstance.cachedConstantTooltip(tooltip(tip, def ? "On" : "Off")), value,
				v -> {
					set.accept(v);
					changed();
				});
	}

	private static OptionInstance<Integer> slider(String key, String tip, int min, int max, int def, String unit, int value,
			IntConsumer set) {
		String range = " Range: " + min + " to " + max + unit + ".";
		return new OptionInstance<>(KEY + key, OptionInstance.cachedConstantTooltip(tooltip(tip + range, def + unit)),
				(caption, v) -> Options.genericValueLabel(caption, Component.literal(v + unit)), new OptionInstance.IntRange(min, max),
				value, v -> {
					set.accept(v);
					changed();
				});
	}

	private static <E extends Enum<E>> OptionInstance<E> choice(String key, String tip, Class<E> type, E def, E value, Consumer<E> set) {
		return new OptionInstance<>(KEY + key, OptionInstance.cachedConstantTooltip(tooltip(tip, name(def).getString())),
				(caption, v) -> name(v), // the cycle button adds "Caption: " itself
				new OptionInstance.Enum<>(List.of(type.getEnumConstants()), Codec.STRING.xmap(s -> Enum.valueOf(type, s), Enum::name)),
				value, v -> {
					set.accept(v);
					changed();
				});
	}

	private static Component name(Enum<?> value) {
		return Component.translatable(KEY + "value." + value.name().toLowerCase(Locale.ROOT));
	}

	/** One color: a live preview on the panel color (with its contrast ratio) on the left, the hex code on the right. */
	private void color(String key, String tip, String def, Supplier<String> get, Consumer<String> set) {
		Component name = Component.translatable(KEY + "color." + key);
		Preview preview = new Preview(name, key.equals("face"), get);
		EditBox box = new EditBox(font, 150, 20, name);
		box.setMaxLength(7);
		box.setValue(get.get());
		box.setHint(Component.literal("RRGGBB"));
		Runnable refresh = () -> box.setTooltip(Tooltip.create(colorTooltip(tip, def, preview.ratio())));
		box.setResponder(s -> {
			boolean valid = XpSaverConfig.isHex(s);
			box.setTextColor(valid ? 0xFFE0E0E0 : 0xFFFF6060); // the box's own text color, or red while the code is not RRGGBB
			if (valid) {
				set.accept(s);
				changed();
			}
			refresh.run();
		});
		refresh.run();
		list.addSmall(preview, box);
	}

	private static Component colorTooltip(String tip, String def, double ratio) {
		String verdict = ratio >= MIN_CONTRAST ? "easy to read" : "too low: hard to read for many players, use " + MIN_CONTRAST + ":1 or more";
		return Component.literal(tip + "\n\nSix hex digits, RRGGBB (a leading # is fine). Default: " + def + ".\nContrast on the panel: "
				+ String.format(Locale.ROOT, "%.1f", ratio) + ":1, " + verdict + ". Applies right away.");
	}

	/**
	 * Not a button: a sample of the color as it appears in the panels, on the panel color, with its contrast ratio, so the
	 * effect of a change is visible without hovering anything. The panel-color row previews normal text on the new color.
	 */
	private static final class Preview extends AbstractWidget {
		private final boolean face;
		private final Supplier<String> hex;

		Preview(Component name, boolean face, Supplier<String> hex) {
			super(0, 0, 150, 20, name);
			this.face = face;
			this.hex = hex;
			this.active = false;
		}

		private int sampleColor() {
			return face ? XpSaverConfig.get().text : Integer.parseInt(hex.get(), 16);
		}

		double ratio() {
			return XpSaverConfig.contrast(sampleColor(), XpSaverConfig.get().face);
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
			XpSaverConfig c = XpSaverConfig.get();
			int x = getX();
			int y = getY();
			g.fill(x, y, x + width, y + height, 0xFF000000);
			g.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF000000 | c.face);
			Minecraft mc = Minecraft.getInstance();
			g.text(mc.font, getMessage(), x + 5, y + 6, 0xFF000000 | sampleColor(), false);
			double ratio = ratio();
			String label = String.format(Locale.ROOT, "%.1f:1", ratio) + (ratio < MIN_CONTRAST ? " ⚠" : "");
			g.text(mc.font, label, x + width - 5 - mc.font.width(label), y + 6, 0xFF000000 | (ratio < MIN_CONTRAST ? c.red : c.text), false);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}
}
