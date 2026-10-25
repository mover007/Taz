package com.taz.xpassist.xp;

import com.taz.xpassist.data.NeuRepo;
import com.taz.xpassist.data.Rules;
import com.taz.xpassist.npc.Npc;
import com.taz.xpassist.util.TextUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns "the cheapest upgrades for your next N levels" into a route: one stop per NPC (grouped by
 * island so you buy everything while you are there), plus the Bazaar / Auction House, then what to
 * do with the items (donate at the museum, add to the accessory bag, ...).
 */
public final class TripPlanner {
	public record Stop(String key, String title, String where, String npcId, String island, String note,
					   List<XpTask> tasks, double cost, double xp, Map<String, Double> materials) {
	}

	public record Plan(int levels, int fromLevel, int toLevel, boolean progressKnown, double cost, double xp,
					   List<Stop> stops, List<String> finishSteps) {
	}

	private TripPlanner() {
	}

	public static Plan build(XpResult result, NeuRepo neu, Rules rules, int levels) {
		if (result == null || neu == null) {
			return null;
		}
		int perLevel = rules != null ? rules.xpPerLevel() : 100;
		int fromLevel = result.currentLevel(perLevel);
		double xp = result.progressKnown() ? result.startXp() : 0;
		double targetXp = result.progressKnown() ? (fromLevel + levels) * (double) perLevel : levels * (double) perLevel;

		List<XpTask> chosen = new ArrayList<>();
		double cost = 0;
		double gained = 0;
		for (XpResult.Entry entry : result.entries()) {
			if (!(entry instanceof XpResult.TaskEntry taskEntry)) {
				continue;
			}
			// Free things you're carrying are always worth doing, whatever the target.
			if (xp >= targetXp && !taskEntry.task().owned) {
				break;
			}
			XpTask task = taskEntry.task();
			chosen.add(task);
			xp += task.xp;
			gained += task.xp;
			cost += task.cost;
		}

		Map<String, List<XpTask>> byStop = new LinkedHashMap<>();
		for (XpTask task : chosen) {
			byStop.computeIfAbsent(task.stop == null || task.stop.isEmpty() ? "OTHER" : task.stop, k -> new ArrayList<>()).add(task);
		}
		List<Stop> stops = new ArrayList<>();
		for (Map.Entry<String, List<XpTask>> entry : byStop.entrySet()) {
			stops.add(stop(entry.getKey(), entry.getValue(), neu));
		}
		stops.sort(Comparator.comparingInt(TripPlanner::stopOrder)
			.thenComparing(stop -> stop.island() == null ? "" : neu.islandName(stop.island()))
			.thenComparing(Comparator.comparingDouble(Stop::xp).reversed()));

		int toLevel = result.progressKnown() ? (int) Math.floor(xp / perLevel) : (int) Math.floor(gained / perLevel);
		return new Plan(levels, fromLevel, toLevel, result.progressKnown(), cost, gained, List.copyOf(stops), finishSteps(chosen));
	}

	private static int stopOrder(Stop stop) {
		return switch (stop.key()) {
			case "OWNED" -> 0;
			case "BZ" -> 2;
			case "AH" -> 3;
			case "CRAFT" -> 4;
			case "OTHER" -> 5;
			default -> 1;
		};
	}

	private static Stop stop(String key, List<XpTask> tasks, NeuRepo neu) {
		double cost = 0;
		double xp = 0;
		Map<String, Double> materials = new LinkedHashMap<>();
		for (XpTask task : tasks) {
			cost += task.cost;
			xp += task.xp;
			if (!task.owned) {
				task.materials.forEach((name, amount) -> materials.merge(name, amount, Double::sum));
			}
		}
		String title;
		String where;
		String npcId = null;
		String island = null;
		String note = null;
		if (key.startsWith("npc:")) {
			npcId = key.substring(4);
			Npc npc = neu.npcs.get(npcId);
			title = npc != null ? npc.name() : TextUtil.titleCase(npcId.replace("_NPC", ""));
			if (npc != null && npc.hasLocation()) {
				island = npc.island();
				NeuRepo.Warp warp = neu.bestWarp(npc.island(), npc.x(), npc.y(), npc.z());
				where = neu.islandName(npc.island()) + (warp != null ? ", /warp " + warp.name() : "");
				if (!npc.lore().isEmpty()) {
					note = String.join(" ", npc.lore());
				}
			} else {
				where = "location unknown";
			}
		} else {
			switch (key) {
				case "OWNED" -> {
					title = "Already in your inventory";
					where = "free";
				}
				case "BZ" -> {
					title = "Bazaar";
					where = "/bz with a Booster Cookie, or the Hub";
				}
				case "AH" -> {
					title = "Auction House";
					where = "/ah with a Booster Cookie, or the Hub";
				}
				case "CRAFT" -> {
					title = "Craft";
					where = "ingredients from the Bazaar, craft anywhere";
				}
				default -> {
					title = "Other";
					where = "";
				}
			}
		}
		return new Stop(key, title, where, npcId, island, note, List.copyOf(tasks), cost, xp, materials);
	}

	/** What to do with what you bought, e.g. "Donate 12 items at the Museum (/warp museum): +18 XP". */
	private static List<String> finishSteps(List<XpTask> tasks) {
		Map<String, int[]> counts = new LinkedHashMap<>();
		Map<String, Double> xp = new LinkedHashMap<>();
		for (XpTask task : tasks) {
			String finish = task.finish == null ? "" : task.finish.startsWith("npc:") ? "CONTACT" : task.finish;
			counts.computeIfAbsent(finish, k -> new int[1])[0]++;
			xp.merge(finish, task.xp, Double::sum);
		}
		List<String> steps = new ArrayList<>();
		for (Map.Entry<String, int[]> entry : counts.entrySet()) {
			int n = entry.getValue()[0];
			String plural = n == 1 ? "" : "s";
			String text = switch (entry.getKey()) {
				case "MUSEUM" -> "Donate " + n + " item" + plural + " at the Museum (/warp museum)";
				case "CONTACT" -> "Add " + n + " Abiphone contact" + plural + " (talk to them with the item)";
				case "BAG" -> "Put " + n + " accessor" + (n == 1 ? "y" : "ies") + " in your Accessory Bag";
				case "PETS" -> "Add " + n + " pet" + plural + " to your Pets menu";
				case "CRAFT" -> "Craft " + n + " minion" + plural;
				case "ESSENCE" -> "Buy " + n + " essence perk tier" + plural + " in the Essence Shops";
				case "USE" -> "Use the " + n + " consumable" + plural;
				default -> null;
			};
			if (text != null) {
				steps.add(text + ": +" + TextUtil.xp(xp.get(entry.getKey())) + " XP");
			}
		}
		return steps;
	}

	/** Planned tasks you get from this NPC; falls back to the cheapest tasks in the list. */
	public static List<XpTask> tasksAtNpc(Plan plan, XpResult result, String npcId) {
		List<XpTask> out = new ArrayList<>();
		if (plan != null) {
			for (Stop stop : plan.stops()) {
				if (npcId.equals(stop.npcId())) {
					out.addAll(stop.tasks());
				}
			}
		}
		if (out.isEmpty() && result != null) {
			for (XpResult.Entry entry : result.entries()) {
				if (entry instanceof XpResult.TaskEntry taskEntry && ("npc:" + npcId).equals(taskEntry.task().stop)) {
					out.add(taskEntry.task());
					if (out.size() >= 8) {
						break;
					}
				}
			}
		}
		return out;
	}
}
