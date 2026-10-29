package com.taz.fishing.trophy;

import com.taz.hud.HudPos;
import com.taz.hud.MovableHud;
import com.taz.hud.TazTheme;
import com.taz.hud.TextHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A trophy collection as a table, one row per species: its icon, its name in its rarity's red (light for common, darker
 * up to legendary), then the total and each tier's count in that tier's red (Bronze light to Diamond dark; a grey ✔ for a
 * tier a higher one covers), and the pity you're working toward.
 * <pre>
 * Trophy Fish · 27/72 · BRONZE 15/18
 *                         Total  B  S  G  D  Pity
 * [] Sulphur Skitter          5  3  2  0  0  G 5/100
 * [] Blobfish               373 269 94  9  1
 * </pre>
 * Icons are the Hypixel resource pack's (Taz ships none); without the pack, a plain item.
 */
public abstract class TrophyTableHud implements MovableHud {
	private static final int PADDING = 3, ICON = 11, GAP = 6;
	/** Names by rarity, common to legendary: light red getting darker. */
	private static final int[] RARITY = {0xFFFFB8B8, 0xFFFF8C8C, 0xFFFF5A5A, 0xFFE02828, 0xFFB3121B};
	/** The tier columns, Bronze to Diamond: the same reds, left to right. */
	private static final int[] TIER = {0xFFFFB8B8, 0xFFFF7A7A, 0xFFEB3434, 0xFFB3121B};
	private static final Map<Identifier, ItemStack> ICONS = new HashMap<>();

	private final String name;
	private final double defaultX, defaultY;

	private record Row(ItemStack icon, Component name, Component[] cells) {
	}

	private record Table(Component title, Component[] labels, List<Row> rows, List<String> footer) {
	}

	protected TrophyTableHud(String name, double defaultX, double defaultY) {
		this.name = name;
		this.defaultX = defaultX;
		this.defaultY = defaultY;
	}

	protected abstract HudPos pos();

	protected abstract TrophyBook book();

	/** Whether to show it now (on its island); the HUD editor shows it anyway. */
	protected abstract boolean here();

	protected abstract boolean onlyMissing();

	protected abstract boolean pityColumn();

	/** Lines under the table. */
	protected abstract List<String> footer();

	/** The icon when the Hypixel resource pack isn't loaded. */
	protected abstract ItemStack plainIcon();

	private Table table(boolean preview) {
		if (!preview && !here()) return null;
		TrophyBook b = book();
		Tier goal = b.goalTier();
		MutableComponent title = TazTheme.styled("§c§l" + b.name + " §7· §f" + b.found() + "§7/" + b.size() * 4);
		if (goal == null) title.append(TazTheme.styled(" §a✔"));
		else title.append(TazTheme.styled(" §7· ")).append(red(goal.name(), TIER[goal.ordinal()])).append(TazTheme.styled(" §f" + b.caughtAt(goal) + "§7/" + b.size()));
		boolean pity = pityColumn();
		Component[] labels = new Component[pity ? 6 : 5];
		labels[0] = TazTheme.styled("§7Total");
		for (Tier t : Tier.values()) labels[1 + t.ordinal()] = red(t.letter(), TIER[t.ordinal()]);
		if (pity) labels[5] = TazTheme.styled("§7Pity");
		List<Row> rows = new ArrayList<>();
		for (int i = 0; i < b.size(); i++) {
			boolean all = true;
			Tier best = null;
			Component[] cells = new Component[labels.length];
			cells[0] = TazTheme.styled("§d" + b.total(i));
			for (Tier t : Tier.values()) {
				int n = b.count(i, t);
				boolean has = b.has(i, t);
				all &= b.reached(i, t);
				if (has) best = t;
				// The tab list can say you have a tier the counts don't know about yet; a higher tier covers a missing one.
				cells[1 + t.ordinal()] = n > 0 ? red(String.valueOf(n), TIER[t.ordinal()]) : has ? red("✔", TIER[t.ordinal()])
						: TazTheme.styled(b.reached(i, t) ? "§8✔" : "§80");
			}
			if (onlyMissing() && all) continue;
			if (pity) cells[5] = TazTheme.styled(b.pity(i).isEmpty() ? "" : "§8" + b.pity(i));
			TrophyBook.Entry e = b.entry(i);
			rows.add(new Row(icon(e, best == null ? Tier.BRONZE : best), red(e.label(), rarityColor(e.rarity())), cells));
		}
		return new Table(title, labels, rows, footer());
	}

