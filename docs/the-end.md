# The End

Settings are in the **The End** category of `/taz`, under **End Stone Protector**. It's off until you switch on **Enabled**.

## End Stone Protector

The protector's stage comes from Hypixel's tab list, so turn its line on first: `/widget` → General Info Widget → Show End Stone Protector Status. Without it, the HUD says how.

In The End, the **Stage HUD** shows:

- **Stage:** 0 Resting, 1 Dormant, 2 Agitated, 3 Disturbed, 4 Awakening or 5 Summoned. At stage 4 it also shows how long it's been there, once Taz has seen it get there.
- **Spot:** which of its six spots in the Dragon's Nest it will rise at (Middle Front, Middle Center, Middle Behind, Left, Right Front or Right Behind), from its head showing there.
- **Rises in:** a 20-second countdown from "The ground begins to shake as an End Stone Protector rises from below!" to it rising, then **Fight!** until it's down.
- **Lobby:** the lobby you're in, and whether you've been in it before.

**Waypoint:**

- **Spot waypoint:** a purple box and beam at the spot it will rise at, seen through walls, with how far away it is, its stage, and the countdown once it's coming. **From stage** sets the stage it starts showing at (1, whenever Taz knows the spot, by default).
- **Highlight it:** a purple box around the protector once it has risen, seen through walls, so you can find it during the fight.

**Alerts:** a title at stage 4 and at stage 5, with the spot when Taz knows it. Landing in a lobby that's already at stage 4 or 5 alerts too. Pick the sound under **Sound**.

## Lobby Tracker

Taz remembers each End lobby you've been in by the server id on the sidebar (the "11/04/26 m147AS" line), with the protector's stage when you left and when it was killed.

- **Same lobby alert:** the moment you land in an End lobby you've already been in, a title and a chat line say so, with how long ago you left it and the protector's stage then, so you can hop again straight away.
- **Lobby list:** a HUD of the lobbies you've been in, newest first, with each one's stage (or "killed 4m ago") and how long ago you left it. **Lobbies in the list** sets how many it shows.
- **Remember for:** how many minutes a lobby is remembered after you leave it (60 by default). **Forget lobbies** clears them all.

Move the HUDs with `/taz` → About → Move HUDs. The lobbies are saved in `config/taz/end-lobbies.json`, so they're still there after a restart. The spawn spots are from the GanKura and Divine Golem mods.
