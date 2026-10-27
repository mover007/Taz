package com.taz.foraging.safari;

import com.taz.Taz;
import com.taz.dungeons.util.TextUtil;
import com.taz.util.Items;
import com.taz.util.TazChat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads your Critterdex (the menu "Critterdex") so Taz knows which critters you've really caught, not only the ones it
 * saw you catch. The wiki: a critter you've caught shows as itself, one you haven't as "???". Every critter shown by
 * name is added to your catches; none are taken away, as a scrolled row may hide some. What the menu held is written to
 * the game log the first time, so its layout can be checked.
 */
public final class CritterdexSync {
	/** Lore that says a critter shown by name isn't caught after all. */
	private static final Pattern NOT_CAUGHT = Pattern.compile("(?i)not (?:yet )?caught|haven't caught|undiscovered|locked|caught: 0\\b");

	private static Screen open;
	private static final Set<String> ADDED = new LinkedHashSet<>();
	private static int ticks, reads;
	private static boolean logged;

	private CritterdexSync() {
	}

	/** Every client tick: in the Critterdex, the critters it shows as caught; when it closes, what that added. */
	public static void tick(Minecraft mc) {
		if (++ticks % 10 != 0) return;
		Screen screen = mc.screen;
		if (screen != open) {
			if (open != null && !ADDED.isEmpty() && SafariConfig.get().tips) {
				TazChat.info("Critterdex synced: " + String.join(", ", ADDED) + " now count" + (ADDED.size() == 1 ? "s" : "") + " as caught ("
						+ SafariConfig.get().caught.size() + "/" + Critters.ALL.size() + ").");
			}
			ADDED.clear();
			open = null;
			reads = 0;
		}
		if (!(screen instanceof AbstractContainerScreen<?> menu) || !isCritterdex(menu)) return;
		open = screen;
		read(menu);
	}

	static boolean isCritterdex(AbstractContainerScreen<?> menu) {
		return TextUtil.cleanChat(menu.getTitle().getString()).trim().matches("(?:\\(\\d+/\\d+\\) )?Critterdex(?: \\(\\d+/\\d+\\))?");
	}

	private static void read(AbstractContainerScreen<?> menu) {
		SafariConfig cfg = SafariConfig.get();
		List<String> seen = new ArrayList<>();
		// Logged once the menu has had a moment to fill.
		boolean log = !logged && ++reads == 3;
		for (Slot slot : menu.getMenu().slots) {
			if (slot.container instanceof Inventory) continue;
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) continue;
			String name = SafariHelper.cleanName(stack.getHoverName().getString());
			List<String> lore = Items.lore(stack);
			if (log) seen.add(name + (lore.isEmpty() ? "" : " " + lore.subList(0, Math.min(3, lore.size()))));
			Critters.Critter c = Critters.named(name);
			if (c == null || lore.stream().anyMatch(l -> NOT_CAUGHT.matcher(l).find())) continue;
			if (!cfg.caught.contains(c.name())) {
				cfg.caught.add(c.name());
				ADDED.add(c.name());
			}
		}
		if (log && !seen.isEmpty()) {
			logged = true;
			Taz.LOGGER.info("Safari: Critterdex menu holds {}", seen);
		}
		if (!ADDED.isEmpty()) Taz.saveIfChanged();
	}
}
