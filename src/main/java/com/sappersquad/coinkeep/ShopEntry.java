package com.sappersquad.coinkeep;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A purchasable catalog entry.
 *
 * Three shapes of entry share this record:
 *  - plain items (no enchantments)
 *  - enchanted books, via the legacy single {@code enchantment} field, which
 *    land on STORED_ENCHANTMENTS
 *  - "signature gear": a named, pre-enchanted tool or armour piece, via the
 *    {@code enchantments} list, which lands on ENCHANTMENTS
 *
 * @param customName overrides the item's own name, so gear can be sold as
 *                   "The Prospector" rather than "Netherite Pickaxe".
 * @param category   the {@code id} of a {@link ShopCategory} - a bare
 *                   lowercase string like {@code "rare"}, unchanged from
 *                   1.0.0. It is a plain String rather than a resolved
 *                   object because categories are a datapack registry now
 *                   and JSON files load in arbitrary order: an entry must
 *                   be allowed to name a category that has not been read
 *                   yet. Resolution happens once, in {@link ShopRegistry}.
 * @param enabled    false hides the entry completely. This exists because a
 *                   datapack cannot DELETE a registry entry, only override
 *                   one - so "remove the diamond from the shop" is expressed
 *                   as an override of the shipped entry with
 *                   {@code "enabled": false}. {@link ShopRegistry} drops
 *                   disabled entries before anything else sees them, so they
 *                   vanish from the tabs and from /buy alike.
 */
