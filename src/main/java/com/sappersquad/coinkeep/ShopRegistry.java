package com.sappersquad.coinkeep;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Read-side view over the {@code coinkeep:shop_entry} and (since 1.1.0)
 * {@code coinkeep:shop_category} datapack registries. Entries are grouped by
 * category and sorted cheapest-first here, once, so the Shop tab is always
 * ordered consistently.
 *
 * <p>Same Registry-identity cache as QuestRegistry - see the note there.
 *
 * <p><b>Category resolution is the one piece of real logic.</b> An entry
 * names its category by a bare id string ({@code "rare"}), because JSON files
 * load in an arbitrary order and an entry must be allowed to mention a
 * category defined in another datapack that has not been read yet. This class
 * joins the two registries once per reload and, crucially, never DROPS an
 * entry whose category is missing: it synthesizes a placeholder tab at the
 * end of the sidebar so the item stays buyable, and the validator reports the
 * dangling id by name. 1.0.0 failed that case inside the codec, which made a
 * one-character typo silently delete a purchasable item.
 */
public class ShopRegistry {

    private static Registry<ShopEntry> cachedRegistry;
    private static Registry<ShopCategory> cachedCategoryRegistry;
    private static Map<String, ShopEntry> byId = Map.of();
    private static Map<String, List<ShopEntry>> byCategory = Map.of();
    private static Map<String, ShopCategory> categoriesById = Map.of();
    private static List<ShopCategory> categories = List.of();
    private static List<String> danglingCategories = List.of();

    private static void ensure(RegistryAccess access) {
        Registry<ShopEntry> registry = access.registryOrThrow(ModRegistries.SHOP_ENTRY);
        Registry<ShopCategory> categoryRegistry = access.registryOrThrow(ModRegistries.SHOP_CATEGORY);
        if (registry == cachedRegistry && categoryRegistry == cachedCategoryRegistry) {
            return;
        }

        // Defined categories first, keyed by their own id field (NOT their
        // registry key) so "category": "rare" keeps matching exactly as it
        // did when this was an enum.
        Map<String, ShopCategory> defined = new LinkedHashMap<>();
        categoryRegistry.stream().forEach(category ->
                defined.putIfAbsent(ShopCategory.normaliseId(category.id()), category));

        Map<String, ShopEntry> ids = new LinkedHashMap<>();
        Map<String, List<ShopEntry>> grouped = new TreeMap<>();
        List<String> dangling = new ArrayList<>();
        registry.stream().forEach(entry -> {
            ids.put(entry.id(), entry);
            String key = ShopCategory.normaliseId(entry.category());
            if (!defined.containsKey(key)) {
                defined.put(key, ShopCategory.placeholder(key));
                dangling.add(key);
            }
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entry);
        });
        // Cheapest first; ties broken by id so ordering is deterministic
        // regardless of registry iteration order.
        grouped.values().forEach(list -> list.sort(
                Comparator.comparingLong(ShopEntry::price).thenComparing(ShopEntry::id)));

        // sort_order, then id - the QuestLine rule. Only categories that
        // actually contain something get a tab, so an addon's category
        // disappears cleanly when the addon does.
        List<ShopCategory> present = new ArrayList<>();
        for (Map.Entry<String, ShopCategory> category : defined.entrySet()) {
            if (grouped.containsKey(category.getKey())) {
                present.add(category.getValue());
            }
        }
        present.sort(Comparator.comparingInt(ShopCategory::sortOrder).thenComparing(ShopCategory::id));

        cachedRegistry = registry;
        cachedCategoryRegistry = categoryRegistry;
        byId = ids;
        byCategory = grouped;
        categoriesById = defined;
        categories = List.copyOf(present);
        danglingCategories = List.copyOf(dangling);
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
    public static List<ShopEntry> inCategory(RegistryAccess access, String categoryId) {
        ensure(access);
        return byCategory.getOrDefault(ShopCategory.normaliseId(categoryId), List.of());
    }

    /** Categories that actually contain something, in sidebar order. */
    public static List<ShopCategory> categories(RegistryAccess access) {
        ensure(access);
        return categories;
    }

    /**
     * The category an entry belongs to, never null - a dangling id resolves
     * to its placeholder rather than dropping the entry.
     */
    public static ShopCategory categoryById(RegistryAccess access, String categoryId) {
        ensure(access);
        String key = ShopCategory.normaliseId(categoryId);
        return categoriesById.getOrDefault(key, ShopCategory.placeholder(key));
    }

    /** Category ids that entries reference but no JSON defines. */
    public static List<String> danglingCategories(RegistryAccess access) {
        ensure(access);
        return danglingCategories;
    }
}
