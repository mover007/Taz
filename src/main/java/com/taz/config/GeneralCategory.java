package com.taz.config;

import com.google.gson.annotations.Expose;
import com.taz.general.AlertsConfig;
import com.taz.general.DarkAuctionConfig;
import com.taz.general.EventTimersConfig;
import com.taz.general.KickTimerConfig;
import com.taz.general.PetDisplayConfig;
import com.taz.general.RagnarockConfig;
import com.taz.general.SpringBootsConfig;
import com.taz.general.SkillTrackerConfig;
import io.github.notenoughupdates.moulconfig.annotations.Accordion;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;

/** The "General" category: features for all of SkyBlock, one dropdown each. */
public final class GeneralCategory {
	@Expose
	@ConfigOption(name = "Alerts", desc = "Big titles and a sound for rare drops, filled bazaar orders, sold auctions, party invites and low health.")
	@Accordion
	public AlertsConfig alerts = new AlertsConfig();

	@Expose
	@ConfigOption(name = "Dark Auction Reminder", desc = "Tells you a few minutes before every Dark Auction (:55 every hour), with a button to warp there.")
	@Accordion
	public DarkAuctionConfig darkAuction = new DarkAuctionConfig();

	@Expose
	@ConfigOption(name = "Event Timers", desc = "A HUD counting down to the Dark Auction, Jacob's Contest, Cult of the Fallen Star and other events, with reminders.")
	@Accordion
	public EventTimersConfig eventTimers = new EventTimersConfig();

	@Expose
	@ConfigOption(name = "Skill Tracker", desc = "Skill XP per hour and time to your next level, counted only while you're training.")
	@Accordion
	public SkillTrackerConfig skillTracker = new SkillTrackerConfig();

	@Expose
	@ConfigOption(name = "Pet Display", desc = "Your active pet, its level and held item, and a title when Autopet swaps it.")
	@Accordion
	public PetDisplayConfig pets = new PetDisplayConfig();

	@Expose
	@ConfigOption(name = "Ragnarock", desc = "Alerts when you cast the Ragnarock Axe or it's cancelled, and the Strength it gave you.")
	@Accordion
	public RagnarockConfig ragnarock = new RagnarockConfig();

	@Expose
	@ConfigOption(name = "Spring Boots", desc = "How high your Spring Boots will throw you as you charge them.")
	@Accordion
	public SpringBootsConfig springBoots = new SpringBootsConfig();

	@Expose
	@ConfigOption(name = "Kick Timer", desc = "How long ago Hypixel kicked you from SkyBlock, so you know when you can rejoin.")
	@Accordion
	public KickTimerConfig kickTimer = new KickTimerConfig();
}
