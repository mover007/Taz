package com.taz.xpassist.progress;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.taz.xpassist.SkyblockXpAssist;
import com.taz.xpassist.config.ModConfig;
import com.taz.xpassist.data.DataManager;
import com.taz.xpassist.data.HypixelItems;
import com.taz.xpassist.data.NeuRepo;
import com.taz.xpassist.data.PlayerProfile;
import com.taz.xpassist.data.Rules;
import com.taz.xpassist.npc.Npc;
import com.taz.xpassist.npc.NpcTracker;
import com.taz.xpassist.npc.SkyblockLocation;
import com.taz.xpassist.util.Chat;
import com.taz.xpassist.util.ItemUtil;
import com.taz.xpassist.util.TextUtil;
import com.taz.xpassist.xp.XpResult;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Notices progress while you play:
 * <ul>
 *   <li>the "+N SkyBlock XP (Category) (x/100)" action bar message (live level progress, and works out what you just did),</li>
 *   <li>the SkyBlock Menu / leveling menus (level, crafted minions, Abiphone contacts, consumables),</li>
 *   <li>museum donations (the "Confirm Donation" screen).</li>
 * </ul>
 */
public final class ProgressDetector {
	private static final Pattern CONTACT_ADDED = Pattern.compile("✆\\s*(.+?) has been added to your Abiphone(?:'s)? contacts", Pattern.CASE_INSENSITIVE);
	private static final Pattern XP_GAIN = Pattern.compile("\\+(\\d+) SkyBlock XP \\(([^()]+)\\) \\((\\d+)/(\\d+)\\)");
	private static final Pattern MENU_LEVEL = Pattern.compile("Your SkyBlock Level: \\[(\\d+)]");
	private static final Pattern MENU_LEVEL_XP = Pattern.compile("(\\d+)/(\\d+) XP");
	private static final Pattern TOTAL_PROGRESS = Pattern.compile("Total Progress: (\\d+(?:\\.\\d+)?)%");
	private static final Pattern MINION_ID = Pattern.compile("^([A-Z0-9_]+_GENERATOR)_(\\d+)$");
	private static final Pattern TIER_LINE = Pattern.compile("^([✔✖])\\s*(.*?)\\s*\\b([IVXLC]+|\\d+)$");
	private static final Pattern TASK_XP = Pattern.compile("(\\d[\\d,]*)\\s*SkyBlock XP", Pattern.CASE_INSENSITIVE);
	private static final Pattern PERCENT = Pattern.compile("(\\d+(?:\\.\\d+)?)%");
	private static final java.util.Set<String> LEVELING_MENUS = java.util.Set.of("Tasks", "Core", "Event", "Dungeon", "Essence Shop",
		"Slaying", "Skill Related", "Skill Related Tasks", "Mining", "Farming", "Fishing", "Foraging", "Hunting", "Miscellaneous",
		"Story", "Complete Dungeons", "Guide");
	private static final java.util.Set<String> NAVIGATION = java.util.Set.of("go back", "close", "next page", "previous page",
		"skyblock leveling", "search", "sort", "filter");
	private static final String[] BANK_ACCOUNTS = {"Super Deluxe", "Palatial", "Luxurious", "Premier", "Deluxe", "Gold"};
	private static final Pattern MINIONS_MENU = Pattern.compile("^(?:\\(\\d+/\\d+\\) )?Crafted Minions$|➜ Minions$");
	public static final String CONFIRM_DONATION = "Confirm Donation";
	private static final int CONFIRM_DONATION_SLOT = 20;

	private static String lastXpKey;
	private static long lastXpAt;
	private static long lastContactAddedAt;
	private static boolean remindedContacts;
	private static final Map<String, Integer> consumableCounts = new HashMap<>();
	private static final Map<String, Long> consumableUsedAt = new HashMap<>();
	private static String lastScreenSignature;
	private static boolean dumpNextMenu;
	private static int ticks;

	private ProgressDetector() {
	}

