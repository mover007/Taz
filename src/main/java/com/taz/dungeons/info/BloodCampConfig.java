package com.taz.dungeons.info;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import com.taz.hud.HudPos;
import com.taz.hud.TazHud;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Dungeons > Blood Camp in the Taz settings GUI (/taz). */
public final class BloodCampConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "When the Watcher moves, and where each of his mobs will land and when, so you can kill them as they spawn.")
	@ConfigEditorBoolean
	@SearchTag("blood camp watcher mobs spawn assist blood rush")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Move HUD", desc = "A countdown to when the Watcher moves.")
	@ConfigEditorBoolean
	public boolean moveHud = true;

	@Expose
	@ConfigOption(name = "Move alert", desc = "Say when the Watcher will move, and a \"Kill Mobs\" title when he does.")
	@ConfigEditorBoolean
	public boolean moveAlert = true;

	@Expose
	@ConfigOption(name = "Spawn boxes", desc = "A box where each mob will land (pale: where it is now).")
	@ConfigEditorBoolean
	public boolean assist = true;

	@Expose
	@ConfigOption(name = "Line", desc = "A line from each mob to where it lands.")
	@ConfigEditorBoolean
	public boolean line = true;

	@Expose
	@ConfigOption(name = "Time left", desc = "Seconds until each mob lands.")
	@ConfigEditorBoolean
	public boolean time = true;

	@ConfigOption(name = "Move HUD position", desc = "Drag it to move, scroll to resize.")
	@ConfigEditorButton(buttonText = "Edit")
	public transient Runnable editHud = TazHud::openEditor;

	@Expose
	public HudPos hud = new HudPos(0.42, 0.38);

	public static BloodCampConfig get() {
		return Taz.config().dungeons.bloodCamp;
	}
}
