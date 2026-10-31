package com.taz.general.pv;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.taz.Taz;
import com.taz.xpassist.config.ModConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * One SkyBlock profile from Hypixel's API, worked out into what the viewer draws: levels from XP, items from their NBT, gear,
 * accessories and pets, as the JSON the viewer reads. It also keeps
 * what has a price (items, pets, sacks, essence, coins) for the networth.
 */
final class ProfileData {
	static final String[] SKILLS = {"farming", "mining", "combat", "foraging", "fishing", "enchanting", "alchemy", "taming", "carpentry",
			"runecrafting", "social", "hunting"};
	/** The skills in the Skill Average, as on Hypixel. */
	private static final Set<String> AVERAGE = Set.of("farming", "mining", "combat", "foraging", "fishing", "enchanting", "alchemy", "taming", "carpentry");
	private static final Map<String, String> SKILL_ICON = Map.ofEntries(Map.entry("farming", "GOLDEN_HOE"), Map.entry("mining", "STONE_PICKAXE"),
			Map.entry("combat", "STONE_SWORD"), Map.entry("foraging", "JUNGLE_SAPLING"), Map.entry("fishing", "FISHING_ROD"),
			Map.entry("enchanting", "ENCHANTING_TABLE"), Map.entry("alchemy", "BREWING_STAND"), Map.entry("taming", "SPAWN_EGG?mc=wolf_spawn_egg"),
			Map.entry("carpentry", "CRAFTING_TABLE"), Map.entry("runecrafting", "MAGMA_CREAM"), Map.entry("social", "EMERALD"), Map.entry("hunting", "LEAD"));
	private static final String[][] SLAYERS = {{"zombie", "Revenant Horror", "ROTTEN_FLESH"}, {"spider", "Tarantula Broodfather", "COBWEB"},
			{"wolf", "Sven Packmaster", "MUTTON"}, {"enderman", "Voidgloom Seraph", "ENDER_PEARL"}, {"blaze", "Inferno Demonlord", "BLAZE_POWDER"},
			{"vampire", "Riftstalker Bloodfiend", "REDSTONE"}};
	private static final String[] CLASSES = {"healer", "mage", "berserk", "archer", "tank"};
	private static final String[] RARITIES = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC"};
	private static final Pattern STAT = Pattern.compile("^(Health|Defense|Strength|Speed|Crit Chance|Crit Damage|Intelligence|Ferocity|Magic Find|"
			+ "True Defense|Attack Speed|Health Regen|Vitality|Mending|Ability Damage|Pet Luck|Sea Creature Chance|Fishing Speed|Mining Speed|"
			+ "Mining Fortune|Farming Fortune|Foraging Fortune): \\+?([\\d,.]+)");
	private static final Pattern WEAPON = Pattern.compile("SWORD|BOW|LONGSWORD|WAND|FISHING WEAPON");
	private static final Pattern TEXTURE_VALUE = Pattern.compile("Value:\\\\?\"([A-Za-z0-9+/=]+)\\\\?\"");
	private static Map<String, String> petHeads;

	final String uuid, name, profileId;
	final JsonObject stats = new JsonObject(), combined = new JsonObject();
	final JsonArray inventories = new JsonArray();
	/** For the networth: items by where they are ("inventory", "enderchest"...), pets as items, sacks and essence by id, coins. */
	final Map<String, List<ProfileItems.Item>> containers = new LinkedHashMap<>();
	final List<ProfileItems.Item> pets = new ArrayList<>();
	final Map<String, Long> sacks = new LinkedHashMap<>(), essence = new LinkedHashMap<>();
	double purse, bank, personalBank;
	boolean noInventory;
	/** What each item is worth, once the networth is worked out (the viewer sorts weapons by it). */
	volatile Map<JsonObject, Double> values = Map.of();

	private ProfileData(String uuid, String name, String profileId) {
		this.uuid = uuid;
		this.name = name;
		this.profileId = profileId;
	}

	/** The profile with this id, or the player's selected one when the id is null or gone. Fails when they have no SkyBlock profile. */
	static ProfileData of(JsonElement answer, String typedName, String profileId) {
		JsonArray profiles = Json.arr(answer, "profiles");
		if (profiles.isEmpty()) throw new Hypixel.Failure("NO_SKYBLOCK", "No SkyBlock profiles.", 0);
		JsonObject profile = null;
		for (JsonElement p : profiles) {
			if (profileId != null && Json.str(p, "profile_id").equals(profileId)) profile = p.getAsJsonObject();
		}
		if (profile == null) {
			for (JsonElement p : profiles) if (Json.bool(p, "selected")) profile = p.getAsJsonObject();
		}
		if (profile == null) profile = profiles.get(0).getAsJsonObject();
		String uuid = Json.str(answer, "uuid");
		String name = Json.str(answer, "player", "displayname");
		ProfileData d = new ProfileData(uuid, name.isEmpty() ? typedName : name, Json.str(profile, "profile_id"));
		d.read(answer, profile, profiles);
		return d;
	}

