package com.sappersquad.coinkeep.gametest;

import com.sappersquad.coinkeep.Coinkeep;
import com.sappersquad.coinkeep.Quest;
import com.sappersquad.coinkeep.QuestRegistry;
import com.sappersquad.coinkeep.TriggerType;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Pins the trigger index against the linear scan it replaced.
 *
 * <p>Quest progress used to be found by walking every quest in the book on
 * every block break, mob kill, craft and advancement. Correct, and O(n) per
 * event — fine for Coinkeep's ~180 quests, bad for a modpack's thousand.
 * {@link QuestRegistry#triggeredBy} makes it a hash lookup instead.
 *
 * <p>The risk in that swap is silent: if the index misses a quest, that quest
 * simply never progresses again, with no error anywhere. So these tests
 * re-derive the old scan's answer and assert the index agrees with it exactly.
 */
@GameTestHolder(Coinkeep.MODID)
@PrefixGameTestTemplate(false)
public class QuestIndexGameTests {

    /** Exactly the predicate the old hot loop used. */
    private static List<Quest> byScan(RegistryAccess access, TriggerType type, String target) {
        List<Quest> found = new ArrayList<>();
        for (Quest quest : QuestRegistry.all(access)) {
            if (quest.triggerType() == type && quest.triggerTarget().equals(target)) {
                found.add(quest);
            }
        }
        return found;
    }

    /**
     * For every quest that ships, looking up its own trigger has to find it.
     * A miss here is a quest that can never be completed again.
     */
    @GameTest(template = "empty")
    public static void everyQuestIsReachableByItsOwnTrigger(GameTestHelper helper) {
        RegistryAccess access = helper.getLevel().registryAccess();
        int checked = 0;

        for (Quest quest : QuestRegistry.all(access)) {
            List<Quest> indexed = QuestRegistry.triggeredBy(access, quest.triggerType(), quest.triggerTarget());
            if (!indexed.contains(quest)) {
                throw new GameTestAssertException("quest '" + quest.id()
                        + "' is not reachable through its own trigger ("
                        + quest.triggerType() + " / " + quest.triggerTarget()
                        + ") - it would never progress");
            }
            checked++;
        }
        if (checked == 0) {
            throw new GameTestAssertException("no quests loaded, so this proved nothing");
        }
        helper.succeed();
    }

    /**
     * The index and the old scan must agree on the WHOLE set, not just on
     * membership - a duplicated entry would pay a quest out twice per event.
     */
    @GameTest(template = "empty")
    public static void indexMatchesTheScanExactly(GameTestHelper helper) {
        RegistryAccess access = helper.getLevel().registryAccess();

        for (Quest quest : QuestRegistry.all(access)) {
            TriggerType type = quest.triggerType();
            String target = quest.triggerTarget();

            List<Quest> scanned = byScan(access, type, target);
            List<Quest> indexed = QuestRegistry.triggeredBy(access, type, target);

            if (scanned.size() != indexed.size() || !indexed.containsAll(scanned)) {
                throw new GameTestAssertException("index disagrees with the scan for "
                        + type + " / " + target + ": scan found " + scanned.size()
                        + ", index found " + indexed.size());
            }
        }
        helper.succeed();
    }

    /** An unknown target returns empty rather than throwing or matching wrongly. */
    @GameTest(template = "empty")
    public static void unknownTriggersReturnEmpty(GameTestHelper helper) {
        RegistryAccess access = helper.getLevel().registryAccess();

        for (TriggerType type : TriggerType.values()) {
            List<Quest> found = QuestRegistry.triggeredBy(access, type, "coinkeep:nothing_has_this_target");
            if (!found.isEmpty()) {
                throw new GameTestAssertException(
                        "unknown target matched " + found.size() + " quests for " + type);
            }
        }
        helper.succeed();
    }

    /**
     * Trigger type is part of the key, not just the target. Two quests that
     * share a target across different triggers - mining a block and crafting
     * the same block - must not bleed into each other.
     */
    @GameTest(template = "empty")
    public static void triggerTypeIsPartOfTheKey(GameTestHelper helper) {
        RegistryAccess access = helper.getLevel().registryAccess();

        for (Quest quest : QuestRegistry.all(access)) {
            for (TriggerType other : TriggerType.values()) {
                if (other == quest.triggerType()) {
                    continue;
                }
                if (QuestRegistry.triggeredBy(access, other, quest.triggerTarget()).contains(quest)) {
                    throw new GameTestAssertException("quest '" + quest.id() + "' ("
                            + quest.triggerType() + ") also answers to " + other
                            + " - the wrong event would advance it");
                }
            }
        }
        helper.succeed();
    }
}
