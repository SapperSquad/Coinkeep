package com.sappersquad.coinkeep;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Keeps {@link ShopOverrides} loaded and handed out.
 *
 * <p>Two moments matter: the server coming up, when the edit folder is the
 * only record of what has been changed, and a player joining, who needs the
 * current edits before they can open the shop.
 */
@EventBusSubscriber(modid = Coinkeep.MODID)
public class ShopOverrideEvents {

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ShopOverrides.set(ShopEditor.loadAll(event.getServer()));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ShopOverrides.pushTo(player);
        }
    }
}