	private void read(JsonElement answer, JsonObject profile, JsonArray profiles) {
		JsonObject member = Json.obj(profile, "members", uuid);
		stats.addProperty("uuid", uuid);
		stats.addProperty("username", name);
		stats.addProperty("profile_id", profileId);
		stats.addProperty("profile_cute_name", Json.str(profile, "cute_name"));
		stats.addProperty("game_mode", Json.str(profile, "game_mode"));
		JsonArray list = new JsonArray();
		for (JsonElement p : profiles) {
			JsonObject o = new JsonObject();
			o.addProperty("profile_id", Json.str(p, "profile_id"));
			o.addProperty("cute_name", Json.str(p, "cute_name"));
			o.addProperty("game_mode", Json.str(p, "game_mode"));
			o.addProperty("selected", Json.bool(p, "selected"));
			list.add(o);
		}
		stats.add("profiles", list);
		stats.add("rank", rank(Json.obj(answer, "player")));

		double sbXp = Json.num(member, "leveling", "experience");
		JsonObject level = new JsonObject();
		level.addProperty("level", Math.floor(sbXp / 100));
		level.addProperty("levelWithProgress", sbXp / 100);
		level.addProperty("xpCurrent", sbXp % 100);
		level.addProperty("xpForNext", 100);
		stats.add("skyblock_level", level);
		stats.addProperty("joined", Json.num(member, "profile", "first_join"));

		purse = Json.num(member, "currencies", "coin_purse");
		stats.addProperty("purse", purse);
		JsonElement balance = Json.at(profile, "banking", "balance");
		if (balance != null) {
			bank = balance.getAsDouble();
			stats.addProperty("bank", bank);
		}
		personalBank = Json.num(member, "profile", "bank_account");
		stats.addProperty("personalBank", personalBank);
		JsonObject souls = new JsonObject();
		souls.addProperty("found", Json.num(member, "fairy_soul", "total_collected"));
		stats.add("fairySouls", souls);
		stats.add("skills", skills(member));

		combined.add("dungeons", dungeons(Json.obj(member, "dungeons")));
		combined.add("slayers", slayers(Json.obj(member, "slayer", "slayer_bosses")));
		readInventories(member);
		combined.add("accessories", accessories(member));
		combined.add("pets", pets(Json.arr(member, "pets_data", "pets")));
		for (Map.Entry<String, JsonElement> e : Json.obj(member, "inventory", "sacks_counts").entrySet()) {
			long n = (long) Json.num(e.getValue());
			if (n > 0) sacks.put(e.getKey(), n);
		}
		for (Map.Entry<String, JsonElement> e : Json.obj(member, "currencies", "essence").entrySet()) {
			long n = (long) Json.num(e.getValue(), "current");
			if (n > 0) essence.put(e.getKey(), n);
		}
	}

	// ---- rank, skills, dungeons, slayers

	/** {rankText, rankColor, plusText, plusColor} from Hypixel's rank fields. */
	private static JsonObject rank(JsonObject p) {
		JsonObject o = new JsonObject();
		String prefix = Json.str(p, "prefix");
		if (!prefix.isEmpty()) {
			Matcher m = Pattern.compile("§([0-9a-f])\\[(.+?)]").matcher(prefix);
			if (m.find()) {
				o.addProperty("rankText", ProfileItems.plain(m.group(2)));
				o.addProperty("rankColor", hex(code(m.group(1).charAt(0))));
				return o;
			}
		}
		String staff = Json.str(p, "rank"), monthly = Json.str(p, "monthlyPackageRank");
		String pack = Json.str(p, "newPackageRank").isEmpty() ? Json.str(p, "packageRank") : Json.str(p, "newPackageRank");
		int plus = color(Json.str(p, "rankPlusColor"), 0xFF5555);
		switch (!staff.isEmpty() && !staff.equals("NORMAL") ? staff : monthly.equals("SUPERSTAR") ? "SUPERSTAR" : pack) {
			case "ADMIN" -> set(o, "ADMIN", 0xFF5555, "", 0);
			case "GAME_MASTER" -> set(o, "GM", 0x00AA00, "", 0);
			case "MODERATOR" -> set(o, "MOD", 0x00AA00, "", 0);
			case "HELPER" -> set(o, "HELPER", 0x5555FF, "", 0);
			case "YOUTUBER" -> set(o, "YOUTUBE", 0xFF5555, "", 0);
			case "SUPERSTAR" -> set(o, "MVP", color(Json.str(p, "monthlyRankColor"), 0xFFAA00), "++", plus);
			case "MVP_PLUS" -> set(o, "MVP", 0x55FFFF, "+", plus);
			case "MVP" -> set(o, "MVP", 0x55FFFF, "", 0);
			case "VIP_PLUS" -> set(o, "VIP", 0x55FF55, "+", 0xFFAA00);
			case "VIP" -> set(o, "VIP", 0x55FF55, "", 0);
			default -> {
			}
		}
		return o;
	}

