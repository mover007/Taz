package com.taz.foraging;

import com.taz.Taz;
import com.taz.render.WorldRenderer;
import com.taz.util.TazChat;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** A beam and pointer to a picked NPC or spot on Galatea (coordinates from the wiki). */
public final class IslandGuide {
	public enum Place {
		NONE("Nowhere", 0, 0, 0),
		// Moonglade Marsh
		AGATHA("Agatha: Starlyn Contests, shop", -648.5, 89, -3.5),
		SWOOP("Swoop: Sweep, Tree Gift milestones", -579.5, 114, -23.5),
		ROSEMARY("Rosemary: Forest Essence perks", -703.5, 162, 3.5),
		KYSHA("Kysha: Fusion House, shards", -601.5, 115, -12),
		JADE_DRAGON("Jade Dragon: Dragon's Lair", -640, 7, -22),
		// Torrhus Canyon
		TORRHUS_SPRINGS("Torrhus Springs: dive here for Ruby Veilshrooms", -646, 161, 200),
		DORY("Dory: how to reach the bottom of the Springs", -645.5, 161, 194.5),
		ANT("Ant: Ant's Cave, Tier XII minion stones", -615.5, 75, 208.5),
		HUNTER_MCNEIL("Hunter McNeil: Hotspot Haven", -652.5, 103, 167.5),
		MIRIA("Miria: Starlyn Contests, Safari Tickets", -654.5, 98, 220.5),
		HELIA("Helia: Torrhus Chapters", -690.5, 93, 155.5),
		HONEY("Honey: Tree Protection Agency", -627.5, 99, 226.5),
		BARTHOLOMEW("Beekeeper Bartholomew: Honeyhives", -666.5, 95, 166.5),
		DANIEL("Forager Daniel: Helix Trees", -636.5, 95, 245.5),
		FREDDY("Freddy: Hunting hints", -694.5, 94.5, 200.5),
		SANGER("Sanger: Elusive Shards", -632.5, 94, 160.5),
		MAXIM("Maxim: Booster upgrades", -574.5, 153, 248.5),
		ANDREA("Andrea: Safari Belt, milestones", -711.5, 108, 178.5),
		ARCHIE("Archie: Safari Essence shop", -706.5, 108, 165.5),
		SAFARI_MANAGER("Safari Manager: Critter Safari", -732.5, 99, 185.5);

		public final String label;
		public final Vec3 pos;

		Place(String label, double x, double y, double z) {
			this.label = label;
			this.pos = new Vec3(x, y, z);
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private static final double ARRIVED = 4.0;

	private IslandGuide() {
	}

	/** Every client tick: arriving clears the destination. */
	public static void tick(Minecraft mc) {
		IslandGuideConfig cfg = IslandGuideConfig.get();
		if (!cfg.enabled || cfg.destination == Place.NONE || mc.player == null || !Galatea.onIslands()) return;
		if (cfg.clearWhenReached && mc.player.position().distanceToSqr(cfg.destination.pos) <= ARRIVED * ARRIVED) {
			TazChat.info("You're at " + cfg.destination.label.split(":")[0] + ".");
			cfg.destination = Place.NONE;
			Taz.save();
		}
	}

	/** LevelRenderEvents.END_MAIN. */
	public static void render(LevelRenderContext context) {
		IslandGuideConfig cfg = IslandGuideConfig.get();
		if (!cfg.enabled || cfg.destination == Place.NONE || !Galatea.onIslands()) return;
		int color = cfg.argb();
		Place p = cfg.destination;
		try (WorldRenderer.Batch batch = WorldRenderer.begin(context)) {
			batch.line(p.pos.add(0, -1, 0), p.pos.add(0, 80, 0), (color & 0xFFFFFF) | 0xA0000000, 4.0f, true);
			batch.line(WorldRenderer.crosshair(batch.camera), p.pos.add(0, 1, 0), (color & 0xFFFFFF) | 0xC0000000, 2.0f, true);
			batch.label(p.pos.add(0, 2.2, 0), p.label + "  " + Math.round(Math.sqrt(p.pos.distanceToSqr(batch.camera))) + "m", color, true);
		} catch (Exception e) {
			Taz.LOGGER.error("Island guide failed", e);
		}
	}
}
