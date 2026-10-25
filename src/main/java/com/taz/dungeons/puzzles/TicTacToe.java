package com.taz.dungeons.puzzles;

import com.taz.dungeons.secrets.DungeonRooms;
import com.taz.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Tic Tac Toe: the button for your best move (a minimax over the board read from the item-frame maps), in red. From
 * NoammAddons.
 */
final class TicTacToe {
	private static final char EMPTY = 0;
	private static final int[][] WINS = {{0, 1, 2}, {3, 4, 5}, {6, 7, 8}, {0, 3, 6}, {1, 4, 7}, {2, 5, 8}, {0, 4, 8}, {6, 4, 2}};
	private static final List<BlockPos> BEST = new ArrayList<>();
	private static DungeonRooms.Room room;
	private static int ticks;

	private TicTacToe() {
	}

	static void onRoomEnter(DungeonRooms.Room r) {
		if (r.data.name().equals("Tic Tac Toe")) room = r;
	}

	/** NoammAddons' rotation for the room (0 when the clay corner is north-west, then clockwise). */
	private static int rotation() {
		return switch (room.rotation) {
			case SOUTH -> 0;
			case WEST -> 90;
			case NORTH -> 180;
			case EAST -> 270;
		};
	}

	static void tick(Minecraft mc) {
		if (!Puzzles.cfg().ticTacToe || room == null || Puzzles.room("Tic Tac Toe") != room || ++ticks % 10 != 0) return;
		BlockPos c = room.toReal(new BlockPos(15, 0, 15));
		List<ItemFrame> frames = mc.level.getEntitiesOfClass(ItemFrame.class, new AABB(c.getX() - 9, 65, c.getZ() - 9, c.getX() + 9, 73, c.getZ() + 9),
				f -> f.getItem().getItem() instanceof MapItem && f.getItem().has(DataComponents.MAP_ID));
		if (frames.size() == 8) {
			BEST.clear();
			return;
		}
		if (frames.size() % 2 == 0) return;
		char[] board = new char[9];
		BlockPos leftmost = null;
		boolean alongX = true;
		int sign = 1;
		for (ItemFrame frame : frames) {
			MapId id = frame.getItem().get(DataComponents.MAP_ID);
			MapItemSavedData data = id == null ? null : mc.level.getMapData(id);
			if (data == null) continue;
			Direction dir = frame.getDirection();
			sign = dir == Direction.SOUTH || dir == Direction.WEST ? -1 : 1;
			BlockPos at = frame.blockPosition();
			int row = 0;
			for (int i = 2; i >= 0; i--) {
				int off = i * sign;
				BlockPos p;
				if (frame.getX() % 0.5 == 0) p = at.offset(off, 0, 0);
				else {
					alongX = false;
					p = at.offset(0, 0, off);
				}
				var block = mc.level.getBlockState(p).getBlock();
				if (block == Blocks.STONE_BUTTON || block == Blocks.AIR) {
					leftmost = p;
					row = i;
					break;
				}
			}
			int column = 72 - (int) frame.getY();
			if (column < 0 || column > 2) continue;
			int color = data.colors[8256] & 0xFF;
			if (color == 114) board[column * 3 + row] = 'X';
			else if (color == 33) board[column * 3 + row] = 'O';
		}
		if (leftmost == null) return;
		BEST.clear();
		for (int idx : best(board, 'O', 'X')) {
			int row = idx % 3, col = idx / 3;
			BEST.add(new BlockPos(alongX ? leftmost.getX() - sign * row : leftmost.getX(), 72 - col, alongX ? leftmost.getZ() : leftmost.getZ() - sign * row));
		}
	}

	static boolean won(char[] b) {
		for (int[] w : WINS) if (b[w[0]] != EMPTY && b[w[0]] == b[w[1]] && b[w[0]] == b[w[2]]) return true;
		return false;
	}

	/** The best moves for {@code me} (Noamm's minimax, based on gnoht.com's). */
	static List<Integer> best(char[] board, char me, char them) {
		List<Integer> moves = new ArrayList<>();
		double top = Double.NEGATIVE_INFINITY;
		double[] scores = new double[9];
		for (int m = 0; m < 9; m++) {
			if (board[m] != EMPTY) continue;
			char[] sim = board.clone();
			sim[m] = me;
			scores[m] = minimax(sim, me, them, false, 1);
			top = Math.max(top, scores[m]);
		}
		for (int m = 0; m < 9; m++) if (board[m] == EMPTY && scores[m] >= top - 0.0001) moves.add(m);
		return moves;
	}

	private static double minimax(char[] b, char me, char them, boolean maximizing, int depth) {
		boolean won = won(b);
		boolean full = true;
		for (char c : b) if (c == EMPTY) full = false;
		if (won) return maximizing ? -1.0 / depth : 1.0 / depth;
		if (full) return 0;
		double res = maximizing ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
		for (int m = 0; m < 9; m++) {
			if (b[m] != EMPTY) continue;
			char[] sim = b.clone();
			sim[m] = maximizing ? me : them;
			double s = minimax(sim, me, them, !maximizing, depth + 1);
			res = maximizing ? Math.max(res, s) : Math.min(res, s);
		}
		return res;
	}

	static void reset() {
		BEST.clear();
		room = null;
	}

	static void render(WorldRenderer.Batch batch) {
		Minecraft mc = Minecraft.getInstance();
		if (BEST.isEmpty() || room == null || Puzzles.room("Tic Tac Toe") != room) return;
		int rot = rotation();
		for (BlockPos p : BEST) {
			if (!mc.level.getBlockState(p).is(Blocks.STONE_BUTTON)) continue;
			double x = p.getX(), y = p.getY(), z = p.getZ(), hw = 0.2, hh = 0.13, t = 0.2;
			AABB box = switch (rot) {
				case 0 -> new AABB(x, y + 0.5 - hh, z + 0.5 - hw, x + t, y + 0.5 + hh, z + 0.5 + hw);
				case 90 -> new AABB(x + 0.5 - hw, y + 0.5 - hh, z, x + 0.5 + hw, y + 0.5 + hh, z + t);
				case 180 -> new AABB(x + 1 - t, y + 0.5 - hh, z + 0.5 - hw, x + 1, y + 0.5 + hh, z + 0.5 + hw);
				default -> new AABB(x + 0.5 - hw, y + 0.5 - hh, z + 1 - t, x + 0.5 + hw, y + 0.5 + hh, z + 1);
			};
			batch.filled(box.inflate(0.02), 0xC0FF2E2E, false);
		}
	}
}
