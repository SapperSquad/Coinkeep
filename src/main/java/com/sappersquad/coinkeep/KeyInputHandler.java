package com.sappersquad.coinkeep;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = Coinkeep.MODID, value = Dist.CLIENT)
public class KeyInputHandler {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (KeyBindings.OPEN_TASKS.consumeClick()) {
            // Quest progress rides on a synced attachment now, so the book
            // opens straight away with no /opentasks round trip.
            QuestClientOpener.open();
        }

        while (KeyBindings.OPEN_SHOP.consumeClick()) {
            // The shop catalog is static/identical on both sides already,
            // so opening it doesn't need a server round trip at all.
            ShopClientOpener.open();
        }
    }
}
