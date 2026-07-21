package com.sappersquad.coinkeep;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = Coinkeep.MODID, value = Dist.CLIENT)
public class KeyBindings {

    private static final String CATEGORY = "key.categories.coinkeep";

    public static final KeyMapping OPEN_TASKS = new KeyMapping(
            "key.coinkeep.open_tasks", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);

    public static final KeyMapping OPEN_SHOP = new KeyMapping(
            "key.coinkeep.open_shop", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_TASKS);
        event.register(OPEN_SHOP);
    }
}
