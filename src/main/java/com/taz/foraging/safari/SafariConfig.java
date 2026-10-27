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
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

import java.util.ArrayList;
import java.util.List;

/** Galatea > Critter Safari in the Taz settings GUI (/taz). */
public final class SafariConfig {
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
	@ConfigOption(name = "Show the way", desc = "A path along the Safari's walkways (SkyHanni's map) to what to do next: the Ritual, "
			+ "Gem Podiums or Birdfeeder when you carry the quest item for it, the spots of critters you still need in your biome, "
			+ "the next biome when yours is done, or the nearest floor drop. At the entrance, a beam to the Safari Manager.")
	@ConfigEditorBoolean
	public boolean showWay = true;

	@Expose
	@ConfigOption(name = "Floor drops", desc = "A green box and name on every floor drop (the string on the ground with a green "
			+ "sparkle), through walls, so you find them fast. They give quest items, Safari Essence and Hunting XP.")
	@ConfigEditorBoolean
	public boolean floorDrops = true;

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
