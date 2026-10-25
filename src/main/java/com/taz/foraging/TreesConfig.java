package com.taz.foraging;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.ChromaColour;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorColour;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** The "Galatea" category of the Taz settings GUI (/taz). */
public final class TreesConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "A line from your crosshair and a \"Next: Fig 23m\" label to the closest fully grown tree, "
			+ "on Galatea and Torrhus Canyon, so when one falls you can go straight to the next.")
	@ConfigEditorBoolean
	@SearchTag("fig mangrove helix foraging tree")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Fig trees", desc = "Point at grown Fig trees.")
	@ConfigEditorBoolean
	public boolean fig = true;

	@Expose
	@ConfigOption(name = "Mangrove trees", desc = "Point at grown Mangrove trees.")
	@ConfigEditorBoolean
	public boolean mangrove = true;

	@Expose
	@ConfigOption(name = "Helix trees", desc = "Point at grown Helix trees (Torrhus Canyon).")
	@ConfigEditorBoolean
	public boolean helix = true;

	@Expose
	@ConfigOption(name = "Search radius", desc = "How far around you (in blocks) trees are looked for. Smaller is lighter on your game.")
	@ConfigEditorSlider(minValue = 32, maxValue = 160, minStep = 16)
	public int radius = 96;

	@Expose
	@ConfigOption(name = "Fig colour", desc = "Pointer colour for Fig trees.")
	@ConfigEditorColour
	public ChromaColour figColour = ChromaColour.Companion.fromStaticRGB(0xFF, 0xC2, 0x3D, 255);

	@Expose
	@ConfigOption(name = "Mangrove colour", desc = "Pointer colour for Mangrove trees.")
	@ConfigEditorColour
	public ChromaColour mangroveColour = ChromaColour.Companion.fromStaticRGB(0x3D, 0xFF, 0x8B, 255);

	@Expose
	@ConfigOption(name = "Helix colour", desc = "Pointer colour for Helix trees.")
	@ConfigEditorColour
	public ChromaColour helixColour = ChromaColour.Companion.fromStaticRGB(0xFF, 0x6B, 0xD5, 255);

	@Expose
	@ConfigOption(name = "Skip protected trees", desc = "Never point at a tree the Tree Protection Agency fined you for (remembered for good), "
			+ "or at one Hypixel says is still regenerating.")
	@ConfigEditorBoolean
	public boolean skipProtected = true;

	@Expose
	@ConfigOption(name = "Protected tree alert", desc = "A big title when you get fined 1,000 coins for hitting a protected tree.")
	@ConfigEditorBoolean
	public boolean protectedAlert = true;

	@ConfigOption(name = "Forget protected trees", desc = "Point at every tree again (click twice).")
	@ConfigEditorButton(buttonText = "Forget")
	public transient Runnable forgetProtected = () -> {
		if (com.taz.util.TazChat.confirm("protectedtrees", "forget the protected trees")) {
			ProtectedTrees.forget();
			com.taz.util.TazChat.info("Protected trees forgotten.");
		}
	};

	@ConfigOption(name = "Tree status", desc = "Print in chat how many trees around you are grown, chopped and regrowing, where the next one is, "
			+ "and how long the tracker's updates take.")
	@ConfigEditorButton(buttonText = "Show")
	public transient Runnable status = TreeTracker::printStatus;

	public static TreesConfig get() {
		return Taz.config().galatea.treePointer;
	}

	public boolean enabled(TreeTracker.Kind kind) {
		return switch (kind) {
			case FIG -> fig;
			case MANGROVE -> mangrove;
			case HELIX -> helix;
		};
	}

	/** ARGB pointer colour for a tree type. */
	public int colour(TreeTracker.Kind kind) {
		ChromaColour c = switch (kind) {
			case FIG -> figColour;
			case MANGROVE -> mangroveColour;
			case HELIX -> helixColour;
		};
		return 0xFF000000 | (c.getEffectiveColourRGB() & 0xFFFFFF);
	}
}
