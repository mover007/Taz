package com.taz.gametest;

import java.util.Arrays;
import java.util.Locale;

/**
 * Runs only the tests for the feature being worked on: gradlew runClientGameTest -Ptests=honeyhive (comma-separated
 * names, e.g. -Ptests=trees,settings).
 */
final class TestFilter {
	private static final String SELECTED = System.getProperty("taz.tests", "").trim().toLowerCase(Locale.ROOT);

	private TestFilter() {
	}

	/** True when any of these names was picked (or nothing was picked), without printing anything. */
	static boolean runs(String... names) {
		if (SELECTED.isEmpty()) return true;
		for (String name : names) {
			if (Arrays.stream(SELECTED.split(",")).map(String::trim).anyMatch(name::equalsIgnoreCase)) return true;
		}
		return false;
	}

	/** True when this test wasn't picked, so it should return straight away. */
	static boolean skip(String name) {
		if (SELECTED.isEmpty()) return false;
		boolean picked = Arrays.stream(SELECTED.split(",")).map(String::trim).anyMatch(name::equalsIgnoreCase);
		if (!picked) System.out.println("[TestFilter] skipped " + name + " (running: " + SELECTED + ")");
		return !picked;
	}
}
