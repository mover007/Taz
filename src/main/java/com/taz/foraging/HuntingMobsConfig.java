package com.taz.foraging;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Galatea > Hunting Mobs in the Taz settings GUI (/taz). */
public final class HuntingMobsConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Makes hard-to-spot hunting mobs glow through walls (for your lasso or net), and can mute Phantoms.")
	@ConfigEditorBoolean
	@SearchTag("hunting mobs glow highlight hideonleaf hideonsun shellwise coralot blue jay lasso shard phantom mute")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Hideonleaf", desc = "§aGreen§7 glow. Moonglade Marsh: the green shulkers hidden in the leaves.")
	@ConfigEditorBoolean
	public boolean hideonleaf = true;

	@Expose
	@ConfigOption(name = "Hideonsun", desc = "§eYellow§7 glow. Torrhus Canyon: the yellow, orange and brown shulkers.")
	@ConfigEditorBoolean
	public boolean hideonsun = true;

	@Expose
	@ConfigOption(name = "Shellwise", desc = "§3Teal§7 glow. Moonglade Marsh: the turtles.")
	@ConfigEditorBoolean
	public boolean shellwise = true;

	@Expose
	@ConfigOption(name = "Coralot", desc = "§dPink§7 glow. Moonglade Marsh: the axolotls.")
	@ConfigEditorBoolean
	public boolean coralot = true;

	@Expose
	@ConfigOption(name = "Blue Jay", desc = "§9Blue§7 glow. Torrhus Canyon: the blue parrots.")
	@ConfigEditorBoolean
	public boolean blueJay = true;

	@Expose
	@ConfigOption(name = "Mute Phantoms", desc = "No Phantom sounds on Galatea.")
	@ConfigEditorBoolean
	public boolean mutePhantoms = true;

	public static HuntingMobsConfig get() {
		return Taz.config().galatea.huntingMobs;
	}
}
