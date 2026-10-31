# General

## Settings

`/taz` opens the settings menu (Mod Menu and an optional key do too), with categories on the left and a search bar. Each feature is one collapsed section with **Enabled** first, and every feature starts off. Big features (Run Predictor, Secret Routes, Puzzle Solvers, Dungeon Map, Critter Safari) have a category of their own.

Settings save by themselves to `config/taz/config.json`. Taz keeps the last good copy, so a damaged file is restored instead of reset.

## HUDs

Every Taz HUD moves in one editor: **About → Move HUDs**. Drag to move, scroll to resize, right-click to hide, R to reset one. HUD text has no background box.

## Chat

Everything Taz says starts with **[TAZ]**. Party messages go out one at a time, and one Hypixel doesn't show is sent again once. Some messages have buttons ([Track], [Bazaar], [AH], [Recipe]) so you never need to type commands.

## Player Viewer

`/pv [player]` opens a player viewer like NEU's: the player in their armor, and tabs for an **Overview** (purse, bank, networth, skill average, Catacombs, Magical Power, skills and slayers), **Dungeons** (Catacombs, classes, and each floor's runs, best score and fastest S+), **Gear** (armor, equipment, pet, weapons, wardrobe and accessories), **Inventories** and **Pets**. Hover anything for details, and use the arrows by the profile name to switch profiles.

`/nw [player]` says a networth in chat: with cosmetics, without them, and unsoulbound. Hover a number to see what it's made of.

With no name, both show you. There's nothing to set up:

- **Profiles** come from Hypixel's API, through Taz's own server, which holds Taz's one API key. Hypixel's rules don't allow a key in a mod. Each lookup is kept 5 minutes in the game and 10 on the server.
- **Levels** are worked out from the raw XP: skills (with Anita's and pet care caps), Catacombs and classes, slayers and pets.
- **Items** are read from the inventories Hypixel stores, so they need the player's inventory API on.
- **Magical Power** counts each different accessory by its rarity, the Hegemony Artifact twice, Abiphone contacts and the Rift Prism. It doesn't know accessory families, so a lower tier of one you have still counts.
- **Gear stats** add up what the armor and equipment lore says.
- **Pet heads** come from the NEU repo that SkyBlock XP downloads; until it has, pets show as bones.

**Networth** is worked out the way [SkyHelper-Networth](https://github.com/Altpapier/SkyHelper-Networth) does it (the networth SkyHelper and SkyCrypt show), with prices from [SkyCofl](https://sky.coflnet.com) and Hypixel's bazaar:

- **Items** are worth their lowest BIN, plus what their upgrades cost. That covers enchantments, potato books, stars and master stars, gemstones and slots, scrolls, the recombobulator, reforge stones, Art of War, drill and rod parts and the rest, each counted at SkyHelper's share.
- **SkyCofl's floor:** every item is also sent to SkyCofl's price estimate, which matches it to sold items like it. When their median sale is higher, that counts instead. This catches what a price list can't see: skins, shiny items, coins in a Crown of Avarice.
- **Pets** are worth SkyCofl's estimate: their level, held item and skin.
- **Where items count from:**
  - inventory, armor, equipment and equipment sets
  - the wardrobe (Hypixel's loadouts)
  - ender chest, backpacks, personal vault and bags
  - candy and carnival masks
  - the museum
- **Bazaar items, sacks and essence** go by Hypixel's bazaar, near insta-buy, as SkyHelper prices them. Purse, bank and personal bank are added on top.
- **Non-cosmetic** leaves out skins and dyes. **Unsoulbound** leaves out soulbound items, museum donations and essence.

Tested on a real profile, it comes within about 1% of SkyHelper's networth. Hover a slot to see what an item counts for; the Gear tab lists weapons most valuable first.

Taz's `/pv` replaces Skyblocker's, which is still at `/skyblocker pv`. Taz isn't affiliated with or endorsed by SkyCofl or Hypixel.

## Features

- **Alerts:** a title and sound for rare drops, filled bazaar orders, sold auctions, party invites and low health.
- **Dark Auction Reminder:** a title and a [Warp] button before every Dark Auction.
- **Event Timers:** a HUD counting down to the Dark Auction, Jacob's Contest, Cult of the Fallen Star, Spooky Festival, Season of Jerry and more.
- **Skill Tracker:** skill XP per hour and time to your next level.
- **Chat Filter:** hides lobby joins, Watchdog, warping messages, ability damage and other spam.
- **Stack Duplicates:** a message that repeats one of the last few (5 by default, **Look back** sets it) shows once with a count: "TROPHY FISH! You caught Blobfish BRONZE! (x3)". Blank and separator lines aren't stacked. If Chat Patches' own counter is on too, turn one of them off.
- **Chat Waypoints:** coordinates in party chat become a beam you can walk to.
- **Chat Commands:** party "!" commands (!warp, !allinvite, !pt, !kick, !f7, !coords, !ping...).
- **Command Keys, Slot Binds, Loadout Keybinds, Command Shortcuts.**
- **Visuals:** Performance HUD, Render Optimizer, Held Item, Block Overlay, Dark Mode, Sound Volumes, Arrow Fix, Etherwarp landing box, Gyro Wand range.
- **Pet Display, Ragnarock, Spring Boots, Kick Timer, Auto Sprint, No Cursor Reset, Camera.**

## Inventory

- **Item Prices:** lowest BIN (from SkyCofl) or bazaar price (from Hypixel) at the bottom of every item's tooltip.
- **Item Overlays:** rarity colour behind items, and numbers on pets, books, minions and menus.
- **Storage Overlay:** every ender chest page and backpack at once in /storage.
- **Auction Price Input:** type prices like 10m or 2.5k.

## Slayer and Fishing

- **Slayer Helper:** a title when your boss spawns, a box round it, and kill times.
- **Fishing Helper:** a title and sound the moment a fish bites, a bobber timer and a Double Hook alert.

## Coming from older mods

Taz replaces Dungeon Run Predictor and SkyBlock XP Assist. On first start it copies their run history, XP progress and settings. Remove their jars; the game won't start with both installed.
