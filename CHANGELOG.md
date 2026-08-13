# Changelog

All notable changes to Coinkeep. Bump this in the same pass as `PUBLISHING.md` and
`README.md` — never one alone.

## 1.3.0+1.21.11 — The same 1.3.0, for Minecraft 1.21.11 (2026-08-13)

A straight port of 1.3.0 to **Minecraft 1.21.11 / NeoForge 21.11.45** — same features,
same content, new target. Verified: all 13 GameTests pass on the rebuilt (registry-based)
test framework, and the book, shop and recipes were smoke-tested in a live client.

### Upgrade note — only if you carry a 1.21.1 world across
- **Stored balances, market demand and the starter-book flag do not carry over** when a
  1.21.1 world is opened on 1.21.11 — NeoForge changed how data attachments are stored
  between those Minecraft versions, so the old data is skipped (a warning appears in the
  log). Vault contents and banknotes survive, since they live in ordinary block/item data.
  On a live server: have players **vault their money or withdraw it as banknotes** before
  upgrading, then deposit again after.
- Fresh 1.21.11 worlds are unaffected.

## 1.3.0 — Economy levers, grid view, and a faster quest engine (2026-08-09)

Round two of player feedback: server owners asked for inflation controls, purchase limits
and a denser shop layout; a profiling pass found the quest engine scaling badly with
modpack-sized quest books.

### Added
- **Grid view for the Shop** — a toggle on the mode strip. Tiles show icon, name and price
  (the requested format), fitting ~20 items on screen instead of ~8. Search filters the
  grid too, sell mode shows held counts in tile corners, and the preference persists.
- **Selective tooltips.** Hovering a shop entry shows a tooltip only when it says more than
  the tile already does: enchantments, modded stat lines, purchase limits, or a name the
  tile had to shorten. Plain items get no box — their row already shows name, price and
  affordability. The "Click to buy" hint line is gone for the same reason.
- **Signature gear shows its enchantments.** The tooltip renders the exact stack a purchase
  builds (one shared `createStack`), so what you see is what `/buy` delivers, and modded
  items bring their own tooltip lines along.
- **Purchase limits**: shop entries take `buy_limit` — how many times each player may ever
  buy that entry. Tracked per player, enforced in the command (not just the GUI), shown on
  the row and in the tooltip.
- **Transaction taxes** (`buyTaxPercent`, `sellTaxPercent`, both default `0` = off). The
  tax is destroyed rather than collected — the point is removing money from circulation.
  Quoted in the GUI exactly as the server will charge it.
- **Configurable sell-price floor** (`sellPriceFloorPercent`, default `15` = old behaviour).
  At `0`, a heavily-farmed renewable becomes worthless until demand recovers — the lever
  against cobblestone-farm inflation.
- **Sidebars size themselves to their labels** (clamped), so "Vaults & Robbery" in the
  Guide and long modded chapter names stop truncating.
- **Breathing room** between the header, tab strip, search field and lists.
- **Four quest-index GameTests** (13 total) proving the new trigger index returns exactly
  what the old full scan did.

### Changed
- **Quest triggers are indexed.** Every block break, mob kill and craft used to scan the
  entire quest book per event, per player — fine at 180 quests, painful at a modpack's
  1,000+. It is now a single hash lookup whose cost does not grow with quest count.

### Fixed
- **Machines can no longer earn quest rewards.** Automated miners acting through fake
  players sailed through the player check; progress and rewards landed on the machine's
  phantom profile, and one-shot item rewards went to a fake inventory some machine mods
  can collect from. Quests now pay only real players; automation earns by selling.
- **The vault-crack victim is actually told now.** Cracking clears the vault's claim, and
  the "your vault was cracked" notification checked the owner *after* it was cleared — so
  the message never sent, ever. The owner is remembered before the claim is wiped.

### Changed — nothing a server or datapack has to do
- All new config keys default to existing behaviour; all new JSON fields are optional.
  A 1.1.0/1.2.0 world, datapack and addon load unchanged.

## 1.2.0 — A bigger book, and search (2026-08-09)

The first release driven by player feedback rather than a plan: with a modpack's worth of
quests, the book was too small and offered no way to find anything in it.

### Added
- **The book is responsive.** It grows with the window instead of sitting at a fixed
  420x282, clamped to 360-640 wide by 200-400 tall so it still fits at GUI scale 4 on a
  1080p screen and never sprawls on an ultrawide. At scale 2 that is over twice the area
  and about ten visible quest rows instead of six.
- **Search on the Quests and Shop tabs.** On Quests it deliberately searches **every
  chapter**, not just the open one — with hundreds of quests, "which chapter is this in?"
  *is* the problem, so a search scoped to the current chapter would not have solved it.
- **Search matches the trigger target**, not just names and descriptions. Typing
  `deepslate`, or a modded id like `create:andesite`, finds the quest by the block it is
  about. This is the case players actually hit with custom blocks.
- **Live match count** beside the field, so an empty list reads as "no matches" rather
  than looking broken. Clicking a chapter clears the search and returns to browsing.
- **Three vault GameTests** (9 total). They assert the invariant the network imposes —
  every synced slot must survive a signed-short round trip — and were verified to *fail*
  against the old code before passing against the fix.

### Fixed
- **Vault balances were corrupted for remote players on a server.** The balance was
  carried across two 32-bit `ContainerData` slots, but vanilla's
  `ClientboundContainerSetDataPacket` serializes each slot with `writeShort` — 16 bits.
  Anything above 32,767 was mangled in transit: a $210,000 vault displayed as $13,392, and
  a $100,000 vault as $4,294,936,224. It is now carried across four 16-bit slots.

  This survived two releases because **every environment it could be tested in bypasses
  the codec**: single-player, the LAN *host* (an in-memory connection passes packet objects
  without serializing), and GameTests. It only ever appeared for a genuine remote guest.

  **Saved data was never affected** — the balance lives in NBT and only the synced display
  was wrong — so no world migration is required. Servers should still update.

