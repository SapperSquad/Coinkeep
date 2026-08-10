# Changelog

All notable changes to Coinkeep. Bump this in the same pass as `PUBLISHING.md` and
`README.md` — never one alone.

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
