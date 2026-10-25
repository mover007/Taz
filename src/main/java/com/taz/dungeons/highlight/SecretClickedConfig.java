package com.taz.dungeons.highlight;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Dungeons > Secret Clicked in the Taz settings GUI (/taz). */
public final class SecretClickedConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "A box on each secret you click or pick up for a few seconds (black if the chest is locked), and a chime.")
	@ConfigEditorBoolean
	@SearchTag("secret clicked chime box chest lever skull locked")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Boxes", desc = "Box the secrets you click.")
	@ConfigEditorBoolean
	public boolean boxes = true;

	@Expose
	@ConfigOption(name = "Item boxes", desc = "Also box where secret items were when you picked them up.")
	@ConfigEditorBoolean
	public boolean items = true;

	@Expose
	@ConfigOption(name = "Through walls", desc = "Draw the boxes through walls.")
	@ConfigEditorBoolean
	public boolean throughWalls = false;

	@Expose
	@ConfigOption(name = "Seconds", desc = "How long a box stays.")
	@ConfigEditorSlider(minValue = 1, maxValue = 60, minStep = 0.5f)
	public float seconds = 7;

	@Expose
	@ConfigOption(name = "Chime", desc = "Play a chime (the blaze's hurt sound) when you get a secret.")
	@ConfigEditorBoolean
	public boolean chime = true;

	@Expose
	@ConfigOption(name = "Chime volume", desc = "How loud the chime is.")
	@ConfigEditorSlider(minValue = 10, maxValue = 100, minStep = 5)
	public float volume = 100;

	@Expose
	@ConfigOption(name = "In boss", desc = "Also in the boss room.")
	@ConfigEditorBoolean
	public boolean inBoss = false;

	public static SecretClickedConfig get() {
		return Taz.config().dungeons.secretClicked;
	}
}
