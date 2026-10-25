package com.taz.general;

import com.taz.hud.TazTheme;
import com.taz.inventory.ItemPrices;
import com.taz.mixin.AbstractSignEditScreenAccessor;
import com.taz.util.Items;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.Set;

/**
 * Auction Price Input: when you set an auction's price, a proper text box instead of Hypixel's sign. Type "10m", "2.5k" or
 * "1b"; Undercut mode prices it that much under the lowest BIN. Enter also presses Create and Confirm in the auction menus.
 * From NoammAddons' Auction Price Input.
 */
public final class AuctionInput {
	private static final Set<String> CREATE = Set.of("Create BIN Auction", "Create Auction");
	private static final Set<String> CONFIRM = Set.of("Confirm BIN Auction", "Confirm Auction");
	private static ItemStack item;
	private static boolean undercut;
	private static String lastText = "";

	private AuctionInput() {
	}

	/** You clicked a slot: the price button (31) in the create menu means the sign is next. */
	public static void onSlotClick(AbstractContainerScreen<?> screen, int slot) {
		if (slot == 31 && CREATE.contains(screen.getTitle().getString())) {
			ItemStack s = screen.getMenu().getSlot(13).getItem();
			item = Items.id(s).isEmpty() ? null : s.copy();
		}
	}

	/** A key in a menu: Enter presses Create or Confirm. True when it did. */
	public static boolean onKey(AbstractContainerScreen<?> screen, int key) {
		AuctionInputConfig cfg = AuctionInputConfig.get();
		if (!cfg.enabled || (key != GLFW.GLFW_KEY_ENTER && key != GLFW.GLFW_KEY_KP_ENTER)) return false;
		String title = screen.getTitle().getString();
		int slot = CREATE.contains(title) ? 29 : CONFIRM.contains(title) ? 11 : -1;
		if (slot < 0 || slot >= screen.getMenu().slots.size()) return false;
		ItemStack button = screen.getMenu().getSlot(slot).getItem();
		if (!button.is(net.minecraft.world.item.Items.GREEN_TERRACOTTA)) return false;
		Minecraft mc = Minecraft.getInstance();
		mc.gameMode.handleContainerInput(screen.getMenu().containerId, slot, 0, ContainerInput.PICKUP, mc.player);
		return true;
	}

	/** A screen opened: Hypixel's price sign becomes Taz's box. */
	public static void onScreen(Screen screen) {
		AuctionInputConfig cfg = AuctionInputConfig.get();
		if (!cfg.enabled || !(screen instanceof AbstractSignEditScreen sign) || item == null) return;
		String[] lines = ((AbstractSignEditScreenAccessor) sign).taz$lines();
		if (lines.length < 4 || !lines[1].equals("^^^^^^^^^^^^^^^") || !lines[2].equals("Your auction") || !lines[3].equals("starting bid")) return;
		BlockPos pos = ((AbstractSignEditScreenAccessor) sign).taz$sign().getBlockPos();
		Minecraft mc = Minecraft.getInstance();
		String[] original = lines.clone();
		ItemStack stack = item;
		mc.execute(() -> mc.setScreen(new Box(pos, original, stack)));
	}

	/** "10m" -> 10000000, "2.5k" -> 2500, "1,000" -> 1000; null when it isn't a number. */
	public static Long parse(String text) {
		String t = text.trim().toLowerCase(Locale.ROOT).replace(",", "");
		if (t.isEmpty()) return null;
		double mult = 1;
		char last = t.charAt(t.length() - 1);
		if (last == 'k' || last == 'm' || last == 'b' || last == 't') {
			mult = switch (last) {
				case 'k' -> 1e3;
				case 'm' -> 1e6;
				case 'b' -> 1e9;
				default -> 1e12;
			};
			t = t.substring(0, t.length() - 1);
		}
		try {
			return Math.round(Double.parseDouble(t) * mult);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static final class Box extends Screen {
		private final BlockPos pos;
		private final String[] original;
		private final ItemStack stack;
		private final long lowestBin;
		private EditBox field;
		private Long value;

		Box(BlockPos pos, String[] original, ItemStack stack) {
			super(Component.literal("Auction Price"));
			this.pos = pos;
			this.original = original;
			this.stack = stack;
			var prices = ItemPrices.get();
			this.lowestBin = prices == null ? 0 : Math.round(prices.market(ItemPrices.key(stack)));
			if (!AuctionInputConfig.get().rememberMode) undercut = AuctionInputConfig.get().undercutDefault;
		}

		@Override
		protected void init() {
			int cx = width / 2, cy = height / 2;
			field = new EditBox(font, cx - 100, cy - 20, 200, 20, Component.literal("Price"));
			field.setMaxLength(32);
			field.setValue(AuctionInputConfig.get().rememberText ? lastText : "");
			field.setResponder(t -> {
				lastText = t;
				recalc();
			});
			addRenderableWidget(field);
			setInitialFocus(field);
			addRenderableWidget(Button.builder(Component.literal("Done"), b -> finish()).bounds(cx - 100, cy + 8, 200, 20).build());
			addRenderableWidget(Button.builder(Component.literal(modeText()), b -> {
				undercut = !undercut;
				b.setMessage(Component.literal(modeText()));
				recalc();
			}).bounds(cx - 100, cy + 32, 200, 20).build());
			recalc();
		}

		private String modeText() {
			return "Mode: " + (undercut ? "Undercut" : "Normal");
		}

		private void recalc() {
			Long v = parse(field.getValue());
			value = v == null ? null : undercut ? Math.max(0, lowestBin - v) : v;
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			int cx = width / 2, cy = height / 2;
			g.fill(0, 0, width, height, 0xC0000000);
			g.item(stack, cx - 8, cy - 78);
			TazTheme.outlined(g, font, Component.literal(undercut ? "Undercut Mode" : "Set Auction Price"), cx - font.width(undercut ? "Undercut Mode" : "Set Auction Price") / 2,
					cy - 56, TazTheme.RED);
			String bin = "Lowest BIN: " + (lowestBin > 0 ? String.format("%,d", lowestBin) : "unknown");
			g.text(font, bin, cx - font.width(bin) / 2, cy - 44, TazTheme.PALE_RED, true);
			String shown = value != null ? "Price: " + String.format("%,d", value) : field.getValue().isEmpty() ? "Type a price (10m, 2.5k)" : "Not a number";
			g.text(font, shown, cx - font.width(shown) / 2, cy - 32, value != null ? TazTheme.WHITE : TazTheme.GREY, true);
			super.extractRenderState(g, mouseX, mouseY, delta);
		}

		@Override
		public boolean keyPressed(KeyEvent event) {
			if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
				finish();
				return true;
			}
			return super.keyPressed(event);
		}

		private void finish() {
			Minecraft mc = Minecraft.getInstance();
			String line = value != null ? String.valueOf(value) : field.getValue();
			if (mc.getConnection() != null) mc.getConnection().send(new ServerboundSignUpdatePacket(pos, true, line, original[1], original[2], original[3]));
			item = null;
			onClose();
		}
	}
}
