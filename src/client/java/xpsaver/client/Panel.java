package xpsaver.client;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import xpsaver.client.config.XpSaverConfig;

/**
 * One framed, scrollable list of text rows in the look of a vanilla container (textures/gui/container/inventory.png, 26.2):
 * light gray face, white top-left and dark gray bottom-right bevel, black outline with cut corners, dark text without a shadow,
 * and a slot-style scrollbar. Placement adapts to the screen: on narrow screens or large GUI scales the panel draws its text
 * smaller instead of squeezing it (see {@link #scale}). Side, offsets, sizes, spacing and colors come from {@link XpSaverConfig}.
 */
final class Panel {
	/** AbstractContainerScreen(menu, inventory, title) uses 176 x 166 (26.2); EnchantmentScreen and AnvilScreen keep it. */
	static final int IMAGE_WIDTH = 176;
	static final int IMAGE_HEIGHT = 166;
	/** Space between a panel and the container window or the screen edge. */
	static final int GAP = 4;
	private static final int PAD = 6; // 3 px frame + 3 px margin
	private static final int SCROLLBAR = 6;
	private static final int LIGHT = 0xFFFFFF;
	private static final int SHADOW = 0x555555;
	private static final int OUTLINE = 0x000000;
	private static final int SLOT = 0x8B8B8B;
	private static final int SLOT_SHADOW = 0x373737;
	/** Same as the vanilla slot highlight (white at half alpha): marks the clickable row under the mouse. */
	private static final int HOVER = 0x80FFFFFF;

	/** One line of the list; {@code tag} is for the caller (the anvil uses it for book rows, which are clickable), -1 for none. */
	record Row(FormattedCharSequence text, int color, int tag) {}

	private int x; // top-left of the frame, screen pixels
	private int y;
	private int width; // panel pixels (screen pixels / scale)
	private int maxHeight;
	private float scale = 1;
	private int scroll;
	private List<Row> rows = List.of();

	private static XpSaverConfig cfg() {
		return XpSaverConfig.get();
	}

	/** Opaque ARGB for a config color (0xRRGGBB), for text. */
	static int argb(int rgb) {
		return 0xFF000000 | rgb;
	}

	static int text() {
		return argb(cfg().text);
	}

	static int dimmed() {
		return argb(cfg().dimmed);
	}

	static int green() {
		return argb(cfg().green);
	}

	static int red() {
		return argb(cfg().red);
	}

	/** Frame and face colors at the configured opacity (text stays opaque). */
	private static int shade(int rgb) {
		return (Math.round(cfg().opacity * 2.55f) << 24) | rgb;
	}

	private static int line() {
		return cfg().lineSpacing;
	}

	/** Same formula as AbstractContainerScreen#init (26.2): (width - imageWidth) / 2. */
	static int leftPos(int screenWidth) {
		return (screenWidth - IMAGE_WIDTH) / 2;
	}

	static int topPos(int screenHeight) {
		return (screenHeight - IMAGE_HEIGHT) / 2;
	}

	static boolean onRight() {
		return cfg().anchor == XpSaverConfig.Anchor.RIGHT;
	}

	/** Free width beside the container window on the configured side (left by default: item-list mods such as JEI sit right). */
	static int sideRoom(int screenWidth) {
		int left = leftPos(screenWidth);
		return (onRight() ? screenWidth - left - IMAGE_WIDTH : left) - 2 * GAP;
	}

	/** X of something {@code width} wide placed beside the window on the configured side, before the X offset. */
	static int sideX(int screenWidth, int width) {
		int left = leftPos(screenWidth);
		return onRight() ? left + IMAGE_WIDTH + GAP : left - GAP - width;
	}

	/** Bottom edge for a side panel: level with the window (clear of JEI's corner buttons) or the screen bottom. */
	static int bottom(int screenHeight) {
		return cfg().panelHeight == XpSaverConfig.PanelHeight.FULL_SCREEN ? screenHeight - GAP
				: Math.min(topPos(screenHeight) + IMAGE_HEIGHT, screenHeight - GAP);
	}

	/**
	 * Scale that gives {@code minWidth} panel pixels in {@code room} screen pixels: 1 when it fits (or when text is set to never
	 * shrink), otherwise smaller in steps that keep each font pixel a whole number of real pixels (sharp text), down to 1 real
	 * pixel per font pixel (as at GUI scale 1).
	 */
	static float scale(int minWidth, int room) {
		if (cfg().textSize == XpSaverConfig.TextSize.NEVER_SHRINK) {
			return 1;
		}
		int gui = Minecraft.getInstance().getWindow().getGuiScale();
		int k = gui;
		while (k > 1 && room * gui < minWidth * k) {
			k--;
		}
		return k / (float) gui;
	}

	/** Width left for text in a panel {@code panelWidth} wide (margins and the scrollbar taken off). */
	static int textWidth(int panelWidth) {
		return panelWidth - PAD - SCROLLBAR - 6;
	}

