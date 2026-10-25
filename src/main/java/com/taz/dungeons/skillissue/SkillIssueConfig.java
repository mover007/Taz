package com.taz.dungeons.skillissue;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Dungeons > Skill Issue in the Taz settings GUI (/taz). */
public final class SkillIssueConfig {
	public enum Where {
		PARTY("Party chat"),
		ME("Only me");

		private final String label;

		Where(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	@Expose
	@ConfigOption(name = "Enabled", desc = "When someone in your dungeon dies, Taz makes a friendly joke about it: what killed them, where "
			+ "(clear, blood, Maxor, Storm, terminals, core, Necron, dragons, Sadan...), how (a trap, crushed, disconnected) and their "
			+ "class: \"[TAZ] Steve died doing terms. Outplayed by a GUI.\" When it's you, Taz makes an excuse instead.")
	@ConfigEditorBoolean
	@SearchTag("death roast joke party chat ghost died")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Where", desc = "Say it in party chat (not when you're solo), or only show it to you.")
	@ConfigEditorDropdown
	public Where where = Where.PARTY;

	@Expose
	@ConfigOption(name = "Excuses for your deaths", desc = "When you die, make an excuse for you: \"[TAZ] Died in terms. The GUI lagged and "
			+ "ate my click.\" Off: say nothing about your own deaths.")
	@ConfigEditorBoolean
	public boolean yourself = true;

	@Expose
	@ConfigOption(name = "Wait between jokes", desc = "Seconds between two jokes, so a wipe doesn't flood the chat.")
	@ConfigEditorSlider(minValue = 1, maxValue = 30, minStep = 1)
	public int cooldownSec = 4;

	@ConfigOption(name = "Try it", desc = "Shows you a few jokes and an excuse Taz could make (nothing is sent).")
	@ConfigEditorButton(buttonText = "Try")
	public transient Runnable sample = SkillIssue::sample;

	public static SkillIssueConfig get() {
		return Taz.config().dungeons.skillIssue;
	}
}
