package com.taz.util;

import net.minecraft.client.Minecraft;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Your Hypixel party as far as chat has told Taz: its leader and members, from /p list, joins, leaves, transfers and
 * disbands. Unknown until something says (the leader is then null).
 */
public final class Party {
	private static final String RANK = "(?:\\[[^]]+] )?";
	private static final Pattern LEADER = Pattern.compile("^Party Leader: " + RANK + "(\\w{1,16}) ●");
	private static final Pattern LIST = Pattern.compile("^Party (?:Leader|Moderators|Members): (.+)$");
	private static final Pattern JOINED_THEIRS = Pattern.compile("^You have joined " + RANK + "(\\w{1,16})'s party!$");
	private static final Pattern TRANSFERRED = Pattern.compile("^The party was transferred to " + RANK + "(\\w{1,16}) by .+$");
	private static final Pattern PROMOTED = Pattern.compile("^" + RANK + "\\w{1,16} has promoted " + RANK + "(\\w{1,16}) to Party Leader$");
	private static final Pattern JOINED = Pattern.compile("^" + RANK + "(\\w{1,16}) joined the party\\.$");
	private static final Pattern LEFT = Pattern.compile("^" + RANK + "(\\w{1,16}) (?:has left the party\\.|has been removed from the party\\.|was removed from your party because they disconnected\\.)$");
	private static final Pattern INVITED = Pattern.compile("^" + RANK + "(\\w{1,16}) invited " + RANK + "\\w{1,16} to the party! They have 60 seconds to accept\\.$");
	private static final Pattern GONE = Pattern.compile("^(?:You left the party\\.|The party was disbanded because all invites expired and the party was empty\\.|"
			+ RANK + "\\w{1,16} has disbanded the party!|You have been kicked from the party by .+|You are not currently in a party\\.|"
			+ "You are not in a party right now\\.)$");
	private static final Set<String> MEMBERS = new LinkedHashSet<>();
	private static String leader;
	/** Party chat has been seen since the party last ended, so there is one even if its members aren't known. */
	private static boolean chatSeen;

	private Party() {
	}

	private static String me() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player == null ? "" : mc.player.getName().getString();
	}

	public static void onChat(String line) {
		Matcher m;
		if (line.startsWith("Party > ")) chatSeen = true;
		if ((m = LEADER.matcher(line)).find()) leader = m.group(1);
		if (line.startsWith("Party Members (")) MEMBERS.clear();
		if ((m = LIST.matcher(line)).matches()) {
			for (String part : m.group(1).split("●")) {
				String name = part.trim().replaceAll("^\\[[^]]+] ", "");
				if (name.matches("\\w{1,16}")) MEMBERS.add(name);
			}
		} else if ((m = JOINED_THEIRS.matcher(line)).matches()) {
			leader = m.group(1);
			MEMBERS.add(leader);
			MEMBERS.add(me());
		} else if ((m = TRANSFERRED.matcher(line)).matches() || (m = PROMOTED.matcher(line)).matches()) {
			leader = m.group(1);
		} else if ((m = JOINED.matcher(line)).matches()) {
			if (leader == null) leader = me();
			MEMBERS.add(me());
			MEMBERS.add(m.group(1));
		} else if ((m = LEFT.matcher(line)).matches()) {
			MEMBERS.remove(m.group(1));
		} else if ((m = INVITED.matcher(line)).matches()) {
			if (leader == null && m.group(1).equals(me())) leader = me();
		} else if (GONE.matcher(line).matches()) {
			leader = null;
			MEMBERS.clear();
			chatSeen = false;
		}
	}

	/** You're in a party as far as chat has shown (members known, or party chat seen), so party messages won't bounce. */
	public static boolean inParty() {
		return MEMBERS.size() > 1 || chatSeen;
	}

	/** The leader's name, or null when Taz doesn't know. */
	public static String leader() {
		return leader;
	}

	/** You lead the party (or Taz doesn't know who does, so it might be you). */
	public static boolean maybeLeader() {
		return leader == null || leader.equalsIgnoreCase(me());
	}

	public static Set<String> members() {
		return MEMBERS;
	}

	/** A member whose name starts with this (for "!kick ab"), or the text itself. */
	public static String find(String partial) {
		for (String m : MEMBERS) if (m.regionMatches(true, 0, partial, 0, partial.length())) return m;
		return partial;
	}

	public static void reset() {
		leader = null;
		MEMBERS.clear();
		chatSeen = false;
	}
}