	private static Component red(String text, int color) {
		return Component.literal(text).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color & 0xFFFFFF)));
	}

	/** A species' name colour from its Hypixel rarity code (§f common, §a uncommon, §9 rare, §5 epic, §6 legendary). */
	public static int rarityColor(String code) {
		return switch (code) {
			case "§a" -> RARITY[1];
			case "§9" -> RARITY[2];
			case "§5" -> RARITY[3];
			case "§6" -> RARITY[4];
			default -> RARITY[0];
		};
	}

	private ItemStack icon(TrophyBook.Entry e, Tier t) {
		Identifier id = Identifier.fromNamespaceAndPath("hypixel_skyblock", "item/fishing/trophy/" + e.model() + "_" + t.name().toLowerCase(Locale.ROOT));
		if (Minecraft.getInstance().getModelManager().getItemModel(id) instanceof MissingItemModel) return plainIcon();
		return ICONS.computeIfAbsent(id, k -> {
			ItemStack s = new ItemStack(Items.PAPER);
			s.set(DataComponents.ITEM_MODEL, k);
			return s;
		});
	}

	/** The table as text lines (tests): the title, the column labels, a line per species, and the footer. */
	public final List<String> shownLines(boolean preview) {
		Table t = table(preview);
		List<String> out = new ArrayList<>();
		if (t == null) return out;
		out.add(t.title.getString());
		out.add(plain(t.labels));
		for (Row r : t.rows) out.add(r.name.getString() + " " + plain(r.cells).trim());
		out.addAll(t.footer);
		return out;
	}

	private static String plain(Component[] cells) {
		List<String> out = new ArrayList<>();
		for (Component c : cells) out.add(c.getString());
		return String.join(" ", out);
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public boolean hidden() {
		return pos().hidden;
	}

	@Override
	public void setHidden(boolean hidden) {
		pos().hidden = hidden;
	}

	@Override
	public float scale() {
		return pos().scale;
	}

	@Override
	public void setScale(float scale) {
		pos().scale = TextHud.clampScale(scale);
	}

	@Override
	public void reset() {
		pos().x = defaultX;
		pos().y = defaultY;
		pos().scale = 1.0f;
	}

	@Override
	public int[] bounds(Font font, int screenW, int screenH, boolean preview) {
		Table t = table(preview);
		return t == null ? null : box(font, t, screenW, screenH);
	}

	@Override
	public void moveTo(int x, int y, int screenW, int screenH) {
		pos().x = screenW > 0 ? x / (double) screenW : 0;
		pos().y = screenH > 0 ? y / (double) screenH : 0;
	}

	@Override
	public void draw(GuiGraphicsExtractor g, Font font, boolean preview) {
		Table t = table(preview);
		if (t == null) return;
		int[] b = box(font, t, g.guiWidth(), g.guiHeight());
		int[] cols = columns(font, t);
		int line = font.lineHeight + 1;
		float scale = TextHud.clampScale(pos().scale);
		g.pose().pushMatrix();
		g.pose().translate(b[0], b[1]);
		g.pose().scale(scale, scale);
		int y = PADDING;
		TazTheme.outlined(g, font, t.title, PADDING, y, TazTheme.TEXT);
		y += line;
		cells(g, font, t.labels, cols, y);
		y += line;
		for (Row r : t.rows) {
			g.pose().pushMatrix();
			g.pose().translate(PADDING, y - 0.5f);
			g.pose().scale(0.5f, 0.5f);
			g.fakeItem(r.icon, 0, 0);
			g.pose().popMatrix();
			TazTheme.outlined(g, font, r.name, PADDING + ICON, y, TazTheme.TEXT);
			cells(g, font, r.cells, cols, y);
			y += line;
		}
		for (String f : t.footer) {
			TazTheme.line(g, font, f, PADDING, y);
			y += line;
		}
		g.pose().popMatrix();
	}

	/** Cells right-aligned at each column's right edge. */
	private static void cells(GuiGraphicsExtractor g, Font font, Component[] cells, int[] cols, int y) {
		for (int j = 0; j < cells.length && j < cols.length; j++) TazTheme.outlined(g, font, cells[j], cols[j] - font.width(cells[j]), y, TazTheme.TEXT);
	}

	private static int width(Font font, String s) {
		return font.width(TazTheme.styled(s));
	}

	/** Each column's right edge, after the icons and names. */
	private static int[] columns(Font font, Table t) {
		int nameW = 0;
		for (Row r : t.rows) nameW = Math.max(nameW, font.width(r.name));
		int[] right = new int[t.labels.length];
		int x = PADDING + ICON + nameW;
		for (int j = 0; j < t.labels.length; j++) {
			int w = font.width(t.labels[j]);
			for (Row r : t.rows) w = Math.max(w, font.width(r.cells[j]));
			x += GAP + w;
			right[j] = x;
		}
		return right;
	}

	/** Unscaled size including padding. */
	private static int[] size(Font font, Table t) {
		int[] cols = columns(font, t);
		int w = cols[cols.length - 1];
		w = Math.max(w, PADDING + font.width(t.title));
		for (String f : t.footer) w = Math.max(w, PADDING + width(font, f));
		int lines = 2 + t.rows.size() + t.footer.size();
		return new int[]{w + PADDING, lines * (font.lineHeight + 1) - 1 + PADDING * 2};
	}

	/** Screen box at the saved spot, pushed back inside the screen if needed. */
	private int[] box(Font font, Table t, int screenW, int screenH) {
		int[] size = size(font, t);
		float scale = TextHud.clampScale(pos().scale);
		int w = Math.round(size[0] * scale), h = Math.round(size[1] * scale);
		int x = (int) Math.round(pos().x * screenW), y = (int) Math.round(pos().y * screenH);
		x = Math.max(0, Math.min(x, screenW - w));
		y = Math.max(0, Math.min(y, screenH - h));
		return new int[]{x, y, w, h};
	}
}
