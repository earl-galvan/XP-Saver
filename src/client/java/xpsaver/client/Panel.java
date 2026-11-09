package xpsaver.client;

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

/**
 * One framed, scrollable list of text rows in the look of a vanilla container (textures/gui/container/inventory.png, 26.2):
 * light gray face, white top-left and dark gray bottom-right bevel, black outline with cut corners, dark text without a shadow,
 * and a slot-style scrollbar. Placement adapts to the screen: on narrow screens or large GUI scales the panel draws its text
 * smaller instead of squeezing it (see {@link #scale}).
 */
final class Panel {
	/** AbstractContainerScreen(menu, inventory, title) uses 176 x 166 (26.2); EnchantmentScreen and AnvilScreen keep it. */
	static final int IMAGE_WIDTH = 176;
	static final int IMAGE_HEIGHT = 166;
	/** Space between a panel and the container window or the screen edge. */
	static final int GAP = 4;
	/** AbstractContainerScreen#extractLabels (26.2) draws titles in -12566464 (0xFF404040), no shadow. */
	static final int TEXT = 0xFF404040;
	static final int MUTED = 0xFF7F7F7F;
	static final int GREEN = 0xFF176B17;
	static final int RED = 0xFFA32020;
	/** Narrowest comfortable panel; with less room the text is drawn smaller rather than wrapped into a thin strip. */
	static final int MIN_WIDTH = 100;
	private static final int LINE = 10;
	private static final int PAD = 6; // 3 px frame + 3 px margin
	private static final int SCROLLBAR = 6;
	private static final int FACE = 0xFFC6C6C6;
	private static final int LIGHT = 0xFFFFFFFF;
	private static final int SHADOW = 0xFF555555;
	private static final int OUTLINE = 0xFF000000;
	private static final int SLOT = 0xFF8B8B8B;
	private static final int SLOT_SHADOW = 0xFF373737;

	/** One line of the list; {@code tag} is for the caller (the anvil uses it for book rows), -1 for none. */
	record Row(FormattedCharSequence text, int color, int tag) {}

	private int x; // top-left of the frame, screen pixels
	private int y;
	private int width; // panel pixels (screen pixels / scale)
	private int maxHeight;
	private float scale = 1;
	private int scroll;
	private List<Row> rows = List.of();

	/** Same formula as AbstractContainerScreen#init (26.2): (width - imageWidth) / 2. */
	static int leftPos(int screenWidth) {
		return (screenWidth - IMAGE_WIDTH) / 2;
	}

	static int topPos(int screenHeight) {
		return (screenHeight - IMAGE_HEIGHT) / 2;
	}

	/** Free width left of the container window (the right side is where item-list mods such as JEI sit). */
	static int sideRoom(int screenWidth) {
		return leftPos(screenWidth) - 2 * GAP;
	}

