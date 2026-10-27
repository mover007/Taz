# Foraging (Galatea)

Features for Moonglade Marsh and Torrhus Canyon. Settings are in the **Foraging** and **Galatea** categories of `/taz`.

## Next Tree Pointer

A line from your crosshair and a label (`Next: Mangrove 23m (84 logs)`) point at the closest fully grown tree of the types you pick (Fig, Mangrove, Helix).

Taz groups touching logs into trees and watches them change:

- **Chopped:** logs were broken and haven't grown back, or the tree has a `FIG TREE 42%` hologram.
- **Regrowing:** logs are coming back.
- **Grown:** nothing changed for 3.5 seconds and the tree is untouched, regrown, or at least 97% of its full size.

It scans a few chunks per tick within the search radius (96 blocks by default) and only regroups trees whose logs changed. When the Tree Protection Agency fines you, that tree is remembered and never pointed at again. A tree Hypixel says is regenerating is skipped for 30 seconds.

The island comes from the tab list's `Area:` line, so keep that widget on.

## Tree Route Assist

Import a tree route from Skyblocker (Waypoints → Share → Export, then **Import from clipboard**). Point names say what they are: `3` is a tree, `E3` an etherwarp spot, `TP3` a teleport, anything else a stop.

- **Normal** mode shows the whole route with numbers and path lines.
- **Guide** mode shows one tree at a time, with the warps that get you there. The next tree comes up when this one is done or skipped (not grown, or regenerating).
- Laps are timed from the first tree, with a split per tree against your best.
- Restart and Skip keys, Export back to Skyblocker, and several routes per island.

## Honeyhives

In Torrhus Canyon, every honeyhive you can loot gets a box from anywhere on the island, and a pointer goes to the closest. Cosmetic hives are ignored (only real ones have a `Honeyhive` hologram). Refill timers are per player, so a hive counts as ready until you loot it, and each loot is remembered per profile until it refills.

Hive positions ship with Taz, and every hive found in game is added to `config/taz/foraging/honeyhives.json`.

## Whisper Counter

```
Whispers (counting)
Forest 209,597/h +1,240
Desert  12,677/h +75
Foraging 0:21
```

Forest and Desert Whispers per hour of foraging, read from the tab list totals. The clock only runs while you're foraging and pauses 3 seconds after you stop. Spending whispers, switching lobbies and Heart of the Forest refunds don't count as gains.

## More

- **Ruby Veilshroom Finder:** every Ruby Veilshroom in the Torrhus Springs caves, through the rock, with respawn timers (50-55 seconds) and a pointer.
- **Tree Progress:** the hologram of the tree you're chopping in a HUD, a `TIMBER!` title, and trees felled per hour.
- **Sweep Details:** Hypixel's Sweep Details chat block as a HUD, and a warning when you chop in the wrong order.
- **Tree Gifts & Loot:** gifts, bonus drops, `KARMA!`, mobs and honeycomb counted in a HUD; each 10-line `TREE GIFT` block becomes one line.
- **Starlyn Contests:** time left in the current contest, your live bracket, your last score and what you claimed.
- **Sea Lumies & Lushlilac:** boxes round sea pickle clumps and flowering azalea.
- **Hunting Mobs:** Hideonleaf, Hideonsun, Shellwise, Coralot and Blue Jays glow through walls. Phantoms can be muted.
- **Fairy Souls:** the souls you haven't found, with hints and a pointer.
- **Pressure Warning:** a title when Torrhus Springs' pressure reaches your limit.
- **Island Guide:** a beam and pointer to any Galatea NPC or spot.
