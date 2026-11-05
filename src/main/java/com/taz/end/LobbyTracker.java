package com.taz.end;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.taz.Taz;
import com.taz.foraging.GalateaRecorder;
import com.taz.hud.HudPos;
import com.taz.hud.TextHud;
import com.taz.util.Alert;
import com.taz.util.TazChat;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The End lobbies you've been in, by the server id on the sidebar's date line ("11/04/26 m147AS"), with the protector's stage
 * in each, and an alert the moment you land in one again, for lobby hopping. Saved in config/taz/end-lobbies.json.
 */
public final class LobbyTracker extends TextHud {
	private static final Pattern SERVER = Pattern.compile("\\d\\d/\\d\\d/\\d\\d\\s+([mM]\\w+)");
	private static final Path FILE = Taz.CONFIG_DIR.resolve("end-lobbies.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int KEEP = 50;
	/** Right after a lobby change the last lobby's sidebar can still be up for a moment. */
	public static final long STALE_MS = 2000;

	/** An End lobby you've been in. */
	public static final class Lobby {
		public String id;
		public long firstAt;
		/** When you were last in it (while you're there, now). */
		public long lastAt;
		/** The protector's stage there when you were last in it ({@link EndStoneProtector.Stage} name), and when Taz saw it. */
		public String stage;
		public long stageAt;
		/** When it was last killed while you were there (0 = not seen). */
		public long killedAt;
		public int visits = 1;
	}

	/** Test hook: the sidebar's server id (null = read the sidebar). */
	public static volatile String testId;

	private static List<Lobby> lobbies;
	private static Lobby current;
	/** How long you'd been away from this lobby (0 = it's new to you), and the protector's stage when you left it. */
	private static long away;
	private static String was;
	/** The server id on the sidebar when you changed server, and whether the sidebar was blank since. */
	private static String before;
	private static String sidebarId;
	private static long switchedAt;
	private static boolean sawEmpty;

	public LobbyTracker() {
		super("End Lobbies", 0.01, 0.56);
	}

	public static Lobby current() {
		return current;
	}

	/** How long you'd been away from the lobby you're in, or 0 when it's new to you. */
	public static long away() {
		return away;
	}

	public static List<Lobby> lobbies() {
		if (lobbies == null) {
			lobbies = new ArrayList<>();
			try {
				if (Files.exists(FILE)) {
					List<Lobby> read = GSON.fromJson(Files.readString(FILE), new TypeToken<List<Lobby>>() {
					}.getType());
					if (read != null) read.stream().filter(l -> l != null && l.id != null).forEach(lobbies::add);
				}
			} catch (Exception e) {
				Taz.LOGGER.error("Couldn't read your End lobbies", e);
			}
		}
		return lobbies;
	}

	static void onServerChange() {
		leave(System.currentTimeMillis());
		before = sidebarId;
		switchedAt = System.currentTimeMillis();
		sawEmpty = false;
	}

	static void tick(Minecraft mc, boolean inEnd) {
		long now = System.currentTimeMillis();
		String id = serverId();
		if (id == null) sawEmpty = true;
		else sidebarId = id;
		if (current != null) {
			if (id == null || id.equals(current.id)) {
				current.lastAt = now;
				return;
			}
			leave(now);
		}
		if (!inEnd || id == null) return;
		if (id.equals(before) && !sawEmpty && now - switchedAt < STALE_MS) return;
		arrive(id, now);
	}

	private static String serverId() {
		if (testId != null) return testId;
		for (String line : GalateaRecorder.sidebarLines()) {
			Matcher m = SERVER.matcher(line);
			if (m.find()) return m.group(1);
		}
		return null;
	}

	private static void arrive(String id, long now) {
		ProtectorConfig cfg = ProtectorConfig.get();
		prune(now);
		Lobby l = null;
		for (Lobby x : lobbies()) {
			if (x.id.equals(id)) l = x;
		}
		away = 0;
		was = null;
		if (l == null) {
			l = new Lobby();
			l.id = id;
			l.firstAt = now;
		} else {
			lobbies.remove(l);
			away = Math.max(1, now - l.lastAt);
			was = describe(l, now);
			l.visits++;
		}
		l.lastAt = now;
		// The stage read just before the lobby was known is this lobby's.
		EndStoneProtector.Stage s = EndStoneProtector.stage();
		if (s != null) {
			l.stage = s.name();
			l.stageAt = now;
		}
		lobbies.add(0, l);
		while (lobbies.size() > KEEP) lobbies.remove(lobbies.size() - 1);
		current = l;
		save();
		if (away > 0 && cfg.sameLobby) {
			Alert.show("§c§lSAME LOBBY", "§7" + id + " · here " + EndStoneProtector.clock(away) + " ago · " + was, cfg.sound);
			TazChat.info("You've been in this End lobby (" + id + ") before: you left it " + EndStoneProtector.clock(away) + " ago, "
					+ "when the protector was " + plain(was) + ".");
		}
	}

	private static void leave(long now) {
		if (current != null) {
			current.lastAt = now;
			save();
		}
		current = null;
		away = 0;
		was = null;
	}

	static void onStage(EndStoneProtector.Stage s) {
		if (current == null) return;
		current.stage = s.name();
		current.stageAt = System.currentTimeMillis();
		save();
	}

	static void onKilled() {
		if (current == null) return;
		current.killedAt = System.currentTimeMillis();
		save();
	}

	/** Clears the lobbies you've been in (the Forget button). */
	public static void forget() {
		int n = lobbies().size();
		lobbies.clear();
		current = null;
		away = 0;
		was = null;
		save();
		TazChat.info("Forgot " + n + (n == 1 ? " End lobby." : " End lobbies."));
	}

	/** For tests: read the lobbies from the file again. */
	public static void reloadForTest() {
		lobbies = null;
	}

	private static void prune(long now) {
		long keep = ProtectorConfig.get().minutes * 60_000L;
		lobbies().removeIf(l -> l != current && now - l.lastAt > keep);
	}

	private static void save() {
		try {
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, GSON.toJson(lobbies()));
		} catch (Exception e) {
			Taz.LOGGER.error("Couldn't save your End lobbies", e);
		}
	}

