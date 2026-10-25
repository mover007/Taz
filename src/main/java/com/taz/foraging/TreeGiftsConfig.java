package com.taz.foraging;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import com.taz.hud.HudPos;
import com.taz.hud.TazHud;
import com.taz.util.Alert;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Galatea > Tree Gifts & Loot in the Taz settings GUI (/taz). */
public final class TreeGiftsConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Counts your Tree Gifts (per tree, bonus gifts, KARMA double loot, mobs that fall out), honeyhive "
			+ "Honeycomb and today's Lottery buff in a HUD, shrinks each Tree Gift to one chat line and alerts rare bonus drops.")
	@ConfigEditorBoolean
	@SearchTag("tree gift bonus gift karma double loot phanpyre lottery buff honeycomb compact chat rare")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "One line per Tree Gift", desc = "Replace the 10-line TREE GIFT block with one line (hover it for the rewards).")
	@ConfigEditorBoolean
	public boolean compact = true;

	@Expose
	@ConfigOption(name = "Hide \"tree is regenerating\"", desc = "Hide \"You cannot damage a tree while it is regenerating!\" (the Next Tree Pointer skips those trees anyway).")
	@ConfigEditorBoolean
	public boolean hideRegenerating = true;

	@Expose
	@ConfigOption(name = "Rare bonus alert", desc = "A title when a Bonus Gift drop is this rare or rarer (in %), like Hummingbird Shard (0.15%).")
	@ConfigEditorSlider(minValue = 0, maxValue = 5, minStep = 0.05f)
	public float rareChance = 1.0f;

	@Expose
	@ConfigOption(name = "Rare bonus sound", desc = "")
	@ConfigEditorDropdown
	public Alert.Sound sound = Alert.Sound.LEVEL_UP;

	@Expose
	@ConfigOption(name = "HUD", desc = "Show the counts in a HUD.")
	@ConfigEditorBoolean
	public boolean hudEnabled = true;

	@ConfigOption(name = "Reset counts", desc = "Start counting from zero.")
	@ConfigEditorButton(buttonText = "Reset")
	public transient Runnable reset = TreeGifts::resetCounts;

	@ConfigOption(name = "Move HUD", desc = "Opens the HUD editor (every Taz HUD): drag to move, scroll to resize.")
	@ConfigEditorButton(buttonText = "Edit")
	public transient Runnable moveHud = TazHud::openEditor;

	@Expose
	public HudPos hud = new HudPos(0.005, 0.40);

	public static TreeGiftsConfig get() {
		return Taz.config().galatea.treeGifts;
	}
}
