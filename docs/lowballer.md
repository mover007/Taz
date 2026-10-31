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

If SkyCofl's estimate can't be reached, Taz uses the price lists behind price tooltips (SkyCofl's lowest BINs, downloaded every 15 minutes, and Hypixel's bazaar) and tries the estimate again after 30 seconds. An item nobody prices is left out of the totals, and the HUD says so.

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

## Lowball Tracker

Open it with `/taz lowballs`, or `/taz` → Lowballer → Lowball Tracker → Open. It works while the Lowballer is on (switch it off with **Track lowballs**). Hover the **?** next to its title for a quick how-to.

### How it works

1. **You lowball.** Every trade where you get items is recorded when it goes through, one row per item, with what you paid. What you paid is your coins (less any coins they gave, plus what your own items were worth), split between their items by worth.
2. **You list it.** List it from the AH's Create Auction menu and its row says **On AH**, with the price you listed it at. The price comes from SkyCofl's copy of your auctions, about a minute after you list it.
3. **It sells.** Taz marks it **Sold** and works out your profit after tax. You don't have to do anything.

Every lowball is one of:

| | Means |
|---|---|
| **Not listed** | You still have it and it isn't on the AH. |
| **On AH** | You listed it. Its price shows in the Price column. |
| **Sold** | It sold, and the Profit column is final. |
| **Kept** | You're using it yourself. It's left out of the profit. |

The tabs show **All**, **Unsold** (Not listed and On AH) and **Sold**.

### The columns

- **Paid:** your share of the trade, and how far under its worth that was.
- **Price:** what it sold for. For an unsold item, its AH price, or **≈** its worth when it isn't listed.
- **Profit:** what it sold for, less tax, less what you paid. For an unsold item, **≈** is what it makes if it sells at that price, after AH tax.

Click a column title to sort by it; click again for the other way. Items from the same trade are joined by a red bar when the list is by date. Type in the search box to find an item or a player.

Hover a row for everything about it: who it was from and when, its worth then and now, its AH price, the tax, how long you held it, and the whole trade it came from ("795M for 4 items, sold so far 1.1B, profit if the rest sell ≈ +292M").

### How sales are noticed

| Sold | How Taz knows |
|---|---|
| On the AH | "[Auction] ... bought" or "You collected ... coins". If it sold while you were offline, SkyCofl's copy of your auctions says so the next time the menu opens. The listing fee and 1% claim tax come off. |
| On the bazaar | "[Bazaar] Sold" or "[Bazaar] Claimed". Selling part of a stack splits it into two rows. |
| To an NPC | "You sold ..." |
| In a trade | You give it in a trade: it sold for what you got for it. |

An auction that ends with no buyer (or that you cancel) goes back to **Not listed**.

### Changing a lowball

Click a row, then use the buttons under the numbers on the left:

- **Sold for…:** type what it sold for (950m, 1.2b), for a sale Taz didn't see.
- **Fix paid…:** type what you really paid for it. The rest of that trade's cost is shared between its other items.
- **Keep it** / **Not kept:** you're using it, or selling it after all.
- **Not sold:** it didn't sell after all.
- **Delete:** click twice.

### The numbers on the left

**Period** switches between all time, 30 days, 7 days and today.

- **Sold:** profit, how many sold and how many made money, average profit, return, average time to sell, best and worst.
- **Bought:** lowballs and coins spent, and how far under worth you paid on average.
- **Unsold now:** how many (and how many are on the AH), what you paid for them, what they're for sale at, and the profit if they sell at that.

**Refresh prices** asks SkyCofl for their worth and your AH listings right away (otherwise, when the menu opens, their worth if it's over 10 minutes old and your listings if they're over 2 minutes old). **Export CSV** saves every lowball as a spreadsheet, `config/taz/lowballer/lowballs.csv`.

Chat lines tell you when a lowball is tracked, when it's on the AH (with the profit if it sells) and when it sells; switch them off with **Chat lines**. Lowballs are saved per account in `config/taz/lowballer/`.

Prices provided by SkyCofl. Taz isn't affiliated with or endorsed by SkyCofl or Hypixel.
