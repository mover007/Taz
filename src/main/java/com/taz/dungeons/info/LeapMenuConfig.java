package com.taz.dungeons.info;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorKeybind;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorText;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;
import org.lwjgl.glfw.GLFW;

/** Dungeons > Leap Menu in the Taz settings GUI (/taz). */
public final class LeapMenuConfig {
	public enum Order {
		ODIN("By class (Odin)"), CLASS("Class A-Z"), NAME("Name A-Z"), NONE("As Hypixel lists them");

		private final String label;

		Order(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	@Expose
	@ConfigOption(name = "Enabled", desc = "The Spirit Leap menu as four big boxes (face, name, class), and an optional party message when you leap.")
	@ConfigEditorBoolean
	@SearchTag("leap menu spirit leap infinileap teleport keybinds")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Custom menu", desc = "Show the four boxes instead of Hypixel's menu.")
	@ConfigEditorBoolean
	public boolean menu = true;

	@Expose
	@ConfigOption(name = "Size", desc = "")
	@ConfigEditorSlider(minValue = 30, maxValue = 100, minStep = 1)
	public int scale = 50;

	@Expose
	@ConfigOption(name = "Order", desc = "Where each teammate goes. By class puts the classes you usually leap to first, like Odin.")
	@ConfigEditorDropdown
	public Order order = Order.ODIN;

	@Expose
	@ConfigOption(name = "Left click only", desc = "Only a left click leaps.")
	@ConfigEditorBoolean
	public boolean leftClickOnly = false;

	@Expose
	@ConfigOption(name = "Keys", desc = "Keys 1-4 leap to the box with that number.")
	@ConfigEditorBoolean
	public boolean keys = true;

	@Expose
	@ConfigOption(name = "Key 1", desc = "")
	@ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_1)
	public int key1 = GLFW.GLFW_KEY_1;

	@Expose
	@ConfigOption(name = "Key 2", desc = "")
	@ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_2)
	public int key2 = GLFW.GLFW_KEY_2;

	@Expose
	@ConfigOption(name = "Key 3", desc = "")
	@ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_3)
	public int key3 = GLFW.GLFW_KEY_3;

	@Expose
	@ConfigOption(name = "Key 4", desc = "")
	@ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_4)
	public int key4 = GLFW.GLFW_KEY_4;

	@Expose
	@ConfigOption(name = "Say who you leaped to", desc = "A party chat message when you leap.")
	@ConfigEditorBoolean
	public boolean announce = true;

	@Expose
	@ConfigOption(name = "Message", desc = "{name} becomes who you leaped to.")
	@ConfigEditorText
	public String message = "Leaping to {name}";

	public static LeapMenuConfig get() {
		return Taz.config().dungeons.leapMenu;
	}
}
