package com.taz.inferno;

import com.google.common.collect.ImmutableMultimap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.taz.Taz;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * What Taz knows about Inferno minions (from the Hypixel SkyBlock wiki and NEU): their fuels, what those make, the rare
 * drops of Hypergolic fuel, each tier's speed, and the item names and icons.
 */
public final class InfernoData {
	/** The gabagool a fuel is made with: its speed and rarity. Hypergolic also makes the rare drops. */
	public enum Grade {
		FUEL("Fuel", 10, "FUEL_GABAGOOL", "§9"), HEAVY("Heavy", 15, "HEAVY_GABAGOOL", "§5"), HYPERGOLIC("Hypergolic", 20, "HYPERGOLIC_GABAGOOL", "§6");

		public final String label;
		public final int speed;
		public final String gabagool;
		public final String color;

		Grade(String label, int speed, String gabagool, String color) {
			this.label = label;
			this.speed = speed;
			this.gabagool = gabagool;
			this.color = color;
		}
	}

	/** The distillate a fuel is made with: the item it makes 4 times in 5, and how many each time. */
	public enum Kind {
		CRUDE_GABAGOOL("Gabagool", 1), BLAZE_ROD("Blaze Rod", 1), MAGMA_CREAM("Magma Cream", 2), NETHER_STALK("Nether Wart", 5),
		GLOWSTONE_DUST("Glowstone", 2.5);

		public final String label;
		/** The average made each time (Magma Cream 1-3, Nether Wart 3-7, Glowstone 2-3). */
		public final double amount;

		Kind(String label, double amount) {
			this.label = label;
			this.amount = amount;
		}

		public String product() {
			return name();
		}

		public String distillate() {
			return name() + "_DISTILLATE";
		}
	}

	/** One of the 15 Inferno Minion Fuels. */
	public record Fuel(Grade grade, Kind kind) {
		public String id() {
			return "INFERNO_" + grade.name() + "_" + kind.name();
		}

		/** "Hypergolic Gabagool", "Heavy Blaze Rod". */
		public String label() {
			return grade.label + " " + kind.label;
		}
	}

	/** A rare drop of Hypergolic fuel, rolled once per harvest: 1 in {@code in}. Eyedrops make the boosted ones 30% likelier. */
	public record Rare(String id, double in, boolean boosted) {
	}

	public static final List<Rare> RARES = List.of(
			new Rare("CHILI_PEPPER", 136, false),
			new Rare("INFERNO_VERTEX", 5_950, true),
			new Rare("INFERNO_APEX", 1_309_091, true),
			new Rare("REAPER_PEPPER", 458_182, true),
			new Rare("GABAGOOL_THE_FISH", 3_927_273, true));
	public static final double EYEDROPS_BOOST = 1.3;
	/** Hypergolic fuel leaves one of these behind when it runs out. */
	public static final String CERAMICS = "HYPERGOLIC_IONIZED_CERAMICS";
	public static final String FUEL_BLOCK = "INFERNO_FUEL_BLOCK";
	public static final String EYEDROPS = "CAPSAICIN_EYEDROPS_NO_CHARGES";
	public static final long FUEL_MS = 24 * 3_600_000L;
	/** Each tier's seconds between actions (I to XI); a harvest takes two actions. */
	private static final int[] SECONDS = {1013, 982, 950, 919, 886, 855, 823, 792, 760, 728, 697};
	/** Each Inferno minion makes them all 18% faster, up to 10 of them. */
	public static final double CELSIUS = 0.18;
	public static final int CELSIUS_MAX = 10;

	/** What a compactor makes from {@code ratio} of {@code from}. */
	public record Compacted(String from, int ratio) {
	}

	/** Compacted items and what they're made of: VERY_CRUDE_GABAGOOL from 192 CRUDE_GABAGOOL. */
	public static final Map<String, Compacted> COMPACTED = Map.ofEntries(
			Map.entry("VERY_CRUDE_GABAGOOL", new Compacted("CRUDE_GABAGOOL", 192)),
			Map.entry("STUFFED_CHILI_PEPPER", new Compacted("CHILI_PEPPER", 160)),
			Map.entry("ENCHANTED_BLAZE_POWDER", new Compacted("BLAZE_ROD", 160)),
			Map.entry("ENCHANTED_BLAZE_ROD", new Compacted("ENCHANTED_BLAZE_POWDER", 160)),
			Map.entry("ENCHANTED_MAGMA_CREAM", new Compacted("MAGMA_CREAM", 160)),
			Map.entry("WHIPPED_MAGMA_CREAM", new Compacted("ENCHANTED_MAGMA_CREAM", 160)),
			Map.entry("ENCHANTED_NETHER_STALK", new Compacted("NETHER_STALK", 160)),
			Map.entry("MUTANT_NETHER_STALK", new Compacted("ENCHANTED_NETHER_STALK", 160)),
			Map.entry("ENCHANTED_GLOWSTONE_DUST", new Compacted("GLOWSTONE_DUST", 160)),
			Map.entry("ENCHANTED_GLOWSTONE", new Compacted("ENCHANTED_GLOWSTONE_DUST", 192)));

