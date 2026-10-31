package com.taz.general.pv;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.taz.Taz;
import com.taz.util.TazChat;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * /pv [player] opens a player viewer like NEU's, and /nw [player] says a networth, both with Hypixel's data (through Taz's
 * server) and SkyCofl's prices. They're commands, not features, so they're always there. Registered after other mods, so
 * Taz's /pv is the one that runs.
 */
public final class PlayerViewer {
	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

	/** One profile: its stats (player, skills, profiles), combined data (gear, pets, dungeons, slayers), inventories and networth. */
	public record Profile(ProfileData data, CompletableFuture<JsonObject> worth) {
		public JsonObject stats() {
			return data.stats;
		}

		public JsonObject combined() {
			return data.combined;
		}

		public JsonArray inventories() {
			return data.inventories;
		}

		/** The networth once it's worked out, else null. */
		public JsonObject networth() {
			return worth.isDone() && !worth.isCompletedExceptionally() ? worth.join() : null;
		}

		/** The networth couldn't be worked out. */
		public boolean networthFailed() {
			return worth.isCompletedExceptionally();
		}

		public String uuid() {
			return data.uuid;
		}

		public String id() {
			return data.profileId;
		}

		public String name() {
			return data.name;
		}
	}

	private PlayerViewer() {
	}

	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		// "username", like Skyblocker's /pv, so the two merge into one command and Taz's runs.
		dispatcher.register(literal("pv")
				.executes(ctx -> open(null))
				.then(argument("username", StringArgumentType.word()).suggests(PlayerViewer::names)
						.executes(ctx -> open(StringArgumentType.getString(ctx, "username")))));
		dispatcher.register(literal("nw")
				.executes(ctx -> networth(null))
				.then(argument("username", StringArgumentType.word()).suggests(PlayerViewer::names)
						.executes(ctx -> networth(StringArgumentType.getString(ctx, "username")))));
	}

	private static int open(String player) {
		String name = name(player);
		if (name != null) open(name, null, -1);
		return 1;
	}

	private static int networth(String player) {
		String name = name(player);
		if (name != null) Networth.show(name);
		return 1;
	}

	/** Loads a profile (the player's selected one when profileId is null) and opens the viewer on this tab (-1: the last one). */
	static void open(String player, String profileId, int tab) {
		Hypixel.skyblock(player).thenApplyAsync(answer -> ProfileData.of(answer, player, profileId)).whenCompleteAsync((data, error) -> {
			if (error != null) Hypixel.explain(error, player);
			else Taz.openScreenNextTick(() -> new PlayerViewerScreen(new Profile(data, Networth.of(data)), tab));
		}, Minecraft.getInstance());
	}

	/** The name to look up: the one typed, or yours. Null (and a message) when it can't be a Minecraft name. */
	private static String name(String typed) {
		String name = typed == null || typed.isBlank() ? Minecraft.getInstance().getUser().getName() : typed.trim();
		if (NAME.matcher(name).matches()) return name;
		TazChat.send(Component.literal("\"" + name + "\" isn't a Minecraft name.").withStyle(s -> s.withColor(0xFF6B6B)));
		return null;
	}

	/** Suggests the players in the tab list. */
	private static CompletableFuture<Suggestions> names(CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder builder) {
		var connection = Minecraft.getInstance().getConnection();
		if (connection == null) return builder.buildFuture();
		return SharedSuggestionProvider.suggest(connection.getOnlinePlayers().stream().map(PlayerInfo::getProfile)
				.map(p -> p.name()).filter(n -> n.length() >= 3 && NAME.matcher(n).matches()), builder);
	}
}
