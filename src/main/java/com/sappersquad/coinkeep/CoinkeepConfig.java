package com.sappersquad.coinkeep;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side config. SERVER rather than COMMON because these change
 * gameplay rules: on a multiplayer server the host decides, and the setting
 * must not differ per client.
 *
 * Defaults preserve the existing co-op behaviour exactly - balance is safe
 * on death. Turning dropBalanceOnDeath on converts this into a high-stakes
 * economy where killing a player can take their money, which is the basis
 * for a PvP/raiding server.
 */
public class CoinkeepConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue DROP_BALANCE_ON_DEATH;
    public static final ModConfigSpec.IntValue BALANCE_DROP_PERCENT;
    public static final ModConfigSpec.BooleanValue RESPECT_KEEP_INVENTORY;
    public static final ModConfigSpec.BooleanValue VAULT_OWNER_ONLY;
    public static final ModConfigSpec.BooleanValue ALLOW_VAULT_CRACKING;
    public static final ModConfigSpec.BooleanValue GIVE_BOOK_ON_FIRST_JOIN;
    public static final ModConfigSpec.IntValue BUY_TAX_PERCENT;
    public static final ModConfigSpec.IntValue SELL_TAX_PERCENT;
    public static final ModConfigSpec.IntValue SELL_PRICE_FLOOR_PERCENT;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Rules for what happens to a player's money when they die.").push("death");

        DROP_BALANCE_ON_DEATH = builder
                .comment(
                        "If true, a dying player's banked balance is converted into physical",
                        "banknotes and dropped at the death site, where anyone can pick it up.",
                        "Physical bills already drop on death regardless of this setting - this",
                        "controls the BANKED balance, which is normally safe.",
                        "Default false (co-op). Set true for PvP / raiding servers.")
                .define("dropBalanceOnDeath", false);

        BALANCE_DROP_PERCENT = builder
                .comment(
                        "How much of the balance drops, as a percentage. 100 drops everything;",
                        "a lower value keeps some banked money as a safety net so a single death",
                        "cannot wipe a long grind. Only used when dropBalanceOnDeath is true.")
                .defineInRange("balanceDropPercent", 100, 1, 100);

        RESPECT_KEEP_INVENTORY = builder
                .comment(
                        "If true, the keepInventory gamerule also protects the banked balance.",
                        "Set false if you want money to drop even on a keepInventory server -",
                        "gear is kept, but wealth is still at stake.")
                .define("respectKeepInventory", true);

        VAULT_OWNER_ONLY = builder
                .comment(
                        "If true, only the player who placed a vault can open it (ops bypass).",
                        "Without this anyone could walk up to a vault and empty it in one click,",
                        "which makes it less secure than a chest. A thief cannot break the block",
                        "either - cracking is their only route in.",
                        "Set false for shared team vaults on a co-op server.")
                .define("vaultOwnerOnly", true);

        ALLOW_VAULT_CRACKING = builder
                .comment(
                        "Allows the Vault Cracker item to empty another player's vault.",
                        "This is the intended way to raid: it costs a cracker each time, so",
                        "raiding is a deliberate investment rather than a free smash-and-grab.",
                        "Set false to make vaulted money completely untakeable - a thief can",
                        "never break a vault, so cracking is the only route in.")
                .define("allowVaultCracking", true);

        builder.pop();

        builder.comment(
                "Levers for controlling the money supply on a busy server.",
                "All three default to Coinkeep's existing behaviour, so leaving this",
                "section alone changes nothing.").push("economy");

        BUY_TAX_PERCENT = builder
                .comment(
                        "A surcharge added to every system-shop purchase, as a percentage.",
                        "20 means an item priced $100 costs $120. The difference is removed",
                        "from circulation, not paid to anyone.")
                .defineInRange("buyTaxPercent", 0, 0, 100);

        SELL_TAX_PERCENT = builder
                .comment(
                        "A cut taken from every system-shop sale, as a percentage.",
                        "20 means a $100 sale pays out $80. This is the stronger of the two",
                        "levers against inflation, because selling is where new money is",
                        "created.")
                .defineInRange("sellTaxPercent", 0, 0, 100);

        SELL_PRICE_FLOOR_PERCENT = builder
                .comment(
                        "How far a sell price can fall as you saturate demand for an item,",
                        "as a percentage of its base price. Default 15.",
                        "This is the knob for renewable-resource inflation: at 15, a",
                        "cobblestone farm still earns forever, just slowly. Set it to 0 and",
                        "a heavily-farmed item can become genuinely worthless until demand",
                        "recovers, which caps that income entirely.")
                .defineInRange("sellPriceFloorPercent", 15, 0, 100);

        builder.pop();

        builder.comment("First-time player setup.").push("onboarding");

        GIVE_BOOK_ON_FIRST_JOIN = builder
                .comment(
                        "Give every player the Coinkeep book the first time they join.",
                        "Strongly recommended: the book is the only way to discover that the",
                        "mod's quests, shop and keybinds exist. Without it a new player has to",
                        "craft one before they can find out there is anything to find out.",
                        "Given once per player, tracked so relogging never duplicates it.")
                .define("giveBookOnFirstJoin", true);

        builder.pop();
        SPEC = builder.build();
    }
}
