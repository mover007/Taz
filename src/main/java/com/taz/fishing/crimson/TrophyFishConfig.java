package com.taz.fishing.crimson;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import com.taz.hud.HudPos;
import com.taz.hud.TazHud;
import com.taz.util.Alert;
import io.github.notenoughupdates.moulconfig.annotations.ConfigAccordionId;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorAccordion;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** The "Trophy Fishing" category of the Taz settings GUI (/taz): Trophy Fish on the Crimson Isle. */
public final class TrophyFishConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Your guide to Trophy Fishing on the Crimson Isle: your 72 Trophy Fish with icons, counts "
			+ "and pity, which fish can bite where you're standing and how to get the ones you're missing, and alerts for rare "
			+ "catches and the Golden Fish.")
	@ConfigEditorBoolean
	@SearchTag("trophy fish fishing crimson isle odger lava magmafish golden fish obfuscated sulphur skitter mana ray vanille")
	public boolean enabled = false;

	@ConfigOption(name = "Trophy Fish", desc = "Your 72 Trophy Fish.")
	@ConfigEditorAccordion(id = 0)
	public boolean section0 = false;

	@Expose
	@ConfigAccordionId(id = 0)
	@ConfigOption(name = "Trophy Fish HUD", desc = "A table of every fish with its icon, how many you've caught of each tier, and your "
			+ "catches toward the Gold (100) and Diamond (600) pity. Open Odger's Trophy Fish menu once to load all your "
			+ "catches; after that Taz counts every new one. Before that, it counts what's in your chat logs (on this computer).")
	@ConfigEditorBoolean
	public boolean tracker = true;

	@Expose
	@ConfigAccordionId(id = 0)
	@ConfigOption(name = "Only missing", desc = "Hide fish you've caught at every tier.")
	@ConfigEditorBoolean
	public boolean onlyMissing = false;

	@Expose
	@ConfigAccordionId(id = 0)
	@ConfigOption(name = "Pity column", desc = "Your catches of each fish toward its Gold pity, then its Diamond pity.")
	@ConfigEditorBoolean
	public boolean pity = true;

	@ConfigAccordionId(id = 0)
	@ConfigOption(name = "Read chat logs again", desc = "Count your Trophy Fish from your chat logs from scratch.")
	@ConfigEditorButton(buttonText = "Re-read")
	public transient Runnable reimport = TrophyFish::reimport;

	@ConfigAccordionId(id = 0)
	@ConfigOption(name = "Trophy Fish HUD position", desc = "Drag it to move, scroll to resize.")
	@ConfigEditorButton(buttonText = "Edit")
	public transient Runnable editTracker = TazHud::openEditor;

	@ConfigOption(name = "Fish guide", desc = "What to fish for next.")
	@ConfigEditorAccordion(id = 1)
	public boolean section1 = false;

	@Expose
	@ConfigAccordionId(id = 1)
	@ConfigOption(name = "Fish guide HUD", desc = "Which Trophy Fish can bite where you're standing right now, the Blazing Volcano, "
			+ "your mana and bobber time, and the fish you still need at your tier with how to get each: ✔ when you're set up "
			+ "for it (in the right area, near Sulphur, 8 blocks above the lava, 1,200 mana, bobber out 20s, Starter Lava Rod).")
	@ConfigEditorBoolean
	public boolean guide = true;

	@ConfigAccordionId(id = 1)
	@ConfigOption(name = "Fish guide HUD position", desc = "Drag it to move, scroll to resize.")
	@ConfigEditorButton(buttonText = "Edit")
	public transient Runnable editGuide = TazHud::openEditor;

	@ConfigOption(name = "Alerts", desc = "Titles for rare catches and the Golden Fish.")
	@ConfigEditorAccordion(id = 2)
	public boolean section2 = false;

	@Expose
	@ConfigAccordionId(id = 2)
	@ConfigOption(name = "Fish titles", desc = "A title for every Gold or Diamond Trophy Fish, and for each fish new to your collection.")
	@ConfigEditorBoolean
	public boolean fishTitles = true;

	@Expose
	@ConfigAccordionId(id = 2)
	@ConfigOption(name = "Golden Fish", desc = "A title when a Golden Fish surfaces, and when it's weak enough to reel in.")
	@ConfigEditorBoolean
	public boolean goldenFish = true;

	@Expose
	@ConfigAccordionId(id = 2)
	@ConfigOption(name = "Tell your party", desc = "Say Gold and Diamond fish in party chat.")
	@ConfigEditorBoolean
	public boolean party = false;

	@Expose
	@ConfigAccordionId(id = 2)
	@ConfigOption(name = "Sound", desc = "Played with the titles.")
	@ConfigEditorDropdown
	public Alert.Sound sound = Alert.Sound.PLING;

	@Expose
	public HudPos trackerHud = new HudPos(0.01, 0.20);

	@Expose
	public HudPos guideHud = new HudPos(0.62, 0.20);

	public static TrophyFishConfig get() {
		return Taz.config().trophyFish;
	}
}
