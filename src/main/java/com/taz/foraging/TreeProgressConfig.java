package com.taz.foraging;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import com.taz.hud.HudPos;
import com.taz.hud.TazHud;
import com.taz.util.Alert;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Galatea > Tree Progress in the Taz settings GUI (/taz). */
public final class TreeProgressConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "A HUD with how far the tree you're chopping is (\"Fig Tree 37%\"), a TIMBER! alert when it falls, "
			+ "and trees felled this session per type and per hour.")
	@ConfigEditorBoolean
	@SearchTag("tree progress percent timber felled fig mangrove helix trees per hour")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "TIMBER! alert", desc = "A title and sound when you fell a whole tree.")
	@ConfigEditorBoolean
	public boolean timberAlert = true;

	@Expose
	@ConfigOption(name = "Sound", desc = "The TIMBER! sound.")
	@ConfigEditorDropdown
	public Alert.Sound sound = Alert.Sound.LEVEL_UP;

	@Expose
	@ConfigOption(name = "Trees felled", desc = "Count trees felled per type, with trees per hour, in the HUD.")
	@ConfigEditorBoolean
	public boolean counts = true;

	@ConfigOption(name = "Reset counts", desc = "Start counting trees from zero.")
	@ConfigEditorButton(buttonText = "Reset")
	public transient Runnable reset = TreeProgress::resetCounts;

	@ConfigOption(name = "Move HUD", desc = "Opens the HUD editor (every Taz HUD): drag to move, scroll to resize.")
	@ConfigEditorButton(buttonText = "Edit")
	public transient Runnable moveHud = TazHud::openEditor;

	@Expose
	public HudPos hud = new HudPos(0.20, 0.64);

	public static TreeProgressConfig get() {
		return Taz.config().galatea.treeProgress;
	}
}