	private static void set(JsonObject o, String text, int color, String plus, int plusColor) {
		o.addProperty("rankText", text);
		o.addProperty("rankColor", hex(color));
		o.addProperty("plusText", plus);
		o.addProperty("plusColor", hex(plusColor));
	}

	private static JsonObject skills(JsonObject member) {
		JsonObject exp = Json.obj(member, "player_data", "experience");
		JsonObject out = new JsonObject(), each = new JsonObject();
		if (exp.isEmpty()) return out;
		double sum = 0, sumProgress = 0;
		int n = 0;
		for (String key : SKILLS) {
			double xp = Json.num(exp, "SKILL_" + key.toUpperCase(Locale.ROOT));
			long[] table = key.equals("runecrafting") ? Levels.RUNECRAFTING : key.equals("social") ? Levels.SOCIAL : Levels.SKILL;
			int cap = switch (key) {
				case "farming" -> 50 + (int) Math.min(10, Json.num(member, "jacobs_contest", "perks", "farming_level_cap"));
				case "taming" -> 50 + Math.min(10, Json.arr(member, "pets_data", "pet_care", "pet_types_sacrificed").size());
				case "mining", "combat", "enchanting" -> 60;
				case "foraging" -> 54;
				case "runecrafting", "social", "hunting" -> 25;
				default -> 50;
			};
			// Levels unlocked past the usual cap ("SKILL_FORAGING_extra_level_cap": 3 makes Foraging go to 57).
			cap = Math.min(table.length, cap + (int) Json.num(exp, "SKILL_" + key.toUpperCase(Locale.ROOT) + "_extra_level_cap"));
			JsonObject level = Levels.skill(xp, table, cap);
			level.addProperty("texture", "/api/item/" + SKILL_ICON.get(key));
			each.add(key, level);
			if (AVERAGE.contains(key)) {
				sum += level.get("level").getAsInt();
				sumProgress += level.get("levelWithProgress").getAsDouble();
				n++;
			}
		}
		out.add("skills", each);
		out.addProperty("averageSkillLevel", n == 0 ? 0 : sum / n);
		out.addProperty("averageSkillLevelWithProgress", n == 0 ? 0 : sumProgress / n);
		return out;
	}

	private static JsonObject dungeons(JsonObject d) {
		JsonObject out = new JsonObject();
		JsonObject cata = Json.obj(d, "dungeon_types", "catacombs"), master = Json.obj(d, "dungeon_types", "master_catacombs");
		JsonObject level = Levels.catacombs(Json.num(cata, "experience"));
		level.addProperty("texture", "/api/item/WITHER_SKELETON_SKULL");
		out.add("level", level);

		JsonObject classes = new JsonObject(), each = new JsonObject();
		double total = 0;
		for (String c : CLASSES) {
			JsonObject l = Levels.catacombs(Json.num(d, "player_classes", c, "experience"));
			each.add(c, l);
			total += Math.min(50, l.get("levelWithProgress").getAsDouble());
		}
		classes.add("classes", each);
		classes.addProperty("classAverageWithProgress", total / CLASSES.length);
		classes.addProperty("selectedClass", Json.str(d, "selected_dungeon_class"));
		out.add("classes", classes);

		double runs = 0;
		for (JsonObject type : List.of(cata, master)) for (Map.Entry<String, JsonElement> e : Json.obj(type, "tier_completions").entrySet()) runs += Json.num(e.getValue());
		JsonObject stats = new JsonObject(), secrets = new JsonObject();
		secrets.addProperty("found", Json.num(d, "secrets"));
		secrets.addProperty("secretsPerRun", runs > 0 ? Json.num(d, "secrets") / runs : 0);
		stats.add("secrets", secrets);
		stats.addProperty("highestFloorBeatenNormal", Json.num(cata, "highest_tier_completed"));
		stats.addProperty("highestFloorBeatenMaster", Json.num(master, "highest_tier_completed"));
		out.add("stats", stats);
		out.add("catacombs", floors(cata, 0));
		out.add("master_catacombs", floors(master, 1));
		return out;
	}

