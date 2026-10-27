package com.taz.dungeons.info;

import com.taz.dungeons.util.DungeonLocation;
import com.taz.hud.HudPos;
import com.taz.hud.TextHud;
import com.taz.util.Alert;
import com.taz.util.Items;
import com.taz.util.TazChat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mask Timers: the Spirit Mask, Bonzo Mask and Phoenix pet that save you from dying. From Odin's Invincibility Timer;
 * timed in server ticks.
 */
public final class MaskTimers extends TextHud {
	public enum Mask {
		SPIRIT("Spirit", "^Second Wind Activated! Your Spirit Mask saved your life!$", 60, 30, "SPIRIT_MASK"),
		BONZO("Bonzo", "^Your (?:. )?Bonzo's Mask saved your life!$", 60, 180, "BONZO_MASK"),
		PHOENIX("Phoenix", "^Your Phoenix Pet saved you from certain death!$", 80, 60, null);

		final String label;
		final Pattern pattern;
		final int invincibleTicks, cooldownSeconds;
		/** Its SkyBlock id (and STARRED_ + it), for the cooldown drawn on the item. */
		final String itemId;
		int active, cooldown;

		Mask(String label, String regex, int invincibleTicks, int cooldownSeconds, String itemId) {
			this.label = label;
			this.pattern = Pattern.compile(regex);
			this.invincibleTicks = invincibleTicks;
			this.cooldownSeconds = cooldownSeconds;
			this.itemId = itemId;
		}

		boolean matches(String id) {
			return itemId != null && (id.equals(itemId) || id.equals("STARRED_" + itemId));
		}
	}

	private static final Pattern COOLDOWN = Pattern.compile("^Cooldown: (\\d+)s$");
	private static ClientLevel level;

	public MaskTimers() {
		super("Mask Timers", 0.40, 0.70);
	}

	/** A chat line: a mask (or the Phoenix) just saved you. */
	public static void onChat(String line) {
		MaskTimersConfig cfg = MaskTimersConfig.get();
		if (!cfg.enabled || (cfg.onlyDungeons && !DungeonLocation.inDungeon())) return;
		for (Mask mask : Mask.values()) {
			if (!mask.pattern.matcher(line).matches()) continue;
			int seconds = mask.cooldownSeconds;
			if (mask == Mask.BONZO) seconds = bonzoCooldown(seconds);
			mask.active = mask.invincibleTicks;
			mask.cooldown = seconds * 20;
			int used = 0;
			for (Mask m : Mask.values()) if (m.cooldown > 0) used++;
			if (cfg.announce) TazChat.party(mask.label + " Procced! (" + used + "/" + Mask.values().length + ")");
			if (cfg.alert) Alert.show("§c" + mask.label, "§fsaved you", Alert.Sound.ANVIL);
			return;
		}
	}

	/** The Bonzo Mask's cooldown from its lore ("Cooldown: 156s", lower with a better mask). */
	private static int bonzoCooldown(int fallback) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return fallback;
		List<String> lore = Items.lore(mc.player.getItemBySlot(EquipmentSlot.HEAD));
		for (int i = lore.size() - 1; i >= 0; i--) {
			Matcher m = COOLDOWN.matcher(lore.get(i));
			if (m.matches()) return Integer.parseInt(m.group(1));
		}
		return fallback;
	}

	/** Every server tick. */
	public static void onServerTick() {
		for (Mask m : Mask.values()) {
			if (m.active > 0) m.active--;
			if (m.cooldown > 0) m.cooldown--;
		}
	}

	/** Every client tick: a new world forgets the timers. */
	public static void tick(Minecraft mc) {
		if (mc.level != level) {
			level = mc.level;
			for (Mask m : Mask.values()) m.active = m.cooldown = 0;
		}
	}

	/** After a slot is drawn: the cooldown over a Spirit or Bonzo Mask, filling down as it runs out. */
	public static void slot(GuiGraphicsExtractor g, Slot slot) {
		MaskTimersConfig cfg = MaskTimersConfig.get();
		if (!cfg.enabled || !cfg.onItem || !slot.hasItem()) return;
		String id = Items.id(slot.getItem());
		for (Mask m : Mask.values()) {
			if (!m.matches(id) || m.cooldown <= 0) continue;
			double left = m.cooldown / (double) (m.cooldownSeconds * 20);
			int top = slot.y + (int) Math.round((1 - Math.min(1, left)) * 16);
			g.fill(slot.x, top, slot.x + 16, slot.y + 16, 0x99800000);
		}
	}

	/** Test hook: ticks of cooldown left. */
	public static int cooldown(Mask mask) {
		return mask.cooldown;
	}

	@Override
	protected HudPos pos() {
		return MaskTimersConfig.get().hud;
	}

	@Override
	public boolean enabled() {
		return MaskTimersConfig.get().enabled;
	}

	@Override
	protected List<String> lines(boolean preview) {
		MaskTimersConfig cfg = MaskTimersConfig.get();
		List<String> lines = new ArrayList<>();
		if (!preview && (cfg.onlyDungeons && !DungeonLocation.inDungeon() || cfg.onlyBoss && !DungeonLocation.inBoss())) return lines;
		Minecraft mc = Minecraft.getInstance();
		String head = mc.player == null ? "" : Items.id(mc.player.getItemBySlot(EquipmentSlot.HEAD));
		for (Mask m : Mask.values()) {
			boolean show = switch (m) {
				case SPIRIT -> cfg.spirit;
				case BONZO -> cfg.bonzo;
				case PHOENIX -> cfg.phoenix;
			};
			if (!show) continue;
			String state;
			if (preview) state = m == Mask.BONZO ? "§c142.5s" : m == Mask.SPIRIT ? "§e2.1s" : "§a✔";
			else if (m.active > 0) state = "§e" + String.format("%.1fs", m.active / 20.0);
			else if (m.cooldown > 0) state = "§c" + String.format("%.1fs", m.cooldown / 20.0);
			else state = "§a✔";
			lines.add("§c" + m.label + (m.matches(head) ? "§4 ●" : "") + " " + state);
		}
		return lines;
	}
}
