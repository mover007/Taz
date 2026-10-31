package com.taz.general.pv;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What an item adds to a networth, worked out the way SkyHelper-Networth does (MIT, github.com/Altpapier/SkyHelper-Networth,
 * the networth SkyCrypt and SkyHelper show): its lowest BIN, plus what its upgrades cost (enchantments, potato books, stars,
 * master stars, gemstones, scrolls, recombobulator, reforge stone and the rest), each counted at the share SkyHelper counts.
 * Taz prices those with SkyCofl's lowest BINs and Hypixel's bazaar instead of SkyHelper's price list. It doesn't open New Year
 * Cake Bags, so the cakes inside don't count.
 */
final class ItemWorth {
	/** A price by id, 0 when unknown. */
	interface Prices {
		double get(String id);
	}

	private static final Map<String, Double> WORTH = Map.ofEntries(Map.entry("enrichment", 0.5), Map.entry("farmingForDummies", 0.5),
			Map.entry("overclocker3000", 0.9), Map.entry("gemstonePowerScroll", 0.5), Map.entry("woodSingularity", 0.5),
			Map.entry("artOfWar", 0.6), Map.entry("fumingPotatoBook", 0.6), Map.entry("gemstoneSlots", 0.6), Map.entry("runes", 0.6),
			Map.entry("tunedTransmission", 0.7), Map.entry("pocketSackInASack", 0.7), Map.entry("essence", 0.75), Map.entry("silex", 0.75),
			Map.entry("artOfPeace", 0.8), Map.entry("divanPowderCoating", 0.8), Map.entry("enchantmentUpgrades", 0.8),
			Map.entry("jalapenoBook", 0.8), Map.entry("manaDisintegrator", 0.8), Map.entry("recombobulator", 0.8),
			Map.entry("thunderInABottle", 0.8), Map.entry("enchantments", 0.85), Map.entry("shensAuctionPrice", 0.85),
			Map.entry("booster", 0.8), Map.entry("dye", 0.9), Map.entry("gemstoneChambers", 0.9), Map.entry("soulboundSkins", 0.8));
	private static final Map<String, Double> ENCHANTMENT_WORTH = Map.of("COUNTER_STRIKE", 0.2, "BIG_BRAIN", 0.35, "ULTIMATE_INFERNO", 0.35,
			"OVERLOAD", 0.35, "ULTIMATE_SOUL_EATER", 0.35, "ULTIMATE_FATAL_TEMPO", 0.65);
	private static final Map<String, Set<String>> BLOCKED_ENCHANTMENTS = Map.of("BONE_BOOMERANG", Set.of("OVERLOAD", "POWER", "ULTIMATE_SOUL_EATER"),
			"DEATH_BOW", Set.of("OVERLOAD", "POWER", "ULTIMATE_SOUL_EATER"), "GARDENING_AXE", Set.of("REPLENISH"),
			"GARDENING_HOE", Set.of("REPLENISH"), "ADVANCED_GARDENING_AXE", Set.of("REPLENISH"), "ADVANCED_GARDENING_HOE", Set.of("REPLENISH"));
	private static final Set<String> STACKING = Set.of("EXPERTISE", "COMPACT", "ABSORB", "CULTIVATING", "CHAMPION", "HECATOMB", "TOXOPHILITE");
	private static final Set<String> IGNORE_SILEX = Set.of("PROMISING_SPADE", "PROMISING_AXE");
	private static final String[] MASTER_STARS = {"FIRST_MASTER_STAR", "SECOND_MASTER_STAR", "THIRD_MASTER_STAR", "FOURTH_MASTER_STAR", "FIFTH_MASTER_STAR"};
	private static final Set<String> RECOMB_CATEGORIES = Set.of("ACCESSORY", "NECKLACE", "GLOVES", "BRACELET", "BELT", "CLOAK", "VACUUM");
	private static final Set<String> RECOMB_IDS = Set.of("DIVAN_HELMET", "DIVAN_CHESTPLATE", "DIVAN_LEGGINGS", "DIVAN_BOOTS", "FERMENTO_HELMET",
			"FERMENTO_CHESTPLATE", "FERMENTO_LEGGINGS", "FERMENTO_BOOTS", "SHADOW_ASSASSIN_CLOAK", "STARRED_SHADOW_ASSASSIN_CLOAK");
	private static final List<String> GEMSTONE_SLOTS = List.of("COMBAT", "OFFENSIVE", "DEFENSIVE", "MINING", "UNIVERSAL", "CHISEL");
	private static final Set<String> NOT_COSMETIC = Set.of("ANCIENT_ELEVATOR", "BEDROCK", "CREATIVE_MIND", "DCTR_SPACE_HELM", "DEAD_BUSH_OF_LOVE",
			"DUECES_BUILDER_CLAY", "GAME_BREAKER", "POTATO_BASKET");
	private static final String[] TIERS = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "VERY_SPECIAL", "ULTIMATE"};
	private static final Pattern KUUDRA_ARMOR = Pattern.compile("^((HOT|FIERY|BURNING|INFERNAL)_)?(AURORA|CRIMSON|TERROR|HOLLOW|FERVOR)_(HELMET|CHESTPLATE|LEGGINGS|BOOTS)$");
	private static final Pattern DIVANS = Pattern.compile("^DIVAN_(HELMET|CHESTPLATE|LEGGINGS|BOOTS|PENDANT)$");
	private static final Map<String, List<String[]>> ENCHANTMENT_UPGRADES = new HashMap<>();
	private static final Map<String, String> REFORGES = new HashMap<>();
	private static final Map<String, List<String>> PRESTIGES = new HashMap<>();
	private static final Map<String, Object[]> MIDAS = Map.of("MIDAS_SWORD", new Object[]{50_000_000L, "MIDAS_SWORD_50M"},
			"STARRED_MIDAS_SWORD", new Object[]{250_000_000L, "STARRED_MIDAS_SWORD_250M"}, "MIDAS_STAFF", new Object[]{100_000_000L, "MIDAS_STAFF_100M"},
			"STARRED_MIDAS_STAFF", new Object[]{500_000_000L, "STARRED_MIDAS_STAFF_500M"});

	static {
		String[][] upgrades = {{"SCAVENGER", "GOLDEN_BOUNTY", "6"}, {"PESTERMINATOR", "PESTHUNTING_GUIDE", "6"}, {"LUCK_OF_THE_SEA", "GOLD_BOTTLE_CAP", "7"},
				{"PISCARY", "TROUBLED_BUBBLE", "7"}, {"FRAIL", "SEVERED_PINCER", "7"}, {"SPIKED_HOOK", "OCTOPUS_TENDRIL", "7"}, {"CHARM", "CHAIN_END_TIMES", "6"},
				{"SMITE", "SEVERED_HAND", "7"}, {"ENDER_SLAYER", "ENDSTONE_IDOL", "7"}, {"BANE_OF_ARTHROPODS", "ENSNARED_SNAIL", "7"},
				{"VENOMOUS", "FATEFUL_STINGER", "7"}, {"THORNS", "PRICKLY_CREEPER", "4"}, {"SCUBA", "VIBRANT_CORAL", "6"},
				{"FOREST_PLEDGE", "WATER_HYACINTH", "6"}, {"KARMA", "FORESTS_FAVOR", "6"}, {"STEALTH", "DISTANT_ECHO", "6"}};
		for (String[] u : upgrades) ENCHANTMENT_UPGRADES.put(u[0], List.<String[]>of(new String[]{u[1], u[2], "0"}));
		List<String[]> turbo = List.of(new String[]{"TURBO_GOURD", "6", "1"}, new String[]{"ENCHANTED_TURBO_GOURD", "7", "1"});
		for (String crop : new String[]{"WHEAT", "CARROT", "POTATO", "PUMPKIN", "MELON", "MUSHROOMS", "COCOA", "CACTI", "CANE", "WARTS", "SUNFLOWER", "MOONFLOWER", "ROSE"}) {
			ENCHANTMENT_UPGRADES.put("TURBO_" + crop, turbo);
		}
		String[] reforges = {"stiff", "HARDENED_WOOD", "trashy", "OVERFLOWING_TRASH_CAN", "salty", "SALT_CUBE", "aote_stone", "AOTE_STONE", "blazing",
				"BLAZEN_SPHERE", "waxed", "BLAZE_WAX", "rooted", "BURROWING_SPORES", "calcified", "CALCIFIED_HEART", "candied", "CANDY_CORN", "perfect",
				"DIAMOND_ATOM", "fleet", "DIAMONITE", "fabled", "DRAGON_CLAW", "spiked", "DRAGON_SCALE", "royal", "DWARVEN_TREASURE", "warped",
				"ENDSTONE_GEODE", "coldfusion", "ENTROPY_SUPPRESSOR", "blooming", "FLOWERING_BOUQUET", "fanged", "FULL_JAW_FANGING_KIT", "jaded", "JADERALD",
				"jerry_stone", "JERRY_STONE", "magnetic", "LAPIS_CRYSTAL", "earthy", "LARGE_WALNUT", "groovy", "MANGROVE_GEM", "fortified", "METEOR_SHARD",
				"gilded", "MIDAS_JEWEL", "cubic", "MOLTEN_CUBE", "moonglade", "MOONGLADE_JEWEL", "lunar", "MOONSTONE", "necrotic", "NECROMANCER_BROOCH",
				"fruitful", "ONYX", "precise", "OPTICAL_LENS", "mossy", "OVERGROWN_GRASS", "pitchin", "PITCHIN_KOI", "undead", "PREMIUM_FLESH",
				"blood_soaked", "PRESUMED_GALLON_OF_RED_PAINT", "mithraic", "PURE_MITHRIL", "reinforced", "RARE_DIAMOND", "ridiculous", "RED_NOSE",
				"loving", "RED_SCARF", "auspicious", "ROCK_GEMSTONE", "treacherous", "RUSTY_ANCHOR", "headstrong", "SALMON_OPAL", "strengthened",
				"SEARING_STONE", "glistening", "SHINY_PRISM", "bustling", "SKYMART_BROCHURE", "spiritual", "SPIRIT_DECOY", "squeaky", "SQUEAKY_TOY",
				"sunny", "SUNSTONE", "suspicious", "SUSPICIOUS_VIAL", "snowy", "TERRY_SNOWGLOBE", "dimensional", "TITANIUM_TESSERACT", "ambered",
				"AMBER_MATERIAL", "beady", "BEADY_EYES", "blessed", "BLESSED_FRUIT", "bulky", "BULKY_STONE", "buzzing", "CLIPPED_WINGS", "erudite",
				"DAEDALUS_NOTES", "submerged", "DEEP_SEA_ORB", "renowned", "DRAGON_HORN", "festive", "FROZEN_BAUBLE", "giant", "GIANT_TOOTH", "lustrous",
				"GLEAMING_CRYSTAL", "bountiful", "GOLDEN_BALL", "chomp", "KUUDRA_MANDIBLE", "lucky", "LUCKY_DICE", "mantid", "MANTID_CLAW", "stellar",
				"PETRIFIED_STARFALL", "scraped", "POCKET_ICEBERG", "ancient", "PRECURSOR_GEAR", "refined", "REFINED_AMBER", "empowered", "SADAN_BROOCH",
				"withered", "WITHER_BLOOD", "glacial", "FRIGID_HUSK", "heated", "HOT_STUFF", "blood_shot", "SHRIVELED_CORNEA", "overpriced",
				"OVERPRICED_DRINK", "deep_fried", "HASHBROWN", "geometric", "GEOMETRIC_ODDITY", "thorny", "BLOOMING_THORNS", "sticky",
				"EXTREMELY_MILD_ADHESIVE", "majestic", "MORNING_DEW", "marshy", "MARSHROOM", "dirty", "DIRT_BOTTLE", "moil", "MOIL_LOG", "toil",
				"TOIL_LOG", "greater_spook", "BOO_STONE"};
		for (int i = 0; i < reforges.length; i += 2) REFORGES.put(reforges[i], reforges[i + 1]);
		// Kuudra armor: a higher tier was made from every tier below it.
		String[] ranks = {"", "HOT_", "BURNING_", "FIERY_", "INFERNAL_"};
		for (String set : new String[]{"CRIMSON", "TERROR", "FERVOR", "HOLLOW", "AURORA"}) {
			for (String piece : new String[]{"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"}) {
				for (int r = 1; r < ranks.length; r++) {
					List<String> below = new ArrayList<>();
					for (int b = r - 1; b >= 0; b--) below.add(ranks[b] + set + "_" + piece);
					PRESTIGES.put(ranks[r] + set + "_" + piece, below);
				}
			}
		}
	}

	/** An item's worth with and without cosmetics, and whether it's cosmetic or soulbound. */
	record Worth(double total, double nonCosmetic, boolean cosmetic, boolean soulbound) {
	}

	private final CompoundTag extra;
	private final List<String> lore = new ArrayList<>();
	private final String name, baseId;
	private final JsonObject sb;
	private final int count;
	private final Prices p;
	private final Map<String, JsonObject> items;
	private boolean noCosmetics;
	private String itemId;
	private double base, price;

	private ItemWorth(CompoundTag item, Prices prices, Map<String, JsonObject> items) {
		CompoundTag tag = item.getCompoundOrEmpty("tag"), display = tag.getCompoundOrEmpty("display");
		this.extra = tag.getCompoundOrEmpty("ExtraAttributes");
		ListTag lines = display.getListOrEmpty("Lore");
		for (int i = 0; i < lines.size(); i++) lore.add(lines.getStringOr(i, ""));
		this.name = ProfileItems.plain(display.getStringOr("Name", ""));
		this.baseId = extra.getStringOr("id", "");
		this.items = items;
		this.sb = items.getOrDefault(baseId, new JsonObject());
		this.count = Math.max(1, item.getByteOr("Count", (byte) 1));
		this.p = prices;
	}

	/**
	 * The item's worth: SkyHelper's way, but at least SkyCofl's median sale for items like it ({@code estimate}, 0 when
	 * there's none), which also prices what SkyHelper's own list knows and Taz's doesn't (skins, shiny items, coins in a
	 * Crown of Avarice).
	 */
	static Worth of(CompoundTag item, Prices prices, Map<String, JsonObject> items, double estimate) {
		ItemWorth w = new ItemWorth(item, prices, items);
		boolean cosmetic = w.cosmetic();
		double total = Math.max(w.calculate(false), estimate);
		double plain = 0;
		if (!cosmetic) {
			plain = w.calculate(true);
			// SkyCofl's estimate includes a skin or dye, which the non-cosmetic networth leaves out.
			if (!w.extra.contains("skin") && !w.extra.contains("dye_item")) plain = Math.max(plain, estimate);
		}
		return new Worth(total, plain, cosmetic, w.soulbound());
	}

	boolean cosmetic() {
		String test = (baseId + name).toUpperCase(Locale.ROOT);
		String last = lore.isEmpty() ? "" : lore.get(lore.size() - 1);
		String category = Json.str(sb, "category");
		return category.equals("COSMETIC") || last.contains("COSMETIC") || test.contains("DYE") || test.contains("SKIN") || category.equals("MEMENTO")
				|| NOT_COSMETIC.contains(baseId) || rune();
	}

	boolean soulbound() {
		return extra.contains("donated_museum") || lore.contains("§8§l* §8Co-op Soulbound §8§l*") || lore.contains("§8§l* §8Soulbound §8§l*");
	}

	private boolean rune() {
		return (baseId.equals("RUNE") || baseId.equals("UNIQUE_RUNE")) && !extra.getCompoundOrEmpty("runes").isEmpty();
	}

	/** The id whose price is the base: skinned, shiny or editioned variants, runes and cakes have their own. */
	private String priceId() {
		String id = baseId;
		String skin = extra.getStringOr("skin", "");
		if (!skin.isEmpty() && !noCosmetics && p.get(id + "_SKINNED_" + skin) > p.get(id)) return id + "_SKINNED_" + skin;
		if (id.equals("PARTY_HAT_SLOTH") && extra.contains("party_hat_emoji") && p.get(id + "_" + upper(str("party_hat_emoji"))) > 0) {
			return id + "_" + upper(str("party_hat_emoji"));
		}
		if (rune() && !noCosmetics) {
			CompoundTag runes = extra.getCompoundOrEmpty("runes");
			String type = runes.keySet().iterator().next();
			return upper("RUNE_" + type + "_" + (int) num(runes, type));
		}
		if (id.equals("NEW_YEAR_CAKE")) return "NEW_YEAR_CAKE_" + (int) num(extra, "new_years_cake");
		if (Set.of("PARTY_HAT_CRAB", "PARTY_HAT_CRAB_ANIMATED", "BALLOON_HAT_2024", "BALLOON_HAT_2025").contains(id) && extra.contains("party_hat_color")) {
			return id + "_" + upper(str("party_hat_color"));
		}
		if (id.equals("DCTR_SPACE_HELM") && extra.contains("edition")) return "DCTR_SPACE_HELM_EDITIONED";
		if (id.equals("CREATIVE_MIND") && !extra.contains("edition")) return "CREATIVE_MIND_UNEDITIONED";
		if (id.equals("ANCIENT_ELEVATOR") && extra.contains("edition")) return "ANCIENT_ELEVATOR_EDITIONED";
		if (num(extra, "is_shiny") > 0 && p.get(id + "_SHINY") > 0) return id + "_SHINY";
		if (id.startsWith("STARRED_") && p.get(id) <= 0 && p.get(id.substring(8)) > 0) return id.substring(8);
		return id;
	}

	private double calculate(boolean noCosmetics) {
		this.noCosmetics = noCosmetics;
		itemId = priceId();
		base = p.get(itemId) * count;
		price = 0;
		pickonimbus();
		prestige();
		shensAuction();
		midas();
		avarice();
		enchantedBook();
		enchantments();
		add("POCKET_SACK_IN_A_SACK", num(extra, "sack_pss"), "pocketSackInASack");
		add("WOOD_SINGULARITY", num(extra, "wood_singularity_count"), "woodSingularity");
		add("JALAPENO_BOOK", num(extra, "jalapeno_count"), "jalapenoBook");
		add("TRANSMISSION_TUNER", num(extra, "tuned_transmission"), "tunedTransmission");
		add("MANA_DISINTEGRATOR", num(extra, "mana_disintegrator_count"), "manaDisintegrator");
		if (baseId.equals("PULSE_RING") && num(extra, "thunder_charge") > 0) {
			add("THUNDER_IN_A_BOTTLE", Math.floor(Math.min(num(extra, "thunder_charge"), 5_000_000) / 50_000), "thunderInABottle");
		}
		if (!noCosmetics && !extra.getCompoundOrEmpty("runes").isEmpty() && !baseId.startsWith("RUNE")) {
			CompoundTag runes = extra.getCompoundOrEmpty("runes");
			String type = runes.keySet().iterator().next();
			add(upper("RUNE_" + type + "_" + (int) num(runes, type)), 1, "runes");
		}
		potatoBooks();
		if (!noCosmetics && extra.contains("dye_item")) add(upper(str("dye_item")), 1, "dye");
		add("THE_ART_OF_WAR", num(extra, "art_of_war_count"), "artOfWar");
		add("THE_ART_OF_PEACE", num(extra, "artOfPeaceApplied"), "artOfPeace");
		add("FARMING_FOR_DUMMIES", num(extra, "farming_for_dummies_count"), "farmingForDummies");
		add("POLARVOID_BOOK", num(extra, "polarvoid"), null);
		if (num(extra, "divan_powder_coating") > 0) add("DIVAN_POWDER_COATING", 1, "divanPowderCoating");
		enrichment();
		recombobulator();
		gemstones();
		if (extra.contains("power_ability_scroll")) add(str("power_ability_scroll"), 1, "gemstonePowerScroll");
		if (extra.contains("modifier") && !Json.str(sb, "category").equals("ACCESSORY")) {
			String stone = REFORGES.get(str("modifier"));
			if (stone != null) add(stone, 1, null);
		}
		stars();
		ListTag scrolls = extra.getListOrEmpty("ability_scroll");
		for (int i = 0; i < scrolls.size(); i++) add(upper(scrolls.getStringOr(i, "")), 1, null);
		for (String part : new String[]{"drill_part_upgrade_module", "drill_part_fuel_tank", "drill_part_engine"}) {
			if (extra.contains(part)) add(upper(str(part)), 1, null);
		}
		if (extra.contains("ethermerge")) add("ETHERWARP_CONDUIT", 1, null);
		if (!noCosmetics && extra.contains("skin") && !baseId.contains(str("skin")) && soulbound()) add(str("skin"), 1, "soulboundSkins");
		for (String part : new String[]{"line", "hook", "sinker"}) {
			String id = extra.getCompoundOrEmpty(part).getStringOr("part", "");
			if (!id.isEmpty()) add(upper(id), 1, null);
		}
		boosters();
		add("OVERCLOCKER_3000", num(extra, "levelable_overclocks"), "overclocker3000");
		return base + price;
	}

	// ---- the handlers, in SkyHelper's order

	private void pickonimbus() {
		if (baseId.equals("PICKONIMBUS") && extra.contains("pickonimbus_durability") && num(extra, "pickonimbus_durability") < 2000) {
			price += base * (num(extra, "pickonimbus_durability") / 2000 - 1);
		}
	}

	private void prestige() {
		List<String> below = PRESTIGES.get(baseId);
		if (below == null || p.get(baseId) > 0) return;
		for (String lower : below) {
			JsonObject found = items.get(lower);
			if (found != null) {
				price += starCosts(Json.arr(found, "upgrade_costs"), false);
				price += starCosts(Json.arr(found, "prestige", "costs"), false);
			}
			if (p.get(lower) > 0) {
				price += p.get(lower);
				break;
			}
		}
	}

	private void shensAuction() {
		if (extra.contains("price") && extra.contains("auction") && extra.contains("bid")) {
			double paid = num(extra, "price") * WORTH.get("shensAuctionPrice");
			if (paid > base) base = paid;
		}
	}

	private void midas() {
		Object[] midas = MIDAS.get(baseId);
		if (midas == null) return;
		if (num(extra, "winning_bid") + num(extra, "additional_coins") >= (long) midas[0] && p.get((String) midas[1]) > 0) base = p.get((String) midas[1]);
	}

	private void avarice() {
		double coins = num(extra, "collected_coins");
		if (coins <= 0) return;
		double zero = p.get("CROWN_OF_AVARICE"), billion = p.get("CROWN_OF_AVARICE_1B");
		// Taz's prices have no 1B crown: then SkyCofl's estimate for this crown says what the coins add.
		if (billion > 0) base = zero + (billion - zero) * Math.min(coins, 1e9) / 1e9;
	}

	private void enchantedBook() {
		if (!baseId.equals("ENCHANTED_BOOK")) return;
		CompoundTag enchants = extra.getCompoundOrEmpty("enchantments");
		if (enchants.isEmpty()) return;
		boolean single = enchants.size() == 1;
		double sum = 0;
		for (String e : enchants.keySet()) {
			double v = p.get("ENCHANTMENT_" + upper(e) + "_" + (int) num(enchants, e));
			sum += v * (single ? 1 : WORTH.get("enchantments"));
		}
		if (sum > 0) base = sum;
	}

	private void enchantments() {
		CompoundTag enchants = extra.getCompoundOrEmpty("enchantments");
		if (baseId.equals("ENCHANTED_BOOK") || enchants.isEmpty()) return;
		Set<String> once = new HashSet<>();
		for (String key : enchants.keySet()) {
			String e = upper(key);
			int level = (int) num(enchants, key);
			if (BLOCKED_ENCHANTMENTS.getOrDefault(baseId, Set.of()).contains(e)) continue;
			if (e.equals("SCAVENGER") && level == 5) continue;
			if (STACKING.contains(e)) level = 1;
			if (e.equals("EFFICIENCY") && level >= 6 && !IGNORE_SILEX.contains(baseId)) {
				int silex = level - (baseId.equals("STONK_PICKAXE") ? 6 : 5);
				if (silex > 0) add("SIL_EX", silex, "silex");
			}
			for (String[] u : ENCHANTMENT_UPGRADES.getOrDefault(e, List.of())) {
				boolean perItem = u[2].equals("1");
				if (level >= Integer.parseInt(u[1]) && (!perItem || once.add(u[0]))) add(u[0], 1, "enchantmentUpgrades");
			}
			price += p.get("ENCHANTMENT_" + e + "_" + level) * ENCHANTMENT_WORTH.getOrDefault(e, WORTH.get("enchantments"));
		}
	}

	private void potatoBooks() {
		int books = (int) num(extra, "hot_potato_count");
		if (books <= 0) return;
		add("HOT_POTATO_BOOK", Math.min(books, 10), null);
		if (books > 10) add("FUMING_POTATO_BOOK", books - 10, "fumingPotatoBook");
	}

	private void enrichment() {
		if (!extra.contains("talisman_enrichment")) return;
		double cheapest = Double.MAX_VALUE;
		for (String e : new String[]{"CRITICAL_CHANCE", "CRITICAL_DAMAGE", "DEFENSE", "HEALTH", "INTELLIGENCE", "MAGIC_FIND", "WALK_SPEED", "STRENGTH",
				"ATTACK_SPEED", "FEROCITY", "SEA_CREATURE_CHANCE"}) {
			double v = p.get("TALISMAN_ENRICHMENT_" + e);
			if (v > 0) cheapest = Math.min(cheapest, v);
		}
		if (cheapest < Double.MAX_VALUE) price += cheapest * WORTH.get("enrichment");
	}

	private void recombobulator() {
		boolean recombobulated = num(extra, "rarity_upgrades") > 0 && !extra.contains("item_tier");
		if (!recombobulated) return;
		String last = lore.isEmpty() ? "" : lore.get(lore.size() - 1);
		boolean allowed = !extra.getCompoundOrEmpty("enchantments").isEmpty() || RECOMB_CATEGORIES.contains(Json.str(sb, "category"))
				|| RECOMB_IDS.contains(baseId) || last.contains("ACCESSORY") || last.contains("HATCESSORY");
		if (allowed) price += p.get("RECOMBOBULATOR_3000") * WORTH.get("recombobulator") * (baseId.equals("BONE_BOOMERANG") ? 0.5 : 1);
	}

	private void gemstones() {
		CompoundTag gems = extra.getCompoundOrEmpty("gems").copy();
		JsonArray slots = Json.arr(sb, "gemstone_slots");
		if (gems.isEmpty() || slots.isEmpty()) return;
		List<String> unlockedSlots = new ArrayList<>();
		List<String> unlocked = new ArrayList<>();
		ListTag list = gems.getListOrEmpty("unlocked_slots");
		boolean hasUnlocked = gems.contains("unlocked_slots");
		for (int i = 0; i < list.size(); i++) unlocked.add(list.getStringOr(i, ""));
		List<String[]> found = new ArrayList<>();
		for (JsonElement slot : slots) {
			String type = Json.str(slot, "slot_type");
			boolean costs = !Json.arr(slot, "costs").isEmpty();
			if (costs && hasUnlocked) {
				for (int i = 0; i < unlocked.size(); i++) {
					if (unlocked.get(i).startsWith(type)) {
						unlockedSlots.add(type);
						unlocked.remove(i);
						break;
					}
				}
			}
			if (!costs) unlockedSlots.add(type);
			String key = null;
			for (String k : gems.keySet()) {
				if (k.startsWith(type) && !k.endsWith("_gem") && !k.equals("unlocked_slots")) {
					key = k;
					break;
				}
			}
			if (key != null) {
				String gemType = GEMSTONE_SLOTS.contains(type) ? gems.getStringOr(key + "_gem", "") : type;
				Tag value = gems.get(key);
				String tier = value instanceof CompoundTag c ? c.getStringOr("quality", "") : value == null ? "" : value.asString().orElse("");
				found.add(new String[]{gemType, tier});
				gems.remove(key);
				if (costs && !hasUnlocked) unlockedSlots.add(type);
			}
		}
		boolean divans = DIVANS.matcher(baseId).matches();
		if (divans || KUUDRA_ARMOR.matcher(baseId).matches()) {
			double share = WORTH.get(divans ? "gemstoneChambers" : "gemstoneSlots");
			List<JsonElement> left = new ArrayList<>();
			slots.forEach(left::add);
			for (String u : unlockedSlots) {
				for (int i = 0; i < left.size(); i++) {
					if (!Json.str(left.get(i), "slot_type").equals(u)) continue;
					double total = 0;
					for (JsonElement cost : Json.arr(left.get(i), "costs")) {
						if (Json.str(cost, "type").equals("COINS")) total += Json.num(cost, "coins");
						else if (Json.str(cost, "type").equals("ITEM")) total += p.get(upper(Json.str(cost, "item_id"))) * Json.num(cost, "amount");
					}
					price += total * share;
					left.remove(i);
					break;
				}
			}
		}
		for (String[] g : found) price += p.get(upper(g[1] + "_" + g[0] + "_GEM"));
	}

	private void stars() {
		JsonArray costs = Json.arr(sb, "upgrade_costs");
		if (costs.isEmpty()) return;
		int level = Math.max(digits("dungeon_item_level"), digits("upgrade_level"));
		if (level > 5 && costs.size() <= 5) {
			for (int s = 0; s < Math.min(level - 5, 5); s++) price += p.get(MASTER_STARS[s]);
		}
		if (level > 0) {
			JsonArray used = new JsonArray();
			for (int i = 0; i < Math.min(level, costs.size()); i++) used.add(costs.get(i));
			price += starCosts(used, true);
		}
	}

	/** What stars or a prestige cost: essence at 75%, items in full. */
	private double starCosts(JsonArray upgrades, boolean stars) {
		double total = 0;
		for (JsonElement upgrade : upgrades) {
			List<JsonElement> parts = new ArrayList<>();
			if (upgrade.isJsonArray()) upgrade.getAsJsonArray().forEach(parts::add);
			else parts.add(upgrade);
			for (JsonElement cost : parts) {
				String essence = Json.str(cost, "essence_type");
				double each = essence.isEmpty() ? p.get(Json.str(cost, "item_id")) : p.get("ESSENCE_" + essence) * WORTH.get("essence");
				total += Json.num(cost, "amount") * each;
			}
		}
		return total;
	}

	private void boosters() {
		CompoundTag tiers = extra.getCompoundOrEmpty("booster_tiers");
		if (!tiers.isEmpty()) {
			for (String booster : tiers.keySet()) {
				int tier = (int) num(tiers, booster);
				List<String> ids = new ArrayList<>();
				ids.add(upper(booster) + "_BOOSTER");
				for (int t = 0; t < tier - 1; t++) ids.add(upper(booster) + "_BOOSTER_" + TIERS[t + 1]);
				for (String id : ids) {
					double v = p.get(id);
					if (v <= 0 && id.endsWith("_BOOSTER")) v = p.get(id + "_COMMON");
					if (v > 0) price += v * WORTH.get("booster");
				}
			}
			return;
		}
		ListTag boosters = extra.getListOrEmpty("boosters");
		for (int i = 0; i < boosters.size(); i++) {
			double v = p.get(upper(boosters.getStringOr(i, "")) + "_BOOSTER");
			if (v > 0) price += v * WORTH.get("booster");
		}
	}

	// ---- helpers

	/** {@code amount} of an item, at the share SkyHelper counts for this kind of upgrade (null: all of it). */
	private void add(String id, double amount, String worth) {
		if (amount <= 0 || id.isEmpty()) return;
		price += p.get(id) * amount * (worth == null ? 1 : WORTH.get(worth));
	}

	private String str(String key) {
		return extra.getStringOr(key, "");
	}

	/** A number that may be stored as a number or as text ("5"). */
	private static double num(CompoundTag tag, String key) {
		Tag t = tag.get(key);
		if (t == null) return 0;
		var n = t.asNumber();
		if (n.isPresent()) return n.get().doubleValue();
		try {
			return Double.parseDouble(t.asString().orElse("0").replaceAll("[^0-9.]", ""));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private int digits(String key) {
		return (int) num(extra, key);
	}

	private static String upper(String s) {
		return s.toUpperCase(Locale.ROOT);
	}
}
