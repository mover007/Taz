package com.taz.xpassist.xp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.taz.xpassist.config.ModConfig;
import com.taz.xpassist.data.CollectionIndex;
import com.taz.xpassist.data.HypixelItems;
import com.taz.xpassist.data.OwnedItems;
import com.taz.xpassist.data.NeuRepo;
import com.taz.xpassist.data.PlayerProfile;
import com.taz.xpassist.data.PriceBook;
import com.taz.xpassist.data.Prices;
import com.taz.xpassist.data.Rules;
import com.taz.xpassist.npc.Npc;
import com.taz.xpassist.util.TextUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the "cheapest SkyBlock XP" list: every upgrade you have not done yet, priced with live
 * data and sorted by coins per SkyBlock XP, the same way the SkyHelper bot's /level command does.
 */
public final class XpCalculator {
	private static final Pattern COINS = Pattern.compile("(?i)^([\\d.,]+)\\s*coins?$");
	private static final Pattern ITEM_COUNT = Pattern.compile("(?i)^(\\d[\\d,]*)x\\s+(.+)$");
	private static final Pattern SLAYER_REQ = Pattern.compile("(?i)^(Zombie|Spider|Wolf|Enderman|Blaze|Vampire) Slayer (\\d+)$");
	private static final Pattern CONTACT_REQ = Pattern.compile("(?i)^Have (.+?)'s? contact$");
	private static final Pattern PET = Pattern.compile("(?i)^\\[lvl\\s*\\d+]\\s+(.+)$");
	private static final Pattern PET_PRICE = Pattern.compile("^LVL_1_(COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC)_([A-Z0-9_]+)$");

	private final NeuRepo neu;
	private final HypixelItems items;
	private final PriceBook book;
	private final PlayerProfile profile;
	private final Rules rules;
	private final Unlocks unlocks;
	private final OwnedItems owned;
	private final List<XpTask> tasks = new ArrayList<>();
	private final List<String> warnings = new ArrayList<>();
	/** Set by linePrice when a requirement is checkable and not met yet (e.g. a slayer level). */
	private String pendingLock;
	/** Items found while pricing a contact's requirements, for its buttons. */
	private final List<XpTask.Buy> pendingBuys = new ArrayList<>();
	private final Map<String, Double> pendingMaterials = new LinkedHashMap<>();

	private XpCalculator(NeuRepo neu, HypixelItems items, Prices prices, PlayerProfile profile, Rules rules, ModConfig config,
						 CollectionIndex collections, OwnedItems owned) {
		this.neu = neu;
		this.items = items;
		this.unlocks = new Unlocks(neu, profile, collections);
		// Only count crafting as "cheapest" when you have unlocked the recipe.
		this.book = new PriceBook(prices, neu, config, id -> unlocks.craftBlocker(id) == null);
		this.profile = profile;
		this.rules = rules;
		this.owned = owned != null ? owned : OwnedItems.EMPTY;
	}

	public static XpResult compute(NeuRepo neu, HypixelItems items, Prices prices, PlayerProfile profile, Rules rules, ModConfig config) {
		return compute(neu, items, prices, profile, rules, config, null, OwnedItems.EMPTY);
	}

	public static XpResult compute(NeuRepo neu, HypixelItems items, Prices prices, PlayerProfile profile, Rules rules, ModConfig config,
								   CollectionIndex collections, OwnedItems owned) {
		XpCalculator calc = new XpCalculator(neu, items, prices, profile, rules, config, collections, owned);
		if (profile == null) {
			calc.warnings.add("No progress known yet, so this lists every upgrade. Open /sblevels menus to sync, or add an API key in /taz > SkyBlock XP > Profile");
		} else if (!profile.fromApi) {
			calc.warnings.add("Using progress seen in game. Open the /sblevels task menus to sync, or add an API key for everything.");
		}
		calc.museum();
		calc.minions();
		calc.abiphone();
		calc.accessories();
		calc.pets();
		calc.consumables();
		calc.essencePerks();
		calc.bagUpgrades();
		calc.bankUpgrades();
		return calc.build(config);
	}

	private XpResult build(ModConfig config) {
		// This runs on the data thread while the list screen may be editing these sets.
		Set<String> hidden;
		Set<String> disabled;
		synchronized (config) {
			hidden = new HashSet<>(config.hiddenTasks);
			disabled = new HashSet<>(config.disabledCategories);
		}
		List<XpTask> priced = new ArrayList<>();
		List<XpTask> unpriced = new ArrayList<>();
		List<XpTask> locked = new ArrayList<>();
		Map<String, String> lockedIds = new HashMap<>();
		for (XpTask task : tasks) {
			if (hidden.contains(task.id) || disabled.contains(task.category.name())) {
				continue;
			}
			// A task whose prerequisite is locked (e.g. the previous minion tier) is locked too.
			if (task.lockedReason == null && task.prerequisite != null && lockedIds.containsKey(task.prerequisite)) {
				task.lockedReason = lockedIds.get(task.prerequisite);
			}
			if (task.lockedReason != null) {
				lockedIds.put(task.id, task.lockedReason);
				locked.add(task);
				continue;
			}
			(task.priced() ? priced : unpriced).add(task);
		}
		locked.sort(Comparator.comparingDouble(XpTask::coinsPerXp));
		priced.sort(Comparator.comparingDouble(XpTask::coinsPerXp).thenComparingDouble(t -> t.cost));
		unpriced.sort(Comparator.comparing(XpTask::fullLabel));

		// Keep prerequisites (previous minion tier, etc.) before the task that needs them.
		Set<String> pricedIds = new HashSet<>();
		for (XpTask task : priced) {
			pricedIds.add(task.id);
		}
		List<XpTask> ordered = new ArrayList<>();
		Set<String> emitted = new HashSet<>();
		Map<String, List<XpTask>> waiting = new HashMap<>();
		for (XpTask task : priced) {
			if (task.prerequisite != null && pricedIds.contains(task.prerequisite) && !emitted.contains(task.prerequisite)) {
				waiting.computeIfAbsent(task.prerequisite, k -> new ArrayList<>()).add(task);
			} else {
				emit(task, ordered, emitted, waiting);
			}
		}
		for (List<XpTask> rest : waiting.values()) {
			for (XpTask task : rest) {
				if (!emitted.contains(task.id)) {
					emit(task, ordered, emitted, waiting);
				}
			}
		}

		int perLevel = rules.xpPerLevel();
		double startXp = profile != null ? profile.skyblockXp : 0;
		double xp = startXp;
		int level = (int) Math.floor(xp / perLevel);
		double cumulative = 0;
		double totalXp = 0;
		List<XpResult.Entry> entries = new ArrayList<>();
		for (XpTask task : ordered) {
			cumulative += task.cost;
			xp += task.xp;
			totalXp += task.xp;
			entries.add(new XpResult.TaskEntry(task, cumulative));
			while (Math.floor(xp / perLevel) > level) {
				level++;
				entries.add(new XpResult.LevelEntry(level, cumulative));
			}
		}
		return new XpResult(List.copyOf(entries), List.copyOf(unpriced), List.copyOf(locked), cumulative, totalXp, startXp,
			profile != null && profile.skyblockXp > 0, profile != null ? profile.profileName : null, List.copyOf(warnings), System.currentTimeMillis(),
			profile != null ? profile.source : null, profile != null ? profile.coins : -1);
	}

