package com.taz.fishing.crimson;

import com.taz.fishing.trophy.TrophyBook;
import com.taz.fishing.trophy.TrophyTableHud;
import com.taz.hud.HudPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Your Trophy Fish on the Crimson Isle as a table (see {@link TrophyTableHud}), with this session's fish under it. */
public final class TrophyFishHud extends TrophyTableHud {
	private static ItemStack plain;

	public TrophyFishHud() {
		super("Trophy Fish", 0.01, 0.20);
	}

	@Override
	protected HudPos pos() {
		return TrophyFishConfig.get().trackerHud;
	}

	@Override
	public boolean enabled() {
		TrophyFishConfig cfg = TrophyFishConfig.get();
		return cfg.enabled && cfg.tracker;
	}

	@Override
	protected TrophyBook book() {
		return TrophyFish.BOOK;
	}

	@Override
	protected boolean here() {
		return TrophyFish.here();
	}

	@Override
	protected boolean onlyMissing() {
		return TrophyFishConfig.get().onlyMissing;
	}

	@Override
	protected boolean pityColumn() {
		return TrophyFishConfig.get().pity;
	}

	@Override
	protected List<String> footer() {
		List<String> out = new ArrayList<>();
		out.add("§7Session: §f" + TrophyFish.sessionFish() + " §7fish · §f" + TrophyFish.sessionMagmafish() + " §7Magmafish");
		if (!TrophyFish.BOOK.fromMenu()) out.add("§8Open Odger's Trophy Fish menu to load all your catches");
		return out;
	}

	@Override
	protected ItemStack plainIcon() {
		if (plain == null) plain = new ItemStack(Items.TROPICAL_FISH);
		return plain;
	}
}
