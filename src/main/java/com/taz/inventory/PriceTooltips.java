package com.taz.inventory;

import com.taz.xpassist.data.Prices;
import com.taz.xpassist.util.TextUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Price lines at the bottom of item tooltips (Inventory > Item Prices). */
public final class PriceTooltips {
	private PriceTooltips() {
	}

	/** ItemTooltipCallback. */
	public static void onTooltip(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, List<Component> lines) {
		PriceTooltipConfig cfg = PriceTooltipConfig.get();
		if (!cfg.enabled || cfg.onlyShift && !Minecraft.getInstance().hasShiftDown()) return;
		String key = ItemPrices.key(stack);
		if (key == null) return;
		Prices prices = ItemPrices.get();
		if (prices == null) return;
		int count = stack.getCount();
		boolean stackLine = cfg.stack && count > 1;
		if (cfg.bazaar && prices.isBazaar(key)) {
			double buy = prices.bazaar(key, false), sell = prices.bazaar(key, true);
			lines.add(Component.literal("§7Bazaar buy: §6" + TextUtil.coins(buy) + " §7sell: §6" + TextUtil.coins(sell)
					+ (stackLine ? " §8(x" + count + ": §6" + TextUtil.coins(buy * count) + "§8/§6" + TextUtil.coins(sell * count) + "§8)" : "")));
		} else if (cfg.lowestBin) {
			double bin = prices.market(key);
			if (Double.isNaN(bin) && key.startsWith("LVL_")) return;
			if (!Double.isNaN(bin)) {
				lines.add(Component.literal("§7Lowest BIN: §6" + TextUtil.coins(bin)
						+ (stackLine ? " §8(x" + count + ": §6" + TextUtil.coins(bin * count) + "§8)" : "")));
			}
		}
	}
}
