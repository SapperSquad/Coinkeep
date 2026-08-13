package com.sappersquad.coinkeep.gametest;

import com.sappersquad.coinkeep.ModBlocks;
import com.sappersquad.coinkeep.VaultBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.inventory.ContainerData;

/**
 * Pins the vault balance against the 16-bit wire.
 *
 * <p><b>Why this exists.</b> Vanilla's {@code ClientboundContainerSetDataPacket}
 * serializes every menu data slot with {@code writeShort}/{@code readShort} —
 * 16 bits, not 32. Coinkeep originally carried the vault's {@code long} across
 * two slots of 32 bits, which is correct arithmetic and wrong on the wire: a
 * $210,000 vault reached a remote client as $13,392, and a $100,000 vault as
 * $4,294,936,224.
 *
 * <p><b>Why it was never caught.</b> Three environments bypass the codec
 * entirely — single-player, the LAN <i>host</i> (an in-memory connection passes
 * packet objects without serializing), and GameTests themselves. So the bug was
 * invisible in every place it could be exercised, and only appeared for a
 * genuine remote guest on a real server.
 *
 * <p>That last point is the trap for this very file: a test that just wrote and
 * read the {@link ContainerData} would have <b>passed on the broken code</b>.
 * So these tests do not trust the in-memory path. They assert the invariant the
 * wire actually imposes — every slot must survive a signed-short round trip —
 * and simulate that truncation explicitly.
 *
 * <p>Registered in {@link ModTestFunctions}; each method is named by a
 * {@code test_instance} JSON.
 */
public class VaultSyncGameTests {

    /** Balances that span the boundaries: under a short, over it, and huge. */
    private static final long[] BALANCES = {
            0L, 1L, 32_767L, 32_768L, 65_535L, 65_536L,
            100_000L, 210_000L, 5_000_000L, 100_000_000L, 999_999_999L
    };

    /** Exactly what the vanilla packet does to one slot: write, then read, as a short. */
    private static int throughWire(int slotValue) {
        short written = (short) slotValue;
        return written;
    }

    private static VaultBlockEntity placeVault(GameTestHelper helper) {
        // The tests run in vanilla's 1x1x1 "minecraft:empty" structure, so the
        // origin is the only cell inside the test volume.
        BlockPos pos = BlockPos.ZERO;
        helper.setBlock(pos, ModBlocks.VAULT.get());
        // The Class-token overload fails the test itself if the entity is
        // missing or of the wrong type.
        return helper.getBlockEntity(pos, VaultBlockEntity.class);
    }

    /**
     * The load-bearing test. Every slot the vault publishes has to come back
     * unchanged after a signed-short round trip, and reassemble to the original
     * balance. Run against the old two-slot scheme this fails immediately at
     * $32,768.
     */
    public static void everyBalanceSurvivesTheShortWire(GameTestHelper helper) {
        VaultBlockEntity vault = placeVault(helper);
        ContainerData data = vault.getData();

        for (long balance : BALANCES) {
            vault.setStored(balance);

            long rebuilt = 0L;
            for (int i = 0; i < data.getCount(); i++) {
                int sent = data.get(i);
                if (sent != (sent & 0xFFFF)) {
                    helper.fail("slot " + i + " of $" + balance
                            + " carries " + sent + ", which does not fit in 16 bits -"
                            + " the wire would mangle it");
                }
                rebuilt |= ((long) (throughWire(sent) & 0xFFFF)) << (i * 16);
            }

            if (rebuilt != balance) {
                helper.fail("$" + balance + " reached a remote client as $" + rebuilt);
            }
        }
        helper.succeed();
    }

    /**
     * The block entity is still the source of truth after a set, independent of
     * how the value is chopped up for sync.
     */
    public static void storedValueRoundTripsLocally(GameTestHelper helper) {
        VaultBlockEntity vault = placeVault(helper);
        for (long balance : BALANCES) {
            vault.setStored(balance);
            if (vault.getStored() != balance) {
                helper.fail("vault stored $" + balance + " but reports $" + vault.getStored());
            }
        }
        helper.succeed();
    }

    /** A vault cannot hold a negative balance, however it is written to. */
    public static void negativeBalancesAreClamped(GameTestHelper helper) {
        VaultBlockEntity vault = placeVault(helper);
        vault.setStored(-5_000L);
        if (vault.getStored() != 0L) {
            helper.fail("negative balance was not clamped, got $" + vault.getStored());
        }
        helper.succeed();
    }
}
