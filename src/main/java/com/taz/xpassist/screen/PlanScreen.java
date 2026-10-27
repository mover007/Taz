package com.taz.xpassist.screen;

import com.taz.hud.TazTheme;
import com.taz.xpassist.config.ModConfig;
import com.taz.xpassist.data.DataManager;
import com.taz.xpassist.data.NeuRepo;
import com.taz.xpassist.npc.Npc;
import com.taz.xpassist.npc.NpcTracker;
import com.taz.xpassist.util.Chat;
import com.taz.xpassist.util.ShopButtons;
import com.taz.xpassist.xp.TaskGroups;
import com.taz.xpassist.util.TextUtil;
import com.taz.xpassist.xp.TripPlanner;
import com.taz.xpassist.xp.XpResult;
import com.taz.xpassist.xp.XpTask;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The cheapest way to your next few levels as a route: stops grouped by island, everything to buy at each stop, then
 * what to do with it.
 */
public class PlanScreen extends Screen {
	private static final int ROW_HEIGHT = 12;
	private static final int LIST_TOP = 50;

	private record Row(String text, TripPlanner.Stop stop, XpTask task) {
	}

	private final Screen parent;
	private final List<Row> rows = new ArrayList<>();
	private TripPlanner.Plan builtFrom;
	private Button levelsButton;
	private double scroll;

	public PlanScreen(Screen parent) {
		super(Component.literal("Level Plan"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int y = 26;
		addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).bounds(6, 6, 50, 16).build());
		addRenderableWidget(Button.builder(Component.literal("-"), b -> changeLevels(-1)).bounds(width / 2 - 72, y, 20, 16).build());
		levelsButton = addRenderableWidget(Button.builder(levelsLabel(), b -> changeLevels(5)).bounds(width / 2 - 48, y, 96, 16).build());
		addRenderableWidget(Button.builder(Component.literal("+"), b -> changeLevels(1)).bounds(width / 2 + 52, y, 20, 16).build());
		builtFrom = null;
	}

	private static Component levelsLabel() {
		int levels = ModConfig.get().hud.planLevels;
		return Component.literal("Next " + levels + " level" + (levels == 1 ? "" : "s"));
	}

