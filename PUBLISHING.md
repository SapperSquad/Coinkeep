# Coinkeep — publishing kit (v1.0.0, UNRELEASED)

Store copy for the Modrinth / CurseForge project pages, plus the upload plan.
Bump this file in the same pass as `CHANGELOG.md` and `README.md` — never one alone.

> This is the **single** publishing doc. An earlier `promo/STORE-PAGE.md` was folded in
> here and deleted, because two copies of store copy drift apart silently.

**Files to upload — ADD as new versions; never delete older ones.**

| Upload as version | File | Game version tag | Loader |
|---|---|---|---|
| `1.0.0+mc1.21.1` | `build/libs/coinkeep-1.0.0.jar` | 1.21.1 | neoforge |

## Release blockers

- [ ] **Create the GitHub repo `SapperSquad/Coinkeep`.** The jar's `issueTrackerURL` and
      `displayURL` already point there, so those links 404 until it exists.
- [ ] **Play a real session.** Every number is defensible on paper and every exploit found has
      been closed, but nobody has judged the economy by *feel* — whether the grind is
      satisfying, whether $65k Mending is right, whether losing a carried vault is thrilling
      or just brutal.

### Cleared

- ~~Package was `com.example.moneymanager`~~ → now `com.sappersquad.coinkeep`.
- ~~README was a port-handoff note~~ → rewritten as product docs.
- ~~No CHANGELOG~~ → `CHANGELOG.md` created.
- ~~Item id `task_book` displayed as "Quest Book"~~ → now `coinkeep:ledger`, "Coinkeep Ledger".
- ~~No art~~ → full promo kit in `promo/`.

---

## Summary (the short-description field)

> Earn it. Bank it. Defend it. 180 quests, a market that reacts to what you sell, banknotes you
> can hold, and vaults other players can crack open.

**Modrinth summary (max 256 chars):**

> A full economy for Minecraft: 180 quests to earn from, a supply-and-demand market, banknotes
> you can actually hold, and vaults other players can crack open. Every quest and price is JSON,
> so modpacks can retune anything.

---

## Project description (paste into the body)

# Coinkeep

**Earn it. Bank it. Defend it.**

Most economy mods give you a number in a chat command. Coinkeep gives you **work that pays**, a
market that **reacts to what you sell**, cash you can **hold in your hand**, and a vault someone
can **crack open** — a full loop, not a balance field.

## Quests — always something paying

**180 quests across 10 chapters**: Mining, Combat, Tools & Armor, Building, Farming, plus one
chapter per vanilla advancement tab.

- Nothing to accept and no NPC to find. Play, and progress tracks itself.
- **Gathering quests repeat forever** on escalating tiers. Progress is a lifetime total that is
  never reset — mine 40 iron and all 40 count toward the next threshold.
- **Almost nothing is gated.** Only 10 dependencies exist in the entire book, and each is a real
  requirement.
- Two ladders show progression: within a quest's tiers, and across its whole chapter.

## The Market — supply and demand

- **Buy** from a categorised catalog, including **Rare** finds (Elytra, Totem, Nether Star) and
  **Signature** gear — named, pre-enchanted endgame pieces like *The Prospector*.
- **Sell** your haul. Each sale saturates that item's demand, so it pays a little less than the
  last, and demand recovers over time. Grinding one block forever stops being worth it, without
  anything ever being locked.
- Demand is **per player**, so nobody can crash the market for everyone else.

## Cash you can actually carry

Eighteen denominations, **$1 to $100,000,000**. Real items you can stack in a chest, hand to
another player, or lose in lava. Your banked balance survives death — **the cash in your pockets
does not.**

## Vaults & robbery

- Money in a vault is **never lost on death**.
- **Only the owner can open it — and only the owner can break it.** A thief can never destroy
  someone's savings.
- Break your own and you get **the whole vault back as an item, money still inside.** Place it
  anywhere and it is all still there.
- **Carrying a loaded vault is the risk.** It is an ordinary item, so dying drops it — and
  whoever loots it gets a *locked* vault they will have to crack.
