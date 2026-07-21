package com.sappersquad.coinkeep;

import net.minecraft.world.entity.player.Player;

/**
 * Replaces the old IPlayerBalance / PlayerBalance / PlayerBalanceProvider
 * capability trio entirely - with data attachments there's no provider
 * class needed, just plain static helpers over player.getData()/setData().
 */
public class BalanceHelper {

    public static long getBalance(Player player) {
        return player.getData(ModAttachments.BALANCE);
    }

    public static void setBalance(Player player, long amount) {
        player.setData(ModAttachments.BALANCE, Math.max(0L, amount));
    }

    public static void addBalance(Player player, long amount) {
        if (amount > 0) {
            setBalance(player, getBalance(player) + amount);
        }
    }

    /** @return true if the player had enough and it was removed, false otherwise. */
    public static boolean removeBalance(Player player, long amount) {
        long current = getBalance(player);
        if (amount < 0 || amount > current) {
            return false;
        }
        setBalance(player, current - amount);
        return true;
    }
}
