package com.taz.fishing.lotus;

import com.taz.fishing.trophy.TrophyBook;
import com.taz.fishing.trophy.TrophyTableHud;
import com.taz.hud.HudPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** Your Trophy Frogs on the Lotus Atoll as a table (see {@link TrophyTableHud}), with this session's frogs and Lily-splosions under it. */
public final class FrogTrackerHud extends TrophyTableHud {
	private static ItemStack plain;

	public FrogTrackerHud() {
		super("Trophy Frogs", 0.01, 0.20);
	}

	@Override
	protected HudPos pos() {
		return LotusConfig.get().trackerHud;
	}

	@Override
	public boolean enabled() {
		LotusConfig cfg = LotusConfig.get();
		return cfg.enabled && cfg.tracker;
	}

	@Override
	protected TrophyBook book() {
		return TrophyFrogs.BOOK;
	}

	@Override
	protected boolean here() {
		return LotusAtoll.here();
	}

	@Override
	protected boolean onlyMissing() {
		return LotusConfig.get().onlyMissing;
	}

	@Override
	protected boolean pityColumn() {
		return LotusConfig.get().pity;
	}

	@Override
	protected List<String> footer() {
		return List.of("§7Session: §f" + TrophyFrogs.sessionFrogs() + " §7frogs · §f" + TrophyFrogs.sessionLotus() + " §7Lotus",
				"§7Lily-splosions: §f" + TrophyFrogs.splosions() + " §7· §f" + TrophyFrogs.splosionLotus() + " §7Lotus");
	}

	@Override
	protected ItemStack plainIcon() {
		if (plain == null) plain = new ItemStack(Items.LILY_PAD);
		return plain;
	}
}
