package com.sappersquad.coinkeep;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

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
        int buyLimit
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

    /**
     * Nothing enchanted is sellable. A sell matches on the ITEM only, so an
     * enchanted entry would let any plain copy of that item be sold at the
     * enchanted price - e.g. a bare Netherite Pickaxe cashed in at "The
     * Prospector" money, or any enchanted book sold at the Mending price.
     */
    public boolean sellable() {
        return enchantmentId == null && enchantments.isEmpty();
    }

    /** True for pre-enchanted gear, which goes on ENCHANTMENTS not STORED_. */
    public boolean isGear() {
        return !enchantments.isEmpty();
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
            Codec.INT.optionalFieldOf("buy_limit", 0).forGetter(ShopEntry::buyLimit)
    ).apply(instance, (id, category, item, count, price, enchantment, level, sell, saturation, name, enchants, buyLimit) ->
            new ShopEntry(id, category, item, count, price, enchantment.orElse(null), level,
                    sell, saturation, name.orElse(null), enchants, buyLimit)));

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
