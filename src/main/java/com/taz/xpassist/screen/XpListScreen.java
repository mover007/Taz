package com.taz.xpassist.screen;

import com.taz.hud.TazTheme;
import com.taz.xpassist.config.ModConfig;
import com.taz.xpassist.data.DataManager;
import com.taz.xpassist.data.NeuRepo;
import com.taz.xpassist.npc.Npc;
import com.taz.xpassist.npc.NpcTracker;
import com.taz.xpassist.progress.LocalProgress;
import com.taz.xpassist.util.Chat;
import com.taz.xpassist.util.ShopButtons;
import com.taz.xpassist.xp.TaskGroups;
import com.taz.xpassist.util.TextUtil;
import com.taz.xpassist.xp.XpCategory;
import com.taz.xpassist.xp.XpResult;
import com.taz.xpassist.xp.XpTask;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Full list, like the SkyHelper /level embed: every upgrade sorted by coins per SkyBlock XP. */
public class XpListScreen extends Screen {
	private static final int ROW_HEIGHT = 12;
	private int listTop = 72;

	private record Row(XpTask task, String text, List<XpTask> group) {
		Row(XpTask task, String text) {
			this(task, text, task == null ? List.of() : List.of(task));
		}
	}

	private final Map<XpCategory, Button> categoryButtons = new EnumMap<>(XpCategory.class);
	private final List<Row> rows = new ArrayList<>();
	private EditBox search;
	/** 0 = upgrades you can do, 1 = locked, 2 = no coin price, 3 = other XP from /skyblockxp. Kept between openings. */
	private static int tab;
	private static final int TABS = 4;
	private final Button[] tabButtons = new Button[TABS];
	private Button unhideButton;
	private double scroll;
	private XpResult builtFrom;
	private String builtQuery = "";
	private int builtTab = -1;

	public XpListScreen() {
		super(Component.literal("SkyBlock XP Assist"));
	}

	@Override
	protected void init() {
		ModConfig config = ModConfig.get();
		// Category toggles: one row on wide screens, two rows of three on narrow ones.
		int count = XpCategory.values().length;
		int perRow = (width - 20) / count >= 84 ? count : (count + 1) / 2;
		int buttonWidth = Math.min(110, (width - 20) / perRow - 4);
		int rowStart = (width - (buttonWidth + 4) * perRow + 4) / 2;
		int y = 30;
		int index = 0;
		for (XpCategory category : XpCategory.values()) {
			int column = index % perRow;
			if (index > 0 && column == 0) {
				y += 20;
			}
			Button button = addRenderableWidget(Button.builder(categoryLabel(category), b -> toggleCategory(category))
				.bounds(rowStart + column * (buttonWidth + 4), y, buttonWidth, 16).build());
			categoryButtons.put(category, button);
			index++;
		}
		y += 20;

		// Toolbar: buttons on the right, the search box takes whatever space is left.
		int[] widths = {0, 52, 70, 64, 40};
		int buttonsWidth = widths[1] + widths[2] + widths[3] + widths[4] + 12;
		int searchWidth = Math.max(80, Math.min(200, width - 20 - buttonsWidth - 6));
		int toolbarWidth = searchWidth + 6 + buttonsWidth;
		int bx = Math.max(10, (width - toolbarWidth) / 2);
		String previous = search != null ? search.getValue() : "";
		search = new EditBox(font, bx, y, searchWidth, 16, Component.literal("Search"));
		search.setHint(Component.literal("Search upgrades...").withStyle(ChatFormatting.DARK_GRAY));
		search.setValue(previous);
		search.setResponder(text -> scroll = 0);
		addRenderableWidget(search);
		bx += searchWidth + 6;

		addRenderableWidget(Button.builder(Component.literal("Refresh"), b -> {
			b.active = false;
			DataManager.refresh(true, error -> {
				b.active = true;
				if (error != null) {
					Chat.error(error);
				}
			});
		}).bounds(bx, y, widths[1], 16).build());
		bx += widths[1] + 4;
		addRenderableWidget(Button.builder(Component.literal("NPC Finder"), b -> minecraft.setScreen(new NpcFinderScreen(this)))
			.bounds(bx, y, widths[2], 16).build());
		bx += widths[2] + 4;
		unhideButton = addRenderableWidget(Button.builder(unhideLabel(), b -> {
			synchronized (config) {
				config.hiddenTasks.clear();
			}
			ModConfig.save();
			DataManager.recompute();
			b.setMessage(unhideLabel());
		}).bounds(bx, y, widths[3], 16).build());
		bx += widths[3] + 4;
		addRenderableWidget(Button.builder(Component.literal("Plan"), b -> minecraft.setScreen(new PlanScreen(this)))
			.bounds(bx, y, widths[4], 16)
			.tooltip(Tooltip.create(Component.literal("Your next levels as a shopping route: what to buy at each NPC")))
			.build());
		// Tabs: what you can do now, what you need to unlock first, and what has no coin price.
		y += 20;
		int tabWidth = Math.min(120, (width - 24) / TABS);
		int tabX = (width - tabWidth * TABS - 4 * (TABS - 1)) / 2;
		for (int i = 0; i < TABS; i++) {
			int tabIndex = i;
			tabButtons[i] = addRenderableWidget(Button.builder(Component.literal(""), b -> {
				tab = tabIndex;
				scroll = 0;
			}).bounds(tabX + i * (tabWidth + 4), y, tabWidth, 16).build());
		}
		updateTabLabels(DataManager.result());
		listTop = y + 22;
		builtFrom = null;
	}

