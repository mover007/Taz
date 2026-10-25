package com.taz.dungeons.config;

import com.taz.dungeons.dungeon.Floor;
import com.taz.dungeons.dungeon.Milestone;
import com.taz.dungeons.run.HistoryExport;
import com.taz.dungeons.run.RunHistory;
import com.taz.dungeons.run.RunRecord;
import com.taz.dungeons.run.SessionStats;
import com.taz.dungeons.util.TextUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Per-floor numbers from your history: PB, medians, every phase, and how accurate the portal estimate has been. */
public final class StatsScreen extends Screen {
	private static final int GOLD = 0xFFFFAA00, WHITE = 0xFFFFFFFF, GRAY = 0xFFAAAAAA, AQUA = 0xFF55FFFF, GREEN = 0xFF55FF55;
	private static Floor lastFloor;

	private final Screen parent;
	private Floor floor;
	private String status = "";

	private record Row(String label, String value, int color) {
	}

	public StatsScreen(Screen parent) {
		super(Component.literal("Dungeon Run Predictor - Stats"));
		this.parent = parent;
	}

	private List<Floor> floorsWithRuns() {
		long cutoff = ModConfig.get().historyCutoffMillis();
		List<Floor> out = new ArrayList<>();
		for (Floor f : Floor.values()) if (RunHistory.count(f, cutoff) > 0) out.add(f);
		return out;
	}

