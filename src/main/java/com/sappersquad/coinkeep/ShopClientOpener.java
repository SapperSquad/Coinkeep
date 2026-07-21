package com.sappersquad.coinkeep;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Opens the book on its Shop tab. Kept as its own class so the K keybind
 * has a direct entry point.
 */
public class ShopClientOpener {
    public static void open() {
        Minecraft.getInstance().setScreen(
                QuestScreen.onShopTab(Component.literal("Coinkeep")));
    }
}
