package com.taz.general;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorKeybind;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;
import org.lwjgl.glfw.GLFW;

/** General > Loadout Keybinds in the Taz settings GUI (/taz). */
public final class LoadoutKeybindsConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "In the Loadout menu: number keys pick a loadout, the arrow keys turn the page.")
	@ConfigEditorBoolean
	@SearchTag("loadout keybinds equipment wardrobe")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Next page", desc = "")
	@ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_RIGHT)
	public int nextPage = GLFW.GLFW_KEY_RIGHT;

	@Expose
	@ConfigOption(name = "Previous page", desc = "")
	@ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_LEFT)
	public int previousPage = GLFW.GLFW_KEY_LEFT;

	@Expose
	@ConfigOption(name = "Loadout 1", desc = "")
	@ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_1)
	public int loadout1 = GLFW.GLFW_KEY_1;

	@Expose
	@ConfigOption(name = "Loadout 2", desc = "")
	@ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_2)
	public int loadout2 = GLFW.GLFW_KEY_2;

	@Expose
	@ConfigOption(name = "Loadout 3", desc = "")
	@ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_3)
	public int loadout3 = GLFW.GLFW_KEY_3;

	@Expose
	@ConfigOption(name = "Loadout 4", desc = "")
	@ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_4)
	public int loadout4 = GLFW.GLFW_KEY_4;

	@Expose
	@ConfigOption(name = "Loadout 5", desc = "")
	@ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_5)
	public int loadout5 = GLFW.GLFW_KEY_5;

	@Expose
	@ConfigOption(name = "Loadout 6", desc = "")
	@ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_6)
	public int loadout6 = GLFW.GLFW_KEY_6;

	@Expose
	@ConfigOption(name = "Loadout 7", desc = "")
	@ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_7)
	public int loadout7 = GLFW.GLFW_KEY_7;

	@Expose
	@ConfigOption(name = "Loadout 8", desc = "")
	@ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_8)
	public int loadout8 = GLFW.GLFW_KEY_8;

	@Expose
	@ConfigOption(name = "Loadout 9", desc = "")
	@ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_9)
	public int loadout9 = GLFW.GLFW_KEY_9;

	@Expose
	@ConfigOption(name = "Loadout 10", desc = "")
	@ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_0)
	public int loadout10 = GLFW.GLFW_KEY_0;

	@Expose
	@ConfigOption(name = "Loadout 11", desc = "")
	@ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_MINUS)
	public int loadout11 = GLFW.GLFW_KEY_MINUS;

	@Expose
	@ConfigOption(name = "Loadout 12", desc = "")
	@ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_EQUAL)
	public int loadout12 = GLFW.GLFW_KEY_EQUAL;

	public int[] keys() {
		return new int[] {loadout1, loadout2, loadout3, loadout4, loadout5, loadout6, loadout7, loadout8, loadout9, loadout10, loadout11, loadout12};
	}

	public static LoadoutKeybindsConfig get() {
		return Taz.config().general.loadoutKeybinds;
	}
}
