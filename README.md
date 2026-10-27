![Taz](branding/taz-github-social-1280x640.png)

# Taz

mTaz's Hypixel SkyBlock features in one client-side Fabric mod for **Minecraft 26.1.2**, with a SkyHanni-style settings GUI (`/taz`).

| Category | What it does |
|---|---|
| **General** | Alerts (rare drops, bazaar orders, sold auctions, party invites, low health), a Dark Auction reminder, a chat filter, command keys, event timers, a skill XP tracker, waypoints from coordinates in chat, a Performance HUD (FPS, TPS, ping), Pet Display, Ragnarock alerts, Spring Boots height, a Kick Timer, an Etherwarp landing guess, the Gyro Wand's range, Chat Commands, Chat tweaks, Command Shortcuts, Slot Binds, Loadout Keybinds, Auto Sprint, No Cursor Reset, Camera, Render Optimizer, Held Item, Arrow Fix, Block Overlay, Sound Volumes, Auction Price Input, a Storage Overlay and Dark Mode. |
| **Inventory** | Lowest BIN and bazaar prices in tooltips, rarity colours behind items, and numbers on items (pet level, book level, minion tier ...). |
| **Slayer** | Boss spawned alert, a box around your own boss, and kill times. |
| **Fishing** | Bite alert, bobber timer, Double Hook alert. |
| **Dungeons** | Run timer and end-time prediction for every Catacombs floor (tuned on M6/M7), live score, splits, PBs, chat and party messages. Secret Routes: Odin waypoint packs for every room, with a guide mode that shows the next step and times each room. Room Secret Times in party chat, Skill Issue: a joke when someone dies, Blessings, Mask Timers, Score Alerts (270/300, Mimic, Prince, Bat), Room Alerts, the Terracotta Timer, a Dungeon Map, Map Info, Auto Requeue, a Leap Menu, Party Finder stats and Auto Kick, Starred Mobs, Doors & Keys, Secret Clicked, Blood Camp, Mage Beam and Puzzle Solvers. |
| **Floor 7** | Tick Timers (Maxor's start, Storm's pads and purple pad, Goldor's death ticks), a Leap Counter, Terminal Highlight, Door Fix, a Terminal Solver, Simon Says, Arrow Align and Wither Dragons. |
| **Galatea** | Points at the closest fully grown Fig, Mangrove or Helix tree (pick which types) with a line and a label, so when a tree falls you can go straight to the next. |
| **Honeyhives** (Galatea category) | Highlights the Torrhus Canyon honeyhives you can loot right now, with refill timers and a pointer to the next one. Cosmetic hives are ignored. |
| **Whisper Counter** (Galatea category) | Forest and Desert Whispers per hour in a small HUD, counted only while you're foraging (it pauses 3 seconds after you stop). |
| **More Galatea** | Ruby Veilshroom finder (through rock, respawn timers), tree progress and TIMBER! alert, Sweep Details HUD with a wrong-order warning, Sea Lumies and Lushlilac highlights, hunting mob glows (Hideonleaf, Hideonsun, Shellwise, Coralot, Blue Jay), muted Phantoms, Fairy Soul waypoints and an island guide to every NPC. |
| **SkyBlock XP** | The cheapest SkyBlock XP upgrades you haven't done, a level plan route, a HUD, and an NPC tracker that highlights NPCs through walls. |

## Install

1. Fabric Loader 0.19.5+, **Fabric API** for 26.1.2 and **Fabric Language Kotlin** (the settings GUI library needs it; SkyHanni needs it too, so you likely have it).
2. Put `build/libs/taz-<version>.jar` (like `taz-1.1.0.jar`) in `mods`, and remove any older `taz-*.jar` from there. Don't use the `-slim` jar; it is missing the settings GUI.
3. **Remove** `dungeon-run-predictor-*.jar` and `skyblock-xp-assist-*.jar`. Taz replaces both, and the game won't start with them installed together.

On first start Taz copies everything from the old mods: run history, triggers and boss lines from `config/dungeonpredictor/`, XP progress and caches from `config/skyblockxpassist/`, and every setting (HUD positions, toggles, API key). The old folders aren't changed.

## Settings

`/taz` is the only command, and it opens the settings GUI (so do Mod Menu and an optional key). **Everything is in there**: every setting, keybind and action of every feature. Categories on the left are areas of the game, and a big feature (the Run Predictor, Secret Routes, Puzzle Solvers, the Dungeon Map, the Critter Safari) has a category of its own; otherwise **each feature is one collapsed dropdown** in its category, with **Enabled** as its first option, so the menu stays short as features are added. **Every feature starts off**: turn on the ones you want. It works like SkyHanni's: categories on the left, a search bar, and a description for every option. It reopens where you left it: the same category, scrolled to the same spot. It is [MoulConfig](https://github.com/NotEnoughUpdates/MoulConfig), the same library SkyHanni uses, bundled inside the jar.

- **About:** a key to open the menu, and **Move HUDs** for the HUD editor.
- **General:** Alerts, Dark Auction Reminder, Event Timers, Skill Tracker, Pet Display, Ragnarock, Spring Boots, Kick Timer.
- **Chat:** Chat Filter, Chat Commands, Chat, Chat Waypoints, Command Shortcuts.
- **Controls:** Command Keys, Slot Binds, Loadout Keybinds, Auto Sprint, No Cursor Reset, Camera.
- **Visuals:** Performance HUD, Render Optimizer, Held Item, Block Overlay, Dark Mode, Sound Volumes, Arrow Fix, Etherwarp, Gyro Wand.
- **Inventory:** Item Prices, Item Overlays, Storage Overlay, Auction Price Input.
- **Dungeons:** Room Secret Times, Skill Issue, Blessings, Mask Timers, Score Alerts, Room Alerts, Terracotta Timer, Starred Mobs, Doors & Keys, Secret Clicked, Blood Camp, Mage Beam, Auto Requeue, Leap Menu, Party Finder. **Room Secret Times:** Enabled, Party chat, Your chat, Reset best times. **Skill Issue:** Enabled, Tone (toxic / friendly), Where (party chat / only me), excuses for your deaths, Try it. See [Room Secret Times and Skill Issue](#room-secret-times-and-skill-issue).
- **Run Predictor** (its own category): Enabled, Move HUD, Stats, Simple / Everything-on presets, Timer HUD, Score, Chat, Party Chat, **After a run** (how long the HUD stays) and **History** (delete one floor's recorded runs).
- **Secret Routes** (its own category): Enabled, Mode (Normal / Guide), Taz's routes, Import from clipboard, Import from Odin, Export, List routes, Remove imported routes, reach distance, show the next step, show hints, path line, Skip and Restart room keys, Reset best times, Move HUD. See [Secret Routes](#secret-routes).
- **Puzzle Solvers** and **Dungeon Map** (Dungeon Map, Map Info): their own categories.
- **Floor 7:** Tick Timers, Leap Counter, Terminal Highlight, Door Fix, Terminal Solver, Simon Says, Arrow Align, Wither Dragons.
- **Slayer → Slayer Helper** and **Fishing → Fishing Helper**.
- **Foraging:** **Next Tree Pointer** (Enabled, **Fig / Mangrove / Helix**, search radius, a colour per type, Tree status), Tree Route Assist, Tree Progress, Sweep Details, Tree Gifts & Loot, **Whisper Counter** (Enabled, show Forest / Desert, pause after, Reset counter, Move HUD), Starlyn Contests.
- **Galatea:** **Honeyhives** (Enabled, next hive pointer, refill timers, through walls, colour, Hive status), Ruby Veilshroom Finder, Pressure Warning, Sea Lumies & Lushlilac, Hunting Mobs, Fairy Souls, Island Guide. See [More Galatea](#more-galatea).
- **Critter Safari** (its own category): Enabled, then **Highlights**, **Route**, **Floor drops & mounds**, **Guide & tips**, **Alerts** and **Your catches** sections. See [Critter Safari](#critter-safari).
- **SkyBlock XP → XP Assist:** Enabled (turns off the list, HUD, NPC tracker, progress reading and downloads), open the list / plan / NPC finder, next upgrades in chat, stop tracking, **Keybinds** (XP list = K, level plan, NPC finder, toggle HUD, stop tracking), Profile (API key, EliteBot), HUD, XP List (pricing), NPC Tracker, **Progress** (refresh, how to sync, unhide, forget seen progress).

Changing the API key or profile re-downloads your XP data by itself; changing pricing options recalculates the list. Buttons that delete data (Delete floor history, Forget seen progress) need a second click within 5 seconds.

Everything is saved in `config/taz/config.json`, **by itself**: a change is written within a second, even with the settings GUI still open, and again when you leave a server or quit, so you never set anything up twice. Taz also keeps `config.json.last-good` (the last file that loaded fine); if `config.json` is ever damaged (the game killed mid-write, a bad hand edit), Taz puts the good copy back instead of starting from defaults, keeps the damaged one as `config.json.broken-<time>`, and tells you in chat.

## Chat

Everything Taz says in chat starts with **[TAZ]**: its own lines (only you see them) and what features say in party chat (Run Predictor end times, shared coordinates, Room Secret Times, Skill Issue). Party messages go out one at a time: Hypixel quietly drops a message sent in the same moment as another (another mod's mask proc message, say), so Taz waits until nothing was sent for a moment, and sends a message again once if Hypixel doesn't show it back in party chat within 2 seconds.

### Chat buttons

Some chat messages have clickable buttons ([Track], [Stop], [Bazaar], [AH], [Recipe], [Open list]). They run `/taz track`, `/taz untrack`, `/taz bz`, `/taz ah`, `/taz recipe` and `/taz list`, so you never need to type them.

## Dungeons

Each run is split into phases from chat (blood rush, blood, clear, portal, and the F6/M6 and F7/M7 boss phases). From the portal on, the end time is the **median of the time left from the same point in your past runs**, weighted towards recent runs, the same teammates, the same number of deaths, and (F7/M7) a similar portal time. Everything is learned in server ticks, and the estimate is stretched by how much the server has lagged over the run so far (not by a moment's hiccup, like the warp into the boss). Past runs are rebuilt from Minecraft's chat logs on every start, so it knows your runs from before it was installed.

Portal estimate accuracy on the author's runs: **M6 ±2.1s** (91% within 5s), M7 ±23s at the portal (boss deaths and dragons are most of that), **±7s once the Core opens**. The Stats button shows your own numbers.

Files in `config/taz/dungeons/`: `history.json` (runs), `triggers.json` (chat triggers), `secret-routes/` (imported route packs, as Odin files) and `secret-times.json` (each room's best time).

## Secret Routes

Secret waypoints for every Catacombs room, in [Odin](https://github.com/odtheking/Odin)'s dungeon waypoint format, so any Odin pack works. Taz finds the room you're in and how it's turned the way Odin does: each 31-block tile of the dungeon grid is recognised by a hash of the block column at its centre (Odin's room table), and a room's waypoints are stored relative to the blue terracotta corner of its roof, turned to face north. A waypoint lands on the same block in Taz as in Odin.

- **Taz's routes:** Taz comes with a full set (3ppopka's Odin routes, 139 rooms). Turn them off to use only yours.
- **Import from clipboard:** an Odin export (`H4sIA...`, Base64 of gzipped JSON), its plain JSON, or a link to either. **Import from Odin** copies the packs from your Odin install (`config/odin/dungeon-waypoints`). A room in a pack you import uses yours; the newest import wins.
- **Export:** every route in use as an Odin export, to share or load into Odin.
- **Normal** mode draws every waypoint in the room as Odin does (its colour, box size, title; see-through unless the waypoint has depth on).
- **Guide** mode follows the route. It always starts at the red start (`Start · stand on the red block`), then the waypoints titled with a number (`1`, `2 down`, `4 item`, `17 | done`), in order. Only the next step shows, bright, with a line from your crosshair, its distance and what to do there (`Step 4/7 Etherwarp · 6m`, `item · pick up the item`), plus the one after it faintly (`then 5 down`). Waypoints without a number are hints (`if 4 locked`, aim spots, princes, notes) and show faintly nearby. Every waypoint goes away once it's done, steps and hints alike: a secret when it's taken by anyone (a chest's lid opens, a lever flips, an essence skull goes, an item is picked up next to it, a bat dies near it, or the room's `3/7 Secrets` count goes up next to it), a block to mine or blow up (`mine`, `line`, `boom`, `breakwarp`) when it breaks, and a spot when you arrive on it (or on one of the next two, for a step someone else did). A spot that says `bat` or `item` also waits for that secret. The Skip key ticks a step off by hand. Each room is timed from its first secret to its last step (`Mossy done in 0:11.2 (new best, -0.9)`), and your progress lasts the whole run. The Restart room key sends you back to the start. Each room you enter and each waypoint done is written to the game log (`[Secret Routes] ...`). Rooms without a red start (puzzles, Blood, Entrance) start at step 1 when you walk in.

The routes follow 3ppopka's legend, which Taz reads for any pack (an Odin waypoint type, when set, wins over the colour):

| Colour | Means |
|---|---|
| Red | Start point |
| Purple | Etherwarp to this block (see-through: text to read) |
| Green | Chest, lever or wither essence |
| Yellow | Where to aim a normal teleport |
| Aqua | Prince |

| Note | Means |
|---|---|
| `bat` | Stand on this block and kill the bat with Hyperion |
| `just tp`, `normal` | Aim here and right-click to teleport (not etherwarp) |
| `down`, `forward`, `up`, ... | Which way to move or mine |
| `line` | Mine only the line (usually with a normal teleport) |
| `breakwarp` | Teleport normally, break the block, then etherwarp |

## Galatea next-tree pointer

On Galatea (Moonglade Marsh) and Torrhus Canyon, a line from your crosshair and a `Next: Mangrove 23m (84 logs)` label point at the closest **fully grown** tree of the types you picked. Trees themselves aren't highlighted.

Tree logs are real blocks (Fig = stripped spruce wood, Mangrove = mangrove wood, Helix = stripped birch + stripped mangrove wood), so Taz groups touching logs into trees and watches them change:

- **Chopped:** logs were broken (by anyone) and haven't grown back, or a `FIG TREE 42%` progress hologram is on it.
- **Regrowing:** logs are coming back right now.
- **Grown:** nothing has changed for 3.5s and the tree was never touched, grew back after being chopped, or is still at least 97% of its grown size.

It only reads chunks within the search radius (default 96 blocks), a few per tick, and when logs change it regroups only the trees they touch. In a stress test with ~35,000 logs being chopped every tick, it takes under 1 ms in the worst tick (it was ~17 ms before).

A tree that was already half-chopped before you arrived looks grown until someone touches it or its hologram shows. The island comes from the tab list's `Area:` line, so keep that widget on.

## Honeyhives

In Torrhus Canyon, every honeyhive you can loot right now gets a box (gold by default) **from anywhere on the island**, and a line from your crosshair plus `Next hive: 23m` points at the closest one. Far away, each cluster gets one label (`3 honeyhives ready`, or `Refill in 34m`).

- **Known in advance:** hives sit in 8 clusters of 2-3. Their positions ship with Taz (`assets/taz/foraging/honeyhives.json`), and every hive found in game is added to `config/taz/foraging/honeyhives.json` for all profiles. A cluster with no known hive yet gets a beam and `Honeyhives here (walk here once to map them)`; once you've been near it, its hives are exact for good.
- **Only real hives:** each lootable hive has a `Honeyhive` hologram over it; cosmetic hives don't, so they're never highlighted.
- **Ready or not:** a hive is ready when its hologram has no `Refill in: 59m 46s` line (it says `Click to loot!`). The hive is found as the block under the hologram, whatever block it is.
- **Ready unless you looted it:** refill timers are per player, so a hive counts as ready until you loot it. Each loot is remembered per profile until its time is up (the hologram corrects the time when you're near), also after you leave and come back.
- **Chat:** your `You stick your hand into the honeyhive...` loot message and `QUEEN BEE! The Honeyhive instantly refilled with loot!` update the hive you're at straight away.

## More Galatea

Moonglade Marsh and Torrhus Canyon, from the 0.27 patch notes, the wiki and the chat lines in your own game logs. The Torrhus ones work out which island you're on from your position (Torrhus Canyon is the part past z 100).

- **Tree Route Assist:** your tree route, imported from Skyblocker: copy it there (Waypoints, Share, Export: `[Skyblocker-Waypoint-Data-V1]...`) and press **Import from clipboard**. A route only shows on its island (Skyblocker's island ids, `foraging_3` = Torrhus Canyon); one made for no island gets the island you're standing on, or, on Galatea, the island its points are on. Point names say what they are: `3` is a tree, `E3` an etherwarp spot, `TP3` a teleport, anything else a stop (`Miria`). **Normal** mode shows the whole route, numbered, with path lines. **Guide** mode goes one tree at a time: only the next tree shows (box, beam, `Tree 4 · 35m`), with the warps and teleports that get you there numbered on a path from your crosshair (`1. Etherwarp E4`, `2. Etherwarp E5`); each ticks off when you reach it. At the tree it says `Chop Tree 4` and shows where to go after (`then Etherwarp E5`); the next tree comes up when this one is done (your `You helped cut 100% of the Fig Tree.` or `TIMBER!` message, or its logs gone) or when you leave it. A tree that isn't grown when you get there, or says it's regenerating, is skipped by itself (`Skipped Tree 4 (not grown)`). **End stops after the last tree** (off by default) turns the teleports and stops after your last tree (`TP1`..., `Miria`) on or off; off, the lap ends at the last tree and starts straight over at Tree 1. Laps are timed from the first tree, with a split at every tree against your best (`Tree 3 -1.2s`). Best laps are kept per route. Restart and Skip keys, Export back to Skyblocker's format, several routes per island (Next route here).
- **Pressure Warning:** a `PRESSURE 85%` title when Torrhus Springs' `Pressure: N%` (action bar) reaches your limit (80% by default), again after you swim back up.
- **Ruby Veilshroom Finder:** Ruby Veilshrooms are crimson fungus blocks in the caves at the bottom of Torrhus Springs (dive down at -646 161 200, past Hotspot Haven and Ant's Cave). The client has the whole cave in its chunks, so Taz finds every one **from anywhere on the island, through the rock**, boxes it, and points at the closest (`Next veilshroom: 23m`). A picked one shows `Respawns in ~37s` (they come back after 50-55 seconds) and, with none ready, the pointer goes to the one back soonest. Before you've ever found one, a beam marks where to dive. Every spot seen is kept in `config/taz/foraging/veilshrooms.json`. HUD: ready / respawning, picked (each gives 4) and per hour.
- **Tree Progress:** the `FIG TREE 37%` hologram of the tree you're chopping in a HUD, a `TIMBER!` title when `TIMBER! You felled the entire Fig Tree!` comes in, and trees felled per type and per hour of chopping.
- **Sweep Details:** Hypixel's `Sweep Details` chat block (sweep, tree toughness, logs, axe-throw and wrong-style penalties) as a HUD instead of chat, and a `WRONG ORDER -50%` title with the right order (Fig: trunk first; Mangrove: branches, trunk, roots; Helix: beige before red).
- **Sea Lumies & Lushlilac:** boxes round Sea Lumies (sea pickles in Murkwater Shallows / Depths, only clumps of 3+ by default; they give Foraging XP) and Lushlilac (flowering azalea on the Reaches and Verdant Summit), with an optional pointer.
- **Hunting Mobs:** Hideonleaf (green shulkers), Hideonsun (yellow / orange / brown shulkers), Shellwise (turtles), Coralot (axolotls) and Blue Jays (blue parrots) glow through walls, each in its own colour. Phantoms can be muted on Galatea.
- **Fairy Souls:** the 12 Moonglade Marsh and 12 Torrhus Canyon souls you haven't found, with a beam, a hint (`Behind a waterfall`) and a pointer to the closest. `SOUL! You found a Fairy Soul!` ticks off the one you're at (per profile, in `config/taz/foraging/fairysouls.json`); Mark all found / Forget found buttons.
- **Tree Gifts & Loot:** counts Tree Gifts per tree, Bonus Gifts, `KARMA!` double loot, mobs that fall out (`A Phanpyre fell from the Tree!`), honeyhive Honeycomb (`HIVE! You found Honeycomb x30 ...`) and today's HOTF Lottery buff (`New buff: Gain +50 Fig Fortune.`) in a HUD. Each 10-line `TREE GIFT` block becomes one line (`TREE GIFT Fig 100% · +5 rewards · KARMA x2 · BONUS Hummingbird Shard (0.15%) · Phanpyre!`, rewards on hover), a Bonus Gift drop at or below 1% gets a title, and `You cannot damage a tree while it is regenerating!` is hidden.
- **Starlyn Contests:** a new contest runs every SkyBlock day and ends 30 seconds before the day does (at :14:30, :34:30 and :54:30; the sidebar's `Agatha's Contest 14m55s` is used when it's showing), so the HUD shows `Agatha's Contest 12:34 left` (Miria on Torrhus Canyon) with your live bracket from the sidebar (`RARE with 1.2k · Epic requires +1.3k`), your last score from `You earned a total of 4,999 points!` with its bracket and how far the next is, and what you claimed this session. The `STARLYN CONTEST REWARDS CLAIMED` blocks become one line: `Claimed 2 Agatha contests: 45 Agatha's Coupon, 17,500 Forest Whispers, ...`. Optional alert a minute before a contest ends.
- **Protected trees (Next Tree Pointer):** when the Tree Protection Agency fines you 1,000 coins, a `PROTECTED TREE` title comes up and the tree you hit is remembered for good (`config/taz/foraging/protected-trees.json`): the pointer never sends you there again. A tree Hypixel says is still regenerating is skipped for 30 seconds.
- **Island Guide:** pick an NPC or spot (Agatha, Swoop, Rosemary, Kysha, Jade Dragon, Torrhus Springs, Dory, Ant, Hunter McNeil, Miria, Helia, Honey, Beekeeper Bartholomew, Forager Daniel, Freddy, Sanger, Maxim, Andrea, Archie, Safari Manager) and get a beam and pointer there; it clears when you arrive.

## Critter Safari

New to the Safari? Turn on the **Critter Safari** category and Taz walks you through it.

- **Getting in:** near the Critter Safari entrance in Torrhus Canyon, a HUD lists what you need (Hunting XV and a Safari Ticket, won in Miria's Starlyn Contests), whether you're carrying a ticket, and a beam points to the Safari Manager, who takes it. Taz also says once in chat how it works: go with 3-4 players and take one biome each (Forest, Cavern, Haunted, Icy).
- **Highlights:** right-click a Critter Capsule to throw it. Rarer critters can escape: a Common one is always caught, a Rare one within 3 throws, a Legendary one within 20. The critters you still need **glow red** with a red name tag and how far away they are (`Foxtrot ✦ new 23m`, where "new" means you've never caught one). A Sparkling one glows gold and gets a title. **Highlight** picks which critters glow: the ones nobody in your party has caught this run (the default; each new one is +5,000 Hunting XP), the ones you've never caught yourself, or all of them.
- **Last seen:** when a critter you need goes out of view, a faint box and name stay where it was last seen (`Foxtrot - last seen 25s ago`) for 90 seconds, or until you get there and it isn't, and the route can take you back to it.
- **Guide HUD:** how many of the 37 are caught, the time left (the Safari closes about an hour after it opens), your capsules and Hunting Hotspot, the critters left in your biome with how to find each one (`Macaw - feed the Birdfeeder a Bag of Seeds`), what's left in the other biomes, and what each quest item you carry is for. What's caught takes no room: done biomes and quest items for critters already caught are left out. **Countdowns** show while something's going on, with timings from real runs: a Shyworm up (about 20 seconds, `Shyworm up 0:14 - don't let it see you`), the Wumpa waking (15-25 seconds after its footsteps), how long Hideyho has been hiding (the faster you find him, the more shards), a Scrappy out of its shell and the Doomspiral.
- **Tips in chat:** a welcome that explains capsules, floor drops and splitting up, and each biome's tricks the first time you walk in. Taz also tells you your hotspot and what a quest item is for when you pick it up (`You have a Bag of Seeds: feed it to the Birdfeeder in the Forest`). It warns when capsules run low or out, says when a biome is done and where to go next, and when 10 and 2 minutes are left.
- **Show the way:** a route through the whole run, one biome at a time, drawn as a path along the Safari's walkways like SkyHanni's navigation. The HUD shows the next step, like `Next: Honeybug - punch the bee nest 23m`. In the biome it's working, the route goes, nearest first:
  1. To a critter of that biome you still need that's in view (or where one was last seen), with how to catch it (`Bloodbat - 1 throw wakes it, the next catches it`). Other biomes' critters still glow, but the route leaves them for their own biome's turn. A Scrappy's armour stops capsules until it's had its food 4 times. Each Safari that food is one of Wholesale Wheat, Flavor-packed Fish (the Cavern fish pond) or Lush Lily Pads (the Cavern's lily pads). Taz learns which from `This particular Scrappy doesn't seem to care for this type of food...` and `The Scrappy cautiously started eating the ...`, sends you for the right food when you have none, and says to catch it once it's `came out of its shell!`.
  2. To anything you can do now:
     - the bee nests (Honeybug)
     - the Hideonwall perches
     - the Snoozle walls, and the Troodon ice walls with an Icebreaker
     - the haunted house's 4 beds (Gazer); beds anywhere else don't count
     - the Rockmite mounds
     - the Cavernfish pond, the Polaris cave and the Shuddersquid pool
     - the spots of Areitas, Litterbugs, Flitters, Shyworms, Chuckwallas and Hideyho
     - Scrappy's food
     - the Ritual, Gem Podiums, Birdfeeder or the water in the mansion's basement (found in the world once it's loaded) when you carry what they need. The Ritual takes a Soothing Incense for each of its 4 candles, and the Gem Podiums all 3 gems. Taz counts the candles you've lit and the gems you've placed from chat, and the guide shows how far along you are (`(1/4 incense: find 3 more)`).

     The other things to do get small boxes, and a floor drop within 16 blocks is grabbed on the way.
  3. Round the biome to find the critters that roam it. If a critter needs quest items you don't have yet (like 3 more Soothing Incense), it goes to floor drops instead: the ones in view, then the map's floor-drop spots.

  **It sticks with what it picked:** once the route has a target, it keeps it until it's done or gone, however you move about, instead of switching to whatever is nearest. Only searching gives way, as soon as there's something real to go to, and a floor drop within 16 blocks is a short detour before it carries on. Places you've been are crossed off. When the biome has nothing left (the Macaw doesn't count, since it may not come at all), the route moves on.

  **Your biome** picks which biome comes first. On Auto, that's your Hunting Hotspot, which takes over as soon as Hypixel names it a few seconds in. Everyone in a party gets a different hotspot, so each of you following yours splits the biomes by itself. After that it's the biome you're in, then the one with most left. If your party agreed on other biomes, pick yours. The walkways are SkyHanni's Safari map: 1,423 points, each knowing its biome the way SkyHanni works it out. Within 32 blocks of the target, and for the last stretch from the walkways to it, the path is worked out over the blocks themselves: walking, stepping up, dropping down, swimming and climbing. So it follows the ground round walls all the way instead of turning into a tracer. Only something nobody can walk to, like a critter in the air, gets a straight line.
- **Floor drops and Rockmite mounds:** floor drops (three bits of string on the ground under a green sparkle) within **Floor drop range** (30 blocks by default) get a green box and name through walls, so you find them fast. The box sits where the strings are drawn (Hypixel shifts them off their entity's position), and it goes the moment you take the drop. While you still need a Rockmite, every rock mound in the Cavern gets an orange box.
- **Alerts:** Sparkling critters, `Two Macaws were attracted to the Birdfeeder!`, the Wumpa waking up (`You hear the sound of massive footsteps`), the Doomspiral ritual and the Gemzie chamber.
- **Sparkling callouts** (off by default): in a party, Taz says in party chat when you find a Sparkling critter, with its biome and where it is (`[TAZ] Sparkling Bloodbat in the Haunted biome at 12, 70, -40`), so a teammate can come for it. Nothing is sent when you're on your own.
- **Run report:** when you leave, Taz sums the run up in chat: what you and your party caught and how long it took, the rewards from Hypixel's summary (Safari Essence, Hunting XP, shards), your best run, and what you missed in each biome and why (`Gemzie (needed Lime Gem, Orange Gem)`, `Bloodbat (seen, not caught)`, `Polaris (not found)`).
- **Critterdex sync:** open your Critterdex and every critter it shows as caught counts as caught in Taz, so "never caught by me" and the "new" tags match your real collection, not only what Taz saw you catch.
- **How it works:** Hypixel puts a name tag with each critter's exact name over its body, which is a mob, an armour stand wearing it or a display entity. Catches come from chat:
  - `CAPTURE! You caught a Foxtrot and gained 2x Foxtrot Shard!` is your own catch, which Taz remembers for "never caught by me".
  - `LOOT SHARE! ... from Steve catching a Treefrog!` is a party member's catch.

  Each Safari is a new world, so a run starts over by itself. Where each critter lives comes from the Hypixel SkyBlock wiki and the forum guide.

## General

- **Alerts:** a big title and a sound for `RARE DROP!` / `VERY RARE DROP!` / `CRAZY RARE DROP!` / `PET DROP!` (pick every drop or only the rarer ones), filled bazaar orders, sold auctions, party invites, and low health (below 25% of your max by default, from the action bar). Players typing those words in chat don't trigger it.
- **Dark Auction Reminder:** a title, a sound and `Dark Auction in 3m 0s! [Warp]` before every Dark Auction (they start at :55 every hour), and again when it starts. The minutes before, the sound and the [Warp] button (`/warp da`) are settings.
- **Chat Filter:** hides lobby joins, Watchdog announcements, warping / profile messages, ability damage, ability errors, kill combo and (optional) friend / guild joins and Autopet. Taz features still read every line.
- **Command Keys:** 8 keys, each with any command (`/pets`, `/storage`, `/warp garden` ...) or chat message. All start unbound.
- **Event Timers:** a HUD counting down to the Dark Auction, Jacob's Contest, Cult of the Fallen Star, Spooky Festival, Season of Jerry, bank interest, Traveling Zoo and New Year (pick which by dragging), worked out from SkyBlock's calendar, with optional reminders and the SkyBlock date.
- **Skill Tracker:** skill XP per hour and time to your next level from the `+12.5 Foraging (52,312/100,000)` action bar messages (percent display works too). Like the whisper counter, the clock only runs while you're getting XP.
- **Chat Waypoints:** `x: 12, y: 70, z: -5` (or `12 70 -5`) in party chat becomes a beam you can walk to; elsewhere it gets a [Waypoint] button. Waypoints go away when you reach them or after 10 minutes. A key sends your own coordinates to party chat.

## Inventory

- **Item Prices:** `Lowest BIN: 900M` or `Bazaar buy: 1.7K sell: 1.5K` at the bottom of every SkyBlock item's tooltip, with the whole stack's price. Enchanted books are priced by their enchant and pets by level and rarity. Uses the prices SkyBlock XP Assist already downloads, or downloads them itself every 15 minutes.
- **Item Overlays:** the rarity colour behind items in every inventory and menu (Common skipped by default), and a small number on pets (level), enchanted books (level), minions (tier), skills and collections in menus (level) and New Year Cakes (year).

## Slayer and Fishing

- **Slayer Helper:** Hypixel puts `Spawned by: <name>` over every slayer boss, so Taz knows which one is yours: a `BOSS SPAWNED` title, a box around it (through walls), and `Boss killed in 0:42 (best 0:38, average 0:45, 12 this session)` after `SLAYER QUEST COMPLETE!`.
- **Fishing Helper:** a `!!!` title and sound the moment a fish bites (Hypixel's `!!!` over your bobber), seconds since you cast over your bobber, and a Double Hook title.

## Whisper Counter

A small HUD on Galatea and Torrhus Canyon:

```
Whispers (counting)
Forest 209,597/h +1,240
Desert  12,677/h +75
Foraging 0:21
```

- **Whispers gained** come from the totals in the tab list (`Forest Whispers: 12,345`, `Desert Whispers: ...`), so every source counts. Spending whispers isn't a loss, switching lobby or profile starts from the new totals instead of counting the jump, and the refund when you reset your Heart of the Forest (`You have reset your Heart of the Forest!`) isn't counted as whispers earned.
- **Only while foraging:** the clock runs while you hit logs, logs break within 7 blocks of you, or whispers come in. 3 seconds after the last of those it says **(paused)** and stops, so the rate is per hour of foraging, not per hour online. The pause time is a setting.
- The rate shows after 10 seconds of foraging. **Reset counter** starts again from zero.
- Needs the tab list's Forest / Desert Whispers lines (and the `Area:` line) turned on in Hypixel's tab list widgets.

## Moving HUDs

HUDs and in-world labels (waypoints, tree pointer, honeyhives) are plain shadowed text with no background box. Every Taz HUD (dungeon panels, SkyBlock XP, Whisper Counter, Event Timers, Skill Tracker) moves in one editor: **About → Move HUDs** in `/taz`, or any feature's Move HUD button. Drag to move (it snaps to the screen edges and centre), scroll to resize, right-click to hide or show, R to reset the one under the mouse, arrow keys to nudge the last one you touched. **Reset all** puts every HUD back. Positions are saved as you go.

## SkyBlock XP

- **Cheapest XP list** (`K`, or the button in /taz): every upgrade you haven't done (museum, unique minions, Abiphone contacts, accessories, pets, consumables, essence shop perks, misc), sorted by coins per SkyBlock XP, with buy buttons for the Bazaar, Auction House and recipes.
- **Level Plan** (button or keybind; plan length is the "Plan levels" slider): the cheapest way to your next levels as a route, one stop per NPC grouped by island, with what to bring.
- **HUD:** your level, the next cheapest upgrades, shopping hints near planned NPCs, and free XP you're carrying ("Ready to donate").
- **NPC tracker:** the NPC glows through walls and gets a box, beam and label. Use the NPC Finder (search an NPC, or an item to find who sells it) or a [Track] button in chat.
- **Progress** is read live from the `+N SkyBlock XP` messages and Hypixel's menus, and loaded from EliteBot (no key needed) or the Hypixel API with your own key. Saved per profile in `config/taz/xpassist/progress.json`.

All game data (prices, NPCs, recipes, museum XP) is downloaded live and cached in `config/taz/xpassist/cache/`. The few rules no API exposes are in `assets/taz/xpassist/rules.json`; copy it to `config/taz/xpassist/rules.json` to override.

## Code layout

```
com.taz
├── Taz, TazCommand          startup, /taz (settings GUI + chat-button actions)
├── util/                    KeyWatcher (GUI keybinds), TazChat ([TAZ] chat and party chat, click-twice confirm), Alert (title + sound)
├── config/                  TazConfig (one @Category per area), AboutConfig, Migration (old mods → Taz), ConfigSafety (last good copy)
├── render/WorldRenderer     boxes, lines and labels in the world (through walls or not), shared by every feature
├── hud/                     MovableHud, TextHud, TazHud (draws every HUD), TazHudEditor (moves them all)
├── general/                 Alerts, DarkAuctionReminder, ChatFilter, CommandKeys, EventTimers (+ SkyblockEvent calendar), SkillTracker, Waypoints
├── inventory/               ItemPrices + PriceTooltips, ItemOverlays
├── slayer/, fishing/        SlayerHelper, FishingHelper
├── dungeons/                Dungeons: run tracker, predictor, score, HUD panels, DungeonTools (Tools buttons)
│   ├── secrets/             DungeonRooms (room + rotation, Odin's way), SecretRoutes (Odin packs), SecretTrainer (Secret Routes), RoomSecretTimes
│   ├── skillissue/          SkillIssue (death jokes)
│   └── util/DungeonParty    classes from the tab list, solo or not
├── foraging/                Galatea: TreeTracker + TreeRenderer (tree pointer), HoneyhiveTracker + HoneyhiveRenderer (honeyhives), WhisperTracker + WhisperHud,
│                            BlockFinder (every block of a kind in loaded chunks), RubyVeilshrooms, TreeProgress, SweepDetails, MoongladeResources,
│                            HuntingMobs, FairySouls, IslandGuide, TreeGifts, StarlynContest, ProtectedTrees, GalateaRecorder, PressureWarning,
│                            TreeRoutes + RouteAssist (Tree Route Assist, Skyblocker format), Islands (island ids), Galatea (which island)
├── xpassist/                SkyBlock XP: data sources, XP calculator, screens, HUD, NPC tracker, XpActions (buttons)
├── mixin/                   chat packets, server time, block changes, container clicks, action bar, glow
└── compat/                  Mod Menu
```

**Adding a feature:** put it in its own package, give it a config class with MoulConfig `@ConfigOption` fields (`@Expose` on everything that should be saved) whose first option is `enabled`, add it as an `@Accordion` field to its category class (`GeneralCategory`, `InventoryCategory`, `DungeonsCategory`, `SlayerCategory`, `FishingCategory`, `GalateaCategory`, `SkyblockXpCategory`, or a new category in `TazConfig`), and start it from `Taz.onInitializeClient`. Group sub-settings as nested `@Accordion` classes (MoulConfig won't mix those with `@ConfigAccordionId` in one class). Its `enabled` starts `false` (no feature is on by default). If saved settings move, bump `TazConfig.CURRENT_VERSION` and add the move to `Migration.upgradeConfigFile` (the old file is kept as `config.json.bak-v<N>`). No new commands: actions are `@ConfigEditorButton` Runnables, keys are `@ConfigEditorKeybind` ints read with `KeyWatcher`. Draw world highlights with `WorldRenderer`. **Every HUD must be movable:** make it a `MovableHud` (a `TextHud` for plain text lines, saved in a `HudPos` field of its config) and `TazHud.register` it, so it shows up in the HUD editor. HUD text has no background box (shadowed text only). Chat lines, action bar messages and ticks reach features through `Taz.onSystemChat`, `Taz.onActionBar` and `Taz.tick`, each wrapped in `safely(...)` so one broken feature never stops the others.

## Room Secret Times and Skill Issue

**Room Secret Times** times each room's secrets from Hypixel's own count on the action bar (`3/7 Secrets`): from the first secret found while you're in the room to the last. It doesn't need Secret Routes or any waypoints, so it works with whatever route you take. Secrets already found when you walk in (a teammate's) don't start the clock. When the room's done it says so in party chat (`[TAZ] Mossy secrets done in 0:11.2`, not when you're solo) and in your chat with your best for that room (`(7/7, new best, -0.9)`). Best times are in `config/taz/dungeons/room-secret-times.json`.

**Skill Issue** roasts whoever dies in your dungeon, in party chat or only to you. **Toxic** tone (the default) asks how they managed it (`[TAZ] How did you die in terms, Steve? It's clicking colours.`, `[TAZ] Tank Steve died. You had ONE job.`); **Friendly** tone is gentler. No swearing either way, so Hypixel's chat filter lets them through. It reads Hypixel's death line (`☠ mTaz was killed by Storm and became a ghost.`) and uses what it can: where the run is (clear, blood, on the way to the boss, Maxor, Storm, terminals, core, Necron, the dragons, Sadan, Livid, Thorn, The Professor, Scarf, Bonzo), what killed them (Shadow Assassins, Fels, crypt mobs, a Mimic...), how (a trap, crushed, disconnected) and their class from the tab list. Their second death gets `(death #2)`. When it's you who dies, Taz makes an excuse for you instead, from the same context: toxic ones blame the team (`[TAZ] Died in terms because SOMEONE didn't do their terminal.`), friendly ones blame the game (`[TAZ] Died in terms. The GUI lagged and ate my click.`). Where the run is comes from its own chat, so it works without the Run Predictor. Every death gets its joke the moment it happens; only a wipe (a fourth death within 5 seconds) is cut short. **Try it** shows you a few.

## From Odin and NoammAddons

Some features are ported from [Odin](https://github.com/odtheking/Odin) (BSD 3-Clause) and [NoammAddons](https://github.com/Noamm9/NoammAddons) (CC0), rewritten for Taz and its look:

- **General:** Performance HUD (FPS, TPS, ping, measured the way F3's network chart does), Pet Display (your pet, level and held item from the summon, Autopet and level-up messages and /pets; a title when Autopet swaps it), Ragnarock (cast and cancel alerts, the Strength you gained, from the axe's howl), Spring Boots (charge height from the boots' notes, a HUD and a box where you'll land), Kick Timer (how long since you were kicked from SkyBlock), Etherwarp (while you sneak with an Etherwarp item, a box where you'd land: red when it works, black when it won't), Gyro Wand (a ring where its pull lands, grey on cooldown, with the Mage's cooldown cut), Chat Commands (party, private and co-op "!" commands: !warp, !allinvite, !pt, !kick, !f7 and the other floors when you lead; !cf, !8ball, !dice, !coords, !ping, !tps, !fps, !location, !holding, !taz (a link to the Taz Discord); !dt for a downtime reminder at the end of the run; !invite by private message), Chat (Ctrl + click a message to copy it, emoji codes like :skull: and <3, Explosive Shot damage per enemy), Command Shortcuts (/dn for /warp dhub, your own list), Slot Binds (bind a slot to a hotbar slot and shift + click to swap; copies your Odin binds), Loadout Keybinds (number keys and arrows in the Loadout menu), Auto Sprint, No Cursor Reset (the mouse stays put when Hypixel swaps one menu for another), Camera (F5 skips the front view), Render Optimizer (no falling blocks, lightning, XP orbs, dying mobs, explosion particles, screen fire, Archer bone meal, healer fairies or Soul Weaver and tentacle heads), Held Item (size, position, turn and swing speed of the item in your hand; copies your NoammAddons values), Arrow Fix (no pull-back on shortbows), Block Overlay (a red box on the block you look at), Sound Volumes (any sound louder or quieter by id), an Etherwarp sound, and Auction Price Input (a text box for prices like 10m or 2.5k with an undercut mode; Enter presses Create and Confirm), Storage Overlay (/storage shows every ender chest page and backpack with its items at once, remembered from when you last opened each; click one to open it) and Dark Mode (a black tint over the world).
- **Dungeons:** Blessings (from the tab list), Auto Requeue (the same floor again when a run ends, unless someone asked for downtime), Leap Menu (Spirit Leap as four big boxes placed by class like Odin, keys 1-4, and a party message when you leap), Party Finder (a joining player's Catacombs level, secrets, magical power and S+ time from EliteBot, so no API key; Auto Kick below your needs; level requirements and missing classes in the menu), splits panel extras (wither doors, 300 score time, time lost to lag), Dungeon Map (Hypixel's map redrawn bigger: room colours, doors, ticks and crosses or room names with secrets, and your teammates' faces and names; it shows only what Hypixel's map shows), Map Info (a score HUD, the room's secrets, and the Blood portal boxed at 300 score), Mask Timers (Spirit Mask, Bonzo Mask and Phoenix: invincibility and cooldown, an alert, `[TAZ] Bonzo Procced! (2/3)` in party chat, the cooldown drawn on the mask), Score Alerts (270 and 300 score titles from Hypixel's live score, Mimic, Prince and Bat kills in party chat), Room Alerts (`Secrets Done!`), Terracotta Timer (Floor 6 boss), Starred Mobs (starred mobs, mini-bosses and bonus bats glow through walls, other mobs' names hidden, teammates glow in their class colour), Doors & Keys (wither and blood doors next to rooms you've been in, white once the party has the key; the keys boxed through walls with a line and a spawn alert), Secret Clicked (a box on each secret you get, black if the chest is locked, and Odin's chime), Blood Camp (when the Watcher moves, and a box where each of his mobs lands with a countdown), Mage Beam (the beam as one red line, sparks hidden), Puzzle Solvers (Water Board: which levers and when; Teleport Maze: the pads that can still be the way out; Ice Fill and Ice Path: the path to take; Blaze: the order to kill them; Creeper Beams: the lantern pairs; Three Weirdos: the right chest; Quiz: the right answer and a timer; Boulder: the next button; Tic Tac Toe: your best move; plus how long each puzzle took and a button for an Architect's First Draft when someone fails one). Odin's puzzle solution tables are included (BSD 3-Clause).
- **Floor 7:** Tick Timers (server ticks, so they stay right through lag), the Leap Counter (how many teammates leaped to you at each Phase 3 spot), Terminal Highlight (the terminals in your Phase 3 section nobody has done, flashing when you click one), Door Fix (turns section 3's iron doors the right way round, on your screen only), Terminal Solver (in each terminal only the slots to click show, in red, with the order for Numbers, left and right clicks for Rubix and the button for Melody; NoammAddons' names for each, and a click sound of your choice), Simon Says (the buttons in order), Arrow Align (how many clicks each frame needs) and Wither Dragons (spawn titles, timers and HUD, boxes, a line to the next dragon, health, and how long each lived).

Taz leaves out the parts of these mods that play for you or show what you couldn't know (NoammAddons' cheat-only features, Odin's singleplayer Etherwarp), and never blocks or changes your clicks: the solvers show the answer and you click it yourself.

Taz knows where you are in a run (floor, boss room, Floor 7 phase and Phase 3 section) from the sidebar and the boss rooms' boxes, and counts server ticks from the ping packet Hypixel sends every tick.

## Credits

Secret Routes uses Odin's dungeon room table (`assets/taz/dungeons/rooms.json`), the Puzzle Solvers use Odin's solution tables (`assets/taz/dungeons/puzzles/`), and Taz follows Odin's room scanning, so Odin waypoint packs line up. Odin is © odtheking, under the BSD 3-Clause License (included as `assets/taz/dungeons/odin-LICENSE.txt`). The secret routes Taz comes with are 3ppopka's (inspired by flameofwar), shared as an Odin waypoint pack. The Critter Safari helper follows critterMod by MrCloudy2 (MIT) for Hypixel's catch messages, how critters are built and how floor drops look. It uses Skyblocker's Rockmite mound texture, and its map is SkyHanni's Critter Safari island graph from the SkyHanni-REPO, © hannibal2, under the MIT License (included as `assets/taz/foraging/safari/skyhanni-repo-LICENSE.txt`).

## Source and history

The code is in git (`main`), with a commit per change, so the history shows how the mod was built. `.gitattributes` keeps `gradlew` working on every OS.

The version (`mod_version` in `gradle.properties`) goes up with every change: new features raise the middle number (1.1.0 to 1.2.0), fixes the last one (1.1.0 to 1.1.1).

## Build and test

JDK 25 · Minecraft 26.1.2 (Mojang names) · Fabric Loader 0.19.5 · Fabric API 0.155.3+26.1.2 · Loom 1.18.4 · Shadow 9.6.1 · MoulConfig 4.8.0 (modern-26.1) · Fabric Language Kotlin 1.13.12.

```
gradlew build                 # build/libs/taz-<version>.jar
gradlew runClient             # dev client
gradlew runClientGameTest     # in-game tests, screenshots in build/run/clientGameTest/screenshots
gradlew runClientGameTest -Ptests=honeyhive   # only one feature's tests (honeyhive, trees, xp, settings, whispers, general, galatea, secrets, roomtimes, skillissue, ported, puzzles, qol)
gradlew runClientGameTest -Ptests=slayer      # one part of the general test (autosave, alerts, chatfilter, commandkeys, events,
                                              # darkauction, skills, waypoints, huds, prices, overlays, slayer, fishing)
gradlew runBacktest           # dev: replay run/config/taz/dungeons/history.json, writes backtest.txt
```

The in-game tests open a singleplayer world and check: the XP list, plan and NPC finder screens and the NPC marker through a wall; progress detection from menus and action-bar XP; fake Galatea trees being chopped, hologram-marked, felled and regrown, picking tree types, a 35,000-log forest stress test of the tracker's speed; every settings GUI category, that /taz is the only command, a GUI keybind, and importing the old mods' settings; the whisper counter's rates, pause and spending, and dragging its HUD in the HUD editor; settings auto-save and restoring a damaged settings file; every alert, the Dark Auction reminder, the chat filter, command keys, the SkyBlock calendar and reminders, the skill tracker, chat waypoints, price tooltips, item overlays, the slayer boss and the fishing bite; Ruby Veilshrooms found by block changes and by scanning chunks (also underground), picked and respawned, tree progress and TIMBER!, sweep details, Sea Lumies and Lushlilac, hunting mob glows and muted Phantoms, Fairy Souls ticked off, and the island guide; a Tree Gift block (from the game log) counted and shrunk to one line with its hover, rare bonus alert, honeycomb and Lottery buff; Starlyn reward claims added up, score bracket and contest end; a protected tree skipped by the pointer and a regenerating one too; and the HUD-text recorder; a HOTF reset refund left out of the whisper counter; contests ending 30 seconds before the SkyBlock day and the sidebar timer and bracket; the pressure warning; and Tree Route Assist (your real Skyblocker export imported to Torrhus Canyon with 37 points, exported back the same, a guided lap timed with splits, a tree that isn't grown and a regenerating one skipped, leaving a tree, laps with and without the end stops, the restart key); and Secret Routes (Odin's room table and Taz's routes read, the four room rotations and every room shape's corner, a room built on the dungeon grid found by its core and turned the right way, an Odin export landing on the right blocks, the route legend (colours and notes), a guided room from its red start through an etherwarp spot, a chest (which starts the timer), a bat spot that waits for the bat, a yellow aim spot that isn't a step, a slab-sized spot, a mined block, a mining hint cleared and a lever, the secret count, a chest a teammate opens and picked-up Paper, the restart key, export and Odin-folder import, Normal mode and the settings); Room Secret Times with Secret Routes off (a teammate's secret not starting the clock, the party and your chat lines, once per room); and Skill Issue (blood, a blood-mob boss ignored, the way to the boss, terminals and core, the joke pools for a terminals death, a second death, a healer and a trap, a disconnect, an excuse for your own death to Storm, turning your excuses off, back-to-back deaths both getting a joke and a wipe cut short, the toxic and friendly tones; and party messages waiting after another mod's, going out one at a time, and being sent again once when Hypixel doesn't show them); and the Critter Safari helper (the entrance guide; critters found with their name tags on a fox, frog, parrot, armour stand and item display, with a CAPTURING decoy and a shard tag ignored; red and gold glows; your catch and a party member's, a quoted catch ignored; the never-caught-by-me mode; the hotspot and Macaw alert; quest item and low capsule tips; a biome done with where to go next; no Sparkling callout on your own and one in a party; the Shyworm, Wumpa and Hideyho countdowns starting and ending; quest items and done biomes leaving the HUD; the run report from Hypixel's summary; and the Critterdex sync), and its way and finds (SkyHanni's map loaded and walked from the Safari Manager to the Ritual, a floor drop of three strings found and two strings ignored, a Rockmite mound by its head texture, map points' biomes, a critter in view first and a nearby one of another biome left alone, the Birdfeeder with bait, searching the Forest or a floor drop without, a picked biome first, your hotspot first even while standing in the Forest, a picked biome done but the Macaw moving on, a Scrappy in view sending you for food first, lily pads found, a refused food crossed off, the food it eats learnt, catching it once it's out of its shell, the armour message explained, a Shining Coin leading to the mansion basement and then the middle of its water, one Soothing Incense sending you to floor drops and four to the Ritual, lit candles counting and the Doomspiral summoned, gems placed counting towards the Gemzies, only the haunted house's beds for Gazers, a critter gone out of view gone to where it was last seen and forgotten once you're there, sticking with the Birdfeeder when a critter comes into view, a floor drop close by walked to, and round a stone wall rather than through it; the drop boxed where its shifted strings are drawn, hidden the moment you hit it and back if it wasn't taken, and gone the tick its strings go).
