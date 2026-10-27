package com.taz;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.taz.general.Waypoints;
import com.taz.xpassist.XpActions;
import com.taz.xpassist.npc.NpcTracker;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.core.BlockPos;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/** /taz is Taz's only command: it opens the settings GUI, where every setting and action is. */
public final class TazCommand {
	private TazCommand() {
	}

	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(literal("taz")
				.executes(ctx -> {
					Taz.openSettings(null);
					return 1;
				})
				.then(literal("list").executes(ctx -> {
					XpActions.openList();
					return 1;
				}))
				.then(literal("track").then(argument("npc", StringArgumentType.greedyString()).executes(ctx -> {
					XpActions.track(StringArgumentType.getString(ctx, "npc"));
					return 1;
				})))
				.then(literal("untrack").executes(ctx -> {
					NpcTracker.untrack();
					return 1;
				}))
				.then(literal("bz").then(argument("query", StringArgumentType.greedyString()).executes(ctx -> {
					XpActions.server("bz " + StringArgumentType.getString(ctx, "query"));
					return 1;
				})))
				.then(literal("ah").then(argument("query", StringArgumentType.greedyString()).executes(ctx -> {
					XpActions.server("ahs " + StringArgumentType.getString(ctx, "query"));
					return 1;
				})))
				.then(literal("recipe").then(argument("id", StringArgumentType.word()).executes(ctx -> {
					XpActions.server("viewrecipe " + StringArgumentType.getString(ctx, "id"));
					return 1;
				})))
				.then(literal("warp").then(argument("place", StringArgumentType.word()).executes(ctx -> {
					XpActions.server("warp " + StringArgumentType.getString(ctx, "place"));
					return 1;
				})))
				.then(literal("waypoint")
						.then(literal("clear").executes(ctx -> {
							Waypoints.clear();
							return 1;
						}))
						.then(argument("x", IntegerArgumentType.integer()).then(argument("y", IntegerArgumentType.integer())
								.then(argument("z", IntegerArgumentType.integer())
										.executes(ctx -> waypoint(ctx, "Waypoint"))
										.then(argument("name", StringArgumentType.greedyString())
												.executes(ctx -> waypoint(ctx, StringArgumentType.getString(ctx, "name")))))))));
	}

	private static int waypoint(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> ctx, String name) {
		BlockPos pos = new BlockPos(IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "y"),
				IntegerArgumentType.getInteger(ctx, "z"));
		Waypoints.add(pos, name);
		return 1;
	}
}
