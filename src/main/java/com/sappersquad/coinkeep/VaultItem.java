package com.sappersquad.coinkeep;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * The vault as an item. Shows what it is still carrying, because a loaded
 * vault dropped on death is loot - and nobody would know to pick it up if it
 * looked identical to an empty one.
 *
 * The amount is shown, but not the owner: knowing a vault is worth cracking
 * is the interesting information, knowing whose it is would just be a
 * targeting list.
 */
public class VaultItem extends BlockItem {

    public VaultItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        VaultContents contents = stack.getOrDefault(ModDataComponents.VAULT_CONTENTS.get(), VaultContents.EMPTY);
        if (contents.stored() > 0) {
            tooltip.add(Component.literal("Holding $" + CurrencyItem.formatValue(contents.stored()))
                    .withStyle(ChatFormatting.GOLD));
        }
        if (contents.owner().isPresent()) {
            tooltip.add(Component.literal("Locked - crack it to open")
                    .withStyle(ChatFormatting.RED));
        }
    }

    /** So a loaded vault never stacks with an empty one and loses its money. */
    @Override
    public int getMaxStackSize(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.VAULT_CONTENTS.get(), VaultContents.EMPTY).isEmpty() ? 64 : 1;
    }
}