	/** Cuts text to fit the screen, keeping formatting codes. */
	private String fit(String text, int maxWidth) {
		if (font.width(text) <= maxWidth) {
			return text;
		}
		return font.plainSubstrByWidth(text, maxWidth - font.width("...")) + "...";
	}

	private static Component categoryLabel(XpCategory category) {
		boolean enabled = !ModConfig.get().disabledCategories.contains(category.name());
		return Component.literal(enabled ? "✔ " : "✘ ").withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED)
			.append(Component.literal(category.label).withStyle(enabled ? ChatFormatting.WHITE : ChatFormatting.GRAY));
	}

	private void updateTabLabels(XpResult result) {
		int locked = result != null ? result.locked().size() : 0;
		int unpriced = result != null ? result.unpriced().size() : 0;
		int other = LocalProgress.openMenuTasks().size();
		String[] labels = {"Upgrades", "Locked (" + locked + ")", "No price (" + unpriced + ")", "Other XP (" + other + ")"};
		for (int i = 0; i < TABS; i++) {
			if (tabButtons[i] != null) {
				tabButtons[i].setMessage(Component.literal(i == tab ? "▶ " + labels[i] + " ◀" : labels[i])
					.withStyle(i == tab ? ChatFormatting.YELLOW : ChatFormatting.WHITE));
			}
		}
	}

	private static Component unhideLabel() {
		return Component.literal("Unhide (" + ModConfig.get().hiddenTasks.size() + ")");
	}

	private void toggleCategory(XpCategory category) {
		ModConfig config = ModConfig.get();
		synchronized (config) {
			if (!config.disabledCategories.remove(category.name())) {
				config.disabledCategories.add(category.name());
			}
		}
		ModConfig.save();
		categoryButtons.get(category).setMessage(categoryLabel(category));
		DataManager.recompute();
	}

	private void rebuildRows(XpResult result) {
		String query = search.getValue().trim().toLowerCase(Locale.ROOT);
		if (result == builtFrom && query.equals(builtQuery) && tab == builtTab) {
			return;
		}
		builtFrom = result;
		builtQuery = query;
		builtTab = tab;
		rows.clear();
		if (result == null) {
			return;
		}
		if (tab == 1) {
			rows.add(new Row(null, "§7Hidden from your list until you meet the requirement (collection, slayer, reputation, contact)"));
			for (XpTask task : result.locked()) {
				if (query.isEmpty() || task.fullLabel().toLowerCase(Locale.ROOT).contains(query)) {
					rows.add(new Row(task, taskText(task) + " §c" + task.lockedReason));
				}
			}
			return;
		}
		if (tab == 3) {
			otherRows(query);
			return;
		}
		if (tab == 2) {
			rows.add(new Row(null, "§7These need something you can't just buy with coins"));
			for (XpTask task : result.unpriced()) {
				if (query.isEmpty() || task.fullLabel().toLowerCase(Locale.ROOT).contains(query)) {
					rows.add(new Row(task, taskText(task)));
				}
			}
			return;
		}
		int startLevel = result.currentLevel(DataManager.rules() != null ? DataManager.rules().xpPerLevel() : 100);
		List<XpTask> run = new ArrayList<>();
		for (XpResult.Entry entry : result.entries()) {
			if (entry instanceof XpResult.TaskEntry taskEntry) {
				XpTask task = taskEntry.task();
				if (query.isEmpty() || task.fullLabel().toLowerCase(Locale.ROOT).contains(query)) {
					run.add(task);
				}
			} else if (entry instanceof XpResult.LevelEntry level && query.isEmpty()) {
				flush(run);
				String label = result.progressKnown() ? "SkyBlock Level " + level.level() : "+" + (level.level() - startLevel) + " Level" + (level.level() - startLevel == 1 ? "" : "s");
				rows.add(new Row(null, "§a§l" + label + ": §6§l" + TextUtil.coins(level.cumulativeCost(), 2)));
			}
		}
		flush(run);
	}

	/** Adds the collected tasks as rows, one per tier run ("Buy Trapped I → V"). */
	private void flush(List<XpTask> run) {
		for (TaskGroups.Line line : TaskGroups.merge(run)) {
			String cost = line.owned() ? "§a§lFREE" : "§6§l" + (line.partial() ? "~" : "") + TextUtil.coins(line.cost());
			String npc = line.first().npcId != null ? " §b⌖" : "";
			rows.add(new Row(line.first(), "§f" + line.fullLabel() + ": " + cost + "§r §8(" + TextUtil.xp(line.xp()) + " XP)" + npc, line.tasks()));
		}
		run.clear();
	}

	/** Everything else from Hypixel's /skyblockxp menus that isn't done yet, grouped by menu page. */
	private void otherRows(String query) {
		rows.add(new Row(null, "§7Open task pages in §f/skyblockxp§7 (Core, Slaying, Skill Related, Misc...) to fill this in"));
		Map<String, List<LocalProgress.MenuTask>> byMenu = new java.util.TreeMap<>();
		for (LocalProgress.MenuTask task : LocalProgress.openMenuTasks()) {
			if (query.isEmpty() || task.name.toLowerCase(Locale.ROOT).contains(query) || task.menu.toLowerCase(Locale.ROOT).contains(query)) {
				byMenu.computeIfAbsent(task.menu, k -> new ArrayList<>()).add(task);
			}
		}
		for (Map.Entry<String, List<LocalProgress.MenuTask>> menu : byMenu.entrySet()) {
			rows.add(new Row(null, ""));
			rows.add(new Row(null, "§e§l" + menu.getKey()));
			for (LocalProgress.MenuTask task : menu.getValue()) {
				String xp = task.xp > 0 ? " §b+" + task.xp + " XP" : "";
				String progress = task.progress != null ? " §8- §7" + task.progress : "";
				rows.add(new Row(null, "  §f• " + task.name + xp + progress));
			}
		}
		if (byMenu.isEmpty()) {
			rows.add(new Row(null, ""));
			rows.add(new Row(null, "§8Nothing synced yet."));
		}
	}

	private static String taskText(XpTask task) {
		if (task.owned) {
			return "§f" + task.fullLabel() + ": §a§lFREE§r §8(" + TextUtil.xp(task.xp) + " XP, you have it)";
		}
		String cost = task.priced() ? (task.partialPrice ? "~" : "") + TextUtil.coins(task.cost) : "?";
		String npc = task.npcId != null ? " §b⌖" : "";
		return "§f" + task.fullLabel() + ": §6§l" + cost + "§r §8(" + TextUtil.xp(task.xp) + " XP)" + npc;
	}

	private int listBottom() {
		return height - 26;
	}

	private int maxScroll() {
		return Math.max(0, rows.size() * ROW_HEIGHT - (listBottom() - listTop));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		XpResult result = DataManager.result();
		rebuildRows(result);
		unhideButton.setMessage(unhideLabel());
		updateTabLabels(result);

		String player = minecraft.getUser().getName();
		String title = "§6§l" + player + "'s Cheapest SkyBlock Experience";
		if (result != null && result.profileName() != null) {
			title += " on " + result.profileName();
		}
		graphics.centeredText(font, TazTheme.styled(fit(title, width - 12)), width / 2, 6, TazTheme.TEXT);
		String subtitle;
		if (result == null) {
			subtitle = "§7" + DataManager.status() + "...";
		} else {
			String total = "§7Total cost for all upgrades: §6§l" + TextUtil.coins(result.totalCost(), 2)
				+ " §8(" + TextUtil.xp(result.totalXp()) + " XP)";
			int affordable = result.affordableLevel();
			String budget = result.coins() >= 0
				? "  §8|  §7Your coins: §6" + TextUtil.coins(result.coins(), 2) + (affordable > 0 ? " §7→ §bLevel " + affordable : "") : "";
			subtitle = "§7Sorted by least coins per SkyBlock XP  §8|  " + total + budget;
			if (font.width(subtitle) > width - 12) {
				subtitle = total + budget;
			}
			if (font.width(subtitle) > width - 12) {
				subtitle = total;
			}
		}
		graphics.centeredText(font, TazTheme.styled(fit(subtitle, width - 12)), width / 2, 18, TazTheme.TEXT);

		int top = listTop;
		int bottom = listBottom();
		scroll = Mth.clamp(scroll, 0, maxScroll());
		graphics.fill(6, top - 2, width - 6, bottom + 2, 0x66000000);
		graphics.enableScissor(6, top, width - 6, bottom);
		Row hovered = null;
		int first = (int) (scroll / ROW_HEIGHT);
		for (int i = first; i < rows.size(); i++) {
			int y = top + i * ROW_HEIGHT - (int) scroll;
			if (y > bottom) {
				break;
			}
			Row row = rows.get(i);
			boolean hover = row.task() != null && mouseY >= y && mouseY < y + ROW_HEIGHT && mouseY >= top && mouseY < bottom
				&& mouseX >= 6 && mouseX < width - 6;
			if (hover) {
				hovered = row;
				graphics.fill(6, y, width - 6, y + ROW_HEIGHT, 0x30FFFFFF);
			}
			int textX = 12;
			if (row.task() != null) {
				graphics.fill(12, y + 3, 17, y + 8, 0xFF000000 | row.task().category.color);
				textX = 21;
			}
			graphics.text(font, TazTheme.styled(fit(row.text(), width - textX - 14)), textX, y + 2, TazTheme.TEXT, true);
		}
		graphics.disableScissor();

		if (maxScroll() > 0) {
			int trackHeight = bottom - top;
			int barHeight = Math.max(16, trackHeight * trackHeight / (rows.size() * ROW_HEIGHT));
			int barY = top + (int) ((trackHeight - barHeight) * (scroll / maxScroll()));
			graphics.fill(width - 10, barY, width - 7, barY + barHeight, 0xAAFFFFFF);
		}

		String footer;
		if (DataManager.error() != null) {
			footer = "§c" + DataManager.error();
		} else if (result != null && !result.warnings().isEmpty()) {
			footer = "§e" + result.warnings().getFirst();
		} else if (DataManager.busy()) {
			footer = "§7" + DataManager.status() + "...";
		} else {
			footer = "§7Left-click: track NPC / details  §8|  §7Right-click: hide"
				+ (result != null && result.source() != null ? "  §8|  §7Progress: §f" + result.source() : "");
		}
		graphics.centeredText(font, TazTheme.styled(fit(footer, width - 12)), width / 2, height - 18, TazTheme.TEXT);

		super.extractRenderState(graphics, mouseX, mouseY, a);

		if (hovered != null) {
			graphics.setComponentTooltipForNextFrame(font, tooltip(hovered.task(), hovered.group()), mouseX, mouseY);
		}
	}

	private List<Component> tooltip(XpTask task, List<XpTask> group) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal(task.fullLabel()).withStyle(ChatFormatting.GOLD));
		if (task.lockedReason != null) {
			lines.add(Component.literal("Locked: " + task.lockedReason).withStyle(ChatFormatting.RED));
		}
		lines.add(Component.literal(TextUtil.xp(task.xp) + " SkyBlock XP for " + (task.priced() ? TextUtil.coins(task.cost) + " coins" : "no coin price"))
			.withStyle(ChatFormatting.GRAY));
		if (task.priced() && task.xp > 0) {
			lines.add(Component.literal(TextUtil.coins(task.coinsPerXp()) + " coins per XP").withStyle(ChatFormatting.DARK_GRAY));
		}
		for (String detail : task.details) {
			lines.add(Component.literal(detail).withStyle(ChatFormatting.WHITE));
		}
		if (group.size() > 1) {
			TaskGroups.Line line = TaskGroups.line(group);
			lines.add(Component.literal(group.size() + " upgrades: " + TextUtil.coins(line.cost()) + " for " + TextUtil.xp(line.xp()) + " XP")
				.withStyle(ChatFormatting.AQUA));
			if (!line.materials().isEmpty()) {
				lines.add(Component.literal("Total needed: " + TextUtil.materials(line.materials())).withStyle(ChatFormatting.LIGHT_PURPLE));
			}
		} else if (!task.materials.isEmpty()) {
			lines.add(Component.literal("Needs: " + TextUtil.materials(task.materials)).withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		lines.add(Component.empty());
		Npc npc = npcFor(task);
		if (npc != null) {
			lines.add(Component.literal("Left-click: track " + npc.name() + " through walls").withStyle(ChatFormatting.AQUA));
		} else {
			lines.add(Component.literal("Left-click: print details in chat").withStyle(ChatFormatting.YELLOW));
		}
		lines.add(Component.literal("Right-click: hide this upgrade").withStyle(ChatFormatting.GRAY));
		return lines;
	}

	private static Npc npcFor(XpTask task) {
		NeuRepo neu = DataManager.neu();
		if (task.npcId == null || neu == null) {
			return null;
		}
		Npc npc = neu.npcs.get(task.npcId);
		return npc != null && npc.hasLocation() ? npc : null;
	}

	private Row rowAt(double mouseX, double mouseY) {
		if (mouseY < listTop || mouseY >= listBottom() || mouseX < 6 || mouseX >= width - 6) {
			return null;
		}
		int index = (int) ((mouseY - listTop + scroll) / ROW_HEIGHT);
		return index >= 0 && index < rows.size() ? rows.get(index) : null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		Row row = rowAt(event.x(), event.y());
		if (row == null || row.task() == null) {
			return false;
		}
		XpTask task = row.task();
		if (event.button() == 1) {
			synchronized (ModConfig.get()) {
				for (XpTask hidden : row.group()) {
					ModConfig.get().hiddenTasks.add(hidden.id);
				}
			}
			ModConfig.save();
			DataManager.recompute();
			return true;
		}
		if (event.button() == 0) {
			Npc npc = npcFor(task);
			onClose();
			Chat.send(Component.literal(task.fullLabel()).withStyle(ChatFormatting.GOLD)
				.append(Component.literal(" - " + TextUtil.xp(task.xp) + " XP for " + (task.priced() ? TextUtil.coins(task.cost) : "?") + " coins")
					.withStyle(ChatFormatting.GRAY)));
			for (String detail : task.details) {
				Chat.info(detail);
			}
			if (!task.owned && !task.buys.isEmpty()) {
				Chat.send(ShopButtons.append(Component.literal("Buy:").withStyle(ChatFormatting.GRAY), task.buys));
			}
			if (npc != null) {
				NpcTracker.track(npc);
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		scroll = Mth.clamp(scroll - scrollY * ROW_HEIGHT * 3, 0, maxScroll());
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
