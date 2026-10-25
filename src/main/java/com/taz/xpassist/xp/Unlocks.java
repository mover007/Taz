package com.taz.xpassist.xp;

import com.taz.xpassist.data.CollectionIndex;
import com.taz.xpassist.data.NeuRepo;
import com.taz.xpassist.data.PlayerProfile;
import com.taz.xpassist.util.TextUtil;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks recipe requirements against your profile: collection tiers ("Requires: Mycelium I"),
 * slayer levels ("Requires: Zombie Slayer 6") and Crimson Isle reputation ("MAGE:500").
 * Anything we can't check counts as unlocked, so nothing is hidden by mistake.
 */
final class Unlocks {
	private static final Pattern COLLECTION = Pattern.compile("^(.+?) ([IVXLC]+)$");
	private static final Pattern SLAYER = Pattern.compile("^(Zombie|Spider|Wolf|Enderman|Blaze|Vampire) Slayer (\\d+)$", Pattern.CASE_INSENSITIVE);

	private final NeuRepo neu;
	private final PlayerProfile profile;
	private final CollectionIndex collections;

	Unlocks(NeuRepo neu, PlayerProfile profile, CollectionIndex collections) {
		this.neu = neu;
		this.profile = profile;
		this.collections = collections;
	}

	/** Why you can't craft this item yet, or null if you can (or we can't tell). */
	String craftBlocker(String itemId) {
		String text = neu.craftText.get(itemId);
		if (text == null || profile == null) {
			return null;
		}
		String requirements = text.replaceFirst("(?i)^requires:?\\s*", "").trim();
		for (String part : requirements.split("&")) {
			String requirement = part.trim();
			Matcher slayer = SLAYER.matcher(requirement);
			if (slayer.matches()) {
				if (profile.slayerLevels == null) {
					continue;
				}
				String boss = slayer.group(1).toLowerCase(Locale.ROOT);
				int need = Integer.parseInt(slayer.group(2));
				int have = profile.slayerLevels.getOrDefault(boss, 0);
				if (have < need) {
					return "Needs " + TextUtil.titleCase(boss) + " Slayer " + need + " (you have " + have + ")";
				}
				continue;
			}
			Matcher collection = COLLECTION.matcher(requirement);
			if (collection.matches() && collections != null && profile.collectionTiers != null) {
				String id = collections.idForName(collection.group(1));
				if (id == null) {
					continue;
				}
				int need = roman(collection.group(2));
				int have = profile.collectionTiers.getOrDefault(id, 0);
				if (have < need) {
					return "Needs " + collection.group(1) + " " + collection.group(2) + " collection"
						+ (have > 0 ? " (you have " + TextUtil.roman(have) + ")" : "");
				}
			}
		}
		return null;
	}

	/** Crimson Isle minions need faction reputation, e.g. "MAGE:500". */
	String reputationBlocker(String itemId) {
		String requirement = neu.reputationReq.get(itemId);
		if (requirement == null || profile == null || profile.reputation == null || !requirement.contains(":")) {
			return null;
		}
		String faction = requirement.substring(0, requirement.indexOf(':')).toUpperCase(Locale.ROOT);
		int need;
		try {
			need = Integer.parseInt(requirement.substring(requirement.indexOf(':') + 1).trim());
		} catch (NumberFormatException e) {
			return null;
		}
		int have = profile.reputation.getOrDefault(faction, 0);
		if (have >= need) {
			return null;
		}
		String name = faction.equals("MAGE") ? "Mage" : faction.equals("BARBARIAN") ? "Barbarian" : TextUtil.titleCase(faction);
		return "Needs " + need + " " + name + " reputation (you have " + have + ")";
	}

	String blocker(String itemId) {
		String craft = craftBlocker(itemId);
		return craft != null ? craft : reputationBlocker(itemId);
	}

	static int roman(String text) {
		Map<Character, Integer> values = Map.of('I', 1, 'V', 5, 'X', 10, 'L', 50, 'C', 100);
		int total = 0;
		for (int i = 0; i < text.length(); i++) {
			int value = values.getOrDefault(text.charAt(i), 0);
			int next = i + 1 < text.length() ? values.getOrDefault(text.charAt(i + 1), 0) : 0;
			total += value < next ? -value : value;
		}
		return total;
	}
}
