package com.taz.general;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorText;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Visuals > Sound Volumes in the Taz settings GUI (/taz). */
public final class SoundVolumesConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Make any sound louder or quieter by its id (0 is silent, 1 normal, 2 the most).")
	@ConfigEditorBoolean
	@SearchTag("sound manager volume mute louder quieter amethyst")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Volumes", desc = "sound=multiplier, separated by commas. Example: block.amethyst_block.break=1.35, entity.blaze.hurt=0")
	@ConfigEditorText
	public String volumes = "minecraft:block.amethyst_block.break=1.35";

	public static SoundVolumesConfig get() {
		return Taz.config().visuals.soundVolumes;
	}
}
