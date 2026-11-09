package xpsaver.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.resources.Identifier;
import xpsaver.client.config.XpSaverConfig;

/**
 * Client entrypoint. Hooks used (all from Fabric API 0.165.2+26.3, no mixins):
 * ScreenEvents.AFTER_INIT / afterForeground (fabric-screen-api-v1), KeyMappingHelper.registerKeyMapping (fabric-key-mapping-api-v1),
 * ClientTickEvents.END_CLIENT_TICK (fabric-lifecycle-events-v1).
 */
public final class XpSaverClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("xpsaver", "main"));
	private static KeyMapping plannerKey;

	/** The planner's key binding (default K); rebound in Controls or the XP Saver settings screen, saved in options.txt. */
	public static KeyMapping plannerKey() {
		return plannerKey;
	}

	@Override
	public void onInitializeClient() {
		XpSaverConfig.load(); // config/xpsaver.json; edited in game through Mod Menu (ModMenuIntegration)
		plannerKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.xpsaver.planner", InputConstants.Type.KEYBOARD, InputConstants.KEY_K, CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (plannerKey.consumeClick()) {
				if (mc.player != null && mc.level != null) {
					mc.gui.setScreen(new PlannerScreen(mc)); // 26.3 opens screens through Gui#setScreen
				}
			}
		});

		// afterForeground fires inside AbstractContainerScreen#extractRenderState after extractContents (screen coordinates)
		// and before the frame's deferred tooltip is drawn; afterExtract fires too late for setComponentTooltipForNextFrame.
		ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
			if (screen instanceof EnchantmentScreen enchanting) {
				EnchantPanel.attach(enchanting);
			} else if (screen instanceof AnvilScreen anvil) {
				ScreenEvents.afterForeground(screen).register((s, g, mouseX, mouseY, delta) -> AnvilMark.draw(anvil, g, mouseX, mouseY));
				AnvilPlanner.attach(anvil);
			}
		});
	}
}
