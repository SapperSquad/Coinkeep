package com.sappersquad.coinkeep;

import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Same approach as ShopClientEvents: quest progress is a synced attachment,
 * so the book needs no server round trip - right-clicking it just opens the
 * screen on the client.
 */
@EventBusSubscriber(modid = Coinkeep.MODID, value = Dist.CLIENT)
public class QuestClientEvents {

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        // This event fires on BOTH logical sides, and in single-player the
        // integrated server shares this JVM. Without this guard we'd call
        // setScreen from the server thread - which Minecraft logs as
        // "setScreen called from non-game thread" and then does anyway,
        // opening the screen twice off the render thread.
        if (!event.getLevel().isClientSide()) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (stack.is(ModItems.BOOK.get())) {
            QuestClientOpener.open();
        }
    }
}
