package com.taz.general;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.taz.Taz;
import com.taz.dungeons.util.DungeonLocation;
import com.taz.dungeons.util.TextUtil;
import com.taz.hud.HudPos;
import com.taz.hud.TextHud;
import com.taz.util.Alert;
import com.taz.util.Items;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pet Display: your active pet and its held item, from "You summoned your ...", Autopet's "equipped your [Lvl 100]
 * ...", level-ups and the Pets menu (the pet whose lore says "Click to despawn!"); kept across restarts. From
 * NoammAddons.
 */
public final class PetDisplay extends TextHud {
	private static final Pattern SUMMON = Pattern.compile("^§aYou (summoned|despawned) your (.+?)(?: ✦)?§a!$");
	private static final Pattern AUTOPET = Pattern.compile("^§cAutopet §eequipped your §7\\[Lvl (\\d+)] (.+?)§e! §a§lVIEW RULE$");
	private static final Pattern LEVEL_UP = Pattern.compile("^Your (.+?) leveled up to level (\\d+)!$");
	private static final Pattern MENU_PET = Pattern.compile("^\\[Lvl (\\d+)] (.+)$");
	private static final Pattern PETS_MENU = Pattern.compile("^(?:\\(\\d+/\\d+\\) )?Pets(?: \\(\\d+/\\d+\\))?$");
	private static final Path FILE = Taz.CONFIG_DIR.resolve("pet.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** The pet: its name with its rarity colour code ("§6Golden Dragon"), level (0 unknown), held item. */
	static final class Pet {
		String name;
		int level;
		String item;
	}

	private static Pet pet;
	private static boolean loaded;
	private static int ticks;

	public PetDisplay() {
		super("Pet", 0.01, 0.20);
	}

	private static Pet pet() {
		if (!loaded) {
			loaded = true;
			try {
				if (Files.exists(FILE)) pet = GSON.fromJson(Files.readString(FILE), Pet.class);
			} catch (Exception e) {
				Taz.LOGGER.error("Couldn't read your pet", e);
			}
		}
		return pet;
	}

	private static void set(Pet p) {
		pet = p;
		loaded = true;
		try {
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, GSON.toJson(p));
		} catch (Exception e) {
			Taz.LOGGER.error("Couldn't save your pet", e);
		}
	}

	/** A chat line, with its colour codes (raw) and without (line). */
	public static void onChat(String raw, String line) {
		Matcher m = SUMMON.matcher(raw);
		if (m.matches()) {
			if (m.group(1).equals("despawned")) {
				set(null);
			} else {
				Pet p = new Pet();
				p.name = m.group(2);
				Pet old = pet();
				if (old != null && TextUtil.cleanChat(old.name).equals(TextUtil.cleanChat(p.name))) {
					p.level = old.level;
					p.item = old.item;
				}
				set(p);
			}
			return;
		}
		m = AUTOPET.matcher(raw);
		if (m.matches()) {
			Pet p = new Pet();
			p.level = Integer.parseInt(m.group(1));
			p.name = m.group(2).replace(" ✦", "");
			set(p);
			PetDisplayConfig cfg = PetDisplayConfig.get();
			if (cfg.enabled && cfg.autopetTitle && (!cfg.titleDungeonsOnly || DungeonLocation.inDungeon())) Alert.show(p.name, null, Alert.Sound.NONE);
			return;
		}
		m = LEVEL_UP.matcher(line);
		Pet p = pet();
		if (m.matches() && p != null && TextUtil.cleanChat(p.name).equals(m.group(1))) {
			p.level = Integer.parseInt(m.group(2));
			set(p);
		}
	}

	/** Every client tick: in the Pets menu, the pet you have out. */
	public static void tick(Minecraft mc) {
		if (!PetDisplayConfig.get().enabled || ++ticks % 10 != 0) return;
		if (!(mc.screen instanceof AbstractContainerScreen<?> screen) || !PETS_MENU.matcher(TextUtil.cleanChat(screen.getTitle().getString())).matches()) return;
		for (Slot slot : screen.getMenu().slots) {
			ItemStack stack = slot.getItem();
			List<String> lore = Items.lore(stack);
			if (lore.stream().noneMatch(l -> l.contains("Click to despawn!"))) continue;
			String raw = stack.getHoverName().getString();
			Matcher m = MENU_PET.matcher(TextUtil.cleanChat(raw));
			if (!m.matches()) return;
			Pet p = new Pet();
			p.level = Integer.parseInt(m.group(1));
			int at = raw.indexOf("] ");
			p.name = at >= 0 ? raw.substring(at + 2) : m.group(2);
			for (String l : lore) if (l.startsWith("Held Item: ")) p.item = l.substring("Held Item: ".length());
			set(p);
			return;
		}
	}

	@Override
	protected HudPos pos() {
		return PetDisplayConfig.get().hud;
	}

	@Override
	public boolean enabled() {
		return PetDisplayConfig.get().enabled;
	}

	@Override
	protected List<String> lines(boolean preview) {
		List<String> lines = new ArrayList<>();
		Pet p = preview ? null : pet();
		if (preview) {
			lines.add("§7[Lvl 200] §6Golden Dragon");
			lines.add("§7Item: §fMinos Relic");
			return lines;
		}
		if (p == null) return lines;
		lines.add((p.level > 0 ? "§7[Lvl " + p.level + "] " : "") + p.name);
		if (p.item != null && PetDisplayConfig.get().showItem) lines.add("§7Item: §f" + p.item);
		return lines;
	}
}
