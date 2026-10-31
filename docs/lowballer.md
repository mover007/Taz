# Lowballer

Settings are in the **Lowballer** category of `/taz`. It's off until you switch on **Enabled**.

## The trade HUD

When you open a trade (`/trade Name`), a HUD next to the trade menu shows the other player's side:

- **Worth:** what their items are worth together, plus any coins they put in.
- **Smart offer:** an offer worked out item by item (below). Their coins count in full.
- **-10%, -12%, -14%...:** their items' worth that far under, plus their coins. Change the list in **Lowball steps**.
- **Your offer:** the coins you've put in, and how far under their worth that is (red when it's over).
- **Their items:** each one's worth, how many sell a day (or "bazaar"), and how far under the smart offer goes for it.

Offers are rounded down to three digits (448,123,456 becomes 448M), the way you'd type them.

Click an offer (it gets a red » under the mouse) to copy it. Then click Coins in the trade menu, and Taz types the amount into the sign. Press Done to put it in.

Hover one of their items for its worth, its smart offer, and why.

Move the HUD with `/taz` → About → Move HUDs. If it's too wide for the room left of the chest (a big GUI scale or a small window), it shrinks to fit.

## Where the prices come from

From [SkyCofl](https://sky.coflnet.com)'s public API, with no key. Taz sends their items to SkyCofl's price estimate, which matches each one to sold items like it: same stars, scrolls, enchants, gemstone slots, pet level and stack size. For each item it gives:

- the lowest BIN,
- the median sale,
- how many sell a day,
- a price that sells it within 5 minutes.

An item's **worth** is the lower of its lowest BIN and median sale. You can pick just one in **Worth**.

Bazaar items get no estimate. For them, Taz asks for the latest bazaar price of the whole stack and uses what insta-selling it would get. Anything else SkyCofl doesn't estimate gets its lowest BIN by id.

If SkyCofl can't be reached, Taz uses its own price list (the one behind price tooltips) and tries SkyCofl again after 30 seconds. An item nobody prices is left out of the totals, and the HUD says so.

Prices are asked for once their side stops changing, all their new items in one go. They're kept for 3 minutes, so opening the trade again doesn't ask again. SkyCofl allows each player 30 lookups in 10 seconds. If it asks Taz to wait, the HUD says how long.

## The smart offer

How far under an item's worth Taz goes, added up:

| Part | How much |
|---|---|
| Your **Profit margin** | 5% unless you change it |
| Tax to sell it again | AH: 1% listing (2% from 10M, 2.5% from 100M), plus 1% claim tax over 1M. Bazaar: 1.25% |
| Slow sellers (AH only) | none at 20+ sales a day, 2% at 5+, 4% at 1+, 7% at one per 4 days, 10% under that, 8% when unknown |
| Unsteady price (AH only) | half of however much more than 10% under its worth it takes to sell within 5 minutes, at most 10% |
| Cheap items (AH only) | 3% under 1M |

It never goes further under than **Most under** (40%).

For example, take a clean Hyperion worth 456M that sells about once a day, and sells within 5 minutes at 388M. Taz goes 5% (margin) + 3.5% (tax) + 4% (slow) + 2.5% (unsteady) = 15% under, so 387M.

Prices provided by SkyCofl. Taz isn't affiliated with or endorsed by SkyCofl or Hypixel.
