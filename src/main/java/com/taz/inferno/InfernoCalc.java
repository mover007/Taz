package com.taz.inferno;

import com.google.gson.annotations.Expose;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The Calculator tab: a day of Inferno minions with any setup, item by item, at SkyCofl prices. */
public final class InfernoCalc {
	/** What a big batch of fuel is planned with, so a day's parts come out even (no crafts rounded up). */
	private static final int BATCH = 100;

	private InfernoCalc() {
	}

	/** A minion upgrade slot. */
	public enum Upgrade {
		NONE("None", 0), FLYCATCHER("Flycatcher", 0.20), EXPANDER("Expander", 0.05), COMPACTOR("Compactor", 0);

		public final String label;
		public final double speed;

		Upgrade(String label, double speed) {
			this.label = label;
			this.speed = speed;
		}

		Upgrade next() {
			return values()[(ordinal() + 1) % values().length];
		}

		@Override
		public String toString() {
			return label;
		}
	}

	/** The setup the Calculator shows (saved with the settings). */
	public static final class Setup {
		/** False until it's been filled in from your minions once. */
		@Expose
		public boolean set;
		@Expose
		public int tier = 11;
		@Expose
		public String fuel = "INFERNO_HYPERGOLIC_CRUDE_GABAGOOL";
		@Expose
		public Upgrade slot1 = Upgrade.FLYCATCHER;
		@Expose
		public Upgrade slot2 = Upgrade.COMPACTOR;
		@Expose
		public int count = 1;
		@Expose
		public boolean infusion = true;
		/** Beacon tier, 0 for none: +2% minion speed a tier. */
		@Expose
		public int beacon;
		/** A Scorched Power Crystal in the beacon: +1% more. */
		@Expose
		public boolean scorched;
		@Expose
		public boolean postcard;
		@Expose
		public boolean freeWill;
		@Expose
		public boolean eyedrops;
		/** Show one minion's day instead of all of them. */
		@Expose
		public boolean oneMinion;

		public InfernoData.Fuel fuel() {
			InfernoData.Fuel f = InfernoData.fuel(fuel);
			return f != null ? f : new InfernoData.Fuel(InfernoData.Grade.HYPERGOLIC, InfernoData.Kind.CRUDE_GABAGOOL);
		}

		boolean compactor() {
			return slot1 == Upgrade.COMPACTOR || slot2 == Upgrade.COMPACTOR;
		}
	}

	/** A line of the day: how many, worth (or costing) how much each. */
	public record Line(String id, double amount, double each, boolean bought) {
		public double total() {
			return each < 0 ? 0 : amount * each;
		}
	}

	/** One day with this setup: what it makes, what it costs, and how fast it runs. */
	public record Day(int minions, double speed, double secondsPerAction, List<Line> makes, List<Line> costs, boolean complete) {
		public double revenue() {
			return makes.stream().mapToDouble(Line::total).sum();
		}

		public double expenses() {
			return costs.stream().mapToDouble(Line::total).sum();
		}

		public double profit() {
			return revenue() - expenses();
		}
	}

	/** The speed boost: Rising Celsius (18% for each Inferno minion, up to 10) and everything else on. */
	public static double speed(Setup s) {
		double b = Math.min(InfernoData.CELSIUS_MAX, Math.max(1, s.count)) * InfernoData.CELSIUS;
		b += s.slot1.speed + s.slot2.speed;
		if (s.infusion) b += 0.10;
		if (s.beacon > 0) b += s.beacon * 0.02 + (s.scorched ? 0.01 : 0);
		if (s.postcard) b += 0.05;
		if (s.freeWill) b += 0.10;
		return b;
	}

	public static Day day(Setup s) {
		InfernoData.Fuel f = s.fuel();
		double boost = speed(s);
		int n = s.oneMinion ? 1 : Math.max(1, s.count);
		boolean hyper = f.grade() == InfernoData.Grade.HYPERGOLIC;
		Map<String, Double> made = new LinkedHashMap<>(InfernoData.makes(f, s.tier, boost, s.eyedrops && hyper));
		// A Super Compactor turns Crude Gabagool into Very Crude Gabagool, which is what you sell.
		if (s.compactor()) {
			Double crude = made.remove("CRUDE_GABAGOOL");
			if (crude != null) made.merge("VERY_CRUDE_GABAGOOL", crude / 192, Double::sum);
		}
		boolean complete = true;
		List<Line> makes = new ArrayList<>();
		for (Map.Entry<String, Double> e : made.entrySet()) {
			double each = InfernoPrices.value(e.getKey());
			if (each < 0) complete = false;
			makes.add(new Line(e.getKey(), e.getValue() * n, each, false));
		}
		makes.sort((a, b) -> Double.compare(b.total(), a.total()));
		// A fuel a minion a day: the cheapest way to make it, its parts at your bazaar prices.
		List<Line> costs = new ArrayList<>();
		InfernoPlanner.Plan p = InfernoPlanner.plan(f, BATCH);
		if (!p.complete()) complete = false;
		for (Map.Entry<String, Long> e : p.buy().entrySet()) {
			double each = InfernoPrices.cost(e.getKey());
			if (each < 0) each = p.each().getOrDefault(e.getKey(), -1.0);
			if (each < 0) complete = false;
			costs.add(new Line(e.getKey(), e.getValue() * (double) n / BATCH, each, true));
		}
		if (s.eyedrops && hyper) {
			double each = InfernoPrices.cost(InfernoData.EYEDROPS);
			if (each < 0) complete = false;
			costs.add(new Line(InfernoData.EYEDROPS, n, each, true));
		}
		costs.sort((a, b) -> Double.compare(b.total(), a.total()));
		double secs = InfernoData.seconds(s.tier) / ((1 + boost) * (1 + f.grade().speed));
		return new Day(n, boost, secs, makes, costs, complete);
	}

	/** Fills the setup in from your minions: their tier, upgrades, how many, and your last fuel. */
	public static void yours(Setup s) {
		InfernoTracker.Minion m = InfernoTracker.best();
		s.tier = m != null && m.tier > 0 ? m.tier : InfernoTracker.tier();
		List<Upgrade> slots = new ArrayList<>();
		if (m != null) {
			for (String u : m.upgrades) {
				if (u.equals("FLYCATCHER_UPGRADE")) slots.add(Upgrade.FLYCATCHER);
				else if (u.equals("MINION_EXPANDER")) slots.add(Upgrade.EXPANDER);
				else if (u.equals("SUPER_COMPACTOR_3000")) slots.add(Upgrade.COMPACTOR);
			}
		}
		s.slot1 = slots.size() > 0 ? slots.get(0) : Upgrade.NONE;
		s.slot2 = slots.size() > 1 ? slots.get(1) : Upgrade.NONE;
		int count = InfernoTracker.tabCount();
		s.count = count > 0 ? count : Math.max(1, (int) InfernoTracker.minions().stream().filter(x -> !x.key.startsWith("?")).count());
		s.infusion = InfernoConfig.get().infusion;
		InfernoTracker.runs().stream().max(java.util.Comparator.comparingLong(r -> r.placedAt)).ifPresent(r -> {
			if (InfernoData.fuel(r.fuel) != null) s.fuel = r.fuel;
			s.eyedrops = r.eyedrops > 0;
		});
		s.set = true;
	}
}
