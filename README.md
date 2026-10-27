<p align="center"><img src="branding/taz-github-social-1280x640.png" alt="Taz" width="720"></p>

<p align="center">
  <a href="https://github.com/mover007/Taz/releases/latest"><img src="https://img.shields.io/badge/download-releases-ff2112" alt="Download"></a>
  <img src="https://img.shields.io/badge/minecraft-26.1.2-ff2112" alt="Minecraft 26.1.2">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-LGPL--3.0-ff2112" alt="LGPL-3.0"></a>
  <a href="https://discord.gg/D4rUnmbpJr"><img src="https://img.shields.io/badge/discord-join-ff2112" alt="Discord"></a>
</p>

# Taz

A Hypixel SkyBlock mod for Fabric. Galatea foraging, the Critter Safari, dungeons, SkyBlock XP and everyday helpers, all in one settings menu: `/taz`.

Every feature starts off, so you only get what you turn on.

## Features

- **Foraging:** a pointer to the next grown tree, Tree Route Assist with the Helix route built in, honeyhives with refill timers, a Whisper Counter, Ruby Veilshrooms, Tree Gifts, Starlyn Contests and Fairy Souls. [More](docs/foraging.md)
- **Critter Safari:** the critters you need highlighted, a route through every biome, floor drops, a quest item guide, countdowns, a run report and a capsule throw preview. [More](docs/critter-safari.md)
- **Dungeons:** a run end-time predictor, Secret Routes (Odin packs), puzzle solvers, a dungeon map, Floor 7 tools, Room Secret Times and Skill Issue. [More](docs/dungeons.md)
- **SkyBlock XP:** the cheapest upgrades you haven't done, a level plan and an NPC finder. [More](docs/skyblock-xp.md)
- **Everyday:** alerts, a Dark Auction reminder, event timers, item prices in tooltips, a chat filter, command keys, a storage overlay, and slayer and fishing helpers. [More](docs/general.md)

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) 0.19.5+ for Minecraft 26.1.2, with [Fabric API](https://modrinth.com/mod/fabric-api) and [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin).
2. Download `taz-<version>.jar` from [Releases](https://github.com/mover007/Taz/releases/latest) and put it in your `mods` folder. Remove any older `taz-*.jar`.
3. Start the game and type `/taz`.

Only download Taz from this page. Never run a jar someone sends you.

## Building

```
./gradlew build
```

The jar is `build/libs/taz-<version>.jar` (JDK 25). Tests, code layout and how to add a feature: [docs/development.md](docs/development.md).

## Credits

- [Odin](https://github.com/odtheking/Odin) (BSD 3-Clause): the dungeon room table, puzzle solutions and room scanning, and several ported features.
- [NoammAddons](https://github.com/Noamm9/NoammAddons) (CC0): several ported features.
- 3ppopka: the secret routes Taz comes with.
- [critterMod](https://modrinth.com/mod/critter-mod) by MrCloudy2 (MIT): Critter Safari chat messages and critter detection.
- [SkyHanni-REPO](https://github.com/hannibal002/SkyHanni-REPO) (MIT): the Critter Safari map.
- [Skyblocker](https://github.com/SkyblockerMod/Skyblocker): the Rockmite mound texture.
- [MoulConfig](https://github.com/NotEnoughUpdates/MoulConfig) (MPL-2.0): the settings menu.

The licences of bundled data are next to it in `src/main/resources/assets/taz/`.

## License

[GNU LGPL v3.0](LICENSE)
