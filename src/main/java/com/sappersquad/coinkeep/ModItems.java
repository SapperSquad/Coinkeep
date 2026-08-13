package com.sappersquad.coinkeep;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(BuiltInRegistries.ITEM, Coinkeep.MODID);

    /**
     * 1.21.11 requires every Item.Properties to carry its own registry id
     * before construction ("Item id not set" at registration otherwise) -
     * the id now bakes the description id and model pointer into the item.
     */
    static ResourceKey<Item> itemId(String name) {
        return ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Coinkeep.MODID, name));
    }

    // The one book - Quests, Shop and Cash are tabs of a single screen, so
    // there's no separate Shop Book any more. A plain Item: QuestClientEvents
    // opens the screen client-side, so there's no server menu to hook here.
    public static final DeferredHolder<Item, Item> BOOK = ITEMS.register("ledger",
            () -> new Item(new Item.Properties().stacksTo(1).setId(itemId("ledger"))));

    public static final DeferredHolder<Item, Item> VAULT_CRACKER = ITEMS.register("vault_cracker",
            () -> new VaultCrackerItem(new Item.Properties().stacksTo(4).setId(itemId("vault_cracker"))));

    private static final LinkedHashMap<String, Long> BILL_VALUES = new LinkedHashMap<>();
    static {
        BILL_VALUES.put("bill_1", 1L);
        BILL_VALUES.put("bill_5", 5L);
        BILL_VALUES.put("bill_10", 10L);
        BILL_VALUES.put("bill_20", 20L);
        BILL_VALUES.put("bill_50", 50L);
        BILL_VALUES.put("bill_100", 100L);
        BILL_VALUES.put("bill_500", 500L);
        BILL_VALUES.put("bill_1k", 1_000L);
        BILL_VALUES.put("bill_5k", 5_000L);
        BILL_VALUES.put("bill_10k", 10_000L);
        BILL_VALUES.put("bill_50k", 50_000L);
        BILL_VALUES.put("bill_100k", 100_000L);
        BILL_VALUES.put("bill_500k", 500_000L);
        BILL_VALUES.put("bill_1m", 1_000_000L);
        BILL_VALUES.put("bill_5m", 5_000_000L);
        BILL_VALUES.put("bill_10m", 10_000_000L);
        BILL_VALUES.put("bill_50m", 50_000_000L);
        BILL_VALUES.put("bill_100m", 100_000_000L);
    }

    public static final Map<String, DeferredHolder<Item, Item>> BILLS = new LinkedHashMap<>();
    static {
        for (Map.Entry<String, Long> entry : BILL_VALUES.entrySet()) {
            String id = entry.getKey();
            long value = entry.getValue();
            BILLS.put(id, ITEMS.register(id,
                    () -> new CurrencyItem(value, new Item.Properties().setId(itemId(id)))));
        }
    }

    /** Denomination ids in ascending value order, for the Cash screen. */
    public static java.util.List<String> billIds() {
        return java.util.List.copyOf(BILL_VALUES.keySet());
    }

    /** Face value of a denomination id, or 0 if it isn't one. */
    public static long billValue(String id) {
        return BILL_VALUES.getOrDefault(id, 0L);
    }

    /**
     * The largest bill worth no more than the given amount, used as the icon
     * for money rewards. BILL_VALUES is in ascending order, so the last match
     * wins. Falls back to the $1 bill.
     */
    public static Item billFor(long amount) {
        Item best = BILLS.get("bill_1").get();
        for (Map.Entry<String, Long> entry : BILL_VALUES.entrySet()) {
            if (entry.getValue() <= amount) {
                best = BILLS.get(entry.getKey()).get();
            }
        }
        return best;
    }
}