	/** What collected items are, and how many base items each one stands for (a compactor makes the bigger ones). */
	public static final Map<String, Integer> PRODUCTS = new LinkedHashMap<>();
	/** What the bazaar and AH call the things fuel is made of: "Gabagool Distillate" → CRUDE_GABAGOOL_DISTILLATE. */
	public static final Map<String, String> BOUGHT = new HashMap<>();
	/** Names in chat of what only Inferno minions make: "Crude Gabagool" → CRUDE_GABAGOOL. */
	public static final Map<String, String> SOLD = new HashMap<>();
	private static final Map<String, Fuel> FUELS = new LinkedHashMap<>();
	private static final Map<String, JsonObject> ITEMS = new HashMap<>();
	private static final Map<String, ItemStack> ICONS = new HashMap<>();

	static {
		for (Grade g : Grade.values()) for (Kind k : Kind.values()) FUELS.put(new Fuel(g, k).id(), new Fuel(g, k));
		Object[][] products = {{"CRUDE_GABAGOOL", 1}, {"VERY_CRUDE_GABAGOOL", 192}, {"CHILI_PEPPER", 1}, {"STUFFED_CHILI_PEPPER", 160}, {"INFERNO_VERTEX", 1},
				{"INFERNO_APEX", 1}, {"REAPER_PEPPER", 1}, {"GABAGOOL_THE_FISH", 1}, {CERAMICS, 1}, {"BLAZE_ROD", 1},
				{"ENCHANTED_BLAZE_POWDER", 160}, {"ENCHANTED_BLAZE_ROD", 25_600}, {"MAGMA_CREAM", 1}, {"ENCHANTED_MAGMA_CREAM", 160},
				{"WHIPPED_MAGMA_CREAM", 25_600}, {"NETHER_STALK", 1}, {"ENCHANTED_NETHER_STALK", 160}, {"MUTANT_NETHER_STALK", 25_600},
				{"GLOWSTONE_DUST", 1}, {"ENCHANTED_GLOWSTONE_DUST", 160}, {"ENCHANTED_GLOWSTONE", 25_600}};
		for (Object[] p : products) PRODUCTS.put((String) p[0], (Integer) p[1]);
		load();
		for (Kind k : Kind.values()) BOUGHT.put(plain(k.distillate()), k.distillate());
		for (Grade g : Grade.values()) BOUGHT.put(plain(g.gabagool), g.gabagool);
		BOUGHT.put(plain(FUEL_BLOCK), FUEL_BLOCK);
		BOUGHT.put("Capsaicin Eyedrops", EYEDROPS);
		BOUGHT.put("Inferno Minion Fuel", "");
		// Only what nothing but an Inferno minion makes: selling Blaze Rods or Nether Wart could be from anywhere.
		for (String id : List.of("CRUDE_GABAGOOL", "VERY_CRUDE_GABAGOOL", "CHILI_PEPPER", "STUFFED_CHILI_PEPPER", "INFERNO_VERTEX", "INFERNO_APEX", "REAPER_PEPPER",
				"GABAGOOL_THE_FISH", CERAMICS)) SOLD.put(plain(id), id);
	}

	private InfernoData() {
	}

	private static void load() {
		try (InputStream in = InfernoData.class.getResourceAsStream("/assets/taz/inferno/items.json")) {
			if (in == null) return;
			JsonObject all = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			for (Map.Entry<String, JsonElement> e : all.entrySet()) ITEMS.put(e.getKey(), e.getValue().getAsJsonObject());
		} catch (Exception e) {
			Taz.LOGGER.warn("Couldn't read the inferno item list", e);
		}
	}

	/** The fuel with this SkyBlock id, or null. */
	public static Fuel fuel(String id) {
		return id == null ? null : FUELS.get(id);
	}

	public static Iterable<Fuel> fuels() {
		return FUELS.values();
	}

	public static boolean isProduct(String id) {
		return PRODUCTS.containsKey(id);
	}

	/** Its name with its rarity colour: "§9Gabagool Distillate". */
	public static String name(String id) {
		JsonObject o = ITEMS.get(id);
		if (o != null && o.has("name")) return o.get("name").getAsString();
		Fuel f = fuel(id);
		return f != null ? f.grade.color + f.label() + " fuel" : "§f" + id;
	}

	/** Its name without colours. */
	public static String plain(String id) {
		return name(id).replaceAll("§.", "");
	}

	/** A fuel's name in the menus: "Hypergolic Gabagool fuel"; anything else by its own name. */
	public static String label(String id) {
		Fuel f = fuel(id);
		return f != null ? f.label() + " fuel" : plain(id);
	}

