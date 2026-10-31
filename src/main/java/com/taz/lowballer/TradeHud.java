package com.taz.lowballer;

import com.taz.dungeons.util.TextUtil;
import com.taz.hud.HudPos;
import com.taz.hud.TextHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * The Lowballer HUD, drawn in the trade menu: what their side is worth, lowball offers to click (the smart one and
 * 10%, 12%, 14%... under), how your coins compare, and each of their items.
 */
public final class TradeHud extends TextHud {
	public static final TradeHud HUD = new TradeHud();
	private static final int NAME_WIDTH = 22;
	private static final java.util.regex.Pattern PET = java.util.regex.Pattern.compile("^\\[Lvl (\\d+)] (.+)$");

	/** A line, how it looks under the mouse, and the coins it offers when clicked (0: not clickable). */
	private record Row(String text, String hover, long offer) {
	}

	private boolean inMenu;
	private List<Row> rows = List.of();
	private int hovered = -1;
	/** Below 1 when it's shrunk to fit left of the chest. */
	private float fit = 1;

	private TradeHud() {
		super("Lowballer (trade menu)", 0.01, 0.12);
	}

	@Override
	protected HudPos pos() {
		return LowballerConfig.get().hud;
	}

	@Override
	public boolean enabled() {
		return LowballerConfig.get().enabled;
	}

	/** Only in the trade menu (the screen draws it, on top of the chest); sample lines in the HUD editor. */
	@Override
	protected List<String> lines(boolean preview) {
		List<Row> shown = inMenu ? rows : preview ? sample() : List.of();
		List<String> out = new ArrayList<>(shown.size());
		for (int i = 0; i < shown.size(); i++) out.add(i == hovered && shown.get(i).offer() > 0 ? shown.get(i).hover() : shown.get(i).text());
		return out;
	}

	@Override
	protected float scaleNow() {
		return inMenu && fit < 1 ? Math.max(0.5f, super.scaleNow() * fit) : super.scaleNow();
	}

	/** Left of the chest but too wide for the room there (a big GUI scale, a small window): shrinks it to fit. */
	private void fit(Font font, int screenW, int screenH, int chestLeft) {
		fit = 1;
		int[] b = bounds(font, screenW, screenH, false);
		if (b == null || b[0] + b[2] <= chestLeft - 2 || b[0] + b[2] / 2 > chestLeft) return;
		fit = Math.max(0.1f, (chestLeft - 2 - b[0]) / (float) b[2]);
	}

	/** End of the trade menu's drawing; {@code chestLeft} is where the chest starts. */
	public static void render(AbstractContainerScreen<?> screen, GuiGraphicsExtractor g, int mouseX, int mouseY, int chestLeft) {
		TradeMenu.Trade t = TradeMenu.trade();
		Minecraft mc = Minecraft.getInstance();
		if (t == null || HUD.hidden() || screen != mc.screen) return;
		LowballerConfig cfg = LowballerConfig.get();
		HUD.rows = rows(t, cfg);
		HUD.inMenu = true;
		try {
			HUD.fit(mc.font, g.guiWidth(), g.guiHeight(), chestLeft);
			HUD.hovered = cfg.clickToOffer ? HUD.lineAt(mc.font, g.guiWidth(), g.guiHeight(), mouseX, mouseY) : -1;
			HUD.draw(g, mc.font, false);
		} finally {
			HUD.inMenu = false;
			HUD.hovered = -1;
		}
	}

	/** A click in the trade menu: on an offer, it's chosen. True when it was. */
	public static boolean onClick(AbstractContainerScreen<?> screen, double mouseX, double mouseY, int chestLeft) {
		TradeMenu.Trade t = TradeMenu.trade();
		LowballerConfig cfg = LowballerConfig.get();
		Minecraft mc = Minecraft.getInstance();
		if (t == null || !cfg.clickToOffer || HUD.hidden() || screen != mc.screen) return false;
		HUD.rows = rows(t, cfg);
		HUD.inMenu = true;
		try {
			HUD.fit(mc.font, screen.width, screen.height, chestLeft);
			int i = HUD.lineAt(mc.font, screen.width, screen.height, mouseX, mouseY);
			if (i < 0 || HUD.rows.get(i).offer() <= 0) return false;
			TradeMenu.choose(HUD.rows.get(i).offer());
			mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
			return true;
		} finally {
			HUD.inMenu = false;
		}
	}

	/** Test hook: the middle of a line on screen, as the trade menu draws it. */
	public static double[] spot(int line, int chestLeft) {
		Minecraft mc = Minecraft.getInstance();
		HUD.rows = rows(TradeMenu.trade(), LowballerConfig.get());
		HUD.inMenu = true;
		try {
			HUD.fit(mc.font, mc.screen.width, mc.screen.height, chestLeft);
			int[] b = HUD.bounds(mc.font, mc.screen.width, mc.screen.height, false);
			float scale = HUD.scaleNow();
			return new double[]{b[0] + 8 * scale, b[1] + (3 + line * 10 + 4) * scale};
		} finally {
			HUD.inMenu = false;
		}
	}

