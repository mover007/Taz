package com.taz.dungeons.map;

import com.taz.dungeons.secrets.DungeonRooms;
import com.taz.dungeons.util.DungeonLocation;
import com.taz.dungeons.util.DungeonParty;
import com.taz.hud.HudPos;
import com.taz.hud.MovableHud;
import com.taz.hud.TazTheme;
import com.taz.hud.TextHud;
import com.taz.mixin.MapItemSavedDataAccessor;
import com.taz.util.Items;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dungeon Map: Hypixel's own dungeon map (the one in your last hotbar slot) redrawn bigger and clearer: each room in its
 * type's colour, wither and blood doors, a tick, cross or "?" per room (or the room's name and its secrets), and every
 * teammate's face where they are, with their name. It only shows what Hypixel's map shows; room names come from the rooms
 * Taz has recognised around you. From NoammAddons' Dungeon Map (the legit one).
 */
public final class DungeonMap implements MovableHud {
	enum Type {
		BLOOD(0xFFB20000), FAIRY(0xFFE39BE2), RARE(0xFFB2B2B2), CHAMPION(0xFFFFC800), PUZZLE(0xFF7B007B), TRAP(0xFFFF8200), NORMAL(0xFF794600),
		ENTRANCE(0xFF00FF00);

		final int color;

		Type(int color) {
			this.color = color;
		}

		static Type ofColor(int c) {
			return switch (c) {
				case 18 -> BLOOD;
				case 82 -> FAIRY;
				case 34 -> RARE;
				case 74 -> CHAMPION;
				case 66 -> PUZZLE;
				case 62 -> TRAP;
				case 63, 85 -> NORMAL;
				case 30 -> ENTRANCE;
				default -> null;
			};
		}
	}

	enum State {
		GREEN, CLEARED, DISCOVERED, FAILED, UNOPENED
	}

	enum Kind {
		NONE, ROOM, SPLIT, DOOR
	}

	/** One of the 11 x 11 cells: rooms on even x and z, the gaps between them on odd ones. */
	record Cell(Kind kind, Type type, State state, int doorColor) {
		static final Cell EMPTY = new Cell(Kind.NONE, null, null, 0);
	}

	private static final int WITHER_DOOR = 0xFF101010, UNOPENED = 0xFF414141, BACKGROUND = 0x90000000;
	private static final Pattern SECRETS = Pattern.compile("(\\d+)/(\\d+) Secrets");
	private static final Cell[] CELLS = new Cell[121];
	/** Room name -> secrets found (from the action bar while you were in it). */
	private static final Map<String, Integer> FOUND = new HashMap<>();
	/** Marker key -> where its face is drawn now (eased toward the marker). */
	private static final Map<String, float[]> SHOWN = new HashMap<>();
	private static ClientLevel level;
	private static int roomSize = 16, startX = 5, startZ = 5;
	private static boolean calibrated;
	private static MapItemSavedData data;

	static {
		java.util.Arrays.fill(CELLS, Cell.EMPTY);
	}

	public static void clear() {
		java.util.Arrays.fill(CELLS, Cell.EMPTY);
		FOUND.clear();
		SHOWN.clear();
		calibrated = false;
		data = null;
		roomSize = 16;
		startX = startZ = 5;
	}

	/** Every client tick: read the hotbar map. */
	public static void tick(Minecraft mc) {
		if (mc.level != level) {
			level = mc.level;
			clear();
		}
		if (!DungeonMapConfig.get().enabled || mc.player == null || mc.level == null || !DungeonLocation.inDungeon()) return;
		ItemStack stack = mc.player.getInventory().getItem(8);
		MapId id = stack.get(DataComponents.MAP_ID);
		MapItemSavedData map = id == null ? null : mc.level.getMapData(id);
		if (map == null) return;
		data = map;
		if (!calibrated && !calibrate(map.colors)) return;
		if (!DungeonLocation.inBoss()) read(map.colors);
	}

