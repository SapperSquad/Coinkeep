package com.sappersquad.coinkeep;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The vault's deposit/withdraw window.
 *
 * Uses vanilla's own button channel (clickMenuButton) rather than a custom
 * packet, keeping the mod's no-custom-networking rule intact - it is the
 * same mechanism the stonecutter and loom use.
 *
 * Button ids are derived from AMOUNTS rather than hand-written constants:
 * ids 0..n-1 deposit, n..2n-1 withdraw. One list drives the menu logic and
 * the screen's layout and labels, so they cannot drift apart.
 */
public class VaultMenu extends AbstractContainerMenu {

    /** Fixed amounts in display order. 0 means "everything". */
    private static final long[] AMOUNTS = {1_000L, 10_000L, 50_000L, 100_000L, 500_000L, 0L};

    /** Buttons per row - one row to deposit, one to withdraw. */
    public static final int PER_ROW = AMOUNTS.length;

    private final ContainerData data;
    @Nullable
    private final VaultBlockEntity vault;

    /** Client side - the block entity is re-fetched from the synced pos. */
    public VaultMenu(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        this(containerId, inventory, resolve(inventory, buf.readBlockPos()));
    }

    public VaultMenu(int containerId, Inventory inventory, @Nullable VaultBlockEntity vault) {
        super(ModMenus.VAULT_MENU.get(), containerId);
        this.vault = vault;
        this.data = vault == null ? new SimpleContainerData(4) : vault.getData();
        addDataSlots(this.data);
    }

    @Nullable
    private static VaultBlockEntity resolve(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos) instanceof VaultBlockEntity found ? found : null;
    }

    /** Reassembled from the high/low int pair - see VaultBlockEntity. */
    public long getStored() {
        // Four 16-bit slots, masked because the wire round-trips them through a
        // signed short - see the note on VaultBlockEntity.data.
        long value = 0L;
        for (int i = 0; i < 4; i++) {
            value |= (data.get(i) & 0xFFFFL) << (i * 16);
        }
        return value;
    }

    /** "$1k", "$500k", "All" - shared with the screen so labels never disagree. */
    public static String labelFor(int slot) {
        long value = AMOUNTS[slot];
        if (value == 0L) {
            return "All";
        }
        return value >= 1_000_000L ? "$" + (value / 1_000_000L) + "m" : "$" + (value / 1_000L) + "k";
    }

    public static int buttonId(int slot, boolean depositing) {
        return depositing ? slot : slot + PER_ROW;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        // Re-checked here, not just on open: a menu left open across a config
        // change or ownership change must not keep working.
        if (vault == null || player.level().isClientSide || !vault.canOpen(player)) {
            return false;
        }
        if (id < 0 || id >= PER_ROW * 2) {
            return false;
        }

        boolean depositing = id < PER_ROW;
        long fixed = AMOUNTS[depositing ? id : id - PER_ROW];

        long balance = BalanceHelper.getBalance(player);
        long stored = vault.getStored();
        long amount = fixed > 0L ? fixed : (depositing ? balance : stored);
        if (amount <= 0L) {
            return false;
        }

        if (depositing) {
            // Move only what's actually there, so a fixed-amount button still
            // does something useful when you're short.
            long moved = Math.min(amount, balance);
            if (moved <= 0L || !BalanceHelper.removeBalance(player, moved)) {
                return false;
            }
            vault.setStored(stored + moved);
        } else {
            long moved = Math.min(amount, stored);
            if (moved <= 0L) {
                return false;
            }
            vault.setStored(stored - moved);
            BalanceHelper.addBalance(player, moved);
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return vault == null || player.distanceToSqr(
                vault.getBlockPos().getX() + 0.5,
                vault.getBlockPos().getY() + 0.5,
                vault.getBlockPos().getZ() + 0.5) <= 64.0;
    }
}
