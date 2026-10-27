package com.taz.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.managed.GsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Keeps settings from ever going back to defaults: config.json.last-good is a copy of the last file that loaded fine,
 * and a file that can't be read (the game killed while writing, a bad hand edit) is swapped for that copy before
 * loading.
 */
public final class ConfigSafety {
	private ConfigSafety() {
	}

	private static Path lastGood(Path file) {
		return file.resolveSibling(file.getFileName() + ".last-good");
	}

	/** True when the file reads as Taz settings. */
	static boolean readable(Path file) {
		try {
			String text = Files.readString(file, StandardCharsets.UTF_8);
			JsonObject json = JsonParser.parseString(text).getAsJsonObject();
			return json.size() > 0 && new GsonMapper<>(TazConfig.class).deserialize(text) != null;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Before loading. Returns a message for the player when the file was restored (or couldn't be), else null.
	 */
	public static String restoreIfBroken(Path file) {
		if (!Files.exists(file) || readable(file)) return null;
		Path backup = lastGood(file);
		try {
			Path broken = file.resolveSibling(file.getFileName() + ".broken-" + System.currentTimeMillis());
			Files.move(file, broken, StandardCopyOption.REPLACE_EXISTING);
			Taz.LOGGER.warn("Taz settings file couldn't be read; kept it as {}", broken.getFileName());
			if (Files.exists(backup) && readable(backup)) {
				Files.copy(backup, file, StandardCopyOption.REPLACE_EXISTING);
				Taz.LOGGER.warn("Restored Taz settings from {}", backup.getFileName());
				return "Your settings file was damaged, so Taz put back the last good copy. Nothing was lost.";
			}
			return "Your settings file was damaged and there was no good copy, so Taz started from defaults. "
					+ "The damaged file is config/taz/" + broken.getFileName() + ".";
		} catch (Exception e) {
			Taz.LOGGER.error("Could not restore the Taz settings file", e);
			return null;
		}
	}

	/** After a good load: the copy to restore from next time. */
	public static void keepGoodCopy(Path file) {
		try {
			if (readable(file)) Files.copy(file, lastGood(file), StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception e) {
			Taz.LOGGER.warn("Could not keep a copy of the Taz settings", e);
		}
	}
}