	/** The protector there when you were last in it: "§e3 Disturbed", or "§akilled 4m ago". */
	private static String describe(Lobby l, long now) {
		if (l.killedAt > 0 && l.killedAt >= l.stageAt) return "§akilled " + EndStoneProtector.clock(now - l.killedAt) + " ago";
		EndStoneProtector.Stage s = stage(l);
		return s == null ? "§8stage not seen" : "stage " + s.shown();
	}

	private static EndStoneProtector.Stage stage(Lobby l) {
		try {
			return l.stage == null ? null : EndStoneProtector.Stage.valueOf(l.stage);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static String plain(String s) {
		return s.replaceAll("§.", "");
	}

	/** The stage HUD's lobby line: which lobby you're in and whether you've been in it. */
	static String hudLine() {
		if (current == null) return "§7Lobby §8reading the sidebar…";
		if (away > 0) return "§7Lobby §f" + current.id + " §8· §cyou were here " + EndStoneProtector.clock(away) + " ago";
		return "§7Lobby §f" + current.id + " §8· §anew";
	}

	@Override
	protected HudPos pos() {
		return ProtectorConfig.get().lobbyPos;
	}

	@Override
	public boolean enabled() {
		ProtectorConfig cfg = ProtectorConfig.get();
		return cfg.enabled && cfg.lobbyList;
	}

	@Override
	protected List<String> lines(boolean preview) {
		List<String> out = new ArrayList<>();
		long now = System.currentTimeMillis();
		if (preview && !EndStoneProtector.here()) {
			out.add("§5§lEnd Lobbies");
			out.add("§a» §fm147AS §8· §e3 Disturbed §8· here");
			out.add("§fm92P §8· §71 Dormant §8· 4m 10s ago");
			out.add("§fm83DR §8· §akilled 12m ago §8· 9m 30s ago");
			return out;
		}
		if (!EndStoneProtector.here() || lobbies().isEmpty()) return out;
		out.add("§5§lEnd Lobbies");
		int shown = 0;
		for (Lobby l : lobbies) {
			if (shown++ >= ProtectorConfig.get().shown) break;
			if (l == current) {
				EndStoneProtector.Stage s = EndStoneProtector.stage();
				out.add("§a» §f" + l.id + " §8· " + (s == null ? "§8stage not seen" : s.shown()) + " §8· here");
			} else {
				out.add("§f" + l.id + " §8· " + describe(l, now).replace("stage ", "") + " §8· " + EndStoneProtector.clock(now - l.lastAt) + " ago");
			}
		}
		return out;
	}
}
