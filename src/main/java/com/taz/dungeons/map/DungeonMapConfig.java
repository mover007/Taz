package com.taz.dungeons.map;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import com.taz.hud.HudPos;
import com.taz.hud.TazHud;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Dungeons > Dungeon Map in the Taz settings GUI (/taz). */
public final class DungeonMapConfig {
	public enum Style {
		CHECKMARKS("Ticks"), SECRETS("Secrets"), NAME("Room name"), NAME_SECRETS("Room name + secrets");

		private final String label;

		Style(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	public enum Names {
		OFF("Off"), HOLDING_LEAP("Holding a Spirit Leap"), ALWAYS("Always");

		private final String label;

		Names(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	@Expose
	@ConfigOption(name = "Enabled", desc = "Hypixel's dungeon map redrawn bigger and clearer, with room names, secrets and your teammates' faces. Shows only "
			+ "what Hypixel's map shows.")
	@ConfigEditorBoolean
	@SearchTag("dungeon map minimap rooms checkmarks secrets player heads")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "In each room", desc = "What to write on each room you've opened.")
	@ConfigEditorDropdown
	public Style style = Style.NAME_SECRETS;

	@Expose
	@ConfigOption(name = "Hide on the Blood room", desc = "Nothing written on the Blood room.")
	@ConfigEditorBoolean
	public boolean hideBlood = false;

	@Expose
	@ConfigOption(name = "Text size", desc = "")
	@ConfigEditorSlider(minValue = 0.4f, maxValue = 1.5f, minStep = 0.1f)
	public float textScale = 1f;

	@Expose
	@ConfigOption(name = "Names", desc = "Teammates' names under their faces.")
	@ConfigEditorDropdown
	public Names names = Names.ALWAYS;

	@Expose
	@ConfigOption(name = "Your name", desc = "Your own name too.")
	@ConfigEditorBoolean
	public boolean ownName = false;

	@Expose
	@ConfigOption(name = "Face size", desc = "")
	@ConfigEditorSlider(minValue = 0.3f, maxValue = 1.5f, minStep = 0.1f)
	public float headScale = 1f;

	@Expose
	@ConfigOption(name = "Name size", desc = "")
	@ConfigEditorSlider(minValue = 0.3f, maxValue = 1.5f, minStep = 0.1f)
	public float nameScale = 0.5f;

	@Expose
	@ConfigOption(name = "Hide in boss", desc = "No map in the boss room.")
	@ConfigEditorBoolean
	public boolean hideInBoss = false;

	@ConfigOption(name = "Move map", desc = "Opens the HUD editor (every Taz HUD): drag to move, scroll to resize.")
	@ConfigEditorButton(buttonText = "Edit")
	public transient Runnable editHud = TazHud::openEditor;

	@Expose
	public HudPos hud = new HudPos(0.005, 0.30);

	public static DungeonMapConfig get() {
		return Taz.config().dungeons.map;
	}
}
