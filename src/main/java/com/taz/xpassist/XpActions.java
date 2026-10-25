package com.taz.xpassist;

import com.taz.util.TazChat;
import com.taz.xpassist.config.ModConfig;
import com.taz.xpassist.data.DataManager;
import com.taz.xpassist.data.NeuRepo;
import com.taz.xpassist.npc.Npc;
import com.taz.xpassist.npc.NpcTracker;
import com.taz.xpassist.progress.LocalProgress;
import com.taz.xpassist.progress.ProgressDetector;
import com.taz.xpassist.screen.XpListScreen;
import com.taz.xpassist.util.Chat;
import com.taz.xpassist.util.ShopButtons;
import com.taz.xpassist.util.TextUtil;
import com.taz.xpassist.xp.XpResult;
import com.taz.xpassist.xp.XpTask;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** What the SkyBlock XP buttons in the settings GUI and the chat buttons ([Track], [Bazaar], ...) do. */
public final class XpActions {
	private XpActions() {
	}

	public static void openList() {
		SkyblockXpAssist.openScreen(XpListScreen::new);
	}

	/** The next cheapest upgrades in chat, with [Track] and buy buttons. */
	public static void next(int count) {
		XpResult result = DataManager.result();
		if (result == null) {
			Chat.info("Still loading: " + DataManager.status());
			return;
		}
		int shown = 0;
		for (XpResult.Entry entry : result.entries()) {
			if (shown >= count) {
				break;
			}
			if (entry instanceof XpResult.TaskEntry taskEntry) {
				XpTask task = taskEntry.task();
				MutableComponent line = Component.literal((shown + 1) + ". ").withStyle(ChatFormatting.GRAY)
					.append(Component.literal(task.fullLabel() + ": ").withStyle(ChatFormatting.WHITE))
					.append(Component.literal(task.owned ? "FREE" : TextUtil.coins(task.cost)).withStyle(task.owned ? ChatFormatting.GREEN : ChatFormatting.GOLD))
					.append(Component.literal(" (" + TextUtil.xp(task.xp) + " XP) ").withStyle(ChatFormatting.DARK_GRAY));
				if (task.npcId != null) {
					line.append(Chat.button("Track", "/taz track " + task.npcId, "Highlight the NPC for this"));
				}
				if (!task.owned) {
					ShopButtons.append(line, task.buys);
				}
				Chat.send(line);
				shown++;
			} else if (entry instanceof XpResult.LevelEntry level && result.progressKnown()) {
				Chat.send(Component.literal("SkyBlock Level " + level.level() + ": ").withStyle(ChatFormatting.GREEN)
					.append(Component.literal(TextUtil.coins(level.cumulativeCost(), 2)).withStyle(ChatFormatting.GOLD)));
			}
		}
		if (shown == 0) {
			Chat.info("Nothing to show. Check the list for hidden categories.");
		}
	}

	/** Re-downloads prices, your profile and the item repo. */
	public static void refresh() {
		Chat.info("Refreshing data...");
		DataManager.refresh(true, error -> {
			if (error != null) {
				Chat.error(error);
			} else {
				MutableComponent done = Component.literal("Data refreshed. ").withStyle(ChatFormatting.GREEN);
				if (DataManager.profile() != null) {
					done.append(Component.literal("Profile " + DataManager.profile().profileName + ". ").withStyle(ChatFormatting.GREEN));
				}
				Chat.send(done.append(Chat.button("Open list", "/taz list", "Open the cheapest XP list")));
			}
		});
	}

	/** Which in-game menus to open once to catch up on progress made before Taz was installed. */
	public static void sync() {
		Chat.send(Component.literal("Syncing what you've already done").withStyle(ChatFormatting.GOLD));
		Chat.info("The list updates by itself when you earn SkyBlock XP. To catch up on older progress, open these once:");
		Chat.send(Component.literal("  1. ").withStyle(ChatFormatting.GRAY)
			.append(Chat.button("SkyBlock Menu", "/sbmenu", "Reads your SkyBlock level"))
			.append(Component.literal(" (your level)").withStyle(ChatFormatting.GRAY)));
		Chat.send(Component.literal("  2. ").withStyle(ChatFormatting.GRAY)
			.append(Chat.button("SkyBlock Leveling", "/sblevels", "Open the leveling menu"))
			.append(Component.literal(" > Tasks > Miscellaneous > Abiphone Contacts and Consumable Items").withStyle(ChatFormatting.GRAY)));
		Chat.info("  3. Your Abiphone (right-click it): reads all your contacts");
		Chat.info("  4. Crafted Minions (from the minion task or the Crafted Minions menu)");
		Chat.info("  5. Museum donations are picked up when you click Confirm Donation.");
		Chat.info("Accessories and pets need an API key (/taz > SkyBlock XP > Profile). Right-click a row in the list to hide anything else you've done.");
	}

	public static void dumpNextMenu() {
		ProgressDetector.dumpNextMenu();
		Chat.info("Open the menu you want to save; it will be written to config/taz/xpassist/dumps.");
	}

	public static void forgetProgress() {
		if (!TazChat.confirm("forget-progress", "clear the progress seen in game for profile " + LocalProgress.activeProfile())) {
			return;
		}
		LocalProgress.forgetCurrent();
		DataManager.recompute();
		Chat.info("Cleared the progress seen in game for profile " + LocalProgress.activeProfile() + ".");
	}

	public static void unhideAll() {
		synchronized (ModConfig.get()) {
			ModConfig.get().hiddenTasks.clear();
		}
		ModConfig.save();
		DataManager.recompute();
		Chat.info("All hidden upgrades are visible again.");
	}

	/** From the [Track] chat buttons: highlight an NPC by its repo id. */
	public static void track(String npcId) {
		NeuRepo neu = DataManager.neu();
		Npc npc = neu != null ? neu.npcs.get(npcId) : null;
		if (npc == null) {
			Chat.error("Unknown NPC " + npcId);
			return;
		}
		NpcTracker.track(npc);
	}

	/** From the [Bazaar] / [AH] / [Recipe] chat buttons: run the Hypixel command (bz, ahs, viewrecipe). */
	public static void server(String command) {
		ShopButtons.sendServerCommand(command);
	}
}
