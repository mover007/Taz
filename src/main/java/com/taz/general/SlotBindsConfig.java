package com.taz.general;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import com.taz.util.TazChat;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorKeybind;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.Map;

/** General > Slot Binds in the Taz settings GUI (/taz). */
public final class SlotBindsConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Bind inventory slots to hotbar slots, then shift + click either to swap them. In your inventory, press the bind "
			+ "key on one slot and then the other (on a bound slot, it removes the bind).")
	@ConfigEditorBoolean
	@SearchTag("slot binds slotbinds swap hotbar inventory")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Bind key", desc = "Press it over a slot in your inventory.")
	@ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
	public int bindKey = GLFW.GLFW_KEY_UNKNOWN;

	@ConfigOption(name = "Copy from Odin", desc = "Copies the slot binds from your Odin settings.")
	@ConfigEditorButton(buttonText = "Copy")
	public transient Runnable importOdin = InventoryKeys::importFromOdin;

	@ConfigOption(name = "Clear binds", desc = "Removes every slot bind.")
	@ConfigEditorButton(buttonText = "Clear")
	public transient Runnable clear = () -> {
		binds().clear();
		Taz.save();
		TazChat.info("Slot binds cleared.");
	};

	/** Slot -> the slot it's bound to (inventory slot numbers: hotbar 36-44). */
	@Expose
	public Map<String, Integer> binds = new LinkedHashMap<>();

	private static Map<String, Integer> binds() {
		return get().binds;
	}

	public static SlotBindsConfig get() {
		return Taz.config().general.slotBinds;
	}
}
