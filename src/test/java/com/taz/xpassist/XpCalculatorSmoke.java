package com.taz.xpassist;

import com.taz.xpassist.config.ModConfig;
import com.taz.xpassist.data.HypixelItems;
import com.taz.xpassist.data.NeuRepo;
import com.taz.xpassist.data.PlayerProfile;
import com.taz.xpassist.data.Prices;
import com.taz.xpassist.data.Rules;
import com.taz.xpassist.npc.Npc;
import com.taz.xpassist.util.TextUtil;
import com.taz.xpassist.xp.XpCalculator;
import com.taz.xpassist.xp.XpResult;
import com.taz.xpassist.xp.XpTask;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;

/** Dev check: runs the XP list against real downloaded data. Args: dataDir neuZip */
public final class XpCalculatorSmoke {
	public static void main(String[] args) throws Exception {
		Path data = Path.of(args[0]);
		long start = System.currentTimeMillis();
		NeuRepo neu = NeuRepo.load(Path.of(args[1]), "test");
		System.out.println("NEU: " + neu.npcs.size() + " npcs, " + neu.craftRecipes.size() + " recipes, "
			+ neu.offersByItem.size() + " shop items, " + neu.warps.size() + " warps in " + (System.currentTimeMillis() - start) + "ms");
		HypixelItems items = HypixelItems.parse(Files.readString(data.resolve("items.json"), StandardCharsets.UTF_8));
		Prices prices = Prices.parse(Files.readString(data.resolve("prices_skyhelper.json"), StandardCharsets.UTF_8),
			Files.readString(data.resolve("bazaar.json"), StandardCharsets.UTF_8));
		Rules rules = Rules.bundled();
		ModConfig config = ModConfig.get();

		if (args.length > 2) {
			// Real profile through EliteBot: args[2] = player uuid
			com.taz.xpassist.data.PlayerProfile elite = com.taz.xpassist.data.EliteProfile.fetch(
				java.util.UUID.fromString(args[2].replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5")), rules, items, neu, "");
			System.out.println("EliteBot: profile=" + elite.profileName + " xp=" + elite.skyblockXp + " museum=" + (elite.museumDonated == null ? null : elite.museumDonated.size())
				+ " accessories=" + (elite.accessories == null ? null : elite.accessories.size()) + " pets=" + elite.bestPets.size()
				+ " minions=" + elite.craftedMinions.size() + " consumed=" + elite.consumed);
			System.out.println("  museum has ZOMBIE_PICKAXE=" + elite.museumDonated.contains("ZOMBIE_PICKAXE") + " ROOKIE_PICKAXE=" + elite.museumDonated.contains("ROOKIE_PICKAXE")
				+ " SKELETON_MASTER=" + elite.museumDonated.contains("SKELETON_MASTER") + " FLOWER_11=" + elite.craftedMinions.contains("FLOWER_11"));
			com.taz.xpassist.data.CollectionIndex collections = com.taz.xpassist.data.CollectionIndex.parse(
				Files.readString(data.resolve("collections.json"), StandardCharsets.UTF_8));
			XpResult real = XpCalculator.compute(neu, items, prices, elite, rules, config, collections,
				new com.taz.xpassist.data.OwnedItems(java.util.Set.of("RADIANT_POWER_ORB"), Map.of()));
			print("Real profile via EliteBot", real, 45);
			System.out.println("coins=" + TextUtil.coins(real.coins(), 2) + " affordable level=" + real.affordableLevel()
				+ " locked=" + real.locked().size());
			real.locked().stream().limit(200).forEach(t -> System.out.println("  LOCKED " + t.fullLabel() + " -> " + t.lockedReason));
			for (XpResult.Entry entry : real.entries()) {
				if (entry instanceof XpResult.TaskEntry t && (t.task().label.contains("Power Orb") || t.task().label.contains("Flux"))) {
					System.out.println("  ORB " + t.task().fullLabel() + ": " + TextUtil.coins(t.task().cost) + " (" + TextUtil.xp(t.task().xp) + " XP) " + t.task().details);
				}
			}
			long essence = real.entries().stream().filter(e -> e instanceof XpResult.TaskEntry t && t.task().category == com.taz.xpassist.xp.XpCategory.ESSENCE).count();
			System.out.println("essence perk tasks: " + essence);
			real.entries().stream().filter(e -> e instanceof XpResult.TaskEntry t && t.task().category == com.taz.xpassist.xp.XpCategory.ESSENCE)
				.limit(4).forEach(e -> System.out.println("  ESS " + ((XpResult.TaskEntry) e).task().fullLabel() + ": " + TextUtil.coins(((XpResult.TaskEntry) e).task().cost) + " (" + TextUtil.xp(((XpResult.TaskEntry) e).task().xp) + " XP)"));
			com.taz.xpassist.xp.TripPlanner.Plan plan = com.taz.xpassist.xp.TripPlanner.build(real, neu, rules, 15);
			System.out.println("===== PLAN " + plan.fromLevel() + " -> " + plan.toLevel() + " cost " + TextUtil.coins(plan.cost(), 2) + " xp " + plan.xp() + " stops " + plan.stops().size());
			for (com.taz.xpassist.xp.TripPlanner.Stop stop : plan.stops()) {
				if (!stop.materials().isEmpty()) System.out.println("  BRING @ " + stop.title() + ": " + TextUtil.materials(stop.materials()));
				System.out.println("  [" + stop.title() + "] " + stop.where() + " — " + stop.tasks().size() + " items, +" + TextUtil.xp(stop.xp()) + " XP, " + TextUtil.coins(stop.cost()) + (stop.note() != null ? "  (" + stop.note() + ")" : ""));
				stop.tasks().stream().limit(6).forEach(t -> System.out.println("      " + t.fullLabel() + " " + TextUtil.coins(t.cost) + " (" + TextUtil.xp(t.xp) + ")"));
			}
			plan.finishSteps().forEach(s -> System.out.println("  THEN " + s));
			return;
		}
		XpResult none = XpCalculator.compute(neu, items, prices, null, rules, config);
		print("No profile", none, 40);

		// A fake mid-game profile: level 376 with 50 XP, a few things already done.
		PlayerProfile fake = new PlayerProfile();
		fake.profileName = "Pomegranate";
		fake.skyblockXp = 37650;
		fake.museumDonated = new HashSet<>(java.util.List.of("DECENT_BOW", "ROSETTA", "SQUIRE_SWORD"));
		fake.craftedMinions.addAll(java.util.List.of("MYCELIUM_1", "FLOWER_1", "FLOWER_2", "FLOWER_3", "FLOWER_4", "FLOWER_5",
			"FLOWER_6", "FLOWER_7", "FLOWER_8", "FLOWER_9", "FLOWER_10"));
		fake.abiphoneContacts.add("madredstoneengineer");
		fake.accessories = new java.util.HashMap<>(Map.of("WOLF_TALISMAN", "COMMON"));
		fake.bestPets.put("BEE", "LEGENDARY");
		XpResult withProfile = XpCalculator.compute(neu, items, prices, fake, rules, config);
		print("Fake profile", withProfile, 40);

		String[] compare = {"Decent Bow", "Mycelium Minion 1", "Mycelium Minion 2", "Mycelium Minion 3", "Mad Redstone Engineer",
			"Flower Minion 11", "Flower Minion 12", "Red Sweater", "Enderman Mask", "Marigold", "Goblin Armor", "Lumber Merchant",
			"Tyzzo", "Rosetta", "Celeste Wand", "Squire Sword", "Spider Tamer", "Pesthunter", "Kysha", "Clay", "Frozen Alex",
			"Oringo", "Icy Sinker", "Totem of Corruption", "Jake", "Tactician", "Squid Boots", "Voidling Minion 1", "Lapis Minion 1"};
		System.out.println("===== Compare with SkyHelper screenshot (no profile)");
		for (String wanted : compare) {
			for (XpResult.Entry entry : none.entries()) {
				if (entry instanceof XpResult.TaskEntry t && t.task().label.contains(wanted)) {
					XpTask task = t.task();
					System.out.println("  " + task.fullLabel() + ": " + (task.partialPrice ? "~" : "") + TextUtil.coins(task.cost)
						+ " (" + TextUtil.xp(task.xp) + " XP) [" + task.costSource + "] " + task.details);
					break;
				}
			}
		}
		for (XpTask task : none.unpriced()) {
			if (task.label.contains("Spider Tamer") || task.label.contains("Oringo") || task.label.contains("Kysha")) {
				System.out.println("  UNPRICED " + task.fullLabel() + " " + task.details);
			}
		}
		for (String name : new String[]{"Gregory", "Mad Redstone Engineer", "Alchemist"}) {
			Npc npc = neu.findNpcByExactName(name);
			System.out.println(name + " -> " + npc + " warp=" + (npc != null ? neu.bestWarp(npc.island(), npc.x(), npc.y(), npc.z()) : null));
		}
		for (String area : new String[]{"Hub", "The End", "Galatea", "Private Island", "Garden", "Dwarven Mines", "Crimson Isle", "Spider's Den"}) {
			System.out.println("Area " + area + " -> " + neu.modeForArea(area, rules));
		}
	}

	private static void print(String title, XpResult result, int limit) {
		System.out.println("===== " + title + ": " + result.entries().size() + " entries, unpriced " + result.unpriced().size()
			+ ", total " + TextUtil.coins(result.totalCost(), 2) + " for " + TextUtil.xp(result.totalXp()) + " XP");
		result.warnings().forEach(w -> System.out.println("  ! " + w));
		int i = 0;
		for (XpResult.Entry entry : result.entries()) {
			if (i++ >= limit) {
				break;
			}
			if (entry instanceof XpResult.TaskEntry t) {
				XpTask task = t.task();
				System.out.println("  " + task.fullLabel() + ": " + (task.partialPrice ? "~" : "") + TextUtil.coins(task.cost)
					+ " (" + TextUtil.xp(task.xp) + " XP) [" + task.costSource + "]" + (task.npcId != null ? " npc=" + task.npcId : ""));
			} else if (entry instanceof XpResult.LevelEntry l) {
				System.out.println("  ** SkyBlock Level " + l.level() + ": " + TextUtil.coins(l.cumulativeCost(), 2));
			}
		}
		Map<String, Integer> counts = new java.util.TreeMap<>();
		for (XpResult.Entry entry : result.entries()) {
			if (entry instanceof XpResult.TaskEntry t) {
				counts.merge(t.task().category.label, 1, Integer::sum);
			}
		}
		System.out.println("  per category: " + counts);
		System.out.println("  sample unpriced: " + result.unpriced().stream().limit(8).map(XpTask::fullLabel).toList());
	}
}
