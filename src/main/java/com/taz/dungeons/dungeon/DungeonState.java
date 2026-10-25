package com.taz.dungeons.dungeon;

import com.taz.dungeons.util.TextUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Live dungeon info read from the sidebar and the tab list. Polled a few times a second. */
public final class DungeonState {
	// Sidebar (ASCII-cleaned)
	private static final Pattern FLOOR = Pattern.compile("The Catacombs \\((E|F[1-7]|M[1-7])\\)");
	private static final Pattern TIME_ELAPSED = Pattern.compile("Time Elapsed: ?(?:(\\d+)h ?)?(?:(\\d+)m ?)?(\\d+)s");
	/** "Cleared: 77% (164)": the number in brackets is Hypixel's own live score. */
	private static final Pattern CLEARED = Pattern.compile("Cleared: ?(\\d+)%(?: ?\\((\\d+)\\))?");

	// Tab list
	private static final Pattern PARTY = Pattern.compile("^Party \\((\\d+)\\)");
	private static final Pattern PARTY_MEMBER = Pattern.compile("([A-Za-z0-9_]{3,16}) ?\\((?:Archer|Berserk|Healer|Mage|Tank|DEAD)\\b");
	private static final Pattern COMPLETED_ROOMS = Pattern.compile("Completed Rooms: ?(\\d+)");
	private static final Pattern SECRETS_PCT = Pattern.compile("Secrets Found: ?([\\d.]+)%");
	private static final Pattern DEATHS = Pattern.compile("Deaths: ?\\(?(\\d+)\\)?");
	private static final Pattern CRYPTS = Pattern.compile("Crypts: ?(\\d+)");
	private static final Pattern PUZZLES = Pattern.compile("^Puzzles: ?\\((\\d+)\\)");
	/** "Water Board: [✔]", "???: [✦]", "Quiz: [✖]" */
	private static final Pattern PUZZLE_STATUS = Pattern.compile("^.+?: ?\\[([✔✖✦])]");

	public boolean inDungeon;
	public Floor floor;
	/** Seconds from the sidebar's "Time Elapsed", or -1 if the run hasn't started. */
	public int timeElapsedSec = -1;
	public int clearedPct = -1;
	/** Hypixel's live dungeon score from the sidebar, or -1. */
	public int liveScore = -1;
	public int partySize = -1;
	public final Set<String> partyMembers = new LinkedHashSet<>();
	public int completedRooms = -1;
	public double secretsPct = -1;
	public int deaths = -1;
	public int crypts = -1;
	public int puzzlesTotal = -1;
	public int puzzlesDone;
	public int puzzlesFailed;

	/** When true, the raw text of everything read is kept for the diagnostic dump. */
	public boolean captureRaw;
	public final List<String> rawSidebar = new ArrayList<>();
	public final List<String> rawTeams = new ArrayList<>();
	public final List<String> rawTab = new ArrayList<>();
	public String sidebarSource = "none";

	public void update(Minecraft mc) {
		reset();
		if (mc.level == null) return;
		Scoreboard scoreboard = mc.level.getScoreboard();
		readSidebar(mc, scoreboard);
		if (!inDungeon || timeElapsedSec < 0) readAllTeams(scoreboard);
		ClientPacketListener connection = mc.getConnection();
		if (connection != null) readTabList(connection);
	}

	private void reset() {
		inDungeon = false;
		floor = null;
		timeElapsedSec = clearedPct = liveScore = partySize = completedRooms = deaths = crypts = puzzlesTotal = -1;
		secretsPct = -1;
		puzzlesDone = puzzlesFailed = 0;
		partyMembers.clear();
		rawSidebar.clear();
		rawTeams.clear();
		rawTab.clear();
		sidebarSource = "none";
	}

