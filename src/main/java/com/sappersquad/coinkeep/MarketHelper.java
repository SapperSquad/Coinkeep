package com.sappersquad.coinkeep;

import net.minecraft.world.entity.player.Player;

import java.util.Map;

/**
 * The market's pricing model.
 *
 * Selling an item saturates your buyers for it, so each sale is worth a
 * little less than the last, and demand recovers over time. That does three
 * jobs at once:
 *
 *  - it makes money renewable, which fixes the economy having no faucet
 *    (every quest pays exactly once, so without this the total money in a
 *    world is fixed forever);
 *  - it makes grinding one cheap item forever pointless without ever
 *    blocking it, so the pressure is to diversify rather than to obey a
 *    gate. Nothing is ever locked;
 *  - buy prices stay fixed, so the tuned catalog remains the stable anchor
 *    and the buy list never reorders under you.
 *
 * Buy price is always well above sell price (see ShopEntry.DEFAULT_SELL_RATIO),
 * so buying and re-selling can never be an infinite money loop.
 */
public class MarketHelper {

    /**
     * Sell price never falls below this fraction of base.
     *
     * Server-configurable, because it is the lever on renewable-resource
     * inflation: above zero, a cobblestone farm earns forever at the floor
     * rate; at zero, a saturated item becomes worthless until demand recovers.
     * Defaults to 0.15, which is the behaviour every existing world has.
     */
    public static double priceFloor() {
        return CoinkeepConfig.SELL_PRICE_FLOOR_PERCENT.get() / 100.0;
    }

    /** Saturation recovered per in-game day. */
    public static final int RECOVERY_PER_DAY = 48;

    private static final long TICKS_PER_DAY = 24000L;

    public static MarketData data(Player player) {
        return player.getData(ModAttachments.MARKET);
    }

    private static void save(Player player, MarketData data) {
        player.setData(ModAttachments.MARKET, data);
    }

    /**
     * How healthy demand is for an item, 0..1. 1.0 = untouched, 0.5 = you
     * have sold exactly its saturation count. Shown in the UI as a percentage
     * so the number is always legible rather than a mystery multiplier.
     */
    public static double demand(Player player, ShopEntry entry) {
        int sold = data(player).soldOf(entry.id());
        int saturation = entry.effectiveSaturation();
        double factor = (double) saturation / (saturation + sold);
        return Math.max(priceFloor(), factor);
    }

    /** Current per-unit sell price, after saturation. Always at least $1. */
    public static long sellPrice(Player player, ShopEntry entry) {
        return Math.max(1L, Math.round(entry.baseSellPrice() * demand(player, entry)));
    }

    /**
     * What a run of {@code quantity} sales actually pays. Priced per unit as
     * saturation climbs during the sale, so dumping a stack is worth less
     * than selling it piecemeal - without needing a separate rule to say so.
     */
    public static long quoteBulk(Player player, ShopEntry entry, int quantity) {
        int sold = data(player).soldOf(entry.id());
        int saturation = entry.effectiveSaturation();
        long base = entry.baseSellPrice();
        long total = 0L;
        for (int i = 0; i < quantity; i++) {
            double factor = Math.max(priceFloor(), (double) saturation / (saturation + sold + i));
            total += Math.max(1L, Math.round(base * factor));
        }
        return total;
    }

    /** How many of a limited entry this player has already bought. */
    public static int boughtCount(Player player, ShopEntry entry) {
        return data(player).boughtOf(entry.id());
    }

    /** How many more they may buy, or -1 when the entry is unlimited. */
    public static int remainingPurchases(Player player, ShopEntry entry) {
        if (!entry.hasBuyLimit()) {
            return -1;
        }
        return Math.max(0, entry.buyLimit() - boughtCount(player, entry));
    }

    /** Records a completed purchase against a limited entry. Server-side only. */
    public static void recordPurchase(Player player, ShopEntry entry) {
        if (!entry.hasBuyLimit()) {
            return;   // nothing to track, so nothing to persist
        }
        MarketData data = data(player);
        data.addBought(entry.id(), 1);
        save(player, data);
    }

    /** Records a completed sale. Server-side only. */
    public static void recordSale(Player player, ShopEntry entry, int quantity) {
        MarketData data = data(player);
        data.addSold(entry.id(), quantity);
        save(player, data);
    }

    /**
     * Recovers demand for elapsed in-game days. Cheap enough to call often;
     * it no-ops until a full day has passed.
     */
    public static void applyRecovery(Player player) {
        long now = player.level().getGameTime();
        MarketData data = data(player);

        if (data.lastDecayed() == 0L) {
            save(player, data.withLastDecayed(now));
            return;
        }

        long days = (now - data.lastDecayed()) / TICKS_PER_DAY;
        if (days <= 0) {
            return;
        }

        int recovered = (int) Math.min(Integer.MAX_VALUE, days * RECOVERY_PER_DAY);
        for (Map.Entry<String, Integer> saturationEntry : data.sold().entrySet()) {
            saturationEntry.setValue(Math.max(0, saturationEntry.getValue() - recovered));
        }
        data.sold().entrySet().removeIf(entry -> entry.getValue() <= 0);

        // Advance by whole days only, so the remainder isn't lost and demand
        // doesn't creep back faster than RECOVERY_PER_DAY.
        save(player, data.withLastDecayed(data.lastDecayed() + days * TICKS_PER_DAY));
    }
}
