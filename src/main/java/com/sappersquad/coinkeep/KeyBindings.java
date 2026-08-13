package com.sappersquad.coinkeep;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = Coinkeep.MODID, value = Dist.CLIENT)
public class KeyBindings {

    // 1.21.11 made key categories typed. The label translates via
    // Identifier.toLanguageKey, so this id reads lang key
    // "key.category.coinkeep.coinkeep".
    private static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(Coinkeep.MODID, Coinkeep.MODID));

    public static final KeyMapping OPEN_TASKS = new KeyMapping(
            "key.coinkeep.open_tasks", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);

    public static final KeyMapping OPEN_SHOP = new KeyMapping(
            "key.coinkeep.open_shop", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        // The category must be registered through the event, not
        // KeyMapping.Category.register - NeoForge owns the sort order.
        event.registerCategory(CATEGORY);
        event.register(OPEN_TASKS);
        event.register(OPEN_SHOP);
    }
}