	private static void emit(XpTask task, List<XpTask> ordered, Set<String> emitted, Map<String, List<XpTask>> waiting) {
		ordered.add(task);
		emitted.add(task.id);
		List<XpTask> unlocked = waiting.remove(task.id);
		if (unlocked != null) {
			for (XpTask next : unlocked) {
				emit(next, ordered, emitted, waiting);
			}
		}
	}

	// ---------------------------------------------------------------- Museum

	private void museum() {
		Set<String> donated = profile != null ? profile.museumDonated : null;
		if (profile != null && profile.fromApi && donated == null) {
			warnings.add("Museum API is off: turn it on in SkyBlock Settings > API Settings. Museum items may already be donated.");
		}
		Map<String, String> parentOf = new HashMap<>();
		Map<String, Integer> setXp = new LinkedHashMap<>();
		Map<String, List<String>> setPieces = new LinkedHashMap<>();
		for (HypixelItems.Item item : items.items.values()) {
			if (item.museumParent != null) {
				parentOf.put(item.id, item.museumParent);
			}
			for (Map.Entry<String, Integer> set : item.armorSets.entrySet()) {
				setXp.put(set.getKey(), set.getValue());
				setPieces.computeIfAbsent(set.getKey(), k -> new ArrayList<>()).add(item.id);
			}
		}
		if (neu.museum.has("children") && neu.museum.get("children").isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : neu.museum.getAsJsonObject("children").entrySet()) {
				if (entry.getValue().isJsonPrimitive()) {
					parentOf.putIfAbsent(entry.getValue().getAsString(), entry.getKey());
				}
			}
		}

		// Every museum entry you haven't donated (directly or through an upgrade), before chain handling.
		Map<String, XpTask> nodes = new LinkedHashMap<>();
		for (HypixelItems.Item item : items.items.values()) {
			if (item.museumXp <= 0 || isDonated(item.id, donated, parentOf)) {
				continue;
			}
			PriceBook.Quote quote = book.quote(item.id);
			XpTask task = new XpTask("museum:" + item.id, XpCategory.MUSEUM, "Donate " + item.name, item.museumXp, quote.cost());
			describeSource(task, quote, item.id);
			lockIfOnlyCraftable(task, quote, item.id);
			task.finish = "MUSEUM";
			if (owned.ids().contains(item.id)) {
				markOwned(task, "You're carrying it: just donate it at the Museum (/warp museum)");
			}
			if (item.museumCategory != null) {
				task.details.add("Museum section: " + TextUtil.titleCase(item.museumCategory));
			}
			nodes.put(item.id, task);
		}

