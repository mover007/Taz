package com.taz.dungeons.highlight;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Dungeons > Doors & Keys in the Taz settings GUI (/taz). */
public final class DoorsAndKeysConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Boxes the wither and blood doors next to rooms you've been in (white once the party has the key, red "
			+ "until then), and the Wither and Blood keys through walls.")
	@ConfigEditorBoolean
	@SearchTag("door highlight wither blood key esp tracer blood rush")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Doors", desc = "Box the wither and blood doors.")
	@ConfigEditorBoolean
	public boolean doors = true;

	@Expose
	@ConfigOption(name = "Keys", desc = "Box the Wither and Blood keys through walls.")
	@ConfigEditorBoolean
	public boolean keys = true;

	@Expose
	@ConfigOption(name = "Key tracer", desc = "A line from your crosshair to the key.")
	@ConfigEditorBoolean
	public boolean tracer = true;

	@Expose
	@ConfigOption(name = "Key spawn alert", desc = "A title when a key spawns.")
	@ConfigEditorBoolean
	public boolean announce = true;

	public static DoorsAndKeysConfig get() {
		return Taz.config().dungeons.doorsAndKeys;
	}
}
