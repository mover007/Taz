package com.taz.general.pv;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.taz.hud.TazTheme;
import com.taz.util.TazChat;
import com.taz.xpassist.util.TextUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** /nw [player]: a SkyBlock networth from SkyCrypt, with cosmetics, without them, and what isn't soulbound. */
public final class Networth {
	/** Test hook: the last networth said, as plain text. */
	public static volatile String lastShown = "";

	private Networth() {
	}

	/** Gets the player's selected profile, then its networth, and says it in chat. */
	static void show(String player) {
		SkyCrypt.get("embed/" + SkyCrypt.part(player))
				.thenCompose(embed -> SkyCrypt.get("networth/" + SkyCrypt.part(Json.str(embed, "uuid")) + "/"
						+ SkyCrypt.part(Json.str(embed, "profile_id"))).thenApply(nw -> message(embed, nw)))
				.whenCompleteAsync((msg, error) -> {
					if (error != null) {
						SkyCrypt.explain(error, player);
						return;
					}
					lastShown = msg.getString();
					TazChat.send(msg);
				}, Minecraft.getInstance());
	}

	/** A line per networth; hover one to see what it's made of. */
	static MutableComponent message(JsonElement embed, JsonElement nw) {
		String name = Json.str(embed, "username"), profile = Json.str(embed, "profile_cute_name");
		JsonObject normal = Json.obj(nw, "normal"), nonCosmetic = Json.obj(nw, "nonCosmetic");
		MutableComponent out = Component.literal(name + "'s networth").withStyle(s -> s.withColor(TazTheme.RED & 0xFFFFFF))
				.append(grey(profile.isEmpty() ? "" : " · " + profile));
		out.append(line("With cosmetics", Json.num(normal, "networth"), parts(normal, "total"), "Everything, skins and dyes included."));
		out.append(line("Non-cosmetic", Json.num(nonCosmetic, "networth"), parts(nonCosmetic, "total"), "Without skins, dyes and other cosmetics."));
		out.append(line("Unsoulbound", Json.num(normal, "unsoulboundNetworth"), parts(normal, "unsoulboundTotal"),
				"What isn't soulbound, so it could be traded. Without cosmetics: "
						+ TextUtil.coins(Json.num(nonCosmetic, "unsoulboundNetworth"), 2) + "."));
		out.append(grey("\n Purse " + TextUtil.coins(Json.num(normal, "purse"), 2) + " · Bank " + TextUtil.coins(Json.num(normal, "bank"), 2)
				+ (Json.num(normal, "personalBank") > 0 ? " · Personal " + TextUtil.coins(Json.num(normal, "personalBank"), 2) : "") + " · "))
				.append(SkyCrypt.credit(name, profile));
		if (Json.bool(normal, "noInventory")) out.append(grey("\n Their inventory API is off, so items they carry aren't counted."));
		return out;
	}

	private static MutableComponent line(String label, double value, List<Component> parts, String about) {
		List<Component> hover = new ArrayList<>();
		hover.add(Component.literal(about).withStyle(s -> s.withColor(TazTheme.GREY & 0xFFFFFF)));
		hover.addAll(parts);
		return Component.literal("\n " + label + ": ").withStyle(s -> s.withColor(TazTheme.GREY & 0xFFFFFF))
				.append(Component.literal(TextUtil.coins(value, 2)).withStyle(s -> s.withColor(TazTheme.TEXT & 0xFFFFFF)
						.withHoverEvent(new HoverEvent.ShowText(Component.empty().append(join(hover))))));
	}

	/** "Inventory 12.3B" for each part of a networth, biggest first. */
	static List<Component> parts(JsonObject result, String field) {
		List<Map.Entry<String, Double>> parts = new ArrayList<>();
		for (Map.Entry<String, JsonElement> e : Json.obj(result, "types").entrySet()) {
			double v = Json.num(e.getValue(), field);
			if (v >= 1) parts.add(Map.entry(e.getKey(), v));
		}
		parts.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
		List<Component> out = new ArrayList<>();
		for (Map.Entry<String, Double> p : parts) {
			out.add(Component.literal(category(p.getKey()) + " ").withStyle(s -> s.withColor(TazTheme.GREY & 0xFFFFFF))
					.append(Component.literal(TextUtil.coins(p.getValue(), 2)).withStyle(s -> s.withColor(TazTheme.TEXT & 0xFFFFFF))));
		}
		return out;
	}

	/** "personal_vault" → "Personal Vault". */
	static String category(String key) {
		if (key.equals("enderchest")) return "Ender Chest";
		StringBuilder out = new StringBuilder();
		for (String word : key.split("_")) {
			if (word.isEmpty()) continue;
			if (!out.isEmpty()) out.append(' ');
			out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase(Locale.ROOT));
		}
		return out.toString();
	}

	private static Component join(List<Component> lines) {
		MutableComponent out = Component.empty();
		for (int i = 0; i < lines.size(); i++) {
			if (i > 0) out.append("\n");
			out.append(lines.get(i));
		}
		return out;
	}

	private static MutableComponent grey(String text) {
		return Component.literal(text).withStyle(s -> s.withColor(TazTheme.GREY & 0xFFFFFF));
	}
}