	/** Each floor's stats: [{name, stats{...}, best_run{...}}]. */
	private static JsonArray floors(JsonObject type, int first) {
		JsonArray out = new JsonArray();
		for (int t = first; t <= 7; t++) {
			String k = String.valueOf(t);
			JsonObject f = new JsonObject(), s = new JsonObject();
			f.addProperty("name", t == 0 ? "Entrance" : "Floor " + t);
			for (String field : new String[]{"times_played", "tier_completions", "milestone_completions", "best_score", "fastest_time", "fastest_time_s",
					"fastest_time_s_plus", "mobs_killed", "watcher_kills"}) {
				s.addProperty(field, Json.num(type, field, k));
			}
			JsonObject damage = new JsonObject();
			for (String c : CLASSES) {
				double v = Json.num(type, "most_damage_" + c, k);
				if (v > Json.num(damage, "damage")) {
					damage.addProperty("damage", v);
					damage.addProperty("type", c);
				}
			}
			s.add("most_damage", damage);
			f.add("stats", s);
			JsonObject best = null;
			double bestScore = -1;
			for (JsonElement run : Json.arr(type, "best_runs", k)) {
				double score = Json.num(run, "score_exploration") + Json.num(run, "score_speed") + Json.num(run, "score_skill") + Json.num(run, "score_bonus");
				if (score > bestScore) {
					bestScore = score;
					best = new JsonObject();
					best.addProperty("grade", score >= 300 ? "S+" : score >= 270 ? "S" : score >= 230 ? "A" : score >= 160 ? "B" : score >= 100 ? "C" : "D");
					for (String field : new String[]{"elapsed_time", "dungeon_class", "secrets_found", "deaths"}) best.add(field, Json.at(run, field));
				}
			}
			if (best != null) f.add("best_run", best);
			out.add(f);
		}
		return out;
	}

	private static JsonObject slayers(JsonObject bosses) {
		JsonObject out = new JsonObject(), data = new JsonObject();
		double total = 0;
		for (String[] s : SLAYERS) {
			JsonObject b = Json.obj(bosses, s[0]);
			double xp = Json.num(b, "xp");
			total += xp;
			JsonObject o = new JsonObject(), kills = new JsonObject();
			o.addProperty("name", s[1]);
			o.addProperty("texture", "/api/item/" + s[2]);
			o.add("level", Levels.slayer(s[0], xp));
			double all = 0;
			for (int t = 0; t < 5; t++) {
				double n = Json.num(b, "boss_kills_tier_" + t);
				kills.addProperty(String.valueOf(t + 1), n);
				all += n;
			}
			kills.addProperty("total", all);
			o.add("kills", kills);
			data.add(s[0], o);
		}
		out.add("data", data);
		out.addProperty("totalSlayerExp", total);
		return out;
	}

	// ---- inventories, gear and accessories

