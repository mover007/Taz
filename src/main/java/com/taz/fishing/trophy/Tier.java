package com.taz.fishing.trophy;

/** The four tiers of every Trophy Fish and Trophy Frog, with the colour code Taz shows each in. */
public enum Tier {
	BRONZE("§6"), SILVER("§f"), GOLD("§e"), DIAMOND("§b");

	public final String code;

	Tier(String code) {
		this.code = code;
	}

	/** "B", "S", "G" or "D". */
	public String letter() {
		return name().substring(0, 1);
	}

	public static Tier named(String name) {
		for (Tier t : values()) if (t.name().equalsIgnoreCase(name.trim())) return t;
		return null;
	}
}