	/** Same objective choice as the vanilla sidebar: the player's team-colour slot first, then SIDEBAR. */
	private void readSidebar(Minecraft mc, Scoreboard scoreboard) {
		Objective objective = null;
		if (mc.player != null) {
			PlayerTeam own = scoreboard.getPlayersTeam(mc.player.getScoreboardName());
			if (own != null) {
				DisplaySlot slot = DisplaySlot.teamColorToSlot(own.getColor());
				if (slot != null) {
					objective = scoreboard.getDisplayObjective(slot);
					if (objective != null) sidebarSource = "team slot " + slot;
				}
			}
		}
		if (objective == null) {
			objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
			if (objective != null) sidebarSource = "SIDEBAR";
		}
		if (objective == null) return;
		if (captureRaw) rawSidebar.add("[title] " + objective.getDisplayName().getString());

		for (PlayerScoreEntry entry : scoreboard.listPlayerScores(objective)) {
			if (entry.isHidden()) continue;
			PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
			String raw = PlayerTeam.formatNameForTeam(team, entry.ownerName()).getString();
			if (captureRaw) rawSidebar.add(raw);
			parseSidebarLine(TextUtil.cleanScoreboard(raw));
		}
	}

	/**
	 * Fallback: Hypixel draws sidebar lines as team prefix + suffix, so scanning every team
	 * finds them even if the objective lookup above came up empty.
	 */
	private void readAllTeams(Scoreboard scoreboard) {
		for (PlayerTeam team : scoreboard.getPlayerTeams()) {
			String raw = team.getPlayerPrefix().getString() + team.getPlayerSuffix().getString();
			if (raw.isEmpty()) continue;
			if (captureRaw) rawTeams.add(team.getName() + " | " + raw);
			boolean hadFloor = inDungeon;
			parseSidebarLine(TextUtil.cleanScoreboard(raw));
			if (!hadFloor && inDungeon) sidebarSource = "team scan";
		}
	}

	private void parseSidebarLine(String line) {
		if (line.isEmpty()) return;
		Matcher m = FLOOR.matcher(line);
		if (m.find()) {
			Floor f = Floor.fromId(m.group(1));
			if (f != null) {
				floor = f;
				inDungeon = true;
			}
			return;
		}
		m = TIME_ELAPSED.matcher(line);
		if (m.find()) {
			int h = m.group(1) == null ? 0 : Integer.parseInt(m.group(1));
			int min = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
			timeElapsedSec = h * 3600 + min * 60 + Integer.parseInt(m.group(3));
			return;
		}
		m = CLEARED.matcher(line);
		if (m.find()) {
			clearedPct = Integer.parseInt(m.group(1));
			if (m.group(2) != null) liveScore = Integer.parseInt(m.group(2));
		}
	}

	private void readTabList(ClientPacketListener connection) {
		for (PlayerInfo info : connection.getListedOnlinePlayers()) {
			Component display = info.getTabListDisplayName();
			if (display == null) continue;
			String raw = TextUtil.cleanChat(display.getString());
			if (raw.isEmpty()) continue;
			if (captureRaw) rawTab.add(raw);
			String line = TextUtil.cleanScoreboard(raw);

			Matcher m = PUZZLE_STATUS.matcher(raw);
			if (m.find()) {
				switch (m.group(1).charAt(0)) {
					case '✔' -> puzzlesDone++;
					case '✖' -> puzzlesFailed++;
					default -> {
					}
				}
				continue;
			}
			m = PARTY.matcher(line);
			if (m.find()) {
				partySize = Integer.parseInt(m.group(1));
				continue;
			}
			m = PARTY_MEMBER.matcher(line);
			if (m.find()) {
				partyMembers.add(m.group(1));
				continue;
			}
			m = COMPLETED_ROOMS.matcher(line);
			if (m.find()) {
				completedRooms = Integer.parseInt(m.group(1));
				continue;
			}
			m = SECRETS_PCT.matcher(line);
			if (m.find()) {
				try {
					secretsPct = Double.parseDouble(m.group(1));
				} catch (NumberFormatException ignored) {
				}
				continue;
			}
			m = PUZZLES.matcher(line);
			if (m.find()) {
				puzzlesTotal = Integer.parseInt(m.group(1));
				continue;
			}
			m = DEATHS.matcher(line);
			if (m.find()) {
				deaths = Integer.parseInt(m.group(1));
				continue;
			}
			m = CRYPTS.matcher(line);
			if (m.find()) crypts = Integer.parseInt(m.group(1));
		}
	}
}
