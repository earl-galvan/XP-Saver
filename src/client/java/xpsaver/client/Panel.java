package xpsaver.client;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** A plain text box drawn beside a container screen. */
final class Panel {
	/** AbstractContainerScreen(menu, inventory, title) uses 176 x 166 (26.3); EnchantmentScreen and AnvilScreen keep it. */
	static final int IMAGE_WIDTH = 176;
	static final int IMAGE_HEIGHT = 166;
	private static final int LINE = 10;
	private static final int PAD = 4;

	private Panel() {}

	/** Same formula as AbstractContainerScreen#init (26.3): (width - imageWidth) / 2. */
	static int leftPos(int screenWidth) {
		return (screenWidth - IMAGE_WIDTH) / 2;
	}

	static int topPos(int screenHeight) {
		return (screenHeight - IMAGE_HEIGHT) / 2;
	}

	/**
	 * Draws to the left of the GUI (the right side is where item-list mods such as JEI sit), shrunk (down to half size)
	 * if it would not fit, so it never covers the GUI. Text colors need alpha in 26.3 (GuiGraphicsExtractor#text skips alpha 0).
	 */
	static void draw(GuiGraphicsExtractor g, Font font, List<Component> lines, int screenWidth, int screenHeight) {
		int w = 0;
		for (Component line : lines) {
			w = Math.max(w, font.width(line));
		}
		w += PAD * 2;
		int h = lines.size() * LINE + PAD * 2 - 2;
		int left = leftPos(screenWidth);
		int room = left - 4;
		float scale = w > room ? Math.max(0.5f, room / (float) w) : 1f;
		int x = Math.max(0, left - 4 - (int) Math.ceil(w * scale));

		g.pose().pushMatrix();
		g.pose().translate(x, topPos(screenHeight));
		g.pose().scale(scale, scale);
		g.fill(0, 0, w, h, 0xC0101010);
		for (int i = 0; i < lines.size(); i++) {
			g.text(font, lines.get(i), PAD, PAD + i * LINE, 0xFFFFFFFF);
		}
		g.pose().popMatrix();
	}
}
