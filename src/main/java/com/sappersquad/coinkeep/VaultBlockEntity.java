package com.sappersquad.coinkeep;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Stores money outside a player's balance.
 *
 * The point is risk management, not storage: a balance can be dropped on
 * death when a server enables that, but vaulted money never is. The cost is
 * that it isn't portable - you have to come back here to touch it.
 *
 * Whether breaking the vault yields its contents is configurable. Dropping
 * by default is deliberate: an untouchable safe would remove any reason to
 * raid a base, so a vault is protection against DEATH, not against players.
 */
public class VaultBlockEntity extends BlockEntity implements MenuProvider {

    private long stored;

    /**
     * Who may open this vault. Null means unclaimed (placed by command or in
     * creative) - the first player to open it claims it, so a vault is never
     * left permanently open to everyone.
     */
    @Nullable
    private UUID owner;

    /**
     * The balance, carried across FOUR slots of 16 bits each.
     *
     * CRITICAL: vanilla's ClientboundContainerSetDataPacket serializes every
     * menu data slot with writeShort/readShort - 16 bits, not 32 (verified in
     * the 1.21.1 decompile). Anything outside -32,768..32,767 is silently
     * mangled on the way to a REMOTE client. A two-slot 32-bit split therefore
     * looked correct everywhere it could be tested - single-player, a LAN host,
     * and GameTests all bypass the codec entirely - and then corrupted the
     * first real multiplayer guest's view: a $210,000 vault read as $13,392.
     *
     * So each slot carries exactly 16 bits and is masked back on read.
     */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return (int) ((stored >>> (index * 16)) & 0xFFFFL);
        }

        @Override
        public void set(int index, int value) {
            int shift = index * 16;
            stored = (stored & ~(0xFFFFL << shift)) | ((value & 0xFFFFL) << shift);
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    public VaultBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VAULT.get(), pos, state);
    }

    public long getStored() {
        return stored;
    }

    public void setStored(long value) {
        this.stored = Math.max(0L, value);
        setChanged();
    }

    public ContainerData getData() {
        return data;
    }

    @Nullable
    public UUID getOwner() {
        return owner;
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        setChanged();
    }

    /**
     * Owner-only to open, but anyone can still break the block - a vault is
     * protection from theft and from death, not from a determined raider.
     * Ops bypass so admins can recover a vault whose owner has left.
     */
    public boolean canOpen(Player player) {
        if (!CoinkeepConfig.VAULT_OWNER_ONLY.get() || owner == null) {
            return true;
        }
        return owner.equals(player.getUUID()) || player.hasPermissions(2);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("Stored", stored);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        stored = tag.getLong("Stored");
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.coinkeep.vault");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new VaultMenu(containerId, inventory, this);
    }
}