	/** Finds the green entrance room to learn the room size and where the grid starts. */
	static boolean calibrate(byte[] colors) {
		int start = 0, length = 0, bestStart = 0, best = 0;
		for (int i = 0; i < colors.length; i++) {
			if (colors[i] == 30) {
				if (length == 0) start = i;
				length++;
			} else {
				if (length >= 16) {
					best = length;
					bestStart = start;
					break;
				}
				length = 0;
			}
		}
		if (best == 0 && length >= 16) {
			best = length;
			bestStart = start;
		}
		if (best != 16 && best != 18) return false;
		roomSize = best;
		int floor = DungeonLocation.floorNumber();
		boolean entrance = "E".equals(DungeonLocation.floor());
		if (entrance) {
			startX = startZ = 22;
		} else if (floor == 1) {
			startX = 22;
			startZ = 11;
		} else if (floor == 2 || floor == 3) {
			startX = startZ = 11;
		} else {
			startX = (bestStart & 127) % (roomSize + 4);
			startZ = (bestStart >> 7) % (roomSize + 4);
		}
		calibrated = true;
		return true;
	}

	/** Reads each cell from the map's pixels (Hypixel's colours: 30 entrance, 18 blood, 66 puzzle...). */
	static void read(byte[] colors) {
		int half = roomSize / 2, halfTile = half + 2;
		for (int x = 0; x <= 10; x++) {
			for (int z = 0; z <= 10; z++) {
				int mx = startX + half + x * halfTile, mz = startZ + half + z * halfTile;
				if (mx >= 128 || mz >= 128) continue;
				int center = colors[mz * 128 + mx] & 0xFF;
				int sideIndex;
				if (x % 2 == 0 && z % 2 == 0) sideIndex = (mz - half) * 128 + (mx - half);
				else if (z % 2 == 1) sideIndex = mz * 128 + mx - 4;
				else sideIndex = (mz - 4) * 128 + mx;
				int side = sideIndex >= 0 && sideIndex < colors.length ? colors[sideIndex] & 0xFF : 0;
				CELLS[z * 11 + x] = cell(x, z, center, side);
			}
		}
	}

	private static Cell cell(int x, int z, int center, int side) {
		if (center == 0) return Cell.EMPTY;
		if (x % 2 == 0 && z % 2 == 0) {
			Type type = Type.ofColor(side);
			if (type == null) return Cell.EMPTY;
			State state = switch (center) {
				case 18 -> type == Type.BLOOD ? State.DISCOVERED : type == Type.PUZZLE ? State.FAILED : State.DISCOVERED;
				case 30 -> type == Type.ENTRANCE ? State.DISCOVERED : State.GREEN;
				case 34 -> State.CLEARED;
				case 85, 119 -> State.UNOPENED;
				default -> State.DISCOVERED;
			};
			return new Cell(Kind.ROOM, type, state, 0);
		}
		if (side == 0) {
			int color = switch (center) {
				case 18 -> Type.BLOOD.color;
				case 30 -> Type.ENTRANCE.color;
				case 119 -> WITHER_DOOR;
				case 85 -> UNOPENED;
				case 74, 82, 66, 62, 63 -> Type.NORMAL.color;
				default -> 0;
			};
			return color == 0 ? Cell.EMPTY : new Cell(Kind.DOOR, null, center == 85 ? State.UNOPENED : State.DISCOVERED, color);
		}
		Type type = Type.ofColor(side);
		return type == null ? Cell.EMPTY : new Cell(Kind.SPLIT, type, State.DISCOVERED, 0);
	}

	/** The action bar: secrets found in the room you're in. */
	public static void onActionBar(String text) {
		Matcher m = SECRETS.matcher(text);
		DungeonRooms.Room room = DungeonRooms.current();
		if (m.find() && room != null) FOUND.put(room.data.name(), Integer.parseInt(m.group(1)));
	}

	/** A world position on the map (pixels from its top-left). */
	static float[] toMap(double x, double z) {
		double mult = (roomSize + 4.0) / 32.0;
		return new float[] {(float) ((x + 185 + 15) * mult + startX), (float) ((z + 185 + 15) * mult + startZ)};
	}

	/** Test hooks: read a made-up map, and what a cell turned out to be ("ROOM ENTRANCE DISCOVERED"). */
	public static boolean testRead(byte[] colors) {
		calibrated = false;
		if (!calibrate(colors)) return false;
		read(colors);
		return true;
	}

	public static String describe(int x, int z) {
		Cell c = CELLS[z * 11 + x];
		return c.kind() + (c.type() == null ? "" : " " + c.type()) + (c.state() == null ? "" : " " + c.state());
	}

	public static boolean calibrated() {
		return calibrated;
	}

	public static int roomSize() {
		return roomSize;
	}

	// ---- the HUD