### Changed — nothing a server or datapack has to do
- No content, registry, config or save-format changes. A 1.1.0 world, datapack and addon
  all load unchanged.

## 1.1.0 — Shop categories are data (2026-08-09)

Shop categories were a hardcoded Java enum, so a companion mod could not add one — every
entry it shipped had to be squeezed into one of Coinkeep's eight, and a big addon swamped
Coinkeep's own browsing. They are a **datapack registry** now, exactly like `quest_line`,
`quest` and `shop_entry`.

### Added
- **`data/<namespace>/coinkeep/shop_category/<id>.json`** — any mod or datapack can define
  its own Shop tab. Fields: `id`, `name`, optional `sort_order` (defaults 100) and optional
  `icon`. Documented in the README.
- **Category icons.** A category may name its own icon item; without one the sidebar keeps
  doing what it always did and draws the category's cheapest entry.
- **Empty categories never render**, so a tab appears exactly when the mod that defines it
  is installed and vanishes with it.
- **Validator coverage.** Server start and every `/reload` now also report a shop entry that
  names a category nobody defined, and log every category with its entry count.
- **First GameTests** (6, `./gradlew runGameTestServer`) — written specifically because 1.0.0
  is published and this is a data migration on live worlds. They pin the eight built-ins,
  their sidebar order, the per-category entry counts, that 1.0.0-shaped JSON still parses,
  the addon contract, and that all four content registries are declared with a **network
  codec** (a server-only registry would leave the client's book and shop empty — Coinkeep's
  own hard-won lesson, now an assertion instead of a comment).

### Changed — nothing a server or datapack has to do
- The eight built-ins (`food`, `weapons`, `armor`, `enchantments`, `ores`, `materials`,
  `rare`, `signature`) ship as Coinkeep's own JSON with the same ids, the same labels and
  the same sidebar order. **A 1.0.0 world, and any 1.0.0 datapack, loads unchanged.**
- `ShopEntry.category` is a `String` id instead of an enum constant. JSON is byte-identical
  (`"category": "rare"`), and an upper-case spelling still works.
- An entry naming an unknown category used to **throw inside the codec and lose the entire
  entry** — one typo silently deleted a purchasable item. It now lands in a placeholder tab
  at the end of the sidebar and the validator names the missing id.

### Upgrade notes
- **Drop-in for servers and clients.** No world data, config or datapack change is required.
  The only visible difference on an unmodified install is the extra category lines in the
  server log at startup.
- **For mod authors:** `ShopEntry.category()` returns `String` rather than `ShopCategory`.
  Anything compiled against 1.0.0 that read that field needs a recompile. Nothing else in
  the API moved — `BalanceHelper` is untouched.

## 1.0.0 — Initial release (live on Modrinth and CurseForge)

A full economy loop for NeoForge 1.21.1: earn it, bank it, defend it.

### Quests
- **180 quests across 10 chapters** — Mining, Combat, Tools & Armor, Building, Farming, plus one
  chapter per vanilla advancement tab (Story, Nether, The End, Adventure, Husbandry).
- Progress tracks automatically off block breaks, mob kills, crafting and advancements — no
  turn-in step and no NPC.
- **Repeatable tiers** on gathering quests. Progress is a lifetime total that is never reset;
  clearing a tier raises the bar instead. Later tiers pay more in total but less per item, so it
  always pays to diversify.
- Only 10 dependencies exist in the whole book, each a genuine requirement.
- Quests sort by what is actionable, with hoverable tier and chapter ladders.

### Market
- **66 shop entries** across 8 categories, including **Rare** finds and named, pre-enchanted
  **Signature** gear.
- **Selling** with per-player supply and demand: each sale saturates that item, prices recover
  over time, and buy prices stay fixed as the stable anchor.
- Enchanted or renamed items are unsellable, so valuable gear can never be cashed in at the
  price of a plain copy.

### Cash
- **18 banknote denominations**, $1 to $100,000,000, as real items.
- Withdraw and deposit from the Ledger's Cash tab; `/pay` transfers directly.
- Banked balance survives death; carried cash does not.

### Vaults & robbery
- Craftable or purchasable vault. Money inside is never lost on death.
- Only the owner can open **or break** a vault — a thief cannot destroy someone's savings.
- Breaking your own returns the vault **as an item with the money still inside**, so it can be
  relocated — and dropped on death, where whoever loots it gets a locked vault.
- The **Vault Cracker** (netherite-tier, consumed on use) is the only way to rob one. Cracking
  clears the claim, freeing the emptied vault to be taken.

### Data-driven
- Every quest, chapter and shop entry is a JSON file in a synced datapack registry.
- Modpacks can add, retune or override anything without touching the jar; items from any other
  mod work by id; `/reload` applies changes live.
- Content is validated on server start and every reload — dangling dependencies, duplicate ids
  and dependency cycles are all reported.

### Server config
`serverconfig/coinkeep-server.toml`, per world, hot-reloading:
`dropBalanceOnDeath`, `balanceDropPercent`, `respectKeepInventory`, `vaultOwnerOnly`,
`allowVaultCracking`, `giveBookOnFirstJoin`.

### Notes
- No custom network packets anywhere — balances, quest progress and market demand ride NeoForge
  synced data attachments, and the vault GUI uses vanilla's menu-button channel.
- English only for 1.0.0; localisation support is planned for 1.0.1.
