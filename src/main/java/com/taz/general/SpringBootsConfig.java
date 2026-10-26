package com.taz.general;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import com.taz.hud.HudPos;
import com.taz.hud.TazHud;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** General > Spring Boots in the Taz settings GUI (/taz). */
public final class SpringBootsConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "How high your Spring Boots will throw you as you charge them.")
	@ConfigEditorBoolean
	@SearchTag("spring boots jump height charge")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "HUD", desc = "Show the height on screen.")
	@ConfigEditorBoolean
	public boolean hudOn = true;

	@Expose
	@ConfigOption(name = "As a percentage", desc = "Show how charged they are instead of blocks.")
	@ConfigEditorBoolean
	public boolean percent = false;

	@Expose
	@ConfigOption(name = "Box", desc = "A box at the height you'll reach, seen through walls.")
	@ConfigEditorBoolean
	public boolean box = true;

	@ConfigOption(name = "Move HUD", desc = "Drag it to move, scroll to resize.")
	@ConfigEditorButton(buttonText = "Edit")
	public transient Runnable moveHud = TazHud::openEditor;

	@Expose
	public HudPos hud = new HudPos(0.47, 0.60);

	public static SpringBootsConfig get() {
		return Taz.config().general.springBoots;
	}
}
