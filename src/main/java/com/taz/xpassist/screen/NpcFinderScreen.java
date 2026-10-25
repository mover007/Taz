package com.taz.xpassist.screen;

import com.taz.xpassist.data.DataManager;
import com.taz.xpassist.data.HypixelItems;
import com.taz.xpassist.data.NeuRepo;
import com.taz.xpassist.npc.Npc;
import com.taz.xpassist.npc.NpcTracker;
import com.taz.xpassist.util.TextUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Search NPCs by name, or by an item they sell ("decent bow" -> Gregory, The End). Click to track. */
public class NpcFinderScreen extends Screen {
	private static final int ROW_HEIGHT = 12;
	private static final int LIST_TOP = 52;

	private record Row(Npc npc, String text) {
	}

	private final Screen parent;
	private final List<Row> rows = new ArrayList<>();
	private EditBox search;
	private Button stopButton;
	private String builtQuery;
	private NeuRepo builtFrom;
	private double scroll;

	public NpcFinderScreen(Screen parent) {
		super(Component.literal("NPC Finder"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		String previous = search != null ? search.getValue() : "";
		search = new EditBox(font, width / 2 - 150, 26, 300, 18, Component.literal("Search"));
		search.setHint(Component.literal("NPC name or an item they sell...").withStyle(ChatFormatting.DARK_GRAY));
		search.setValue(previous);
		search.setResponder(text -> scroll = 0);
		addRenderableWidget(search);
		setInitialFocus(search);
		addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).bounds(6, 6, 50, 16).build());
		stopButton = addRenderableWidget(Button.builder(Component.literal("Stop tracking"), b -> {
			NpcTracker.untrack();
			b.active = false;
		}).bounds(width - 96, 6, 90, 16).build());
		stopButton.active = NpcTracker.target() != null;
		builtQuery = null;
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	private void rebuild() {
		NeuRepo neu = DataManager.neu();
		String query = search.getValue().trim();
		if (neu == builtFrom && query.equals(builtQuery)) {
			return;
		}
		builtFrom = neu;
		builtQuery = query;
		rows.clear();
		if (neu == null) {
			return;
		}
		if (query.isEmpty()) {
			List<Npc> all = new ArrayList<>();
			for (Npc npc : neu.npcs.values()) {
				if (npc.hasLocation()) {
					all.add(npc);
				}
			}
			all.sort(Comparator.comparing((Npc npc) -> neu.islandName(npc.island())).thenComparing(Npc::name));
			for (Npc npc : all) {
				rows.add(new Row(npc, npcText(neu, npc)));
			}
			return;
		}
		for (Npc npc : neu.searchNpcs(query)) {
			if (npc.hasLocation()) {
				rows.add(new Row(npc, npcText(neu, npc)));
			}
		}
		String wanted = TextUtil.normalize(query);
		HypixelItems items = DataManager.items();
		List<Row> offers = new ArrayList<>();
		for (Map.Entry<String, List<NeuRepo.ShopOffer>> entry : neu.offersByItem.entrySet()) {
			String name = items != null && items.name(entry.getKey()) != null ? items.name(entry.getKey()) : neu.name(entry.getKey());
			if (!TextUtil.normalize(name).contains(wanted)) {
				continue;
			}
			for (NeuRepo.ShopOffer offer : entry.getValue()) {
				Npc npc = neu.npcs.get(offer.npcId());
				if (npc == null || !npc.hasLocation()) {
					continue;
				}
				offers.add(new Row(npc, "§a" + name + " §7sold by §f" + npc.name() + " §8(§e" + neu.islandName(npc.island()) + "§8) §6"
					+ costText(neu, offer)));
			}
			if (offers.size() > 200) {
				break;
			}
		}
		if (!offers.isEmpty()) {
			rows.add(new Row(null, ""));
			rows.add(new Row(null, "§7§lItems sold by NPCs"));
			rows.addAll(offers);
		}
	}

	private static String npcText(NeuRepo neu, Npc npc) {
		String tag = "Abiphone".equals(npc.source()) ? " §d(Abiphone contact)" : "";
		return "§f" + npc.name() + " §8- §e" + neu.islandName(npc.island()) + " §8(" + npc.x() + ", " + npc.y() + ", " + npc.z() + ")" + tag;
	}

	private static String costText(NeuRepo neu, NeuRepo.ShopOffer offer) {
		List<String> parts = new ArrayList<>();
		for (Map.Entry<String, Double> cost : offer.costs().entrySet()) {
			if (NeuRepo.COIN.equals(cost.getKey())) {
				parts.add(TextUtil.coins(cost.getValue()) + " coins");
			} else {
				HypixelItems items = DataManager.items();
				String name = items != null && items.name(cost.getKey()) != null ? items.name(cost.getKey()) : neu.name(cost.getKey());
				parts.add(TextUtil.xp(cost.getValue()) + "x " + name);
			}
		}
		String text = String.join(" + ", parts);
		return offer.resultCount() > 1 ? text + " for " + offer.resultCount() : text;
	}

	private int listBottom() {
		return height - 22;
	}

	private int maxScroll() {
		return Math.max(0, rows.size() * ROW_HEIGHT - (listBottom() - LIST_TOP));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		rebuild();
		stopButton.active = NpcTracker.target() != null;
		graphics.centeredText(font, "§6§lNPC Finder", width / 2, 10, 0xFFFFFFFF);
		int top = LIST_TOP;
		int bottom = listBottom();
		scroll = Mth.clamp(scroll, 0, maxScroll());
		graphics.fill(6, top - 2, width - 6, bottom + 2, 0x66000000);
		graphics.enableScissor(6, top, width - 6, bottom);
		int first = (int) (scroll / ROW_HEIGHT);
		for (int i = first; i < rows.size(); i++) {
			int y = top + i * ROW_HEIGHT - (int) scroll;
			if (y > bottom) {
				break;
			}
			Row row = rows.get(i);
			if (row.npc() != null && mouseY >= y && mouseY < y + ROW_HEIGHT && mouseY >= top && mouseY < bottom) {
				graphics.fill(6, y, width - 6, y + ROW_HEIGHT, 0x30FFFFFF);
			}
			graphics.text(font, row.text(), 12, y + 2, 0xFFFFFFFF, true);
		}
		graphics.disableScissor();
		String footer = DataManager.neu() == null ? "§7" + DataManager.status() + "..."
			: "§7" + rows.size() + " results  §8|  §7Click an NPC to highlight it through walls";
		graphics.centeredText(font, footer, width / 2, height - 15, 0xFFFFFFFF);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (event.button() != 0 || event.y() < LIST_TOP || event.y() >= listBottom()) {
			return false;
		}
		int index = (int) ((event.y() - LIST_TOP + scroll) / ROW_HEIGHT);
		if (index < 0 || index >= rows.size() || rows.get(index).npc() == null) {
			return false;
		}
		Npc npc = rows.get(index).npc();
		minecraft.setScreen(null);
		NpcTracker.track(npc);
		return true;
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
