package com.taz.inferno;

import com.taz.general.AuctionInput;
import com.taz.hud.TazTheme;
import com.taz.lowballer.Lowball;
import com.taz.lowballer.LowballTracker;
import com.taz.util.TazChat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import org.lwjgl.glfw.GLFW;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Inferno minion menu (/taz inferno): your numbers on the left, and six lists: each fuel you put in, your minions,
 * what you collected, what you bought, the best fuel to run, and the Fuel Planner (the cheapest way to make any fuel).
 */
public final class InfernoScreen extends Screen {
	private static final int PANEL = 0xF2140B0C, LINE = 0xFF4A1A1E, HOVER = 0x30FFFFFF, PICKED = 0x40FF2E2E;
	private static final int ROW_H = 22, LIST_TOP = 38;
	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ENGLISH);
	private static final List<Component> HELP = List.of(
			TazTheme.styled("§cHow the Inferno tracker works"),
			TazTheme.styled("§7Buy fuel parts on the bazaar (or a fuel on the AH) and"),
			TazTheme.styled("§7craft it: the fuel costs what you paid for its parts."),
			TazTheme.styled("§7Put it in a minion: it's a row in §fFuels§7, with what its"),
			TazTheme.styled("§724 hours should make (§8≈§7) at SkyCofl prices."),
			TazTheme.styled("§7Collect from the minion: what you took out is §fCollected§7,"),
			TazTheme.styled("§7worth what it sells for after tax."),
			Component.empty(),
			TazTheme.styled("§fBest fuel §7- the profit a day of every fuel."),
			TazTheme.styled("§fPlanner §7- the cheapest way to make any fuel, and"),
			TazTheme.styled("§7exactly what to buy for as many as you want."),
			Component.empty(),
			TazTheme.styled("§8Taz knows a minion when you right-click it, so open"),
			TazTheme.styled("§8each one once. Click a row to change or delete it."));

	private enum Tab {
		FUELS("Fuels", "Fuels"), MINIONS("Minions", "Minions"), DROPS("Drops", "Drops"), BOUGHT("Bought", "Bought"), BEST("Best fuel", "Best"),
		PLANNER("Planner", "Plan"), CALC("Calculator", "Calc");

		final String label;
		/** In a narrow window, so the tabs don't run into the title. */
		final String shortLabel;

		Tab(String label, String shortLabel) {
			this.label = label;
			this.shortLabel = shortLabel;
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

	/** Which list and period you looked at last, and the planner's fuel. */
	private static Tab tab = Tab.FUELS;
	private static Period period = Period.ALL;
	private static InfernoData.Grade grade = InfernoData.Grade.HYPERGOLIC;
	private static InfernoData.Kind kind = InfernoData.Kind.CRUDE_GABAGOOL;
	private static int amount = 1;
	/** The Best fuel list's minion tier (0: your best minion's) and whether it counts eyedrops. */
	private static int bestTier;
	private static boolean bestEyedrops;

	private record Hit(int x, int y, int w, int h, Runnable action) {
	}

	/** A row of the Bought list: a bazaar buy, or a fuel bought on the AH. */
	private record Buy(long at, String id, int count, long coins, int left, String how, Object source) {
	}

	private final List<Hit> hits = new ArrayList<>();
	private int px, py, pw, ph, statsW, col, scroll;
	private Object picked;
	private EditBox box, amountBox, countBox;
	private long deleteArmed;
	/** What the box is for: a fuel's cost, or items you collected that Taz didn't see. */
	private boolean typingCollected;
	private String note;
	private long noteAt;
	private List<Component> tip;

	public InfernoScreen() {
		super(Component.literal("Inferno Minions"));
	}

	@Override
	protected void init() {
		pw = Math.min(width - 12, 600);
		ph = Math.min(height - 12, 300);
		px = (width - pw) / 2;
		py = (height - ph) / 2;
		statsW = pw >= 500 ? 150 : 124;
		// Four money columns at most; narrower in a small window, so names still fit.
		col = Math.max(48, Math.min(62, (pw - statsW - 30 - 130) / 4));
		String typed = box == null ? "" : box.getValue();
		boolean shown = box != null && box.visible;
		box = new EditBox(font, px + 8, py + ph - 20, statsW, 14, Component.literal("Cost"));
		box.setMaxLength(24);
		box.setValue(typed);
		box.visible = shown;
		box.setHint(hint("It cost, e.g. 4.5m"));
		addRenderableWidget(box);
		amountBox = new EditBox(font, px + 8 + statsW - 46, py + 81, 46, 12, Component.literal("How many"));
		amountBox.setMaxLength(5);
		amountBox.setValue(String.valueOf(amount));
		amountBox.setResponder(s -> {
			String digits = s.replaceAll("\\D", "");
			try {
				amount = digits.isEmpty() ? 1 : Math.max(1, Math.min(9999, Integer.parseInt(digits)));
			} catch (NumberFormatException e) {
				amount = 1;
			}
			scroll = 0;
		});
		addRenderableWidget(amountBox);
		countBox = new EditBox(font, px + 8 + statsW - 46, py + 85, 46, 12, Component.literal("Minions"));
		countBox.setMaxLength(3);
		countBox.setValue(String.valueOf(InfernoConfig.get().calc.count));
		countBox.setResponder(s -> {
			String digits = s.replaceAll("\\D", "");
			InfernoConfig.get().calc.count = digits.isEmpty() ? 1 : Math.max(1, Math.min(100, Integer.parseInt(digits)));
		});
		addRenderableWidget(countBox);
		InfernoTracker.open();
		InfernoTracker.refresh(false);
	}

	private static Component hint(String text) {
		return Component.literal(text).withColor(TazTheme.DARK_GREY & 0xFFFFFF);
	}

	// ---- test hooks

	public void showTab(String name) {
		tab = Tab.valueOf(name);
		scroll = 0;
		picked = null;
	}

	public void plan(String fuelId, int n) {
		InfernoData.Fuel f = InfernoData.fuel(fuelId);
		grade = f.grade();
		kind = f.kind();
		amountBox.setValue(String.valueOf(n));
		tab = Tab.PLANNER;
	}

	public int rowCount() {
		return switch (tab) {
			case FUELS -> runs().size();
			case MINIONS -> InfernoTracker.minions().size();
			case DROPS -> drops().size();
			case BOUGHT -> buys().size();
			case BEST -> InfernoData.Kind.values().length * InfernoData.Grade.values().length;
			case PLANNER -> plan().buy().size() + plan().steps().size();
			case CALC -> calcRows(InfernoCalc.day(InfernoConfig.get().calc)).size();
		};
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
		amountBox.visible = tab == Tab.PLANNER && InfernoTracker.loaded();
		countBox.visible = tab == Tab.CALC && InfernoTracker.loaded();
		if (!InfernoTracker.loaded()) {
			g.centeredText(font, "Join a world to see your Inferno minions.", px + pw / 2, py + ph / 2, TazTheme.GREY);
			super.extractRenderState(g, mouseX, mouseY, delta);
			return;
		}
		tabs(g, mouseX, mouseY);
		int actionsTop = py + ph - 58;
		switch (tab) {
			case BEST -> bestLeft(g, mouseX, mouseY, actionsTop);
			case PLANNER -> plannerLeft(g, mouseX, mouseY, actionsTop);
			case CALC -> calcLeft(g, mouseX, mouseY, actionsTop);
			default -> stats(g, mouseX, mouseY, actionsTop);
		}
		actions(g, mouseX, mouseY, actionsTop);
		list(g, mouseX, mouseY);
		super.extractRenderState(g, mouseX, mouseY, delta);
		if (tip != null) g.setComponentTooltipForNextFrame(font, tip, mouseX, mouseY);
	}

	// ---- top: the tabs

	private void tabs(GuiGraphicsExtractor g, int mx, int my) {
		int x = px + pw - 8;
		Tab[] all = Tab.values();
		int full = (all.length - 1) * 10;
		for (Tab t : all) full += font.width(t.label);
		boolean narrow = x - full < px + 8 + font.width(title) + 18;
		for (int i = all.length - 1; i >= 0; i--) {
			Tab t = all[i];
			String label = narrow ? t.shortLabel : t.label;
			x -= font.width(label);
			boolean hover = in(mx, my, x - 2, py + 5, font.width(label) + 4, 12);
			g.text(font, label, x, py + 7, t == tab ? TazTheme.RED : hover ? TazTheme.PALE_RED : TazTheme.GREY, false);
			if (t == tab) g.horizontalLine(x, x + font.width(label) - 1, py + 17, TazTheme.RED);
			if (hover) tip = List.of(Component.literal(switch (t) {
				case FUELS -> "Every fuel you put in a minion: cost, what it should make, what you collected";
				case MINIONS -> "Your Inferno minions: their fuel and profit";
				case DROPS -> "What you collected, and rare drops against what you'd expect";
				case BOUGHT -> "Fuel parts and fuels you bought";
				case BEST -> "The profit a day of every fuel";
				case PLANNER -> "The cheapest way to make a fuel, and what to buy for it";
				case CALC -> "A day of Inferno minions with any setup: what they make and cost, item by item";
			}));
			click(x - 2, py + 5, font.width(label) + 4, 12, () -> {
				tab = t;
				scroll = 0;
				picked = null;
				box.visible = false;
			});
			x -= 10;
		}
	}

	// ---- left: your numbers

	private void stats(GuiGraphicsExtractor g, int mx, int my, int limit) {
		int x = px + 8, y = py + 25;
		String label = "Period: " + period.label + " ▾";
		boolean hover = in(mx, my, x - 1, y - 1, font.width(label) + 2, 10);
		g.text(font, label, x, y, hover ? TazTheme.RED : TazTheme.PALE_RED, false);
		if (hover) tip = List.of(Component.literal("Click for another period"), hint("Fuels put in, and things collected, sold and bought in it"));
		click(x - 1, y - 1, font.width(label) + 2, 10, () -> {
			period = Period.values()[(period.ordinal() + 1) % Period.values().length];
			scroll = 0;
		});
		y += 14;
		InfernoTracker.Stats s = InfernoTracker.stats(period.since());
		Rows r = new Rows(g, mx, my, limit);
		y = r.header("SPENT", y);
		y = r.row("Fuels", s.fuels() + " · " + coins(s.fuelCost()), TazTheme.TEXT, y, "Fuels you put in, at what they cost you");
		if (s.eyedrops() > 0) y = r.row("Eyedrops", s.eyedrops() + " · " + coins(s.eyedropsCost()), TazTheme.TEXT, y, "Capsaicin Eyedrops you used");
		y += 3;
		y = r.header("MADE", y);
		y = r.row("Collected", coins(s.made()), TazTheme.TEXT, y, "What you collected from your minions, worth what it sells for after tax");
		y = r.row("Sold", coins(s.sold()), TazTheme.TEXT, y, "Coins from selling Gabagool, Chili Peppers and the rare drops");
		y = r.row("Profit", signed(s.profit()), s.profit() < 0 ? TazTheme.RED : TazTheme.WHITE, y,
				"Collected less spent. Fuels still burning haven't made it all yet");
		if (s.expected() > 0) y = r.row("Expected", "≈" + signed(s.expected() - s.spent()), TazTheme.GREY, y,
				"If every fuel makes what it should in its 24 hours (and you collect it all)");
		y += 3;
		y = r.header("NOW", y);
		y = r.row("Burning", s.running() + (s.running() == 1 ? " fuel" : " fuels"), TazTheme.TEXT, y, "Fuels in your minions now, or waiting their turn");
		if (s.running() > 0) r.row("A day", "≈" + signed(s.runningProfit()), s.runningProfit() < 0 ? TazTheme.RED : TazTheme.GREY, y,
				"What the fuels in your minions should make, less what they cost");
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
			return row(label, value, color, y, about, null);
		}

		int row(String label, String value, int color, int y, String about, Runnable action) {
			if (y + 9 > limit) return y;
			int x = px + 8;
			boolean hover = in(mx, my, x, y - 1, statsW, 10);
			g.text(font, label, x, y, TazTheme.GREY, false);
			g.text(font, value, x + statsW - font.width(value), y, action != null && hover ? TazTheme.RED : color, false);
			if (hover) tip = List.of(Component.literal(label), hint(about));
			if (action != null) click(x, y - 1, statsW, 10, action);
			return y + 10;
		}
	}

	// ---- left on Best fuel: the minion it's for

	private void bestLeft(GuiGraphicsExtractor g, int mx, int my, int limit) {
		Rows r = new Rows(g, mx, my, limit);
		InfernoTracker.Minion m = InfernoTracker.best();
		int tier = tier(m);
		int y = r.header("FOR A MINION LIKE", py + 25);
		y = r.row("Tier", InfernoData.roman(tier) + " ▾", TazTheme.PALE_RED, y, "Click for another tier (it starts at your best minion's)", () ->
				bestTier = tier % 11 + 1);
		y = r.row("Speed", "+" + Math.round(InfernoTracker.boost(m) * 100) + "%", TazTheme.TEXT, y,
				"Rising Celsius, Mithril Infusion, your best minion's Flycatchers and the Other speed setting");
		y = r.row("Eyedrops", bestEyedrops ? "On ▾" : "Off ▾", TazTheme.PALE_RED, y,
				"Capsaicin Eyedrops: 30% more rare drops from Hypergolic fuel, for what they cost", () -> bestEyedrops = !bestEyedrops);
		InfernoConfig cfg = InfernoConfig.get();
		y = r.row("Prices", cfg.prices + " ▾", TazTheme.PALE_RED, y, "Orders: buy orders in, sell offers out. Instant: insta-buy and insta-sell",
				() -> cfg.prices = cfg.prices == InfernoConfig.Prices.ORDERS ? InfernoConfig.Prices.INSTANT : InfernoConfig.Prices.ORDERS);
		y += 4;
		InfernoData.Fuel hyper = new InfernoData.Fuel(InfernoData.Grade.HYPERGOLIC, InfernoData.Kind.CRUDE_GABAGOOL);
		double h = InfernoData.harvests(tier, InfernoTracker.boost(m), hyper);
		y = r.header("HYPERGOLIC FUEL", y);
		y = r.row("Harvests", String.format(Locale.ROOT, "%,d/day", Math.round(h)), TazTheme.TEXT, y, "Two actions each; the fuel makes it 21 times faster");
		y = r.row("Chili Peppers", String.format(Locale.ROOT, "%.1f/day", h / 136), TazTheme.TEXT, y, "1 in 136 harvests");
		r.row("Vertexes", String.format(Locale.ROOT, "%.2f/day", h / 5950 * (bestEyedrops ? InfernoData.EYEDROPS_BOOST : 1)), TazTheme.TEXT, y,
				"Inferno Vertex: 1 in 5,950 harvests (30% more with eyedrops)");
	}

	private static int tier(InfernoTracker.Minion m) {
		return bestTier > 0 ? bestTier : m != null && m.tier > 0 ? m.tier : InfernoTracker.tier();
	}

	// ---- left on Planner: which fuel, how many, and the cost

	private void plannerLeft(GuiGraphicsExtractor g, int mx, int my, int limit) {
		int x = px + 8, y = py + 25;
		g.text(font, "MAKE", x, y, TazTheme.DARK_RED, false);
		y += 11;
		int cx = x;
		for (InfernoData.Grade gr : InfernoData.Grade.values()) cx = choice(g, gr.label, cx, y, gr == grade, mx, my, () -> grade = gr) + 8;
		y += 11;
		cx = x;
		for (InfernoData.Kind k : InfernoData.Kind.values()) {
			if (cx + font.width(k.label) > x + statsW) {
				cx = x;
				y += 10;
			}
			cx = choice(g, k.label, cx, y, k == kind, mx, my, () -> kind = k) + 8;
		}
		g.text(font, "How many", x, py + 83, TazTheme.GREY, false);
		InfernoPlanner.Plan p = plan();
		Map<String, Long> exact = InfernoPlanner.exact(p);
		Rows r = new Rows(g, mx, my, limit);
		y = r.header("COST", py + 100);
		if (!p.complete()) {
			r.row("Prices", InfernoPrices.asking() ? "coming…" : "missing", TazTheme.GREY, y, "Waiting for SkyCofl");
			return;
		}
		long total = exact.isEmpty() ? Math.round(p.total()) : exact.values().stream().mapToLong(Long::longValue).sum();
		y = r.row("All of it", (exact.isEmpty() ? "≈" : "") + coins(total), TazTheme.WHITE, y, exact.isEmpty()
				? "At one item's insta-buy price; the exact cost of that many is coming from SkyCofl"
				: "What insta-buying all of it costs on the bazaar now (bigger amounts cost more each)");
		y = r.row("Each fuel", coins(total / Math.max(1, amount)), TazTheme.TEXT, y, "The cheapest way: each part bought or crafted, whichever is cheaper");
		y = r.row("On the AH", p.ah() > 0 ? coins(Math.round(p.ah())) : "none", TazTheme.TEXT, y, "The cheapest of this fuel on the AH now");
		if (p.plain() > 0) y = r.row("Gabagool bought", coins(Math.round(p.plain())), TazTheme.TEXT, y, "Each, buying its gabagool instead of crafting it");
		double other = p.ah() > 0 ? (p.plain() > 0 ? Math.min(p.ah(), p.plain()) : p.ah()) : p.plain();
		if (other > 0) {
			long save = Math.round(other * amount) - total;
			r.row("You save", signed(save), save < 0 ? TazTheme.RED : TazTheme.WHITE, y, "Against the cheaper of the AH and buying the gabagool");
		}
	}

	/** A word you click to pick it. */
	private int choice(GuiGraphicsExtractor g, String text, int x, int y, boolean on, int mx, int my, Runnable action) {
		int w = font.width(text);
		boolean hover = in(mx, my, x - 1, y - 1, w + 2, 10);
		g.text(font, text, x, y, on ? TazTheme.RED : hover ? TazTheme.PALE_RED : TazTheme.GREY, false);
		if (on) g.horizontalLine(x, x + w - 1, y + 9, TazTheme.DARK_RED);
		click(x - 1, y - 1, w + 2, 10, () -> {
			action.run();
			scroll = 0;
		});
		return x + w;
	}

	private InfernoPlanner.Plan plan() {
		InfernoPlanner.Plan p = InfernoPlanner.plan(new InfernoData.Fuel(grade, kind), amount);
		InfernoPlanner.priceExactly(p);
		return p;
	}

	// ---- left, at the bottom: what you picked

	private void actions(GuiGraphicsExtractor g, int mx, int my, int top) {
		int x = px + 8, y = top;
		g.horizontalLine(px + 6, px + statsW + 10, y - 4, LINE);
		if (tab == Tab.CALC) {
			box.visible = false;
			calcTotals(g, mx, my, y);
			return;
		}
		if (tab == Tab.PLANNER) {
			button(g, "List it in chat", x, y, mx, my, "What to buy (click a line to open the bazaar) and what to craft", this::listInChat);
			if (!InfernoPlanner.BUY.isEmpty()) button(g, "Cheapest again", x, y + 13, mx, my, "Craft whatever's cheaper to craft again",
					InfernoPlanner.BUY::clear);
			fresh(g, x, y + 30, "Click a craft to buy it");
			return;
		}
		if (picked == null) {
			String state = InfernoPrices.asking() ? "Checking prices…" : InfernoPrices.problem();
			if (state != null) g.text(font, font.plainSubstrByWidth(state, statsW), x, y, TazTheme.GREY, false);
			else button(g, "Refresh prices", x, y, mx, my, "Ask SkyCofl for every price now", () -> InfernoTracker.refresh(true));
			if (tab == Tab.FUELS || tab == Tab.DROPS) {
				button(g, "Add collected…", x, y + 13, mx, my, "Type in what you collected that Taz didn't see: it's shared between the fuels burning now",
						() -> type(true));
			} else {
				box.visible = false;
			}
			if (box.visible) g.text(font, "Enter to save, Esc to cancel", x, py + ph - 31, TazTheme.DARK_GREY, false);
			else fresh(g, x, y + 30, tab == Tab.BEST ? "Click a fuel to plan it" : "Click a row to change it");
			return;
		}
		boolean armed = System.currentTimeMillis() - deleteArmed < 3000;
		if (picked instanceof InfernoTracker.Run r) {
			g.text(font, font.plainSubstrByWidth(InfernoData.label(r.fuel), statsW), x, y, TazTheme.TEXT, false);
			int bx = button(g, "Cost…", x, y + 13, mx, my, "Type in what this fuel cost you", () -> type(false)) + 10;
			button(g, "Add collected…", bx, y + 13, mx, my, "Type in what you collected from it that Taz didn't see", () -> type(true));
			button(g, armed ? "Sure? Delete" : "Delete", x, y + 26, mx, my, "Remove it from the tracker", () -> armed(() -> InfernoTracker.delete(r)));
			if (box.visible) g.text(font, "Enter to save, Esc to cancel", x, py + ph - 31, TazTheme.DARK_GREY, false);
		} else if (picked instanceof InfernoTracker.Minion m) {
			g.text(font, "Inferno Minion " + m.label(), x, y, TazTheme.TEXT, false);
			button(g, armed ? "Sure? Forget it" : "Forget it", x, y + 13, mx, my, "You picked it up: Taz stops showing it (its fuels stay)",
					() -> armed(() -> InfernoTracker.forget(m)));
		} else if (picked instanceof Buy b && b.source instanceof InfernoTracker.Purchase p) {
			g.text(font, font.plainSubstrByWidth(b.count + "x " + InfernoData.plain(b.id), statsW), x, y, TazTheme.TEXT, false);
			button(g, armed ? "Sure? Delete" : "Delete", x, y + 13, mx, my, "Remove this buy", () -> armed(() -> InfernoTracker.delete(p)));
		} else {
			picked = null;
		}
	}

	/** Opens the box for a cost, or for items collected ("33 chili, 1 vertex"). */
	private void type(boolean collected) {
		typingCollected = collected;
		box.setHint(hint(collected ? "e.g. 33 chili, 1 vertex" : "It cost, e.g. 4.5m"));
		box.setValue("");
		box.visible = true;
		setFocused(box);
	}

	private void armed(Runnable action) {
		if (System.currentTimeMillis() - deleteArmed < 3000) {
			action.run();
			picked = null;
			deleteArmed = 0;
		} else {
			deleteArmed = System.currentTimeMillis();
		}
	}

	private void fresh(GuiGraphicsExtractor g, int x, int y, String idle) {
		boolean fresh = note != null && System.currentTimeMillis() - noteAt < 6000;
		g.text(font, font.plainSubstrByWidth(fresh ? note : idle, statsW), x, y, fresh ? TazTheme.WHITE : TazTheme.DARK_GREY, false);
	}

	private int button(GuiGraphicsExtractor g, String text, int x, int y, int mx, int my, String about, Runnable action) {
		int w = font.width(text);
		boolean hover = in(mx, my, x - 1, y - 2, w + 2, 12);
		g.text(font, text, x, y, hover ? TazTheme.RED : TazTheme.PALE_RED, false);
		if (hover) tip = List.of(hint(about));
		click(x - 1, y - 2, w + 2, 12, action);
		return x + w;
	}

	// ---- right: the list

	private int lx, right, top, visible;

	private void list(GuiGraphicsExtractor g, int mx, int my) {
		lx = px + statsW + 22;
		right = px + pw - 8;
		top = py + LIST_TOP;
		visible = Math.max(1, (py + ph - 18 - top) / ROW_H);
		switch (tab) {
			case FUELS -> fuels(g, mx, my);
			case MINIONS -> minions(g, mx, my);
			case DROPS -> dropsList(g, mx, my);
			case BOUGHT -> bought(g, mx, my);
			case BEST -> best(g, mx, my);
			case PLANNER -> planner(g, mx, my);
			case CALC -> calc(g, mx, my);
		}
		InfernoConfig cfg = InfernoConfig.get();
		if (!cfg.enabled) {
			String off = "Tracking is off · turn it on";
			int ow = font.width(off);
			boolean on = in(mx, my, lx - 1, py + ph - 14, ow + 2, 10);
			g.text(font, off, lx, py + ph - 13, on ? TazTheme.LIGHT_RED : TazTheme.RED, false);
			if (on) tip = List.of(Component.literal("Start tracking your fuel, minions and drops"), hint("Same as /taz → Inferno Minions → Enabled"));
			click(lx - 1, py + ph - 14, ow + 2, 10, () -> cfg.enabled = true);
		} else if (rowCount() > visible) {
			String more = (scroll + 1) + "-" + Math.min(rowCount(), scroll + visible) + " of " + rowCount() + " · scroll";
			g.text(font, more, lx, py + ph - 13, TazTheme.DARK_GREY, false);
		}
		String credit = "Prices: SkyCofl";
		int cw = font.width(credit), cx = right - cw, cy = py + ph - 13;
		boolean hover = in(mx, my, cx - 1, cy - 1, cw + 2, 10);
		g.text(font, credit, cx, cy, hover ? TazTheme.PALE_RED : TazTheme.DARK_GREY, false);
		if (hover) tip = List.of(Component.literal("Prices provided by SkyCofl (sky.coflnet.com)"), hint("Taz isn't affiliated with SkyCofl or Hypixel."));
		click(cx - 1, cy - 1, cw + 2, 10, () -> ConfirmLinkScreen.confirmLinkNow(this, "https://sky.coflnet.com"));
	}

	/** Column titles, right-aligned to the columns from the right; the first is the name. */
	private void headers(GuiGraphicsExtractor g, int mx, int my, String name, String... cols) {
		g.text(font, name, lx + 20, py + 25, TazTheme.DARK_GREY, false);
		for (int i = 0; i < cols.length; i += 2) {
			String text = cols[i];
			int x = colX(cols.length / 2 - 1 - i / 2), w = font.width(text);
			boolean hover = in(mx, my, x - w - 2, py + 23, w + 4, 11);
			g.text(font, text, x - w, py + 25, hover ? TazTheme.GREY : TazTheme.DARK_GREY, false);
			if (hover) tip = List.of(Component.literal(cols[i + 1]));
		}
	}

	/** The right edge of a column, 0 being the last. */
	private int colX(int fromRight) {
		return right - fromRight * col;
	}

	/** Draws a row's background, icon and name; true when the mouse is on it. */
	private boolean row(GuiGraphicsExtractor g, int i, Object item, String icon, String name, int nameColor, int cols, int mx, int my, Runnable click) {
		int y = top + (i - scroll) * ROW_H;
		boolean hover = in(mx, my, lx - 2, y, right - lx + 4, ROW_H);
		if (item != null && item == picked) g.fill(lx - 2, y, right + 2, y + ROW_H, PICKED);
		else if (hover) g.fill(lx - 2, y, right + 2, y + ROW_H, HOVER);
		g.item(InfernoData.icon(icon), lx, y + 3);
		int nameW = colX(cols) - 6 - (lx + 20);
		g.text(font, font.plainSubstrByWidth(name, nameW), lx + 20, y + 2, nameColor, false);
		if (click != null) click(lx - 2, y, right - lx + 4, ROW_H, click);
		return hover;
	}

	/** The coloured word and the grey rest under a row's name. */
	private void badge(GuiGraphicsExtractor g, int i, String tag, int color, String rest, int cols) {
		int y = top + (i - scroll) * ROW_H + 12, x = lx + 20, width = colX(cols) - 6 - x;
		g.text(font, tag, x, y, color, false);
		g.text(font, font.plainSubstrByWidth(rest, Math.max(0, width - font.width(tag))), x + font.width(tag), y, TazTheme.DARK_GREY, false);
	}

	private void cell(GuiGraphicsExtractor g, int i, int fromRight, boolean under, String text, int color) {
		int y = top + (i - scroll) * ROW_H + (under ? 12 : 2);
		g.text(font, text, colX(fromRight) - font.width(text), y, color, false);
	}

	private void empty(GuiGraphicsExtractor g, String... lines) {
		int y = top + 24, cx = (lx + right) / 2;
		for (String line : lines) {
			g.centeredText(font, line, cx, y, y == top + 24 ? TazTheme.TEXT : TazTheme.GREY);
			y += 11;
		}
	}

	private int clampScroll(int size) {
		scroll = Math.max(0, Math.min(scroll, size - visible));
		return Math.min(size, scroll + visible);
	}

	// ---- Fuels

	private List<InfernoTracker.Run> runs() {
		long since = period.since(), now = System.currentTimeMillis();
		return InfernoTracker.runs().stream().filter(r -> r.placedAt >= since || r.running(now) || r.queued(now))
				.sorted(Comparator.comparingLong((InfernoTracker.Run r) -> r.placedAt).thenComparingLong(r -> r.id).reversed()).toList();
	}

	private void fuels(GuiGraphicsExtractor g, int mx, int my) {
		headers(g, mx, my, "Fuel", "Spent", "What it cost you: its parts at what you paid (≈: some at today's price), and eyedrops",
				"Expected", "What its 24 hours should make, after tax", "Made", "What you collected from the minion while it burned, after tax",
				"Profit", "Made less spent. ≈: if it makes what it should");
		List<InfernoTracker.Run> rows = runs();
		if (rows.isEmpty()) {
			empty(g, "No fuels yet.", "", "Put an Inferno Minion Fuel in a minion:", "it shows up here with what it cost", "and what it should make.",
					"(Right-click the minion so Taz knows which one.)");
		}
		long now = System.currentTimeMillis();
		int end = clampScroll(rows.size());
		for (int i = scroll; i < end; i++) {
			InfernoTracker.Run r = rows.get(i);
			InfernoData.Fuel f = InfernoData.fuel(r.fuel);
			InfernoTracker.Minion m = InfernoTracker.minionOf(r);
			String where = (m == null ? "" : " · " + m.label()) + (r.eyedrops > 0 ? " · eyedrops" : "");
			boolean hover = row(g, i, r, r.fuel, f.label(), TazTheme.TEXT, 4, mx, my, () -> pick(r));
			if (r.early) badge(g, i, "Taken out", TazTheme.DARK_GREY, where, 4);
			else if (r.queued(now)) badge(g, i, "Waiting", TazTheme.GREY, " · starts in " + LowballTracker.span(r.startAt - now) + where, 4);
			else if (r.running(now)) badge(g, i, "Burning", TazTheme.LIGHT_RED, " · " + LowballTracker.span(r.endsAt - now) + " left" + where, 4);
			else badge(g, i, "Done", TazTheme.WHITE, " · " + LowballTracker.span(now - r.endsAt) + " ago" + where, 4);
			cell(g, i, 3, false, (r.estimated ? "≈" : "") + coins(r.totalCost()), TazTheme.TEXT);
			long expected = r.expectedNow(now);
			cell(g, i, 2, false, expected >= 0 ? "≈" + coins(expected) : "?", TazTheme.GREY);
			long made = InfernoTracker.made(r);
			boolean live = r.running(now) || r.queued(now);
			cell(g, i, 1, false, made > 0 ? coins(made) : "-", made > 0 ? TazTheme.TEXT : TazTheme.DARK_GREY);
			if (live && made > 0) cell(g, i, 1, true, "so far", TazTheme.DARK_GREY);
			if (live) {
				if (r.expected >= 0) {
					long p = r.expected - r.totalCost();
					cell(g, i, 0, false, "≈" + signed(p), p < 0 ? TazTheme.RED : TazTheme.GREY);
					cell(g, i, 0, true, "expected", TazTheme.DARK_GREY);
				}
			} else if (made >= 0) {
				long p = made - r.totalCost();
				cell(g, i, 0, false, signed(p), p < 0 ? TazTheme.RED : TazTheme.WHITE);
			}
			if (hover) tip = details(r, m);
		}
	}

	private List<Component> details(InfernoTracker.Run r, InfernoTracker.Minion m) {
		List<Component> out = new ArrayList<>();
		long now = System.currentTimeMillis();
		InfernoData.Fuel f = InfernoData.fuel(r.fuel);
		out.add(line(f.grade().color + f.label() + " fuel"));
		out.add(line("§8" + f.grade().speed + "x speed · makes " + f.kind().label + (f.grade() == InfernoData.Grade.HYPERGOLIC ? " and rare drops" : "")));
		out.add(Component.empty());
		out.add(line("§7In §fInferno Minion " + (m == null ? "?" : m.label()) + "§7, put in " + date(r.placedAt)));
		if (r.early) out.add(line("§7Taken out after §f" + LowballTracker.span(r.endsAt - r.startAt)));
		else if (r.queued(now)) out.add(line("§7Starts in §f" + LowballTracker.span(r.startAt - now) + " §8(after the one burning)"));
		else if (r.running(now)) out.add(line("§7Burning: §f" + LowballTracker.span(r.endsAt - now) + " left"));
		else out.add(line("§7Ran out " + date(r.endsAt)));
		String how = switch (r.how) {
			case "Crafted" -> "crafted: its parts at what you paid";
			case "Bought on the AH" -> "bought on the AH";
			case "Already in the minion" -> "already in it when Taz first saw it: today's price";
			case "Typed in" -> "typed in by you";
			default -> "not seen bought or crafted: today's price";
		};
		out.add(line("§7Cost: §6" + (r.estimated ? "≈" : "") + coins(r.cost) + " §8(" + how + ")"));
		if (r.estimated && r.how.equals("Crafted")) out.add(line("§8≈: some parts weren't seen bought, so they're at today's price"));
		if (r.eyedrops > 0) out.add(line("§7Eyedrops: §f" + r.eyedrops + " §6" + coins(r.eyedropsCost) + " §8(30% more rare drops)"));
		if (r.early && r.expected >= 0) {
			out.add(line("§7Should have made: §6≈" + coins(r.expectedNow(now)) + " §8in the " + LowballTracker.span(r.endsAt - r.startAt) + " it burned"));
		} else if (r.expected >= 0) {
			out.add(line("§7Should make: §6≈" + coins(r.expected) + " §8in 24h · " + String.format(Locale.ROOT, "%,d", Math.round(r.harvests)) + " harvests a day"));
		}
		Map<String, Long> drops = InfernoTracker.drops(r);
		long made = InfernoTracker.made(r);
		if (!drops.isEmpty()) {
			out.add(Component.empty());
			out.add(line("§7Collected: §6" + (made >= 0 ? coins(made) : "?") + " §8after tax"));
			drops.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(8)
					.forEach(e -> out.add(line(" §f" + String.format(Locale.ROOT, "%,d", e.getValue()) + "x " + InfernoData.name(e.getKey()))));
		} else {
			out.add(line("§7Collected: §8nothing yet"));
		}
		if (m != null && m.storage > 0 && (r.running(now) || now - r.endsAt < 86_400_000L)) {
			out.add(line("§8In the minion when you last looked: " + coins(m.storage) + " (" + LowballTracker.span(now - m.storageAt) + " ago)"));
		}
		if (r.running(now) || r.queued(now)) {
			if (r.expected >= 0) out.add(line("§7Profit if it makes what it should: " + money(r.expected - r.totalCost())));
		} else if (made >= 0) {
			out.add(line("§7Profit: " + money(made - r.totalCost())));
		}
		out.add(line(r == picked ? "§8Click to unpick" : "§8Click to change it"));
		return out;
	}

	// ---- Minions

	private void minions(GuiGraphicsExtractor g, int mx, int my) {
		headers(g, mx, my, "Minion", "Fuels", "Fuels put in it", "Spent", "What its fuels and eyedrops cost",
				"Made", "What you collected from it, after tax", "Profit", "Made less spent");
		List<InfernoTracker.Minion> rows = InfernoTracker.minions().stream().sorted(Comparator.comparingInt(m -> m.number)).toList();
		if (rows.isEmpty()) empty(g, "No minions yet.", "", "Right-click each Inferno minion once:", "Taz remembers it, its tier and upgrades.");
		long now = System.currentTimeMillis();
		int end = clampScroll(rows.size());
		for (int i = scroll; i < end; i++) {
			InfernoTracker.Minion m = rows.get(i);
			boolean hover = row(g, i, m, "INFERNO_GENERATOR_" + (m.tier > 0 ? m.tier : 11), "Minion " + m.label(), TazTheme.TEXT, 4, mx, my,
					() -> pick(m));
			InfernoTracker.Run r = InfernoTracker.running(m, now);
			long ends = InfernoTracker.fuelEnds(m, now);
			if (r == null || ends == 0) badge(g, i, "No fuel", TazTheme.RED, m.key.startsWith("?") ? " · not right-clicked" : "", 4);
			else {
				InfernoData.Fuel f = InfernoData.fuel(r.fuel);
				badge(g, i, f.grade().label, TazTheme.LIGHT_RED, " · " + LowballTracker.span(ends - now) + " left" + (m.eyedropsEnd > now ? " · eyedrops" : ""), 4);
			}
			int fuels = 0;
			long spent = 0, made = 0;
			for (InfernoTracker.Run x : InfernoTracker.runs()) {
				if (!x.minion.equals(m.key)) continue;
				fuels++;
				spent += x.totalCost();
				made += Math.max(0, InfernoTracker.made(x));
			}
			cell(g, i, 3, false, String.valueOf(fuels), TazTheme.TEXT);
			cell(g, i, 2, false, coins(spent), TazTheme.TEXT);
			cell(g, i, 1, false, coins(made), TazTheme.TEXT);
			cell(g, i, 0, false, signed(made - spent), made - spent < 0 ? TazTheme.RED : TazTheme.WHITE);
			if (hover) tip = minionTip(m, r, now);
		}
	}

	private List<Component> minionTip(InfernoTracker.Minion m, InfernoTracker.Run r, long now) {
		List<Component> out = new ArrayList<>();
		out.add(line("§9Inferno Minion " + m.label()));
		if (!m.key.startsWith("?")) out.add(line("§8At " + m.key.replace(",", " ")));
		out.add(Component.empty());
		out.add(line("§7Upgrades: §f" + (m.upgrades.isEmpty() ? "none seen" : String.join("§7, §f", m.upgrades.stream().map(InfernoScreen::upgrade).toList()))));
		out.add(line("§7Speed: §f+" + Math.round(InfernoTracker.boost(m) * 100) + "% §8(Rising Celsius, infusion, upgrades)"));
		if (r != null) {
			InfernoData.Fuel f = InfernoData.fuel(r.fuel);
			out.add(line("§7Fuel: " + f.grade().color + f.label() + " §7until §f" + date(InfernoTracker.fuelEnds(m, now))));
			out.add(line("§7Harvests: §f" + String.format(Locale.ROOT, "%,d", Math.round(r.harvests)) + " a day"));
		} else {
			out.add(line("§cNo fuel"));
		}
		if (m.eyedropsEnd > now) out.add(line("§7Eyedrops: §f" + LowballTracker.span(m.eyedropsEnd - now) + " left"));
		if (m.storage >= 0 && m.storageAt > 0) out.add(line("§7In it when you last looked: §6" + coins(m.storage) + " §8(" + LowballTracker.span(now - m.storageAt) + " ago)"));
		out.add(line("§8Click to forget it (picked up)"));
		return out;
	}

	private static String upgrade(String id) {
		return switch (id) {
			case "FLYCATCHER_UPGRADE" -> "Flycatcher";
			case "MINION_EXPANDER" -> "Minion Expander";
			case "SUPER_COMPACTOR_3000" -> "Super Compactor";
			case "DWARVEN_COMPACTOR" -> "Dwarven Compactor";
			case "COMPACTOR" -> "Compactor";
			case "BUDGET_HOPPER" -> "Budget Hopper";
			case "ENCHANTED_HOPPER" -> "Enchanted Hopper";
			default -> id;
		};
	}

	// ---- Drops

	private record Drop(String id, long count, double expected, long worth) {
	}

	private List<Drop> drops() {
		InfernoTracker.Stats s = InfernoTracker.stats(period.since());
		Map<String, Long> counts = new LinkedHashMap<>(s.drops());
		for (InfernoData.Rare r : InfernoData.RARES) counts.putIfAbsent(r.id(), 0L);
		List<Drop> out = new ArrayList<>();
		for (Map.Entry<String, Long> e : counts.entrySet()) {
			double v = InfernoPrices.value(e.getKey());
			double exp = s.expectedDrops().getOrDefault(e.getKey(), -1.0);
			if (e.getValue() == 0 && exp <= 0) continue;
			out.add(new Drop(e.getKey(), e.getValue(), exp, v < 0 ? -1 : Math.round(v * e.getValue())));
		}
		out.sort(Comparator.comparingLong((Drop d) -> d.worth).reversed());
		return out;
	}

	private void dropsList(GuiGraphicsExtractor g, int mx, int my) {
		headers(g, mx, my, "Item", "Collected", "How many you took out of your minions", "Expected",
				"Rare drops: how many you'd expect from your Hypergolic fuels so far", "Worth", "What they sell for after tax");
		List<Drop> rows = drops();
		if (rows.isEmpty()) empty(g, "Nothing collected yet.", "", "Collect from an Inferno minion:", "what you take out shows up here.");
		int end = clampScroll(rows.size());
		for (int i = scroll; i < end; i++) {
			Drop d = rows.get(i);
			InfernoData.Rare rare = InfernoData.RARES.stream().filter(r -> r.id().equals(d.id)).findFirst().orElse(null);
			boolean hover = row(g, i, null, d.id, InfernoData.plain(d.id), TazTheme.TEXT, 3, mx, my, null);
			if (rare != null) badge(g, i, "Rare", TazTheme.LIGHT_RED, String.format(Locale.ROOT, " · 1 in %,d harvests", Math.round(rare.in())), 3);
			cell(g, i, 2, false, String.format(Locale.ROOT, "%,d", d.count), TazTheme.TEXT);
			if (d.expected >= 0) {
				String exp = d.expected >= 10 ? String.format(Locale.ROOT, "≈%,d", Math.round(d.expected))
						: d.expected >= 0.01 ? String.format(Locale.ROOT, "≈%.2f", d.expected) : "≈0";
				cell(g, i, 1, false, exp, TazTheme.GREY);
				// Luck only means something once you'd expect at least one.
				if (d.expected >= 1) cell(g, i, 1, true, Lowball.pct(d.count / d.expected) + " luck", TazTheme.DARK_GREY);
			}
			cell(g, i, 0, false, d.worth >= 0 ? coins(d.worth) : "?", TazTheme.TEXT);
			if (hover) {
				double each = InfernoPrices.value(d.id);
				List<Component> t = new ArrayList<>(List.of(line(InfernoData.name(d.id)), Component.empty()));
				t.add(line("§7Collected: §f" + String.format(Locale.ROOT, "%,d", d.count)));
				if (each >= 0) t.add(line("§7Sells for: §6" + coins(Math.round(each)) + " §8each after tax"));
				if (d.expected >= 0) t.add(line("§7Expected so far: §f" + String.format(Locale.ROOT, "%.2f", d.expected) + " §8(from your Hypergolic fuels)"));
				tip = t;
			}
		}
	}

	// ---- Bought

	private List<Buy> buys() {
		long since = period.since();
		List<Buy> out = new ArrayList<>();
		for (InfernoTracker.Purchase p : InfernoTracker.bought()) if (p.at >= since) out.add(new Buy(p.at, p.id, p.count, p.coins, p.left, p.how, p));
		for (InfernoTracker.Stock s : InfernoTracker.stock()) {
			if (s.at >= since && s.how.equals("Bought on the AH")) out.add(new Buy(s.at, s.fuel, 1, s.cost, 1, "AH", s));
		}
		out.sort(Comparator.comparingLong(Buy::at).reversed());
		return out;
	}

	private void bought(GuiGraphicsExtractor g, int mx, int my) {
		headers(g, mx, my, "Item", "Amount", "How many you bought", "Each", "What you paid for one", "Paid", "What you paid in all");
		List<Buy> rows = buys();
		if (rows.isEmpty()) empty(g, "Nothing bought yet.", "", "Buy distillates, Inferno Fuel Blocks, gabagool or", "eyedrops on the bazaar (or a fuel on the AH):",
				"each buy shows up here, and the fuel you craft", "costs what you paid for its parts.");
		int end = clampScroll(rows.size());
		for (int i = scroll; i < end; i++) {
			Buy b = rows.get(i);
			String id = b.id.isEmpty() ? "INFERNO_HYPERGOLIC_CRUDE_GABAGOOL" : b.id;
			String name = b.id.isEmpty() ? "Inferno Minion Fuel" : InfernoData.label(b.id);
			boolean hover = row(g, i, b.source, id, name, TazTheme.TEXT, 3, mx, my, () -> pick(b.source));
			int used = b.count - b.left;
			String rest = b.source instanceof InfernoTracker.Purchase ? " · " + (used == 0 ? "not used yet" : b.left == 0 ? "all used" : used + " used") : " · not in a minion yet";
			badge(g, i, b.how, TazTheme.GREY, rest + " · " + LowballTracker.span(System.currentTimeMillis() - b.at) + " ago", 3);
			cell(g, i, 2, false, String.format(Locale.ROOT, "%,d", b.count), TazTheme.TEXT);
			cell(g, i, 1, false, coins(b.coins / Math.max(1, b.count)), TazTheme.GREY);
			cell(g, i, 0, false, coins(b.coins), TazTheme.TEXT);
			if (hover) tip = List.of(line(InfernoData.name(id.equals(b.id) ? id : "INFERNO_HYPERGOLIC_CRUDE_GABAGOOL")),
					line("§7Bought " + date(b.at) + " §8(" + b.how + ")"), line("§7" + b.count + " for §6" + coins(b.coins)),
					line(b.source instanceof InfernoTracker.Purchase ? "§7In fuel so far: §f" + used : "§7A fuel you haven't put in a minion yet"),
					line("§8Click to delete it"));
		}
	}

	// ---- Calculator

	private void calcLeft(GuiGraphicsExtractor g, int mx, int my, int limit) {
		InfernoCalc.Setup s = InfernoConfig.get().calc;
		if (!s.set) {
			InfernoCalc.yours(s);
			countBox.setValue(String.valueOf(s.count));
		}
		InfernoData.Fuel f = s.fuel();
		Rows r = new Rows(g, mx, my, limit - 4);
		int x = px + 8, y = py + 25;
		g.text(font, "SETUP", x, y, TazTheme.DARK_RED, false);
		String mine = "Use yours";
		button(g, mine, x + statsW - font.width(mine), y, mx, my, "Your minions' tier, upgrades and count, and your last fuel", () -> {
			InfernoCalc.yours(s);
			countBox.setValue(String.valueOf(s.count));
		});
		y += 11;
		y = r.row("Tier", InfernoData.roman(s.tier) + " ▾", TazTheme.PALE_RED, y, "Click for another tier", () -> s.tier = s.tier % 11 + 1);
		y = r.row("Fuel", f.grade().label + " ▾", TazTheme.PALE_RED, y, "Fuel: 10x, Heavy: 15x, Hypergolic: 20x faster (and rare drops)", () ->
				s.fuel = new InfernoData.Fuel(InfernoData.Grade.values()[(f.grade().ordinal() + 1) % InfernoData.Grade.values().length], f.kind()).id());
		y = r.row("Gabagool", f.kind().label + " ▾", TazTheme.PALE_RED, y, "What the fuel makes most of", () ->
				s.fuel = new InfernoData.Fuel(f.grade(), InfernoData.Kind.values()[(f.kind().ordinal() + 1) % InfernoData.Kind.values().length]).id());
		String slots = "Flycatcher: 20% faster. Expander (Minion Expander): 5% faster. Compactor (Super Compactor 3000): "
				+ "Crude Gabagool comes out as Very Crude Gabagool";
		y = r.row("Slot 1", s.slot1 + " ▾", TazTheme.PALE_RED, y, slots, () -> s.slot1 = s.slot1.next());
		y = r.row("Slot 2", s.slot2 + " ▾", TazTheme.PALE_RED, y, slots, () -> s.slot2 = s.slot2.next());
		g.text(font, "Minions", x, y, TazTheme.GREY, false);
		if (in(mx, my, x, y - 1, statsW - 50, 10)) tip = List.of(Component.literal("Minions"),
				hint("How many Inferno minions you run. Each one makes them all 18% faster, up to 10"));
		y += 13;
		y = r.header("SPEED", y);
		y = r.row("Mithril Infusion", onOff(s.infusion), TazTheme.PALE_RED, y, "10% faster", () -> s.infusion = !s.infusion);
		y = r.row("Beacon", (s.beacon == 0 ? "None" : InfernoData.roman(s.beacon)) + " ▾", TazTheme.PALE_RED, y,
				"A beacon on your island set to minion speed: 2% faster a tier", () -> s.beacon = (s.beacon + 1) % 6);
		if (s.beacon > 0) y = r.row("Scorched crystal", onOff(s.scorched), TazTheme.PALE_RED, y,
				"A Scorched Power Crystal in the beacon: 1% faster", () -> s.scorched = !s.scorched);
		y = r.row("Postcard", onOff(s.postcard), TazTheme.PALE_RED, y, "A Postcard on your island: 5% faster", () -> s.postcard = !s.postcard);
		y = r.row("Free Will", onOff(s.freeWill), TazTheme.PALE_RED, y, "A minion that stayed after Free Will: 10% faster", () -> s.freeWill = !s.freeWill);
		y = r.row("Eyedrops", onOff(s.eyedrops), TazTheme.PALE_RED, y, "Capsaicin Eyedrops: 30% more rare drops from Hypergolic fuel (not Chili Peppers)",
				() -> s.eyedrops = !s.eyedrops);
		InfernoConfig cfg = InfernoConfig.get();
		y = r.row("Prices", cfg.prices + " ▾", TazTheme.PALE_RED, y, "Orders: buy orders in, sell offers out. Instant: insta-buy and insta-sell",
				() -> cfg.prices = cfg.prices == InfernoConfig.Prices.ORDERS ? InfernoConfig.Prices.INSTANT : InfernoConfig.Prices.ORDERS);
		InfernoCalc.Day d = InfernoCalc.day(s);
		y = r.row("Speed", "+" + Math.round(d.speed() * 100) + "%", TazTheme.TEXT, y, "Rising Celsius and everything above, before the fuel");
		r.row("Each action", String.format(Locale.ROOT, "%.1fs", d.secondsPerAction()), TazTheme.TEXT, y, "Time between a minion's actions with this fuel");
	}

	private static String onOff(boolean on) {
		return on ? "On ▾" : "Off ▾";
	}

	/** Bottom left on the Calculator: the day's totals. */
	private void calcTotals(GuiGraphicsExtractor g, int mx, int my, int y) {
		InfernoCalc.Setup s = InfernoConfig.get().calc;
		InfernoCalc.Day d = InfernoCalc.day(s);
		String label = s.oneMinion ? "A day, one minion ▾" : "A day, " + d.minions() + (d.minions() == 1 ? " minion ▾" : " minions ▾");
		button(g, label, px + 8, y, mx, my, "Click for one minion or all of them", () -> s.oneMinion = !s.oneMinion);
		Rows r = new Rows(g, mx, my, py + ph - 4);
		y += 12;
		String about = d.complete() ? "" : " (some prices are still coming)";
		y = r.row("Makes", coins(Math.round(d.revenue())), TazTheme.TEXT, y, "What it all sells for, after tax" + about);
		y = r.row("Costs", coins(Math.round(d.expenses())), TazTheme.TEXT, y, "A fuel for each minion, made the cheapest way, and eyedrops" + about);
		long profit = Math.round(d.profit());
		r.row("Profit", signed(profit), profit < 0 ? TazTheme.RED : TazTheme.WHITE, y, "Makes less costs" + about);
	}

	private static List<InfernoCalc.Line> calcRows(InfernoCalc.Day d) {
		List<InfernoCalc.Line> rows = new ArrayList<>(d.makes());
		rows.addAll(d.costs());
		return rows;
	}

	private void calc(GuiGraphicsExtractor g, int mx, int my) {
		InfernoCalc.Day d = InfernoCalc.day(InfernoConfig.get().calc);
		headers(g, mx, my, "Made, then bought", "Amount", "How many a day", "Each", "What one sells for after tax, or costs to buy",
				"Total", "A day");
		List<InfernoCalc.Line> rows = calcRows(d);
		int end = clampScroll(rows.size());
		for (int i = scroll; i < end; i++) {
			InfernoCalc.Line l = rows.get(i);
			boolean hover = row(g, i, null, l.id(), InfernoData.plain(l.id()), TazTheme.TEXT, 3, mx, my, null);
			boolean ah = InfernoData.fuel(l.id()) != null;
			badge(g, i, l.bought() ? "Buy" : "Makes", l.bought() ? TazTheme.LIGHT_RED : TazTheme.WHITE,
					l.bought() ? (ah ? " · AH" : " · bazaar") : "", 3);
			cell(g, i, 2, false, amount(l.amount()), TazTheme.WHITE);
			cell(g, i, 1, false, l.each() >= 0 ? coins(Math.round(l.each())) : "?", TazTheme.GREY);
			cell(g, i, 0, false, l.each() >= 0 ? (l.bought() ? "-" : "") + coins(Math.round(l.total())) : "?", l.bought() ? TazTheme.PALE_RED : TazTheme.TEXT);
			if (hover) tip = List.of(line(InfernoData.name(l.id())), Component.empty(),
					line("§7" + (l.bought() ? "Buy " : "Makes ") + "§f" + amount(l.amount()) + " §7a day at §6" + (l.each() >= 0 ? coins(Math.round(l.each())) : "?")
							+ " §7each" + (l.bought() ? "" : " §8(after tax)")));
		}
		if (!d.complete()) empty(g, "", "Waiting for SkyCofl's prices…");
	}

	private static String amount(double n) {
		return n >= 10 ? String.format(Locale.ROOT, "%,d", Math.round(n)) : String.format(Locale.ROOT, "%.2f", n);
	}

	// ---- Best fuel

	private record Best(InfernoData.Fuel fuel, double cost, double makes, double profit) {
	}

	private List<Best> bestRows() {
		InfernoTracker.Minion m = InfernoTracker.best();
		int tier = tier(m);
		double eyedrops = bestEyedrops ? Math.max(0, InfernoPrices.cost(InfernoData.EYEDROPS)) : 0;
		List<Best> out = new ArrayList<>();
		for (InfernoData.Fuel f : InfernoData.fuels()) {
			double cost = InfernoPrices.fuelCost(f);
			boolean drops = bestEyedrops && f.grade() == InfernoData.Grade.HYPERGOLIC;
			double makes = InfernoPrices.worth(InfernoData.makes(f, tier, InfernoTracker.boost(m), drops));
			double profit = cost < 0 || makes < 0 ? Double.NEGATIVE_INFINITY : makes - cost - (drops ? eyedrops : 0);
			out.add(new Best(f, cost + (drops ? eyedrops : 0), makes, profit));
		}
		out.sort(Comparator.comparingDouble((Best b) -> b.profit).reversed());
		return out;
	}

	private void best(GuiGraphicsExtractor g, int mx, int my) {
		headers(g, mx, my, "Fuel", "Cost", "The cheaper of crafting it from bazaar parts and the AH" + (bestEyedrops ? ", plus eyedrops" : ""),
				"Makes", "≈ What its 24 hours make, after tax", "Profit", "≈ A day, per minion, after its cost");
		List<Best> rows = bestRows();
		int end = clampScroll(rows.size());
		for (int i = scroll; i < end; i++) {
			Best b = rows.get(i);
			boolean hover = row(g, i, null, b.fuel.id(), b.fuel.label(), i == 0 && b.profit > Double.NEGATIVE_INFINITY ? TazTheme.WHITE : TazTheme.TEXT, 3, mx, my,
					() -> {
						grade = b.fuel.grade();
						kind = b.fuel.kind();
						tab = Tab.PLANNER;
						scroll = 0;
					});
			badge(g, i, b.fuel.grade().speed + "x", b.fuel.grade() == InfernoData.Grade.HYPERGOLIC ? TazTheme.LIGHT_RED : TazTheme.GREY,
					" · " + b.fuel.kind().label + (i == 0 ? " · best" : ""), 3);
			cell(g, i, 2, false, b.cost > 0 ? coins(Math.round(b.cost)) : "?", TazTheme.TEXT);
			cell(g, i, 1, false, b.makes >= 0 ? coins(Math.round(b.makes)) : "?", TazTheme.GREY);
			if (b.profit > Double.NEGATIVE_INFINITY) cell(g, i, 0, false, signed(Math.round(b.profit)), b.profit < 0 ? TazTheme.RED : TazTheme.WHITE);
			if (hover) tip = bestTip(b);
		}
	}

	private List<Component> bestTip(Best b) {
		InfernoTracker.Minion m = InfernoTracker.best();
		int tier = tier(m);
		List<Component> out = new ArrayList<>();
		out.add(line(b.fuel.grade().color + b.fuel.label() + " fuel"));
		out.add(line("§8In an Inferno Minion " + InfernoData.roman(tier) + ", for 24 hours"));
		out.add(Component.empty());
		Map<String, Double> makes = InfernoData.makes(b.fuel, tier, InfernoTracker.boost(m), bestEyedrops && b.fuel.grade() == InfernoData.Grade.HYPERGOLIC);
		for (Map.Entry<String, Double> e : makes.entrySet()) {
			double v = InfernoPrices.value(e.getKey());
			String n = e.getValue() >= 10 ? String.format(Locale.ROOT, "%,d", Math.round(e.getValue())) : String.format(Locale.ROOT, "%.2f", e.getValue());
			out.add(line(" §f" + n + " " + InfernoData.name(e.getKey()) + (v >= 0 ? " §8= " + coins(Math.round(v * e.getValue())) : "")));
		}
		double craft = InfernoPrices.craftCost(b.fuel), ah = InfernoPrices.cost(b.fuel.id());
		out.add(Component.empty());
		if (craft > 0) out.add(line("§7Craft it from bazaar parts: §6" + coins(Math.round(craft))));
		if (ah > 0) out.add(line("§7On the AH: §6" + coins(Math.round(ah))));
		out.add(line("§8Click to plan it"));
		return out;
	}

	// ---- Planner

	private void planner(GuiGraphicsExtractor g, int mx, int my) {
		headers(g, mx, my, "Buy, then craft", "Amount", "How many", "Each", "Insta-buy price for one", "Total", "What that many cost to insta-buy now");
		InfernoPlanner.Plan p = plan();
		Map<String, Long> exact = InfernoPlanner.exact(p);
		List<Object> rows = new ArrayList<>(p.buy().keySet());
		List<InfernoPlanner.Step> steps = new ArrayList<>(p.steps());
		java.util.Collections.reverse(steps);
		rows.addAll(steps);
		int end = clampScroll(rows.size());
		for (int i = scroll; i < end; i++) {
			if (rows.get(i) instanceof String id) {
				long n = p.buy().get(id);
				double each = p.each().getOrDefault(id, -1.0);
				boolean craftable = InfernoPlanner.craftable(id);
				boolean hover = row(g, i, null, id, InfernoData.plain(id), TazTheme.TEXT, 3, mx, my, craftable && InfernoPlanner.BUY.contains(id) ? () -> {
					InfernoPlanner.BUY.remove(id);
				} : null);
				badge(g, i, "Buy", TazTheme.LIGHT_RED, " · " + (id.startsWith("INFERNO_") && InfernoData.fuel(id) != null ? "AH" : "bazaar")
						+ (craftable && InfernoPlanner.BUY.contains(id) ? " · click to craft it" : ""), 3);
				cell(g, i, 2, false, String.format(Locale.ROOT, "%,d", n), TazTheme.WHITE);
				cell(g, i, 1, false, each >= 0 ? coins(Math.round(each)) : "?", TazTheme.GREY);
				Long ex = exact.get(id);
				cell(g, i, 0, false, ex != null ? coins(ex) : each >= 0 ? "≈" + coins(Math.round(each * n)) : "?", TazTheme.TEXT);
				if (hover) {
					List<Component> t = new ArrayList<>(List.of(line(InfernoData.name(id)), Component.empty(),
							line("§7Buy §f" + String.format(Locale.ROOT, "%,d", n) + " §7at §6" + (each >= 0 ? coins(Math.round(each)) : "?") + " §7each")));
					if (ex != null) t.add(line("§7That many cost §6" + coins(ex) + " §8on the bazaar now"));
					double craft = InfernoPlanner.craftPrice(id);
					if (craft >= 0) t.add(line("§7Crafting one costs §6≈" + coins(Math.round(craft))));
					if (craftable && InfernoPlanner.BUY.contains(id)) t.add(line("§8You chose to buy it. Click to craft it again"));
					tip = t;
				}
			} else if (rows.get(i) instanceof InfernoPlanner.Step s) {
				boolean top = s.id().equals(p.fuel());
				String name = top ? InfernoData.label(s.id()) : InfernoData.plain(s.id());
				boolean hover = row(g, i, null, s.id(), name, top ? TazTheme.WHITE : TazTheme.TEXT, 3, mx, my, top ? null : () -> InfernoPlanner.BUY.add(s.id()));
				String from = String.join(" + ", s.recipe().in().entrySet().stream().map(e -> e.getValue() + " " + InfernoData.plain(e.getKey())).toList());
				badge(g, i, "Craft " + String.format(Locale.ROOT, "%,d", s.crafts()) + "x", TazTheme.PALE_RED, " · " + from, 3);
				cell(g, i, 2, false, String.format(Locale.ROOT, "%,d", s.makes()), TazTheme.TEXT);
				if (s.spare() > 0) cell(g, i, 2, true, s.spare() + " spare", TazTheme.DARK_GREY);
				if (!top) cell(g, i, 0, false, "buy instead?", TazTheme.DARK_GREY);
				if (hover) {
					List<Component> t = new ArrayList<>(List.of(line(InfernoData.name(s.id())), Component.empty(),
							line("§7Craft §f" + String.format(Locale.ROOT, "%,d", s.crafts()) + " §7times: §f" + from + " §7each"),
							line("§7Makes §f" + String.format(Locale.ROOT, "%,d", s.makes()) + (s.spare() > 0 ? " §8(" + s.spare() + " spare)" : ""))));
					if (!top) {
						double craft = InfernoPlanner.craftPrice(s.id()), buy = InfernoPrices.instaBuy(s.id());
						if (craft >= 0) t.add(line("§7Crafting one: §6≈" + coins(Math.round(craft))));
						if (buy >= 0) t.add(line("§7Buying one: §6" + coins(Math.round(buy))));
						t.add(line("§8Click to buy it instead"));
					}
					tip = t;
				}
			}
		}
		if (!p.complete()) empty(g, "", "Waiting for SkyCofl's prices…");
	}

	/** The plan in chat: what to buy (click a line for the bazaar) and what to craft, in order. */
	private void listInChat() {
		InfernoPlanner.Plan p = plan();
		if (!p.complete()) return;
		Map<String, Long> exact = InfernoPlanner.exact(p);
		long total = exact.isEmpty() ? Math.round(p.total()) : exact.values().stream().mapToLong(Long::longValue).sum();
		TazChat.send(TazTheme.styled("§7Fuel plan: §f" + amount + "x " + InfernoData.label(p.fuel()) + " §7for §6" + (exact.isEmpty() ? "≈" : "")
				+ coins(total) + " §8(" + coins(total / Math.max(1, amount)) + " each)"));
		TazChat.send(TazTheme.styled("§7Buy (click to open the bazaar):"));
		for (Map.Entry<String, Long> e : p.buy().entrySet()) {
			Long ex = exact.get(e.getKey());
			double each = p.each().getOrDefault(e.getKey(), -1.0);
			String cost = ex != null ? coins(ex) : "≈" + coins(Math.round(each * e.getValue()));
			String name = InfernoData.plain(e.getKey());
			boolean ah = InfernoData.fuel(e.getKey()) != null;
			Component lineC = TazTheme.styled(" §f" + String.format(Locale.ROOT, "%,d", e.getValue()) + "x " + InfernoData.name(e.getKey()) + " §6" + cost)
					.withStyle(st -> st.withClickEvent(new ClickEvent.RunCommand(ah ? "/ahs " + name : "/bz " + name))
							.withHoverEvent(new HoverEvent.ShowText(Component.literal(ah ? "Search the AH" : "Open the bazaar at " + name))));
			TazChat.send(lineC);
		}
		List<InfernoPlanner.Step> steps = new ArrayList<>(p.steps());
		java.util.Collections.reverse(steps);
		TazChat.send(TazTheme.styled("§7Then craft, in this order:"));
		for (InfernoPlanner.Step s : steps) {
			String from = String.join(" + ", s.recipe().in().entrySet().stream().map(e -> e.getValue() + " " + InfernoData.plain(e.getKey())).toList());
			TazChat.send(TazTheme.styled(" §f" + String.format(Locale.ROOT, "%,d", s.crafts()) + "x §7" + InfernoData.label(s.id()) + " §8(" + from + ")"));
		}
		note = "Sent to chat";
		noteAt = System.currentTimeMillis();
	}

	// ---- bits

	private void pick(Object o) {
		picked = picked == o ? null : o;
		box.visible = false;
		deleteArmed = 0;
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
		return InfernoTracker.coins(coins);
	}

	private static String signed(long coins) {
		return (coins < 0 ? "-" : "+") + InfernoTracker.coins(Math.abs(coins));
	}

	// ---- input

	private void click(int x, int y, int w, int h, Runnable action) {
		hits.add(new Hit(x, y, w, h, action));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (box.visible && box.isMouseOver(event.x(), event.y()) || amountBox.visible && amountBox.isMouseOver(event.x(), event.y())) {
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
			if (typingCollected) {
				Map<String, Long> items = InfernoTracker.parseItems(box.getValue());
				if (items == null) {
					note = "Try: 33 chili, 1 vertex";
				} else {
					InfernoTracker.addCollected(items, picked instanceof InfernoTracker.Run r ? r : null);
					note = "Added " + String.join(", ", items.entrySet().stream().map(e -> e.getValue() + " " + InfernoData.plain(e.getKey())).toList());
					box.visible = false;
				}
				noteAt = System.currentTimeMillis();
				return true;
			}
			Long v = AuctionInput.parse(box.getValue());
			if (v != null && v >= 0 && picked instanceof InfernoTracker.Run r) {
				InfernoTracker.editCost(r, v);
				box.visible = false;
			}
			return true;
		}
		if (box.visible && event.key() == GLFW.GLFW_KEY_ESCAPE) {
			box.visible = false;
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void removed() {
		InfernoTracker.save();
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private static boolean in(int mx, int my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}
}
