package com.taz.general.pv;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.taz.hud.TazTheme;
import com.taz.xpassist.util.TextUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** /pv: a player viewer like NEU's. Tabs along the top, the player on the left, SkyCrypt's data on the right. */
public final class PlayerViewerScreen extends Screen {
	public static final String[] TABS = {"Overview", "Dungeons", "Gear", "Inventories", "Pets"};
	private static final int OVERVIEW = 0, DUNGEONS = 1, GEAR = 2, INVENTORIES = 3, PETS = 4;
	private static final int W = 430, H = 232, TAB_W = 26, TAB_H = 22, LEFT = 116, CX = LEFT + 8, CW = W - CX - 8;
	private static final int PANEL = 0xF2140B0C, TAB_OFF = 0xF20E0708, SLOT = 0xFF221516, BAR = 0xFF2E1B1C, LINE = 0xFF4A1A1E;
	private static final String[] SKILLS = {"farming", "mining", "combat", "foraging", "fishing", "enchanting", "alchemy", "taming",
			"carpentry", "runecrafting", "social", "hunting"};
	private static final String[] SLAYERS = {"zombie", "spider", "wolf", "enderman", "blaze", "vampire"};
	private static final String[] TIERS = {"I", "II", "III", "IV", "V"};
	private static final int WARDROBE_COLUMNS = 8, ROWS = 11;
	private static final DateTimeFormatter JOINED = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
	/** The tab /pv opens on: the one you looked at last. */
	private static int lastTab;

	private record Hit(int x, int y, int w, int h, Runnable action) {
	}

	private final PlayerViewer.Profile p;
	private final UUID uuid;
	private final ItemStack[] tabIcons;
	private final List<Hit> hits = new ArrayList<>();
	private ViewerPlayer model;
	private final ItemStack[] worn = new ItemStack[5];
	private int tab;
	private int ox, oy;
	private float scale = 1;
	private List<Component> tip;
	private JsonElement inventories;
	private String inventoryError;
	private boolean inventoriesAsked;
	private int inventory, inventoryScroll, petScroll, wardrobePage;
	private String switching;

	PlayerViewerScreen(PlayerViewer.Profile profile, int tab) {
		super(Component.literal(profile.name() + " · Player Viewer"));
		this.p = profile;
		this.uuid = uuid(profile.uuid());
		this.tab = tab < 0 ? lastTab : tab;
		lastTab = this.tab;
		ViewerItems.refresh();
		tabIcons = new ItemStack[] {ViewerItems.head(uuid), new ItemStack(Items.WITHER_SKELETON_SKULL), new ItemStack(Items.DIAMOND_CHESTPLATE),
				new ItemStack(Items.CHEST), new ItemStack(Items.BONE)};
	}

	@Override
	protected void init() {
		scale = Math.min(1f, Math.min((width - 8f) / W, (height - 8f) / (H + TAB_H)));
		ox = Math.round((width - W * scale) / 2);
		oy = Math.round((height - (H + TAB_H) * scale) / 2 + TAB_H * scale);
		if (model == null && minecraft.level != null) {
			model = new ViewerPlayer(minecraft.level, uuid, p.name());
			model.setSilent(true);
		}
	}

	/** Test hooks: the tab showing, and showing another. */
	public int tab() {
		return tab;
	}

	public void showTab(int tab) {
		this.tab = lastTab = tab;
	}

	/** Test hook: whether the Inventories tab has its data (or an error). */
	public boolean inventoriesDone() {
		return inventories != null || inventoryError != null;
	}

	/** Test hooks: the profile showing, switching to the next one, and where a point of the panel is on screen. */
	public String profileName() {
		return Json.str(p.stats(), "profile_cute_name");
	}

	public void nextProfile() {
		switchProfile(1);
	}

	public int[] screenPos(int x, int y) {
		return new int[] {Math.round(ox + x * scale), Math.round(oy + y * scale)};
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		tip = null;
		hits.clear();
		int mx = (int) Math.floor((mouseX - ox) / scale), my = (int) Math.floor((mouseY - oy) / scale);
		g.pose().pushMatrix();
		g.pose().translate(ox, oy);
		g.pose().scale(scale, scale);
		for (int i = 0; i < TABS.length; i++) if (i != tab) tabButton(g, i, mx, my);
		g.fill(0, 0, W, H, PANEL);
		g.outline(0, 0, W, H, TazTheme.DARK_RED);
		tabButton(g, tab, mx, my);
		g.verticalLine(LEFT, 6, H - 7, LINE);
		left(g, mx, my);
		switch (tab) {
			case DUNGEONS -> dungeons(g, mx, my);
			case GEAR -> gear(g, mx, my);
			case INVENTORIES -> inventories(g, mx, my);
			case PETS -> pets(g, mx, my);
			default -> overview(g, mx, my);
		}
		g.pose().popMatrix();
		if (model != null) {
			dress();
			int x1 = ox + Math.round(12 * scale), y1 = oy + Math.round(32 * scale);
			int x2 = ox + Math.round((LEFT - 12) * scale), y2 = oy + Math.round((H - 44) * scale);
			InventoryScreen.extractEntityInInventoryFollowsMouse(g, x1, y1, x2, y2, Math.round(64 * scale), 0.0625f, mouseX, mouseY, model);
		}
		if (tip != null) g.setComponentTooltipForNextFrame(font, tip, mouseX, mouseY);
	}

