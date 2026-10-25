package com.taz.xpassist.xp;

public enum XpCategory {
	MUSEUM("Museum", 0xFFD27F),
	MINIONS("Unique Minions", 0x7FD8FF),
	ABIPHONE("Abiphone", 0xC58CFF),
	ACCESSORIES("Accessories", 0xFF9FD6),
	PETS("Pets", 0x9CFF8C),
	CONSUMABLES("Consumables", 0xFFB37F),
	ESSENCE("Essence Shop", 0x8CE8FF),
	MISC("Misc", 0xD9D9D9);

	public final String label;
	public final int color;

	XpCategory(String label, int color) {
		this.label = label;
		this.color = color;
	}
}
