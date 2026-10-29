package com.taz.fishing.trophy;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.taz.Taz;
import com.taz.dungeons.util.TextUtil;
import com.taz.fishing.lotus.TrophyFrogs;
import com.taz.util.Items;
import com.taz.util.TazChat;
import com.taz.xpassist.progress.LocalProgress;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/**
 * One trophy collection, the Crimson Isle's Trophy Fish or the Lotus Atoll's Trophy Frogs: how many of each species
 * you've caught at each tier, and which tiers you have.
 * <ul>
 * <li>Counts come from the collector's menu (Odger's "Trophy Fish", lore like "Bronze ✔ (12)" and "Diamond ✖"), plus
 * every catch since then from Minecraft's chat logs and this session. Before you open the menu, the chat logs alone.</li>
 * <li>Catches are chat lines like "TROPHY FISH! You caught a Blobfish BRONZE!" after an icon.</li>
 * <li>Which tiers you have also comes from the tab list's widget (" ●●○● Common Frog").</li>
 * </ul>
 */
public final class TrophyBook {
	/** A species: its name as Hypixel writes it, the resource pack's model for its icons, its rarity colour and Gold pity. */
	public record Entry(String label, String model, String rarity, int goldPity) {
		public int diamondPity() {
			return goldPity * 6;
		}
	}

	/** A catch of yours: which book, species and tier, and whether it's new to your collection. */
	public record Catch(TrophyBook book, int entry, Tier tier, boolean isNew) {
	}

