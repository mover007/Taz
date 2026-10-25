package com.taz.xpassist.config;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import com.taz.xpassist.SkyblockXpAssist;
import com.taz.xpassist.XpActions;
import com.taz.hud.TazHud;
import com.taz.xpassist.npc.NpcTracker;
import com.taz.xpassist.screen.NpcFinderScreen;
import com.taz.xpassist.screen.PlanScreen;
import io.github.notenoughupdates.moulconfig.ChromaColour;
import io.github.notenoughupdates.moulconfig.annotations.Accordion;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorColour;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorKeybind;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorText;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The "SkyBlock XP" category of the Taz settings GUI (/taz). Saved inside config/taz/config.json;
 * progress, caches and menu dumps live in config/taz/xpassist/.
 */
public class ModConfig {
	public enum BazaarMode {
		INSTANT_BUY("Instant buy price"),
		BUY_ORDER("Top buy order");

		private final String label;

		BazaarMode(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	@Expose
	@ConfigOption(name = "Enabled", desc = "Turn every SkyBlock XP feature off: list, HUD, NPC tracker, progress reading and downloads.")
	@ConfigEditorBoolean
	public boolean enabled = false;

	@ConfigOption(name = "Cheapest XP list", desc = "Every SkyBlock XP upgrade you haven't done, sorted by coins per XP.")
	@ConfigEditorButton(buttonText = "Open")
	public transient Runnable openList = XpActions::openList;

	@ConfigOption(name = "Level plan", desc = "The cheapest route to your next levels, one stop per NPC.")
	@ConfigEditorButton(buttonText = "Open")
	public transient Runnable openPlan = () -> SkyblockXpAssist.openScreen(() -> new PlanScreen(null));

	@ConfigOption(name = "NPC finder", desc = "Search any NPC, or an item to find who sells it, and track it through walls.")
	@ConfigEditorButton(buttonText = "Open")
	public transient Runnable openNpcFinder = () -> SkyblockXpAssist.openScreen(() -> new NpcFinderScreen(null));

	@ConfigOption(name = "Next upgrades in chat", desc = "Print the next cheapest upgrades in chat, with [Track] and buy buttons.")
	@ConfigEditorButton(buttonText = "Print")
	public transient Runnable printNext = () -> XpActions.next(this.hud.hudRows);

	@ConfigOption(name = "Stop tracking", desc = "Stop highlighting the tracked NPC.")
	@ConfigEditorButton(buttonText = "Stop")
	public transient Runnable untrack = NpcTracker::untrack;

	@Expose
	@ConfigOption(name = "Keybinds", desc = "Keys that work in game (not while a menu is open).")
	@Accordion
	public Keys keys = new Keys();

	@Expose
	@ConfigOption(name = "Profile", desc = "Where your progress (museum, accessories, pets, minions, XP) comes from.")
	@Accordion
	public Profile profile = new Profile();

	@Expose
	@ConfigOption(name = "HUD", desc = "Your level, the next cheapest upgrades and shopping hints.")
	@Accordion
	public Hud hud = new Hud();

	@Expose
	@ConfigOption(name = "XP List", desc = "How upgrades are priced and listed.")
	@Accordion
	public XpList xpList = new XpList();

	@Expose
	@ConfigOption(name = "NPC Tracker", desc = "How a tracked NPC is shown in the world.")
	@Accordion
	public Tracker tracker = new Tracker();

	@ConfigOption(name = "Progress", desc = "Refresh, sync help, hidden upgrades and debug tools.")
	@Accordion
	public transient Progress progress = new Progress();

	@Expose
	@ConfigOption(name = "Data Sources", desc = "Where prices, items and NPCs are downloaded from, and how often.")
	@Accordion
	public DataSources dataSources = new DataSources();

	// Set by the XP list (right-click to hide, category toggles); not shown in the settings GUI.
	@Expose
	public Set<String> disabledCategories = new LinkedHashSet<>();
	@Expose
	public Set<String> hiddenTasks = new LinkedHashSet<>();

	public static final class Progress {
		@ConfigOption(name = "Refresh data", desc = "Re-download prices, your profile and the item repo now. Happens by itself when you change the API key or profile.")
		@ConfigEditorButton(buttonText = "Refresh")
		public transient Runnable refresh = XpActions::refresh;

		@ConfigOption(name = "How to sync", desc = "Print which in-game menus to open once to catch up on progress from before you installed Taz.")
		@ConfigEditorButton(buttonText = "Show")
		public transient Runnable sync = XpActions::sync;

		@ConfigOption(name = "Show hidden upgrades", desc = "Unhide everything you right-clicked away in the list.")
		@ConfigEditorButton(buttonText = "Unhide")
		public transient Runnable unhide = XpActions::unhideAll;

		@ConfigOption(name = "Save next menu", desc = "Write the next menu you open to config/taz/xpassist/dumps (useful if Hypixel changes a menu and syncing breaks).")
		@ConfigEditorButton(buttonText = "Arm")
		public transient Runnable dump = XpActions::dumpNextMenu;

		@ConfigOption(name = "Forget seen progress", desc = "§cClear the progress Taz saw in game for this profile.§7 Click twice to confirm.")
		@ConfigEditorButton(buttonText = "Clear")
		public transient Runnable forget = XpActions::forgetProgress;
	}

	public static final class DataSources {
		@Expose
		@ConfigOption(name = "Profile refresh (minutes)", desc = "How often your profile is re-downloaded while you play SkyBlock.")
		@ConfigEditorSlider(minValue = 1, maxValue = 60, minStep = 1)
		public int profileRefreshMinutes = 10;

		@Expose
		@ConfigOption(name = "Price refresh (minutes)", desc = "How often Bazaar and Auction House prices are re-downloaded.")
		@ConfigEditorSlider(minValue = 5, maxValue = 120, minStep = 5)
		public int priceRefreshMinutes = 30;

		@Expose
		@ConfigOption(name = "Repo check (hours)", desc = "How often the NEU item repo is checked for updates.")
		@ConfigEditorSlider(minValue = 1, maxValue = 48, minStep = 1)
		public int repoCheckHours = 6;

		@Expose
		@ConfigOption(name = "Hypixel API", desc = "Base URL of the Hypixel API.")
		@ConfigEditorText
		public String hypixelApiBase = "https://api.hypixel.net/v2";

		@Expose
		@ConfigOption(name = "Prices URL", desc = "Lowest BIN / Bazaar price list (SkyHelper's by default).")
		@ConfigEditorText
		public String pricesUrl = "https://raw.githubusercontent.com/SkyHelperBot/Prices/main/pricesV2.json";

		@Expose
		@ConfigOption(name = "NEU repo owner", desc = "GitHub owner of the item repo.")
		@ConfigEditorText
		public String neuRepoOwner = "NotEnoughUpdates";

		@Expose
		@ConfigOption(name = "NEU repo name", desc = "GitHub name of the item repo.")
		@ConfigEditorText
		public String neuRepoName = "NotEnoughUpdates-REPO";

		@Expose
		@ConfigOption(name = "NEU repo branch", desc = "Branch of the item repo.")
		@ConfigEditorText
		public String neuRepoBranch = "master";

		@Expose
		@ConfigOption(name = "Rules URL", desc = "Optional URL to a rules.json that replaces the bundled one (same format).")
		@ConfigEditorText
		public String rulesUrl = "";
	}

	public static final class Keys {
		@Expose
		@ConfigOption(name = "Open XP list", desc = "Opens the cheapest XP list.")
		@ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_K)
		public int openList = GLFW.GLFW_KEY_K;

		@Expose
		@ConfigOption(name = "Open level plan", desc = "Opens the level plan.")
		@ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
		public int plan = GLFW.GLFW_KEY_UNKNOWN;

		@Expose
		@ConfigOption(name = "Open NPC finder", desc = "Opens the NPC finder.")
		@ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
		public int npcFinder = GLFW.GLFW_KEY_UNKNOWN;

		@Expose
		@ConfigOption(name = "Toggle XP HUD", desc = "Shows or hides the XP HUD.")
		@ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
		public int toggleHud = GLFW.GLFW_KEY_UNKNOWN;

		@Expose
		@ConfigOption(name = "Stop tracking", desc = "Stops highlighting the tracked NPC.")
		@ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
		public int untrack = GLFW.GLFW_KEY_UNKNOWN;
	}