	/** The lines for a trade (also what the tests read). */
	public static List<String> text(TradeMenu.Trade t) {
		return rows(t, LowballerConfig.get()).stream().map(Row::text).toList();
	}

	private static List<Row> rows(TradeMenu.Trade t, LowballerConfig cfg) {
		List<Row> out = new ArrayList<>();
		out.add(line("§c§lLowballer §8· §7" + t.other()));
		int n = t.items().size();
		if (n == 0 && t.coins() == 0) {
			out.add(line("§8Waiting for their items"));
			return out;
		}
		String what = n + (n == 1 ? " item" : " items") + (t.coins() > 0 ? " + " + coins(t.coins()) + " coins" : "");
		out.add(line("§7Worth: §6" + coins(t.worth()) + " §8(" + what + ")"));
		if (t.pricing() > 0) out.add(line("§7Pricing " + t.pricing() + (t.pricing() == 1 ? " item…" : " items…")));
		if (t.unpriced() > 0) out.add(line("§e" + t.unpriced() + (t.unpriced() == 1 ? " item has" : " items have") + " no price: left out"));
		if (t.problem() != null) out.add(line("§c" + t.problem()));

		long items = t.itemsWorth();
		if (items > 0) {
			if (cfg.smartOffer) {
				long smart = t.smart();
				out.add(offer("§a§lSmart offer:", smart, " §8(" + Lowball.pct(1 - smart / (double) t.worth()) + " under)"));
			}
			for (double step : Lowball.steps(cfg.steps)) {
				long v = Lowball.round(Math.round(items * (1 - step / 100))) + t.coins();
				out.add(offer("§7-" + Lowball.pct(step / 100) + ":", v, ""));
			}
		}
		if (cfg.yourOffer && t.yourCoins() > 0 && t.worth() > 0) {
			double under = 1 - t.yourCoins() / (double) t.worth();
			out.add(line("§7Your offer: §6" + coins(t.yourCoins()) + (under >= 0 ? " §8(" + Lowball.pct(under) + " under)" : " §c(" + Lowball.pct(-under) + " over!)")));
		}
		if (cfg.itemList) {
			for (TradeMenu.Item i : t.items()) {
				String name = name(i.stack());
				if (i.pricing()) out.add(line("§8 " + name + " · pricing…"));
				else if (i.priced() == null) out.add(line("§8 " + name + " · no price"));
				else out.add(line("§7 " + name + " §6" + coins(i.priced().worth()) + " §8" + (i.priced().bazaar() ? "bazaar" : i.priced().volume() < 0 ? "sales ?" : Lowball.perDay(i.priced().volume()))
						+ " -" + Lowball.pct(i.offer().under())));
			}
		}
		if (cfg.clickToOffer && TradeMenu.chosen() > 0 && System.currentTimeMillis() - TradeMenu.chosenAt() < 8_000) {
			out.add(line("§aCopied " + coins(TradeMenu.chosen()) + ": now click Coins"));
		} else {
			out.add(line("§8SkyCofl prices" + (cfg.clickToOffer && items > 0 ? " · click an offer" : "")));
		}
		return out;
	}

	private static Row line(String text) {
		return new Row(text, text, 0);
	}

	private static Row offer(String label, long coins, String extra) {
		return new Row(label + " §6" + coins(coins) + extra, "§c» " + label.replaceFirst("§[0-9a-f]", "§f") + " §c" + coins(coins) + extra, coins);
	}

	/** "64x Enchanted Diamond", "Ender Dragon (100)", cut to fit. */
	private static String name(ItemStack stack) {
		String name = TextUtil.cleanChat(stack.getHoverName().getString()).trim();
		java.util.regex.Matcher pet = PET.matcher(name);
		if (pet.matches()) name = pet.group(2) + " (" + pet.group(1) + ")";
		if (stack.getCount() > 1) name = stack.getCount() + "x " + name;
		return name.length() > NAME_WIDTH ? name.substring(0, NAME_WIDTH - 1).trim() + "…" : name;
	}

	/** 936M, 1.04B, 80.7K. */
	static String coins(long coins) {
		return com.taz.xpassist.util.TextUtil.coins(coins, 2);
	}

	/** For the HUD editor: a made-up trade. */
	private static List<Row> sample() {
		List<TradeMenu.Item> items = new ArrayList<>();
		LowballerConfig cfg = LowballerConfig.get();
		for (Object[] it : new Object[][]{{"Hyperion", 456_500_000L, 1.0, 388_000_000L}, {"Ender Dragon", 469_800_000L, 26.0, 399_300_000L}}) {
			ItemStack stack = new ItemStack(Items.GOLDEN_SWORD);
			stack.set(DataComponents.CUSTOM_NAME, Component.literal((String) it[0]));
			Lowball.Priced p = new Lowball.Priced((Long) it[1], (Double) it[2], (Long) it[3], false);
			items.add(new TradeMenu.Item(stack, p, Lowball.offer(p, cfg), false));
		}
		return rows(new TradeMenu.Trade("Steve", items, 0, 800_000_000L, null), cfg);
	}
}
