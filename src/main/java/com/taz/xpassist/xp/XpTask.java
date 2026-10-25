package com.taz.xpassist.xp;

import java.util.ArrayList;
import java.util.List;

/** One thing you can buy or do for SkyBlock XP, e.g. "Museum - Donate Decent Bow". */
public final class XpTask {
	public final String id;
	public final XpCategory category;
	public final String label;
	public final double xp;
	/** Coins needed. 0 means free, NaN means we could not price it. */
	public double cost;
	public boolean partialPrice;
	public String costSource = "";
	/** NPC to visit for this task (the shop that sells the item, or the Abiphone contact). */
	public String npcId;
	/** Task that has to be done first (e.g. the previous minion tier). */
	public String prerequisite;
	public final List<String> details = new ArrayList<>();
	/** Where you get it: "npc:<ID>", "AH", "BZ", "CRAFT", "OWNED" or "ESSENCE". Used to group shopping trips. */
	public String stop = "";
	/** Where you finish it: "MUSEUM", "npc:<ID>" (Abiphone contact), "BAG", "PETS", "USE", "CRAFT" or "ESSENCE". */
	public String finish = "";
	/** You already carry the item, so this upgrade is free. */
	public boolean owned;
	/** Why you can't do this yet (e.g. "Needs Mycelium I collection"), or null. */
	public String lockedReason;
	/**
	 * Consecutive tasks with the same group (tiers of one essence perk, accessory bag upgrades) are shown
	 * as one line: groupLabel with "{}" replaced by "firstStep → lastStep".
	 */
	public String groupKey;
	public String groupLabel;
	public String groupStep;
	/** What you need to bring for this task, e.g. "Fossil Essence" -> 1500 (summed per stop in the plan). */
	public final java.util.Map<String, Double> materials = new java.util.LinkedHashMap<>();

	/** Things to buy for this task, for the [Bazaar] / [AH] / [Recipe] chat buttons. */
	public final List<Buy> buys = new ArrayList<>();

	/** kind is "bz", "ah" or "recipe"; query is the item name (or item id for recipes). */
	public record Buy(String kind, String query, String label) {
	}

	public void addBuy(String kind, String query, String label) {
		for (Buy buy : buys) {
			if (buy.kind().equals(kind) && buy.query().equals(query)) {
				return;
			}
		}
		buys.add(new Buy(kind, query, label));
	}

	public XpTask(String id, XpCategory category, String label, double xp, double cost) {
		this.id = id;
		this.category = category;
		this.label = label;
		this.xp = xp;
		this.cost = cost;
	}

	public boolean priced() {
		return !Double.isNaN(cost);
	}

	public double coinsPerXp() {
		return xp <= 0 ? Double.MAX_VALUE : cost / xp;
	}

	public String fullLabel() {
		return category.label + " - " + label;
	}
}
