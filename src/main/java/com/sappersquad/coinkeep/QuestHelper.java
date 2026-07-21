package com.sappersquad.coinkeep;

import net.minecraft.world.entity.player.Player;

/**
 * Static accessors over the synced quest_progress attachment. Safe on both
 * sides: the client reads the synced copy, the server mutates it.
 */
public class QuestHelper {

    public enum State {
        /** A prerequisite is still incomplete. */
        LOCKED,
        /** Unlocked and being worked on. */
        AVAILABLE,
        COMPLETED
    }

    public static QuestProgressData data(Player player) {
        return player.getData(ModAttachments.QUEST_PROGRESS);
    }

    /** Attachments are mutated in place, so re-set to mark the holder dirty and resync. */
    private static void save(Player player, QuestProgressData data) {
        player.setData(ModAttachments.QUEST_PROGRESS, data);
    }

    public static int getProgress(Player player, String questId) {
        return data(player).getProgress(questId);
    }

    public static boolean isCompleted(Player player, String questId) {
        return data(player).isCompleted(questId);
    }

    public static void incrementProgress(Player player, String questId, int amount) {
        QuestProgressData data = data(player);
        data.incrementProgress(questId, amount);
        save(player, data);
    }

    public static void setProgress(Player player, String questId, int value) {
        QuestProgressData data = data(player);
        data.setProgress(questId, value);
        save(player, data);
    }

    public static void markCompleted(Player player, String questId) {
        QuestProgressData data = data(player);
        data.markCompleted(questId);
        save(player, data);
    }

    /** Tiers already cleared. 0 means working toward tier 1. */
    public static int getTier(Player player, String questId) {
        return data(player).getTier(questId);
    }

    public static void setTier(Player player, String questId, int tier) {
        QuestProgressData data = data(player);
        data.setTier(questId, tier);
        save(player, data);
    }

    /** The tier being worked toward now (or the last one, once maxed). */
    public static int activeTier(Player player, Quest quest) {
        int cleared = getTier(player, quest.id());
        return quest.isMaxed(cleared) ? Math.max(1, quest.maxTier()) : cleared + 1;
    }

    /** A quest unlocks only once every one of its dependencies is complete. */
    public static boolean isUnlocked(Player player, Quest quest) {
        QuestProgressData data = data(player);
        for (String dependency : quest.dependencies()) {
            if (!data.isCompleted(dependency)) {
                return false;
            }
        }
        return true;
    }

    public static State stateOf(Player player, Quest quest) {
        if (isCompleted(player, quest.id())) {
            return State.COMPLETED;
        }
        return isUnlocked(player, quest) ? State.AVAILABLE : State.LOCKED;
    }

    /** Completed quests in a line, for the chapter progress readout. */
    public static int completedIn(Player player, String lineId) {
        int count = 0;
        for (Quest quest : QuestRegistry.questsIn(player.level().registryAccess(), lineId)) {
            if (isCompleted(player, quest.id())) {
                count++;
            }
        }
        return count;
    }
}
