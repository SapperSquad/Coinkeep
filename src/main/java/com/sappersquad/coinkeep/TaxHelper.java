package com.sappersquad.coinkeep;

/**
 * The two tax levers, in one place so the shop screen quotes exactly what the
 * server will charge.
 *
 * <p>Both default to 0, so a server that ignores this section sees the prices
 * it always saw.
 *
 * <p>Tax is <b>destroyed, not collected</b>. There is no treasury to pay it to,
 * and the point is to remove money from circulation - a tax that pooled
 * somewhere and was later spent would not fight inflation at all.
 */
public class TaxHelper {

    /** What a purchase actually costs, surcharge included. */
    public static long buyCost(ShopEntry entry) {
        long price = entry.price();
        int percent = CoinkeepConfig.BUY_TAX_PERCENT.get();
        if (percent <= 0) {
            return price;
        }
        // Rounded up: a tax that rounds to nothing on cheap items is a loophole
        // on exactly the items bought in bulk.
        return price + Math.max(1L, (price * percent + 99) / 100);
    }

    /** The surcharge on its own, for display. */
    public static long buyTax(ShopEntry entry) {
        return buyCost(entry) - entry.price();
    }

    /** What a sale actually pays after the cut. */
    public static long sellPayout(long grossPayout) {
        int percent = CoinkeepConfig.SELL_TAX_PERCENT.get();
        if (percent <= 0 || grossPayout <= 0) {
            return grossPayout;
        }
        long cut = (grossPayout * percent) / 100;
        // Never round a sale down to nothing - selling always pays something,
        // or the market stops being usable at high tax rates.
        return Math.max(1L, grossPayout - cut);
    }

    /** The cut on its own, for display. */
    public static long sellTax(long grossPayout) {
        return grossPayout - sellPayout(grossPayout);
    }

    public static boolean buyTaxActive() {
        return CoinkeepConfig.BUY_TAX_PERCENT.get() > 0;
    }

    public static boolean sellTaxActive() {
        return CoinkeepConfig.SELL_TAX_PERCENT.get() > 0;
    }
}
