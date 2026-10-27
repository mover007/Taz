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
	@ConfigOption(name = "Glow", desc = "The critters you need glow through walls (red, gold if Sparkling).")
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
	@ConfigOption(name = "Show the way", desc = "A beam to the Safari Manager at the entrance, and to the Birdfeeder, gem pedestals or "
			+ "ritual room when you have the quest item for it.")
	@ConfigEditorBoolean
	public boolean showWay = true;

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
