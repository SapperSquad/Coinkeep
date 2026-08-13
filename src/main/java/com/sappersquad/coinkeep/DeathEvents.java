package com.sappersquad.coinkeep;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Drops a dying player's banked balance as physical banknotes, when the
 * server has opted into it (see CoinkeepConfig).
 *
 * Dropping real bills rather than simply deleting the money is deliberate:
 * it means a kill TRANSFERS wealth instead of destroying it, so the economy
 * keeps its total and a raid has a spoil worth taking.
 */
@EventBusSubscriber(modid = Coinkeep.MODID)
public class DeathEvents {

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!CoinkeepConfig.DROP_BALANCE_ON_DEATH.get()) {
            return;
        }
        if (CoinkeepConfig.RESPECT_KEEP_INVENTORY.get()
                && player.level().getGameRules().get(GameRules.KEEP_INVENTORY)) {
            return;
        }

        long balance = BalanceHelper.getBalance(player);
        if (balance <= 0) {
            return;
        }

        long dropped = balance * CoinkeepConfig.BALANCE_DROP_PERCENT.get() / 100L;
        if (dropped <= 0) {
            return;
        }
        BalanceHelper.setBalance(player, balance - dropped);

        for (ItemStack stack : CashHelper.asBanknotes(dropped)) {
            ItemEntity entity = new ItemEntity(player.level(),
                    player.getX(), player.getY() + 0.5, player.getZ(), stack);
            entity.setDefaultPickUpDelay();
            player.level().addFreshEntity(entity);
        }

        player.sendSystemMessage(Component.literal(
                "You dropped $" + CurrencyItem.formatValue(dropped) + " in cash!"
        ).withStyle(ChatFormatting.RED));
    }

}
