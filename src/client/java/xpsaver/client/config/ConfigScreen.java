package xpsaver.client.config;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
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
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import xpsaver.client.XpSaverClient;

/**
 * XP Saver settings, built from the same widgets as the game's own option screens (OptionsSubScreen, OptionInstance sliders and
 * cycle buttons, EditBox). Every change is applied at once (features read the config every frame) and saved to
 * config/xpsaver.json when the screen is left. Nothing needs a restart. The color rows belong to the selected panel style;
 * switching style rebuilds the screen (at the same scroll position) to show that style's colors.
 */
public final class ConfigScreen extends OptionsSubScreen {
	private static final String KEY = "xpsaver.config.";
	/** Below this contrast ratio, text is hard to read for many players (WCAG AA for normal text). */
	private static final double MIN_CONTRAST = 4.5;
	private final double initialScroll;
	private boolean rebuild;
	private Button keyButton;
	private Button keyReset;
	private boolean listening; // the planner key button was clicked: the next key or mouse button becomes the planner key

	public ConfigScreen(Screen parent) {
		this(parent, 0);
	}

	private ConfigScreen(Screen parent, double scroll) {
		super(parent, Minecraft.getInstance().options, Component.translatable(KEY + "title"));
		this.initialScroll = scroll;
	}

	@Override
	protected void init() {
		super.init();
		if (list != null) {
			list.setScrollAmount(initialScroll);
		}
	}

	/** A style change is applied on the next tick, outside the click that made it. */
	@Override
	public void tick() {
		super.tick();
		if (rebuild && list != null) {
			minecraft.gui.setScreen(new ConfigScreen(lastScreen, list.scrollAmount()));
		}
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
				slider("planner_width", "Widest the planner window gets, so its lines stay easy to read on large screens.",
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
						XpSaverConfig.SCROLL_MIN, XpSaverConfig.SCROLL_MAX, 2, "", c.scrollLines, v -> c.scrollLines = v));

		list.addHeader(Component.translatable(KEY + "look"));
		boolean dark = c.style == XpSaverConfig.Style.DARK;
		XpSaverConfig.Palette p = c.palette();
		XpSaverConfig.Palette d = dark ? XpSaverConfig.Palette.dark() : XpSaverConfig.Palette.inventory();
		list.addSmall(
				choice("style", "Dark: the original see-through dark panel with light text. Inventory: a light panel drawn like the "
						+ "game's inventory window. Each style keeps its own colors below.", XpSaverConfig.Style.class,
						XpSaverConfig.Style.DARK, c.style, v -> {
							c.style = v;
							rebuild = true;
						}),
				choice("frame", "Inventory style only. Window edge draws the game's beveled window frame; Flat draws only the "
						+ "panel color.", XpSaverConfig.Frame.class, XpSaverConfig.Frame.BEVELED, c.frame, v -> c.frame = v));
		list.addHeader(Component.translatable(KEY + (dark ? "colors.dark" : "colors.inventory")));
		list.addSmall(
				slider("opacity", "How solid the panels are. Below 100% the world shows through; the contrast ratios below already "
						+ "count the brightest and darkest world.", XpSaverConfig.OPACITY_MIN, XpSaverConfig.OPACITY_MAX, d.opacity, "%",
						p.opacity, v -> p.opacity = v),
				bool("text_shadow", "Adds a shadow under panel text. On suits the dark style; off matches the game's own window labels "
						+ "on the light one.", d.textShadow, p.textShadow, v -> p.textShadow = v));
		color("face", "Panel color, behind all the text.", d.faceColor, () -> p.faceColor, v -> p.faceColor = v);
		color("heading", "Headings such as \"Tick the books to use:\" and \"Cheapest order\".", d.headingColor,
				() -> p.headingColor, v -> p.headingColor = v);
		color("text", "Normal text.", d.textColor, () -> p.textColor, v -> p.textColor = v);
		color("hint", "Hints, step details and enchantment names.", d.hintColor, () -> p.hintColor, v -> p.hintColor = v);
		color("dimmed", "Steps already done and choices that are blocked (their \u2714 or \u2718 says which).", d.dimmedColor,
				() -> p.dimmedColor, v -> p.dimmedColor = v);
		color("green", "Ticked books, totals and \u2714 done enchantments.", d.greenColor, () -> p.greenColor, v -> p.greenColor = v);
		color("red", "Warnings and books left out. (The anvil \u2718 mark uses the Inventory style's red: it sits on the light "
				+ "anvil window.)", d.redColor, () -> p.redColor, v -> p.redColor = v);
		color("amber", "The step in the anvil now, and books that do nothing on their own.", d.amberColor,
				() -> p.amberColor, v -> p.amberColor = v);
		color("teal", "Upgrades (\u2191) and \"pick one\" groups.", d.tealColor, () -> p.tealColor, v -> p.tealColor = v);

