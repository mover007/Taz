package com.taz.lowballer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * The smart part of the Lowballer: how far under its worth to offer for one stack. Your profit margin, plus the tax you
 * pay to sell it again, plus more the slower it sells, the more its price jumps around and the cheaper it is.
 */
public final class Lowball {
	private Lowball() {
	}

	/** What one stack is worth, how many sell a day (-1 when unknown), and a price that sells it in 5 minutes (0 when unknown). */
	public record Priced(long worth, double volume, long fastSell, boolean bazaar) {
	}

	/** How far under its worth to offer (0.15 = 15%), the coins that makes, and why, one reason per part. */
	public record Offer(double under, long coins, List<String> why) {
	}

	public static Offer offer(Priced p, LowballerConfig cfg) {
		List<String> why = new ArrayList<>();
		double margin = cfg.margin / 100.0;
		why.add(pct(margin) + " margin");
		double tax = tax(p.worth(), p.bazaar());
		why.add(pct(tax) + (p.bazaar() ? " bazaar tax" : " AH tax"));
		double under = margin + tax;
		if (!p.bazaar()) {
			double slow = slowness(p.volume());
			if (slow > 0) why.add("+" + pct(slow) + " " + (p.volume() < 0 ? "sales unknown" : "sells " + perDay(p.volume())));
			double unsteady = unsteady(p);
			if (unsteady > 0) why.add("+" + pct(unsteady) + " unsteady price");
			double cheap = p.worth() < 1_000_000 ? 0.03 : 0;
			if (cheap > 0) why.add("+3% under 1M");
			under += slow + unsteady + cheap;
		}
		under = Math.max(0, Math.min(under, cfg.maxUnder / 100.0));
		return new Offer(under, round(Math.round(p.worth() * (1 - under))), why);
	}

	/** Selling it again: the AH listing fee (1%, 2% from 10M, 2.5% from 100M) and 1% claim tax over 1M, or the 1.25% bazaar tax. */
	static double tax(long price, boolean bazaar) {
		if (bazaar) return 0.0125;
		double listing = price < 10_000_000 ? 0.01 : price < 100_000_000 ? 0.02 : 0.025;
		return listing + (price > 1_000_000 ? 0.01 : 0);
	}

	/** More off the slower it sells: nothing at 20+ a day, up to 10% under one every 4 days. */
	static double slowness(double perDay) {
		if (perDay < 0) return 0.08;
		if (perDay >= 20) return 0;
		if (perDay >= 5) return 0.02;
		if (perDay >= 1) return 0.04;
		if (perDay >= 0.25) return 0.07;
		return 0.10;
	}

	/** When selling within 5 minutes means going more than 10% under its worth, half of the rest (at most 10%). */
	static double unsteady(Priced p) {
		if (p.fastSell() <= 0 || p.worth() <= 0) return 0;
		double gap = 1 - p.fastSell() / (double) p.worth();
		return Math.max(0, Math.min((gap - 0.10) * 0.5, 0.10));
	}

	/** Down to 3 significant digits, the way you'd type it: 448,123,456 is 448,000,000. */
	public static long round(long coins) {
		if (coins < 1000) return Math.max(0, coins);
		long factor = 1;
		while (coins / factor >= 1000) factor *= 10;
		return coins / factor * factor;
	}

	/** The lowball steps from the setting ("10, 12, 14"): 1 to 99%, smallest first, at most 10. */
	public static List<Double> steps(String text) {
		TreeSet<Double> out = new TreeSet<>();
		for (String part : (text == null ? "" : text).split("[,;\\s%]+")) {
			try {
				double v = Double.parseDouble(part.trim());
				if (v >= 1 && v < 100) out.add(v);
			} catch (NumberFormatException ignored) {
				// not a number
			}
		}
		List<Double> list = new ArrayList<>(out);
		return list.size() > 10 ? list.subList(0, 10) : list;
	}

	/** "26/day", "2.5/day", "1 per 2.4 days", or "?" when unknown. */
	public static String perDay(double v) {
		if (v < 0) return "?";
		if (v >= 10) return Math.round(v) + "/day";
		if (v >= 0.95) return String.format(Locale.ROOT, "%.1f/day", v).replace(".0/", "/");
		if (v <= 0) return "rarely";
		double days = 1 / v;
		String n = days < 10 ? String.format(Locale.ROOT, "%.1f", days).replace(".0", "") : String.valueOf(Math.round(days));
		return "1 per " + n + " days";
	}

	/** 0.125 is "12.5%", 0.12 is "12%". */
	public static String pct(double fraction) {
		String s = String.format(Locale.ROOT, "%.1f", fraction * 100);
		return (s.endsWith(".0") ? s.substring(0, s.length() - 2) : s) + "%";
	}
}
