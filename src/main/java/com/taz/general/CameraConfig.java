package com.taz.general;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Controls > Camera in the Taz settings GUI (/taz). */
public final class CameraConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "F5 skips the front-facing view (first person, then behind you). From Odin.")
	@ConfigEditorBoolean
	@SearchTag("camera f5 front third person")
	public boolean enabled = false;

	public static CameraConfig get() {
		return Taz.config().controls.camera;
	}
}