	private void changeLevels(int delta) {
		ModConfig config = ModConfig.get();
		config.hud.planLevels = Mth.clamp(config.hud.planLevels + delta, 1, 100);
		ModConfig.save();
		levelsButton.setMessage(levelsLabel());
		DataManager.replan();
		scroll = 0;
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	private void rebuild(TripPlanner.Plan plan) {
		if (plan == builtFrom) {
			return;
		}
		builtFrom = plan;
		rows.clear();
		if (plan == null) {
			return;
		}
		NeuRepo neu = DataManager.neu();
		String island = null;
		for (TripPlanner.Stop stop : plan.stops()) {
			String group = "OWNED".equals(stop.key()) ? "Ready now"
				: stop.island() != null && neu != null ? neu.islandName(stop.island())
				: stop.npcId() == null ? "Anywhere" : "Location not in the NEU repo";
			if (!Objects.equals(group, island)) {
				if (!rows.isEmpty()) {
					rows.add(new Row("", null, null));
				}
				rows.add(new Row("§e§l" + group, null, null));
				island = group;
			}
			String where = stop.npcId() != null ? " §8(" + stop.where().replace(neu != null && stop.island() != null ? neu.islandName(stop.island()) + ", " : "", "") + ")" : " §8(" + stop.where() + ")";
			rows.add(new Row("§b▸ " + stop.title() + where + " §7- " + stop.tasks().size() + " item" + (stop.tasks().size() == 1 ? "" : "s")
				+ ", §a+" + TextUtil.xp(stop.xp()) + " XP§7, §6" + TextUtil.coins(stop.cost()), stop, null));
			if (!stop.materials().isEmpty()) {
				rows.add(new Row("     §dBring: " + TextUtil.materials(stop.materials()) + " §8(buy on the Bazaar first)", stop, null));
			}
			if (stop.note() != null) {
				rows.add(new Row("     §8" + stop.note(), stop, null));
			}
			for (TaskGroups.Line line : TaskGroups.merge(stop.tasks())) {
				String cost = line.owned() ? "§a§lFREE" : "§6" + (line.partial() ? "~" : "") + TextUtil.coins(line.cost());
				String needs = line.materials().isEmpty() || line.owned() ? "" : " §d· " + TextUtil.materials(line.materials());
				rows.add(new Row("     §f• " + line.fullLabel() + " " + cost + " §8(" + TextUtil.xp(line.xp()) + " XP)" + needs, stop, line.first()));
			}
		}
		if (!plan.finishSteps().isEmpty()) {
			rows.add(new Row("", null, null));
			rows.add(new Row("§a§lThen", null, null));
			for (String step : plan.finishSteps()) {
				rows.add(new Row("§a✔ §f" + step, null, null));
			}
		}
	}

	private int listBottom() {
		return height - 22;
	}

	private int maxScroll() {
		return Math.max(0, rows.size() * ROW_HEIGHT - (listBottom() - LIST_TOP));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		TripPlanner.Plan plan = DataManager.plan();
		rebuild(plan);
		XpResult result = DataManager.result();
		String title;
		if (plan == null) {
			title = "§6§lLevel Plan §7- " + DataManager.status() + "...";
		} else if (plan.progressKnown()) {
			title = "§6§lLevel Plan: §b" + plan.fromLevel() + " §7→ §b" + plan.toLevel();
		} else {
			title = "§6§lLevel Plan: §b+" + plan.toLevel() + " levels";
		}
		graphics.centeredText(font, TazTheme.styled(title), width / 2, 8, TazTheme.TEXT);
		if (plan != null) {
			String subtitle = "§7" + plan.stops().size() + " stops  §8|  §7Cost §6" + TextUtil.coins(plan.cost(), 2) + "  §8|  §a+" + TextUtil.xp(plan.xp()) + " XP";
			if (result != null && result.coins() >= 0) {
				subtitle += "  §8|  §7You have §6" + TextUtil.coins(result.coins(), 2);
			}
			graphics.centeredText(font, TazTheme.styled(subtitle), width / 2, 44 - 12 + 16, TazTheme.TEXT);
		}

		int top = LIST_TOP + 10;
		int bottom = listBottom();
		scroll = Mth.clamp(scroll, 0, maxScroll());
		graphics.fill(6, top - 2, width - 6, bottom + 2, 0x66000000);
		graphics.enableScissor(6, top, width - 6, bottom);
		Row hovered = null;
		for (int i = (int) (scroll / ROW_HEIGHT); i < rows.size(); i++) {
			int y = top + i * ROW_HEIGHT - (int) scroll;
			if (y > bottom) {
				break;
			}
			Row row = rows.get(i);
			boolean clickable = row.stop() != null || row.task() != null;
			if (clickable && mouseY >= y && mouseY < y + ROW_HEIGHT && mouseY >= top && mouseY < bottom) {
				hovered = row;
				graphics.fill(6, y, width - 6, y + ROW_HEIGHT, 0x30FFFFFF);
			}
			String text = row.text();
			if (font.width(text) > width - 26) {
				text = font.plainSubstrByWidth(text, width - 26 - font.width("...")) + "...";
			}
			graphics.text(font, TazTheme.styled(text), 12, y + 2, TazTheme.TEXT, true);
		}
		graphics.disableScissor();
		graphics.centeredText(font, TazTheme.styled("§7Click a stop to track the NPC and get its shopping list  §8|  §7Scroll for more"), width / 2, height - 15, TazTheme.TEXT);
		super.extractRenderState(graphics, mouseX, mouseY, a);

		if (hovered != null && hovered.task() != null) {
			List<Component> lines = new ArrayList<>();
			lines.add(Component.literal(hovered.task().fullLabel()).withStyle(ChatFormatting.GOLD));
			for (String detail : hovered.task().details) {
				lines.add(Component.literal(detail).withStyle(ChatFormatting.WHITE));
			}
			graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		int top = LIST_TOP + 10;
		if (event.button() != 0 || event.y() < top || event.y() >= listBottom()) {
			return false;
		}
		int index = (int) ((event.y() - top + scroll) / ROW_HEIGHT);
		if (index < 0 || index >= rows.size()) {
			return false;
		}
		Row row = rows.get(index);
		if (row.stop() == null) {
			return false;
		}
		minecraft.setScreen(null);
		goTo(row.stop());
		return true;
	}

	/** Tracks the stop's NPC (which prints the shopping list), or prints the list for the Bazaar / AH. */
	public static void goTo(TripPlanner.Stop stop) {
		NeuRepo neu = DataManager.neu();
		Npc npc = stop.npcId() != null && neu != null ? neu.npcs.get(stop.npcId()) : null;
		if (npc != null && npc.hasLocation()) {
			NpcTracker.track(npc);
			return;
		}
		Chat.send(Component.literal(stop.title() + " ").withStyle(ChatFormatting.GOLD)
			.append(Component.literal("(" + stop.where() + ")").withStyle(ChatFormatting.GRAY)));
		printShoppingList(stop.tasks());
	}

	public static void printShoppingList(List<XpTask> tasks) {
		double cost = 0;
		double xp = 0;
		java.util.Map<String, Double> materials = new java.util.LinkedHashMap<>();
		java.util.List<XpTask.Buy> materialBuys = new ArrayList<>();
		for (TaskGroups.Line line : TaskGroups.merge(tasks)) {
			cost += line.cost();
			xp += line.xp();
			MutableComponent text = Component.literal("  • " + line.label() + " ").withStyle(ChatFormatting.WHITE)
				.append(Component.literal(line.owned() ? "FREE" : TextUtil.coins(line.cost())).withStyle(line.owned() ? ChatFormatting.GREEN : ChatFormatting.GOLD))
				.append(Component.literal(" (+" + TextUtil.xp(line.xp()) + " XP)").withStyle(ChatFormatting.DARK_GRAY));
			if (!line.owned() && !line.materials().isEmpty()) {
				text.append(Component.literal(" · " + TextUtil.materials(line.materials())).withStyle(ChatFormatting.LIGHT_PURPLE));
				line.materials().forEach((name, amount) -> materials.merge(name, amount, Double::sum));
				for (XpTask.Buy buy : line.first().buys) {
					if (materialBuys.stream().noneMatch(b -> b.query().equals(buy.query()))) {
						materialBuys.add(buy);
					}
				}
			}
			if (!line.owned()) {
				ShopButtons.append(text, line.first().buys);
			}
			Chat.send(text);
		}
		if (!materials.isEmpty()) {
			Chat.send(ShopButtons.append(Component.literal("  Bring: ").withStyle(ChatFormatting.LIGHT_PURPLE)
				.append(Component.literal(TextUtil.materials(materials)).withStyle(ChatFormatting.WHITE)), materialBuys));
		}
		if (tasks.size() > 1) {
			Chat.send(Component.literal("  Total: ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(TextUtil.coins(cost, 2)).withStyle(ChatFormatting.GOLD))
				.append(Component.literal(" for +" + TextUtil.xp(xp) + " XP").withStyle(ChatFormatting.GREEN)));
		}
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
