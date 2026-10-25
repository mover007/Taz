package com.taz.hud;

import com.taz.Taz;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Moves every Taz HUD (About > Move HUDs, or the Move HUDs key). Drag to move, scroll to resize,
 * right-click to hide or show, R to reset the one under the mouse, arrow keys to nudge the last one moved.
 */
public final class TazHudEditor extends Screen {
	private static final int OUTLINE = 0xFFFFAA00;
	private static final int OUTLINE_HIDDEN = 0xFF777777;
	private static final int OUTLINE_HOVER = 0xFFFFFF55;
	private static final int SNAP = 4;

	private record Shown(MovableHud hud, int x, int y, int w, int h) {
		boolean contains(double px, double py) {
			return px >= x && px < x + w && py >= y && py < y + h;
		}
	}

	private final List<Shown> shown = new ArrayList<>();
	private MovableHud dragging;
	private MovableHud selected;
	private double dragOffX, dragOffY;

	public TazHudEditor() {
		super(Component.literal("Taz HUD Editor"));
	}

	@Override
	protected void init() {
		int w = 110, gap = 4, y = height - 28;
		int x = width / 2 - (w * 3 + gap * 2) / 2;
		addRenderableWidget(Button.builder(Component.literal("Settings..."), b -> Taz.openSettings(null)).bounds(x, y, w, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Reset all"), b -> {
			for (MovableHud hud : TazHud.all()) hud.reset();
			Taz.save();
		}).bounds(x + (w + gap), y, w, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(x + (w + gap) * 2, y, w, 20).build());
	}

	/** Light dim (no blur, so the game stays visible), then the HUDs under the buttons and help text. */
	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		g.fill(0, 0, width, height, 0x50000000);
		shown.clear();
		for (MovableHud hud : TazHud.all()) {
			if (!hud.enabled()) continue;
			int[] b;
			try {
				b = hud.bounds(font, width, height, true);
				if (b == null) continue;
				hud.draw(g, font, true);
			} catch (Exception e) {
				Taz.LOGGER.error("HUD {} failed to draw in the editor", hud.name(), e);
				continue;
			}
			Shown s = new Shown(hud, b[0], b[1], b[2], b[3]);
			shown.add(s);
			if (hud.hidden()) g.fill(s.x(), s.y(), s.x() + s.w(), s.y() + s.h(), 0x90202020);
			boolean hover = s.contains(mouseX, mouseY) || hud == dragging;
			int color = hover ? OUTLINE_HOVER : (hud.hidden() ? OUTLINE_HIDDEN : OUTLINE);
			border(g, s, color);
			String label = hud.name() + (hud.hidden() ? " (hidden)" : "") + (hover ? String.format("  %.0f%%", hud.scale() * 100) : "");
			int labelY = s.y() >= 11 ? s.y() - 10 : s.y() + s.h() + 2;
			g.text(font, label, s.x(), labelY, color, true);
			// Text is drawn after boxes within a stratum, so give each HUD its own or overlapping ones bleed through.
			g.nextStratum();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		String help1 = "Drag to move, scroll to resize";
		String help2 = "Right-click to hide/show, R to reset, arrows to nudge";
		int w = Math.max(font.width(help1), font.width(help2)) + 8;
		// Out of the way while you drag or point at a HUD under it.
		if (dragging != null || mouseY < 44 && at(mouseX, mouseY) != null) return;
		g.fill(width / 2 - w / 2, 5, width / 2 + w / 2, 41, 0xA0000000);
		g.centeredText(font, "Taz HUD Editor", width / 2, 8, 0xFFFFAA00);
		g.centeredText(font, help1, width / 2, 19, 0xFFAAAAAA);
		g.centeredText(font, help2, width / 2, 30, 0xFFAAAAAA);
	}

	private static void border(GuiGraphicsExtractor g, Shown s, int color) {
		int x0 = s.x() - 1, y0 = s.y() - 1, x1 = s.x() + s.w() + 1, y1 = s.y() + s.h() + 1;
		g.fill(x0, y0, x1, y0 + 1, color);
		g.fill(x0, y1 - 1, x1, y1, color);
		g.fill(x0, y0, x0 + 1, y1, color);
		g.fill(x1 - 1, y0, x1, y1, color);
	}

	/** The topmost HUD under the mouse (the last one drawn). */
	private Shown at(double x, double y) {
		for (int i = shown.size() - 1; i >= 0; i--) if (shown.get(i).contains(x, y)) return shown.get(i);
		return null;
	}

	private Shown shownOf(MovableHud hud) {
		for (Shown s : shown) if (s.hud() == hud) return s;
		return null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		Shown s = at(event.x(), event.y());
		if (s == null) return false;
		if (event.button() == 0) {
			dragging = s.hud();
			selected = s.hud();
			dragOffX = event.x() - s.x();
			dragOffY = event.y() - s.y();
			return true;
		}
		if (event.button() == 1) {
			s.hud().setHidden(!s.hud().hidden());
			Taz.save();
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging == null) return super.mouseDragged(event, dx, dy);
		Shown s = shownOf(dragging);
		if (s == null) return true;
		double nx = clamp(event.x() - dragOffX, 0, width - s.w());
		double ny = clamp(event.y() - dragOffY, 0, height - s.h());
		// Snap to screen edges and the centre line.
		if (nx < SNAP) nx = 0;
		if (width - s.w() - nx < SNAP) nx = width - s.w();
		if (Math.abs(nx + s.w() / 2.0 - width / 2.0) < SNAP) nx = width / 2.0 - s.w() / 2.0;
		if (ny < SNAP) ny = 0;
		if (height - s.h() - ny < SNAP) ny = height - s.h();
		dragging.moveTo((int) Math.round(nx), (int) Math.round(ny), width, height);
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging != null) {
			dragging = null;
			Taz.save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		Shown s = at(mouseX, mouseY);
		if (s == null || scrollY == 0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		float next = s.hud().scale() + (scrollY > 0 ? 0.1f : -0.1f);
		s.hud().setScale(Math.round(next * 20f) / 20f);
		selected = s.hud();
		Taz.save();
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		Shown target = selected != null ? shownOf(selected) : null;
		switch (event.key()) {
			case GLFW.GLFW_KEY_R -> {
				Shown hover = at(mouseX(), mouseY());
				Shown s = hover != null ? hover : target;
				if (s == null) return true;
				s.hud().reset();
			}
			case GLFW.GLFW_KEY_LEFT -> nudge(target, -1, 0);
			case GLFW.GLFW_KEY_RIGHT -> nudge(target, 1, 0);
			case GLFW.GLFW_KEY_UP -> nudge(target, 0, -1);
			case GLFW.GLFW_KEY_DOWN -> nudge(target, 0, 1);
			default -> {
				return super.keyPressed(event);
			}
		}
		Taz.save();
		return true;
	}

	private void nudge(Shown s, int dx, int dy) {
		if (s != null) s.hud().moveTo(s.x() + dx, s.y() + dy, width, height);
	}

	private double mouseX() {
		return minecraft.mouseHandler.xpos() * width / minecraft.getWindow().getScreenWidth();
	}

	private double mouseY() {
		return minecraft.mouseHandler.ypos() * height / minecraft.getWindow().getScreenHeight();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void removed() {
		Taz.save();
		super.removed();
	}

	private static double clamp(double v, double lo, double hi) {
		return hi < lo ? lo : Math.max(lo, Math.min(hi, v));
	}
}