	/**
	 * Scale that gives {@code minWidth} panel pixels in {@code room} screen pixels: 1 when it fits, otherwise smaller in steps that keep
	 * each font pixel a whole number of real pixels (sharp text), down to 1 real pixel per font pixel (as at GUI scale 1).
	 */
	static float scale(int minWidth, int room) {
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
	 * Puts the panel in the column left of the container, from {@code top} down to at most {@code bottom}, up to {@code wanted} wide.
	 * Returns false when there is no usable room (a window only a few hundred pixels wide).
	 */
	boolean placeLeft(int screenWidth, int top, int bottom, int wanted) {
		int room = sideRoom(screenWidth);
		scale = scale(Math.min(wanted, MIN_WIDTH), room);
		width = Math.min(wanted, (int) (room / scale));
		x = leftPos(screenWidth) - GAP - (int) Math.ceil(width * scale);
		y = top;
		maxHeight = (int) ((bottom - top) / scale);
		return width >= 40 && maxHeight >= 3 * LINE;
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
		return rows.size() * LINE + 2 * PAD - 1;
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
		int index = (int) Math.floor(((mouseY - y) / scale - PAD + scroll) / LINE);
		return index >= 0 && index < rows.size() ? rows.get(index).tag() : -1;
	}

	/** Mouse wheel over the panel scrolls it. Returns true if handled. */
	boolean scroll(double mouseX, double mouseY, double amount) {
		if (!contains(mouseX, mouseY)) {
			return false;
		}
		scroll -= (int) Math.round(amount * LINE * 2);
		return true; // clamped when drawn
	}

	void draw(GuiGraphicsExtractor g, Font font) {
		if (rows.isEmpty()) {
			return;
		}
		int height = height();
		int overflow = contentHeight() - height;
		scroll = Math.max(0, Math.min(scroll, overflow));
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		frame(g, 0, 0, width, height);
		g.enableScissor(3, 3, width - 3, height - 3);
		for (int i = 0; i < rows.size(); i++) {
			int rowY = PAD + i * LINE - scroll;
			if (rowY + LINE > 0 && rowY < height) {
				g.text(font, rows.get(i).text(), PAD, rowY, rows.get(i).color(), false);
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
	}

	/** The container window frame, pixel for pixel as inventory.png draws its edges and corners. */
	static void frame(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		int r = x + w;
		int b = y + h;
		g.fill(x + 3, y + 3, r - 3, b - 3, FACE);
		g.fill(x + 2, y + 1, r - 3, y + 3, LIGHT); // top bevel
		g.fill(x + 1, y + 2, x + 3, b - 3, LIGHT); // left bevel
		g.fill(x + 3, y + 3, x + 4, y + 4, LIGHT);
		g.fill(r - 3, y + 3, r - 1, b - 2, SHADOW); // right bevel
		g.fill(x + 3, b - 3, r - 2, b - 1, SHADOW); // bottom bevel
		g.fill(r - 4, b - 4, r - 3, b - 3, SHADOW);
		g.fill(r - 3, y + 2, r - 2, y + 3, FACE);
		g.fill(x + 2, b - 3, x + 3, b - 2, FACE);
		g.fill(x + 2, y, r - 3, y + 1, OUTLINE); // outline with cut corners
		g.fill(x + 3, b - 1, r - 2, b, OUTLINE);
		g.fill(x, y + 2, x + 1, b - 3, OUTLINE);
		g.fill(r - 1, y + 3, r, b - 2, OUTLINE);
		g.fill(x + 1, y + 1, x + 2, y + 2, OUTLINE);
		g.fill(r - 3, y + 1, r - 2, y + 2, OUTLINE);
		g.fill(r - 2, y + 2, r - 1, y + 3, OUTLINE);
		g.fill(x + 1, b - 3, x + 2, b - 2, OUTLINE);
		g.fill(x + 2, b - 2, x + 3, b - 1, OUTLINE);
		g.fill(r - 2, b - 2, r - 1, b - 1, OUTLINE);
	}

	/** Sunken box like an inventory slot: dark top-left edge, white bottom-right edge. */
	private static void slot(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, SLOT);
		g.fill(x, y, x + w - 1, y + 1, SLOT_SHADOW);
		g.fill(x, y, x + 1, y + h - 1, SLOT_SHADOW);
		g.fill(x + 1, y + h - 1, x + w, y + h, LIGHT);
		g.fill(x + w - 1, y + 1, x + w, y + h, LIGHT);
	}

	/** Raised box like the creative inventory's scroller: white top-left edge, dark bottom-right edge. */
	private static void raised(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, FACE);
		g.fill(x, y, x + w - 1, y + 1, LIGHT);
		g.fill(x, y, x + 1, y + h - 1, LIGHT);
		g.fill(x + 1, y + h - 1, x + w, y + h, SHADOW);
		g.fill(x + w - 1, y + 1, x + w, y + h, SHADOW);
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
			out.add(new Row(FormattedCharSequence.composite(indent, part), TEXT, tag));
		}
	}

	/** Swaps the chat colors the mod's lines use (made for dark backgrounds) for darker ones that read on the light gray face. */
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
		return switch (color.getValue()) {
			case 0xFFFFFF, 0xFFAA00 -> style.withColor(TEXT & 0xFFFFFF); // white and gold (headings), as vanilla titles
			case 0xAAAAAA -> style.withColor(0x606060); // gray
			case 0x555555 -> style.withColor(MUTED & 0xFFFFFF); // dark gray
			case 0xFFFF55 -> style.withColor(0x8A5300); // yellow
			case 0x55FF55, 0x00AA00 -> style.withColor(GREEN & 0xFFFFFF);
			case 0x55FFFF -> style.withColor(0x0E6470); // aqua
			case 0xFF5555 -> style.withColor(RED & 0xFFFFFF);
			default -> style;
		};
	}
}
