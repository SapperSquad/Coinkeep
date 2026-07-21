package com.sappersquad.coinkeep;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

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
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide) {
            BalanceHelper.addBalance(player, value);
            player.displayClientMessage(
                    Component.literal("Deposited $" + formatValue(value)
                                    + " - balance: $" + formatValue(BalanceHelper.getBalance(player)))
                            .withStyle(ChatFormatting.GREEN),
                    true);
            stack.shrink(1);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Worth $" + formatValue(value)).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal("Right-click to deposit").withStyle(ChatFormatting.GRAY));
    }

    public static String formatValue(long value) {
        return String.format("%,d", value);
    }
}
