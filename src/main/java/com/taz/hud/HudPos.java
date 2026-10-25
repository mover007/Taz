package com.taz.hud;

import com.google.gson.annotations.Expose;

/**
 * Where a {@link TextHud} sits, saved with its feature's settings (not shown in the settings GUI).
 * x and y are fractions of the screen so the spot survives resizing.
 */
public final class HudPos {
	@Expose
	public double x;
	@Expose
	public double y;
	@Expose
	public float scale = 1.0f;
	@Expose
	public boolean hidden;

	public HudPos() {
	}

	public HudPos(double x, double y) {
		this.x = x;
		this.y = y;
	}
}
