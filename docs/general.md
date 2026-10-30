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

With no name, both show you. The data is from [SkyCrypt](https://sky.shiiyu.moe), so they need your own SkyCrypt API key: sign in on SkyCrypt, request a key on your dashboard's Developer page, and paste it in **General → Player Viewer**. SkyCrypt only has players someone looked up on its site recently; for anyone else, Taz gives you a link to open their page first. Each answer is kept 5 minutes.

Taz's `/pv` replaces Skyblocker's, which is still at `/skyblocker pv`. Taz isn't affiliated with or endorsed by SkyCrypt or Hypixel.

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

- **Item Prices:** lowest BIN or bazaar price at the bottom of every item's tooltip.
- **Item Overlays:** rarity colour behind items, and numbers on pets, books, minions and menus.
- **Storage Overlay:** every ender chest page and backpack at once in /storage.
- **Auction Price Input:** type prices like 10m or 2.5k.

## Slayer and Fishing

- **Slayer Helper:** a title when your boss spawns, a box round it, and kill times.
- **Fishing Helper:** a title and sound the moment a fish bites, a bobber timer and a Double Hook alert.

## Coming from older mods

Taz replaces Dungeon Run Predictor and SkyBlock XP Assist. On first start it copies their run history, XP progress and settings. Remove their jars; the game won't start with both installed.
