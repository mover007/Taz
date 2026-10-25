package com.taz.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.function.Supplier;

/** A big on-screen title with a sound, for the moments you shouldn't miss (rare drops, your boss, a bite). */
public final class Alert {
	/** The sound picked in a feature's settings. */
	public enum Sound {
		PLING("Pling", () -> SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f),
		HIGH_PLING("High pling", () -> SoundEvents.NOTE_BLOCK_PLING.value(), 2.0f),
		BELL("Bell", () -> SoundEvents.NOTE_BLOCK_BELL.value(), 1.0f),
		CHIME("Chime", () -> SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0f),
		LEVEL_UP("Level up", () -> SoundEvents.PLAYER_LEVELUP, 1.0f),
		ORB("XP orb", () -> SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f),
		ANVIL("Anvil", () -> SoundEvents.ANVIL_LAND, 1.0f),
		NONE("No sound", null, 1.0f);

		private final String label;
		private final Supplier<SoundEvent> event;
		private final float pitch;

		Sound(String label, Supplier<SoundEvent> event, float pitch) {
			this.label = label;
			this.event = event;
			this.pitch = pitch;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	/** Test hooks: the last title and subtitle shown. */
	public static volatile String lastTitle;
	public static volatile String lastSubtitle;

	private Alert() {
	}

	/** Title (and optional subtitle) for 2 seconds, plus the sound. Safe from any thread. */
	public static void show(String title, String subtitle, Sound sound) {
		lastTitle = title;
		lastSubtitle = subtitle;
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> {
			mc.gui.setTimes(3, 40, 10);
			mc.gui.setSubtitle(Component.literal(subtitle == null ? "" : subtitle));
			mc.gui.setTitle(Component.literal(title));
			play(sound);
		});
	}

	public static void play(Sound sound) {
		if (sound == null || sound.event == null) return;
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> mc.getSoundManager().play(SimpleSoundInstance.forUI(sound.event.get(), sound.pitch, 1.0f)));
	}
}