	private void readInventories(JsonObject member) {
		JsonObject inv = Json.obj(member, "inventory");
		if (inv.isEmpty()) {
			noInventory = true;
			combined.add("gear", new JsonObject());
			return;
		}
		List<ProfileItems.Item> main = ProfileItems.decode(Json.at(inv, "inv_contents"));
		List<ProfileItems.Item> armor = ProfileItems.decode(Json.at(inv, "inv_armor"));
		List<ProfileItems.Item> equipment = ProfileItems.decode(Json.at(inv, "equipment_contents"));
		List<ProfileItems.Item> ender = ProfileItems.decode(Json.at(inv, "ender_chest_contents"));
		List<ProfileItems.Item> wardrobeSlots = ProfileItems.decode(Json.at(inv, "wardrobe_contents"));
		List<ProfileItems.Item> vault = ProfileItems.decode(Json.at(inv, "personal_vault_contents"));
		List<ProfileItems.Item> talismans = ProfileItems.decode(Json.at(inv, "bag_contents", "talisman_bag"));
		List<ProfileItems.Item> backpacks = new ArrayList<>();

		// The inventory as you see it: the 27 main slots, then the hotbar.
		List<ProfileItems.Item> shown = new ArrayList<>();
		if (main.size() > 9) shown.addAll(main.subList(9, main.size()));
		shown.addAll(main.subList(0, Math.min(9, main.size())));
		tab("Inventory", "/api/item/CHEST", shown, main.size() > 9 ? main.size() - 9 : 0);
		tab("Ender Chest", "/api/item/ENDER_CHEST", ender, 0);
		tab("Accessory Bag", "/api/item/ACCESSORY_BAG?mc=player_head", talismans, 0);
		List<String> packs = new ArrayList<>(Json.obj(inv, "backpack_contents").keySet());
		packs.sort((a, b) -> Integer.compare(number(a), number(b)));
		for (String n : packs) {
			List<ProfileItems.Item> icon = ProfileItems.decode(Json.at(inv, "backpack_icons", n));
			ProfileItems.Item pack = icon.isEmpty() ? null : icon.get(0);
			List<ProfileItems.Item> items = ProfileItems.decode(Json.at(inv, "backpack_contents", n));
			tab("Backpack " + (number(n) + 1), pack == null ? "/api/item/CHEST" : Json.str(pack.json(), "texture_path"), items, 0);
			backpacks.addAll(items);
			if (pack != null) backpacks.add(pack);
		}
		tab("Personal Vault", "/api/item/IRON_DOOR", vault, 0);
		Map<String, String[]> bags = new LinkedHashMap<>();
		bags.put("fishing_bag", new String[]{"Fishing Bag", "/api/item/FISHING_BAG?mc=player_head"});
		bags.put("potion_bag", new String[]{"Potion Bag", "/api/item/POTION_BAG?mc=player_head"});
		bags.put("quiver", new String[]{"Quiver", "/api/item/ARROW"});
		bags.put("sacks_bag", new String[]{"Sack of Sacks", "/api/item/SACK_OF_SACKS?mc=player_head"});
		for (Map.Entry<String, String[]> b : bags.entrySet()) {
			List<ProfileItems.Item> items = ProfileItems.decode(Json.at(inv, "bag_contents", b.getKey()));
			tab(b.getValue()[0], b.getValue()[1], items, 0);
			containers.put(b.getKey(), present(items));
		}

		containers.put("inventory", present(main));
		containers.put("armor", present(armor));
		// Loadouts: Hypixel now keeps the wardrobe and equipment sets there. The set you wear is empty (it's on you).
		JsonObject loadout = Json.obj(member, "loadout");
		JsonArray sets = new JsonArray();
		List<ProfileItems.Item> wardrobe = new ArrayList<>(present(wardrobeSlots)), equipmentSets = new ArrayList<>();
		if (!wardrobeSlots.isEmpty()) sets = wardrobe(wardrobeSlots);
		int wearing = (int) Json.num(loadout, "armor", "equipped_set");
		for (String id : sortedIds(Json.obj(loadout, "armor"))) {
			JsonObject set = Json.obj(loadout, "armor", id);
			JsonArray pieces = new JsonArray();
			boolean any = false;
			for (String part : new String[]{"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"}) {
				List<ProfileItems.Item> piece = ProfileItems.decode(Json.at(set, part));
				ProfileItems.Item item = piece.isEmpty() ? null : piece.get(0);
				if (item != null) {
					wardrobe.add(item);
					any = true;
				}
				pieces.add(json(item));
			}
			if (!any && number(id) == wearing) {
				pieces = new JsonArray();
				for (int i = 3; i >= 0; i--) pieces.add(json(i < armor.size() ? armor.get(i) : null));
				any = !armor.isEmpty();
			}
			if (any) sets.add(pieces);
		}
		for (String id : sortedIds(Json.obj(loadout, "equipment"))) {
			for (int slot = 1; slot <= 4; slot++) equipmentSets.addAll(present(ProfileItems.decode(Json.at(loadout, "equipment", id, "EQUIPMENT_SLOT_" + slot))));
		}
		List<ProfileItems.Item> allEquipment = new ArrayList<>(present(equipment));
		allEquipment.addAll(equipmentSets);
		containers.put("equipment", allEquipment);
		List<ProfileItems.Item> shared = new ArrayList<>(present(ProfileItems.decode(Json.at(member, "shared_inventory", "candy_inventory_contents"))));
		shared.addAll(present(ProfileItems.decode(Json.at(member, "shared_inventory", "carnival_mask_inventory_contents"))));
		containers.put("candy_and_masks", shared);
		containers.put("enderchest", present(ender));
		containers.put("wardrobe", wardrobe);
		containers.put("backpacks", present(backpacks));
		containers.put("personal_vault", present(vault));
		containers.put("accessories", present(talismans));

		JsonObject gear = new JsonObject();
		JsonObject armorObj = new JsonObject();
		JsonArray worn = new JsonArray();
		for (int i = 3; i >= 0; i--) worn.add(json(i < armor.size() ? armor.get(i) : null));
		armorObj.add("armor", worn);
		List<ProfileItems.Item> statItems = new ArrayList<>(armor);
		statItems.addAll(equipment);
		armorObj.add("stats", statTotals(statItems));
		gear.add("armor", armorObj);
		JsonObject equipmentObj = new JsonObject();
		JsonArray eq = new JsonArray();
		for (int i = 0; i < 4; i++) eq.add(json(i < equipment.size() ? equipment.get(i) : null));
		equipmentObj.add("equipment", eq);
		gear.add("equipment", equipmentObj);
		gear.add("wardrobe", sets);
		gear.add("weapons", weapons(main, ender, backpacks, vault));
		combined.add("gear", gear);
	}

