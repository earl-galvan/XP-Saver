package xpsaver.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xpsaver.core.Planner;

/**
 * Player settings, saved as JSON in config/xpsaver.json. Every value has a default and a range; {@link #validate} puts anything
 * out of range (or hand-edited into nonsense) back inside it. Features read these values every frame, so changes apply live.
 */
public final class XpSaverConfig {
	private static final Logger LOG = LoggerFactory.getLogger("xpsaver");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static XpSaverConfig instance = new XpSaverConfig();

	public enum Anchor { LEFT, RIGHT }

	public enum ButtonLayout { AUTO, STACKED, ROW }

	public enum PanelHeight { MATCH_WINDOW, FULL_SCREEN }

	public enum TextSize { AUTO, NEVER_SHRINK }

	public enum Frame { INVENTORY, FLAT }

	// Ranges, shared by validate() and the config screen's sliders.
	public static final int OFFSET_MIN = -100;
	public static final int OFFSET_MAX = 100;
	public static final int MARK_OFFSET_MIN = -20;
	public static final int MARK_OFFSET_MAX = 20;
	public static final int PANEL_WIDTH_MIN = 100;
	public static final int PANEL_WIDTH_MAX = 240;
	public static final int PLANNER_WIDTH_MIN = 200;
	public static final int PLANNER_WIDTH_MAX = 480;
	public static final int SHRINK_WIDTH_MIN = 60;
	public static final int SHRINK_WIDTH_MAX = 200;
	public static final int LINE_MIN = 9;
	public static final int LINE_MAX = 14;
	public static final int SCROLL_MIN = 1;
	public static final int SCROLL_MAX = 6;
	public static final int OPACITY_MIN = 30;
	public static final int OPACITY_MAX = 100;
	public static final int STEP_COST_MIN = 1;
	public static final int STEP_COST_MAX = 39; // the anvil refuses 40 and up ("Too Expensive!")

	// Features
	public boolean tablePanel = true;
	public boolean tableOffers = true;
	public boolean anvilMark = true;
	public boolean recommendedDropdown = true;
	public boolean cheapestOrderDropdown = true;

	// Position and size
	public Anchor anchor = Anchor.LEFT;
	public int offsetX = 0;
	public int offsetY = 0;
	public ButtonLayout buttonLayout = ButtonLayout.AUTO;
	public int panelWidth = 170;
	public PanelHeight panelHeight = PanelHeight.MATCH_WINDOW;
	public int markOffsetX = 0;
	public int markOffsetY = 0;
	public int plannerMaxWidth = 360;

	// Text and readability
	public TextSize textSize = TextSize.AUTO;
	public int shrinkWidth = 100;
	public int lineSpacing = 10;
	public boolean textShadow = false;
	public int scrollLines = 2;

	// Look and colors. Defaults are at least 4.5:1 against the default face (WCAG AA for normal text).
	public Frame frame = Frame.INVENTORY;
	public int opacity = 100;
	public String faceColor = "C6C6C6";
	public String textColor = "404040";
	public String dimmedColor = "525252";
	public String hintColor = "4C4C4C";
	public String greenColor = "125A12";
	public String redColor = "9A1A1A";
	public String amberColor = "6B4000";
	public String tealColor = "0A5562";

	// Planner
	public int maxStepCost = Planner.MAX_STEP_COST;

	// Parsed colors (0xRRGGBB), kept in step with the strings by validate(); not saved.
	public transient int face;
	public transient int text;
	public transient int dimmed;
	public transient int hint;
	public transient int green;
	public transient int red;
	public transient int amber;
	public transient int teal;

	public XpSaverConfig() {
		validate();
	}

	public static XpSaverConfig get() {
		return instance;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("xpsaver.json");
	}