	/** Panel width that fits text lines up to {@code textWidth} wide. */
	static int widthFor(int textWidth) {
		return textWidth + PAD + SCROLLBAR + 6;
	}

	/**
	 * Puts the panel beside the container on the configured side, from {@code top} down to at most {@code bottom}, up to
	 * {@code wanted} wide, then moves it by the configured X/Y offset (kept on screen).
	 * Returns false when there is no usable room (a window only a few hundred pixels wide).
	 */
	boolean placeBeside(int screenWidth, int screenHeight, int top, int bottom, int wanted) {
		XpSaverConfig c = cfg();
		int room = sideRoom(screenWidth);
		scale = scale(Math.min(wanted, c.shrinkWidth), room);
		width = Math.max(1, Math.min(wanted, (int) (room / scale)));
		int screenW = (int) Math.ceil(width * scale);
		x = Math.max(0, Math.min(screenWidth - screenW, sideX(screenWidth, screenW) + c.offsetX));
		y = Math.max(0, top + c.offsetY);
		maxHeight = (int) ((Math.min(bottom + c.offsetY, screenHeight - GAP) - y) / scale);
		return width >= 40 && maxHeight >= 3 * line();
	}

	/** Centers a panel {@code width} wide on the screen (call after {@link #setRows}, which decides the height). */
	void placeCentered(int screenWidth, int screenHeight, int width) {
		scale = 1;
		this.width = width;
		maxHeight = screenHeight - 2 * GAP;
		x = (screenWidth - width) / 2;
		y = (screenHeight - height()) / 2;
	}

	int width() {
		return width;
	}

	void setRows(List<Row> rows) {
		this.rows = rows;
	}

	void scrollToTop() {
		scroll = 0;
	}

	private int contentHeight() {
		return rows.size() * line() + 2 * PAD - 1;
	}

	private int height() {
		return Math.min(contentHeight(), maxHeight);
	}

	boolean contains(double mouseX, double mouseY) {
		return !rows.isEmpty() && mouseX >= x && mouseX < x + width * scale && mouseY >= y && mouseY < y + height() * scale;
	}

	/** Tag of the row under the mouse, or -1. */
	int tagAt(double mouseX, double mouseY) {
		if (!contains(mouseX, mouseY)) {
			return -1;
		}
		int index = (int) Math.floor(((mouseY - y) / scale - PAD + scroll) / line());
		return index >= 0 && index < rows.size() ? rows.get(index).tag() : -1;
	}

	/** Mouse wheel over the panel scrolls it. Returns true if handled. */
	boolean scroll(double mouseX, double mouseY, double amount) {
		if (!contains(mouseX, mouseY)) {
			return false;
		}
		scroll -= (int) Math.round(amount * line() * cfg().scrollLines);
		return true; // clamped when drawn
	}

	/** Draws the panel; a clickable row (tag >= 0) under the mouse gets the slot highlight and a pointing-hand cursor. */
	void draw(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
		if (rows.isEmpty()) {
			return;
		}
		XpSaverConfig c = cfg();
		int line = line();
		int height = height();
		int overflow = contentHeight() - height;
		scroll = Math.max(0, Math.min(scroll, overflow));
		int hovered = tagAt(mouseX, mouseY);
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		if (c.frame == XpSaverConfig.Frame.INVENTORY) {
			frame(g, 0, 0, width, height);
		} else {
			g.fill(0, 0, width, height, shade(c.face));
		}
		g.enableScissor(3, 3, width - 3, height - 3);
		for (int i = 0; i < rows.size(); i++) {
			int rowY = PAD + i * line - scroll;
			if (rowY + line > 0 && rowY < height) {
				if (hovered >= 0 && rows.get(i).tag() == hovered) {
					g.fill(PAD - 2, rowY - 1, PAD + textWidth(width) + 1, rowY + line - 1, HOVER);
				}
				g.text(font, rows.get(i).text(), PAD, rowY, rows.get(i).color(), c.textShadow);
			}
		}
		g.disableScissor();
		if (overflow > 0) {
			int trackX = width - 4 - SCROLLBAR;
			int trackH = height - 8;
			int thumbH = Math.max(8, (trackH - 2) * height / contentHeight());
			int thumbY = 5 + (trackH - 2 - thumbH) * scroll / overflow;
			slot(g, trackX, 4, SCROLLBAR, trackH);
			raised(g, trackX + 1, thumbY, SCROLLBAR - 2, thumbH);
		}
		g.pose().popMatrix();
		if (hovered >= 0) {
			g.requestCursor(CursorTypes.POINTING_HAND);
		}
	}