		list.addHeader(Component.translatable(KEY + "planner"));
		list.addBig(slider("max_step_cost", "Most levels any single anvil step in a plan may cost. The anvil refuses 40 or "
				+ "more (\"Too Expensive!\"); lower values keep each step affordable but may leave enchantments out.",
				XpSaverConfig.STEP_COST_MIN, XpSaverConfig.STEP_COST_MAX, 29, " levels", c.maxStepCost, v -> c.maxStepCost = v));
		plannerKeyRow();
	}

	/**
	 * The planner's key binding, changed the way Options → Controls → Key Binds does it (KeyBindsScreen, 26.3): click the button,
	 * then press a key or mouse button; Esc leaves it unbound. It is the same KeyMapping the Controls screen shows, so it is saved
	 * in the game's options.txt (OptionsSubScreen#removed saves it) rather than in xpsaver.json.
	 */
	private void plannerKeyRow() {
		KeyMapping key = XpSaverClient.plannerKey();
		keyButton = Button.builder(Component.empty(), b -> {
			listening = true;
			refreshKey();
		}).width(150).build();
		keyReset = Button.builder(Component.translatable("controls.reset"), b -> setPlannerKey(key.getDefaultKey())).width(150)
				.tooltip(Tooltip.create(Component.literal("Puts the planner key back to " + key.getDefaultKey().getDisplayName().getString() + ".")))
				.build();
		refreshKey();
		list.addSmall(keyButton, keyReset);
	}

	private void setPlannerKey(InputConstants.Key value) {
		XpSaverClient.plannerKey().setKey(value);
		KeyMapping.resetMapping(); // rebuilds the game's key -> binding lookup, as KeyBindsList#resetMappingAndUpdateButtons does
		listening = false;
		refreshKey();
	}

	/** Button text and tooltip as the Controls screen shows them: "> K <" while waiting, "[ K ]" in yellow when another binding uses it. */
	private void refreshKey() {
		if (keyButton == null) {
			return;
		}
		KeyMapping key = XpSaverClient.plannerKey();
		MutableComponent clashes = Component.empty();
		boolean clash = false;
		if (!key.isUnbound()) {
			for (KeyMapping other : minecraft.options.keyMappings) {
				if (other != key && key.same(other)) {
					clashes.append(clash ? ", " : "").append(Component.translatable(other.getName()));
					clash = true;
				}
			}
		}
		Component value = key.getTranslatedKeyMessage();
		if (listening) {
			value = Component.literal("> ").append(value.copy().withStyle(ChatFormatting.WHITE, ChatFormatting.UNDERLINE)).append(" <")
					.withStyle(ChatFormatting.YELLOW);
		} else if (clash) {
			value = Component.literal("[ ").append(value.copy().withStyle(ChatFormatting.WHITE)).append(" ]").withStyle(ChatFormatting.YELLOW);
		}
		keyButton.setMessage(Component.translatable(KEY + "planner_key").append(": ").append(value));
		String tip = "Opens the planner: the cheapest order for the item in your hand, using the books in your inventory. Click, then "
				+ "press a key or mouse button. Esc leaves it unbound. The same setting as Options \u2192 Controls \u2192 Key Binds \u2192 "
				+ "XP Saver.\n\nDefault: " + key.getDefaultKey().getDisplayName().getString() + ". Applies right away.";
		keyButton.setTooltip(Tooltip.create(clash ? Component.literal(tip + "\n\n").append(Component.translatable(
				"controls.keybinds.duplicateKeybinds", clashes).withStyle(ChatFormatting.YELLOW)) : Component.literal(tip)));
		keyReset.active = !key.isDefault();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (!listening) {
			return super.keyPressed(event);
		}
		setPlannerKey(event.isEscape() ? InputConstants.UNKNOWN : InputConstants.getKey(event));
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (!listening) {
			return super.mouseClicked(event, doubleClick);
		}
		setPlannerKey(InputConstants.Type.MOUSE.getOrCreate(event.button()));
		return true;
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
				XpSaverClient.plannerKey().setKey(XpSaverClient.plannerKey().getDefaultKey()); // the planner key is an XP Saver setting too
				KeyMapping.resetMapping();
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
		return Component.literal(tip + "\n\nSix hex digits, RRGGBB (a leading # is fine). Default: " + def + ".\nContrast on the panel (worst case at this opacity): "
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
			return face ? XpSaverConfig.get().palette().text : Integer.parseInt(hex.get(), 16);
		}

		/** Worst case at the style's opacity: the brightest or darkest world showing through. */
		double ratio() {
			XpSaverConfig.Palette p = XpSaverConfig.get().palette();
			return XpSaverConfig.worstContrast(sampleColor(), p.face, p.opacity);
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
			XpSaverConfig.Palette c = XpSaverConfig.get().palette();
			int x = getX();
			int y = getY();
			g.fill(x, y, x + width, y + height, 0xFF000000);
			g.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF000000 | c.face);
			Minecraft mc = Minecraft.getInstance();
			g.text(mc.font, getMessage(), x + 5, y + 6, 0xFF000000 | sampleColor(), c.textShadow);
			double ratio = ratio();
			String label = String.format(Locale.ROOT, "%.1f:1", ratio) + (ratio < MIN_CONTRAST ? " ⚠" : "");
			g.text(mc.font, label, x + width - 5 - mc.font.width(label), y + 6, 0xFF000000 | (ratio < MIN_CONTRAST ? c.red : c.text), c.textShadow);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}
}
