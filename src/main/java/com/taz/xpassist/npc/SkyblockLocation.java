package com.taz.xpassist.npc;

import com.taz.xpassist.data.DataManager;
import com.taz.xpassist.data.NeuRepo;
import com.taz.xpassist.progress.LocalProgress;
import com.taz.xpassist.progress.ProgressDetector;
import com.taz.xpassist.util.TextUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Works out whether you are on SkyBlock and which island, from the sidebar and the tab list "Area:" line. */
public final class SkyblockLocation {
	private static final Pattern PROFILE = Pattern.compile("^Profile: ([A-Za-z]+)");
	private static final Pattern SB_LEVEL = Pattern.compile("^SB Level: \\[(\\d+)] (\\d+)");
	private static boolean onSkyblock;
	private static String area;
	private static String island;
	private static int ticks;

	private SkyblockLocation() {
	}

	public static boolean onSkyblock() {
		return onSkyblock;
	}

	/** The area name as Hypixel shows it, e.g. "Hub", or null. */
	public static String area() {
		return area;
	}

	/** The island id used by the NEU repo, e.g. "hub" or "combat_3", or null when unknown. */
	public static String island() {
		return island;
	}

	public static void tick(Minecraft minecraft) {
		if (++ticks % 20 != 0) {
			return;
		}
		if (minecraft.level == null || minecraft.getConnection() == null) {
			onSkyblock = false;
			area = null;
			island = null;
			return;
		}
		Objective sidebar = minecraft.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
		onSkyblock = sidebar != null
			&& TextUtil.strip(sidebar.getDisplayName().getString()).toUpperCase(Locale.ROOT).replace(" ", "").contains("SKYBLOCK");

		String found = null;
		boolean dungeon = false;
		for (PlayerInfo info : minecraft.getConnection().getListedOnlinePlayers()) {
			Component name = info.getTabListDisplayName();
			if (name == null) {
				continue;
			}
			String text = TextUtil.strip(name.getString()).trim();
			if (found == null && (text.startsWith("Area:") || text.startsWith("Dungeon:"))) {
				dungeon = text.startsWith("Dungeon:");
				found = text.substring(text.indexOf(':') + 1).trim();
			} else if (onSkyblock) {
				Matcher profile = PROFILE.matcher(text);
				if (profile.find()) {
					String before = LocalProgress.activeProfile();
					LocalProgress.setActiveProfile(profile.group(1));
					if (!before.equalsIgnoreCase(profile.group(1))) {
						DataManager.recompute();
					}
				}
				Matcher level = SB_LEVEL.matcher(text);
				if (level.find()) {
					ProgressDetector.onTabLevel(Integer.parseInt(level.group(1)), Integer.parseInt(level.group(2)));
				}
			}
		}
		area = found;
		NeuRepo neu = DataManager.neu();
		if (found == null) {
			island = null;
		} else if (dungeon) {
			island = "dungeon";
		} else if (neu != null && DataManager.rules() != null) {
			island = neu.modeForArea(found, DataManager.rules());
		}
	}
}
