package com.sappersquad.coinkeep;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/**
 * Quest and shop content lives in datapack registries rather than in Java,
 * so a modpack can add, retune or override anything by dropping JSON in a
 * datapack - no jar edit, and `/reload` applies it live.
 *
 * Content paths:
 *   data/&lt;namespace&gt;/coinkeep/quest_line/&lt;id&gt;.json
 *   data/&lt;namespace&gt;/coinkeep/quest/&lt;id&gt;.json
 *   data/&lt;namespace&gt;/coinkeep/shop_category/&lt;id&gt;.json   (1.1.0)
 *   data/&lt;namespace&gt;/coinkeep/shop_entry/&lt;id&gt;.json
 *
 * IMPORTANT: each registry is declared with a **network codec** (the third
 * argument), which is what makes NeoForge sync it to clients on join. The
 * quest book and shop screens render client-side, so a server-only registry
 * would leave them empty - that is the trap with the simpler
 * AddReloadListenerEvent approach, and why this uses datapack registries.
 * SHOP_CATEGORY is declared the same way for exactly that reason: the Shop
 * tab's sidebar is drawn on the client, so a server-only category registry
 * would give every player an empty shop.
 */
@EventBusSubscriber(modid = Coinkeep.MODID)
public class ModRegistries {

    public static final ResourceKey<Registry<QuestLine>> QUEST_LINE = ResourceKey.createRegistryKey(
            Identifier.fromNamespaceAndPath(Coinkeep.MODID, "quest_line"));

    public static final ResourceKey<Registry<Quest>> QUEST = ResourceKey.createRegistryKey(
            Identifier.fromNamespaceAndPath(Coinkeep.MODID, "quest"));

    public static final ResourceKey<Registry<ShopCategory>> SHOP_CATEGORY = ResourceKey.createRegistryKey(
            Identifier.fromNamespaceAndPath(Coinkeep.MODID, "shop_category"));

    public static final ResourceKey<Registry<ShopEntry>> SHOP_ENTRY = ResourceKey.createRegistryKey(
            Identifier.fromNamespaceAndPath(Coinkeep.MODID, "shop_entry"));

    @SubscribeEvent
    public static void registerDataPackRegistries(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(QUEST_LINE, QuestLine.CODEC, QuestLine.CODEC);
        event.dataPackRegistry(QUEST, Quest.CODEC, Quest.CODEC);
        event.dataPackRegistry(SHOP_CATEGORY, ShopCategory.CODEC, ShopCategory.CODEC);
        event.dataPackRegistry(SHOP_ENTRY, ShopEntry.CODEC, ShopEntry.CODEC);
    }
}