	private void tabButton(GuiGraphicsExtractor g, int i, int mx, int my) {
		int x = 4 + i * (TAB_W + 2), y = -TAB_H;
		boolean on = i == tab, hover = in(mx, my, x, y, TAB_W, TAB_H);
		if (on) {
			g.fill(x, y, x + TAB_W, 1, PANEL);
			g.verticalLine(x, y, 0, TazTheme.DARK_RED);
			g.verticalLine(x + TAB_W - 1, y, 0, TazTheme.DARK_RED);
			g.horizontalLine(x, x + TAB_W - 1, y, TazTheme.RED);
		} else {
			g.fill(x, y + 3, x + TAB_W, 0, hover ? 0xF2231314 : TAB_OFF);
			g.outline(x, y + 3, TAB_W, TAB_H - 3, LINE);
		}
		g.item(tabIcons[i], x + 5, y + (on ? 3 : 5));
		if (hover) tip = List.of(Component.literal(TABS[i]).withColor(TazTheme.RED & 0xFFFFFF));
		click(x, y, TAB_W, TAB_H, () -> showTab(i));
	}

	// ---- the left column: name, profile, the player, level

	private void left(GuiGraphicsExtractor g, int mx, int my) {
		int c = LEFT / 2;
		Component name = rankedName();
		if (font.width(name) > LEFT - 8) name = Component.literal(p.name()).withColor(0xFFFFFF);
		g.centeredText(font, name, c, 8, TazTheme.TEXT);

		JsonArray profiles = Json.arr(p.stats(), "profiles");
		String profile = switching != null ? "Loading " + switching + "…" : Json.str(p.stats(), "profile_cute_name") + mode(Json.str(p.stats(), "game_mode"));
		int w = font.width(profile);
		g.centeredText(font, profile, c, 20, TazTheme.PALE_RED);
		if (profiles.size() > 1 && switching == null) {
			arrow(g, "◀", c - w / 2 - 10, 20, mx, my, () -> switchProfile(-1));
			arrow(g, "▶", c + w / 2 + 4, 20, mx, my, () -> switchProfile(1));
		}
		if (in(mx, my, c - w / 2, 18, w, 12)) tip = profilesTip(profiles);

		JsonObject level = Json.obj(p.stats(), "skyblock_level");
		g.centeredText(font, Component.literal("Level ").withColor(TazTheme.GREY & 0xFFFFFF)
				.append(Component.literal(String.valueOf((int) Json.num(level, "level"))).withColor(TazTheme.RED & 0xFFFFFF)), c, H - 38, TazTheme.TEXT);
		if (in(mx, my, 4, H - 40, LEFT - 8, 11)) {
			tip = List.of(Component.literal("SkyBlock Level " + oneDecimal(Json.num(level, "levelWithProgress"))).withColor(TazTheme.RED & 0xFFFFFF),
					grey("XP " + count(Json.num(level, "xpCurrent")) + " / " + count(Json.num(level, "xpForNext"))));
		}
		long joined = (long) Json.num(p.stats(), "joined");
		if (joined > 0) {
			g.centeredText(font, "Joined " + JOINED.format(Instant.ofEpochMilli(joined).atZone(ZoneId.systemDefault())), c, H - 27, TazTheme.GREY);
		}
		String credit = "Data from SkyCrypt";
		int cw = font.width(credit), cx = c - cw / 2, cy = H - 14;
		boolean hover = in(mx, my, cx, cy - 1, cw, 10);
		g.text(font, hover ? Component.literal(credit).withStyle(s -> s.withUnderlined(true)) : Component.literal(credit), cx, cy,
				hover ? TazTheme.PALE_RED : TazTheme.DARK_GREY, false);
		if (hover) tip = List.of(Component.literal("Open " + p.name() + " on sky.shiiyu.moe"),
				grey("Taz isn't affiliated with SkyCrypt or Hypixel."));
		click(cx, cy - 1, cw, 10, () -> ConfirmLinkScreen.confirmLinkNow(this, SkyCrypt.SITE + SkyCrypt.part(p.name()) + "/"
				+ SkyCrypt.part(Json.str(p.stats(), "profile_cute_name"))));
	}

	private void arrow(GuiGraphicsExtractor g, String text, int x, int y, int mx, int my, Runnable action) {
		boolean hover = in(mx, my, x - 2, y - 2, 10, 12);
		g.text(font, text, x, y, hover ? TazTheme.RED : TazTheme.DARK_GREY, false);
		click(x - 2, y - 2, 10, 12, action);
	}

