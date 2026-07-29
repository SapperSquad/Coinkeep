# Coinkeep

**Earn it. Bank it. Defend it.**

A full in-game economy for **Minecraft 1.21.1 / NeoForge 21.1.235** — quests to earn from,
a market that reacts to what you sell, banknotes you can hold, and vaults other players
can crack open.

Mod id `coinkeep`. Published by **SapperSquad**.

---

## The book

Everything lives in one item, the **Coinkeep Ledger**. You are given one the first time
you join; craft another with a book + a gold nugget. Press **J** to open it anywhere,
or **K** to open it straight on the Shop tab.

Four tabs: **Quests**, **Shop**, **Cash**, **Guide**.

---

## Quests

**180 quests across 10 chapters** — Mining, Combat, Tools & Armor, Building, Farming,
plus one chapter per vanilla advancement tab (Story, Nether, The End, Adventure,
Husbandry), so the book covers the whole base game.

- Nothing to accept. Play, and progress tracks itself.
- **Gathering quests repeat forever** on escalating tiers. Progress is a lifetime running
  total that is never reset — mine 40 iron and all 40 count toward the next threshold.
  Later tiers pay more in total but slightly less per item, so it always pays to spread out.
- **Almost nothing is gated.** Only 10 dependencies exist in the whole book, and each is a
  real requirement (crafting tiers; the Wither needs skulls from wither skeletons; a beacon
  needs a nether star).
- Quests sort by what is actionable: in progress, untouched, locked, then complete.
- **Two ladders** show progression — the tier ladder inside a quest, and the chapter ladder
  across its siblings. Hover any pip for its exact requirement and payout.

## Shop & Market

- **Buy** from a categorised catalog, cheapest first, priced green or red by affordability.
  Categories include **Rare** (Elytra, Totem, Nether Star…) and **Signature** — named,
  pre-enchanted endgame gear like *The Prospector* at $210,000.
- **Sell** your haul. Each sale saturates that item's demand, so it pays a little less than
  the last, and demand recovers over time. Grinding one block forever stops being worth it
  without anything ever being locked.
- Demand is **per player**, so nobody can crash the market for everyone else.
- Buy prices are fixed, and the spread means buying and re-selling can never be a money loop.
- Anything enchanted or renamed is **unsellable**, so a $210k named tool can never be cashed
  in at the price of a plain one.

## Cash

**18 banknote denominations**, $1 to $100,000,000.

- Withdraw your balance as notes; shift-click for as many as you can afford.
- **Deposit all bills** banks everything you are carrying at once.
- Your banked balance survives death. **Cash in your pockets does not.**
- `/pay` sends money directly; handing over notes works too.

## Vaults & robbery

- Craft a vault (4 iron blocks, 4 gold blocks, 1 diamond block). Money inside is **never
  lost on death**, even where the server drops balances.
- **Only the owner can open it — and only the owner can break it.** A thief cannot destroy
  someone's savings.
- Breaking your own vault returns it **as an item with the money still inside**, so you can
  relocate. The tooltip shows the amount.
- Carrying a loaded vault is the risk: it is an ordinary item, so dying drops it, and
  whoever loots it gets a *locked* vault they must crack.
- A **Vault Cracker** (netherite-tier, consumed on use) is the only way to rob one. You
  cannot see how much is inside before committing. Cracking also clears the claim, so the
  emptied vault can then be broken and taken.

---

## Adding your own content

Every quest, chapter and shop entry is a JSON file in a datapack registry, so a modpack can
add, retune or override anything without touching the jar. `/reload` applies changes live.

```
data/<namespace>/coinkeep/quest_line/<id>.json
data/<namespace>/coinkeep/quest/<id>.json
data/<namespace>/coinkeep/shop_entry/<id>.json
```

A quest is usually this short — name and icon are optional and derived from the trigger, so
nothing ever renders unnamed or icon-less:

```json
{
  "id": "mine_coal",
  "line": "mining",
  "trigger": "block_break",
  "target": "minecraft:coal_ore",
  "count": 32,
  "rewards": [ { "type": "money", "amount": 200 } ]
}
```

**Quest fields:** `trigger` is one of `block_break`, `mob_kill`, `item_craft`, `advancement`.
Optional: `name`, `description`, `icon`, `dependencies`, `max_tier` (1 = one-shot,
0 = repeats forever), `target_growth`, `reward_growth`.

**Reward types** — every reward states what it gives, so a command reward never renders as
the bare word "command":

```json
{ "type": "money",   "amount": 500 }
{ "type": "item",    "item": "minecraft:diamond", "count": 3 }
{ "type": "command", "command": "effect give @p minecraft:resistance 600 1",
                     "label": "10 min Resistance II", "icon": "minecraft:potion" }
```

**Shop entries** take `category`, `item`, `count`, `price`, and optionally `sell_price`
(defaults to 40% of the per-unit buy price), `saturation`, `name`, and an `enchantments`
list for pre-enchanted gear. Items from any other mod work by id.

Content is validated on server start and on every `/reload`: dangling dependency ids,
duplicate ids and dependency cycles are all reported, because a typo would otherwise leave a
quest silently unreachable.

---

## Commands

| Command | Purpose |
|---|---|
| `/balance` | Show your balance |
| `/buy <id>` | Buy a catalog entry |
| `/sell <id> [qty]` | Sell items at the current market price |
| `/pay <player> <amount>` | Send money to another player |
| `/withdraw <denom> [qty]` | Turn balance into banknotes |
| `/depositall` | Bank every note you are carrying |
| `/addbalance <player> <amount>` | Op-only; for other mods' reward systems |

## Server config

`serverconfig/coinkeep-server.toml`, per world. Hot-reloads.

| Setting | Default | Effect |
|---|---|---|
| `dropBalanceOnDeath` | `false` | Convert a dying player's balance into notes at the death site |
| `balanceDropPercent` | `100` | How much drops, when the above is on |
| `respectKeepInventory` | `true` | The keepInventory gamerule also protects the balance |
| `vaultOwnerOnly` | `true` | Only the placer can open a vault |
| `allowVaultCracking` | `true` | Set false to make vaulted money completely untakeable |
| `giveBookOnFirstJoin` | `true` | Hand every new player a Ledger |

---

## Building

Requires **JDK 21**.

```
./gradlew build        # produces build/libs/coinkeep-1.0.0.jar
./gradlew runClient    # dev client
```

Quest and shop content only loads once a **world** loads, so a client sitting at the title
screen proves nothing. To boot straight into a world for testing:

```
./gradlew runClient -PquickPlay="New World"
```

## Design notes

- **No custom network packets.** Balance, quest progress and market saturation all ride
  NeoForge **synced data attachments**; the vault GUI uses vanilla's `clickMenuButton`.
  That is also why the datapack registries are declared with a network codec — a
  server-only registry would leave the client's book empty.
- **Quest progress is keyed by string id**, so renaming a quest in JSON resets that quest's
  progress rather than corrupting a save.
- **A vault's money and owner live on the item** via a data component, which is what lets a
  broken vault be carried, dropped on death, and still need cracking by whoever finds it.

## Copyright

Copyright (c) 2026 SapperSquad. All rights reserved.

No license is granted: this work may not be copied, modified,
redistributed, or included in other distributions without express
permission from the copyright holder.
