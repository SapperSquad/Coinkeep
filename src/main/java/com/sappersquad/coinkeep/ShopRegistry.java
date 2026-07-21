package com.sappersquad.coinkeep;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-side view over the {@code coinkeep:shop_entry} datapack registry.
 * Entries are grouped by category and sorted cheapest-first here, once, so
 * the Shop tab is always ordered consistently.
 *
 * Same Registry-identity cache as QuestRegistry - see the note there.
 */
public class ShopRegistry {

    private static Registry<ShopEntry> cachedRegistry;
    private static Map<String, ShopEntry> byId = Map.of();
    private static Map<ShopCategory, List<ShopEntry>> byCategory = Map.of();
    private static List<ShopCategory> categories = List.of();

    private static void ensure(RegistryAccess access) {
        Registry<ShopEntry> registry = access.registryOrThrow(ModRegistries.SHOP_ENTRY);
        if (registry == cachedRegistry) {
            return;
        }

        Map<String, ShopEntry> ids = new LinkedHashMap<>();
        Map<ShopCategory, List<ShopEntry>> grouped = new EnumMap<>(ShopCategory.class);
        registry.stream().forEach(entry -> {
            ids.put(entry.id(), entry);
            grouped.computeIfAbsent(entry.category(), key -> new ArrayList<>()).add(entry);
        });
        // Cheapest first; ties broken by id so ordering is deterministic
        // regardless of registry iteration order.
        grouped.values().forEach(list -> list.sort(
                Comparator.comparingLong(ShopEntry::price).thenComparing(ShopEntry::id)));

        // Categories in enum declaration order, but only those with content.
        List<ShopCategory> present = new ArrayList<>();
        for (ShopCategory category : ShopCategory.values()) {
            if (grouped.containsKey(category)) {
                present.add(category);
            }
        }

        cachedRegistry = registry;
        byId = ids;
        byCategory = grouped;
        categories = present;
    }

    public static Collection<ShopEntry> all(RegistryAccess access) {
        ensure(access);
        return byId.values();
    }

    public static ShopEntry byId(RegistryAccess access, String id) {
        ensure(access);
        return byId.get(id);
    }

    /** Category contents, cheapest first. */
    public static List<ShopEntry> inCategory(RegistryAccess access, ShopCategory category) {
        ensure(access);
        return byCategory.getOrDefault(category, List.of());
    }

    /** Categories that actually contain something, in declaration order. */
    public static List<ShopCategory> categories(RegistryAccess access) {
        ensure(access);
        return categories;
    }
}
