package xpsaver.client;

import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import xpsaver.client.config.XpSaverConfig;
import xpsaver.core.AnvilCost;
import xpsaver.core.EnchantInfo;

/** F2: check or cross next to the anvil output slot, with the reason as a tooltip. */
final class AnvilMark {
	/** AnvilMenu input slot definitions (26.2): result slot at (134, 47), 16 px wide. */
	private static final int MARK_X = 134 + 16 + 4;
	private static final int MARK_Y = 47 + 4;

	private AnvilMark() {}

	static void draw(AnvilScreen screen, GuiGraphicsExtractor g, int mouseX, int mouseY) {
		Minecraft mc = Minecraft.getInstance();
		AnvilMenu menu = screen.getMenu();
		ItemStack left = menu.getSlot(AnvilMenu.INPUT_SLOT).getItem();
		ItemStack right = menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem();
		if (!XpSaverConfig.get().anvilMark || left.isEmpty() || right.isEmpty() || mc.level == null || mc.player == null) {
			return; // rename-only or empty: nothing to compare
		}
		boolean creative = mc.player.hasInfiniteMaterials();
		int cost = menu.getCost(); // the game's own price, synced to the client
		boolean hasOutput = menu.getSlot(AnvilMenu.RESULT_SLOT).hasItem();

		Registry<Enchantment> registry = Enchants.registry(mc);
		Map<String, EnchantInfo> infos = Enchants.infos(registry);
		AnvilCost.Result here = AnvilCost.combine(Enchants.piece(left, registry), Enchants.piece(right, registry), infos, false, creative);
		AnvilCost.Result swapped = AnvilCost.combine(Enchants.piece(right, registry), Enchants.piece(left, registry), infos, false, creative);

		boolean ok;
		Component reason;
		if (!hasOutput) {
			ok = false;
			String swap = swapped.valid() ? ". Swap the inputs: " + swapped.cost() + " levels" : "";
			if (cost >= AnvilCost.TOO_EXPENSIVE && !creative) {
				reason = Component.literal("Too Expensive! " + cost + " levels (the anvil allows at most 39)" + swap);
			} else if (swapped.valid()) {
				reason = Component.literal("Invalid this way round" + swap);
			} else if (here.status() == AnvilCost.Status.INVALID_CONFLICT) {
				reason = Component.literal("Invalid: the enchantments conflict or don't fit this item");
			} else {
				reason = Component.literal("Invalid: these two items can't be combined");
			}
		} else if (swapped.valid() && here.valid() && swapped.cost() < here.cost()) {
			ok = false;
			reason = Component.literal("Cheaper order exists: swap the inputs (" + swapped.cost() + " instead of " + here.cost() + " levels)");
		} else {
			ok = true;
			reason = Component.literal("Valid, cheapest order: " + cost + " levels");
		}

		XpSaverConfig cfg = XpSaverConfig.get();
		int x = Panel.leftPos(screen.width) + MARK_X + cfg.markOffsetX;
		int y = Panel.topPos(screen.height) + MARK_Y + cfg.markOffsetY;
		// The mark sits on the anvil's own light window whatever the panel style, so it uses the Inventory style's colors and shadow.
		XpSaverConfig.Palette light = cfg.inventory;
		g.text(mc.font, ok ? "✔" : "✘", x, y, Panel.argb(ok ? light.green : light.red), light.textShadow);
		if (mouseX >= x - 1 && mouseX < x + 9 && mouseY >= y - 1 && mouseY < y + 9) {
			g.setComponentTooltipForNextFrame(mc.font, List.of(reason.copy().withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED)), mouseX, mouseY);
		}
	}
}
