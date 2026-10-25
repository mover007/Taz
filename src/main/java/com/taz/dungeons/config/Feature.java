package com.taz.dungeons.config;

/**
 * Every dungeon on/off setting, read with ModConfig.on(f). The names and descriptions shown in
 * the settings GUI are on ModConfig's fields; this holds each one's "Simple" preset value.
 */
public enum Feature {
	// ---- Timer HUD
	HUD_TITLE(true),
	HUD_ELAPSED(true),
	HUD_PORTAL_END(true),
	HUD_LIVE_ETA(false),
	HUD_ETA_RANGE(false),
	HUD_BEST_POSSIBLE(false),
	HUD_PHASE(false),
	HUD_DEATHS(false),
	HUD_PACE(false),
	HUD_PB(false),
	HUD_RESULT(true),
	HUD_LAG(true),
	HUD_LEARNING(false),

	// ---- Score panel lines (the panel itself is toggled separately)
	SCORE_LIVE(true),
	SCORE_PROJECTED(true),
	SCORE_SECRETS(true),
	SCORE_CRYPTS(true),
	SCORE_PUZZLES(true),
	SCORE_DEATHS(true),
	SCORE_BONUS(true),
	SCORE_TIME_LIMIT(true),
	PAUL(false),

	// ---- Chat
	CHAT_PORTAL(true),
	CHAT_PORTAL_BEST(false),
	CHAT_PORTAL_PB(false),
	CHAT_CORE(true),
	CHAT_PARTY_CORE(false),
	CHAT_PARTY_END(false),
	CHAT_PARTY_BEST(false),
	CHAT_SUMMARY(true),
	CHAT_SUMMARY_ESTIMATE(true),
	CHAT_SUMMARY_BEST(false),
	CHAT_SUMMARY_SCORE(false),
	CHAT_GOLD_SPLITS(false),
	CHAT_PB(true),
	CHAT_MISSED(false),
	CHAT_DEBUG(false),

	// ---- Other
	LAG_ADJUST(true),
	LOG_IMPORT(true),
	BOSS_LINE_LOG(true),
	DIAGNOSTICS(true);

	public final boolean simpleDefault;

	Feature(boolean simpleDefault) {
		this.simpleDefault = simpleDefault;
	}
}