	private void tab(String name, String texture, List<ProfileItems.Item> items, int separatorAfter) {
		if (items.isEmpty()) return;
		JsonObject t = new JsonObject();
		t.addProperty("name", name);
		t.addProperty("texture", texture);
		JsonArray slots = new JsonArray();
		for (ProfileItems.Item i : items) slots.add(json(i));
		t.add("items", slots);
		if (separatorAfter > 0) t.addProperty("separatorAfter", separatorAfter);
		inventories.add(t);
	}

	/** Wardrobe sets: each page is 9 sets of 4 rows (helmet to boots). */
	private static JsonArray wardrobe(List<ProfileItems.Item> slots) {
		Map<Integer, JsonArray> sets = new java.util.TreeMap<>();
		for (int i = 0; i < slots.size(); i++) {
			if (slots.get(i) == null) continue;
			int set = (i / 36) * 9 + i % 9, piece = (i % 36) / 9;
			JsonArray s = sets.computeIfAbsent(set, k -> {
				JsonArray a = new JsonArray();
				for (int p = 0; p < 4; p++) a.add(new JsonObject());
				return a;
			});
			s.set(piece, json(slots.get(i)));
		}
		JsonArray out = new JsonArray();
		sets.values().forEach(out::add);
		return out;
	}

	private static JsonObject weapons(List<ProfileItems.Item> main, List<ProfileItems.Item> ender, List<ProfileItems.Item> backpacks, List<ProfileItems.Item> vault) {
		JsonArray list = new JsonArray();
		JsonElement best = null;
		List<List<ProfileItems.Item>> places = List.of(main, ender, backpacks, vault);
		for (List<ProfileItems.Item> place : places) {
			for (int i = 0; i < place.size(); i++) {
				ProfileItems.Item item = place.get(i);
				if (item == null || !WEAPON.matcher(item.type()).find()) continue;
				list.add(item.json());
				// The weapon in their hotbar first, else the first one found.
				if (best == null || place == main && i < 9 && !hotbar(best, main)) best = item.json();
			}
		}
		JsonObject out = new JsonObject();
		out.add("weapons", list);
		if (best != null) out.add("highest_priority_weapon", best);
		return out;
	}

	private static boolean hotbar(JsonElement item, List<ProfileItems.Item> main) {
		for (int i = 0; i < Math.min(9, main.size()); i++) if (main.get(i) != null && main.get(i).json() == item) return true;
		return false;
	}

	/** Stats added up from the lore of their armor and equipment ("Strength: +250"). */
	private static JsonObject statTotals(List<ProfileItems.Item> items) {
		Map<String, Double> sums = new LinkedHashMap<>();
		for (ProfileItems.Item item : items) {
			if (item == null) continue;
			for (JsonElement line : Json.arr(item.json(), "lore")) {
				Matcher m = STAT.matcher(ProfileItems.plain(line.getAsString()));
				if (m.find()) {
					try {
						sums.merge(m.group(1).toLowerCase(Locale.ROOT).replace(' ', '_'), Double.parseDouble(m.group(2).replace(",", "")), Double::sum);
					} catch (NumberFormatException ignored) {
						// not a number after all
					}
				}
			}
		}
		JsonObject out = new JsonObject();
		sums.forEach(out::addProperty);
		return out;
	}

	/** Magical Power from the Accessory Bag: each different accessory by rarity, the Hegemony Artifact twice, Abiphone contacts and the Rift Prism. */
	private JsonObject accessories(JsonObject member) {
		JsonObject out = new JsonObject(), mp = new JsonObject();
		Set<String> seen = new HashSet<>();
		int accessories = 0, hegemony = 0, recombobulated = 0;
		for (ProfileItems.Item item : containers.getOrDefault("accessories", List.of())) {
			if (!seen.add(item.id())) continue;
			int power = switch (Json.str(item.json(), "rarity")) {
				case "uncommon" -> 5;
				case "rare" -> 8;
				case "epic" -> 12;
				case "legendary" -> 16;
				case "mythic", "divine" -> 22;
				case "very_special" -> 5;
				default -> 3;
			};
			accessories += power;
			if (item.id().equals("HEGEMONY_ARTIFACT")) hegemony = power;
			if (item.nbt().getCompoundOrEmpty("tag").getCompoundOrEmpty("ExtraAttributes").getIntOr("rarity_upgrades", 0) > 0) recombobulated++;
		}
		int abiphone = Json.arr(member, "nether_island_player_data", "abiphone", "active_contacts").size() / 2;
		int prism = Json.bool(member, "rift", "access", "consumed_prism") ? 11 : 0;
		mp.addProperty("total", accessories + hegemony + abiphone + prism);
		mp.addProperty("accessories", accessories);
		mp.addProperty("abiphone", abiphone);
		mp.addProperty("riftPrism", prism);
		JsonObject heg = new JsonObject();
		heg.addProperty("amount", hegemony);
		mp.add("hegemony", heg);
		out.add("magicalPower", mp);
		out.addProperty("unique", seen.size());
		out.addProperty("recombobulated", recombobulated);
		out.addProperty("selectedPower", Json.str(member, "accessory_bag_storage", "selected_power"));
		return out;
	}

