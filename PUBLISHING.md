# Coinkeep — publishing kit (v1.2.0)

Store copy for the Modrinth / CurseForge project pages, plus the upload plan.
Bump this file in the same pass as `CHANGELOG.md` and `README.md` — never one alone.

> This is the **single** publishing doc. An earlier `promo/STORE-PAGE.md` was folded in
> here and deleted, because two copies of store copy drift apart silently.

> **STATUS (resolved 2026-08-09): 1.0.0 IS LIVE on Modrinth and CurseForge.**
> The old pre-release blocker list is gone — it described a world before the
> upload. **1.1.0 is an update on a live listing**, and it was built as one:
> the migration tests in `gametest/ShopCategoryGameTests` pin that a 1.0.0
> world and any 1.0.0 datapack load unchanged.

> **1.2.0 carries a multiplayer data-correctness fix.** The vault balance was
> synced across two 32-bit menu slots, but vanilla serializes each slot as a
> **16-bit short** — so on a real server a remote player saw a corrupted vault
> balance (a $210,000 vault read as $13,392). It was invisible in single-player,
> to a LAN host, and to GameTests, which is why it survived two releases.
> **Saved data was never affected** — the balance is stored in NBT and only the
> display was wrong — so no world migration is needed. Anyone running a
> multiplayer server should still update.

## 1.1.0 upload — order matters

**Coinkeep 1.1.0 must be live on BOTH stores before Highroller 2.0.0 goes up
anywhere.** Highroller 2.0.0 hard-requires `coinkeep [1.1.0,)` — a player who
installs Highroller 2.0.0 while the stores' latest Coinkeep is 1.0.0 gets a
mod that refuses to load. Upload here first, then Highroller, and pin the
dependency version on Highroller's listings (its PUBLISHING.md has the full
launch checklist).

**Files to upload — ADD as new versions; never delete older ones.**

| Upload as version | File | Game version tag | Loader |
|---|---|---|---|
| `1.0.0+mc1.21.1` | `build/libs/coinkeep-1.0.0.jar` | 1.21.1 | neoforge |
| `1.1.0+mc1.21.1` | `build/libs/coinkeep-1.1.0.jar` | 1.21.1 | neoforge |
| `1.2.0+mc1.21.1` | `build/libs/coinkeep-1.2.0.jar` | 1.21.1 | neoforge |

> **Redeploy note.** Highroller requires Coinkeep **1.1.0+** from its 2.0.0 build onward,
> so Alex's server and BOTH players' clients need the new Coinkeep jar in the same pass as
> the new Highroller jar. A 1.1.0 client against a 1.0.0 server (or vice versa) will not
> agree on the shop-category registry.

## 1.2.0 release state (verified 2026-08-09)

- **Build green**, jar `build/libs/coinkeep-1.2.0.jar`; **9/9 GameTests green**
  (`./gradlew runGameTestServer`) — the six category-migration tests plus three
  new vault-sync tests.
- **The vault tests were proved, not assumed.** They were run against the old
  two-slot code and *failed* (`$1 reached a remote client as $65536`) before
  being run against the fix and passing. A regression test that passes on the
  broken code would have been worthless here, because the in-memory path the
  test runs on is exactly what hid the bug.
- **Docs in step**: CHANGELOG, README (bigger book + search) and this file all
  speak 1.2.0.
- **Still unverified by a human**: the new panel size and the search UX. The
  sizing cap (640x400) is a judgement call from one piece of player feedback,
  not a measurement.

## 1.1.0 release state (verified 2026-08-09)

- **Build green**, jar `build/libs/coinkeep-1.1.0.jar`; **6/6 GameTests green**
  (`./gradlew runGameTestServer` — the built-ins, sidebar order, per-category
  counts, 1.0.0-JSON migration, the addon contract, network-codec assertion).
- **Docs in step**: CHANGELOG, README (data-driven categories + the addon
  how-to) and this file all speak 1.1.0.
