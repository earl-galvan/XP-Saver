package xpsaver.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Mod Menu's "modmenu" entrypoint (fabric.mod.json): gives XP Saver's entry in the Mods list a config button.
 * Mod Menu is optional; without it this class is never loaded and the mod works the same (settings stay in config/xpsaver.json).
 */
public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ConfigScreen::new;
	}
}
