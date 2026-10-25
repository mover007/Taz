package com.taz.xpassist.util;

import java.util.Locale;
import java.util.regex.Pattern;

public final class TextUtil {
	private static final Pattern FORMATTING = Pattern.compile("(?i)§[0-9a-fk-orx]");
	private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]");

	private TextUtil() {
	}

	/** Removes Minecraft legacy formatting codes. */
	public static String strip(String text) {
		if (text == null) {
			return "";
		}
		return FORMATTING.matcher(text).replaceAll("").replace("§", "");
	}

	/** Lowercase letters and digits only; used for fuzzy name matching. */
	public static String normalize(String text) {
		return NON_ALNUM.matcher(strip(text).toLowerCase(Locale.ROOT)).replaceAll("");
	}

	/** "GOLDEN_DRAGON" -> "Golden Dragon". */
	public static String titleCase(String id) {
		StringBuilder out = new StringBuilder();
		for (String part : id.toLowerCase(Locale.ROOT).split("_")) {
			if (part.isEmpty()) {
				continue;
			}
			if (!out.isEmpty()) {
				out.append(' ');
			}
			out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}
		return out.toString();
	}

	/** Formats coins the way the SkyHelper bot does: 500, 1.8K, 25.4K, 18.11B. */
	public static String coins(double value, int decimals) {
		if (Double.isNaN(value)) {
			return "?";
		}
		double abs = Math.abs(value);
		String suffix;
		double scaled;
		if (abs >= 1e9) {
			scaled = value / 1e9;
			suffix = "B";
		} else if (abs >= 1e6) {
			scaled = value / 1e6;
			suffix = "M";
		} else if (abs >= 1e3) {
			scaled = value / 1e3;
			suffix = "K";
		} else {
			return String.valueOf(Math.round(value));
		}
		String number = String.format(Locale.ROOT, "%." + decimals + "f", scaled);
		if (number.contains(".")) {
			number = number.replaceAll("0+$", "").replaceAll("\\.$", "");
		}
		return number + suffix;
	}

	public static String coins(double value) {
		return coins(value, 1);
	}

	/** Exact amount with separators, e.g. 72500 -> "72,500". */
	public static String amount(double value) {
		return String.format(Locale.US, "%,d", Math.round(value));
	}

	/** "1,500 Fossil Essence, 64 Foul Flesh". */
	public static String materials(java.util.Map<String, Double> materials) {
		StringBuilder out = new StringBuilder();
		for (java.util.Map.Entry<String, Double> entry : materials.entrySet()) {
			if (!out.isEmpty()) {
				out.append(", ");
			}
			out.append(amount(entry.getValue())).append(' ').append(entry.getKey());
		}
		return out.toString();
	}

	public static String xp(double xp) {
		if (xp == Math.floor(xp)) {
			return String.valueOf((long) xp);
		}
		return String.format(Locale.ROOT, "%.1f", xp);
	}

	private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII", "XIII", "XIV", "XV"};

	public static String roman(int number) {
		return number > 0 && number < ROMAN.length ? ROMAN[number] : String.valueOf(number);
	}

	/** Removes a trailing roman numeral, e.g. "Flower Minion XI" -> "Flower Minion". */
	public static String stripTrailingRoman(String name) {
		return name.replaceFirst("\\s+[IVXLC]+$", "");
	}
}