	// ---- pets

	private JsonObject pets(JsonArray list) {
		List<JsonObject> out = new ArrayList<>();
		Map<String, Integer> best = new HashMap<>();
		int skins = 0;
		for (JsonElement e : list) {
			if (!e.isJsonObject()) continue;
			String type = Json.str(e, "type"), held = Json.str(e, "heldItem"), skin = Json.str(e, "skin");
			int tier = Math.max(0, List.of(RARITIES).indexOf(Json.str(e, "tier")));
			if (held.equals("PET_ITEM_TIER_BOOST")) tier = Math.min(RARITIES.length - 1, tier + 1);
			String rarity = RARITIES[tier];
			int level = Levels.pet(Json.num(e, "exp"), rarity, type);
			if (!skin.isEmpty()) skins++;
			best.merge(type, tier + 1, Math::max);
			String name = words(type);
			JsonObject p = new JsonObject();
			p.addProperty("type", type);
			p.addProperty("display_name", name);
			p.addProperty("rarity", rarity.toLowerCase(Locale.ROOT));
			p.addProperty("level", level);
			p.addProperty("active", Json.bool(e, "active"));
			p.addProperty("texture_path", petTexture(type, tier, skin));
			JsonArray lore = new JsonArray();
			lore.add("§8" + words(rarity) + " pet");
			lore.add("");
			lore.add("§7XP: §f" + String.format(Locale.US, "%,d", Math.round(Json.num(e, "exp"))));
			if (!held.isEmpty()) lore.add("§7Held item: §f" + words(held.replaceFirst("^PET_ITEM_", "")));
			if (!skin.isEmpty()) lore.add("§7Skin: §d" + words(skin));
			if (Json.num(e, "candyUsed") > 0) lore.add("§7Candy used: §f" + (int) Json.num(e, "candyUsed"));
			p.add("lore", lore);
			out.add(p);
			pets.add(new ProfileItems.Item(p, petNbt(e.getAsJsonObject(), name, level, rarity)));
		}
		out.sort((a, b) -> {
			if (Json.bool(a, "active") != Json.bool(b, "active")) return Json.bool(a, "active") ? -1 : 1;
			int r = Integer.compare(List.of(RARITIES).indexOf(Json.str(b, "rarity").toUpperCase(Locale.ROOT)),
					List.of(RARITIES).indexOf(Json.str(a, "rarity").toUpperCase(Locale.ROOT)));
			return r != 0 ? r : Double.compare(Json.num(b, "level"), Json.num(a, "level"));
		});
		JsonObject o = new JsonObject();
		JsonArray arr = new JsonArray();
		out.forEach(arr::add);
		o.add("pets", arr);
		o.addProperty("amount", out.size());
		o.addProperty("amountSkins", skins);
		int score = best.values().stream().mapToInt(Integer::intValue).sum();
		JsonObject petScore = new JsonObject(), petStats = new JsonObject();
		petScore.addProperty("amount", score);
		int[] thresholds = {10, 25, 50, 75, 100, 130, 175, 225, 275, 325};
		int mf = 0;
		for (int t : thresholds) if (score >= t) mf++;
		petStats.addProperty("magic_find", mf);
		petScore.add("stats", petStats);
		o.add("petScore", petScore);
		return o;
	}

	/** A pet as the 1.8 item SkyCofl prices: a head with id PET and its petInfo. */
	private static CompoundTag petNbt(JsonObject pet, String name, int level, String rarity) {
		CompoundTag item = new CompoundTag(), tag = new CompoundTag(), extra = new CompoundTag(), display = new CompoundTag();
		item.putShort("id", (short) 397);
		item.putByte("Count", (byte) 1);
		item.putShort("Damage", (short) 3);
		extra.putString("id", "PET");
		// Without the empty fields ("skin": null): SkyCofl matches pets with them to cheaper ones.
		JsonObject info = new JsonObject();
		for (Map.Entry<String, JsonElement> e : pet.entrySet()) if (!e.getValue().isJsonNull()) info.add(e.getKey(), e.getValue());
		extra.putString("petInfo", info.toString());
		display.putString("Name", "[Lvl " + level + "] " + name);
		ListTag lore = new ListTag();
		lore.add(StringTag.valueOf(rarity));
		display.put("Lore", lore);
		tag.put("ExtraAttributes", extra);
		tag.put("display", display);
		item.put("tag", tag);
		return item;
	}

