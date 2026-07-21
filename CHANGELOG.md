# Changelog

All notable changes to Coinkeep. Bump this in the same pass as `PUBLISHING.md` and
`README.md` — never one alone.

## 1.0.0 — Initial release (unreleased)

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
