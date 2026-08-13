package com.sappersquad.coinkeep;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A vault is never destroyed, only moved or robbed.
 *
 * Design rules:
 *  - Only the owner can break it. A thief cannot delete someone's savings.
 *  - Breaking it yields a vault ITEM still holding the money and the owner,
 *    so it can be relocated - and so it can be looted off your corpse and
 *    cracked later by whoever kills you.
 *  - Cracking it takes the money AND clears the claim, which is what lets
 *    the robber then break the empty vault and keep the block.
 */
public class VaultBlock extends BaseEntityBlock {

    public static final MapCodec<VaultBlock> CODEC = simpleCodec(VaultBlock::new);

    /** So the door faces the player who placed it, not always north. */
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

    public VaultBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // BaseEntityBlock defaults to INVISIBLE - without this the vault
        // would be a fully functional block you cannot see.
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VaultBlockEntity(pos, state);
    }

    /** Placing restores whatever the item was carrying: money and owner. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof VaultBlockEntity vault)) {
            return;
        }
        VaultContents carried = stack.getOrDefault(ModDataComponents.VAULT_CONTENTS.get(), VaultContents.EMPTY);
        vault.setStored(carried.stored());

        // A looted vault keeps its original owner, so the thief who plants it
        // still has to crack it. Only a blank vault claims a new owner.
        if (carried.owner().isPresent()) {
            vault.setOwner(carried.owner().get());
        } else if (placer instanceof Player player) {
            vault.setOwner(player.getUUID());
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof VaultBlockEntity vault) {

            if (!vault.canOpen(player)) {
                serverPlayer.sendOverlayMessage(Component.literal(
                        "This vault belongs to someone else."
                ).withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
            if (vault.getOwner() == null) {
                vault.setOwner(player.getUUID());
            }
            serverPlayer.openMenu(vault, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Only the owner can break a vault. A thief's options are to crack it or
     * leave it - they can never simply delete someone's savings, and a
     * cracked (now unowned) vault is free for anyone to take.
     */
    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof VaultBlockEntity vault && !vault.canOpen(player)) {
            return 0.0F;
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    /**
     * Drops the vault as a loaded item rather than as a plain block, then
     * blanks the block entity so the default drop cannot duplicate it.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof VaultBlockEntity vault
                && vault.canOpen(player)) {

            long stored = vault.getStored();
            if (stored > 0 || vault.getOwner() != null) {
                ItemStack drop = new ItemStack(ModBlocks.VAULT_ITEM.get());
                drop.set(ModDataComponents.VAULT_CONTENTS.get(),
                        new VaultContents(stored, java.util.Optional.ofNullable(vault.getOwner())));
                vault.setStored(0L);
                vault.setOwner(null);
                Block.popResource(level, pos, drop);

                if (stored > 0 && player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.sendOverlayMessage(Component.literal(
                            "Vault picked up with $" + CurrencyItem.formatValue(stored) + " still inside."
                    ).withStyle(ChatFormatting.GOLD));
                }
                // Stop the loot table adding a second, empty vault.
                level.removeBlockEntity(pos);
                level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
