# SkyBlock XP

Settings are in the **SkyBlock XP** category of `/taz`.

- **Cheapest XP list** (`K`): every upgrade you haven't done (museum, minions, Abiphone contacts, accessories, pets, consumables, essence perks and more), sorted by coins per SkyBlock XP, with Bazaar, Auction House and recipe buttons.
- **Level Plan:** the cheapest way to your next levels as a route, one stop per NPC, grouped by island.
- **HUD:** your level, the next cheapest upgrades, and free XP you're carrying.
- **NPC Tracker:** the NPC you need glows through walls with a beam. Find one by name or by the item it sells.
- **Progress** is read from `+N SkyBlock XP` messages and Hypixel's menus, and loaded from EliteBot (no key needed) or the Hypixel API with your own key.

Game data (prices, NPCs, recipes, museum XP) is downloaded live and cached in `config/taz/xpassist/cache/`. Rules no API exposes are in `assets/taz/xpassist/rules.json`; copy it to `config/taz/xpassist/rules.json` to override.