	public static final class Profile {
		@Expose
		@ConfigOption(name = "Hypixel API key", desc = "From developer.hypixel.net. Needed to read your profile progress from Hypixel.")
		@ConfigEditorText
		public String hypixelApiKey = "";

		@Expose
		@ConfigOption(name = "Profile name", desc = "Leave empty to use the profile you have selected in game.")
		@ConfigEditorText
		public String profileName = "";

		@Expose
		@ConfigOption(name = "Use EliteBot", desc = "Without an API key, load your progress from EliteBot, a public SkyBlock stats service that mirrors the Hypixel API. Off = only what the mod sees in game.")
		@ConfigEditorBoolean
		public boolean useEliteBot = true;

		@Expose
		@ConfigOption(name = "EliteBot API", desc = "Base URL of the EliteBot API.")
		@ConfigEditorText
		public String eliteApiBase = "https://api.elitebot.dev";
	}

	public static final class Hud {
		@Expose
		@ConfigOption(name = "Enabled", desc = "Show the XP HUD.")
		@ConfigEditorBoolean
		public boolean hudEnabled = true;

		@Expose
		@ConfigOption(name = "Only on SkyBlock", desc = "Hide the HUD outside SkyBlock.")
		@ConfigEditorBoolean
		public boolean hudOnlyOnSkyblock = true;

