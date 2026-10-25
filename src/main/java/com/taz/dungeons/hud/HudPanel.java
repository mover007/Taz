package com.taz.dungeons.hud;

/** The movable HUD panels. Positions are fractions of the screen (top-left corner of the panel). */
public enum HudPanel {
	TIMER("timer", "Timer & ETA", 0.005, 0.22),
	SCORE("score", "Score", 0.005, 0.36),
	SPLITS("splits", "Splits", 0.005, 0.50),
	SESSION("session", "Session", 0.005, 0.70);

	public final String id;
	public final String displayName;
	public final double defaultX;
	public final double defaultY;

	HudPanel(String id, String displayName, double defaultX, double defaultY) {
		this.id = id;
		this.displayName = displayName;
		this.defaultX = defaultX;
		this.defaultY = defaultY;
	}
}