	private Component rankedName() {
		JsonObject rank = Json.obj(p.stats(), "rank");
		String text = Json.str(rank, "rankText"), plus = Json.str(rank, "plusText");
		int color = hex(Json.str(rank, "rankColor"), 0xAAAAAA);
		MutableComponent out = Component.empty();
		if (!text.isEmpty()) {
			out.append(Component.literal("[" + text).withColor(color));
			if (!plus.isEmpty()) out.append(Component.literal(plus).withColor(hex(Json.str(rank, "plusColor"), color)));
			out.append(Component.literal("] ").withColor(color));
		}
		return out.append(Component.literal(p.name()).withColor(text.isEmpty() ? 0xAAAAAA : color));
	}

	private List<Component> profilesTip(JsonArray profiles) {
		List<Component> out = new ArrayList<>();
		out.add(Component.literal("Profiles").withColor(TazTheme.RED & 0xFFFFFF));
		for (JsonElement e : profiles) {
			boolean here = Json.str(e, "profile_id").equals(p.id());
			out.add(Component.literal((here ? "▶ " : "  ") + Json.str(e, "cute_name") + mode(Json.str(e, "game_mode"))
					+ (Json.bool(e, "selected") ? " (selected)" : "")).withColor(here ? 0xFFFFFF : TazTheme.GREY & 0xFFFFFF));
		}
		if (profiles.size() > 1) out.add(grey("Click the arrows to switch."));
		return out;
	}

	private void switchProfile(int step) {
		JsonArray profiles = Json.arr(p.stats(), "profiles");
		int at = 0;
		for (int i = 0; i < profiles.size(); i++) if (Json.str(profiles.get(i), "profile_id").equals(p.id())) at = i;
		JsonElement next = profiles.get(Math.floorMod(at + step, profiles.size()));
		switching = Json.str(next, "cute_name");
		PlayerViewer.open(p.name(), Json.str(next, "profile_id"), tab);
	}

