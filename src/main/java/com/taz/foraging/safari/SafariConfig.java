package com.taz.foraging.safari;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import com.taz.hud.HudPos;
import com.taz.hud.TazHud;
import com.taz.util.Alert;
import com.taz.util.TazChat;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

import java.util.ArrayList;
import java.util.List;

/** Galatea > Critter Safari in the Taz settings GUI (/taz). */
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

	@Expose
	@ConfigOption(name = "Highlight", desc = "Which critters glow: the ones nobody in your party has caught this run (each new one gives "
			+ "+5,000 Hunting XP), the ones you've never caught yourself, or all of them. Sparkling ones always glow.")
	@ConfigEditorDropdown
	public Highlight highlight = Highlight.RUN;

	@Expose
	@ConfigOption(name = "Boxes", desc = "A red box round each critter you need, through walls (gold if Sparkling).")
	@ConfigEditorBoolean
	public boolean boxes = true;

	@Expose
	@ConfigOption(name = "Glow", desc = "The critters you need also glow with an outline through walls.")
	@ConfigEditorBoolean
	public boolean glow = true;

	@Expose
	@ConfigOption(name = "Name tags", desc = "A red name tag over each critter you need, with how far away it is. \"new\" means you've "
			+ "never caught one.")
	@ConfigEditorBoolean
	public boolean nameTags = true;

	@Expose
	@ConfigOption(name = "Guide HUD", desc = "Critters left in your biome and how to find each, the other biomes, your capsules, "
			+ "the time left and what your quest items are for. At the entrance: what you need to get in.")
	@ConfigEditorBoolean
	public boolean guide = true;

	@Expose
	@ConfigOption(name = "Tips in chat", desc = "Taz explains each step in chat: how the Safari works, every biome, your hotspot, "
			+ "what each quest item you pick up is for, and where to go next.")
	@ConfigEditorBoolean
	public boolean tips = true;

	@Expose
	@ConfigOption(name = "Show the way", desc = "A path through the whole run, one biome at a time, along the Safari's walkways "
			+ "(SkyHanni's map): to critters you still need, then bee nests, walls, perches, beds, mounds and the Ritual, Gem Podiums "
			+ "or Birdfeeder when you have what they need, then round the biome for the ones that roam, and to floor drops when "
			+ "you need a quest item. At the entrance, a beam to the Safari Manager.")
	@ConfigEditorBoolean
	public boolean showWay = true;

	@Expose
	@ConfigOption(name = "Your biome", desc = "The biome the route does first. Auto: the one you're in, then your hotspot, then the one "
			+ "with most left. In a party, pick the one you took. When it's done (the Macaw doesn't count) the route moves on.")
	@ConfigEditorDropdown
	public Zone zone = Zone.AUTO;

	@Expose
	@ConfigOption(name = "Floor drops", desc = "A green box and name on every floor drop (the string on the ground with a green "
			+ "sparkle), through walls, so you find them fast. They give quest items, Safari Essence and Hunting XP.")
	@ConfigEditorBoolean
	public boolean floorDrops = true;

	@Expose
	@ConfigOption(name = "Floor drop range", desc = "Only floor drops this many blocks away or closer get a box and name. The route "
			+ "still knows about the rest.")
	@ConfigEditorSlider(minValue = 5, maxValue = 150, minStep = 5)
	public int floorDropRange = 30;

	@Expose
	@ConfigOption(name = "Rockmite mounds", desc = "An orange box on each small rock mound in the Cavern while you still need a "
			+ "Rockmite: punch them and one in four has a Rockmite.")
	@ConfigEditorBoolean
	public boolean mounds = true;

	@Expose
	@ConfigOption(name = "Alerts", desc = "A title for a Sparkling critter, a Macaw at the Birdfeeder, the Wumpa, the Doomspiral and Gemzies.")
	@ConfigEditorBoolean
	public boolean alerts = true;

	@Expose
	@ConfigOption(name = "Alert sound", desc = "")
	@ConfigEditorDropdown
	public Alert.Sound sound = Alert.Sound.PLING;

	@ConfigOption(name = "Guide HUD position", desc = "Drag it to move, scroll to resize.")
	@ConfigEditorButton(buttonText = "Edit")
	public transient Runnable editHud = TazHud::openEditor;

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

	public static SafariConfig get() {
		return Taz.config().galatea.safari;
	}
}
