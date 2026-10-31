package com.taz.lowballer;

import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * One stack you got in a trade, for the Lowball Tracker: what you paid, what it was worth, and what it sold for.
 * Unsold is HOLDING (not listed) or LISTED (on the AH); then SOLD, or KEPT when you use it yourself.
 */
public final class Flip {
	public enum Status {
		HOLDING, LISTED, SOLD, KEPT
	}

	public long id;
	/** When the trade went through. */
	public long boughtAt;
	/** Who you traded with. */
	public String from = "";
	public ItemStack stack = ItemStack.EMPTY;
	/** The item as saved, kept when it can't be read back (so saving never loses it). */
	Tag stackTag;
	/** Its name without colours, as Hypixel's chat lines call it. */
	public String name = "";
	/** Hypixel's item uuid, or "" for stackables. */
	public String uuid = "";
	public int count = 1;
	/** Your share of what you gave for the whole trade. */
	public long paid;
	/** SkyCofl's worth at the trade, 0 when unknown. */
	public long worth;
	/** Sales a day then (-1: unknown). */
	public double volume = -1;
	public boolean bazaar;
	/** Its worth the last time the tracker asked, and when. */
	public long worthNow;
	public long worthNowAt;
	/** Items you got in that trade, and all you gave for them. */
	public int tradeItems = 1;
	public long tradePaid;
	public Status status = Status.HOLDING;
	/** The name in "BIN Auction started for ...", and when. */
	public String listedAs = "";
	public long listedAt;
	public boolean bin = true;
	/** Its price on the AH (from SkyCofl's copy of your auctions), 0 until known. */
	public long listPrice;
	/** You typed in what you paid: a late price doesn't split the trade's cost again. */
	public boolean manualPaid;
	/** What it sold for, the tax and fees on that, when, how ("BIN", "Auction", "Bazaar", "NPC", "Trade", "By hand") and to whom. */
	public long sold;
	public long fees;
	public long soldAt;
	public String soldHow = "";
	public String soldTo = "";
	/** You claimed the coins from its auction (so the fees are exact). */
	public boolean collected;

	public boolean open() {
		return status == Status.HOLDING || status == Status.LISTED;
	}

	public long profit() {
		return sold - fees - paid;
	}

	/** Its latest worth: now, or else at the trade. */
	public long worthLatest() {
		return worthNow > 0 ? worthNow : worth;
	}

	/** What it's for sale at: its AH price when listed, else its worth now; 0 when neither is known. */
	public long askingPrice() {
		return status == Status.LISTED && listPrice > 0 ? listPrice : worthLatest();
	}

	/** What selling it at its asking price would make, after AH tax (or bazaar tax). */
	public long expectedProfit() {
		long price = askingPrice();
		return price <= 0 ? 0 : price - Math.round(price * Lowball.tax(price, bazaar && status != Status.LISTED)) - paid;
	}

	/** Its profit if sold, else what it should make. */
	public long profitOrExpected() {
		return status == Status.SOLD ? profit() : expectedProfit();
	}

	/** How far under its worth you got it (0.15: 15% under); NaN when its worth is unknown. */
	public double under() {
		return worth <= 0 ? Double.NaN : 1 - paid / (double) worth;
	}

	public void sell(long price, long fees, String how, String to, long at) {
		status = Status.SOLD;
		sold = price;
		this.fees = fees;
		soldHow = how;
		soldTo = to == null ? "" : to;
		soldAt = at;
	}

	/** Back to unsold. */
	public void unsell() {
		status = listedAt > 0 ? Status.LISTED : Status.HOLDING;
		sold = fees = soldAt = 0;
		soldHow = soldTo = "";
		collected = false;
	}

	/** The first {@code n} of the stack, with their share of what was paid; this keeps the rest. */
	Flip split(int n) {
		Flip part = copy();
		part.count = n;
		part.stack = stack.copyWithCount(n);
		part.stackTag = null;
		part.paid = paid * n / count;
		part.worth = worth * n / count;
		part.worthNow = worthNow * n / count;
		paid -= part.paid;
		worth -= part.worth;
		worthNow -= part.worthNow;
		count -= n;
		stack = stack.copyWithCount(count);
		stackTag = null;
		return part;
	}

	private Flip copy() {
		Flip f = new Flip();
		f.load(save(null));
		f.stack = stack.copy();
		f.stackTag = stackTag;
		return f;
	}

	CompoundTag save(DynamicOps<Tag> ops) {
		CompoundTag t = new CompoundTag();
		t.putLong("id", id);
		t.putLong("boughtAt", boughtAt);
		t.putString("from", from);
		if (ops != null) {
			Tag item = stack.isEmpty() ? stackTag : ItemStack.OPTIONAL_CODEC.encodeStart(ops, stack).result().orElse(stackTag);
			if (item != null) t.put("item", item);
		}
		t.putString("name", name);
		t.putString("uuid", uuid);
		t.putInt("count", count);
		t.putLong("paid", paid);
		t.putLong("worth", worth);
		t.putDouble("volume", volume);
		t.putBoolean("bazaar", bazaar);
		t.putLong("worthNow", worthNow);
		t.putLong("worthNowAt", worthNowAt);
		t.putInt("tradeItems", tradeItems);
		t.putLong("tradePaid", tradePaid);
		t.putString("status", status.name());
		t.putString("listedAs", listedAs);
		t.putLong("listedAt", listedAt);
		t.putLong("listPrice", listPrice);
		t.putBoolean("manualPaid", manualPaid);
		t.putBoolean("bin", bin);
		t.putLong("sold", sold);
		t.putLong("fees", fees);
		t.putLong("soldAt", soldAt);
		t.putString("soldHow", soldHow);
		t.putString("soldTo", soldTo);
		t.putBoolean("collected", collected);
		return t;
	}

	void load(CompoundTag t) {
		id = t.getLongOr("id", 0);
		boughtAt = t.getLongOr("boughtAt", 0);
		from = t.getStringOr("from", "");
		name = t.getStringOr("name", "");
		uuid = t.getStringOr("uuid", "");
		count = Math.max(1, t.getIntOr("count", 1));
		paid = t.getLongOr("paid", 0);
		worth = t.getLongOr("worth", 0);
		volume = t.getDoubleOr("volume", -1);
		bazaar = t.getBooleanOr("bazaar", false);
		worthNow = t.getLongOr("worthNow", 0);
		worthNowAt = t.getLongOr("worthNowAt", 0);
		tradeItems = Math.max(1, t.getIntOr("tradeItems", 1));
		tradePaid = t.getLongOr("tradePaid", paid);
		try {
			status = Status.valueOf(t.getStringOr("status", "HOLDING"));
		} catch (IllegalArgumentException e) {
			status = Status.HOLDING;
		}
		listedAs = t.getStringOr("listedAs", "");
		listedAt = t.getLongOr("listedAt", 0);
		listPrice = t.getLongOr("listPrice", 0);
		manualPaid = t.getBooleanOr("manualPaid", false);
		bin = t.getBooleanOr("bin", true);
		sold = t.getLongOr("sold", 0);
		fees = t.getLongOr("fees", 0);
		soldAt = t.getLongOr("soldAt", 0);
		soldHow = t.getStringOr("soldHow", "");
		soldTo = t.getStringOr("soldTo", "");
		collected = t.getBooleanOr("collected", false);
		stackTag = t.get("item");
	}
}