	/** Reads config/xpsaver.json; a missing or unreadable file means defaults (written back so the file exists). */
	public static void load() {
		Path path = path();
		XpSaverConfig loaded = null;
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				loaded = GSON.fromJson(reader, XpSaverConfig.class);
			} catch (IOException | JsonParseException e) {
				LOG.warn("XP Saver: could not read {}, using defaults", path, e);
			}
		}
		instance = loaded == null ? new XpSaverConfig() : loaded;
		instance.validate();
		save();
	}

	/** Writes to a temporary file first, so a crash mid-write never leaves a half-written config. */
	public static void save() {
		Path path = path();
		try {
			Files.createDirectories(path.getParent());
			Path tmp = path.resolveSibling("xpsaver.json.tmp");
			try (Writer writer = Files.newBufferedWriter(tmp)) {
				GSON.toJson(instance, writer);
			}
			Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			LOG.warn("XP Saver: could not save {}", path, e);
		}
	}

	public static void resetToDefaults() {
		instance = new XpSaverConfig();
		save();
	}

	/** Puts every value back inside its range, replaces missing or invalid ones with defaults, and applies the planner budget. */
	public void validate() {
		anchor = anchor == null ? Anchor.LEFT : anchor; // null: missing or unknown name in a hand-edited file
		buttonLayout = buttonLayout == null ? ButtonLayout.AUTO : buttonLayout;
		panelHeight = panelHeight == null ? PanelHeight.MATCH_WINDOW : panelHeight;
		textSize = textSize == null ? TextSize.AUTO : textSize;
		frame = frame == null ? Frame.INVENTORY : frame;
		offsetX = clamp(offsetX, OFFSET_MIN, OFFSET_MAX);
		offsetY = clamp(offsetY, OFFSET_MIN, OFFSET_MAX);
		markOffsetX = clamp(markOffsetX, MARK_OFFSET_MIN, MARK_OFFSET_MAX);
		markOffsetY = clamp(markOffsetY, MARK_OFFSET_MIN, MARK_OFFSET_MAX);
		panelWidth = clamp(panelWidth, PANEL_WIDTH_MIN, PANEL_WIDTH_MAX);
		plannerMaxWidth = clamp(plannerMaxWidth, PLANNER_WIDTH_MIN, PLANNER_WIDTH_MAX);
		shrinkWidth = clamp(shrinkWidth, SHRINK_WIDTH_MIN, SHRINK_WIDTH_MAX);
		lineSpacing = clamp(lineSpacing, LINE_MIN, LINE_MAX);
		scrollLines = clamp(scrollLines, SCROLL_MIN, SCROLL_MAX);
		opacity = clamp(opacity, OPACITY_MIN, OPACITY_MAX);
		maxStepCost = clamp(maxStepCost, STEP_COST_MIN, STEP_COST_MAX);
		faceColor = hexOr(faceColor, "C6C6C6");
		textColor = hexOr(textColor, "404040");
		dimmedColor = hexOr(dimmedColor, "525252");
		hintColor = hexOr(hintColor, "4C4C4C");
		greenColor = hexOr(greenColor, "125A12");
		redColor = hexOr(redColor, "9A1A1A");
		amberColor = hexOr(amberColor, "6B4000");
		tealColor = hexOr(tealColor, "0A5562");
		face = Integer.parseInt(faceColor, 16);
		text = Integer.parseInt(textColor, 16);
		dimmed = Integer.parseInt(dimmedColor, 16);
		hint = Integer.parseInt(hintColor, 16);
		green = Integer.parseInt(greenColor, 16);
		red = Integer.parseInt(redColor, 16);
		amber = Integer.parseInt(amberColor, 16);
		teal = Integer.parseInt(tealColor, 16);
		Planner.maxStepCost = maxStepCost;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	/** True for exactly six hex digits (RRGGBB), with or without a leading '#'. */
	public static boolean isHex(String s) {
		return s != null && s.replaceFirst("^#", "").matches("[0-9A-Fa-f]{6}");
	}

	private static String hexOr(String s, String fallback) {
		return isHex(s) ? s.replaceFirst("^#", "").toUpperCase(Locale.ROOT) : fallback;
	}

	/** WCAG 2 contrast ratio between two 0xRRGGBB colors (1 to 21; 4.5 is the usual minimum for normal text). */
	public static double contrast(int a, int b) {
		double la = luminance(a);
		double lb = luminance(b);
		return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
	}

	private static double luminance(int rgb) {
		return 0.2126 * channel(rgb >> 16) + 0.7152 * channel(rgb >> 8) + 0.0722 * channel(rgb);
	}

	private static double channel(int c) {
		double v = (c & 0xFF) / 255.0;
		return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
	}
}