	private final HudPos defaults = new HudPos(0.005, 0.30);

	@Override
	public String name() {
		return "Dungeon Map";
	}

	@Override
	public boolean enabled() {
		return DungeonMapConfig.get().enabled;
	}

	@Override
	public boolean hidden() {
		return DungeonMapConfig.get().hud.hidden;
	}

	@Override
	public void setHidden(boolean hidden) {
		DungeonMapConfig.get().hud.hidden = hidden;
	}

	@Override
	public float scale() {
		return DungeonMapConfig.get().hud.scale;
	}

	@Override
	public void setScale(float scale) {
		DungeonMapConfig.get().hud.scale = TextHud.clampScale(scale);
	}

	@Override
	public void reset() {
		HudPos p = DungeonMapConfig.get().hud;
		p.x = defaults.x;
		p.y = defaults.y;
		p.scale = 1f;
	}

	private static boolean showing(boolean preview) {
		DungeonMapConfig cfg = DungeonMapConfig.get();
		if (preview) return true;
		return cfg.enabled && DungeonLocation.inDungeon() && calibrated && !(DungeonLocation.inBoss() && cfg.hideInBoss);
	}

	@Override
	public int[] bounds(Font font, int screenW, int screenH, boolean preview) {
		if (!showing(preview)) return null;
		HudPos p = DungeonMapConfig.get().hud;
		float s = TextHud.clampScale(p.scale);
		int size = Math.round(128 * s);
		int x = (int) Math.round(p.x * screenW), y = (int) Math.round(p.y * screenH);
		x = Math.max(0, Math.min(x, screenW - size));
		y = Math.max(0, Math.min(y, screenH - size));
		return new int[] {x, y, size, size};
	}

	@Override
	public void moveTo(int x, int y, int screenW, int screenH) {
		HudPos p = DungeonMapConfig.get().hud;
		p.x = screenW > 0 ? x / (double) screenW : 0;
		p.y = screenH > 0 ? y / (double) screenH : 0;
	}

	@Override
	public void draw(GuiGraphicsExtractor g, Font font, boolean preview) {
		int[] b = bounds(font, g.guiWidth(), g.guiHeight(), preview);
		if (b == null) return;
		boolean sample = preview && !calibrated;
		g.pose().pushMatrix();
		g.pose().translate(b[0], b[1]);
		float s = TextHud.clampScale(DungeonMapConfig.get().hud.scale);
		g.pose().scale(s, s);
		g.fill(0, 0, 128, 128, BACKGROUND);
		frame(g, 0, 0, 128, 128, TazTheme.RED);
		if (sample) {
			drawSample(g, font);
		} else {
			g.pose().pushMatrix();
			g.pose().translate(startX, startZ);
			drawRooms(g);
			drawText(g, font);
			g.pose().popMatrix();
			if (!DungeonLocation.inBoss()) drawPlayers(g, font);
		}
		g.pose().popMatrix();
	}

	private static void frame(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		g.fill(x, y, x + w, y + 1, color);
		g.fill(x, y + h - 1, x + w, y + h, color);
		g.fill(x, y, x + 1, y + h, color);
		g.fill(x + w - 1, y, x + w, y + h, color);
	}

	private static int roomColor(Cell c) {
		return c.state() == State.UNOPENED ? UNOPENED : c.type().color;
	}

	private static void drawRooms(GuiGraphicsExtractor g) {
		int gap = roomSize / 4, step = roomSize + gap;
		int door = 6, doorOffset = (roomSize - door) / 2;
		for (int z = 0; z <= 10; z++) {
			for (int x = 0; x <= 10; x++) {
				Cell c = CELLS[z * 11 + x];
				if (c.kind() == Kind.NONE) continue;
				int ox = (x >> 1) * step, oz = (z >> 1) * step;
				boolean xEven = x % 2 == 0, zEven = z % 2 == 0;
				if (xEven && zEven) {
					g.fill(ox, oz, ox + roomSize, oz + roomSize, roomColor(c));
				} else if (!xEven && !zEven) {
					g.fill(ox, oz, ox + roomSize + gap, oz + roomSize + gap, roomColor(c));
				} else {
					int color = c.kind() == Kind.DOOR ? c.doorColor() : roomColor(c);
					boolean vertical = !xEven;
					int len = c.kind() == Kind.DOOR ? door : roomSize;
					int x1 = vertical ? ox + roomSize : ox + (c.kind() == Kind.DOOR ? doorOffset : 0);
					int z1 = vertical ? oz + (c.kind() == Kind.DOOR ? doorOffset : 0) : oz + roomSize;
					g.fill(x1, z1, x1 + (vertical ? gap : len), z1 + (vertical ? len : gap), color);
				}
			}
		}
	}

