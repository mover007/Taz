package com.taz.inventory;

import com.google.gson.JsonObject;
import com.taz.Taz;
import com.taz.xpassist.config.ModConfig;
import com.taz.xpassist.data.DataManager;
import com.taz.xpassist.data.Prices;
import com.taz.xpassist.util.Http;
import com.taz.xpassist.util.ItemUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Item prices for tooltips: the ones SkyBlock XP Assist already downloaded when it's on, or else the same two
 * files (SkyHelper lowest-BIN prices and Hypixel's bazaar), downloaded in the background every 15 minutes
 * into config/taz/prices/.
 */
public final class ItemPrices {
	private static final long REFRESH_MS = 15 * 60_000L;
	private static final Pattern PET_LEVEL = Pattern.compile("^\\[Lvl (\\d+)]");

	private static volatile Prices own;
	private static volatile boolean loading;
	private static long lastAttempt;
	/** Test hook: prices to use instead of downloading. */
	public static Prices testPrices;

	private ItemPrices() {
	}

	/** Current prices, or null while the first download is still running. */
	public static Prices get() {
		if (testPrices != null) return testPrices;
		Prices shared = DataManager.prices();
		if (shared != null && System.currentTimeMillis() - shared.loadedAt < 2 * REFRESH_MS) return shared;
		if (!loading && System.currentTimeMillis() - lastAttempt > REFRESH_MS) refresh();
		return own != null ? own : shared;
	}

	private static void refresh() {
		loading = true;
		lastAttempt = System.currentTimeMillis();
		Thread t = new Thread(() -> {
			try {
				Path dir = Taz.CONFIG_DIR.resolve("prices");
				Files.createDirectories(dir);
				Path market = dir.resolve("prices.json"), bazaar = dir.resolve("bazaar.json");
				ModConfig.DataSources sources = ModConfig.get().dataSources;
				try {
					Http.download(sources.pricesUrl, market);
				} catch (Exception e) {
					Taz.LOGGER.warn("Item prices: could not download lowest BIN prices ({})", e.toString());
				}
				try {
					Http.download(sources.hypixelApiBase + "/skyblock/bazaar", bazaar);
				} catch (Exception e) {
					Taz.LOGGER.warn("Item prices: could not download bazaar prices ({})", e.toString());
				}
				own = Prices.parse(Files.exists(market) ? Files.readString(market, StandardCharsets.UTF_8) : null,
						Files.exists(bazaar) ? Files.readString(bazaar, StandardCharsets.UTF_8) : null);
			} catch (Exception e) {
				Taz.LOGGER.warn("Item prices: could not load prices", e);
			} finally {
				loading = false;
			}
		}, "Taz item prices");
		t.setDaemon(true);
		t.start();
	}

	/**
	 * The price-file key of an item: its SkyBlock id, "ENCHANTMENT_ULTIMATE_WISE_5" for a one-enchant book,
	 * "LVL_100_LEGENDARY_GOLDEN_DRAGON" for a pet. Null when it isn't a SkyBlock item.
	 */
	public static String key(ItemStack stack) {
		String id = ItemUtil.skyblockId(stack);
		if (id.isEmpty()) return null;
		if (id.equals("ENCHANTED_BOOK")) {
			CustomData data = stack.get(DataComponents.CUSTOM_DATA);
			CompoundTag enchants = data == null ? null : data.copyTag().getCompoundOrEmpty("enchantments");
			if (enchants != null && enchants.size() == 1) {
				String name = enchants.keySet().iterator().next();
				return "ENCHANTMENT_" + name.toUpperCase(Locale.ROOT) + "_" + enchants.getIntOr(name, 0);
			}
			return id;
		}
		if (id.equals("PET")) {
			JsonObject pet = ItemUtil.petInfo(stack);
			if (pet == null || !pet.has("type") || !pet.has("tier")) return id;
			Matcher m = PET_LEVEL.matcher(ItemUtil.name(stack));
			String level = m.find() ? m.group(1) : "1";
			return "LVL_" + level + "_" + pet.get("tier").getAsString() + "_" + pet.get("type").getAsString();
		}
		return id;
	}
}
