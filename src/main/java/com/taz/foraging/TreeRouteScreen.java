package com.taz.foraging;

import com.taz.Taz;
import com.taz.dungeons.util.TextUtil;
import com.taz.hud.TazTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Foraging > Tree Route Assist > Choose route: every route you have, by island; click one to use it there. */
public final class TreeRouteScreen extends Screen {
	private static final int ROW = 24, PER_PAGE = 7;
	private int page;

	public TreeRouteScreen() {
		super(Component.literal("Tree Routes"));
	}

	@Override
	protected void init() {
		List<TreeRoutes.Route> routes = TreeRoutes.all();
		int pages = Math.max(1, (routes.size() + PER_PAGE - 1) / PER_PAGE);
		page = Math.min(page, pages - 1);
		int w = Math.min(340, width - 40), x = width / 2 - w / 2, y = 40;
		for (TreeRoutes.Route r : routes.subList(page * PER_PAGE, Math.min(routes.size(), (page + 1) * PER_PAGE))) {
			boolean inUse = TreeRoutes.activeOn(r.island) == r;
			Button b = Button.builder(Component.literal(label(r, inUse)), btn -> {
				RouteAssist.useRoute(r);
				rebuildWidgets();
			}).bounds(x, y, w, 20).build();
			b.active = !inUse;
			addRenderableWidget(b);
			y += ROW;
		}
		int by = height - 28;
		if (pages > 1) {
			addRenderableWidget(Button.builder(Component.literal("<"), b -> {
				page = (page + pages - 1) % pages;
				rebuildWidgets();
			}).bounds(width / 2 - 104, by, 20, 20).build());
			addRenderableWidget(Button.builder(Component.literal(">"), b -> {
				page = (page + 1) % pages;
				rebuildWidgets();
			}).bounds(width / 2 + 84, by, 20, 20).build());
		}
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(width / 2 - 50, by, 100, 20).build());
	}

	/** "✔ Helix route · Torrhus Canyon · 12 trees · best 1:15.7 · Taz". */
	private static String label(TreeRoutes.Route r, boolean inUse) {
		long trees = r.points.stream().filter(p -> p.kind() == TreeRoutes.Kind.TREE).count();
		return (inUse ? "✔ " : "") + r.name + " · " + Islands.name(r.island) + " · " + trees + " trees"
				+ (r.bestLapMs > 0 ? " · best " + TextUtil.formatTimeTenths(r.bestLapMs) : "") + (r.builtIn ? " · Taz" : "");
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		g.centeredText(font, title, width / 2, 10, TazTheme.RED);
		g.centeredText(font, "Click a route to use it on its island. Import your own from Skyblocker in /taz.", width / 2, 24, TazTheme.GREY);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(Taz.settingsScreen(null, "Foraging"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