- **GitHub**: repo `github.com/SapperSquad/Coinkeep` exists and is the
  configured remote; the 1.1.0 commit is local-only until the orchestrator's
  push. (The old "create the repo" blocker is history — it exists, and the
  jar's `displayURL`/`issueTrackerURL` resolve.)
- The 1.0.0-era blocker list (repo creation, feel-pass) is retired: 1.0.0
  shipped and is live on both stores.

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

Every quest, chapter, **shop category** and shop entry is a JSON file in a datapack registry.
Add, retune or override anything without touching the jar. Items from any other mod work by id.
`/reload` applies changes live, and content is validated on load so a typo cannot silently
strand a quest.

Companion mods get their **own Shop tab** rather than being dumped into Materials: drop a
`shop_category` JSON with an id, a name and a sort order, point your entries at it, and the tab
appears exactly when your mod is installed and disappears with it. **Highroller** (our casino
mod) is the worked example — its 90 machines, tables and decor blocks all sell from a
Highroller tab of their own.

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

## Changelog for the 1.2.0 upload

Paste into the **changelog field on the version upload**.

> **1.2.0 — A bigger book, and search.**
>
> Straight from player feedback: with a modpack's worth of quests, the book was
> too small and there was no way to find anything.
>
> - **The book scales with your window** instead of sitting at a fixed size —
>   up to 640x400, which is over twice the area and roughly ten visible quest
>   rows instead of six. Long modded item names finally have somewhere to go.
> - **Search, on both Quests and Shop.** On Quests it searches **every chapter
>   at once**, not just the one you have open — "which chapter is this in?" was
>   the actual problem. It matches a quest's name, its description, *and the
>   block or item it is about*, so typing `deepslate` or a modded id like
>   `create:andesite` finds it. A live match count sits beside the field, and
>   clicking a chapter clears the search and returns you to browsing.
> - **Fixed: vault balances were wrong for other players on a server.** The
>   balance was synced across two 32-bit slots, but Minecraft sends each slot as
>   a 16-bit value — so a $210,000 vault showed as $13,392 to anyone but the
>   owner's own client. Your saved money was never affected, only what remote
>   players saw. **If you run a server, update.**
>
> Nine automated tests now run on every build, including three that pin the
> vault against exactly this class of bug.

## Changelog for the 1.1.0 upload

Paste into the **changelog field on the version upload**:

> **1.1.0 — Shop categories are data.**
>
> Shop categories used to be hardcoded, so a companion mod had no way to add one — every
> item it sold got squeezed into one of Coinkeep's eight, and a big addon buried Coinkeep's
> own catalog. Now any mod or datapack can define its own Shop tab:
> `data/<namespace>/coinkeep/shop_category/<id>.json`, with a name, a sort order and an
> optional icon. A tab appears exactly when the mod that defines it is installed, and
> disappears with it.
>
> The first mod to use it is our own: **Highroller 2.0.0** ships a **Highroller** tab with
> all 90 of its casino entries — slot machines to a $250,000 Grand Casino blueprint —
> instead of burying them in Materials and Rare. (Which is why Highroller 2.0.0 requires
> Coinkeep 1.1.0+.)
>
> - **Nothing to do on upgrade.** The eight built-ins ship as Coinkeep's own JSON with the
>   same ids, labels and order, so existing worlds and existing datapacks load unchanged —
>   `"category": "rare"` still means what it always meant.
> - **A typo no longer eats an item.** An entry naming an unknown category used to fail its
>   whole entry and vanish from the shop; it now lands in a clearly-named placeholder tab
>   and the content validator reports the missing id on load.
> - **Categories can carry their own icon**; without one the sidebar still shows the
>   category's cheapest entry.
> - Startup now logs every category and how many entries it holds.
> - **Mod authors:** `ShopEntry.category()` returns a `String` id rather than an enum, so
>   anything compiled against 1.0.0 that read that field needs a recompile. `BalanceHelper`
>   is untouched.

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

- Modrinth project: **live** (1.0.0 uploaded) — *record the URL/project id here on the next
  store visit*
- CurseForge project: **live** (1.0.0 uploaded) — *record the URL/project id here on the next
  store visit*
- Minecraft **1.21.1** · NeoForge **21.1.235**
- Environment: **client and server required**
- Categories: `economy`, `adventure`, `game-mechanics`, `utility`
- Tags: economy, quests, money, shop, market, vault, currency, multiplayer
- License: split policy (Alex, 2026-07-28) — **All Rights Reserved** on the Modrinth/CurseForge
  listing (`mod_license`/jar metadata match), **MIT** LICENSE in the GitHub repo. Pack makers who
  want to tweak shop contents can be pointed at the MIT source.
- Donation: `ko-fi.com/sappersquad`
- Discord: https://discord.gg/mZ9CG6xh2A
- Platforms that received the last release: **Modrinth + CurseForge, version 1.0.0.**
  Next up: **1.1.0 to both** — and it must land BEFORE Highroller 2.0.0 (see the
  upload-order note at the top).
