# Development

JDK 25 · Minecraft 26.1.2 (Mojang names) · Fabric Loader 0.19.5 · Fabric API 0.155.3 · Loom 1.18.4 · MoulConfig 4.8.0 · Fabric Language Kotlin 1.13.12

```
./gradlew build                                # build/libs/taz-<version>.jar
./gradlew runClient                            # dev client
./gradlew runClientGameTest                    # every in-game test
./gradlew runClientGameTest -Ptests=safari     # one part
./gradlew runBacktest                          # replay recorded dungeon runs through the predictor
```

Use `taz-<version>.jar`, not the `-slim` jar, which is missing the settings GUI library.

## Tests

The in-game tests open a singleplayer world, build what each feature needs (fake trees, honeyhives, critters, dungeon rooms) and check it end to end. Screenshots go to `build/run/clientGameTest/screenshots`. Parts: `xp`, `trees`, `settings`, `honeyhive`, `whispers`, `general`, `galatea`, `safari`, `safariway`, `secrets`, `roomtimes`, `skillissue`, `ported`, `puzzles`, `qol`, and the general test's parts (`autosave`, `alerts`, `chatfilter`, `commandkeys`, `events`, `darkauction`, `skills`, `waypoints`, `huds`, `prices`, `overlays`, `slayer`, `fishing`).

## Code layout

```
com.taz
├── Taz, TazCommand    startup, /taz
├── config/            TazConfig (one category per area), Migration (old layouts), ConfigSafety
├── util/              TazChat, Party, Alert, KeyWatcher
├── render/            WorldRenderer: boxes, lines and labels in the world
├── hud/               MovableHud, TextHud, TazHud, the HUD editor
├── general/, inventory/, slayer/, fishing/
├── dungeons/          run predictor, secrets, puzzles, Floor 7, map
├── foraging/          Galatea features
│   └── safari/        Critter Safari
├── xpassist/          SkyBlock XP
├── mixin/
└── compat/            Mod Menu
```

## Adding a feature

1. Give it a config class whose first option is `enabled` (default `false`), with `@Expose` on saved fields.
2. Add it as an `@Accordion` field to its category class, or a new `@Category` in `TazConfig` for a big feature.
3. Start it from `Taz.onInitializeClient`. Chat, action bar and ticks come through `Taz.onSystemChat`, `Taz.onActionBar` and `Taz.tick`, each wrapped in `safely(...)`.
4. Every HUD is a `MovableHud` registered with `TazHud`. Draw in the world with `WorldRenderer`.
5. If saved settings move, bump `TazConfig.CURRENT_VERSION` and add the move to `Migration`.

No new commands: actions are `@ConfigEditorButton` fields, keys are `@ConfigEditorKeybind` fields.

## Versions and releases

`mod_version` in `gradle.properties` goes up with every change: features raise the middle number, fixes the last. Pushing a tag like `v1.2.1` makes GitHub build the jar and publish a release with it (`.github/workflows/release.yml`).