		@Expose
		@ConfigOption(name = "Rows", desc = "How many of the next cheapest upgrades to list.")
		@ConfigEditorSlider(minValue = 1, maxValue = 15, minStep = 1)
		public int hudRows = 5;

		@Expose
		@ConfigOption(name = "Plan levels", desc = "How many levels ahead the Level Plan and the HUD shopping hints look.")
		@ConfigEditorSlider(minValue = 1, maxValue = 30, minStep = 1)
		public int planLevels = 5;

		@Expose
		@ConfigOption(name = "Shopping hints", desc = "Show what to buy when you are near an NPC from your plan.")
		@ConfigEditorBoolean
		public boolean hudShoppingHints = true;

		@ConfigOption(name = "Move HUD", desc = "Drag the HUD and scroll to resize it.")
		@ConfigEditorButton(buttonText = "Edit")
		public transient Runnable moveHud = TazHud::openEditor;

		@Expose
		public int hudX = 4;
		@Expose
		public int hudY = 4;
		@Expose
		public float hudScale = 1.0F;
	}

	public static final class XpList {
		@Expose
		@ConfigOption(name = "Show unpriced", desc = "Also list upgrades that need something you can't buy.")
		@ConfigEditorBoolean
		public boolean showUnpriced = false;

		@Expose
		@ConfigOption(name = "Bazaar price", desc = "Price Bazaar items at the insta-buy price or the top buy order.")
		@ConfigEditorDropdown
		public BazaarMode bazaarMode = BazaarMode.INSTANT_BUY;

		@Expose
		@ConfigOption(name = "Use NPC shops", desc = "Count buying from NPC shops when it's the cheapest way.")
		@ConfigEditorBoolean
		public boolean useNpcShops = true;

		@Expose
		@ConfigOption(name = "Use crafting", desc = "Count crafting (unlocked recipes only) when it's the cheapest way.")
		@ConfigEditorBoolean
		public boolean useCraftCosts = true;
	}

	public static final class Tracker {
		@Expose
		@ConfigOption(name = "Glow", desc = "The tracked NPC glows through walls.")
		@ConfigEditorBoolean
		public boolean trackerGlow = true;

		@Expose
		@ConfigOption(name = "Box and beam", desc = "A box, a beam into the sky and a name label, visible through walls.")
		@ConfigEditorBoolean
		public boolean trackerBeam = true;

		@Expose
		@ConfigOption(name = "Tracer line", desc = "A line from your crosshair to the NPC.")
		@ConfigEditorBoolean
		public boolean trackerTracer = false;

		@Expose
		@ConfigOption(name = "Colour", desc = "Colour of the glow, box, beam and label.")
		@ConfigEditorColour
		public ChromaColour trackerColor = ChromaColour.Companion.fromStaticRGB(0x55, 0xFF, 0xFF, 255);

		/** The tracker colour as 0xRRGGBB. */
		public int rgb() {
			return trackerColor.getEffectiveColourRGB() & 0xFFFFFF;
		}
	}

	public static ModConfig get() {
		return Taz.config().skyblockXp.xpAssist;
	}

	public static Path dir() {
		return Taz.CONFIG_DIR.resolve("xpassist");
	}

	public static void save() {
		Taz.save();
	}

	public boolean hasApiKey() {
		return profile.hypixelApiKey != null && !profile.hypixelApiKey.isBlank();
	}
}
