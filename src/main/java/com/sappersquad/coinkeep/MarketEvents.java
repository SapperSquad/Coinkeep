package com.sappersquad.coinkeep;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Ticks demand recovery. Deliberately cheap: applyRecovery() no-ops until a
 * full in-game day has elapsed, and this only checks a few times a minute,
 * so the cost is a clock comparison per player.
 *
 * It runs on a timer rather than only on sale so the sell screen's prices
 * visibly climb back while you're stood looking at it, instead of jumping
 * the moment you next sell something.
 */
@EventBusSubscriber(modid = Coinkeep.MODID)
public class MarketEvents {

    private static final int CHECK_INTERVAL_TICKS = 200;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % CHECK_INTERVAL_TICKS == 0) {
            MarketHelper.applyRecovery(player);
        }
    }

    /** Catches up demand that recovered while the player was logged out. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MarketHelper.applyRecovery(player);
        giveStarterBook(player);
    }

    /**
     * Hands out the book on first join.
     *
     * The book is the only signpost that any of this mod exists - without it
     * a new player has no way to learn there are quests, a shop, or the J/K
     * keybinds, and would have to craft one to find that out. Tracked by a
     * persistent attachment so relogging never duplicates it.
     */
    private static void giveStarterBook(ServerPlayer player) {
        if (!CoinkeepConfig.GIVE_BOOK_ON_FIRST_JOIN.get()) {
            return;
        }
        if (player.getData(ModAttachments.GOT_STARTER_BOOK)) {
            return;
        }
        player.setData(ModAttachments.GOT_STARTER_BOOK, true);

        ItemStack book = new ItemStack(ModItems.BOOK.get());
        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
        player.sendSystemMessage(Component.literal(
                "Coinkeep: press J to open your book - quests, shop and cash are all inside."
        ).withStyle(ChatFormatting.GOLD));
    }
}