	private static int stateColor(State s) {
		return switch (s) {
			case GREEN -> 0xFF55FF55;
			case FAILED -> 0xFFFF2E2E;
			case CLEARED -> 0xFFFFFFFF;
			default -> 0xFFA8A8A8;
		};
	}

	private static void drawText(GuiGraphicsExtractor g, Font font) {
		DungeonMapConfig cfg = DungeonMapConfig.get();
		int gap = roomSize / 4, step = roomSize + gap;
		java.util.Set<DungeonRooms.Room> named = new java.util.HashSet<>();
		for (int z = 0; z <= 10; z += 2) {
			for (int x = 0; x <= 10; x += 2) {
				Cell c = CELLS[z * 11 + x];
				if (c.kind() != Kind.ROOM || c.type() == Type.ENTRANCE || c.type() == Type.FAIRY) continue;
				if (c.type() == Type.BLOOD && cfg.hideBlood) continue;
				DungeonRooms.Room room = DungeonRooms.at(x / 2, z / 2);
				boolean known = room != null && c.state() != State.UNOPENED;
				if (known && !named.add(room)) continue;
				float cx, cz;
				if (known && room.tiles().size() > 1) {
					float sx = 0, sz = 0;
					for (int[] t : room.tiles()) {
						sx += t[0] * step + roomSize / 2f;
						sz += t[1] * step + roomSize / 2f;
					}
					cx = sx / room.tiles().size();
					cz = sz / room.tiles().size();
				} else {
					cx = (x / 2) * step + roomSize / 2f;
					cz = (z / 2) * step + roomSize / 2f;
				}
				List<String> lines = new ArrayList<>();
				DungeonMapConfig.Style style = cfg.style;
				int secrets = known ? room.data.secrets() : 0;
				if (known && (style == DungeonMapConfig.Style.NAME || style == DungeonMapConfig.Style.NAME_SECRETS)) {
					lines.addAll(List.of(room.data.name().split(" ")));
				}
				if (known && secrets > 0 && (style == DungeonMapConfig.Style.SECRETS || style == DungeonMapConfig.Style.NAME_SECRETS)) {
					lines.add(FOUND.getOrDefault(room.data.name(), 0) + "/" + secrets);
				}
				if (lines.isEmpty()) {
					String mark = switch (c.state()) {
						case GREEN, CLEARED -> "✔";
						case FAILED -> "✖";
						case UNOPENED -> "?";
						default -> "";
					};
					if (!mark.isEmpty()) lines.add(mark);
				}
				drawLines(g, font, lines, cx, cz, stateColor(c.state()), room == null ? roomSize : roomSize * Math.max(1, (int) Math.round(Math.sqrt(room.tiles().size()))));
			}
		}
	}

	private static void drawLines(GuiGraphicsExtractor g, Font font, List<String> lines, float cx, float cz, int color, int maxWidth) {
		if (lines.isEmpty()) return;
		float scale = DungeonMapConfig.get().textScale;
		int widest = 0;
		for (String l : lines) widest = Math.max(widest, font.width(l));
		if (widest * scale > maxWidth + 2) scale = Math.max(0.4f, (maxWidth + 2f) / widest);
		float lineH = 9 * scale;
		float y = cz - lines.size() * lineH / 2;
		for (String l : lines) {
			g.pose().pushMatrix();
			g.pose().translate(cx, y);
			g.pose().scale(scale, scale);
			TazTheme.outlined(g, font, Component.literal(l), -font.width(l) / 2, 0, color, 0xFF000000);
			g.pose().popMatrix();
			y += lineH;
		}
	}

