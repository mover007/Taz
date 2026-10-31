package com.taz.general;

import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorInfoText;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** General > Player Viewer in the Taz settings GUI (/taz): what /pv and /nw do. They're commands, so there's nothing to switch on. */
public final class PlayerViewerConfig {
	@ConfigOption(name = "Commands", desc = "§e/pv [player]§7 opens a player viewer: skills, slayers, dungeons, gear, inventories and pets. "
			+ "§e/nw [player]§7 says a networth: with cosmetics, without them, and what isn't soulbound. With no name, it's you.\n\n"
			+ "Data from Hypixel's API, through Taz's server; prices provided by SkyCofl (sky.coflnet.com). Taz isn't affiliated with or "
			+ "endorsed by SkyCofl or Hypixel.")
	@ConfigEditorInfoText
	@SearchTag("pv nw networth profile viewer hypixel cofl skycofl")
	public transient String info = "";
}
