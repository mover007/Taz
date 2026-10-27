# Dungeons

Settings are in the **Dungeons**, **Run Predictor**, **Secret Routes**, **Puzzle Solvers**, **Dungeon Map** and **Floor 7** categories of `/taz`.

## Run Predictor

Each run is split into phases from chat (blood rush, blood, clear, portal and the boss phases). From the portal on, the end time is the median of the time left from the same point in your past runs, weighted towards recent runs, the same teammates and the same number of deaths. It works in server ticks and accounts for lag. Past runs are rebuilt from Minecraft's chat logs, so it knows your runs from before it was installed.

Accuracy on the author's runs: M6 ±2.1s at the portal, M7 ±7s once the Core opens. The Stats button shows your own numbers.

## Secret Routes

Secret waypoints for every Catacombs room in [Odin](https://github.com/odtheking/Odin)'s format, so any Odin pack works. Taz finds the room and its rotation the way Odin does, so waypoints land on the same blocks.

- **Taz's routes:** a full set (3ppopka's, 139 rooms). Turn them off to use only yours.
- **Import** an Odin export from the clipboard, or copy the packs from your Odin install. **Export** shares yours.
- **Normal** mode draws every waypoint like Odin.
- **Guide** mode shows only the next step, with a line from your crosshair and what to do there. Waypoints clear as secrets are taken, blocks are mined or you arrive. Each room is timed against your best.

| Colour | Means |
|---|---|
| Red | Start point |
| Purple | Etherwarp to this block |
| Green | Chest, lever or wither essence |
| Yellow | Where to aim a normal teleport |
| Aqua | Prince |

| Note | Means |
|---|---|
| `bat` | Stand here and kill the bat |
| `just tp`, `normal` | Aim here and teleport (not etherwarp) |
| `line` | Mine only the line |
| `breakwarp` | Teleport, break the block, then etherwarp |

## Room Secret Times

Times each room's secrets from Hypixel's own count on the action bar, from the first secret to the last. Says it in party chat (`[TAZ] Mossy secrets done in 0:11.2`) and in your chat with your best. Works without Secret Routes.

## Skill Issue

A joke in party chat (or only to you) when someone dies, based on where the run is, what killed them and their class. **Toxic** (default) or **Friendly** tone, no swearing. When you die, Taz makes an excuse for you instead.

## More dungeon features

Blessings, Auto Requeue, Leap Menu, Party Finder stats and Auto Kick, Dungeon Map and Map Info, Mask Timers, Score Alerts, Room Alerts, Terracotta Timer, Starred Mobs, Doors & Keys, Secret Clicked, Blood Camp, Mage Beam, and Puzzle Solvers (Water Board, Teleport Maze, Ice Fill, Ice Path, Blaze, Creeper Beams, Three Weirdos, Quiz, Boulder, Tic Tac Toe).

## Floor 7

Tick Timers, Leap Counter, Terminal Highlight, Door Fix, Terminal Solver, Simon Says, Arrow Align and Wither Dragons (spawn titles, timers, boxes and health).

The solvers show the answer and you click it yourself: Taz never clicks for you or blocks your clicks.

## Files

`config/taz/dungeons/`: `history.json` (runs), `triggers.json` (chat triggers), `secret-routes/` (imported packs) and `secret-times.json` (best times).