	public static void init() {
		LocalProgress.load();
		// Action bar messages arrive through GuiMixin; this catches XP messages sent as normal chat.
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				onActionBar(message);
			}
		});
	}

	public static void onActionBar(Component message) {
		try {
			onGameMessage(TextUtil.strip(message.getString()));
		} catch (Exception e) {
			SkyblockXpAssist.LOGGER.warn("Error reading message", e);
		}
	}

	/** Writes the next opened menu (title, item names, ids and lore) to config/taz/xpassist/dumps for debugging. */
	public static void dumpNextMenu() {
		dumpNextMenu = true;
	}

	public static void tick(Minecraft minecraft) {
		if (minecraft.player == null) {
			return;
		}
		ticks++;
		if (ticks % 20 == 0) {
			trackConsumables(minecraft);
			remindAboutContacts();
			scanInventory(minecraft);
		}
		if (ticks % 10 == 0 && minecraft.screen instanceof AbstractContainerScreen<?> screen) {
			try {
				scanScreen(screen);
			} catch (Exception e) {
				SkyblockXpAssist.LOGGER.warn("Error reading menu {}", screen.getTitle().getString(), e);
			}
		}
	}

	// ---------------------------------------------------------------- live XP

	/** SB Level from the tab list widget, e.g. "SB Level: [377] 45/100 XP". */
	public static void onTabLevel(int level, int xpInLevel) {
		updateXp(level * 100.0 + xpInLevel);
	}

	private static void updateXp(double total) {
		LocalProgress.Profile local = LocalProgress.current();
		if (Math.abs(local.skyblockXp - total) < 0.5) {
			return;
		}
		local.skyblockXp = total;
		local.xpUpdatedAt = System.currentTimeMillis();
		saveSoon();
		DataManager.recompute();
	}

	private static void onGameMessage(String text) {
		Matcher contact = CONTACT_ADDED.matcher(text);
		if (contact.find()) {
			onContactAdded(contact.group(1).trim());
			return;
		}
		Matcher gain = XP_GAIN.matcher(text);
		if (!gain.find()) {
			return;
		}
		int amount = Integer.parseInt(gain.group(1));
		String category = gain.group(2).trim();
		int progress = Integer.parseInt(gain.group(3));
		int perLevel = Integer.parseInt(gain.group(4));
		// The action bar repeats the same message for a few seconds.
		String key = amount + "|" + category + "|" + progress;
		long now = System.currentTimeMillis();
		if (key.equals(lastXpKey) && now - lastXpAt < 15_000) {
			return;
		}
		lastXpKey = key;
		lastXpAt = now;

		PlayerProfile effective = DataManager.effectiveProfile();
		double known = LocalProgress.current().skyblockXp >= 0 ? LocalProgress.current().skyblockXp
			: effective != null ? effective.skyblockXp : -1;
		if (known > 0) {
			int level = (int) Math.floor(known / perLevel);
			double total = level * (double) perLevel + progress;
			if (total < known) {
				total += perLevel;
			}
			updateXp(total);
		}
		attribute(category.toLowerCase(Locale.ROOT), amount);
		DataManager.scheduleProfileRefresh(120_000);
	}

	/** Works out which list entry the XP came from. */
	private static void attribute(String category, int amount) {
		Minecraft minecraft = Minecraft.getInstance();
		NeuRepo neu = DataManager.neu();
		if (neu == null || minecraft.player == null) {
			return;
		}
		if (category.contains("minion")) {
			attributeMinion(minecraft, neu, amount);
		} else if (category.contains("consumable")) {
			attributeConsumable();
		} else if ((category.contains("abiphone") || category.contains("contact"))
			&& System.currentTimeMillis() - lastContactAddedAt > 15_000) {
			// Only guess from nearby NPCs when the "added to your Abiphone" chat line didn't name the contact.
			attributeContact(minecraft, neu);
		}
	}

	private static void attributeMinion(Minecraft minecraft, NeuRepo neu, int amount) {
		JsonObject xpTable = neu.miscObject("minionXp");
		PlayerProfile effective = DataManager.effectiveProfile();
		List<ItemStack> stacks = new ArrayList<>();
		Inventory inventory = minecraft.player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			stacks.add(inventory.getItem(i));
		}
		stacks.add(minecraft.player.containerMenu.getCarried());
		for (ItemStack stack : stacks) {
			Matcher matcher = MINION_ID.matcher(ItemUtil.skyblockId(stack));
			if (!matcher.matches()) {
				continue;
			}
			String tier = matcher.group(2);
			String apiId = matcher.group(1).replace("_GENERATOR", "") + "_" + tier;
			boolean known = LocalProgress.current().craftedMinions.contains(apiId)
				|| (effective != null && effective.craftedMinions.contains(apiId));
			int tierXp = xpTable.has(tier) ? xpTable.get(tier).getAsInt() : -1;
			if (!known && tierXp == amount) {
				LocalProgress.current().craftedMinions.add(apiId);
				done("minion:" + matcher.group(1) + "_" + tier, "Crafted " + ItemUtil.name(stack), amount);
				return;
			}
		}
	}

	private static void attributeConsumable() {
		long now = System.currentTimeMillis();
		String best = null;
		long bestAt = 0;
		for (Map.Entry<String, Long> entry : consumableUsedAt.entrySet()) {
			if (now - entry.getValue() < 20_000 && entry.getValue() > bestAt) {
				best = entry.getKey();
				bestAt = entry.getValue();
			}
		}
		if (best == null) {
			return;
		}
		consumableUsedAt.remove(best);
		PlayerProfile effective = DataManager.effectiveProfile();
		int used = Math.max(LocalProgress.current().consumed.getOrDefault(best, 0),
			effective != null ? effective.consumed.getOrDefault(best, 0) : 0);
		LocalProgress.current().consumed.put(best, used + 1);
		HypixelItems items = DataManager.items();
		String name = items != null && items.name(best) != null ? items.name(best) : best;
		done(null, "Used " + name, -1);
	}

	/**
	 * Items you carry make their upgrades free: museum items to donate, accessories for the bag,
	 * pets for the pets menu. Only recomputes when the set of ids actually changes.
	 */
	private static void scanInventory(Minecraft minecraft) {
		if (!SkyblockLocation.onSkyblock()) {
			return;
		}
		Rules rules = DataManager.rules();
		java.util.Set<String> ids = new java.util.HashSet<>();
		Map<String, String> pets = new HashMap<>();
		Inventory inventory = minecraft.player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			String id = ItemUtil.skyblockId(stack);
			if (id.isEmpty()) {
				continue;
			}
			ids.add(id);
			if (id.equals("PET") && rules != null) {
				JsonObject info = ItemUtil.petInfo(stack);
				if (info != null && info.has("type") && info.has("tier")) {
					String rarity = info.get("tier").getAsString();
					if (info.has("heldItem") && info.get("heldItem").isJsonPrimitive()
						&& "PET_ITEM_TIER_BOOST".equals(info.get("heldItem").getAsString())) {
						rarity = rules.upgradeRarity(rarity);
					}
					String type = info.get("type").getAsString();
					pets.merge(type, rarity, (a, b) -> rules.petScore(a) >= rules.petScore(b) ? a : b);
				}
			}
		}
		DataManager.setOwned(new com.taz.xpassist.data.OwnedItems(java.util.Set.copyOf(ids), Map.copyOf(pets)));
	}

	/** Once per game session: EliteBot can't see contacts, so ask for one look at the Abiphone. */
	private static void remindAboutContacts() {
		if (remindedContacts || !SkyblockLocation.onSkyblock() || ticks < 20 * 15) {
			return;
		}
		PlayerProfile effective = DataManager.effectiveProfile();
		if (effective == null || effective.abiphoneKnown || !effective.abiphoneContacts.isEmpty()) {
			return;
		}
		remindedContacts = true;
		Chat.send(Component.literal("Right-click your Abiphone once so I can see which contacts you already have. ")
			.withStyle(ChatFormatting.YELLOW)
			.append(Component.literal("New contacts are picked up automatically after that.").withStyle(ChatFormatting.GRAY)));
	}

	/** "✆ Tony has been added to your Abiphone's contacts!" */
	private static void onContactAdded(String name) {
		lastContactAddedAt = System.currentTimeMillis();
		NeuRepo neu = DataManager.neu();
		String canonical = neu != null ? contactFor(neu, name, true) : null;
		String display = canonical != null ? canonical : name;
		if (LocalProgress.current().abiphoneContacts.add(TextUtil.normalize(display))) {
			done("abiphone:" + display, "Added " + display + " to your Abiphone", 10);
		}
	}

	/** Finds the NEU contact for a name. */
	private static String contactFor(NeuRepo neu, String text, boolean allowWords) {
		String wanted = TextUtil.normalize(text);
		String plain = " " + TextUtil.strip(text).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim() + " ";
		String best = null;
		for (Map.Entry<String, JsonElement> entry : neu.abiphone.entrySet()) {
			String name = entry.getKey();
			if (TextUtil.normalize(name).equals(wanted)) {
				return name;
			}
			if (entry.getValue().isJsonObject() && entry.getValue().getAsJsonObject().has("callNames")) {
				for (JsonElement call : entry.getValue().getAsJsonObject().getAsJsonArray("callNames")) {
					if (TextUtil.normalize(call.getAsString()).equals(wanted)) {
						return name;
					}
				}
			}
			String words = " " + name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim() + " ";
			if (allowWords && name.length() >= 4 && plain.contains(words) && (best == null || name.length() > best.length())) {
				best = name;
			}
		}
		return best;
	}

	/** The contact you just added is the Abiphone NPC standing next to you. */
	private static void attributeContact(Minecraft minecraft, NeuRepo neu) {
		String island = SkyblockLocation.island();
		Npc nearest = null;
		double nearestDistance = 12;
		for (Map.Entry<String, JsonElement> entry : neu.abiphone.entrySet()) {
			Npc npc = neu.findNpcByExactName(entry.getKey());
			if (npc == null) {
				npc = neu.npcs.get("ABIPHONE:" + entry.getKey());
			}
			if (npc == null || !npc.hasLocation() || (island != null && !island.equals(npc.island()))) {
				continue;
			}
			double distance = minecraft.player.position().distanceTo(new net.minecraft.world.phys.Vec3(npc.x() + 0.5, npc.y(), npc.z() + 0.5));
			if (distance < nearestDistance && !LocalProgress.current().abiphoneContacts.contains(TextUtil.normalize(entry.getKey()))) {
				nearest = npc;
				nearestDistance = distance;
			}
		}
		if (nearest == null && NpcTracker.target() != null && neu.abiphone.has(NpcTracker.target().name())) {
			nearest = NpcTracker.target();
		}
		if (nearest != null) {
			LocalProgress.current().abiphoneContacts.add(TextUtil.normalize(nearest.name()));
			done("abiphone:" + nearest.name(), "Added " + nearest.name() + " to your Abiphone", 10);
		}
	}

	private static void trackConsumables(Minecraft minecraft) {
		Rules rules = DataManager.rules();
		if (rules == null) {
			return;
		}
		Map<String, Integer> counts = new HashMap<>();
		for (JsonElement element : rules.consumables()) {
			counts.put(element.getAsJsonObject().get("item").getAsString(), 0);
		}
		Inventory inventory = minecraft.player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			String id = ItemUtil.skyblockId(stack);
			if (counts.containsKey(id)) {
				counts.merge(id, stack.getCount(), Integer::sum);
			}
		}
		long now = System.currentTimeMillis();
		for (Map.Entry<String, Integer> entry : counts.entrySet()) {
			Integer before = consumableCounts.get(entry.getKey());
			if (before != null && entry.getValue() < before) {
				consumableUsedAt.put(entry.getKey(), now);
			}
		}
		consumableCounts.clear();
		consumableCounts.putAll(counts);
	}

	// ---------------------------------------------------------------- menus

	/** Called from the container screen mixin before a slot click is sent. */
	public static void onSlotClick(AbstractContainerScreen<?> screen, int slotId) {
		if (!CONFIRM_DONATION.equals(screen.getTitle().getString()) || slotId != CONFIRM_DONATION_SLOT) {
			return;
		}
		HypixelItems items = DataManager.items();
		AbstractContainerMenu menu = screen.getMenu();
		List<String> names = new ArrayList<>();
		for (int i = 0; i < Math.min(17, menu.slots.size()); i++) {
			ItemStack stack = menu.slots.get(i).getItem();
			String id = ItemUtil.skyblockId(stack);
			if (id.isEmpty() || items == null || items.get(id) == null) {
				continue;
			}
			LocalProgress.current().museumDonated.add(id);
			LocalProgress.current().museumDonated.addAll(items.get(id).armorSets.keySet());
			names.add(ItemUtil.name(stack));
		}
		if (!names.isEmpty()) {
			done(null, "Donated " + String.join(", ", names) + " to the Museum", -1);
		}
	}

	private static void scanScreen(AbstractContainerScreen<?> screen) {
		String title = TextUtil.strip(screen.getTitle().getString()).trim();
		List<ItemStack> stacks = containerItems(screen);
		StringBuilder signature = new StringBuilder(title);
		for (ItemStack stack : stacks) {
			signature.append('|').append(stack.isEmpty() ? "" : ItemUtil.name(stack)).append(stack.getCount());
		}
		String sig = signature.toString();
		if (sig.equals(lastScreenSignature) || stacks.stream().allMatch(ItemStack::isEmpty)) {
			return;
		}
		lastScreenSignature = sig;
		if (dumpNextMenu) {
			dumpNextMenu = false;
			dump(title, stacks);
		}

		boolean changed = false;
		for (ItemStack stack : stacks) {
			changed |= readLevel(stack);
		}
		if (MINIONS_MENU.matcher(title).find()) {
			changed |= readMinions(stacks);
		} else if (title.startsWith("Abiphone ")) {
			changed |= readAbiphone(stacks);
		} else if (title.contains("Abiphone Contacts")) {
			changed |= readContacts(stacks);
		} else if (title.contains("Consumable Items")) {
			changed |= readConsumables(stacks);
		} else if (title.contains("Museum") && !title.equals(CONFIRM_DONATION)) {
			changed |= readMuseum(stacks);
		}
		if (title.contains("Bank Upgrades")) {
			changed |= readBankUpgrades(stacks);
		} else if (isLevelingMenu(title) && !title.contains("Abiphone") && !title.contains("Consumable") && !title.contains("Minion")
			&& !title.startsWith("Essence Shop")) {
			changed |= readLevelingTasks(title, stacks);
		}
		if (changed) {
			saveSoon();
			DataManager.recompute();
		}
	}

	/** Only the menu's own slots, not your inventory below it. */
	private static List<ItemStack> containerItems(AbstractContainerScreen<?> screen) {
		List<ItemStack> out = new ArrayList<>();
		Minecraft minecraft = Minecraft.getInstance();
		for (Slot slot : screen.getMenu().slots) {
			if (minecraft.player != null && slot.container == minecraft.player.getInventory()) {
				continue;
			}
			out.add(slot.getItem());
		}
		return out;
	}

	/** "SkyBlock Leveling" item in the SkyBlock Menu: "Your SkyBlock Level: [377]" and "45/100 XP". */
	private static boolean readLevel(ItemStack stack) {
		Integer level = null;
		Integer xp = null;
		for (String line : ItemUtil.lore(stack)) {
			Matcher levelMatch = MENU_LEVEL.matcher(line);
			if (levelMatch.find()) {
				level = Integer.parseInt(levelMatch.group(1));
				continue;
			}
			Matcher xpMatch = MENU_LEVEL_XP.matcher(line);
			if (level != null && xp == null && xpMatch.find()) {
				xp = Integer.parseInt(xpMatch.group(1));
			}
		}
		if (level == null || xp == null) {
			return false;
		}
		double before = LocalProgress.current().skyblockXp;
		updateXp(level * 100.0 + xp);
		return before != LocalProgress.current().skyblockXp;
	}

	/** Crafted Minions menu: one item per minion type, each tier marked with ✔ (crafted) or ✖. */
	private static boolean readMinions(List<ItemStack> stacks) {
		NeuRepo neu = DataManager.neu();
		if (neu == null) {
			return false;
		}
		Map<String, String> baseByName = new HashMap<>();
		for (Map.Entry<String, JsonElement> entry : neu.miscObject("minions").entrySet()) {
			baseByName.put(TextUtil.normalize(TextUtil.stripTrailingRoman(neu.name(entry.getKey() + "_1"))), entry.getKey());
		}
		int added = 0;
		for (ItemStack stack : stacks) {
			String base = baseByName.get(TextUtil.normalize(TextUtil.stripTrailingRoman(ItemUtil.name(stack))));
			if (base == null) {
				continue;
			}
			String apiBase = base.replace("_GENERATOR", "");
			for (String line : ItemUtil.lore(stack)) {
				Matcher tier = TIER_LINE.matcher(line);
				if (tier.find() && tier.group(1).equals("✔")) {
					int number = romanOrNumber(tier.group(3));
					if (number > 0 && LocalProgress.current().craftedMinions.add(apiBase + "_" + number)) {
						added++;
					}
				}
			}
		}
		if (added > 0) {
			Chat.info("Synced " + added + " crafted minion tier" + (added == 1 ? "" : "s") + " from this menu.");
		}
		return added > 0;
	}

	/** Abiphone Contacts task menu: unfinished contacts say "This task can only be completed once!". */
	private static boolean readContacts(List<ItemStack> stacks) {
		NeuRepo neu = DataManager.neu();
		if (neu == null) {
			return false;
		}
		int added = 0;
		for (ItemStack stack : stacks) {
			String found = contactFor(neu, ItemUtil.name(stack), true);
			if (found == null) {
				continue;
			}
			String contact = TextUtil.normalize(found);
			String lore = String.join("\n", ItemUtil.lore(stack)).toLowerCase(Locale.ROOT);
			boolean unfinished = lore.contains("can only be completed once") || lore.contains("✖");
			boolean finished = lore.contains("completed") || lore.contains("✔");
			if (!unfinished && finished && LocalProgress.current().abiphoneContacts.add(contact)) {
				added++;
			}
		}
		if (added > 0) {
			Chat.info("Synced " + added + " Abiphone contact" + (added == 1 ? "" : "s") + " from this menu.");
		}
		return added > 0;
	}

	/** Your Abiphone's own menu ("Abiphone XII Mega Color"): every contact you have is listed by name. */
	private static boolean readAbiphone(List<ItemStack> stacks) {
		NeuRepo neu = DataManager.neu();
		if (neu == null) {
			return false;
		}
		int added = 0;
		for (ItemStack stack : stacks) {
			String found = contactFor(neu, ItemUtil.name(stack), true);
			if (found != null && LocalProgress.current().abiphoneContacts.add(TextUtil.normalize(found))) {
				added++;
			}
		}
		if (added > 0) {
			Chat.info("Synced " + added + " Abiphone contact" + (added == 1 ? "" : "s") + " from your Abiphone.");
		}
		return added > 0;
	}

	/** /skyblockxp task pages are titled "Core ➜ Fast Travels Unlocked", "Tasks ➜ Slaying", "Skill Related Tasks", ... */
	private static boolean isLevelingMenu(String title) {
		if (title.equals("Skill Related Tasks")) {
			return true;
		}
		int arrow = title.indexOf('➜');
		return arrow > 0 && LEVELING_MENUS.contains(title.substring(0, arrow).trim().replaceFirst("^\\(\\d+/\\d+\\) ", ""));
	}

	private enum TaskState { DONE, OPEN, UNKNOWN }

	/** How Hypixel marks tasks: "This task can only be completed once!" / ✖ when open, COMPLETED / ✔ / 100% when done. */
	private static TaskState taskState(List<String> lore) {
		String text = String.join("\n", lore).toLowerCase(Locale.ROOT);
		if (text.contains("can only be completed once") || text.contains("can be completed") || text.contains("not completed")
			|| text.contains("incomplete")) {
			return TaskState.OPEN;
		}
		for (String line : lore) {
			if (line.startsWith("✖")) {
				return TaskState.OPEN;
			}
		}
		if (text.contains("completed") || text.contains("maxed")) {
			return TaskState.DONE;
		}
		for (String line : lore) {
			if (line.toLowerCase(Locale.ROOT).contains("progress")) {
				Matcher percent = PERCENT.matcher(line);
				if (percent.find()) {
					return Double.parseDouble(percent.group(1)) >= 100 ? TaskState.DONE : TaskState.OPEN;
				}
			}
		}
		return TaskState.UNKNOWN;
	}

	/** Records every task on a /skyblockxp page for the "Other XP" tab. */
	private static boolean readLevelingTasks(String title, List<ItemStack> stacks) {
		LocalProgress.Profile local = LocalProgress.current();
		local.menuTasks.keySet().removeIf(key -> key.startsWith(title + "|"));
		int open = 0;
		for (ItemStack stack : stacks) {
			String name = ItemUtil.name(stack);
			if (name.isEmpty() || NAVIGATION.contains(name.toLowerCase(Locale.ROOT))) {
				continue;
			}
			List<String> lore = ItemUtil.lore(stack);
			TaskState state = taskState(lore);
			int xp = -1;
			String progress = null;
			for (String line : lore) {
				Matcher matcher = TASK_XP.matcher(line);
				if (xp < 0 && matcher.find()) {
					xp = Integer.parseInt(matcher.group(1).replace(",", ""));
				}
				String lower = line.toLowerCase(Locale.ROOT);
				if (progress == null && (lower.contains("progress") || lower.matches(".*\\d+\\s*/\\s*\\d+.*"))) {
					progress = line;
				}
			}
			if (state == TaskState.UNKNOWN && xp < 0 && progress == null) {
				continue;
			}
			LocalProgress.MenuTask task = new LocalProgress.MenuTask();
			task.menu = title;
			task.name = name;
			task.xp = xp;
			task.progress = progress;
			task.done = state == TaskState.DONE;
			task.seenAt = System.currentTimeMillis();
			local.menuTasks.put(title + "|" + name, task);
			if (!task.done) {
				open++;
			}
		}
		return open >= 0;
	}

	/** Core ➜ Bank Upgrades: which bank accounts you have bought. */
	private static boolean readBankUpgrades(List<ItemStack> stacks) {
		Set<String> owned = new java.util.HashSet<>();
		boolean sawAny = false;
		for (ItemStack stack : stacks) {
			String name = ItemUtil.name(stack);
			for (String account : BANK_ACCOUNTS) {
				if (name.contains(account)) {
					sawAny = true;
					if (taskState(ItemUtil.lore(stack)) != TaskState.OPEN) {
						owned.add("BANK_UPGRADE_" + account.toUpperCase(Locale.ROOT).replace(' ', '_'));
					}
					break;
				}
			}
		}
		if (!sawAny) {
			return false;
		}
		LocalProgress.current().bankUpgrades = new java.util.LinkedHashSet<>(owned);
		Chat.info("Synced your bank upgrades (" + owned.size() + " bought).");
		return true;
	}

	/** Consumable Items task menu: each item shows "Total Progress: N%". */
	private static boolean readConsumables(List<ItemStack> stacks) {
		NeuRepo neu = DataManager.neu();
		Rules rules = DataManager.rules();
		if (neu == null || rules == null) {
			return false;
		}
		JsonObject misc = neu.sbObject("miscellaneous_task");
		boolean changed = false;
		for (ItemStack stack : stacks) {
			String id = ItemUtil.skyblockId(stack);
			for (JsonElement element : rules.consumables()) {
				JsonObject rule = element.getAsJsonObject();
				if (!rule.get("item").getAsString().equals(id) || !misc.has(rule.get("xp_key").getAsString())) {
					continue;
				}
				int max = (int) Math.round(misc.get(rule.get("total_key").getAsString()).getAsDouble()
					/ misc.get(rule.get("xp_key").getAsString()).getAsDouble());
				for (String line : ItemUtil.lore(stack)) {
					Matcher progress = TOTAL_PROGRESS.matcher(line);
					if (progress.find()) {
						int used = (int) Math.round(Double.parseDouble(progress.group(1)) / 100.0 * max);
						Integer before = LocalProgress.current().consumed.put(id, used);
						changed |= before == null || before != used;
					}
				}
			}
		}
		return changed;
	}

	/** Museum pages: only items whose lore clearly says they are donated. */
	private static boolean readMuseum(List<ItemStack> stacks) {
		HypixelItems items = DataManager.items();
		if (items == null) {
			return false;
		}
		int added = 0;
		for (ItemStack stack : stacks) {
			String id = ItemUtil.skyblockId(stack);
			HypixelItems.Item item = items.get(id);
			if (item == null || (item.museumXp <= 0 && item.armorSets.isEmpty())) {
				continue;
			}
			String lore = String.join("\n", ItemUtil.lore(stack)).toLowerCase(Locale.ROOT);
			if (!lore.contains("donated") || lore.contains("not donated") || lore.contains("missing")) {
				continue;
			}
			if (LocalProgress.current().museumDonated.add(id)) {
				added++;
			}
			LocalProgress.current().museumDonated.addAll(item.armorSets.keySet());
		}
		if (added > 0) {
			Chat.info("Synced " + added + " museum donation" + (added == 1 ? "" : "s") + " from this page.");
		}
		return added > 0;
	}

	// ---------------------------------------------------------------- helpers

	private static void done(String taskId, String what, int xp) {
		saveSoon();
		DataManager.recompute();
		String label = what;
		XpResult result = DataManager.result();
		if (taskId != null && result != null) {
			for (XpResult.Entry entry : result.entries()) {
				if (entry instanceof XpResult.TaskEntry taskEntry && taskEntry.task().id.equals(taskId)) {
					label = taskEntry.task().fullLabel();
				}
			}
		}
		Chat.send(Component.literal("✔ ").withStyle(ChatFormatting.GREEN)
			.append(Component.literal(label).withStyle(ChatFormatting.WHITE))
			.append(Component.literal(xp > 0 ? " (+" + xp + " XP), removed from your list" : ", list updated").withStyle(ChatFormatting.GRAY)));
	}

	private static void saveSoon() {
		LocalProgress.save();
	}

	private static int romanOrNumber(String text) {
		if (text.chars().allMatch(Character::isDigit)) {
			return Integer.parseInt(text);
		}
		Map<Character, Integer> values = Map.of('I', 1, 'V', 5, 'X', 10, 'L', 50, 'C', 100);
		int total = 0;
		for (int i = 0; i < text.length(); i++) {
			int value = values.getOrDefault(text.charAt(i), 0);
			int next = i + 1 < text.length() ? values.getOrDefault(text.charAt(i + 1), 0) : 0;
			total += value < next ? -value : value;
		}
		return total;
	}

	private static void dump(String title, List<ItemStack> stacks) {
		StringBuilder out = new StringBuilder("Title: ").append(title).append('\n');
		for (int i = 0; i < stacks.size(); i++) {
			ItemStack stack = stacks.get(i);
			if (stack.isEmpty()) {
				continue;
			}
			out.append("\n[").append(i).append("] ").append(ItemUtil.name(stack)).append("  id=").append(ItemUtil.skyblockId(stack)).append('\n');
			for (String line : ItemUtil.lore(stack)) {
				out.append("    ").append(line).append('\n');
			}
		}
		try {
			Path dir = ModConfig.dir().resolve("dumps");
			Files.createDirectories(dir);
			Path file = dir.resolve(title.replaceAll("[^A-Za-z0-9 _()-]", "_").trim() + ".txt");
			Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
			Chat.info("Saved this menu to " + file);
		} catch (IOException e) {
			Chat.error("Could not save the menu dump: " + e.getMessage());
		}
	}
}
