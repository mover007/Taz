package com.taz.general;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorInfoText;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorText;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;
import net.minecraft.util.Util;

import java.net.URI;

/** General > Player Viewer in the Taz settings GUI (/taz): /pv and /nw, with data from SkyCrypt. They're commands, so there's no switch. */
public final class PlayerViewerConfig {
	@ConfigOption(name = "Commands", desc = "§e/pv [player]§7 opens a player viewer: skills, slayers, dungeons, gear, inventories and pets. "
			+ "§e/nw [player]§7 says a networth: with cosmetics, without them, and what isn't soulbound. With no name, it's you.\n\n"
			+ "Data from SkyCrypt (sky.shiiyu.moe). Taz isn't affiliated with or endorsed by SkyCrypt or Hypixel.")
	@ConfigEditorInfoText
	public transient String info = "";

	@Expose
	@ConfigOption(name = "SkyCrypt API key", desc = "/pv and /nw need your own key: sign in on sky.shiiyu.moe, open your dashboard's "
			+ "Developer page and request one, then paste it here. Keep it private: don't share it or show it on stream.")
	@ConfigEditorText
	@SearchTag("pv nw networth profile viewer skycrypt api key")
	public String apiKey = "";

	@ConfigOption(name = "Get a key", desc = "Opens your SkyCrypt dashboard in your browser.")
	@ConfigEditorButton(buttonText = "Open")
	public transient Runnable getKey = () -> Util.getPlatform().openUri(URI.create("https://sky.shiiyu.moe/dashboard"));

	public static PlayerViewerConfig get() {
		return Taz.config().general.viewer;
	}
}
