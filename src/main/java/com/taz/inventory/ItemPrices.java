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
import java.util.List;
import java.util.Locale;

/**
 * Item prices for tooltips: the ones SkyBlock XP Assist already downloaded when it's on, or else the same two
 * lists (SkyCofl's lowest BINs and Hypixel's bazaar), downloaded in the background every 15 minutes
 * into config/taz/prices/.
 */
public final class ItemPrices {
	private static final long REFRESH_MS = 15 * 60_000L;
	private static final List<String> PET_TIERS = List.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC");

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
	 * The price-list key of an item: its SkyBlock id, "ENCHANTMENT_ULTIMATE_WISE_5" for a one-enchant book (a bazaar
	 * id), "GOLDEN_DRAGON;4" for a legendary pet (SkyCofl's key: the type and the rarity's number, any level).
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
			int tier = PET_TIERS.indexOf(pet.get("tier").getAsString());
			return tier < 0 ? id : pet.get("type").getAsString() + ";" + tier;
		}
		return id;
	}
}
