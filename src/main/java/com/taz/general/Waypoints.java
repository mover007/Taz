package com.taz.general;

import com.taz.Taz;
import com.taz.dungeons.util.TextUtil;
import com.taz.render.WorldRenderer;
import com.taz.util.KeyWatcher;
import com.taz.util.TazChat;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Waypoints from coordinates in chat, and a key to share yours (General > Chat Waypoints). */
public final class Waypoints {
	/** "x: 12, y: 70, z: -5", "x 12 y 70 z -5". */
	private static final Pattern XYZ = Pattern.compile("(?i)\\bx:?\\s*(-?\\d{1,7})[,\\s]+y:?\\s*(-?\\d{1,3})[,\\s]+z:?\\s*(-?\\d{1,7})");
	/** "12 70 -5" or "12, 70, -5" (a y the world can have). */
	private static final Pattern BARE = Pattern.compile("(?<![\\w.-])(-?\\d{1,7})(?:,\\s*|\\s+)(-?\\d{1,3})(?:,\\s*|\\s+)(-?\\d{1,7})(?![\\w.])");
	/** A player's message: "Party > [MVP+] Steve: ...", "[312] [VIP] Steve: ...", "Guild > Steve [Rank]: ...". */
	private static final Pattern PLAYER_LINE = Pattern.compile("^(Party > |Guild > |Co-op > |Officer > )?(?:\\[\\d+] )?(?:\\S+ )?(?:\\[[^]]+] )?(\\w{1,16})(?: \\[[^]]+])?: (.+)$");
	private static final double BEAM_HEIGHT = 120;
	private static final KeyWatcher SHARE_KEY = new KeyWatcher();

	public record Waypoint(BlockPos pos, String name, long addedMs) {
	}

	private static final List<Waypoint> WAYPOINTS = new ArrayList<>();

	private Waypoints() {
	}

	public static List<Waypoint> all() {
		return WAYPOINTS;
	}

	public static void add(BlockPos pos, String name) {
		WAYPOINTS.removeIf(w -> w.pos.equals(pos));
		WAYPOINTS.add(new Waypoint(pos, name, System.currentTimeMillis()));
		if (WAYPOINTS.size() > 20) WAYPOINTS.remove(0);
	}

	public static void clear() {
		WAYPOINTS.clear();
		TazChat.info("Waypoints cleared.");
	}

	/** Coordinates in a player's chat line, or null. */
	static int[] coords(String line) {
		Matcher p = PLAYER_LINE.matcher(line);
		if (!p.find()) return null;
		String text = p.group(3);
		Matcher m = XYZ.matcher(text);
		if (!m.find()) {
			m = BARE.matcher(text);
			if (!m.find()) return null;
		}
		int y = Integer.parseInt(m.group(2));
		if (y < -64 || y > 320) return null;
		return new int[]{Integer.parseInt(m.group(1)), y, Integer.parseInt(m.group(3))};
	}

	private static String sender(String line) {
		Matcher p = PLAYER_LINE.matcher(line);
		return p.find() ? p.group(2) : "Waypoint";
	}

	/** A plain-text chat line: party coordinates become a waypoint straight away. */
	public static void onChat(String line) {
		WaypointsConfig cfg = WaypointsConfig.get();
		if (!cfg.enabled || !cfg.autoParty || !line.startsWith("Party > ")) return;
		int[] c = coords(line);
		if (c != null && !sender(line).equals(myName())) add(new BlockPos(c[0], c[1], c[2]), sender(line));
	}

	/** ClientReceiveMessageEvents.MODIFY_GAME: a [Waypoint] button after other coordinates. */
	public static Component addButton(Component message, boolean overlay) {
		WaypointsConfig cfg = WaypointsConfig.get();
		if (overlay || !cfg.enabled) return message;
		String line = TextUtil.cleanChat(message.getString());
		if (cfg.autoParty && line.startsWith("Party > ")) return message;
		int[] c = coords(line);
		if (c == null) return message;
		String command = "/taz waypoint " + c[0] + " " + c[1] + " " + c[2] + " " + sender(line);
		return message.copy().append(Component.literal(" [Waypoint]").withStyle(s -> s.withColor(ChatFormatting.AQUA)
				.withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Show a beam at " + c[0] + " " + c[1] + " " + c[2])))));
	}

	private static String myName() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player == null ? "" : mc.player.getName().getString();
	}

	/** Every client tick: the share key, and removing reached or old waypoints. */
	public static void tick(Minecraft mc) {
		WaypointsConfig cfg = WaypointsConfig.get();
		boolean share = SHARE_KEY.pressed(mc, cfg.shareKey);
		if (!cfg.enabled || mc.player == null) return;
		if (share) {
			BlockPos p = mc.player.blockPosition();
			TazChat.party("x: " + p.getX() + ", y: " + p.getY() + ", z: " + p.getZ());
		}
		long now = System.currentTimeMillis();
		double near = cfg.removeDistance * (double) cfg.removeDistance;
		WAYPOINTS.removeIf(w -> now - w.addedMs > cfg.expireMinutes * 60_000L
				|| mc.player.position().distanceToSqr(Vec3.atCenterOf(w.pos)) <= near);
	}

	/** On server change: coordinates belong to the server they were posted on. */
	public static void onServerChange() {
		WAYPOINTS.clear();
	}

	/** LevelRenderEvents.END_MAIN. */
	public static void render(LevelRenderContext context) {
		WaypointsConfig cfg = WaypointsConfig.get();
		if (!cfg.enabled || WAYPOINTS.isEmpty()) return;
		int color = cfg.argb();
		try (WorldRenderer.Batch batch = WorldRenderer.begin(context)) {
			for (Waypoint w : WAYPOINTS) {
				Vec3 base = Vec3.atBottomCenterOf(w.pos);
				AABB box = new AABB(w.pos);
				batch.filled(box, (color & 0xFFFFFF) | 0x40000000, true);
				batch.outline(box, color, 2.5f, true);
				batch.line(base, base.add(0, BEAM_HEIGHT, 0), (color & 0xFFFFFF) | 0xA0000000, 4.0f, true);
				batch.label(base.add(0, 1.6, 0), w.name + "  " + Math.round(Math.sqrt(batch.camera.distanceToSqr(base))) + "m", color, true);
			}
		} catch (Exception e) {
			Taz.LOGGER.error("Waypoints failed to draw", e);
		}
	}
}