	/** The player model wears their armor and holds their best weapon, as their icons come in. */
	private void dress() {
		JsonArray armor = Json.arr(p.combined(), "gear", "armor", "armor");
		EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND};
		for (int i = 0; i < slots.length; i++) {
			JsonElement item = i < 4 ? (i < armor.size() ? armor.get(i) : null) : Json.at(p.combined(), "gear", "weapons", "highest_priority_weapon");
			ItemStack stack = ViewerItems.wearable(Json.str(item, "texture_path"));
			if (stack != worn[i]) {
				worn[i] = stack;
				model.setItemSlot(slots[i], stack);
			}
		}
	}

	// ---- Overview: money, averages, skills and slayers

	private void overview(GuiGraphicsExtractor g, int mx, int my) {
		JsonObject normal = Json.obj(p.networth(), "normal"), nonCosmetic = Json.obj(p.networth(), "nonCosmetic");
		boolean nw = p.networth() != null;
		JsonElement bank = Json.at(p.stats(), "bank");
		int y = 8;
		stat(g, mx, my, CX, y, "Purse", TextUtil.coins(Json.num(p.stats(), "purse"), 2), null);
		stat(g, mx, my, CX, y + 10, "Bank", bank == null ? "API off" : TextUtil.coins(bank.getAsDouble(), 2),
				Json.num(p.stats(), "personalBank") > 0 ? List.of(grey("Personal bank " + TextUtil.coins(Json.num(p.stats(), "personalBank"), 2))) : null);
		stat(g, mx, my, CX, y + 20, "Networth", nw ? TextUtil.coins(Json.num(normal, "networth"), 2) : "?", parts("With cosmetics", normal, "total"));
		stat(g, mx, my, CX, y + 30, "Non-cosmetic", nw ? TextUtil.coins(Json.num(nonCosmetic, "networth"), 2) : "?",
				parts("Without cosmetics", nonCosmetic, "total"));
		stat(g, mx, my, CX, y + 40, "Unsoulbound", nw ? TextUtil.coins(Json.num(normal, "unsoulboundNetworth"), 2) : "?",
				parts("Not soulbound", normal, "unsoulboundTotal"));
		int bx = CX + 150;
		stat(g, mx, my, bx, y, "Skill Average", oneDecimal(Json.num(p.stats(), "skills", "averageSkillLevel")),
				List.of(grey("With progress: " + twoDecimals(Json.num(p.stats(), "skills", "averageSkillLevelWithProgress")))));
		JsonObject cata = Json.obj(p.combined(), "dungeons", "level");
		stat(g, mx, my, bx, y + 10, "Catacombs", String.valueOf((int) Json.num(cata, "level")), skillTip("Catacombs", cata));
		stat(g, mx, my, bx, y + 20, "Magical Power", count(Json.num(p.combined(), "accessories", "magicalPower", "total")), null);
		stat(g, mx, my, bx, y + 30, "Fairy Souls", count(Json.num(p.stats(), "fairySouls", "found")) + " / "
				+ count(Json.num(p.stats(), "fairySouls", "total")), null);
		stat(g, mx, my, bx, y + 40, "Slayer XP", TextUtil.coins(Json.num(p.combined(), "slayers", "totalSlayerExp"), 1), null);

		header(g, "Skills", CX, 64);
		JsonObject skills = Json.obj(p.stats(), "skills", "skills");
		int i = 0;
		for (String key : SKILLS) {
			if (!skills.has(key)) continue;
			int x = CX + (i % 3) * 100, sy = 76 + (i / 3) * 22;
			skillCell(g, mx, my, x, sy, cap(key), Json.obj(skills, key), 78);
			i++;
		}

		header(g, "Slayers", CX, 170);
		JsonObject slayers = Json.obj(p.combined(), "slayers", "data");
		i = 0;
		for (String key : SLAYERS) {
			if (!slayers.has(key)) continue;
			JsonObject s = Json.obj(slayers, key);
			int x = CX + i * 50, sy = 182;
			g.item(ViewerItems.icon(Json.str(s, "texture")), x, sy);
			JsonObject level = Json.obj(s, "level");
			boolean maxed = Json.bool(level, "maxed");
			g.text(font, "Lv " + (int) Json.num(level, "level"), x + 18, sy, maxed ? TazTheme.RED : TazTheme.TEXT, true);
			g.text(font, TextUtil.coins(Json.num(level, "xp"), 1), x + 18, sy + 10, TazTheme.GREY, true);
			if (in(mx, my, x, sy, 48, 20)) tip = slayerTip(s);
			i++;
		}
	}

	private List<Component> parts(String title, JsonObject result, String field) {
		if (p.networth() == null) return List.of(grey("SkyCrypt couldn't work out the networth."));
		List<Component> out = new ArrayList<>();
		out.add(Component.literal(title).withColor(TazTheme.RED & 0xFFFFFF));
		out.addAll(Networth.parts(result, field));
		return out;
	}

	private void skillCell(GuiGraphicsExtractor g, int mx, int my, int x, int y, String name, JsonObject skill, int barWidth) {
		g.item(ViewerItems.icon(Json.str(skill, "texture")), x, y);
		levelBar(g, mx, my, x + 18, y, name, skill, barWidth);
	}

	/** "Farming 60" over a progress bar; hover for the XP. */
	private void levelBar(GuiGraphicsExtractor g, int mx, int my, int x, int y, String name, JsonObject skill, int barWidth) {
		boolean maxed = Json.bool(skill, "maxed");
		g.text(font, name + " " + (int) Json.num(skill, "level"), x, y + 1, maxed ? TazTheme.RED : TazTheme.TEXT, true);
		bar(g, x, y + 11, barWidth, maxed ? 1 : Json.num(skill, "progress"), maxed);
		if (in(mx, my, x - 18, y, barWidth + 20, 18)) tip = skillTip(name, skill);
	}

	private List<Component> skillTip(String name, JsonObject skill) {
		List<Component> out = new ArrayList<>();
		out.add(Component.literal(name + " " + twoDecimals(Json.num(skill, "levelWithProgress"))).withColor(TazTheme.RED & 0xFFFFFF));
		if (Json.bool(skill, "maxed")) out.add(grey("Maxed"));
		else out.add(grey("Next level " + count(Json.num(skill, "xpCurrent")) + " / " + count(Json.num(skill, "xpForNext")) + " XP"));
		out.add(grey("Total " + count(Json.num(skill, "xp")) + " XP"));
		return out;
	}

	private List<Component> slayerTip(JsonObject s) {
		JsonObject level = Json.obj(s, "level"), kills = Json.obj(s, "kills");
		List<Component> out = new ArrayList<>();
		out.add(Component.literal(Json.str(s, "name") + " " + (int) Json.num(level, "level")).withColor(TazTheme.RED & 0xFFFFFF));
		out.add(grey(count(Json.num(level, "xp")) + " XP" + (Json.bool(level, "maxed") ? " (maxed)"
				: ", " + count(Json.num(level, "xpForNext") - Json.num(level, "xp")) + " to the next level")));
		for (int t = 1; t <= TIERS.length; t++) {
			double n = Json.num(kills, String.valueOf(t));
			if (n > 0) out.add(grey("Tier " + TIERS[t - 1] + " kills: " + count(n)));
		}
		out.add(grey("Total kills: " + count(Json.num(kills, "total"))));
		return out;
	}

	// ---- Dungeons: Catacombs, classes and floors

	private void dungeons(GuiGraphicsExtractor g, int mx, int my) {
		JsonObject d = Json.obj(p.combined(), "dungeons");
		JsonObject cata = Json.obj(d, "level");
		skillCell(g, mx, my, CX, 8, "Catacombs", cata, 120);
		int bx = CX + 150;
		JsonObject classes = Json.obj(d, "classes");
		stat(g, mx, my, bx, 8, "Class Average", twoDecimals(Json.num(classes, "classAverageWithProgress")), null);
		stat(g, mx, my, bx, 18, "Secrets", count(Json.num(d, "stats", "secrets", "found")) + " (" + oneDecimal(Json.num(d, "stats", "secrets", "secretsPerRun"))
				+ " a run)", null);
		stat(g, mx, my, bx, 28, "Highest", "F" + (int) Json.num(d, "stats", "highestFloorBeatenNormal") + " · M"
				+ (int) Json.num(d, "stats", "highestFloorBeatenMaster"), null);

		header(g, "Classes", CX, 42);
		String selected = Json.str(classes, "selectedClass").toLowerCase(Locale.ROOT);
		int i = 0;
		for (Map.Entry<String, JsonElement> e : Json.obj(classes, "classes").entrySet()) {
			if (!e.getValue().isJsonObject()) continue;
			int x = CX + i * 60;
			String name = cap(e.getKey()) + (e.getKey().equals(selected) ? " ✦" : "");
			levelBar(g, mx, my, x, 54, name, e.getValue().getAsJsonObject(), 54);
			i++;
		}

		floors(g, mx, my, "The Catacombs", Json.arr(d, "catacombs"), "F", CX, 82);
		floors(g, mx, my, "Master Mode", Json.arr(d, "master_catacombs"), "M", CX + 150, 82);
	}

	private void floors(GuiGraphicsExtractor g, int mx, int my, String title, JsonArray floors, String letter, int x, int y) {
		header(g, title, x, y);
		g.text(font, "Runs", x + 22, y + 12, TazTheme.DARK_GREY, false);
		g.text(font, "Score", x + 62, y + 12, TazTheme.DARK_GREY, false);
		g.text(font, "S+", x + 100, y + 12, TazTheme.DARK_GREY, false);
		int row = 0;
		for (JsonElement f : floors) {
			if (f == null || !f.isJsonObject()) continue;
			JsonObject stats = Json.obj(f, "stats");
			if (Json.num(stats, "times_played") <= 0 && Json.num(stats, "tier_completions") <= 0) continue;
			int ry = y + 24 + row * 11;
			String name = Json.str(f, "name");
			String label = name.startsWith("Floor ") ? letter + name.substring(6) : name.equals("Entrance") ? "E" : name;
			boolean hover = in(mx, my, x, ry - 1, 146, 11);
			if (hover) g.fill(x - 2, ry - 2, x + 146, ry + 9, 0x30FFFFFF);
			g.text(font, label, x, ry, TazTheme.RED, false);
			g.text(font, count(Json.num(stats, "tier_completions")), x + 22, ry, TazTheme.TEXT, false);
			g.text(font, count(Json.num(stats, "best_score")), x + 62, ry, TazTheme.TEXT, false);
			double splus = Json.num(stats, "fastest_time_s_plus");
			g.text(font, splus > 0 ? time(splus) : "-", x + 100, ry, TazTheme.TEXT, false);
			if (hover) tip = floorTip(title + " " + (name.startsWith("Floor ") ? letter + name.substring(6) : name), f);
			row++;
		}
		if (row == 0) g.text(font, "No runs yet", x, y + 24, TazTheme.DARK_GREY, false);
	}

	private List<Component> floorTip(String title, JsonElement floor) {
		JsonObject s = Json.obj(floor, "stats"), best = Json.obj(floor, "best_run");
		List<Component> out = new ArrayList<>();
		out.add(Component.literal(title).withColor(TazTheme.RED & 0xFFFFFF));
		out.add(grey("Played " + count(Json.num(s, "times_played")) + " · completed " + count(Json.num(s, "tier_completions"))
				+ " · milestones " + count(Json.num(s, "milestone_completions"))));
		out.add(grey("Best score " + count(Json.num(s, "best_score"))));
		out.add(grey("Fastest " + time(Json.num(s, "fastest_time")) + " · S " + time(Json.num(s, "fastest_time_s"))
				+ " · S+ " + time(Json.num(s, "fastest_time_s_plus"))));
		if (Json.num(s, "most_damage", "damage") > 0) {
			out.add(grey("Most damage " + TextUtil.coins(Json.num(s, "most_damage", "damage"), 1) + " as " + cap(Json.str(s, "most_damage", "type"))));
		}
		out.add(grey("Mobs killed " + count(Json.num(s, "mobs_killed")) + " · Watcher kills " + count(Json.num(s, "watcher_kills"))));
		if (best.size() > 0) {
			out.add(Component.literal("Best run: " + Json.str(best, "grade") + " in " + time(Json.num(best, "elapsed_time")) + " as "
					+ cap(Json.str(best, "dungeon_class")) + ", " + (int) Json.num(best, "secrets_found") + " secrets, "
					+ (int) Json.num(best, "deaths") + " deaths").withColor(TazTheme.PALE_RED & 0xFFFFFF));
		}
		return out;
	}

	// ---- Gear: armor, equipment, pet, weapons, wardrobe and accessories

	private void gear(GuiGraphicsExtractor g, int mx, int my) {
		JsonObject gear = Json.obj(p.combined(), "gear");
		header(g, "Armor", CX, 8);
		JsonArray armor = Json.arr(gear, "armor", "armor"), equipment = Json.arr(gear, "equipment", "equipment");
		for (int i = 0; i < 4; i++) {
			slot(g, mx, my, CX, 20 + i * 18, i < armor.size() ? armor.get(i) : null);
			slot(g, mx, my, CX + 20, 20 + i * 18, i < equipment.size() ? equipment.get(i) : null);
		}
		JsonElement pet = activePet();
		petSlot(g, mx, my, CX + 42, 20, pet);
		slot(g, mx, my, CX + 42, 38, Json.at(gear, "weapons", "highest_priority_weapon"));

		String set = Json.str(gear, "armor", "set_name");
		int ty = 20;
		if (!set.isEmpty()) {
			g.text(font, set, CX + 64, ty, 0xFF000000 | ViewerItems.rarity(Json.str(gear, "armor", "set_rarity")), true);
			ty += 11;
		}
		for (Map.Entry<String, JsonElement> e : Json.obj(gear, "armor", "stats").entrySet()) {
			if (ty > 92) break;
			g.text(font, Networth.category(e.getKey()) + " ", CX + 64, ty, TazTheme.GREY, false);
			g.text(font, number(Json.num(e.getValue())), CX + 64 + font.width(Networth.category(e.getKey()) + " "), ty, TazTheme.TEXT, false);
			ty += 10;
		}

		JsonArray wardrobe = Json.arr(gear, "wardrobe");
		int pages = Math.max(1, (wardrobe.size() + WARDROBE_COLUMNS - 1) / WARDROBE_COLUMNS);
		wardrobePage = Math.min(wardrobePage, pages - 1);
		int wx = CX + CW - WARDROBE_COLUMNS * 18;
		header(g, "Wardrobe" + (pages > 1 ? " " + (wardrobePage + 1) + "/" + pages : ""), wx, 8);
		if (pages > 1) {
			arrow(g, "◀", wx + WARDROBE_COLUMNS * 18 - 22, 8, mx, my, () -> wardrobePage = Math.floorMod(wardrobePage - 1, pages));
			arrow(g, "▶", wx + WARDROBE_COLUMNS * 18 - 9, 8, mx, my, () -> wardrobePage = Math.floorMod(wardrobePage + 1, pages));
		}
		for (int c = 0; c < WARDROBE_COLUMNS; c++) {
			int n = wardrobePage * WARDROBE_COLUMNS + c;
			JsonArray pieces = n < wardrobe.size() && wardrobe.get(n).isJsonArray() ? wardrobe.get(n).getAsJsonArray() : new JsonArray();
			for (int r = 0; r < 4; r++) slot(g, mx, my, wx + c * 18, 20 + r * 18, r < pieces.size() ? pieces.get(r) : null);
		}

		JsonArray weapons = Json.arr(gear, "weapons", "weapons");
		header(g, "Weapons" + (weapons.size() > 48 ? " (48 of " + weapons.size() + ")" : ""), CX, 100);
		for (int i = 0; i < Math.min(48, weapons.size()); i++) slot(g, mx, my, CX + (i % 16) * 18, 112 + (i / 16) * 18, weapons.get(i));
		if (weapons.isEmpty()) g.text(font, "None, or their inventory API is off", CX, 114, TazTheme.DARK_GREY, false);

		JsonObject acc = Json.obj(p.combined(), "accessories");
		header(g, "Accessories", CX, 170);
		stat(g, mx, my, CX, 182, "Magical Power", count(Json.num(acc, "magicalPower", "total")), magicalPowerTip(acc));
		stat(g, mx, my, CX, 192, "Unique", count(Json.num(acc, "unique")) + " · " + Json.arr(acc, "missing").size() + " missing", null);
		stat(g, mx, my, CX + 150, 182, "Recombobulated", count(Json.num(acc, "recombobulated")), null);
		String power = Json.str(acc, "selectedPower");
		stat(g, mx, my, CX + 150, 192, "Power", power.isEmpty() ? "None" : cap(power), null);
	}

	private List<Component> magicalPowerTip(JsonObject acc) {
		JsonObject mp = Json.obj(acc, "magicalPower");
		List<Component> out = new ArrayList<>();
		out.add(Component.literal("Magical Power " + count(Json.num(mp, "total"))).withColor(TazTheme.RED & 0xFFFFFF));
		out.add(grey("Accessories " + count(Json.num(mp, "accessories"))));
		if (Json.num(mp, "abiphone") > 0) out.add(grey("Abiphone " + count(Json.num(mp, "abiphone"))));
		if (Json.num(mp, "riftPrism") > 0) out.add(grey("Rift Prism " + count(Json.num(mp, "riftPrism"))));
		if (Json.num(mp, "hegemony", "amount") > 0) out.add(grey("Hegemony Artifact " + count(Json.num(mp, "hegemony", "amount"))));
		return out;
	}

	private JsonElement activePet() {
		for (JsonElement pet : Json.arr(p.combined(), "pets", "pets")) if (Json.bool(pet, "active")) return pet;
		return null;
	}

	// ---- Inventories: SkyCrypt's inventory tabs, loaded when you first open this tab

	private void inventories(GuiGraphicsExtractor g, int mx, int my) {
		if (!inventoriesAsked) {
			inventoriesAsked = true;
			SkyCrypt.get("inventory/" + SkyCrypt.part(p.uuid()) + "/" + SkyCrypt.part(p.id())).whenCompleteAsync((json, error) -> {
				if (error != null) {
					Throwable t = SkyCrypt.cause(error);
					inventoryError = t instanceof SkyCrypt.Failure f && f.code.equals("NOT_CACHED")
							? "SkyCrypt hasn't loaded these inventories yet. Open their SkyCrypt page, then reopen /pv."
							: "Couldn't load the inventories: " + t.getMessage();
				} else {
					inventories = json;
				}
			}, Minecraft.getInstance());
		}
		if (inventoryError != null) {
			g.textWithWordWrap(font, Component.literal(inventoryError), CX, 12, CW, TazTheme.LIGHT_RED);
			return;
		}
		if (inventories == null) {
			g.text(font, "Loading inventories…", CX, 12, TazTheme.GREY, false);
			return;
		}
		List<JsonObject> tabs = new ArrayList<>();
		for (JsonElement e : inventories.isJsonArray() ? inventories.getAsJsonArray() : Json.arr(inventories, "items")) {
			if (e.isJsonObject()) tabs.add(e.getAsJsonObject());
		}
		if (tabs.isEmpty()) {
			g.text(font, "No inventories: their inventory API may be off.", CX, 12, TazTheme.GREY, false);
			return;
		}
		inventory = Math.min(inventory, tabs.size() - 1);
		for (int i = 0; i < tabs.size() && i < 24; i++) {
			int x = CX + (i % 2) * 19, y = 8 + (i / 2) * 18;
			JsonObject t = tabs.get(i);
			boolean on = i == inventory, hover = in(mx, my, x, y, 18, 18);
			g.fill(x, y, x + 18, y + 18, on ? 0xFF3A1416 : SLOT);
			if (on || hover) g.outline(x, y, 18, 18, on ? TazTheme.RED : LINE);
			g.item(ViewerItems.icon(Json.str(t, "texture")), x + 1, y + 1);
			if (hover) tip = List.of(Component.literal(Json.str(t, "name")).withColor(TazTheme.RED & 0xFFFFFF),
					grey(Json.arr(t, "items").size() + " slots"));
			int n = i;
			click(x, y, 18, 18, () -> {
				inventory = n;
				inventoryScroll = 0;
			});
		}
		JsonObject t = tabs.get(inventory);
		JsonArray items = Json.arr(t, "items");
		// Items after the separator (the hotbar, say) start a new row, a little lower.
		int sep = (int) Json.num(t, "separatorAfter");
		int sepRows = sep > 0 && sep < items.size() ? (sep + 8) / 9 : 0;
		int rows = sepRows > 0 ? sepRows + (items.size() - sep + 8) / 9 : (items.size() + 8) / 9;
		inventoryScroll = Math.max(0, Math.min(inventoryScroll, rows - ROWS));
		int gx = CX + 46;
		header(g, Json.str(t, "name") + (rows > ROWS ? "  (scroll)" : ""), gx, 8);
		for (int i = 0; i < items.size(); i++) {
			boolean after = sepRows > 0 && i >= sep;
			int n = after ? i - sep : i;
			int row = (after ? sepRows + n / 9 : n / 9) - inventoryScroll;
			if (row < 0 || row >= ROWS) continue;
			slot(g, mx, my, gx + (n % 9) * 18, 20 + row * 18 + (after && inventoryScroll < sepRows ? 4 : 0), items.get(i));
		}
	}

	// ---- Pets

	private void pets(GuiGraphicsExtractor g, int mx, int my) {
		JsonObject pets = Json.obj(p.combined(), "pets");
		stat(g, mx, my, CX, 8, "Pet Score", count(Json.num(pets, "petScore", "amount")) + " (+" + (int) Json.num(pets, "petScore", "stats", "magic_find")
				+ " Magic Find)", null);
		stat(g, mx, my, CX, 18, "Pets", count(Json.num(pets, "amount")) + " · " + count(Json.num(pets, "amountSkins")) + " skins · "
				+ Json.arr(pets, "missing").size() + " missing", null);
		JsonArray list = Json.arr(pets, "pets");
		int columns = 16, rows = (list.size() + columns - 1) / columns, visible = ROWS - 1;
		int top = 32;
		petScroll = Math.max(0, Math.min(petScroll, rows - visible));
		for (int i = petScroll * columns; i < list.size() && i < (petScroll + visible) * columns; i++) {
			int n = i - petScroll * columns;
			petSlot(g, mx, my, CX + (n % columns) * 18, top + (n / columns) * 18, list.get(i));
		}
		if (list.isEmpty()) g.text(font, "No pets", CX, top + 2, TazTheme.DARK_GREY, false);
		else if (rows > visible) g.text(font, "Scroll for more", CX + CW - font.width("Scroll for more"), H - 12, TazTheme.DARK_GREY, false);
	}

	private void petSlot(GuiGraphicsExtractor g, int mx, int my, int x, int y, JsonElement pet) {
		slot(g, mx, my, x, y, pet);
		if (pet == null) return;
		int level = (int) Json.num(pet, "level");
		if (Json.bool(pet, "active")) g.outline(x, y, 18, 18, TazTheme.RED);
		if (in(mx, my, x, y, 18, 18)) {
			List<Component> lines = new ArrayList<>();
			lines.add(Component.literal("§7[Lvl " + level + "] " + code(Json.str(pet, "rarity")) + Json.str(pet, "display_name")
					+ (Json.bool(pet, "active") ? " §c(active)" : "")));
			for (JsonElement line : Json.arr(pet, "lore")) lines.add(Component.literal(line.isJsonPrimitive() ? line.getAsString() : ""));
			tip = lines;
		}
	}

	// ---- drawing helpers

	/** An item slot with a rarity tint; hover shows its tooltip. */
	private void slot(GuiGraphicsExtractor g, int mx, int my, int x, int y, JsonElement item) {
		g.fill(x, y, x + 18, y + 18, SLOT);
		g.outline(x, y, 18, 18, 0xFF2C1B1C);
		if (item == null || !item.isJsonObject() || !ViewerItems.present(item)) return;
		g.fill(x + 1, y + 1, x + 17, y + 17, 0x38000000 | ViewerItems.rarity(Json.str(item, "rarity")));
		ItemStack icon = ViewerItems.icon(Json.str(item, "texture_path"));
		g.item(icon, x + 1, y + 1);
		int count = (int) Json.num(item, "Count");
		if (count > 1) g.itemDecorations(font, icon, x + 1, y + 1, String.valueOf(count));
		if (in(mx, my, x, y, 18, 18)) {
			g.fill(x + 1, y + 1, x + 17, y + 17, 0x50FFFFFF);
			tip = ViewerItems.tooltip(item);
		}
	}

	private void stat(GuiGraphicsExtractor g, int mx, int my, int x, int y, String label, String value, List<Component> hover) {
		g.text(font, label + " ", x, y, TazTheme.GREY, false);
		int vx = x + font.width(label + " ");
		g.text(font, value, vx, y, TazTheme.TEXT, false);
		if (hover != null && in(mx, my, x, y - 1, vx - x + font.width(value), 10)) tip = hover;
	}

	private void header(GuiGraphicsExtractor g, String text, int x, int y) {
		g.text(font, text, x, y, TazTheme.RED, true);
	}

	private void bar(GuiGraphicsExtractor g, int x, int y, int w, double progress, boolean maxed) {
		g.fill(x, y, x + w, y + 4, BAR);
		int fill = (int) Math.round(w * Math.max(0, Math.min(1, progress)));
		if (fill > 0) {
			g.fill(x, y, x + fill, y + 4, maxed ? TazTheme.RED : TazTheme.DARK_RED);
			g.horizontalLine(x, x + fill - 1, y, maxed ? TazTheme.PALE_RED : TazTheme.RED);
		}
	}

	private void click(int x, int y, int w, int h, Runnable action) {
		hits.add(new Hit(x, y, w, h, action));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			int mx = (int) Math.floor((event.x() - ox) / scale), my = (int) Math.floor((event.y() - oy) / scale);
			for (Hit h : List.copyOf(hits)) {
				if (in(mx, my, h.x, h.y, h.w, h.h)) {
					h.action.run();
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int step = scrollY > 0 ? -1 : scrollY < 0 ? 1 : 0;
		if (tab == INVENTORIES) inventoryScroll = Math.max(0, inventoryScroll + step);
		else if (tab == PETS) petScroll = Math.max(0, petScroll + step);
		else return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private static boolean in(int mx, int my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private static Component grey(String text) {
		return Component.literal(text).withColor(TazTheme.GREY & 0xFFFFFF);
	}

	private static String mode(String gameMode) {
		return switch (gameMode) {
			case "ironman" -> " ♲";
			case "island" -> " ☀";
			case "bingo" -> " Ⓑ";
			default -> "";
		};
	}

	/** The § colour code for a rarity. */
	private static String code(String rarity) {
		return switch (rarity.toLowerCase(Locale.ROOT).replace(' ', '_')) {
			case "uncommon" -> "§a";
			case "rare" -> "§9";
			case "epic" -> "§5";
			case "legendary" -> "§6";
			case "mythic" -> "§d";
			case "divine" -> "§b";
			case "special", "very_special" -> "§c";
			case "supreme", "admin" -> "§4";
			default -> "§f";
		};
	}

	private static String cap(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
	}

	private static String count(double n) {
		return String.format(Locale.US, "%,d", Math.round(n));
	}

	private static String number(double n) {
		return n == Math.rint(n) ? count(n) : oneDecimal(n);
	}

	private static String oneDecimal(double n) {
		return String.format(Locale.US, "%.1f", n);
	}

	private static String twoDecimals(double n) {
		return String.format(Locale.US, "%.2f", n);
	}

	/** Milliseconds as "4:38". */
	private static String time(double ms) {
		if (ms <= 0) return "-";
		long s = Math.round(ms / 1000);
		return s / 60 + ":" + String.format(Locale.ROOT, "%02d", s % 60);
	}

	private static int hex(String color, int fallback) {
		try {
			return color.startsWith("#") ? Integer.parseInt(color.substring(1), 16) : fallback;
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	/** "28667672039044989b0019b14a2c34d6" as a UUID. */
	static UUID uuid(String id) {
		String s = id.replace("-", "");
		if (s.length() != 32) return UUID.nameUUIDFromBytes(id.getBytes());
		return UUID.fromString(s.substring(0, 8) + "-" + s.substring(8, 12) + "-" + s.substring(12, 16) + "-" + s.substring(16, 20) + "-" + s.substring(20));
	}
}
