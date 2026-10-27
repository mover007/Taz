package com.taz.general;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Visuals > Render Optimizer in the Taz settings GUI (/taz). */
public final class RenderOptimizerConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Stops drawing things you don't need: falling blocks, lightning, XP orbs, dying mobs, explosions, the fire on your screen and more.")
	@ConfigEditorBoolean
	@SearchTag("render optimizer hide falling blocks lightning xp orbs death animation explosion fire overlay fps")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Falling blocks", desc = "")
	@ConfigEditorBoolean
	public boolean fallingBlocks = true;

	@Expose
	@ConfigOption(name = "Lightning", desc = "")
	@ConfigEditorBoolean
	public boolean lightning = true;

	@Expose
	@ConfigOption(name = "XP orbs", desc = "")
	@ConfigEditorBoolean
	public boolean xpOrbs = true;

	@Expose
	@ConfigOption(name = "Dying mobs", desc = "Mobs in their death animation.")
	@ConfigEditorBoolean
	public boolean dying = true;

	@Expose
	@ConfigOption(name = "Dying mobs' name tags", desc = "Also their name tags.")
	@ConfigEditorBoolean
	public boolean dyingTags = false;

	@Expose
	@ConfigOption(name = "Explosion particles", desc = "")
	@ConfigEditorBoolean
	public boolean explosions = true;

	@Expose
	@ConfigOption(name = "Fire on screen", desc = "The flames over your view while you burn.")
	@ConfigEditorBoolean
	public boolean fireOverlay = true;

	@Expose
	@ConfigOption(name = "Archer passive", desc = "Dungeons: the Archer passive's floating bone meal.")
	@ConfigEditorBoolean
	public boolean archerBoneMeal = true;

	@Expose
	@ConfigOption(name = "Healer fairy", desc = "Dungeons: the healer fairy some mobs hold.")
	@ConfigEditorBoolean
	public boolean healerFairy = true;

	@Expose
	@ConfigOption(name = "Soul Weaver", desc = "Dungeons: the Soul Weaver head some mobs wear.")
	@ConfigEditorBoolean
	public boolean soulWeaver = true;

	@Expose
	@ConfigOption(name = "Tentacle head", desc = "Dungeons: the tentacle head some mobs wear.")
	@ConfigEditorBoolean
	public boolean tentacle = true;

	public static RenderOptimizerConfig get() {
		return Taz.config().visuals.renderOptimizer;
	}
}
