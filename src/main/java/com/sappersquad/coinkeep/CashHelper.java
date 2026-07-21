package com.sappersquad.coinkeep;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Shared conversion between a money amount and physical banknotes. */
public class CashHelper {

    /**
     * Greedy largest-denomination breakdown, merged into stacks of 64.
     *
     * Greedy matters: paying out $1,000,000 in $1 notes would spawn a million
     * item entities and take the server down. Working from the largest note
     * keeps even huge amounts to a handful of stacks.
     *
     * A $1 note exists and amounts are whole dollars, so the pass always
     * represents the full amount with nothing left over.
     */
    public static List<ItemStack> asBanknotes(long amount) {
        List<ItemStack> out = new ArrayList<>();
        List<String> denominations = ModItems.billIds();
        long remaining = amount;

        for (int i = denominations.size() - 1; i >= 0 && remaining > 0; i--) {
            String id = denominations.get(i);
            long value = ModItems.billValue(id);
            if (value <= 0 || remaining < value) {
                continue;
            }
            long count = remaining / value;
            remaining -= count * value;

            while (count > 0) {
                int stackSize = (int) Math.min(64L, count);
                out.add(new ItemStack(ModItems.BILLS.get(id).get(), stackSize));
                count -= stackSize;
            }
        }
        return out;
    }
}