- A **Vault Cracker** is the only way to rob one. You cannot see how much is inside before you
  commit. Cracking clears the claim, so the emptied vault is then free to take.

## Built for modpacks

Every quest, chapter and shop entry is a JSON file in a datapack registry. Add, retune or
override anything without touching the jar. Items from any other mod work by id. `/reload`
applies changes live, and content is validated on load so a typo cannot silently strand a quest.

## Commands

| | |
|---|---|
| `/balance` | check your balance |
| `/buy` · `/sell` | trade without the GUI |
| `/pay` | send money to another player |
| `/withdraw` · `/depositall` | convert balance to and from banknotes |
| `/addbalance` | operator payouts |
| *keys* | **J** Ledger · **K** straight to the Shop (both rebindable) |

Built for **NeoForge 1.21.1**. Balances, quest progress and market demand are stored per player
as data attachments — nothing to migrate, nothing to corrupt, and no custom packets.

---

## Gallery upload plan

Art lives in `promo/`.

| File | Use |
|---|---|
| `icon-512.png` | Project icon (both stores) |
| `banner-1920x640.png` | CurseForge header |
| `gallery-1-quests.png` | Gallery 1 |
| `gallery-2-market.png` | Gallery 2 |
| `gallery-3-cash.png` | Gallery 3 |
| `gallery-4-vaults.png` | Gallery 4 |
| `gallery-5-datapack.png` | Gallery 5 |

Real in-game screenshots would strengthen this further — the Ledger open on Quests, the Sell tab
with demand bars visible, a chest full of mixed denominations, and a vault being cracked.

**Any art stating a count (180 quests, 66 shop entries, 18 denominations) must be re-checked
every release.** Images cannot be grepped, so they go stale invisibly. Regenerate with
`scratchpad/gen-coinkeep-promo.ps1`.

---

## Changelog for the 1.0.0 upload

Paste into the **changelog field on the version upload** (both stores have one). This is the
short, per-upload note — `CHANGELOG.md` in the repo is the long-form history. Keep them in step.

> **1.0.0 — Initial release.** A full economy loop for NeoForge 1.21.1: earn it, bank it,
> defend it.
>
> - **180 quests across 10 chapters**, covering mining, combat, gear, building, farming and
>   every vanilla advancement tab. Gathering quests repeat forever on escalating tiers, and
>   progress is a lifetime total that is never reset.
> - **A market with supply and demand.** Buy from a 66-entry catalog, or sell your haul —
>   every sale saturates that item's demand, and prices recover over time, so variety beats
>   grinding one block.
> - **Cash you can hold.** 18 banknote denominations from $1 to $100,000,000. Your banked
>   balance survives death; the cash in your pockets does not.
> - **Vaults and robbery.** Only the owner can open *or break* a vault. Break your own and it
>   comes back as an item with the money still inside — so carrying it is the risk. A Vault
>   Cracker is the only way to rob one.
> - **Signature gear and rare finds** — named, pre-enchanted endgame pieces, plus Elytra,
>   Totems, Nether Stars and more as money sinks.
> - **Fully datapack-driven.** Every quest, chapter and shop entry is JSON; modpacks can add,
>   retune or override anything without touching the jar, and `/reload` applies it live.
>
> English only in 1.0.0; localisation support is planned for 1.0.1.

---

## Platform facts

- Modrinth project id: *(not created yet)*
- CurseForge project id: *(not created yet)*
- Minecraft **1.21.1** · NeoForge **21.1.235**
- Environment: **client and server required**
- Categories: `economy`, `adventure`, `game-mechanics`, `utility`
- Tags: economy, quests, money, shop, market, vault, currency, multiplayer
- License: **All Rights Reserved** (`mod_license` in `gradle.properties`). Fine for a standalone
  gameplay mod. Worth noting it discourages pack makers who want to tweak shop contents — since
  all content is datapack JSON, an explicit "packs may redistribute and retune" note in the
  description would remove that friction without changing the license.
- Donation: `ko-fi.com/sappersquad`
- Discord: https://discord.gg/mZ9CG6xh2A
- Platforms that received the last release: **none yet — unpublished.**
