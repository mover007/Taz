package com.taz.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A HUD that can be moved, resized and hidden in the Taz HUD editor. */
public interface MovableHud {
	/** Shown in the editor. */
	String name();

	/** Its feature is switched on (otherwise it's neither drawn nor listed in the editor). */
	boolean enabled();

	boolean hidden();

	void setHidden(boolean hidden);

	float scale();

	void setScale(float scale);

	/** Back to the default spot and size. */
	void reset();

	/**
	 * Screen box {x, y, width, height} it takes up now, or null when there's nothing to show. With
	 * {@code preview} (the editor), sample content is used when there is nothing live.
	 */
	int[] bounds(Font font, int screenW, int screenH, boolean preview);

	/** Moves the box's top-left corner to x, y. */
	void moveTo(int x, int y, int screenW, int screenH);

	/** Draws it at its spot (with sample content when {@code preview} and nothing live). */
	void draw(GuiGraphicsExtractor g, Font font, boolean preview);
}