		for (Map.Entry<String, List<String>> set : setPieces.entrySet()) {
			String setId = set.getKey();
			if (isDonated(setId, donated, parentOf)) {
				continue;
			}
			List<String> pieces = set.getValue();
			pieces.sort(Comparator.naturalOrder());
			List<String> names = new ArrayList<>();
			double total = 0;
			String npc = null;
			List<String> pieceLines = new ArrayList<>();
			int ownedPieces = 0;
			Map<String, PriceBook.Quote> pieceQuotes = new LinkedHashMap<>();
			for (String piece : pieces) {
				String name = items.name(piece);
				names.add(name);
				if (owned.ids().contains(piece)) {
					ownedPieces++;
					pieceLines.add("  " + name + ": you have it");
					continue;
				}
				PriceBook.Quote quote = book.quote(piece);
				total += quote.cost();
				if (npc == null && quote.npcId() != null) {
					npc = quote.npcId();
				}
				pieceLines.add("  " + name + ": " + TextUtil.coins(quote.cost()) + " (" + sourceText(quote) + ")");
				pieceQuotes.put(piece, quote);
			}
			XpTask task = new XpTask("museum:" + setId, XpCategory.MUSEUM, "Donate " + setName(setId, names), setXp.get(setId), total);
			task.costSource = "Full set";
			task.npcId = npc;
			for (Map.Entry<String, PriceBook.Quote> piece : pieceQuotes.entrySet()) {
				addBuy(task, piece.getValue(), piece.getKey());
			}
			task.stop = npc != null ? "npc:" + npc : "AH";
			task.finish = "MUSEUM";
			if (ownedPieces == pieces.size()) {
				markOwned(task, "You're carrying the whole set: just donate it at the Museum (/warp museum)");
			}
			task.details.add("Needs every piece:");
			task.details.addAll(pieceLines);
			nodes.put(setId, task);
		}
		addMuseumChains(nodes, parentOf);
	}

	/**
	 * Donating an upgraded item also counts every lower tier as donated (Overflux counts Mana Flux and
	 * Radiant), and you get their XP. For each upgrade tree, repeatedly pick the donation with the best
	 * coins per XP, crediting it with the lower tiers it covers, so nothing is counted twice.
	 */
	private void addMuseumChains(Map<String, XpTask> nodes, Map<String, String> parentOf) {
		Map<String, List<String>> children = new HashMap<>();
		List<String> roots = new ArrayList<>();
		for (String id : nodes.keySet()) {
			String parent = parentOf.get(id);
			if (parent != null && nodes.containsKey(parent)) {
				children.computeIfAbsent(parent, k -> new ArrayList<>()).add(id);
			} else {
				roots.add(id);
			}
		}
		for (String root : roots) {
			List<String> tree = new ArrayList<>();
			collect(root, children, tree);
			Set<String> covered = new HashSet<>();
			String previous = null;
			while (covered.size() < tree.size()) {
				String best = null;
				double bestRatio = Double.MAX_VALUE;
				double bestGain = 0;
				for (String id : tree) {
					XpTask task = nodes.get(id);
					if (covered.contains(id) || !task.priced()) {
						continue;
					}
					double gain = 0;
					List<String> sub = new ArrayList<>();
					collect(id, children, sub);
					for (String below : sub) {
						if (!covered.contains(below)) {
							gain += nodes.get(below).xp;
						}
					}
					double ratio = task.cost / gain;
					if (ratio < bestRatio || (ratio == bestRatio && gain > bestGain)) {
						best = id;
						bestRatio = ratio;
						bestGain = gain;
					}
				}
				if (best == null) {
					break;
				}
				XpTask base = nodes.get(best);
				List<String> sub = new ArrayList<>();
				collect(best, children, sub);
				List<String> alsoCounts = new ArrayList<>();
				for (String below : sub) {
					if (!below.equals(best) && !covered.contains(below)) {
						alsoCounts.add(nodes.get(below).label.replaceFirst("^Donate ", ""));
					}
				}
				covered.addAll(sub);
				String label = base.label + (alsoCounts.isEmpty() ? "" : " (+" + alsoCounts.size() + " lower tier" + (alsoCounts.size() == 1 ? "" : "s") + ")");
				XpTask task = copyWith(base, label, bestGain);
				if (!alsoCounts.isEmpty()) {
					task.details.add(Math.min(1, task.details.size()), "Also counts as donating: " + String.join(", ", alsoCounts) + " (their XP is included)");
				}
				task.prerequisite = previous;
				previous = task.id;
				tasks.add(task);
			}
			// Tiers nobody could price stay listed on their own (they show under Unpriced).
			for (String id : tree) {
				if (!covered.contains(id)) {
					tasks.add(nodes.get(id));
				}
			}
		}
	}

	private static void collect(String id, Map<String, List<String>> children, List<String> out) {
		out.add(id);
		for (String child : children.getOrDefault(id, List.of())) {
			collect(child, children, out);
		}
	}

	private static XpTask copyWith(XpTask base, String label, double xp) {
		XpTask task = new XpTask(base.id, base.category, label, xp, base.cost);
		task.partialPrice = base.partialPrice;
		task.costSource = base.costSource;
		task.npcId = base.npcId;
		task.stop = base.stop;
		task.finish = base.finish;
		task.owned = base.owned;
		task.lockedReason = base.lockedReason;
		task.details.addAll(base.details);
		task.buys.addAll(base.buys);
		task.materials.putAll(base.materials);
		task.groupKey = base.groupKey;
		task.groupLabel = base.groupLabel;
		task.groupStep = base.groupStep;
		return task;
	}

	private static boolean isDonated(String id, Set<String> donated, Map<String, String> parentOf) {
		if (donated == null) {
			return false;
		}
		String current = id;
		for (int depth = 0; current != null && depth < 20; depth++) {
			if (donated.contains(current)) {
				return true;
			}
			current = parentOf.get(current);
		}
		return false;
	}

	private static String setName(String setId, List<String> names) {
		if (names.size() == 1) {
			return names.getFirst();
		}
		String[] first = names.getFirst().split(" ");
		int common = first.length;
		for (String name : names) {
			String[] words = name.split(" ");
			int i = 0;
			while (i < common && i < words.length && words[i].equals(first[i])) {
				i++;
			}
			common = i;
		}
		if (common == 0) {
			return TextUtil.titleCase(setId) + " Armor";
		}
		return String.join(" ", Arrays.copyOf(first, common)) + " Armor";
	}

	// ---------------------------------------------------------------- Unique minions

	private void minions() {
		JsonObject maxTiers = neu.miscObject("minions");
		JsonObject xpTable = neu.miscObject("minionXp");
		if (xpTable.size() == 0) {
			xpTable = rules.fallbackXp().getAsJsonObject("minion_tiers");
		}
		if (profile != null && profile.fromApi && profile.craftedMinions.isEmpty()) {
			warnings.add("Your profile has no crafted minions listed, so every minion tier is shown.");
		}
		for (Map.Entry<String, JsonElement> entry : maxTiers.entrySet()) {
			String base = entry.getKey();
			int max = entry.getValue().getAsInt();
			String apiBase = base.replace("_GENERATOR", "");
			String minionName = TextUtil.stripTrailingRoman(neu.name(base + "_1"));
			for (int tier = 1; tier <= max; tier++) {
				if (profile != null && profile.craftedMinions.contains(apiBase + "_" + tier)) {
					continue;
				}
				String itemId = base + "_" + tier;
				double xp = xpTable.has(String.valueOf(tier)) ? xpTable.get(String.valueOf(tier)).getAsDouble() : 1;
				NeuRepo.Recipe recipe = neu.craftRecipes.get(itemId);
				double cost = Double.NaN;
				boolean partial = false;
				List<String> recipeLines = new ArrayList<>();
				XpTask minionBuys = new XpTask("", XpCategory.MINIONS, "", 0, 0);
				if (recipe != null) {
					cost = 0;
					for (Map.Entry<String, Double> input : recipe.inputs().entrySet()) {
						String inputId = input.getKey();
						if (inputId.startsWith(base + "_")) {
							recipeLines.add("  " + neu.name(inputId) + " (previous tier)");
							continue;
						}
						PriceBook.Quote quote = book.quote(inputId);
						if (quote.priced()) {
							cost += quote.cost() * input.getValue();
						} else {
							partial = true;
						}
						addBuy(minionBuys, quote, inputId);
						recipeLines.add("  " + fmtCount(input.getValue()) + "x " + itemName(inputId) + ": "
							+ TextUtil.coins(quote.cost() * input.getValue()) + " (" + sourceText(quote) + ")");
					}
				}
				XpTask task = new XpTask("minion:" + itemId, XpCategory.MINIONS, "Craft " + minionName + " " + tier, xp, cost);
				task.partialPrice = partial;
				task.costSource = "Craft";
				task.stop = "CRAFT";
				task.finish = "CRAFT";
				task.addBuy("recipe", itemId, minionName + " " + TextUtil.roman(tier));
				task.buys.addAll(minionBuys.buys);
				task.lockedReason = unlocks.blocker(itemId);
				if (tier > 1) {
					task.prerequisite = "minion:" + base + "_" + (tier - 1);
				}
				String requirement = neu.craftText.get(itemId);
				if (requirement != null) {
					task.details.add(requirement);
				}
				String reputation = neu.reputationReq.get(itemId);
				if (reputation != null) {
					task.details.add("Requires " + reputation.replace(":", " reputation: "));
				}
				if (!recipeLines.isEmpty()) {
					task.details.add("Recipe:");
					task.details.addAll(recipeLines);
				}
				if (partial) {
					task.details.add("Some ingredients have no price (counted as 0).");
				}
				tasks.add(task);
			}
		}
	}

	// ---------------------------------------------------------------- Abiphone

	private void abiphone() {
		JsonObject misc = neu.sbObject("miscellaneous_task");
		double xp = misc.has("abiphone_contacts_xp") ? misc.get("abiphone_contacts_xp").getAsDouble()
			: rules.fallbackXp().get("abiphone_contact").getAsDouble();
		if (profile != null && !profile.abiphoneKnown && profile.abiphoneContacts.isEmpty()) {
			warnings.add(profile.source + " has no Abiphone data: right-click your Abiphone once so contacts you have are hidden.");
		}
		for (Map.Entry<String, JsonElement> entry : neu.abiphone.entrySet()) {
			if (!entry.getValue().isJsonObject()) {
				continue;
			}
			String name = entry.getKey();
			JsonObject contact = entry.getValue().getAsJsonObject();
			if (profile != null && hasContact(name, contact)) {
				continue;
			}
			List<String> lines = new ArrayList<>();
			if (contact.has("requirement")) {
				for (JsonElement line : contact.getAsJsonArray("requirement")) {
					String text = TextUtil.strip(line.getAsString()).trim().replaceFirst("^-\\s*", "").replaceFirst("\\.$", "").trim();
					if (!text.isEmpty()) {
						lines.add(text);
					}
				}
			}
			pendingLock = null;
			pendingBuys.clear();
			pendingMaterials.clear();
			double cost = lines.isEmpty() ? Double.NaN : requirementCost(lines);
			XpTask task = new XpTask("abiphone:" + name, XpCategory.ABIPHONE, name + " Contact", xp, cost);
			task.costSource = "Requirement";
			if (Double.isNaN(cost) && pendingLock != null) {
				task.lockedReason = pendingLock;
			}
			task.buys.addAll(pendingBuys);
			task.materials.putAll(pendingMaterials);
			task.details.add("To add this contact:");
			boolean listed = false;
			for (String line : lines) {
				if (line.toLowerCase(Locale.ROOT).startsWith("no requirement")) {
					continue;
				}
				task.details.add("  " + line);
				listed = true;
			}
			if (!listed) {
				task.details.add("  Requirement not in the NEU repo yet - talk to them in game to see it");
			}
			Npc npc = neu.findNpcByExactName(name);
			if (npc == null) {
				npc = neu.npcs.get("ABIPHONE:" + name);
			}
			if (npc != null && npc.hasLocation()) {
				task.npcId = npc.id();
				task.stop = "npc:" + npc.id();
				task.finish = "npc:" + npc.id();
				task.details.add("Talk to " + npc.name() + " in " + neu.islandName(npc.island()));
			}
			tasks.add(task);
		}
	}

	private boolean hasContact(String name, JsonObject contact) {
		if (profile.abiphoneContacts.contains(TextUtil.normalize(name))) {
			return true;
		}
		if (contact.has("callNames")) {
			for (JsonElement callName : contact.getAsJsonArray("callNames")) {
				if (profile.abiphoneContacts.contains(TextUtil.normalize(callName.getAsString()))) {
					return true;
				}
			}
		}
		return false;
	}

	/** Prices requirement lines such as "64x Mutant Nether Wart" or "32.000.000 Coins". NaN if any part can't be bought. */
	private double requirementCost(List<String> lines) {
		List<List<String>> alternatives = new ArrayList<>();
		List<String> current = new ArrayList<>();
		for (String line : lines) {
			if (line.toLowerCase(Locale.ROOT).endsWith(" or")) {
				current.add(line.substring(0, line.length() - 3).trim());
				alternatives.add(current);
				current = new ArrayList<>();
			} else {
				current.add(line);
			}
		}
		alternatives.add(current);

		double best = Double.NaN;
		for (List<String> group : alternatives) {
			if (group.isEmpty()) {
				continue;
			}
			double total = 0;
			boolean ok = true;
			for (String line : group) {
				double part = linePrice(line);
				if (Double.isNaN(part)) {
					ok = false;
					break;
				}
				total += part;
			}
			if (ok && (Double.isNaN(best) || total < best)) {
				best = total;
			}
		}
		return best;
	}

	private double linePrice(String line) {
		// Contacts always need something in game even when the repo says "No Requirement", so never call them free.
		if (line.toLowerCase(Locale.ROOT).startsWith("no requirement")) {
			return Double.NaN;
		}
		Matcher coins = COINS.matcher(line);
		if (coins.matches()) {
			return Double.parseDouble(coins.group(1).replace(",", "").replace(".", ""));
		}
		// Requirements you either already meet (free) or can't buy (locked).
		Matcher slayer = SLAYER_REQ.matcher(line);
		if (slayer.matches() && profile != null && profile.slayerLevels != null) {
			int need = Integer.parseInt(slayer.group(2));
			int have = profile.slayerLevels.getOrDefault(slayer.group(1).toLowerCase(Locale.ROOT), 0);
			if (have >= need) {
				return 0;
			}
			pendingLock = "Needs " + TextUtil.titleCase(slayer.group(1)) + " Slayer " + need + " (you have " + have + ")";
			return Double.NaN;
		}
		Matcher contact = CONTACT_REQ.matcher(line);
		if (contact.matches() && profile != null) {
			String other = TextUtil.normalize(contact.group(1));
			if (profile.abiphoneContacts.contains(other)) {
				return 0;
			}
			pendingLock = "Needs " + contact.group(1) + "'s Abiphone contact first";
			return Double.NaN;
		}
		Matcher count = ITEM_COUNT.matcher(line);
		if (!count.matches()) {
			return Double.NaN;
		}
		int amount = Integer.parseInt(count.group(1).replace(",", ""));
		String name = count.group(2).replaceFirst("\\s*\\(.*\\)\\s*$", "").replaceFirst("\\.$", "").trim();
		Matcher pet = PET.matcher(name);
		if (pet.matches()) {
			pendingBuys.add(new XpTask.Buy("ah", pet.group(1).trim(), "[Lvl 1] " + pet.group(1).trim()));
			pendingMaterials.merge("[Lvl 1] " + pet.group(1).trim() + " pet", (double) amount, Double::sum);
			return amount * cheapestPet(pet.group(1));
		}
		String id = resolveItem(name);
		if (id == null) {
			return Double.NaN;
		}
		PriceBook.Quote quote = book.quote(id);
		XpTask holder = new XpTask("", XpCategory.ABIPHONE, "", 0, 0);
		addBuy(holder, quote, id);
		pendingBuys.addAll(holder.buys);
		// Use the name as the requirement writes it ("Bezal Shard"), not a guessed id.
		pendingMaterials.merge(items.name(id) != null ? items.name(id) : name, (double) amount, Double::sum);
		return amount * quote.cost();
	}

	/** Hypixel's item names first, then NEU's, then a guessed id ("Jungle Axe" -> JUNGLE_AXE, "Nessie Shard" -> SHARD_NESSIE). */
	private String resolveItem(String name) {
		String id = items.idForName(name);
		if (id == null) {
			id = neu.idForName(name);
		}
		if (id != null && book.quote(id).priced()) {
			return id;
		}
		String guess = name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9 ]", "").trim().replace(' ', '_');
		List<String> guesses = new ArrayList<>(List.of(guess));
		if (guess.endsWith("_SHARD")) {
			guesses.add("SHARD_" + guess.substring(0, guess.length() - "_SHARD".length()));
		}
		if (guess.endsWith("S")) {
			guesses.add(guess.substring(0, guess.length() - 1));
		}
		for (String candidate : guesses) {
			if (book.quote(candidate).priced()) {
				return candidate;
			}
		}
		return id;
	}

	private double cheapestPet(String petName) {
		String type = petName.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
		double best = Double.NaN;
		for (String rarity : List.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC")) {
			double price = book.prices().market("LVL_1_" + rarity + "_" + type);
			if (!Double.isNaN(price) && (Double.isNaN(best) || price < best)) {
				best = price;
			}
		}
		return best;
	}

	// ---------------------------------------------------------------- Accessories

	private void accessories() {
		Map<String, String> owned = profile != null ? profile.accessories : null;
		if (profile != null && !profile.fromApi) {
			warnings.add("Accessories and pets are hidden until your profile loads (EliteBot or an API key).");
			return;
		}
		if (profile != null && profile.fromApi && owned == null) {
			warnings.add("Inventory API is off, so accessories are hidden. Turn it on in SkyBlock Settings > API Settings.");
			return;
		}
		JsonObject core = neu.sbObject("core_task");
		double perMp = core.has("accessory_bag_xp") ? core.get("accessory_bag_xp").getAsDouble()
			: rules.fallbackXp().get("accessory_bag_per_mp").getAsDouble();

		Map<String, String> family = new HashMap<>();
		for (Map.Entry<String, JsonElement> entry : neu.miscObject("talisman_upgrades").entrySet()) {
			for (JsonElement upgrade : entry.getValue().getAsJsonArray()) {
				union(family, entry.getKey(), upgrade.getAsString());
			}
		}
		Set<String> ignored = new HashSet<>();
		for (JsonElement element : neu.miscArray("ignored_talisman")) {
			ignored.add(element.getAsString());
		}
		Map<String, Integer> familyPower = new HashMap<>();
		if (owned != null) {
			for (Map.Entry<String, String> entry : owned.entrySet()) {
				familyPower.merge(find(family, entry.getKey()), rules.magicalPower(entry.getValue()), Math::max);
			}
		}
		for (HypixelItems.Item item : items.items.values()) {
			if (!"ACCESSORY".equals(item.category) || item.tier == null || "RIFT".equals(item.origin)
				|| ignored.contains(item.id) || (owned != null && owned.containsKey(item.id))) {
				continue;
			}
			int power = rules.magicalPower(item.tier);
			int gain = power - familyPower.getOrDefault(find(family, item.id), 0);
			if (gain <= 0) {
				continue;
			}
			PriceBook.Quote quote = book.quote(item.id);
			XpTask task = new XpTask("accessory:" + item.id, XpCategory.ACCESSORIES,
				"Buy " + item.name + " (+" + gain + " MP)", gain * perMp, quote.cost());
			describeSource(task, quote, item.id);
			lockIfOnlyCraftable(task, quote, item.id);
			task.finish = "BAG";
			task.details.add(TextUtil.titleCase(item.tier) + " accessory, +" + gain + " Magical Power");
			if (this.owned.ids().contains(item.id)) {
				markOwned(task, "You're carrying it: put it in your Accessory Bag");
			}
			tasks.add(task);
		}
	}

	private static String find(Map<String, String> parent, String id) {
		String root = id;
		while (parent.containsKey(root) && !parent.get(root).equals(root)) {
			root = parent.get(root);
		}
		return root;
	}

	private static void union(Map<String, String> parent, String a, String b) {
		String rootA = find(parent, a);
		String rootB = find(parent, b);
		if (!rootA.equals(rootB)) {
			parent.put(rootB, rootA);
		}
	}

	// ---------------------------------------------------------------- Pets

	private void pets() {
		if (profile != null && !profile.fromApi) {
			return;
		}
		JsonObject core = neu.sbObject("core_task");
		double perScore = core.has("pet_score_xp") ? core.get("pet_score_xp").getAsDouble()
			: rules.fallbackXp().get("pet_score").getAsDouble();
		Map<String, Double> options = new TreeMap<>(book.prices().marketPrices());
		for (Map.Entry<String, Double> entry : options.entrySet()) {
			if (entry.getKey().contains("_SKINNED_")) {
				continue;
			}
			Matcher matcher = PET_PRICE.matcher(entry.getKey());
			if (!matcher.matches()) {
				continue;
			}
			String rarity = matcher.group(1);
			String type = matcher.group(2);
			int ownedScore = profile != null ? rules.petScore(profile.bestPets.get(type)) : 0;
			int gain = rules.petScore(rarity) - ownedScore;
			if (gain <= 0) {
				continue;
			}
			String label = "Buy " + TextUtil.titleCase(rarity) + " " + TextUtil.titleCase(type) + " Pet";
			XpTask task = new XpTask("pet:" + rarity + "_" + type, XpCategory.PETS, label, gain * perScore, entry.getValue());
			task.costSource = "Auction House";
			task.stop = "AH";
			task.finish = "PETS";
			task.addBuy("ah", TextUtil.titleCase(type), TextUtil.titleCase(rarity) + " " + TextUtil.titleCase(type));
			task.details.add("Buy from: Auction House (level 1 price)");
			task.details.add("+" + gain + " Pet Score");
			String carried = owned.pets().get(type);
			if (carried != null && rules.petScore(carried) >= rules.petScore(rarity)) {
				markOwned(task, "You're carrying this pet: add it to your Pets menu");
			}
			tasks.add(task);
		}
	}

	// ---------------------------------------------------------------- Consumables

	private void consumables() {
		JsonObject misc = neu.sbObject("miscellaneous_task");
		for (JsonElement element : rules.consumables()) {
			JsonObject rule = element.getAsJsonObject();
			String xpKey = rule.get("xp_key").getAsString();
			String totalKey = rule.get("total_key").getAsString();
			if (!misc.has(xpKey) || !misc.has(totalKey)) {
				continue;
			}
			double xpEach = misc.get(xpKey).getAsDouble();
			int max = (int) Math.round(misc.get(totalKey).getAsDouble() / xpEach);
			String itemId = rule.get("item").getAsString();
			int used = profile != null
				? Math.max(profile.intAt(rule.get("profile_path").getAsString()), profile.consumed.getOrDefault(itemId, 0)) : 0;
			int remaining = max - used;
			if (remaining <= 0) {
				continue;
			}
			PriceBook.Quote quote = book.quote(itemId);
			String verb = rule.has("verb") ? rule.get("verb").getAsString() : "Use";
			XpTask task = new XpTask("consumable:" + itemId, XpCategory.CONSUMABLES,
				verb + " " + remaining + "x " + itemName(itemId), remaining * xpEach, quote.cost() * remaining);
			describeSource(task, quote, itemId);
			task.finish = "USE";
			task.details.add(used + "/" + max + " used, " + TextUtil.xp(xpEach) + " XP each");
			if (profile != null && !profile.knownConsumables.contains(itemId)) {
				task.details.add(0, "Not checked: " + profile.source + " doesn't have this count. Open /skyblockxp > Miscellaneous > Consumable Items to check.");
				task.costSource = task.costSource + " (unchecked)";
			}
			if (owned.ids().contains(itemId)) {
				task.details.add("You're carrying some already");
			}
			tasks.add(task);
		}
	}

	// ---------------------------------------------------------------- Essence shop perks

	/** Perk tiers in the 11 essence shops, paid with essence bought on the bazaar. */
	private void essencePerks() {
		if (neu.essenceShops.size() == 0) {
			return;
		}
		if (profile != null && (!profile.fromApi || profile.essencePerks == null)) {
			warnings.add("Essence perks are hidden until your profile loads, so ones you own aren't suggested.");
			return;
		}
		JsonObject task = neu.sbObject("essence_shop_task");
		List<Integer> xpTable = new ArrayList<>();
		if (task.has("essence_shop_xp")) {
			for (JsonElement element : task.getAsJsonArray("essence_shop_xp")) {
				xpTable.add(element.getAsInt());
			}
		}
		if (xpTable.isEmpty()) {
			xpTable.addAll(List.of(2, 2, 3, 5, 7, 8, 8, 8, 9, 10));
		}
		for (Map.Entry<String, JsonElement> shop : neu.essenceShops.entrySet()) {
			String essenceId = shop.getKey();
			String type = TextUtil.titleCase(essenceId.replace("ESSENCE_", ""));
			PriceBook.Quote quote = book.quote(essenceId);
			JsonObject shopNpcs = rules.object("essence_shop_npcs");
			Npc shopNpc = shopNpcs.has(essenceId) ? neu.findNpcByExactName(shopNpcs.get(essenceId).getAsString()) : null;
			boolean shopKnown = shopNpc != null && shopNpc.hasLocation();
			for (Map.Entry<String, JsonElement> perkEntry : shop.getValue().getAsJsonObject().entrySet()) {
				JsonObject perk = perkEntry.getValue().getAsJsonObject();
				if (!perk.has("costs")) {
					continue;
				}
				String perkName = perk.has("name") ? perk.get("name").getAsString() : TextUtil.titleCase(perkEntry.getKey());
				List<Integer> costs = new ArrayList<>();
				for (JsonElement cost : perk.getAsJsonArray("costs")) {
					costs.add(cost.getAsInt());
				}
				int have = profile != null ? profile.essencePerks.getOrDefault(perkEntry.getKey(), 0) : 0;
				for (int tier = have + 1; tier <= costs.size(); tier++) {
					int xp = tier - 1 < xpTable.size() ? xpTable.get(tier - 1) : xpTable.getLast();
					int essence = costs.get(tier - 1);
					String tierText = costs.size() > 1 ? " " + TextUtil.roman(tier) : "";
					XpTask perkTask = new XpTask("essence:" + perkEntry.getKey() + ":" + tier, XpCategory.ESSENCE,
						"Buy " + perkName + tierText + " (" + type + ")", xp, quote.cost() * essence);
					perkTask.costSource = shopKnown ? "Essence shop: " + shopNpc.name() : "Essence shop";
					perkTask.stop = shopKnown ? "npc:" + shopNpc.id() : "BZ";
					perkTask.npcId = shopKnown ? shopNpc.id() : null;
					perkTask.finish = "ESSENCE";
					perkTask.groupKey = "essence:" + perkEntry.getKey();
					perkTask.groupLabel = "Buy " + perkName + " {} (" + type + ")";
					perkTask.groupStep = TextUtil.roman(tier);
					perkTask.addBuy("bz", type + " Essence", type + " Essence");
					if (tier > have + 1) {
						perkTask.prerequisite = "essence:" + perkEntry.getKey() + ":" + (tier - 1);
					}
					perkTask.details.add("Costs " + TextUtil.amount(essence) + " " + type + " Essence (" + TextUtil.coins(quote.cost()) + " each on the Bazaar)");
					perkTask.materials.put(type + " Essence", (double) essence);
					perkTask.details.add(shopKnown
						? "Upgrade it at " + shopNpc.name() + " (" + neu.islandName(shopNpc.island()) + "), the " + type + " Essence Shop"
						: "Upgrade it in the " + type + " Essence Shop");
					tasks.add(perkTask);
				}
			}
		}
	}

	// ---------------------------------------------------------------- Misc: Jacobus and bank upgrades

	/** Accessory bag upgrades from Jacobus: +2 XP each, priced by how many you have bought. */
	private void bagUpgrades() {
		JsonObject config = rules.object("accessory_bag_upgrades");
		if (profile == null || profile.bagUpgrades < 0 || !config.has("price_brackets")) {
			return;
		}
		JsonObject misc = neu.sbObject("miscellaneous_task");
		double xp = misc.has("accessory_bag_upgrades_xp") ? misc.get("accessory_bag_upgrades_xp").getAsDouble() : 2;
		int max = config.has("max") ? config.get("max").getAsInt() : 99;
		Npc jacobus = neu.findNpcByExactName(config.has("npc") ? config.get("npc").getAsString() : "Jacobus");
		for (int n = profile.bagUpgrades + 1; n <= max; n++) {
			double coins = Double.NaN;
			for (JsonElement bracket : config.getAsJsonArray("price_brackets")) {
				if (n <= bracket.getAsJsonObject().get("upTo").getAsInt()) {
					coins = bracket.getAsJsonObject().get("coins").getAsDouble();
					break;
				}
			}
			XpTask task = new XpTask("bagupgrade:" + n, XpCategory.MISC, "Accessory Bag upgrade #" + n, xp, coins);
			task.costSource = "Coins";
			task.finish = "BUY";
			if (jacobus != null && jacobus.hasLocation()) {
				task.stop = "npc:" + jacobus.id();
				task.npcId = jacobus.id();
				task.details.add("Buy from " + jacobus.name() + " in " + neu.islandName(jacobus.island()) + ": +2 accessory slots");
			}
			task.groupKey = "bagupgrade";
			task.groupLabel = "Accessory Bag upgrades {}";
			task.groupStep = "#" + n;
			if (n > profile.bagUpgrades + 1) {
				task.prerequisite = "bagupgrade:" + (n - 1);
			}
			tasks.add(task);
		}
	}

	/** Bank account upgrades at any Banker. Only shown once we know which ones you have. */
	private void bankUpgrades() {
		if (profile == null || profile.bankUpgrades == null) {
			return;
		}
		JsonObject core = neu.sbObject("core_task");
		JsonObject xpTable = core.has("bank_upgrades_xp") ? core.getAsJsonObject("bank_upgrades_xp") : new JsonObject();
		Npc banker = null;
		for (Npc npc : neu.searchNpcs("Banker")) {
			if (npc.hasLocation() && (banker == null || "hub".equals(npc.island()))) {
				banker = npc;
			}
		}
		String previous = null;
		for (JsonElement element : rules.array("bank_upgrades")) {
			JsonObject upgrade = element.getAsJsonObject();
			String key = upgrade.get("key").getAsString();
			if (profile.bankUpgrades.contains(key)) {
				continue;
			}
			XpTask task = new XpTask("bank:" + key, XpCategory.MISC, "Upgrade to " + upgrade.get("name").getAsString(),
				xpTable.has(key) ? xpTable.get(key).getAsDouble() : 0, 0);
			double cost = upgrade.get("coins").getAsDouble();
			List<String> itemLines = new ArrayList<>();
			if (upgrade.has("items")) {
				for (Map.Entry<String, JsonElement> item : upgrade.getAsJsonObject("items").entrySet()) {
					PriceBook.Quote quote = book.quote(item.getKey());
					int amount = item.getValue().getAsInt();
					cost += quote.cost() * amount;
					addBuy(task, quote, item.getKey());
					itemLines.add("  " + amount + "x " + itemName(item.getKey()) + ": " + TextUtil.coins(quote.cost() * amount));
					task.materials.merge(itemName(item.getKey()), (double) amount, Double::sum);
				}
			}
			task.cost = cost;
			task.costSource = "Coins + items";
			task.finish = "BUY";
			task.details.add(TextUtil.coins(upgrade.get("coins").getAsDouble()) + " coins plus:");
			task.details.addAll(itemLines);
			long goldNeeded = upgrade.has("gold_collection") ? upgrade.get("gold_collection").getAsLong() : 0;
			int milestone = upgrade.has("museum_milestone") ? upgrade.get("museum_milestone").getAsInt() : 0;
			task.details.add("Needs " + TextUtil.coins(goldNeeded) + " Gold collection" + (milestone > 0 ? " and Museum Milestone " + milestone : ""));
			if (profile.collectionAmounts != null && profile.collectionAmounts.getOrDefault("GOLD_INGOT", 0L) < goldNeeded) {
				task.lockedReason = "Needs " + TextUtil.coins(goldNeeded) + " Gold collection (you have "
					+ TextUtil.coins(profile.collectionAmounts.getOrDefault("GOLD_INGOT", 0L)) + ")";
			}
			if (banker != null) {
				task.stop = "npc:" + banker.id();
				task.npcId = banker.id();
				task.details.add("Upgrade at a Banker (" + banker.name() + ", " + neu.islandName(banker.island()) + ")");
			}
			task.prerequisite = previous;
			previous = task.id;
			tasks.add(task);
		}
	}

	// ---------------------------------------------------------------- helpers

	/** No coin price, and the only way to get it is a recipe you haven't unlocked: that's a lock, not "unpriced". */
	private void lockIfOnlyCraftable(XpTask task, PriceBook.Quote quote, String itemId) {
		if (!quote.priced() && neu.craftRecipes.containsKey(itemId)) {
			task.lockedReason = unlocks.craftBlocker(itemId);
		}
	}

	/** You already carry what this needs: it costs nothing. */
	private static void markOwned(XpTask task, String detail) {
		task.lockedReason = null;
		task.cost = 0;
		task.owned = true;
		task.partialPrice = false;
		task.costSource = "In your inventory";
		task.stop = "OWNED";
		task.details.addFirst(detail);
	}

	/** Adds the [Bazaar] / [AH] / [Recipe] button for buying an item from wherever it's cheapest. */
	private void addBuy(XpTask task, PriceBook.Quote quote, String itemId) {
		String name = itemName(itemId);
		switch (quote.source()) {
			case BAZAAR -> task.addBuy("bz", name, name);
			case AUCTION -> task.addBuy("ah", name, name);
			case CRAFT -> task.addBuy("recipe", itemId, name);
			default -> {
			}
		}
	}

	private void describeSource(XpTask task, PriceBook.Quote quote, String itemId) {
		addBuy(task, quote, itemId);
		task.costSource = quote.source().label;
		task.stop = switch (quote.source()) {
			case BAZAAR -> "BZ";
			case AUCTION -> "AH";
			case CRAFT -> "CRAFT";
			case NPC_SHOP -> "npc:" + quote.npcId();
			default -> "";
		};
		Npc chosen = quote.npcId() != null ? neu.npcs.get(quote.npcId()) : null;
		if (chosen != null) {
			task.npcId = chosen.id();
			task.costSource = "NPC: " + chosen.name();
			task.details.add("Buy from " + chosen.name() + " in " + neu.islandName(chosen.island()) + " (" + TextUtil.coins(quote.cost()) + ")");
			if (!chosen.lore().isEmpty()) {
				task.details.add("  " + String.join(" ", chosen.lore()));
			}
		} else if (quote.source() == PriceBook.Source.CRAFT) {
			NeuRepo.Recipe recipe = neu.craftRecipes.get(itemId);
			String unlock = neu.craftText.get(itemId);
			task.details.add("Cheapest to craft" + (unlock != null ? " (" + unlock + ")" : "") + ":");
			if (recipe != null) {
				for (Map.Entry<String, Double> input : recipe.inputs().entrySet()) {
					task.details.add("  " + fmtCount(input.getValue()) + "x " + itemName(input.getKey()));
				}
				if (recipe.outputCount() > 1) {
					task.details.add("  makes " + recipe.outputCount());
				}
			}
		} else {
			task.details.add("Buy from: " + sourceText(quote));
		}
		// Mention NPC shops even when they are not the cheapest, so you know who sells it and where.
		int listed = 0;
		for (NeuRepo.ShopOffer offer : neu.offersByItem.getOrDefault(itemId, List.of())) {
			Npc npc = neu.npcs.get(offer.npcId());
			if (npc == null || npc == chosen || listed >= 3) {
				continue;
			}
			listed++;
			double price = 0;
			for (Map.Entry<String, Double> cost : offer.costs().entrySet()) {
				price += book.quote(cost.getKey()).cost() * cost.getValue();
			}
			task.details.add("Also sold by " + npc.name() + " in " + neu.islandName(npc.island()) + " for ~" + TextUtil.coins(price / offer.resultCount()));
			if (task.npcId == null && npc.hasLocation()) {
				task.npcId = npc.id();
			}
		}
	}

	private String sourceText(PriceBook.Quote quote) {
		if (quote.npcId() != null) {
			Npc npc = neu.npcs.get(quote.npcId());
			return "NPC " + (npc != null ? npc.name() : quote.npcId());
		}
		return quote.source().label;
	}

	private String itemName(String id) {
		String name = items.name(id);
		return name != null ? name : neu.name(id);
	}

	private static String fmtCount(double count) {
		return count == Math.floor(count) ? String.valueOf((long) count) : String.valueOf(count);
	}
}
