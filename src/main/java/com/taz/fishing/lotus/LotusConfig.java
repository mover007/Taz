package com.taz.fishing.lotus;

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

/** The "Lotus Atoll" category of the Taz settings GUI (/taz). */
public final class LotusConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Your guide to the Lotus Atoll: your 48 Trophy Frogs with pity counts, which frogs can bite "
			+ "where you're standing and how to get the ones you're missing, waypoints for its places and Wormhole spots, its Fairy "
			+ "Souls, and alerts for rare frogs and sea creatures.")
	@ConfigEditorBoolean
	@SearchTag("lotus atoll trophy frog frogs ribery betty wormhole froggles frogcoin blessing lily pad puddle jumper frog prince fishing")
	public boolean enabled = false;

	@ConfigOption(name = "Trophy Frogs", desc = "Your 48 Trophy Frogs.")
	@ConfigEditorAccordion(id = 0)
	public boolean section0 = false;

	@Expose
	@ConfigAccordionId(id = 0)
	@ConfigOption(name = "Trophy Frog HUD", desc = "A table of every species with its icon, how many you've caught of each tier, and your catches toward the "
			+ "Gold (100, or 50 for Exploding and Puddle Jumper) and Diamond (6x that) pity. The tiers you have come from the tab "
			+ "list's Trophy Frogs widget; past catches are counted from your chat logs, so they count from before Taz (on this "
			+ "computer).")
	@ConfigEditorBoolean
	public boolean tracker = true;

	@Expose
	@ConfigAccordionId(id = 0)
	@ConfigOption(name = "Only missing", desc = "Hide species you've caught at every tier.")
	@ConfigEditorBoolean
	public boolean onlyMissing = false;

	@Expose
	@ConfigAccordionId(id = 0)
	@ConfigOption(name = "Pity column", desc = "Your catches of each species toward its Gold pity, then its Diamond pity.")
	@ConfigEditorBoolean
	public boolean pity = true;

	@ConfigAccordionId(id = 0)
	@ConfigOption(name = "Read chat logs again", desc = "Count your Trophy Frogs from your chat logs from scratch.")
	@ConfigEditorButton(buttonText = "Re-read")
	public transient Runnable reimport = TrophyFrogs::reimport;

	@ConfigAccordionId(id = 0)
	@ConfigOption(name = "Trophy Frog HUD position", desc = "Drag it to move, scroll to resize.")
	@ConfigEditorButton(buttonText = "Edit")
	public transient Runnable editTracker = TazHud::openEditor;

	@ConfigOption(name = "Frog guide", desc = "What to fish for next.")
	@ConfigEditorAccordion(id = 1)
	public boolean section1 = false;

	@Expose
	@ConfigAccordionId(id = 1)
	@ConfigOption(name = "Frog guide HUD", desc = "Which Trophy Frogs can bite where you're standing right now, the rain and your "
			+ "Frogcoin blessing, and the frogs you still need at your tier with how to get each: ✔ when you're set up for it "
			+ "(raining, Red Sweater on, underwater, on leaves, in the air, in the Cave or Highlands).")
	@ConfigEditorBoolean
	public boolean guide = true;

	@ConfigAccordionId(id = 1)
	@ConfigOption(name = "Frog guide HUD position", desc = "Drag it to move, scroll to resize.")
	@ConfigEditorButton(buttonText = "Edit")
	public transient Runnable editGuide = TazHud::openEditor;

	@ConfigOption(name = "Waypoints", desc = "Places, Wormhole spots and Fairy Souls.")
	@ConfigEditorAccordion(id = 2)
	public boolean section2 = false;

	@Expose
	@ConfigAccordionId(id = 2)
	@ConfigOption(name = "Places", desc = "Labels on Ribery, Betty, the Lotus Eater's Cave, the Highlands launch (Researcher Kay), the "
			+ "Lotus Highlands and the Tewtil Tunnel.")
	@ConfigEditorBoolean
	public boolean places = true;

	@Expose
	@ConfigAccordionId(id = 2)
	@ConfigOption(name = "Wormhole spots", desc = "The 21 spots a Wormhole can open at (two are open at a time). Reality Hoppers only "
			+ "bite in a Wormhole, and you only see them wearing Froggles.")
	@ConfigEditorBoolean
	public boolean wormholes = true;

	@Expose
	@ConfigAccordionId(id = 2)
	@ConfigOption(name = "Without Froggles too", desc = "Show the Wormhole spots even when you're not wearing Froggles.")
	@ConfigEditorBoolean
	public boolean wormholesAlways = false;

	@Expose
	@ConfigAccordionId(id = 2)
	@ConfigOption(name = "Fairy Souls", desc = "Waypoints for the Atoll's 6 Fairy Souls you haven't found (colour, pointer and hints are "
			+ "set in Galatea > Fairy Souls).")
	@ConfigEditorBoolean
	public boolean fairySouls = true;

	@ConfigOption(name = "Alerts", desc = "Titles for rare catches.")
	@ConfigEditorAccordion(id = 3)
	public boolean section3 = false;

	@Expose
	@ConfigAccordionId(id = 3)
	@ConfigOption(name = "Frog titles", desc = "A title for every Gold or Diamond Trophy Frog, and for each frog new to your collection.")
	@ConfigEditorBoolean
	public boolean frogTitles = true;

	@Expose
	@ConfigAccordionId(id = 3)
	@ConfigOption(name = "Rare sea creatures", desc = "A title when a Frog Prince or a Puddle Jumper shows up.")
	@ConfigEditorBoolean
	public boolean rareAlert = true;

	@Expose
	@ConfigAccordionId(id = 3)
	@ConfigOption(name = "Blessing ending", desc = "A title a minute before your Frogcoin blessing ends.")
	@ConfigEditorBoolean
	public boolean blessingAlert = true;

	@Expose
	@ConfigAccordionId(id = 3)
	@ConfigOption(name = "Tell your party", desc = "Say Gold and Diamond frogs, Frog Princes and Puddle Jumpers in party chat.")
	@ConfigEditorBoolean
	public boolean party = false;

	@Expose
	@ConfigAccordionId(id = 3)
	@ConfigOption(name = "Sound", desc = "Played with the titles.")
	@ConfigEditorDropdown
	public Alert.Sound sound = Alert.Sound.PLING;

	@Expose
	public HudPos trackerHud = new HudPos(0.01, 0.20);

	@Expose
	public HudPos guideHud = new HudPos(0.62, 0.20);

	public static LotusConfig get() {
		return Taz.config().lotus;
	}
}