	private static final Pattern CAUGHT = Pattern.compile("^[^A-Za-z]*TROPHY (FISH|FROG)! You caught (?:an? )?(.+) (BRONZE|SILVER|GOLD|DIAMOND)!$");
	private static final Pattern NEW = Pattern.compile("^NEW DISCOVERY: (.+) (BRONZE|SILVER|GOLD|DIAMOND)$");
	/** A tab list line of the trophy widget: a dot per tier, filled when you have it. */
	private static final Pattern TAB = Pattern.compile("^([●○]{4}) (.+)$");
	private static final Pattern MENU_HAVE = Pattern.compile("^(Bronze|Silver|Gold|Diamond) ✔ \\(([\\d,]+)\\)$");
	private static final Pattern MENU_NONE = Pattern.compile("^(Bronze|Silver|Gold|Diamond) ✖$");
	private static final Pattern LOG_LINE = Pattern.compile("^\\[\\d\\d:\\d\\d:\\d\\d] \\[[^]]*]: (?:\\[System] )?\\[CHAT] (.*)$");
	private static final Pattern LOG_FILE = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})-\\d+\\.log\\.gz$");
	private static final Pattern DUPLICATE_SUFFIX = Pattern.compile("\\s*\\(x\\d+\\)$");
	/** Bumped when the log reading changes, so files read the old way are read again. */
	private static final int CACHE_VERSION = 3;
	private static final int T = 4;
	private static final Path DIR = Taz.CONFIG_DIR.resolve("fishing");

	private static boolean importing;
	private static String newFind;
	private static Screen menuScreen;
	private static final Set<String> loggedMenus = new HashSet<>();
	private static final Set<TrophyBook> toldMenu = new HashSet<>();
	private static int ticks;

	public final String name;
	private final String word;
	private final List<Entry> entries;
	private final Path logCache, menuFile;
	private final Map<String, Integer> byName = new HashMap<>();
	/** Archived chat logs read, by file: each catch packed as (time in ms << 8 | species * 4 + tier). */
	private final Map<String, long[]> logs = new HashMap<>();
	/** This session's catches, packed the same way. */
	private final List<Long> live = new ArrayList<>();
	/** The collector's menu, by profile. */
	private final Map<String, Menu> menus = new HashMap<>();
	private final boolean[] owned;
	private boolean imported, menusLoaded;
	private int[] counts;
	private String countsFor;
	private Consumer<Catch> listener = c -> {
	};

	/** What the menu said for each species, and when it was read (0 = never). */
	private static final class Menu {
		final int[] counts;
		final long[] at;

		Menu(int n) {
			counts = new int[n * T];
			at = new long[n];
		}
	}

	/** {@code word} is "FISH" or "FROG", as in "TROPHY FISH!"; {@code key} names its files. */
	public TrophyBook(String name, String word, String key, List<Entry> entries) {
		this.name = name;
		this.word = word;
		this.entries = List.copyOf(entries);
		this.logCache = DIR.resolve("trophy-" + key + "-logs.json");
		this.menuFile = DIR.resolve("trophy-" + key + "-menu.json");
		this.owned = new boolean[entries.size() * T];
		for (int i = 0; i < entries.size(); i++) byName.put(norm(entries.get(i).label), i);
	}

	private static List<TrophyBook> books() {
		return List.of(TrophyFrogs.BOOK);
	}

	private static String norm(String s) {
		return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}

	/** Called with each of your catches (live, not from the logs). */
	public void onCatch(Consumer<Catch> listener) {
		this.listener = listener;
	}

	public int size() {
		return entries.size();
	}

	public Entry entry(int i) {
		return entries.get(i);
	}

	/** The species with this name ("Obfuscated 1" and "Obfuscated-1" are the same), or -1. */
	public int index(String name) {
		return byName.getOrDefault(norm(name), -1);
	}

	// ---- what you have

	public int count(int i, Tier t) {
		return counts()[i * T + t.ordinal()];
	}

	public boolean has(int i, Tier t) {
		return owned[i * T + t.ordinal()] || count(i, t) > 0;
	}

	public int total(int i) {
		int n = 0;
		for (Tier t : Tier.values()) n += count(i, t);
		return n;
	}

	/** Catches toward the next pity you haven't hit: "G 47/100", "D 120/600", or "" once you have both. */
	public String pity(int i) {
		Entry e = entries.get(i);
		if (!has(i, Tier.GOLD)) return "G " + total(i) + "/" + e.goldPity;
		if (!has(i, Tier.DIAMOND)) return "D " + total(i) + "/" + e.diamondPity();
		return "";
	}

	/** The lowest tier you haven't got every species of, or null when you have them all. */
	public Tier goalTier() {
		for (Tier t : Tier.values()) for (int i = 0; i < size(); i++) if (!has(i, t)) return t;
		return null;
	}

	public int caughtAt(Tier t) {
		int n = 0;
		for (int i = 0; i < size(); i++) if (has(i, t)) n++;
		return n;
	}

	public int found() {
		int n = 0;
		for (Tier t : Tier.values()) n += caughtAt(t);
		return n;
	}

	/** Whether the collector's menu has been read on this profile. */
	public boolean fromMenu() {
		for (long at : menu().at) if (at > 0) return true;
		return false;
	}

	private Menu menu() {
		loadMenus();
		return menus.computeIfAbsent(LocalProgress.activeProfile(), p -> new Menu(size()));
	}

	private int[] counts() {
		importAll();
		String profile = LocalProgress.activeProfile();
		if (counts != null && profile.equals(countsFor)) return counts;
		Menu m = menu();
		int[] c = m.counts.clone();
		for (long[] file : logs.values()) for (long e : file) add(c, e, m);
		for (long e : live) add(c, e, m);
		countsFor = profile;
		return counts = c;
	}

	/** A catch counts if it came after the menu was read. */
	private static void add(int[] c, long e, Menu m) {
		int i = (int) (e & 0xFF);
		if ((e >>> 8) > m.at[i / T]) c[i]++;
	}

	// ---- chat, the tab list and the menu

	/** A plain-text chat line: your catches, for every book. */
	public static void onChat(String line) {
		Matcher m = NEW.matcher(line);
		if (m.matches()) {
			newFind = m.group(1) + " " + m.group(2);
			return;
		}
		m = CAUGHT.matcher(line);
		if (!m.matches()) return;
		boolean isNew = (m.group(2) + " " + m.group(3)).equalsIgnoreCase(newFind);
		newFind = null;
		for (TrophyBook b : books()) {
			if (!b.word.equals(m.group(1))) continue;
			int i = b.index(m.group(2));
			if (i < 0) return;
			Tier t = Tier.valueOf(m.group(3));
			b.live.add(System.currentTimeMillis() << 8 | (long) i * T + t.ordinal());
			b.counts = null;
			b.listener.accept(new Catch(b, i, t, isNew));
		}
	}

	/** Every client tick: the tab list's trophy widget each second, and an open trophy menu. */
	public static void tick(Minecraft mc) {
		if (++ticks % 10 != 0) return;
		readMenu(mc);
		if (ticks % 20 == 0) readTab(tabLines(mc));
	}

	/** The tab list's lines as plain text (Hypixel's icons kept). */
	public static List<String> tabLines(Minecraft mc) {
		List<String> out = new ArrayList<>();
		if (mc.getConnection() == null) return out;
		for (PlayerInfo info : mc.getConnection().getListedOnlinePlayers()) {
			Component c = info.getTabListDisplayName();
			if (c != null) out.add(TextUtil.cleanChat(c.getString()));
		}
		return out;
	}

	/** Plain-text tab list lines; the trophy widget's say which tiers you have. */
	public static void readTab(List<String> lines) {
		for (String line : lines) {
			Matcher m = TAB.matcher(line);
			if (!m.matches()) continue;
			for (TrophyBook b : books()) {
				int i = b.index(m.group(2));
				if (i < 0) continue;
				for (Tier t : Tier.values()) b.owned[i * T + t.ordinal()] = m.group(1).charAt(t.ordinal()) == '●';
			}
		}
	}

	private static void readMenu(Minecraft mc) {
		if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) {
			menuScreen = null;
			return;
		}
		if (screen != menuScreen) {
			menuScreen = screen;
			toldMenu.clear();
		}
		String title = TextUtil.cleanChat(screen.getTitle().getString());
		Map<TrophyBook, Map<Integer, int[]>> found = new LinkedHashMap<>();
		List<String> seen = new ArrayList<>();
		for (Slot slot : screen.getMenu().slots) {
			if (slot.container instanceof Inventory) continue;
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) continue;
			String item = TextUtil.cleanChat(stack.getHoverName().getString());
			List<String> lore = Items.lore(stack);
			if (seen.size() < 3) seen.add(item + " " + lore);
			for (TrophyBook b : books()) {
				int i = b.index(item);
				int[] tiers = i < 0 ? null : tiers(lore);
				if (tiers != null) found.computeIfAbsent(b, k -> new HashMap<>()).put(i, tiers);
			}
		}
		// Taz learns new trophy menus' layouts from the log.
		if ((title.contains("Trophy") || title.contains("Frog")) && !seen.isEmpty() && loggedMenus.add(title)) {
			Taz.LOGGER.info("Trophy menu \"{}\" holds {}", title, seen);
		}
		for (Map.Entry<TrophyBook, Map<Integer, int[]>> e : found.entrySet()) {
			if (e.getValue().size() < 2) continue;
			TrophyBook b = e.getKey();
			boolean first = !b.fromMenu();
			int[] before = b.counts().clone();
			b.setMenu(e.getValue());
			if (toldMenu.add(b) && (first || !Arrays.equals(before, b.counts()))) {
				TazChat.info("Updated " + e.getValue().size() + " " + b.name + " from " + (title.isEmpty() ? "the menu" : "\"" + title + "\"") + ".");
			}
		}
	}

	/** Each tier's count from a species' lore, or null when the lore doesn't list all four. */
	public static int[] tiers(List<String> lore) {
		int[] out = {-1, -1, -1, -1};
		for (String line : lore) {
			Matcher m = MENU_HAVE.matcher(line);
			if (m.matches()) {
				out[Tier.named(m.group(1)).ordinal()] = Integer.parseInt(m.group(2).replace(",", ""));
				continue;
			}
			m = MENU_NONE.matcher(line);
			if (m.matches()) out[Tier.named(m.group(1)).ordinal()] = 0;
		}
		for (int v : out) if (v < 0) return null;
		return out;
	}

	/** Species read from the menu just now (tests call this directly). */
	public void setMenu(Map<Integer, int[]> read) {
		Menu m = menu();
		long now = System.currentTimeMillis();
		for (Map.Entry<Integer, int[]> e : read.entrySet()) {
			System.arraycopy(e.getValue(), 0, m.counts, e.getKey() * T, T);
			m.at[e.getKey()] = now;
		}
		counts = null;
		saveMenus();
	}

	private void loadMenus() {
		if (menusLoaded) return;
		menusLoaded = true;
		try {
			if (!Files.exists(menuFile)) return;
			JsonObject profiles = JsonParser.parseString(Files.readString(menuFile, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("profiles");
			for (String p : profiles.keySet()) {
				JsonObject o = profiles.getAsJsonObject(p);
				Menu m = new Menu(size());
				JsonArray c = o.getAsJsonArray("counts"), at = o.getAsJsonArray("at");
				for (int i = 0; i < Math.min(c.size(), m.counts.length); i++) m.counts[i] = c.get(i).getAsInt();
				for (int i = 0; i < Math.min(at.size(), m.at.length); i++) m.at[i] = at.get(i).getAsLong();
				menus.put(p, m);
			}
		} catch (Exception e) {
			Taz.LOGGER.warn("Could not read {}", menuFile, e);
		}
	}

	private void saveMenus() {
		try {
			JsonObject profiles = new JsonObject();
			for (Map.Entry<String, Menu> e : menus.entrySet()) {
				JsonObject o = new JsonObject();
				JsonArray c = new JsonArray(), at = new JsonArray();
				for (int v : e.getValue().counts) c.add(v);
				for (long v : e.getValue().at) at.add(v);
				o.add("counts", c);
				o.add("at", at);
				profiles.add(e.getKey(), o);
			}
			JsonObject root = new JsonObject();
			root.addProperty("order", "species x tier: " + labels());
			root.add("profiles", profiles);
			Files.createDirectories(menuFile.getParent());
			Files.writeString(menuFile, root.toString(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			Taz.LOGGER.warn("Could not save {}", menuFile, e);
		}
	}

	private String labels() {
		List<String> out = new ArrayList<>();
		for (Entry e : entries) out.add(e.label);
		return String.join(", ", out);
	}

	// ---- reading the chat logs

	/** Reads every archived chat log a book hasn't read yet, once, in the background. */
	private static void importAll() {
		if (importing) return;
		List<TrophyBook> todo = new ArrayList<>();
		for (TrophyBook b : books()) if (!b.imported) todo.add(b);
		if (todo.isEmpty()) return;
		importing = true;
		Thread th = new Thread(() -> {
			Map<TrophyBook, Map<String, long[]>> read = readLogs(todo);
			Minecraft.getInstance().execute(() -> {
				for (TrophyBook b : todo) {
					b.logs.putAll(read.get(b));
					b.imported = true;
					b.counts = null;
				}
				importing = false;
			});
		}, "Taz-Trophies");
		th.setDaemon(true);
		th.start();
	}

	private static Map<TrophyBook, Map<String, long[]>> readLogs(List<TrophyBook> books) {
		Map<TrophyBook, Map<String, long[]>> out = new HashMap<>();
		for (TrophyBook b : books) out.put(b, b.loadLogCache());
		Path dir = FabricLoader.getInstance().getGameDir().resolve("logs");
		Set<TrophyBook> changed = new HashSet<>();
		try (Stream<Path> files = Files.isDirectory(dir) ? Files.list(dir) : Stream.empty()) {
			for (Path p : (Iterable<Path>) files::iterator) {
				String name = p.getFileName().toString();
				if (!LOG_FILE.matcher(name).matches()) continue;
				List<TrophyBook> need = new ArrayList<>();
				for (TrophyBook b : books) if (!out.get(b).containsKey(name)) need.add(b);
				if (need.isEmpty()) continue;
				Map<TrophyBook, long[]> found = readLog(p, need);
				for (TrophyBook b : need) out.get(b).put(name, found.get(b));
				changed.addAll(need);
			}
		} catch (Exception e) {
			Taz.LOGGER.warn("Could not read the chat logs for trophies", e);
		}
		for (TrophyBook b : changed) b.saveLogCache(out.get(b));
		return out;
	}

	/** Every catch in one chat log for these books, packed with its time. */
	static Map<TrophyBook, long[]> readLog(Path p, List<TrophyBook> books) {
		Map<TrophyBook, List<Long>> found = new HashMap<>();
		for (TrophyBook b : books) found.put(b, new ArrayList<>());
		Matcher date = LOG_FILE.matcher(p.getFileName().toString());
		LocalDate day = date.matches() ? LocalDate.parse(date.group(1)) : LocalDate.now();
		int last = -1;
		try (BufferedReader r = new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(p)), StandardCharsets.UTF_8))) {
			String raw;
			while ((raw = r.readLine()) != null) {
				int sec = secondOfDay(raw);
				if (sec < 0) continue;
				// A log that runs past midnight.
				if (last >= 0 && sec + 3600 < last) day = day.plusDays(1);
				last = sec;
				if (!raw.contains("TROPHY ")) continue;
				Matcher lm = LOG_LINE.matcher(raw);
				if (!lm.matches()) continue;
				Matcher m = CAUGHT.matcher(DUPLICATE_SUFFIX.matcher(TextUtil.cleanChat(lm.group(1))).replaceAll(""));
				if (!m.matches()) continue;
				for (TrophyBook b : books) {
					int i = b.word.equals(m.group(1)) ? b.index(m.group(2)) : -1;
					if (i < 0) continue;
					long ms = day.atTime(LocalTime.ofSecondOfDay(sec)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
					found.get(b).add(ms << 8 | (long) i * T + Tier.valueOf(m.group(3)).ordinal());
				}
			}
		} catch (Exception e) {
			Taz.LOGGER.warn("Could not read {}", p, e);
		}
		Map<TrophyBook, long[]> out = new HashMap<>();
		for (TrophyBook b : books) out.put(b, found.get(b).stream().mapToLong(Long::longValue).toArray());
		return out;
	}

	/** "[20:11:40] ..." to its second of the day, or -1. */
	private static int secondOfDay(String raw) {
		if (raw.length() < 10 || raw.charAt(0) != '[' || raw.charAt(3) != ':' || raw.charAt(6) != ':' || raw.charAt(9) != ']') return -1;
		try {
			return Integer.parseInt(raw, 1, 3, 10) * 3600 + Integer.parseInt(raw, 4, 6, 10) * 60 + Integer.parseInt(raw, 7, 9, 10);
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	/** How many of each species and tier one chat log holds (tests). */
	public int[] countLog(Path p) {
		int[] c = new int[size() * T];
		for (long e : readLog(p, List.of(this)).get(this)) c[(int) (e & 0xFF)]++;
		return c;
	}

	private Map<String, long[]> loadLogCache() {
		Map<String, long[]> out = new HashMap<>();
		try {
			if (!Files.exists(logCache)) return out;
			JsonObject root = JsonParser.parseString(Files.readString(logCache, StandardCharsets.UTF_8)).getAsJsonObject();
			if (!root.has("version") || root.get("version").getAsInt() != CACHE_VERSION) return out;
			JsonObject files = root.getAsJsonObject("files");
			for (String name : files.keySet()) {
				JsonArray a = files.getAsJsonArray(name);
				long[] c = new long[a.size()];
				for (int i = 0; i < c.length; i++) c[i] = a.get(i).getAsLong();
				out.put(name, c);
			}
		} catch (Exception e) {
			Taz.LOGGER.warn("Could not read {}", logCache, e);
		}
		return out;
	}

	private void saveLogCache(Map<String, long[]> files) {
		try {
			JsonObject f = new JsonObject();
			for (Map.Entry<String, long[]> e : files.entrySet()) {
				JsonArray a = new JsonArray();
				for (long v : e.getValue()) a.add(v);
				f.add(e.getKey(), a);
			}
			JsonObject root = new JsonObject();
			root.addProperty("version", CACHE_VERSION);
			root.addProperty("catches", "time in ms << 8 | species * 4 + tier; species: " + labels());
			root.add("files", f);
			Files.createDirectories(logCache.getParent());
			Files.writeString(logCache, root.toString(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			Taz.LOGGER.warn("Could not save {}", logCache, e);
		}
	}

	/** Forgets what was read and reads every chat log again. */
	public void reimport() {
		try {
			Files.deleteIfExists(logCache);
		} catch (Exception e) {
			Taz.LOGGER.warn("Could not delete {}", logCache, e);
		}
		logs.clear();
		imported = false;
		counts = null;
		importAll();
		TazChat.info("Reading your chat logs for " + name + " again.");
	}

	/** Tests: no logs, no menu, nothing caught. */
	public void resetForTest() {
		logs.clear();
		live.clear();
		menus.clear();
		menusLoaded = true;
		imported = true;
		importing = false;
		Arrays.fill(owned, false);
		counts = null;
		newFind = null;
	}
}