	@Override
	protected void init() {
		List<Floor> floors = floorsWithRuns();
		if (floor == null) floor = lastFloor != null && floors.contains(lastFloor) ? lastFloor
				: SessionStats.lastFloor() != null && floors.contains(SessionStats.lastFloor()) ? SessionStats.lastFloor()
				: floors.isEmpty() ? null : floors.get(floors.size() - 1);
		int w = 40, gap = 3;
		int x = width / 2 - (floors.size() * (w + gap) - gap) / 2;
		for (Floor f : floors) {
			Button b = Button.builder(Component.literal(f.id), btn -> {
				floor = f;
				lastFloor = f;
				rebuildWidgets();
			}).bounds(x, 24, w, 20).build();
			b.active = f != floor;
			addRenderableWidget(b);
			x += w + gap;
		}
		int fy = height - 28;
		addRenderableWidget(Button.builder(Component.literal("Export CSV"), b -> {
			try {
				Path p = HistoryExport.exportCsv();
				status = "Saved " + p.getFileName() + " in config/taz/dungeons";
			} catch (Exception e) {
				status = "Export failed: " + e.getMessage();
			}
		}).bounds(width / 2 - 104, fy, 100, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(width / 2 + 4, fy, 100, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		g.centeredText(font, title, width / 2, 8, GOLD);
		if (floor == null) {
			g.centeredText(font, "No runs recorded yet.", width / 2, 60, GRAY);
			return;
		}
		List<Row> left = summary(floor), right = phases(floor);
		int colW = Math.min(220, width / 2 - 20);
		drawRows(g, left, width / 2 - colW - 10, 54, colW);
		drawRows(g, right, width / 2 + 10, 54, colW);
		if (!status.isEmpty()) g.centeredText(font, status, width / 2, height - 42, GREEN);
	}

	private void drawRows(GuiGraphicsExtractor g, List<Row> rows, int x, int y, int w) {
		for (Row r : rows) {
			if (r.value() == null) {
				g.text(font, r.label(), x, y, r.color(), true);
			} else {
				g.text(font, r.label(), x, y, GRAY, true);
				g.text(font, r.value(), x + w - font.width(r.value()), y, r.color(), true);
			}
			y += font.lineHeight + 3;
		}
	}

	private static List<Row> summary(Floor f) {
		List<Row> rows = new ArrayList<>();
		long cutoff = ModConfig.get().historyCutoffMillis();
		List<RunRecord> all = RunHistory.recent(f, cutoff, Integer.MAX_VALUE);
		List<RunRecord> recent = RunHistory.recent(f, cutoff, 60);
		rows.add(new Row(f.displayName(), null, GOLD));
		rows.add(new Row("Runs recorded", Integer.toString(all.size()), WHITE));
		RunHistory.personalBest(f, cutoff).ifPresent(pb -> rows.add(new Row("Personal best", TextUtil.formatTimeTenths(pb), AQUA)));
		List<Long> totals = new ArrayList<>(), portals = new ArrayList<>();
		for (RunRecord r : recent) {
			totals.add(r.totalMs);
			Long p = r.split(Milestone.BOSS_ENTER);
			if (p != null) portals.add(p);
		}
		if (!totals.isEmpty()) {
			rows.add(new Row("Median (last 60)", TextUtil.formatTimeTenths(q(totals, 0.5)), WHITE));
			rows.add(new Row("Typical range", TextUtil.formatTime(q(totals, 0.1)) + " - " + TextUtil.formatTime(q(totals, 0.9)), WHITE));
		}
		if (!portals.isEmpty()) rows.add(new Row("Median portal time", TextUtil.formatTimeTenths(q(portals, 0.5)), WHITE));

		List<Long> errs = new ArrayList<>();
		for (int i = all.size() - 1; i >= 0 && errs.size() < 30; i--) {
			RunRecord r = all.get(i);
			if (r.predictedAtBossMs > 0) errs.add(Math.abs(r.totalMs - r.predictedAtBossMs));
		}
		if (!errs.isEmpty()) {
			long within5 = errs.stream().filter(e -> e <= 5000).count();
			long mean = errs.stream().mapToLong(Long::longValue).sum() / errs.size();
			rows.add(new Row("Portal estimate error", String.format("+/-%.1fs (last %d)", mean / 1000.0, errs.size()), GREEN));
			rows.add(new Row("Within 5 seconds", (100 * within5 / errs.size()) + "%", GREEN));
		}
		SessionStats.FloorSession s = SessionStats.get(f);
		if (s != null && s.runs() > 0) {
			rows.add(new Row("", null, GRAY));
			rows.add(new Row("This session", null, GOLD));
			rows.add(new Row("Runs", Integer.toString(s.runs()), WHITE));
			rows.add(new Row("Average / best", TextUtil.formatTime(s.averageMs()) + " / " + TextUtil.formatTimeTenths(s.bestMs), WHITE));
		}
		return rows;
	}

	/** Each segment: median of the last 60 runs, fastest 5% of the last 120, and best ever. */
	private static List<Row> phases(Floor f) {
		List<Row> rows = new ArrayList<>();
		long cutoff = ModConfig.get().historyCutoffMillis();
		List<RunRecord> all = RunHistory.recent(f, cutoff, Integer.MAX_VALUE);
		rows.add(new Row("Phase          median / fast / best", null, GOLD));
		for (int i = 0; i < f.segmentCount(); i++) {
			Milestone a = f.splits.get(i).milestone(), b = f.splits.get(i + 1).milestone();
			List<Long> d = new ArrayList<>();
			long best = Long.MAX_VALUE;
			for (int k = 0; k < all.size(); k++) {
				RunRecord r = all.get(k);
				Long ta = r.split(a), tb = r.split(b);
				if (ta == null || tb == null || tb <= ta) continue;
				best = Math.min(best, tb - ta);
				if (k >= all.size() - 120) d.add(tb - ta);
			}
			if (d.isEmpty()) continue;
			List<Long> last60 = d.subList(Math.max(0, d.size() - 60), d.size());
			rows.add(new Row(f.splits.get(i).segmentName(),
					sec(q(last60, 0.5)) + " / " + sec(q(d, 0.05)) + " / " + sec(best), WHITE));
		}
		return rows;
	}

	private static String sec(long ms) {
		return String.format("%.1fs", ms / 1000.0);
	}

	private static long q(List<Long> v, double q) {
		List<Long> s = new ArrayList<>(v);
		s.sort(null);
		return s.get((int) Math.round(q * (s.size() - 1)));
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
