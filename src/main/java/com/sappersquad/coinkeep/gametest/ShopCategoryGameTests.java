package com.sappersquad.coinkeep.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.sappersquad.coinkeep.ModRegistries;
import com.sappersquad.coinkeep.ShopCategory;
import com.sappersquad.coinkeep.ShopEntry;
import com.sappersquad.coinkeep.ShopRegistry;
import com.sappersquad.coinkeep.QuestRegistry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DataPackRegistriesHooks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Coinkeep's first GameTests, and they exist for one reason: <b>1.0.0 is
 * published</b>. Turning the hardcoded {@code ShopCategory} enum into a
 * datapack registry is a data migration on live worlds, so the migration
 * itself needs proof rather than confidence.
 *
 * <p>What "unchanged" has to mean, and what each test pins:
 * <ul>
 *   <li>The eight built-in ids still exist, still read the same, and still
 *       appear in the same sidebar order the enum's declaration order gave
 *       them.</li>
 *   <li>Every shipped shop entry still lands in the category it always did,
 *       with the same counts.</li>
 *   <li>A 1.0.0-shaped {@code shop_entry} JSON still parses, including the
 *       upper-case spelling the old {@code valueOf} accepted.</li>
 *   <li>The registry is SYNCED. The shop sidebar is client-rendered, so a
 *       server-only registry would leave every player an empty shop - the
 *       trap this mod already learned once and wrote down in ModRegistries.</li>
 * </ul>
 *
 * <p>Registered in {@link ModTestFunctions}; each method is named by a
 * {@code test_instance} JSON.
 */
public class ShopCategoryGameTests {

    /** id -> label, in the exact order the 1.0.0 enum declared them. */
    private static final Map<String, String> BUILT_INS = builtIns();