	/** The container window frame, pixel for pixel as inventory.png draws its edges and corners. */
	static void frame(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		int r = x + w;
		int b = y + h;
		int face = shade(cfg().face);
		int light = shade(LIGHT);
		int shadow = shade(SHADOW);
		int outline = shade(OUTLINE);
		g.fill(x + 3, y + 3, r - 3, b - 3, face);
		g.fill(x + 2, y + 1, r - 3, y + 3, light); // top bevel
		g.fill(x + 1, y + 2, x + 3, b - 3, light); // left bevel
		g.fill(x + 3, y + 3, x + 4, y + 4, light);
		g.fill(r - 3, y + 3, r - 1, b - 2, shadow); // right bevel
		g.fill(x + 3, b - 3, r - 2, b - 1, shadow); // bottom bevel
		g.fill(r - 4, b - 4, r - 3, b - 3, shadow);
		g.fill(r - 3, y + 2, r - 2, y + 3, face);
		g.fill(x + 2, b - 3, x + 3, b - 2, face);
		g.fill(x + 2, y, r - 3, y + 1, outline); // outline with cut corners
		g.fill(x + 3, b - 1, r - 2, b, outline);
		g.fill(x, y + 2, x + 1, b - 3, outline);
		g.fill(r - 1, y + 3, r, b - 2, outline);
		g.fill(x + 1, y + 1, x + 2, y + 2, outline);
		g.fill(r - 3, y + 1, r - 2, y + 2, outline);
		g.fill(r - 2, y + 2, r - 1, y + 3, outline);
		g.fill(x + 1, b - 3, x + 2, b - 2, outline);
		g.fill(x + 2, b - 2, x + 3, b - 1, outline);
		g.fill(r - 2, b - 2, r - 1, b - 1, outline);
	}

	/** Sunken box like an inventory slot: dark top-left edge, white bottom-right edge. */
	private static void slot(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, shade(SLOT));
		g.fill(x, y, x + w - 1, y + 1, shade(SLOT_SHADOW));
		g.fill(x, y, x + 1, y + h - 1, shade(SLOT_SHADOW));
		g.fill(x + 1, y + h - 1, x + w, y + h, shade(LIGHT));
		g.fill(x + w - 1, y + 1, x + w, y + h, shade(LIGHT));
	}

	/** Raised box like the creative inventory's scroller: white top-left edge, dark bottom-right edge. */
	private static void raised(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, shade(cfg().face));
		g.fill(x, y, x + w - 1, y + 1, shade(LIGHT));
		g.fill(x, y, x + 1, y + h - 1, shade(LIGHT));
		g.fill(x + 1, y + h - 1, x + w, y + h, shade(SHADOW));
		g.fill(x + w - 1, y + 1, x + w, y + h, shade(SHADOW));
	}

	/** Wraps a line to {@code width}; continuation lines get a hanging indent so a wrapped entry still reads as one. */
	static void wrap(List<Row> out, Font font, Component line, int width, int color, int tag) {
		Component light = onLight(line);
		List<FormattedCharSequence> parts = font.split(light, width);
		if (parts.size() > 1) { // only lines that really wrap give up room for the indent
			parts = font.split(light, width - font.width("   "));
		}
		if (parts.isEmpty()) { // a blank line
			out.add(new Row(FormattedCharSequence.EMPTY, color, tag));
		}
		FormattedCharSequence indent = FormattedCharSequence.forward("   ", Style.EMPTY);
		for (int i = 0; i < parts.size(); i++) {
			out.add(new Row(i == 0 ? parts.get(i) : FormattedCharSequence.composite(indent, parts.get(i)), color, tag));
		}
	}

	/** Wraps {@code line} into the space right of {@code indentPx}, every part starting at the same x. */
	static void indented(List<Row> out, Font font, Component line, int width, int indentPx, int tag) {
		int spaces = Math.max(1, Math.round(indentPx / (float) font.width(" ")));
		FormattedCharSequence indent = FormattedCharSequence.forward(" ".repeat(spaces), Style.EMPTY);
		for (FormattedCharSequence part : font.split(onLight(line), width - font.width(" ".repeat(spaces)))) {
			out.add(new Row(FormattedCharSequence.composite(indent, part), text(), tag));
		}
	}

	/** Swaps the chat colors the mod's lines use (made for dark backgrounds) for the configured ones that read on the light face. */
	static Component onLight(Component line) {
		MutableComponent out = Component.empty();
		line.visit((style, text) -> {
			out.append(Component.literal(text).setStyle(onLight(style)));
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	private static Style onLight(Style style) {
		TextColor color = style.getColor();
		if (color == null) {
			return style;
		}
		XpSaverConfig c = cfg();
		return switch (color.getValue()) {
			case 0xFFFFFF, 0xFFAA00 -> style.withColor(c.text); // white and gold (headings), as vanilla titles
			case 0xAAAAAA -> style.withColor(c.hint); // gray: hints and enchantment names
			case 0x555555 -> style.withColor(c.dimmed); // dark gray: done steps, blocked choices
			case 0xFFFF55 -> style.withColor(c.amber); // yellow: the current step, "nothing on its own"
			case 0x55FF55, 0x00AA00 -> style.withColor(c.green);
			case 0x55FFFF -> style.withColor(c.teal); // aqua: upgrades, "pick one"
			case 0xFF5555 -> style.withColor(c.red);
			default -> style;
		};
	}
}
