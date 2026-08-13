package com.sappersquad.coinkeep;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * Single-use tool that empties someone else's vault without destroying it.
 *
 * This is the ONLY profitable way to take vaulted money: smashing a vault
 * destroys its contents by default, so raiding has to be deliberate and
 * paid for rather than opportunistic. Being consumed on use is what makes a
 * raid an economic decision - each one costs a cracker.
 *
 * The vault survives, so the owner comes back to an emptied safe rather than
 * a hole, which reads as a burglary instead of griefing.
 */
public class VaultCrackerItem extends Item {

    public VaultCrackerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof VaultBlockEntity vault)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        // Your own vault: fall through so the normal right-click opens it,
        // rather than wasting a cracker on a door you already have a key to.
        if (vault.canOpen(player)) {
            return InteractionResult.PASS;
        }
        if (!CoinkeepConfig.ALLOW_VAULT_CRACKING.get()) {
            player.displayClientMessage(Component.literal(
                    "Vaults cannot be cracked on this server."
            ).withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        long stored = vault.getStored();
        if (stored <= 0) {
            // Don't burn the cracker on an empty safe.
            player.displayClientMessage(Component.literal(
                    "This vault is empty."
            ).withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.FAIL;
        }

        // Remember who to notify BEFORE the claim is cleared - checking the
        // vault after setOwner(null) meant the victim was never told.
        java.util.UUID robbedOwner = vault.getOwner();
        vault.setStored(0L);
        // Cracking breaks the claim as well as the lock: the vault is no
        // longer anyone's, so the robber can now break it and keep the block
        // too. A cracked safe shouldn't stay welded to its old owner.
        vault.setOwner(null);
        for (ItemStack notes : CashHelper.asBanknotes(stored)) {
            Block.popResource(level, context.getClickedPos(), notes);
        }
        context.getItemInHand().shrink(1);

        level.playSound(null, context.getClickedPos(),
                SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0F, 0.5F);
        player.displayClientMessage(Component.literal(
                "Cracked the vault - $" + CurrencyItem.formatValue(stored) + " spills out!"
        ).withStyle(ChatFormatting.GOLD), false);

        // Tell the victim, if they're online. A silent theft they only notice
        // days later is far more frustrating than being told they were raided.
        if (robbedOwner != null && level.getServer() != null) {
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(robbedOwner);
            if (owner != null) {
                owner.displayClientMessage(Component.literal(
                        "Your vault was cracked - $" + CurrencyItem.formatValue(stored) + " was taken!"
                ).withStyle(ChatFormatting.RED), false);
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Empties another player's vault").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Consumed on use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
