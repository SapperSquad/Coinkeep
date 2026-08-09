package com.sappersquad.coinkeep;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

import java.util.Locale;
import java.util.Optional;

/**
 * A tab in the Shop's sidebar.
 *
 * <p><b>1.1.0: this used to be a hardcoded Java enum</b> (FOOD, WEAPONS,
 * ARMOR, ENCHANTMENTS, ORES, MATERIALS, RARE, SIGNATURE), which meant an
 * addon could not add one - every entry a companion mod shipped had to be
 * squeezed into a Coinkeep category, and a big addon would swamp Coinkeep's
 * own browsing. It is a datapack registry now, exactly like
 * {@code quest_line} / {@code quest} / {@code shop_entry}, so any namespace
 * can define its own:
 *
 * <pre>data/&lt;namespace&gt;/coinkeep/shop_category/&lt;id&gt;.json</pre>
 *
 * <p><b>Nothing about the old data shape changed.</b> Entries still say
 * {@code "category": "rare"} - a bare lowercase id, not a resource location -
 * because {@link ShopEntry} references a category by its {@code id} field the
 * same way {@link Quest} references its {@link QuestLine}. The eight
 * built-ins ship as Coinkeep's own JSON with those exact ids, so every world
 * and every third-party datapack written against 1.0.0 resolves unchanged.
 *
 * @param id         the value {@code shop_entry.category} matches on.
 * @param name       what the sidebar tab reads. Free text, so "Highroller"
 *                   or "Cuisine" is as valid as "Rare".
 * @param sortOrder  sidebar order, low first, ties broken by id. JSON files
 *                   load in an arbitrary order, so without this the tabs
 *                   would shuffle between launches - the same reason
 *                   {@link QuestLine} has one.
 * @param icon       optional. When absent the sidebar keeps its 1.0.0
 *                   behaviour and draws the category's cheapest entry, which
 *                   is free and always representative.
 */
public record ShopCategory(String id, String name, int sortOrder, Optional<Item> icon) {

    /** Where an addon's category lands if it never sets sort_order. */
    public static final int DEFAULT_SORT_ORDER = 100;

    /**
     * Loaded from data/&lt;namespace&gt;/coinkeep/shop_category/&lt;id&gt;.json.
     */
    public static final Codec<ShopCategory> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(ShopCategory::id),
            Codec.STRING.fieldOf("name").forGetter(ShopCategory::name),
            Codec.INT.optionalFieldOf("sort_order", DEFAULT_SORT_ORDER).forGetter(ShopCategory::sortOrder),
            BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("icon").forGetter(ShopCategory::icon)
    ).apply(instance, ShopCategory::new));

    /**
     * The stand-in for a category a shop entry names but nobody defined.
     *
     * <p>1.0.0 handled that case by THROWING inside the entry's codec, which
     * dropped the whole entry - a typo made an item silently unbuyable. The
     * entry now survives under a synthesized tab at the end of the sidebar,
     * and {@link QuestRegistry#validate} reports the typo by name. Losing a
     * purchasable item is worse than an ugly tab.
     */
    public static ShopCategory placeholder(String id) {
        return new ShopCategory(id, titleCase(id.replace('_', ' ')), Integer.MAX_VALUE, Optional.empty());
    }

    private static String titleCase(String text) {
        StringBuilder out = new StringBuilder(text.length());
        boolean startOfWord = true;
        for (char c : text.toCharArray()) {
            out.append(startOfWord ? Character.toUpperCase(c) : c);
            startOfWord = c == ' ';
        }
        return out.toString();
    }

    /** Kept from the enum days so call sites read the same. */
    public String getLabel() {
        return name;
    }

    /** Normalised form used everywhere a category is matched. */
    public static String normaliseId(String raw) {
        return raw.toLowerCase(Locale.ROOT);
    }
}
