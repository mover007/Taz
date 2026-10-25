package com.taz.general;

import java.util.HashMap;
import java.util.Map;

/**
 * Sound Volumes: make any sound louder or quieter by its id, like "minecraft:block.amethyst_block.break=1.35". From
 * NoammAddons' Sound Manager (AbstractSoundInstanceMixin asks here).
 */
public final class SoundVolumes {
	private static String parsedFrom;
	private static Map<String, Float> parsed = new HashMap<>();

	private SoundVolumes() {
	}

	private static Map<String, Float> volumes() {
		String text = SoundVolumesConfig.get().volumes;
		if (!text.equals(parsedFrom)) {
			Map<String, Float> out = new HashMap<>();
			for (String pair : text.split(",")) {
				int eq = pair.indexOf('=');
				if (eq <= 0) continue;
				String id = pair.substring(0, eq).trim();
				if (!id.contains(":")) id = "minecraft:" + id;
				try {
					out.put(id, Math.max(0, Math.min(2, Float.parseFloat(pair.substring(eq + 1).trim()))));
				} catch (NumberFormatException ignored) {
					// skip a bad entry
				}
			}
			parsed = out;
			parsedFrom = text;
		}
		return parsed;
	}

	/** The volume multiplier for a sound id ("minecraft:block.amethyst_block.break"). */
	public static float multiplier(String id) {
		if (!SoundVolumesConfig.get().enabled) return 1;
		return volumes().getOrDefault(id, 1f);
	}
}