    private static Map<String, String> builtIns() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("food", "Food");
        map.put("weapons", "Weapons");
        map.put("armor", "Armor");
        map.put("enchantments", "Enchantments");
        map.put("ores", "Ores");
        map.put("materials", "Materials");
        map.put("rare", "Rare");
        map.put("signature", "Signature");
        return map;
    }

    /** The 1.0.0 catalog's shape, measured before the conversion. */
    private static final Map<String, Integer> SHIPPED_COUNTS = shippedCounts();

    private static Map<String, Integer> shippedCounts() {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("food", 6);
        map.put("weapons", 7);
        map.put("armor", 12);
        map.put("enchantments", 8);
        map.put("ores", 7);
        map.put("materials", 8);
        map.put("rare", 12);
        map.put("signature", 6);
        return map;
    }

    /**
     * The eight built-ins load from Coinkeep's own JSON and sort into the
     * enum's old declaration order. A player upgrading a 1.0.0 world sees
     * the same sidebar, in the same order, reading the same words.
     */
    public static void theEightBuiltInsSurviveTheConversion(GameTestHelper helper) {
        RegistryAccess access = helper.getLevel().registryAccess();
        List<ShopCategory> categories = ShopRegistry.categories(access);

        List<String> got = new ArrayList<>();
        categories.forEach(category -> got.add(category.id()));
        List<String> want = new ArrayList<>(BUILT_INS.keySet());
        helper.assertTrue(got.equals(want),
                "sidebar order must still be " + want + " but is " + got);

        for (ShopCategory category : categories) {
            String label = BUILT_INS.get(category.id());
            helper.assertTrue(label.equals(category.getLabel()),
                    category.id() + " must read '" + label + "' but reads '" + category.getLabel() + "'");
        }
        helper.succeed();
    }

    /**
     * Every shipped entry lands where it always did. Counts, not just ids -
     * an entry silently dropping into a placeholder tab would still have a
     * category, so only the numbers catch it.
     */
    public static void everyShippedEntryLandsInItsOldCategory(GameTestHelper helper) {
        RegistryAccess access = helper.getLevel().registryAccess();
        int total = 0;
        for (Map.Entry<String, Integer> expected : SHIPPED_COUNTS.entrySet()) {
            int size = ShopRegistry.inCategory(access, expected.getKey()).size();
            helper.assertTrue(size == expected.getValue(),
                    expected.getKey() + " must hold " + expected.getValue() + " entries, holds " + size);
            total += size;
        }
        helper.assertTrue(ShopRegistry.all(access).size() == total,
                "every entry must be reachable through a category tab; "
                        + ShopRegistry.all(access).size() + " entries but " + total + " in tabs");
        helper.assertTrue(ShopRegistry.danglingCategories(access).isEmpty(),
                "shipped content must not reference an undefined category: "
                        + ShopRegistry.danglingCategories(access));
        helper.succeed();
    }

    /**
     * THE hard-won lesson, now an assertion instead of a comment: the Shop
     * sidebar is drawn client-side, so {@code shop_category} must be
     * declared with a network codec or every client gets an empty shop.
     */
    public static void everyContentRegistryIsSyncedToClients(GameTestHelper helper) {
        var synced = DataPackRegistriesHooks.getSyncedCustomRegistries();
        for (ResourceKey<?> key : List.of(ModRegistries.QUEST_LINE, ModRegistries.QUEST,
                ModRegistries.SHOP_CATEGORY, ModRegistries.SHOP_ENTRY)) {
            helper.assertTrue(synced.contains(key),
                    key.identifier() + " must be registered with a NETWORK codec - a server-only "
                            + "registry leaves the client's book empty");
        }
        helper.succeed();
    }

    /**
     * A 1.0.0 datapack's {@code shop_entry} JSON must still parse byte for
     * byte, including the upper-case category spelling the old
     * {@code ShopCategory.valueOf(name.toUpperCase())} tolerated.
     */
    public static void oneZeroZeroEntryJsonStillParses(GameTestHelper helper) {
        ShopEntry lower = parse(helper, """
                { "id": "test_lower", "category": "rare", "item": "minecraft:elytra", "price": 100 }""");
        helper.assertTrue("rare".equals(lower.category()),
                "category must stay 'rare', got '" + lower.category() + "'");

        ShopEntry upper = parse(helper, """
                { "id": "test_upper", "category": "RARE", "item": "minecraft:elytra", "price": 100 }""");
        helper.assertTrue("rare".equals(upper.category()),
                "an upper-case category must normalise to 'rare', got '" + upper.category() + "'");

        // The case 1.0.0 could not survive: an unknown category threw inside
        // the codec and took the entry with it. It parses now.
        ShopEntry unknown = parse(helper, """
                { "id": "test_unknown", "category": "space_junk", "item": "minecraft:elytra", "price": 100 }""");
        helper.assertTrue("space_junk".equals(unknown.category()),
                "an unknown category must not fail the entry");

        ShopCategory placeholder = ShopCategory.placeholder("space_junk");
        helper.assertTrue("Space Junk".equals(placeholder.getLabel()),
                "placeholder label must be readable, got '" + placeholder.getLabel() + "'");
        helper.assertTrue(placeholder.sortOrder() == Integer.MAX_VALUE,
                "a placeholder tab must sort to the end");
        helper.succeed();
    }

    /**
     * The addon contract, exercised the way an addon exercises it: a
     * category JSON with a name, a sort order and an icon round-trips, and
     * sort_order defaults sanely when omitted.
     */
    public static void anAddonCategoryJsonRoundTrips(GameTestHelper helper) {
        ShopCategory full = parseCategory(helper, """
                { "id": "highroller", "name": "Highroller", "sort_order": 200,
                  "icon": "minecraft:gold_ingot" }""");
        helper.assertTrue("highroller".equals(full.id()), "id");
        helper.assertTrue("Highroller".equals(full.name()), "name");
        helper.assertTrue(full.sortOrder() == 200, "sort_order");
        helper.assertTrue(full.icon().isPresent() && full.icon().get() == Items.GOLD_INGOT, "icon");

        ShopCategory minimal = parseCategory(helper, """
                { "id": "minimal", "name": "Minimal" }""");
        helper.assertTrue(minimal.sortOrder() == ShopCategory.DEFAULT_SORT_ORDER,
                "a category without sort_order must default to " + ShopCategory.DEFAULT_SORT_ORDER);
        helper.assertTrue(minimal.icon().isEmpty(),
                "no icon means the sidebar falls back to the cheapest entry, as in 1.0.0");
        helper.succeed();
    }

    /** The whole content set still validates - the /reload check, run headless. */
    public static void contentStillValidates(GameTestHelper helper) {
        List<String> problems = QuestRegistry.validate(helper.getLevel().registryAccess());
        helper.assertTrue(problems.isEmpty(), "content validation must be clean: " + problems);
        helper.succeed();
    }

    private static ShopEntry parse(GameTestHelper helper, String json) {
        var result = ShopEntry.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
        helper.assertTrue(result.result().isPresent(),
                "shop_entry must parse: " + result.error().map(Object::toString).orElse(""));
        return result.result().orElseThrow();
    }

    private static ShopCategory parseCategory(GameTestHelper helper, String json) {
        var result = ShopCategory.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
        helper.assertTrue(result.result().isPresent(),
                "shop_category must parse: " + result.error().map(Object::toString).orElse(""));
        return result.result().orElseThrow();
    }
}
