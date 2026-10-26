package com.taz.general;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/**
 * General > Held Item in the Taz settings GUI (/taz): the size, place and swing of the item in your hand. From NoammAddons'
 * Animations (ItemInHandRendererMixin and LivingEntityMixin read these).
 */
public final class HeldItemConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Change the size, position, turn and swing of the item in your hand.")
	@ConfigEditorBoolean
	@SearchTag("held item animations view model hand size position swing speed terminator")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Size", desc = "0 is normal, -0.5 half size, 1 double.")
	@ConfigEditorSlider(minValue = -1.5f, maxValue = 1.5f, minStep = 0.05f)
	public float scale = 0;

	@Expose
	@ConfigOption(name = "X", desc = "Left / right.")
	@ConfigEditorSlider(minValue = -2, maxValue = 2, minStep = 0.01f)
	public float x = 0;

	@Expose
	@ConfigOption(name = "Y", desc = "Down / up.")
	@ConfigEditorSlider(minValue = -2, maxValue = 2, minStep = 0.01f)
	public float y = 0;

	@Expose
	@ConfigOption(name = "Z", desc = "Nearer / further.")
	@ConfigEditorSlider(minValue = -2, maxValue = 2, minStep = 0.01f)
	public float z = 0;

	@Expose
	@ConfigOption(name = "Turn X", desc = "Degrees.")
	@ConfigEditorSlider(minValue = -50, maxValue = 50, minStep = 1)
	public float rotX = 0;

	@Expose
	@ConfigOption(name = "Turn Y", desc = "Degrees.")
	@ConfigEditorSlider(minValue = -50, maxValue = 50, minStep = 1)
	public float rotY = 0;

	@Expose
	@ConfigOption(name = "Turn Z", desc = "Degrees.")
	@ConfigEditorSlider(minValue = -50, maxValue = 50, minStep = 1)
	public float rotZ = 0;

	@Expose
	@ConfigOption(name = "Swing X", desc = "How far the swing moves sideways (1 is normal).")
	@ConfigEditorSlider(minValue = 0, maxValue = 2, minStep = 0.01f)
	public float swingX = 1;

	@Expose
	@ConfigOption(name = "Swing Y", desc = "How far the swing moves up and down (1 is normal).")
	@ConfigEditorSlider(minValue = 0, maxValue = 2, minStep = 0.01f)
	public float swingY = 1;

	@Expose
	@ConfigOption(name = "Swing Z", desc = "How far the swing moves forward (1 is normal).")
	@ConfigEditorSlider(minValue = 0, maxValue = 2, minStep = 0.01f)
	public float swingZ = 1;

	@Expose
	@ConfigOption(name = "Swing speed", desc = "0 is normal; lower is slower, higher is faster.")
	@ConfigEditorSlider(minValue = -2, maxValue = 1, minStep = 0.05f)
	public float swingSpeed = 0;

	@Expose
	@ConfigOption(name = "Ignore Haste", desc = "The swing speed ignores Haste and Mining Fatigue.")
	@ConfigEditorBoolean
	public boolean ignoreHaste = false;

	@Expose
	@ConfigOption(name = "No hand sway", desc = "The item doesn't sway when you look around.")
	@ConfigEditorBoolean
	public boolean noSway = false;

	@Expose
	@ConfigOption(name = "No equip animation", desc = "No dip when the item in your hand changes.")
	@ConfigEditorBoolean
	public boolean noEquip = false;

	@Expose
	@ConfigOption(name = "No swing", desc = "The item doesn't swing.")
	@ConfigEditorBoolean
	public boolean noSwing = false;

	@Expose
	@ConfigOption(name = "No swing: Terminator only", desc = "Only stop the swing while holding a Terminator.")
	@ConfigEditorBoolean
	public boolean terminatorOnly = false;

	@ConfigOption(name = "Copy from NoammAddons", desc = "Copies your NoammAddons Animations values.")
	@ConfigEditorButton(buttonText = "Copy")
	public transient Runnable importNoamm = HeldItem::importFromNoamm;

	public static HeldItemConfig get() {
		return Taz.config().general.heldItem;
	}
}