	private static void drawPlayers(GuiGraphicsExtractor g, Font font) {
		Minecraft mc = Minecraft.getInstance();
		if (data == null || mc.player == null || mc.getConnection() == null) return;
		DungeonMapConfig cfg = DungeonMapConfig.get();
		String me = mc.player.getName().getString();
		List<String> living = new ArrayList<>();
		for (String n : DungeonParty.order()) if (!n.equalsIgnoreCase(me) && !DungeonParty.dead(n)) living.add(n);
		boolean names = cfg.names == DungeonMapConfig.Names.ALWAYS
				|| cfg.names == DungeonMapConfig.Names.HOLDING_LEAP && Items.id(mc.player.getMainHandItem()).contains("SPIRIT_LEAP");
		for (Map.Entry<String, MapDecoration> e : ((MapItemSavedDataAccessor) data).taz$decorations().entrySet()) {
			MapDecoration d = e.getValue();
			if (d.type().value() == MapDecorationTypes.FRAME.value()) continue;
			String key = e.getKey();
			char last = key.isEmpty() ? ' ' : key.charAt(key.length() - 1);
			int index = Character.isDigit(last) ? last - '0' : -1;
			if (index < 0 || index >= living.size()) continue;
			float tx = (d.x() + 128) >> 1, tz = (d.y() + 128) >> 1;
			float[] at = SHOWN.computeIfAbsent(key, k -> new float[] {tx, tz});
			at[0] += (tx - at[0]) * 0.25f;
			at[1] += (tz - at[1]) * 0.25f;
			String name = living.get(index);
			Player p = mc.level.getPlayerByUUID(info(name) == null ? new java.util.UUID(0, 0) : info(name).getProfile().id());
			if (p != null) {
				float[] real = toMap(p.getX(), p.getZ());
				at[0] = real[0];
				at[1] = real[1];
			}
			head(g, font, name, at[0], at[1], p != null ? p.getYRot() : d.rot() * 22.5f, 0xFF000000, names);
		}
		float[] mine = toMap(mc.player.getX(), mc.player.getZ());
		head(g, font, me, mine[0], mine[1], mc.player.getYRot(), TazTheme.RED, names && cfg.ownName);
	}

	private static PlayerInfo info(String name) {
		Minecraft mc = Minecraft.getInstance();
		return mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(name);
	}

	private static void head(GuiGraphicsExtractor g, Font font, String name, float x, float z, float yaw, int border, boolean showName) {
		DungeonMapConfig cfg = DungeonMapConfig.get();
		g.pose().pushMatrix();
		g.pose().translate(x, z);
		g.pose().pushMatrix();
		g.pose().rotate((float) Math.toRadians(yaw + 180));
		g.pose().scale(cfg.headScale, cfg.headScale);
		g.fill(-7, -7, 7, 7, border);
		PlayerInfo info = info(name);
		if (info != null) PlayerFaceExtractor.extractRenderState(g, info.getSkin(), -6, -6, 12);
		else g.fill(-6, -6, 6, 6, 0xFF6E5A5A);
		g.pose().popMatrix();
		if (showName) {
			g.pose().translate(0, 8 * cfg.headScale);
			g.pose().scale(cfg.nameScale, cfg.nameScale);
			TazTheme.outlined(g, font, Component.literal(name), -font.width(name) / 2, 0, TazTheme.TEXT, 0xFF000000);
		}
		g.pose().popMatrix();
	}

	/** The HUD editor's sample: a small made-up dungeon. */
	private static void drawSample(GuiGraphicsExtractor g, Font font) {
		int r = 16, gap = 4, step = r + gap, o = 5;
		int[][] rooms = {{0, 0, Type.ENTRANCE.color}, {1, 0, Type.NORMAL.color}, {2, 0, Type.PUZZLE.color}, {1, 1, Type.NORMAL.color},
				{2, 1, Type.CHAMPION.color}, {3, 1, Type.NORMAL.color}, {3, 2, Type.BLOOD.color}, {0, 1, Type.TRAP.color}, {1, 2, UNOPENED}};
		for (int[] room : rooms) g.fill(o + room[0] * step, o + room[1] * step, o + room[0] * step + r, o + room[1] * step + r, room[2]);
		g.fill(o + r, o + 5, o + r + gap, o + 11, Type.NORMAL.color);
		g.fill(o + step + 5, o + r, o + step + 11, o + r + gap, WITHER_DOOR);
		drawLines(g, font, List.of("✔"), o + step + r / 2f, o + r / 2f, 0xFF55FF55, r);
		drawLines(g, font, List.of("?"), o + step + r / 2f, o + 2 * step + r / 2f, 0xFFA8A8A8, r);
		head(g, font, "You", o + step + 8, o + step + 8, 0, TazTheme.RED, false);
	}
}
