package com.taz.foraging.safari;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import com.taz.hud.HudPos;
import com.taz.hud.TazHud;
import com.taz.util.Alert;
import com.taz.util.TazChat;
import io.github.notenoughupdates.moulconfig.annotations.ConfigAccordionId;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorAccordion;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorInfoText;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

import java.util.ArrayList;
import java.util.List;

/** The "Critter Safari" category of the Taz settings GUI (/taz). */
public final class SafariConfig {
	/** The biome the route works first. */
	public enum Zone {
		AUTO("Auto"), FOREST("Forest"), CAVERN("Cavern"), HAUNTED("Haunted"), ICY("Icy");

		private final String label;

		Zone(String label) {
			this.label = label;
		}

		/** The biome, or null for Auto. */
		public Critters.Biome biome() {
			return this == AUTO ? null : Critters.Biome.valueOf(name());
		}

		@Override
		public String toString() {
			return label;
		}
	}

	public enum Highlight {
		RUN("Not caught this run"), MINE("Never caught by me"), ALL("Every critter");

		private final String label;

		Highlight(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	@Expose
	@ConfigOption(name = "Enabled", desc = "Your guide to the Critter Safari in Torrhus Canyon. The critters you still need glow red with "
			+ "a name tag, a HUD lists what's left in your biome and how to find each one, and Taz tells you what to do at every step: "
			+ "getting in, each biome, and what every quest item is for.")
	@ConfigEditorBoolean
	@SearchTag("critter safari hunting critters capsule highlight glow critterdex shard biome forest cavern haunted icy macaw wumpa doomspiral sparkling")
	public boolean enabled = false;

	@ConfigOption(name = "Highlights", desc = "Which critters glow, and how they're marked.")
	@ConfigEditorAccordion(id = 0)
	public boolean section0 = false;

	@Expose
	@ConfigAccordionId(id = 0)
	@ConfigOption(name = "Highlight", desc = "Which critters glow: the ones nobody in your party has caught this run (each new one gives "
			+ "+5,000 Hunting XP), the ones you've never caught yourself, or all of them. Sparkling ones always glow.")
	@ConfigEditorDropdown
	public Highlight highlight = Highlight.RUN;

	@Expose
	@ConfigAccordionId(id = 0)
	@ConfigOption(name = "Boxes", desc = "A red box round each critter you need, through walls (gold if Sparkling).")
	@ConfigEditorBoolean
	public boolean boxes = true;

	@Expose
	@ConfigAccordionId(id = 0)
	@ConfigOption(name = "Glow", desc = "The critters you need also glow with an outline through walls.")
	@ConfigEditorBoolean
	public boolean glow = true;

	@Expose
	@ConfigAccordionId(id = 0)
	@ConfigOption(name = "Name tags", desc = "A red name tag over each critter you need, with how far away it is. \"new\" means you've "
			+ "never caught one.")
	@ConfigEditorBoolean
	public boolean nameTags = true;

	@Expose
	@ConfigAccordionId(id = 0)
	@ConfigOption(name = "Last seen", desc = "When a critter you need goes out of view, a faint box stays where it was last seen (for "
			+ "90 seconds, or until you get there), and the route can take you back to it.")
	@ConfigEditorBoolean
	public boolean lastSeen = true;

	@ConfigOption(name = "Capsule throws", desc = "Where a Critter Capsule you throw will go.")
	@ConfigEditorAccordion(id = 6)
	public boolean section6 = false;

	@Expose
	@ConfigAccordionId(id = 6)
	@ConfigOption(name = "Throw preview", desc = "While you hold a Critter Capsule, the arc a throw would fly and where it lands; green "
			+ "when it would hit a critter you need. Taz measures your throws to learn Hypixel's arc, so it gets exact after the "
			+ "first one. It only shows the way; aiming and throwing stay yours. Landing previews are a grey area in Hypixel's "
			+ "rules, so it's off unless you turn it on.")
	@ConfigEditorBoolean
	public boolean throwPreview = false;

	@ConfigOption(name = "Route", desc = "The path through the run, biome by biome.")
	@ConfigEditorAccordion(id = 1)
	public boolean section1 = false;

	@Expose
	@ConfigAccordionId(id = 1)
	@ConfigOption(name = "Show the way", desc = "A path through the whole run, one biome at a time, along the Safari's walkways "
			+ "(SkyHanni's map): to critters you still need, then bee nests, walls, perches, beds, mounds and the Ritual, Gem Podiums "
			+ "or Birdfeeder when you have what they need, then round the biome for the ones that roam, and to floor drops when "
			+ "you need a quest item. At the entrance, a beam to the Safari Manager.")
	@ConfigEditorBoolean
	public boolean showWay = true;

	@Expose
	@ConfigAccordionId(id = 1)
	@ConfigOption(name = "Your biome", desc = "The biome the route does first. Auto: your Hunting Hotspot (everyone in a party gets a "
			+ "different one), then the one you're in, then the one with most left. In a party that agreed on other biomes, pick "
			+ "yours. When it's done (the Macaw doesn't count) the route moves on.")
	@ConfigEditorDropdown
	public Zone zone = Zone.AUTO;

	@Expose
	@ConfigAccordionId(id = 1)
	@ConfigOption(name = "Full clear", desc = "For parties farming Hunting XP: you stay in your Hunting Hotspot (or Your biome) and "
			+ "catch every critter in it, ones caught before too. Every critter there glows, the route takes you to each one and "
			+ "round the biome again and again, and the HUD counts your catches there. Your party clears the other biomes.")
	@ConfigEditorBoolean
	public boolean fullClear = false;

	@ConfigOption(name = "Floor drops & mounds", desc = "Boxes on floor drops and Rockmite mounds.")
	@ConfigEditorAccordion(id = 2)
	public boolean section2 = false;

	@Expose
	@ConfigAccordionId(id = 2)
	@ConfigOption(name = "Floor drops", desc = "A green box and name on every floor drop (the string on the ground with a green "
			+ "sparkle), through walls, so you find them fast. They give quest items, Safari Essence and Hunting XP.")
	@ConfigEditorBoolean
	public boolean floorDrops = true;

	@Expose
	@ConfigAccordionId(id = 2)
	@ConfigOption(name = "Floor drop range", desc = "Only floor drops this many blocks away or closer get a box and name. The route "
			+ "still knows about the rest.")
	@ConfigEditorSlider(minValue = 5, maxValue = 150, minStep = 5)
	public int floorDropRange = 30;

	@Expose
	@ConfigAccordionId(id = 2)
	@ConfigOption(name = "Rockmite mounds", desc = "An orange box on each small rock mound in the Cavern while you still need a "
			+ "Rockmite: punch them and one in four has a Rockmite.")
	@ConfigEditorBoolean
	public boolean mounds = true;

	@ConfigOption(name = "Guide & tips", desc = "The guide HUD and the tips in chat.")
	@ConfigEditorAccordion(id = 3)
	public boolean section3 = false;

	@Expose
	@ConfigAccordionId(id = 3)
	@ConfigOption(name = "Guide HUD", desc = "Critters left in your biome and how to find each, the other biomes, your capsules, "
			+ "the time left and what your quest items are for. At the entrance: what you need to get in.")
	@ConfigEditorBoolean
	public boolean guide = true;

	@ConfigAccordionId(id = 3)
	@ConfigOption(name = "Guide HUD position", desc = "Drag it to move, scroll to resize.")
	@ConfigEditorButton(buttonText = "Edit")
	public transient Runnable editHud = TazHud::openEditor;

	@Expose
	@ConfigAccordionId(id = 3)
	@ConfigOption(name = "Tips in chat", desc = "Taz explains each step in chat: how the Safari works, every biome, your hotspot, "
			+ "what each quest item you pick up is for, and where to go next.")
	@ConfigEditorBoolean
	public boolean tips = true;

	@Expose
	@ConfigAccordionId(id = 3)
	@ConfigOption(name = "Countdowns", desc = "Timers on the guide HUD while something's going on: a Shyworm up (about 20 seconds), the "
			+ "Wumpa waking, Hideyho hiding (the faster you find him, the more shards), a Scrappy out of its shell and the Doomspiral.")
	@ConfigEditorBoolean
	public boolean countdowns = true;

	@Expose
	@ConfigAccordionId(id = 3)
	@ConfigOption(name = "Run report", desc = "When you leave, a report in chat: what you and your party caught, the rewards, your best "
			+ "run, and what you missed in each biome and why (the quest items it needed, seen but not caught, or never found).")
	@ConfigEditorBoolean
	public boolean report = true;

	@ConfigOption(name = "Alerts", desc = "Titles for the moments you shouldn't miss.")
	@ConfigEditorAccordion(id = 4)
	public boolean section4 = false;

	@Expose
	@ConfigAccordionId(id = 4)
	@ConfigOption(name = "Alerts", desc = "A title for a Sparkling critter, a Macaw at the Birdfeeder, the Wumpa, the Doomspiral and Gemzies.")
	@ConfigEditorBoolean
	public boolean alerts = true;

	@Expose
	@ConfigAccordionId(id = 4)
	@ConfigOption(name = "Alert sound", desc = "")
	@ConfigEditorDropdown
	public Alert.Sound sound = Alert.Sound.PLING;

	@Expose
	@ConfigAccordionId(id = 4)
	@ConfigOption(name = "Sparkling callouts", desc = "Say in party chat when you find a Sparkling critter, with its biome and where it "
			+ "is, so a teammate can come for it. Only while you're in a party.")
	@ConfigEditorBoolean
	public boolean callouts = false;

	@ConfigOption(name = "Your catches", desc = "The critters Taz remembers you catching.")
	@ConfigEditorAccordion(id = 5)
	public boolean section5 = false;

	@ConfigAccordionId(id = 5)
	@ConfigOption(name = "Critterdex sync", desc = "Open your Critterdex and Taz counts every critter it shows as caught, so \"never "
			+ "caught by me\" and the \"new\" tags match your real collection.")
	@ConfigEditorInfoText
	public transient String critterdex = "";

	@ConfigAccordionId(id = 5)
	@ConfigOption(name = "Forget my catches", desc = "Count every critter as never caught (click twice).")
	@ConfigEditorButton(buttonText = "Forget")
	public transient Runnable forget = () -> {
		if (TazChat.confirm("safari", "forget every critter you've caught")) SafariHelper.forgetCaught();
	};

	@Expose
	public HudPos hud = new HudPos(0.01, 0.30);

	/** Every critter you've caught yourself since Taz started counting (your own CAPTURE! lines). */
	@Expose
	public List<String> caught = new ArrayList<>();

	/** The most critters caught in one run (you and your party), for the run report. */
	@Expose
	public int bestCaught = 0;

	/** A capsule's flight as measured from your throws (blocks a tick, drag a tick, gravity a tick); 0 throws: not yet. */
	@Expose
	public double throwSpeed, throwDrag, throwGravity;
	@Expose
	public int throwsMeasured;

	public static SafariConfig get() {
		return Taz.config().safari;
	}
}