public record ShopEntry(
        String id,
        String category,
        Item item,
        int count,
        long price,
        String enchantmentId,
        int enchantmentLevel,
        long sellPrice,
        int saturation,
        String customName,
        List<EnchantmentSpec> enchantments,
        int buyLimit,
        boolean enabled
) {
    /** True when this entry can only be bought a fixed number of times. */
    public boolean hasBuyLimit() {
        return buyLimit > 0;
    }

    /**
     * Builds the exact stack a purchase hands over - custom name, and
     * enchantments on the right component (books carry theirs as cargo under
     * STORED_ENCHANTMENTS; gear carries active ENCHANTMENTS).
     *
     * Shared by the server's /buy and the client's shop tooltip, so what the
     * tooltip shows is by construction what the purchase delivers. Signature
     * gear's enchantment list used to be invisible before buying - a $210,000
     * item you could not inspect.
     */
    public net.minecraft.world.item.ItemStack createStack(net.minecraft.core.RegistryAccess access) {
        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item, count);

        if (customName != null && !customName.isBlank()) {
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    net.minecraft.network.chat.Component.literal(customName)
                            .withStyle(net.minecraft.ChatFormatting.GOLD));
        }

        var lookup = access.lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        var component = isGear()
                ? net.minecraft.core.component.DataComponents.ENCHANTMENTS
                : net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS;
        var mutable = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(
                stack.getOrDefault(component, net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY));
        boolean any = false;

        // Legacy single-enchantment form (the enchanted books).
        if (enchantmentId != null) {
            any |= applyEnchant(lookup, mutable, enchantmentId, enchantmentLevel);
        }
        for (EnchantmentSpec spec : enchantments) {
            any |= applyEnchant(lookup, mutable, spec.id(), spec.level());
        }

        if (any) {
            stack.set(component, mutable.toImmutable());
        }
        return stack;
    }

    /** @return true if the enchantment resolved and was applied. */
    private static boolean applyEnchant(
            net.minecraft.core.HolderLookup.RegistryLookup<net.minecraft.world.item.enchantment.Enchantment> lookup,
            net.minecraft.world.item.enchantment.ItemEnchantments.Mutable target, String id, int level) {
        net.minecraft.resources.ResourceLocation location = net.minecraft.resources.ResourceLocation.tryParse(id);
        if (location == null || level <= 0) {
            return false;
        }
        var holder = lookup.get(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.ENCHANTMENT, location));
        holder.ifPresent(h -> target.set(h, level));
        return holder.isPresent();
    }
    /** Sell price defaults to this fraction of the buy price. The gap is a
     *  deliberate spread - without it, buying and re-selling the same item
     *  would be an infinite money loop. */
    public static final double DEFAULT_SELL_RATIO = 0.40;

    /** Sales of one item before its price halves. Lower = drops faster. */
    public static final int DEFAULT_SATURATION = 64;

    /**
     * Per-unit sell price before saturation.
     *
     * CRITICAL: {@code price} buys a stack of {@code count}, but selling is
     * priced per single item - so the buy price MUST be divided by count
     * first. Deriving straight from price made bulk entries an infinite
     * money loop (buy 16 redstone for $250, sell them back for $1,600).
     */
    public long baseSellPrice() {
        if (sellPrice > 0) {
            return sellPrice;
        }
        double perUnitBuy = (double) price / Math.max(1, count);
        return Math.max(1L, Math.round(perUnitBuy * DEFAULT_SELL_RATIO));
    }

    public int effectiveSaturation() {
        return saturation > 0 ? saturation : DEFAULT_SATURATION;
    }

    /** What one single item costs to buy, which is what a sale is priced against. */
    public double perUnitBuyPrice() {
        return (double) price / Math.max(1, count);
    }

    /**
     * True when this entry could be bought and immediately re-sold at a
     * profit - the one edit that breaks the economy outright.
     *
     * The shipped catalog cannot hit this because sell prices are DERIVED at
     * 40% of the per-unit buy price. Hand-editing both numbers can, and a
     * single looping entry is worth infinite money to anyone who finds it, so
     * the editing commands refuse rather than warn. Saturation only ever
     * pushes the sell price DOWN, so comparing the un-saturated base price is
     * the strict (safe) test.
     */
    public boolean createsMoneyLoop() {
        return baseSellPrice() >= perUnitBuyPrice();
    }

    // Small copies used by the /coinkeep shop commands. A record is the right
    // shape for this: an edit produces a new entry, which is then serialised
    // whole, so there is no way to half-apply a change.
    public ShopEntry withPrice(long newPrice) {
        return new ShopEntry(id, category, item, count, newPrice, enchantmentId, enchantmentLevel,
                sellPrice, saturation, customName, enchantments, buyLimit, enabled);
    }

    public ShopEntry withSellPrice(long newSellPrice) {
        return new ShopEntry(id, category, item, count, price, enchantmentId, enchantmentLevel,
                newSellPrice, saturation, customName, enchantments, buyLimit, enabled);
    }

    public ShopEntry withCount(int newCount) {
        return new ShopEntry(id, category, item, newCount, price, enchantmentId, enchantmentLevel,
                sellPrice, saturation, customName, enchantments, buyLimit, enabled);
    }

    public ShopEntry withBuyLimit(int newBuyLimit) {
        return new ShopEntry(id, category, item, count, price, enchantmentId, enchantmentLevel,
                sellPrice, saturation, customName, enchantments, newBuyLimit, enabled);
    }

    public ShopEntry withCategory(String newCategory) {
        return new ShopEntry(id, ShopCategory.normaliseId(newCategory), item, count, price,
                enchantmentId, enchantmentLevel, sellPrice, saturation, customName,
                enchantments, buyLimit, enabled);
    }

    public ShopEntry withEnabled(boolean nowEnabled) {
        return new ShopEntry(id, category, item, count, price, enchantmentId, enchantmentLevel,
                sellPrice, saturation, customName, enchantments, buyLimit, nowEnabled);
    }

    /**
     * Nothing enchanted is sellable. A sell matches on the ITEM only, so an
     * enchanted entry would let any plain copy of that item be sold at the
     * enchanted price - e.g. a bare Netherite Pickaxe cashed in at "The
     * Prospector" money, or any enchanted book sold at the Mending price.
     */
    public boolean sellable() {
        return enchantmentId == null && enchantments.isEmpty();
    }

    /**
     * True for pre-enchanted gear, which goes on ENCHANTMENTS not STORED_.
     *
     * An enchanted book is never gear however many enchantments it lists: its
     * enchantments are cargo to be applied at an anvil, so they belong on
     * STORED_ENCHANTMENTS. The shipped books all use the legacy single
     * {@code enchantment} field (and so never reached this test), but
     * {@code /coinkeep shop add} captures a held book's enchantments into the
     * list form, which would otherwise hand over a book that is itself
     * magical and transfers nothing.
     */
    public boolean isGear() {
        return !enchantments.isEmpty() && item != net.minecraft.world.item.Items.ENCHANTED_BOOK;
    }

    /**
     * Builds an entry from a held stack: item, stack size, custom name and
     * enchantments all captured as they are. The inverse of
     * {@link #createStack}, so buying back what you just listed returns the
     * same thing you were holding.
     */
    public static ShopEntry fromStack(String id, String category,
                                      net.minecraft.world.item.ItemStack stack, long price) {
        String name = null;
        var custom = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        if (custom != null) {
            name = custom.getString();
        }

        // Gear carries live ENCHANTMENTS; a book carries the same data as
        // cargo under STORED_ENCHANTMENTS. Read whichever is populated - the
        // entry stores them the same way either way, and isGear() decides
        // which component they are written back to.
        var active = stack.getOrDefault(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        var stored = stack.getOrDefault(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS,
                net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        var source = active.isEmpty() ? stored : active;

        List<EnchantmentSpec> specs = new java.util.ArrayList<>();
        for (var holder : source.keySet()) {
            holder.unwrapKey().ifPresent(key ->
                    specs.add(new EnchantmentSpec(key.location().toString(), source.getLevel(holder))));
        }
        // Deterministic file output: registry iteration order is not stable.
        specs.sort(Comparator.comparing(EnchantmentSpec::id));

        return new ShopEntry(id, ShopCategory.normaliseId(category), stack.getItem(),
                Math.max(1, stack.getCount()), price, null, 0, 0L, 0, name,
                List.copyOf(specs), 0, true);
    }

    /**
     * Loaded from data/&lt;namespace&gt;/coinkeep/shop_entry/&lt;id&gt;.json.
     */
    public static final Codec<ShopEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(ShopEntry::id),
            // 1.0.0 parsed this straight into an enum constant, which THREW
            // on anything unknown and took the whole entry down with it.
            // Lower-casing and keeping the string means a 1.0.0 datapack
            // reads identically, an addon's own category id is legal, and a
            // typo costs a validator warning instead of a vanished item.
            Codec.STRING.xmap(ShopCategory::normaliseId, category -> category)
                    .fieldOf("category").forGetter(ShopEntry::category),
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(ShopEntry::item),
            Codec.INT.optionalFieldOf("count", 1).forGetter(ShopEntry::count),
            Codec.LONG.fieldOf("price").forGetter(ShopEntry::price),
            // Legacy single-enchantment form, used by the enchanted books.
            Codec.STRING.optionalFieldOf("enchantment")
                    .forGetter(entry -> Optional.ofNullable(entry.enchantmentId())),
            Codec.INT.optionalFieldOf("enchantment_level", 0).forGetter(ShopEntry::enchantmentLevel),
            // 0 = derive from price. Set explicitly to break the default ratio
            // for an item you want to be unusually good or bad to sell.
            Codec.LONG.optionalFieldOf("sell_price", 0L).forGetter(ShopEntry::sellPrice),
            // 0 = use the default. Lower saturates (and so devalues) faster.
            Codec.INT.optionalFieldOf("saturation", 0).forGetter(ShopEntry::saturation),
            Codec.STRING.optionalFieldOf("name")
                    .forGetter(entry -> Optional.ofNullable(entry.customName())),
            EnchantmentSpec.CODEC.listOf().optionalFieldOf("enchantments", List.of())
                    .forGetter(ShopEntry::enchantments),
            // 0 = unlimited (the default). Otherwise, how many times each
            // player may ever buy this entry - for one-off unlocks and for
            // rationing anything that would unbalance a server in bulk.
            Codec.INT.optionalFieldOf("buy_limit", 0).forGetter(ShopEntry::buyLimit),
            // Absent = true, so every pre-1.4.0 JSON file reads unchanged.
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(ShopEntry::enabled)
    ).apply(instance, (id, category, item, count, price, enchantment, level, sell, saturation, name, enchants, buyLimit, enabled) ->
            new ShopEntry(id, category, item, count, price, enchantment.orElse(null), level,
                    sell, saturation, name.orElse(null), enchants, buyLimit, enabled)));

    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    /**
     * "silk touch" -> "Silk Touch". Enchantment names are derived from their
     * registry id, so every multi-word one (blast protection, fire aspect,
     * feather falling...) needs each word capitalised, not just the first.
     */
    private static String titleCase(String text) {
        StringBuilder out = new StringBuilder(text.length());
        boolean startOfWord = true;
        for (char c : text.toCharArray()) {
            out.append(startOfWord ? Character.toUpperCase(c) : c);
            startOfWord = c == ' ';
        }
        return out.toString();
    }

    public String displayName() {
        if (customName != null && !customName.isBlank()) {
            return customName;
        }
        if (enchantmentId != null) {
            String raw = enchantmentId.contains(":") ? enchantmentId.split(":")[1] : enchantmentId;
            String nice = titleCase(raw.replace("_", " "));
            // Level 1 shows no numeral, matching vanilla - "Mending" and
            // "Silk Touch", never "Mending I".
            if (enchantmentLevel <= 1) {
                return nice + " Book";
            }
            String level = enchantmentLevel < ROMAN.length ? ROMAN[enchantmentLevel] : String.valueOf(enchantmentLevel);
            return nice + " " + level + " Book";
        }
        return item.getDescription().getString();
    }
}
