package com.taz.general;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.annotations.Accordion;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorKeybind;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorText;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;
import org.lwjgl.glfw.GLFW;

/** Controls > Command Keys in the Taz settings GUI (/taz): a key for any command. */
public final class CommandKeysConfig {
	public static final class Slot {
		@Expose
		@ConfigOption(name = "Key", desc = "Press this in game (with no menu open) to run the command.")
		@ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
		public int key = GLFW.GLFW_KEY_UNKNOWN;

		@Expose
		@ConfigOption(name = "Command", desc = "What to run, with the slash: /pets, /warp garden, /pc hi ...")
		@ConfigEditorText
		public String command;

		public Slot() {
			this("");
		}

		Slot(String command) {
			this.command = command;
		}
	}

	@Expose
	@ConfigOption(name = "Enabled", desc = "Bind keys to commands, like /pets or /warp garden. Every key starts unbound.")
	@ConfigEditorBoolean
	@SearchTag("hotkey keybind command warp pets storage wardrobe")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Key 1", desc = "")
	@Accordion
	public Slot slot1 = new Slot("/pets");

	@Expose
	@ConfigOption(name = "Key 2", desc = "")
	@Accordion
	public Slot slot2 = new Slot("/storage");

	@Expose
	@ConfigOption(name = "Key 3", desc = "")
	@Accordion
	public Slot slot3 = new Slot("/wardrobe");

	@Expose
	@ConfigOption(name = "Key 4", desc = "")
	@Accordion
	public Slot slot4 = new Slot("/equipment");

	@Expose
	@ConfigOption(name = "Key 5", desc = "")
	@Accordion
	public Slot slot5 = new Slot("/warp hub");

	@Expose
	@ConfigOption(name = "Key 6", desc = "")
	@Accordion
	public Slot slot6 = new Slot("/warp garden");

	@Expose
	@ConfigOption(name = "Key 7", desc = "")
	@Accordion
	public Slot slot7 = new Slot("/warp dungeon_hub");

	@Expose
	@ConfigOption(name = "Key 8", desc = "")
	@Accordion
	public Slot slot8 = new Slot("/bz");

	public Slot[] slots() {
		return new Slot[]{slot1, slot2, slot3, slot4, slot5, slot6, slot7, slot8};
	}

	public static CommandKeysConfig get() {
		return Taz.config().controls.commandKeys;
	}
}