	// ---- speed and what a fuel makes

	public static int seconds(int tier) {
		return SECONDS[Math.max(1, Math.min(11, tier)) - 1];
	}

	/** Harvests a day: two actions each, sped up by {@code boost} (1.8 = +180%) and by the fuel (with Hypixel's extra 1x). */
	public static double harvests(int tier, double boost, Fuel fuel) {
		double fuelSpeed = fuel == null ? 0 : fuel.grade.speed;
		return 86_400.0 / (2 * seconds(tier)) * (1 + boost) * (1 + fuelSpeed);
	}

	/** What a fuel makes in a day, item by item (base items; a compactor doesn't change the worth). */
	public static Map<String, Double> makes(Fuel fuel, int tier, double boost, boolean eyedrops) {
		Map<String, Double> out = new LinkedHashMap<>();
		double h = harvests(tier, boost, fuel);
		out.merge(fuel.kind.product(), 0.8 * h * fuel.kind.amount, Double::sum);
		out.merge("CRUDE_GABAGOOL", 0.2 * h, Double::sum);
		if (fuel.grade == Grade.HYPERGOLIC) {
			for (Rare r : RARES) {
				double n = h / r.in * (eyedrops && r.boosted ? EYEDROPS_BOOST : 1);
				if (r.id.equals("INFERNO_APEX") && tier >= 10) n *= 2;
				out.merge(r.id, n, Double::sum);
			}
			out.merge(CERAMICS, 1.0, Double::sum);
		}
		return out;
	}

	/** The minion tier in a menu title: "Inferno Minion XI" → 11; 0 when it isn't an Inferno minion. */
	public static int tier(String title) {
		if (title == null || !title.startsWith("Inferno Minion ")) return 0;
		return roman(title.substring("Inferno Minion ".length()).trim());
	}

	static int roman(String s) {
		int total = 0, last = 0;
		for (int i = s.length() - 1; i >= 0; i--) {
			int v = switch (s.charAt(i)) {
				case 'I' -> 1;
				case 'V' -> 5;
				case 'X' -> 10;
				default -> -1;
			};
			if (v < 0) return 0;
			total += v < last ? -v : v;
			last = Math.max(last, v);
		}
		return total >= 1 && total <= 11 ? total : 0;
	}

	public static String roman(int n) {
		return switch (n) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			case 5 -> "V";
			case 6 -> "VI";
			case 7 -> "VII";
			case 8 -> "VIII";
			case 9 -> "IX";
			case 10 -> "X";
			default -> "XI";
		};
	}

	// ---- icons

	/** The item's icon: Hypixel's own model when its pack is loaded, else the head or item NEU says it looks like. */
	public static ItemStack icon(String id) {
		if (id == null || id.isEmpty()) return new ItemStack(Items.PAPER);
		return ICONS.computeIfAbsent(id, InfernoData::make);
	}

	private static ItemStack make(String id) {
		ItemStack s = pack(id);
		JsonObject o = ITEMS.get(id);
		if (s == null) {
			Item item = o == null ? null : vanilla(o.get("item").getAsString());
			s = new ItemStack(item != null ? item : Items.PAPER);
			if (o != null && o.has("texture")) {
				GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8)), "TazHead",
						new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", o.get("texture").getAsString()))));
				s.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
			}
		}
		if (o != null && o.has("glint")) s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		s.set(DataComponents.CUSTOM_NAME, com.taz.hud.TazTheme.styled(name(id)).withStyle(st -> st.withItalic(false)));
		return s;
	}

	private static ItemStack pack(String id) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getResourceManager() == null) return null;
		Identifier model = null;
		String file = id.toLowerCase(Locale.ROOT) + ".json";
		for (Identifier f : mc.getResourceManager().listResources("items",
				x -> x.getNamespace().equals("hypixel_skyblock") && x.getPath().endsWith("/" + file)).keySet()) {
			model = Identifier.fromNamespaceAndPath(f.getNamespace(), f.getPath().substring("items/".length(), f.getPath().length() - ".json".length()));
			break;
		}
		if (model == null || mc.getModelManager().getItemModel(model) instanceof MissingItemModel) return null;
		ItemStack s = new ItemStack(Items.PAPER);
		s.set(DataComponents.ITEM_MODEL, model);
		return s;
	}

	private static Item vanilla(String name) {
		Identifier id = Identifier.tryParse(name);
		return id == null ? null : BuiltInRegistries.ITEM.getOptional(id).filter(i -> i != Items.AIR).orElse(null);
	}

	/** Forgets the icons (Hypixel's pack may have loaded since). */
	public static void clearIcons() {
		ICONS.clear();
	}

	static Component text(String s) {
		return com.taz.hud.TazTheme.styled(s);
	}
}
