# Inferno Minions

Settings are in the **Inferno Minions** category of `/taz`. It's off until you switch on **Enabled** (or click **turn it on** at the bottom of the menu). Open the menu with `/taz inferno`, or `/taz` → Inferno Minions → Profit Tracker → Open. Hover the **?** next to its title for a quick how-to.

## How it works

1. **You buy the parts.** Bazaar buys (instant or a claimed buy order) of Gabagool and other distillates, Inferno Fuel Blocks, Fuel, Heavy or Hypergolic Gabagool and Capsaicin Eyedrops are kept with what you paid. So is an Inferno Minion Fuel bought on the AH.
2. **You craft a fuel.** A fuel that shows up in your inventory while the Craft Item menu is open was crafted. It costs what you paid for its parts (6 distillates, 2 fuel blocks and the gabagool), oldest buys first. A part you didn't buy is at today's price, and the cost gets a **≈**.
3. **You put it in a minion.** Right-click the minion so Taz knows which one, and put the fuel in. That fuel is a row in **Fuels**, with what it cost and what its 24 hours should make. More fuel in one minion waits its turn.
4. **You collect.** What leaves the minion's storage is collected, worth what it sells for after tax, and goes on the fuel burning then. Taz follows the menu when Hypixel reopens it after Collect All, and a compactor turning 192 Crude Gabagool into a Very Crude Gabagool isn't counted. (With a hopper, which sells by itself, only what leaves just after a click counts.)

Taz counts a minion once you've right-clicked it. It reads its tier and its Flycatchers and Minion Expanders from its menu, so open each one once. It also reads the tab list's Minions line ("12x Inferno V [ACTIVE]") for how many Inferno minions you have and their tier.

Capsaicin Eyedrops used on a minion (one fewer in your inventory right after you right-click it) are added to that minion's fuel. Taking a fuel out ends it, and any waiting behind it come back to you.

Missed something? **Add collected…** (under the numbers, on Fuels and Drops) takes what you collected, like `33 chili, 2 very crude, 1.5k crude`, and shares it between the fuels burning now. Pick a fuel first to put it all on that one. **Cost…** types in what a fuel cost you.

## The lists

- **Fuels:** every fuel you put in. **Spent** is what it cost you, with eyedrops. **Expected** is what its 24 hours should make. **Made** is what you collected while it burned. **Profit** is made less spent, or **≈** what it should make less spent while it's still burning. Hover a row for the minion, where its cost came from, and what you collected.
- **Minions:** your Inferno minions with their fuel and time left, and each one's fuels, spending and profit. Hover for its upgrades, speed and what was in its storage the last time you looked. **Forget it** when you pick one up.
- **Drops:** everything you collected and what it's worth. For the rare drops of Hypergolic fuel, it also shows how many you'd expect from your fuels so far, and your luck.
- **Bought:** your fuel buys and how many have gone into a fuel.
- **Best fuel:** all 15 fuels by profit a day: what one costs (the cheaper of crafting it from bazaar parts and the AH), what its 24 hours make, and the difference. Pick the tier and turn eyedrops on or off on the left. Click a fuel to plan it.
- **Planner:** see below.

On the left are your numbers for the period (all time, 30 days, 7 days or today): spent, collected, sold, profit, expected profit, and the fuels burning now.

## Fuel Planner

Pick a fuel (its gabagool and distillate) and type how many you want. Taz works out the cheapest way to make them with insta-buy prices. Each part is bought or crafted, whichever is cheaper, all the way down:

- Hypergolic Gabagool: 12 Heavy Gabagool and 1 Sulphuric Coal
- Heavy Gabagool: 24 Fuel Gabagool and 1 Sulphuric Coal
- Fuel Gabagool: 24 Crude Gabagool and 1 Sulphuric Coal, or 8 Sulphuric Coal and 1 Very Crude Gabagool (makes 8)
- Very Crude Gabagool: 192 Crude Gabagool
- Sulphuric Coal: 16 Enchanted Coal and 1 Enchanted Sulphur (makes 4)
- Enchanted Coal: 160 Coal. Enchanted Sulphur: 160 Sulphur

The list shows exactly what to buy (how many, each, total) and then what to craft, in order, with any spare. The total is what that many cost to insta-buy now: SkyCofl prices the whole amount, so a big order that climbs the bazaar's offers costs more each. On the left it's compared with buying the fuel on the AH, and with buying the gabagool instead of crafting it.

Rather buy something than craft it (thousands of Enchanted Coal, say)? Click its craft row and Taz buys it instead. Click it again in the buy list to craft it again, or **Cheapest again** to undo them all. **List it in chat** sends the plan to chat; click a line to open the bazaar at that item.

## What a fuel should make

From the Hypixel SkyBlock wiki:

- A harvest takes two of the minion's actions (697 seconds each at XI, 1,013 at I).
- Speed: each Inferno minion makes them all 18% faster, up to 10 (+180%). A Mithril Infusion adds 10%, a Flycatcher 20% and a Minion Expander 5%. The fuel makes it 10, 15 or 20 times faster (11, 16 or 21 with Hypixel's extra one).
- 4 harvests in 5 make the fuel's item (1 Crude Gabagool or Blaze Rod, 1-3 Magma Cream, 3-7 Nether Wart, 2-3 Glowstone Dust); the rest make Crude Gabagool.
- Hypergolic fuel also rolls each harvest for a Chili Pepper (1 in 136), Inferno Vertex (1 in 5,950), Reaper Pepper (1 in 458,182), Inferno Apex (1 in 1,309,091, two at tiers X and XI) and Gabagool the Fish (1 in 3,927,273). Eyedrops make all but the Chili Pepper 30% likelier. It leaves a Hypergolic Ionized Ceramics behind.

**Prices and speed** sets **Bazaar prices** (orders: buy orders in, sell offers out; or instant), your **Mithril Infusion**, **Other speed** (a Beacon or Power Crystal) and how many **Inferno minions** you have (0 takes it from the tab list, or counts the ones you've opened). Expected assumes the minion never fills up: collect often, or use a Super Compactor.

## Fuel timers and alerts

- **Fuel HUD:** each minion's fuel and time left, soonest first, with "No fuel" in red, and "storage full!" when the tab list says your Inferno minions are full. By default only on your island. Move it with `/taz` → About → Move HUDs.
- **Fuel ran out alert:** a title and a chat line when a minion's fuel runs out.
- **Rare drop alert:** a title when you collect an Inferno Apex, Reaper Pepper or Gabagool the Fish (and an Inferno Vertex, if you want).

Chat lines tell you when you craft a fuel, put one in and collect, with the coins; switch them off with **Chat lines**. Everything is saved per account in `config/taz/inferno/`. Prices are provided by [SkyCofl](https://sky.coflnet.com).
