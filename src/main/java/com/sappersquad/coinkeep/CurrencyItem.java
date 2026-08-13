package com.sappersquad.coinkeep;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

public class CurrencyItem extends Item {

    private final long value;

    public CurrencyItem(long value, Properties properties) {
        super(properties);
        this.value = value;
    }

    public long getValue() {
        return value;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide()) {
            BalanceHelper.addBalance(player, value);
            player.displayClientMessage(
                    Component.literal("Deposited $" + formatValue(value)
                                    + " - balance: $" + formatValue(BalanceHelper.getBalance(player)))
                            .withStyle(ChatFormatting.GREEN),
                    true);
            stack.shrink(1);
        }

        // The sided variants are gone in 1.21.11: SUCCESS swings client-side
        // and the server ignores the swing source, which is what sidedSuccess
        // used to arrange by hand.
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.literal("Worth $" + formatValue(value)).withStyle(ChatFormatting.GOLD));
        tooltip.accept(Component.literal("Right-click to deposit").withStyle(ChatFormatting.GRAY));
    }

    public static String formatValue(long value) {
        return String.format("%,d", value);
    }
}
