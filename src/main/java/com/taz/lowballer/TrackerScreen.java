package com.taz.lowballer;

import com.taz.general.AuctionInput;
import com.taz.hud.TazTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.ToLongFunction;

/**
 * The Lowball Tracker menu (/taz lowballs): your numbers on the left, every lowball on the right. A lowball is unsold
 * ("On AH" or "Not listed"), sold, or kept for yourself.
 */
public final class TrackerScreen extends Screen {
	private static final int PANEL = 0xF2140B0C, LINE = 0xFF4A1A1E, HOVER = 0x30FFFFFF, PICKED = 0x40FF2E2E;
	private static final int ROW_H = 22, LIST_TOP = 38;
	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ENGLISH);
	private static final List<Component> HELP = List.of(
			TazTheme.styled("§cHow the Lowball Tracker works"),
			TazTheme.styled("§7Every trade where you get items is a lowball: each item"),
			TazTheme.styled("§7gets a row with what you paid (your coins, split by worth)."),
			Component.empty(),
			TazTheme.styled("§fUnsold §7- you still have it:"),
			TazTheme.styled("§7 §6On AH§7: you listed it. Its price comes from SkyCofl."),
			TazTheme.styled("§7 §fNot listed§7: not on the AH yet."),
			TazTheme.styled("§fSold §7- Taz saw it sell on the AH, the bazaar, to an NPC"),
			TazTheme.styled("§7or in a trade, and took off the tax."),
			TazTheme.styled("§fKept §7- you're using it: left out of the profit."),
			Component.empty(),
			TazTheme.styled("§7Profit = sold for - tax - what you paid. §8≈§7 is what an"),
			TazTheme.styled("§7unsold item makes if it sells at its AH price (or its worth)."),
			Component.empty(),
			TazTheme.styled("§8Click a row to type in a sale, fix what you paid,"),
			TazTheme.styled("§8keep it or delete it. Click a column to sort."));

	private enum Tab {
		ALL("All"), UNSOLD("Unsold"), SOLD("Sold");

		final String label;

		Tab(String label) {
			this.label = label;
		}
	}

	private enum Period {
		ALL("All time"), MONTH("30 days"), WEEK("7 days"), DAY("Today");

		final String label;

		Period(String label) {
			this.label = label;
		}

		long since() {
			long now = System.currentTimeMillis();
			return switch (this) {
				case ALL -> 0;
				case MONTH -> now - 30 * 86_400_000L;
				case WEEK -> now - 7 * 86_400_000L;
				case DAY -> LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
			};
		}
	}

	private enum Sort {
		DATE(f -> f.boughtAt), PAID(f -> f.paid), PRICE(f -> f.status == Flip.Status.SOLD ? f.sold : f.askingPrice()), PROFIT(Flip::profitOrExpected);

		final ToLongFunction<Flip> key;

		Sort(ToLongFunction<Flip> key) {
			this.key = key;
		}
	}

	/** What you typed into the box: a sale, or what you paid. */
	private enum Edit {
		SOLD("Sold for, e.g. 950m"), PAID("You paid, e.g. 400m");

		final String hint;

		Edit(String hint) {
			this.hint = hint;
		}
	}

	/** Which list, period and order you looked at last. */
	private static Tab tab = Tab.ALL;
	private static Period period = Period.ALL;
	private static Sort sort = Sort.DATE;
	private static boolean ascending;

	private record Hit(int x, int y, int w, int h, Runnable action) {
	}

	private final List<Hit> hits = new ArrayList<>();
	private int px, py, pw, ph;
	/** The numbers' width and each money column's, narrower in a small window. */
	private int statsW, col;
	private int scroll;
	private Flip picked;
	private EditBox box, search;
	private Edit editing;
	private long deleteArmed;
	private String note;
	private long noteAt;
	private List<Component> tip;

	public TrackerScreen() {
		super(Component.literal("Lowball Tracker"));
	}

	@Override
	protected void init() {
		pw = Math.min(width - 12, 560);
		ph = Math.min(height - 12, 300);
		px = (width - pw) / 2;
		py = (height - ph) / 2;
		statsW = pw >= 480 ? 150 : 124;
		col = pw >= 480 ? 60 : 52;
		String typed = box == null ? "" : box.getValue(), query = search == null ? "" : search.getValue();
		boolean shown = box != null && box.visible;
		box = new EditBox(font, px + 8, py + ph - 20, statsW, 14, Component.literal("Amount"));
		box.setMaxLength(24);
		box.setValue(typed);
		box.visible = shown;
		if (editing != null) box.setHint(hint(editing.hint));
		addRenderableWidget(box);
		int searchX = px + 12 + font.width(title) + 16;
		search = new EditBox(font, searchX, py + 4, Math.min(130, pw / 4), 13, Component.literal("Search"));
		search.setMaxLength(40);
		search.setHint(hint("Search items or players"));
		search.setValue(query);
		search.setResponder(q -> scroll = 0);
		addRenderableWidget(search);
		LowballTracker.refresh(false);
		LowballTracker.syncListings(false);
	}

	private static Component hint(String text) {
		return Component.literal(text).withColor(TazTheme.DARK_GREY & 0xFFFFFF);
	}

	/** Test hooks: what the list shows, picking a lowball, searching, and the tab. */
	public List<Flip> rows() {
		long since = period.since();
		String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
		Comparator<Flip> order = Comparator.comparingLong(sort.key).thenComparingLong(f -> f.boughtAt).thenComparingLong(f -> f.id);
		return LowballTracker.flips().stream().filter(f -> switch (tab) {
			case ALL -> f.boughtAt >= since || f.status == Flip.Status.SOLD && f.soldAt >= since || f.open();
			case UNSOLD -> f.open();
			case SOLD -> f.status == Flip.Status.SOLD && f.soldAt >= since;
		}).filter(f -> q.isEmpty() || f.name.toLowerCase(Locale.ROOT).contains(q) || f.from.toLowerCase(Locale.ROOT).contains(q))
				.sorted(ascending ? order : order.reversed()).toList();
	}

	public void pick(Flip f) {
		picked = f;
		editing = null;
		box.visible = false;
		deleteArmed = 0;
	}

	public void search(String text) {
		search.setValue(text);
	}

	public void showTab(String name) {
		tab = Tab.valueOf(name);
		scroll = 0;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		tip = null;
		hits.clear();
		g.fill(px, py, px + pw, py + ph, PANEL);
		g.outline(px, py, pw, ph, TazTheme.DARK_RED);
		g.text(font, title, px + 8, py + 7, TazTheme.RED, false);
		int qx = px + 8 + font.width(title) + 5;
		boolean qHover = in(mouseX, mouseY, qx - 2, py + 5, 10, 12);
		g.text(font, "?", qx, py + 7, qHover ? TazTheme.RED : TazTheme.PALE_RED, false);
		if (qHover) tip = HELP;
		g.horizontalLine(px + 6, px + pw - 7, py + 19, LINE);
		g.verticalLine(px + statsW + 14, py + 24, py + ph - 7, LINE);
		if (!LowballTracker.loaded()) {
			search.visible = false;
			g.centeredText(font, "Join a world to see your lowballs.", px + pw / 2, py + ph / 2, TazTheme.GREY);
			super.extractRenderState(g, mouseX, mouseY, delta);
			return;
		}
		search.visible = true;
		LowballTracker.Stats all = LowballTracker.stats(period.since());
		tabs(g, all, mouseX, mouseY);
		int actionsTop = py + ph - 70;
		stats(g, all, mouseX, mouseY, actionsTop);
		actions(g, mouseX, mouseY, actionsTop);
		list(g, mouseX, mouseY);
		super.extractRenderState(g, mouseX, mouseY, delta);
		if (tip != null) g.setComponentTooltipForNextFrame(font, tip, mouseX, mouseY);
	}

	// ---- top: All / Unsold / Sold

	private void tabs(GuiGraphicsExtractor g, LowballTracker.Stats s, int mx, int my) {
		int x = px + pw - 8;
		Tab[] all = Tab.values();
		for (int i = all.length - 1; i >= 0; i--) {
			Tab t = all[i];
			String text = t == Tab.UNSOLD ? t.label + " (" + s.unsold() + ")" : t.label;
			x -= font.width(text);
			boolean hover = in(mx, my, x - 2, py + 5, font.width(text) + 4, 12);
			g.text(font, text, x, py + 7, t == tab ? TazTheme.RED : hover ? TazTheme.PALE_RED : TazTheme.GREY, false);
			if (t == tab) g.horizontalLine(x, x + font.width(text) - 1, py + 17, TazTheme.RED);
			if (hover) tip = List.of(Component.literal(switch (t) {
				case ALL -> "Everything bought or sold in the period, and all you haven't sold";
				case UNSOLD -> "Lowballs you still have: on the AH or not listed yet";
				case SOLD -> "Lowballs sold in the period";
			}));
			click(x - 2, py + 5, font.width(text) + 4, 12, () -> {
				tab = t;
				scroll = 0;
			});
			x -= 12;
		}
	}

	// ---- left: the numbers

	private void stats(GuiGraphicsExtractor g, LowballTracker.Stats s, int mx, int my, int limit) {
		int x = px + 8, y = py + 25;
		String label = "Period: " + period.label + " ▾";
		boolean hover = in(mx, my, x - 1, y - 1, font.width(label) + 2, 10);
		g.text(font, label, x, y, hover ? TazTheme.RED : TazTheme.PALE_RED, false);
		if (hover) tip = List.of(Component.literal("Click for another period"), hint("The numbers and the All and Sold lists use it"));
		click(x - 1, y - 1, font.width(label) + 2, 10, () -> {
			period = Period.values()[(period.ordinal() + 1) % Period.values().length];
			scroll = 0;
		});
		y += 14;
		Rows r = new Rows(g, mx, my, limit);
		y = r.header("SOLD", y);
		y = r.row("Profit", s.sold() == 0 ? "-" : signed(s.profit()), s.profit() < 0 ? TazTheme.RED : TazTheme.WHITE, y,
				"After tax, less what you paid");
		y = r.row("Sold", s.sold() == 0 ? "0" : s.sold() + " · " + Lowball.pct(s.winRate()) + " won", TazTheme.TEXT, y,
				"How many sold, and how many of them made money");
		if (s.sold() > 0) {
			y = r.row("Average", signed(s.profit() / s.sold()), TazTheme.TEXT, y, "Average profit per sale");
			y = r.row("Return", (s.roi() >= 0 ? "+" : "") + Lowball.pct(s.roi()), TazTheme.TEXT, y, "Profit compared with what you paid");
			y = r.row("Time to sell", LowballTracker.span(s.heldMs()), TazTheme.TEXT, y, "Average from the trade to the sale");
			y = r.flip("Best", s.best(), y);
			if (s.sold() > 1) y = r.flip("Worst", s.worst(), y);
		}
		y += 4;
		y = r.header("BOUGHT", y);
		y = r.row("Lowballs", s.bought() + " · " + coins(s.spent()), TazTheme.TEXT, y, "Items you got in trades, and what you paid for them");
		if (!Double.isNaN(s.under())) y = r.row("Under worth", Lowball.pct(s.under()), TazTheme.TEXT, y, "How far under their worth you paid, on average");
		y += 4;
		y = r.header("UNSOLD NOW", y);
		y = r.row("Items", s.unsold() == 0 ? "0" : s.unsold() + " · " + s.listed() + " on AH", TazTheme.TEXT, y, "Lowballs you still have");
		if (s.unsold() > 0) {
			y = r.row("Paid", coins(s.unsoldPaid()), TazTheme.TEXT, y, "What you paid for them");
			y = r.row("Selling for", s.unsoldPrice() > 0 ? coins(s.unsoldPrice()) : "?", TazTheme.TEXT, y,
					"Their AH prices, or their worth when not listed");
			r.row("If they sell", s.unsoldPrice() > 0 ? "≈" + signed(s.unsoldProfit()) : "?", s.unsoldProfit() < 0 ? TazTheme.RED : TazTheme.WHITE, y,
					"All of them added up: the profit if each sells at its price, after AH tax. One that would sell at a loss takes away from the rest.");
		}
	}

	/** The numbers' rows: label left, value right, each with a hover line; none drawn past {@code limit}. */
	private final class Rows {
		final GuiGraphicsExtractor g;
		final int mx, my, limit;

		Rows(GuiGraphicsExtractor g, int mx, int my, int limit) {
			this.g = g;
			this.mx = mx;
			this.my = my;
			this.limit = limit;
		}

		int header(String text, int y) {
			if (y + 9 > limit) return y;
			g.text(font, text, px + 8, y, TazTheme.DARK_RED, false);
			return y + 11;
		}

		int row(String label, String value, int color, int y, String about) {
			if (y + 9 > limit) return y;
			int x = px + 8;
			g.text(font, label, x, y, TazTheme.GREY, false);
			g.text(font, value, x + statsW - font.width(value), y, color, false);
			if (in(mx, my, x, y - 1, statsW, 10)) tip = List.of(Component.literal(label), hint(about));
			return y + 10;
		}

		int flip(String label, Flip f, int y) {
			int next = row(label, signed(f.profit()), f.profit() < 0 ? TazTheme.RED : TazTheme.WHITE, y, "");
			if (next > y && in(mx, my, px + 8, y - 1, statsW, 10)) tip = details(f);
			return next;
		}
	}

	// ---- left, at the bottom: the lowball you picked

	private void actions(GuiGraphicsExtractor g, int mx, int my, int top) {
		int x = px + 8, y = top;
		g.horizontalLine(px + 6, px + statsW + 10, y - 4, LINE);
		if (picked != null && !LowballTracker.flips().contains(picked)) pick(null);
		if (picked == null) {
			box.visible = false;
			String state = LowballTracker.pricing() ? "Checking prices…" : LowballTracker.problem();
			if (state != null) g.text(font, font.plainSubstrByWidth(state, statsW), x, y, TazTheme.GREY, false);
			else button(g, "Refresh prices", x, y, mx, my, "Ask SkyCofl for their worth and your AH listings now", () -> {
				LowballTracker.refresh(true);
				LowballTracker.syncListings(true);
			});
			button(g, "Export CSV", x, y + 13, mx, my, "Save every lowball as a spreadsheet", () -> {
				try {
					Path p = LowballTracker.exportCsv();
					note = "Saved " + p.getFileName() + " in config/taz/lowballer";
				} catch (Exception e) {
					note = "Couldn't save: " + e.getMessage();
				}
				noteAt = System.currentTimeMillis();
			});
			boolean fresh = note != null && System.currentTimeMillis() - noteAt < 6000;
			g.text(font, font.plainSubstrByWidth(fresh ? note : "Click a lowball to change it", statsW), x, y + 30,
					fresh ? TazTheme.WHITE : TazTheme.DARK_GREY, false);
			return;
		}
		g.text(font, font.plainSubstrByWidth(LowballTracker.label(picked), statsW), x, y, TazTheme.TEXT, false);
		y += 13;
		int bx = x;
		if (picked.open()) {
			bx = button(g, "Sold for…", bx, y, mx, my, "Type in what it sold for (a sale Taz didn't see)", () -> edit(Edit.SOLD)) + 10;
		} else if (picked.status == Flip.Status.SOLD) {
			bx = button(g, "Not sold", bx, y, mx, my, "It didn't sell after all: back to unsold", () -> LowballTracker.unsell(picked)) + 10;
		} else {
			bx = button(g, "Not kept", bx, y, mx, my, "You're selling it after all: back to unsold", () -> LowballTracker.keep(picked, false)) + 10;
		}
		button(g, "Fix paid…", bx, y, mx, my, "Type in what you paid for it; the rest of that trade's cost goes to its other items",
				() -> edit(Edit.PAID));
		y += 13;
		bx = x;
		if (picked.open()) bx = button(g, "Keep it", bx, y, mx, my, "You're using it, not selling it: left out of the profit",
				() -> LowballTracker.keep(picked, true)) + 10;
		boolean armed = System.currentTimeMillis() - deleteArmed < 3000;
		button(g, armed ? "Sure? Delete" : "Delete", bx, y, mx, my, "Remove it from the tracker", () -> {
			if (System.currentTimeMillis() - deleteArmed < 3000) {
				LowballTracker.delete(picked);
				pick(null);
			} else {
				deleteArmed = System.currentTimeMillis();
			}
		});
		if (box.visible) g.text(font, "Enter to save, Esc to cancel", x, py + ph - 31, TazTheme.DARK_GREY, false);
	}

	private void edit(Edit what) {
		editing = what;
		box.setHint(hint(what.hint));
		box.setValue("");
		box.visible = true;
		setFocused(box);
	}

	private int button(GuiGraphicsExtractor g, String text, int x, int y, int mx, int my, String about, Runnable action) {
		int w = font.width(text);
		boolean hover = in(mx, my, x - 1, y - 2, w + 2, 12);
		g.text(font, text, x, y, hover ? TazTheme.RED : TazTheme.PALE_RED, false);
		if (hover) tip = List.of(hint(about));
		click(x - 1, y - 2, w + 2, 12, action);
		return x + w;
	}

	// ---- right: the lowballs

	private void list(GuiGraphicsExtractor g, int mx, int my) {
		int lx = px + statsW + 22, right = px + pw - 8, top = py + LIST_TOP;
		int profitX = right, priceX = right - col, paidX = right - 2 * col;
		header(g, "Item", lx + 20, Sort.DATE, false, mx, my, "Sort by date");
		header(g, "Paid", paidX, Sort.PAID, true, mx, my, "Sort by what you paid");
		header(g, "Price", priceX, Sort.PRICE, true, mx, my, "What it sold for, or its AH price (≈ its worth when not listed)");
		header(g, "Profit", profitX, Sort.PROFIT, true, mx, my, "After tax. ≈: if it sells at that price");

		LowballerConfig cfg = LowballerConfig.get();
		List<Flip> rows = rows();
		int bottom = py + ph - 18, visible = Math.max(1, (bottom - top) / ROW_H);
		scroll = Math.max(0, Math.min(scroll, rows.size() - visible));
		if (rows.isEmpty()) empty(g, (lx + right) / 2, top + 24);
		for (int i = scroll; i < rows.size() && i < scroll + visible; i++) {
			Flip f = rows.get(i);
			int y = top + (i - scroll) * ROW_H;
			boolean hover = in(mx, my, lx - 2, y, right - lx + 4, ROW_H);
			if (f == picked) g.fill(lx - 2, y, right + 2, y + ROW_H, PICKED);
			else if (hover) g.fill(lx - 2, y, right + 2, y + ROW_H, HOVER);
			// Items from the same trade are joined by a bar when the list is by date.
			if (sort == Sort.DATE && f.tradeItems > 1) {
				boolean up = i > scroll && sameTrade(rows.get(i - 1), f), down = i + 1 < rows.size() && i + 1 < scroll + visible && sameTrade(rows.get(i + 1), f);
				g.fill(lx - 6, up ? y : y + 3, lx - 4, down ? y + ROW_H : y + ROW_H - 3, TazTheme.DARK_RED);
			}
			if (!f.stack.isEmpty()) g.item(f.stack, lx, y + 3);
			String under = under(f);
			int nameW = paidX - font.width(coins(f.paid)) - 6 - (lx + 20), statusW = paidX - font.width(under) - 6 - (lx + 20);
			g.text(font, font.plainSubstrByWidth(LowballTracker.label(f), nameW), lx + 20, y + 2, f.status == Flip.Status.KEPT ? TazTheme.GREY : TazTheme.TEXT, false);
			badge(g, f, lx + 20, y + 12, statusW);
			cell(g, coins(f.paid), paidX, y + 2, TazTheme.TEXT);
			cell(g, under, paidX, y + 12, TazTheme.DARK_GREY);
			switch (f.status) {
				case SOLD -> {
					cell(g, coins(f.sold), priceX, y + 2, TazTheme.TEXT);
					cell(g, signed(f.profit()), profitX, y + 2, f.profit() < 0 ? TazTheme.RED : TazTheme.WHITE);
					cell(g, roi(f), profitX, y + 12, TazTheme.DARK_GREY);
				}
				case KEPT -> {
					cell(g, "-", priceX, y + 2, TazTheme.DARK_GREY);
					cell(g, "-", profitX, y + 2, TazTheme.DARK_GREY);
				}
				default -> {
					boolean listed = f.status == Flip.Status.LISTED && f.listPrice > 0;
					long price = f.askingPrice();
					cell(g, price <= 0 ? "?" : (listed ? "" : "≈") + coins(price), priceX, y + 2, listed ? TazTheme.LIGHT_RED : TazTheme.GREY);
					cell(g, listed ? "on AH" : price > 0 ? "worth" : "", priceX, y + 12, TazTheme.DARK_GREY);
					if (price > 0) {
						cell(g, "≈" + signed(f.expectedProfit()), profitX, y + 2, f.expectedProfit() < 0 ? TazTheme.RED : TazTheme.GREY);
						cell(g, "if it sells", profitX, y + 12, TazTheme.DARK_GREY);
					}
				}
			}
			if (hover) tip = details(f);
			click(lx - 2, y, right - lx + 4, ROW_H, () -> pick(picked == f ? null : f));
		}
		if (!cfg.enabled || !cfg.track) {
			g.text(font, "Tracking is off: /taz → Lowballer", lx, py + ph - 13, TazTheme.RED, false);
		} else if (rows.size() > visible) {
			String more = (scroll + 1) + "-" + Math.min(rows.size(), scroll + visible) + " of " + rows.size() + " · scroll";
			g.text(font, more, lx, py + ph - 13, TazTheme.DARK_GREY, false);
		}
		String credit = "Prices: SkyCofl";
		int cw = font.width(credit), cx = right - cw, cy = py + ph - 13;
		boolean hover = in(mx, my, cx - 1, cy - 1, cw + 2, 10);
		g.text(font, credit, cx, cy, hover ? TazTheme.PALE_RED : TazTheme.DARK_GREY, false);
		if (hover) tip = List.of(Component.literal("Prices and your AH listings provided by SkyCofl (sky.coflnet.com)"),
				hint("Taz isn't affiliated with SkyCofl or Hypixel."));
		click(cx - 1, cy - 1, cw + 2, 10, () -> ConfirmLinkScreen.confirmLinkNow(this, "https://sky.coflnet.com"));
	}

	/** A column title you click to sort by it (again: the other way). */
	private void header(GuiGraphicsExtractor g, String text, int x, Sort by, boolean rightAligned, int mx, int my, String about) {
		String shown = sort == by ? text + (ascending ? " ▲" : " ▼") : text;
		int w = font.width(shown), left = rightAligned ? x - w : x;
		boolean hover = in(mx, my, left - 2, py + 23, w + 4, 11);
		g.text(font, shown, left, py + 25, sort == by ? TazTheme.PALE_RED : hover ? TazTheme.GREY : TazTheme.DARK_GREY, false);
		if (hover) tip = List.of(Component.literal(about));
		click(left - 2, py + 23, w + 4, 11, () -> {
			if (sort == by) ascending = !ascending;
			else {
				sort = by;
				ascending = false;
			}
			scroll = 0;
		});
	}

	/** The coloured line under its name: on the AH, not listed, sold, or kept. */
	private void badge(GuiGraphicsExtractor g, Flip f, int x, int y, int width) {
		long now = System.currentTimeMillis();
		String tag, rest;
		int color;
		switch (f.status) {
			case LISTED -> {
				tag = "On AH";
				rest = " · " + LowballTracker.span(now - f.listedAt);
				color = TazTheme.LIGHT_RED;
			}
			case HOLDING -> {
				tag = "Not listed";
				rest = " · got " + LowballTracker.span(now - f.boughtAt) + " ago";
				color = TazTheme.GREY;
			}
			case SOLD -> {
				tag = "Sold";
				rest = " · " + how(f) + " · held " + LowballTracker.span(f.soldAt - f.boughtAt);
				color = TazTheme.WHITE;
			}
			default -> {
				tag = "Kept";
				rest = " · yours";
				color = TazTheme.DARK_GREY;
			}
		}
		g.text(font, tag, x, y, color, false);
		g.text(font, font.plainSubstrByWidth(rest, Math.max(0, width - font.width(tag))), x + font.width(tag), y, TazTheme.DARK_GREY, false);
	}

	private static String how(Flip f) {
		return switch (f.soldHow) {
			case "BIN" -> "BIN";
			case "Auction" -> "auction";
			case "Bazaar" -> "bazaar";
			case "NPC" -> "NPC";
			case "Trade" -> "trade";
			default -> "by you";
		};
	}

	private void empty(GuiGraphicsExtractor g, int cx, int y) {
		List<String> lines = LowballTracker.flips().isEmpty()
				? List.of("No lowballs yet.", "", "Trade for items with the Lowballer on:", "each one shows up here with what you paid.",
						"List it on the AH and Taz marks it sold,", "with your profit after tax.")
				: List.of(!search.getValue().isBlank() ? "Nothing matches \"" + search.getValue().trim() + "\""
						: tab == Tab.UNSOLD ? "Everything's sold. Nice." : "Nothing in this period");
		for (String line : lines) {
			g.centeredText(font, line, cx, y, line.startsWith("No lowballs") ? TazTheme.TEXT : TazTheme.GREY);
			y += 11;
		}
	}

	private void cell(GuiGraphicsExtractor g, String text, int rightX, int y, int color) {
		g.text(font, text, rightX - font.width(text), y, color, false);
	}

	private static boolean sameTrade(Flip a, Flip b) {
		return a.boughtAt == b.boughtAt && a.from.equals(b.from);
	}

	/** Its tooltip: the item, then the lowball, then its trade. */
	private List<Component> details(Flip f) {
		List<Component> out = new ArrayList<>();
		if (!f.stack.isEmpty()) out.addAll(getTooltipFromItem(minecraft, f.stack));
		else out.add(Component.literal(LowballTracker.label(f)));
		out.add(Component.empty());
		long now = System.currentTimeMillis();
		out.add(line("§7Got from §f" + f.from + "§7, " + date(f.boughtAt)));
		out.add(line("§7Paid: §6" + coins(f.paid) + (Double.isNaN(f.under()) ? "" : " §8(" + under(f) + " its worth)")
				+ (f.manualPaid ? " §8· typed in" : "")));
		if (f.worth > 0) out.add(line("§7Worth then: §6" + coins(f.worth) + (f.bazaar ? " §8(bazaar)" : f.volume >= 0 ? " §8· sells " + Lowball.perDay(f.volume) : "")));
		if (f.open() && f.worthNow > 0) out.add(line("§7Worth now: §6" + coins(f.worthNow) + " §8(" + LowballTracker.span(now - f.worthNowAt) + " ago)"));
		switch (f.status) {
			case LISTED -> {
				out.add(line("§7On the AH" + (f.listPrice > 0 ? " for §6" + coins(f.listPrice) : " §8(price coming from SkyCofl)") + " §8· listed "
						+ LowballTracker.span(now - f.listedAt) + " ago"));
				if (f.askingPrice() > 0) out.add(line("§7If it sells: " + money(f.expectedProfit()) + " §8after " + Lowball.pct(Lowball.tax(f.askingPrice(), false)) + " AH tax"));
			}
			case HOLDING -> {
				out.add(line("§7Not listed yet"));
				if (f.askingPrice() > 0) out.add(line("§7If it sells at its worth: " + money(f.expectedProfit()) + " §8after tax"));
			}
			case SOLD -> {
				String how = switch (f.soldHow) {
					case "By hand" -> "§8(typed in by you)";
					case "Trade" -> "§8in a trade with " + f.soldTo;
					default -> "§8on " + how(f) + (f.soldTo.isEmpty() ? "" : " to " + f.soldTo);
				};
				out.add(line("§7Sold for §6" + coins(f.sold) + " " + how + "§8, " + date(f.soldAt)));
				if (f.fees > 0) out.add(line("§7Tax and fees: §c-" + coins(f.fees) + (f.collected || !f.soldHow.equals("BIN") && !f.soldHow.equals("Auction") ? "" : " §8(estimated)")));
				String pct = roi(f).isEmpty() ? "" : " §8(" + roi(f) + ")";
				out.add(line("§7Profit: " + money(f.profit()) + pct + " §7· held " + LowballTracker.span(f.soldAt - f.boughtAt)));
			}
			case KEPT -> out.add(line("§7Kept for yourself: left out of the profit"));
		}
		List<Flip> trade = LowballTracker.flips().stream().filter(x -> sameTrade(x, f)).toList();
		if (trade.size() > 1) {
			long sold = 0, profit = 0;
			int left = 0;
			for (Flip x : trade) {
				if (x.status == Flip.Status.SOLD) sold += x.sold;
				else if (x.open()) left++;
				profit += x.status == Flip.Status.KEPT ? 0 : x.profitOrExpected();
			}
			out.add(Component.empty());
			out.add(line("§7The whole trade: §6" + coins(f.tradePaid) + " §7for " + f.tradeItems + " items"));
			out.add(line("§7Sold so far §6" + coins(sold) + (left > 0 ? " §7· " + left + " unsold" : "")));
			out.add(line("§7Profit" + (left > 0 ? " if the rest sell: §8≈" : ": ") + money(profit)));
		}
		out.add(line(f == picked ? "§8Click to unpick" : "§8Click to change it"));
		return out;
	}

	/** "14.9% under" (its worth), or "12% over" when you paid more; "" when its worth is unknown. */
	private static String under(Flip f) {
		double u = f.under();
		return Double.isNaN(u) ? "" : u >= 0 ? Lowball.pct(u) + " under" : Lowball.pct(-u) + " over";
	}

	/** Its return ("+23.3%"); "" when it cost next to nothing (the % would mean nothing). */
	private static String roi(Flip f) {
		if (f.paid <= 0 || Math.abs(f.profit()) > 100.0 * f.paid) return "";
		return (f.profit() >= 0 ? "+" : "") + Lowball.pct(f.profit() / (double) f.paid);
	}

	private static Component line(String text) {
		return TazTheme.styled(text);
	}

	private static String money(long coins) {
		return (coins < 0 ? "§c" : "§a") + signed(coins);
	}

	private static String date(long ms) {
		return WHEN.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()));
	}

	private static String coins(long coins) {
		return LowballTracker.coins(coins);
	}

	private static String signed(long coins) {
		return (coins < 0 ? "-" : "+") + LowballTracker.coins(Math.abs(coins));
	}

	// ---- input

	private void click(int x, int y, int w, int h, Runnable action) {
		hits.add(new Hit(x, y, w, h, action));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (box.visible && box.isMouseOver(event.x(), event.y()) || search.isMouseOver(event.x(), event.y())) {
			return super.mouseClicked(event, doubleClick);
		}
		if (event.button() == 0) {
			for (Hit h : List.copyOf(hits)) {
				if (in((int) event.x(), (int) event.y(), h.x, h.y, h.w, h.h)) {
					h.action.run();
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX < px + statsW + 14) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		scroll = Math.max(0, scroll + (scrollY > 0 ? -1 : scrollY < 0 ? 1 : 0));
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (box.visible && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
			Long v = AuctionInput.parse(box.getValue());
			if (v != null && v >= 0 && picked != null) {
				if (editing == Edit.PAID) LowballTracker.editPaid(picked, v);
				else LowballTracker.sellByHand(picked, v);
				box.visible = false;
				editing = null;
			}
			return true;
		}
		if (box.visible && event.key() == GLFW.GLFW_KEY_ESCAPE) {
			box.visible = false;
			editing = null;
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private static boolean in(int mx, int my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}
}