	/** The pet's head (or its skin) from the NEU repo Taz keeps for SkyBlock XP, else a bone. */
	private static String petTexture(String type, int tier, String skin) {
		Map<String, String> heads = petHeads();
		String hash = skin.isEmpty() ? null : heads.get("PET_SKIN_" + skin);
		if (hash == null) hash = heads.get(type + ";" + tier);
		if (hash == null) for (int t = RARITIES.length - 1; t >= 0 && hash == null; t--) hash = heads.get(type + ";" + t);
		return hash != null ? "/api/head/" + hash : "/api/item/PET_" + type + "?mc=bone";
	}

	/** Head textures of the pets and pet skins in the NEU repo zip, read once. */
	private static synchronized Map<String, String> petHeads() {
		if (petHeads != null) return petHeads;
		Map<String, String> out = new HashMap<>();
		Path zip = ModConfig.dir().resolve("cache").resolve("neu-repo.zip");
		if (Files.exists(zip)) {
			try (ZipFile z = new ZipFile(zip.toFile())) {
				var entries = z.entries();
				while (entries.hasMoreElements()) {
					ZipEntry entry = entries.nextElement();
					String path = entry.getName();
					int at = path.indexOf("/items/");
					if (at < 0 || !path.endsWith(".json")) continue;
					String id = path.substring(at + 7, path.length() - 5);
					if (!id.contains(";") && !id.startsWith("PET_SKIN_")) continue;
					try (InputStream in = z.getInputStream(entry)) {
						Matcher m = TEXTURE_VALUE.matcher(new String(in.readAllBytes(), StandardCharsets.UTF_8));
						if (m.find()) {
							String hash = ProfileItems.hashOf(m.group(1));
							if (!hash.isEmpty()) out.put(id, hash);
						}
					}
				}
			} catch (Exception e) {
				Taz.LOGGER.warn("Player viewer: couldn't read pet heads from the NEU repo", e);
			}
		}
		petHeads = out;
		return out;
	}

	// ---- helpers

	private static List<ProfileItems.Item> present(List<ProfileItems.Item> items) {
		List<ProfileItems.Item> out = new ArrayList<>();
		for (ProfileItems.Item i : items) if (i != null) out.add(i);
		return out;
	}

	private static JsonElement json(ProfileItems.Item item) {
		return item == null ? new JsonObject() : item.json();
	}

	/** An object's keys that are numbers ("1".."18"), in order. */
	private static List<String> sortedIds(JsonObject o) {
		List<String> ids = new ArrayList<>();
		for (Map.Entry<String, JsonElement> e : o.entrySet()) if (e.getValue().isJsonObject()) ids.add(e.getKey());
		ids.sort((a, b) -> Integer.compare(number(a), number(b)));
		return ids;
	}

	private static int number(String s) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/** "ENDER_DRAGON" → "Ender Dragon". */
	static String words(String id) {
		StringBuilder out = new StringBuilder();
		for (String w : id.toLowerCase(Locale.ROOT).split("_")) {
			if (w.isEmpty()) continue;
			if (!out.isEmpty()) out.append(' ');
			out.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
		}
		return out.toString();
	}

	private static String hex(int rgb) {
		return String.format("#%06X", rgb & 0xFFFFFF);
	}

	/** A Hypixel colour name ("RED", "GOLD") as RGB. */
	private static int color(String name, int fallback) {
		return switch (name) {
			case "BLACK" -> 0x000000;
			case "DARK_BLUE" -> 0x0000AA;
			case "DARK_GREEN" -> 0x00AA00;
			case "DARK_AQUA" -> 0x00AAAA;
			case "DARK_RED" -> 0xAA0000;
			case "DARK_PURPLE" -> 0xAA00AA;
			case "GOLD" -> 0xFFAA00;
			case "GRAY" -> 0xAAAAAA;
			case "DARK_GRAY" -> 0x555555;
			case "BLUE" -> 0x5555FF;
			case "GREEN" -> 0x55FF55;
			case "AQUA" -> 0x55FFFF;
			case "RED" -> 0xFF5555;
			case "LIGHT_PURPLE" -> 0xFF55FF;
			case "YELLOW" -> 0xFFFF55;
			case "WHITE" -> 0xFFFFFF;
			default -> fallback;
		};
	}

	/** A § colour code as RGB. */
	private static int code(char c) {
		int[] rgb = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA, 0x555555, 0x5555FF, 0x55FF55, 0x55FFFF,
				0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};
		int i = Character.digit(c, 16);
		return i < 0 ? 0xAAAAAA : rgb[i];
	}
}
