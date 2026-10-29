package com.sappersquad.coinkeep.gametest;

import com.sappersquad.coinkeep.Quest;
import com.sappersquad.coinkeep.QuestEvents;
import com.sappersquad.coinkeep.QuestRegistry;
import com.sappersquad.coinkeep.TriggerType;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * Pins block-break quests against the ore variants 1.17 introduced.
 *
 * <p><b>Why this exists.</b> Every ore quest shipped targeting one block id —
 * {@code minecraft:diamond_ore} and friends. Since the 1.17 world height
 * change, ore below y=0 generates as a <i>different block</i>:
 * {@code minecraft:deepslate_diamond_ore}. Those breaks matched nothing, so
 * the quests they were written for could not progress at the depth players
 * actually mine. Diamond and redstone are the worst of it — both generate
 * almost entirely inside the deepslate layer, so those two quests were close
 * to uncompletable by normal play.
 *
 * <p><b>Why nothing caught it.</b> A quest with an unreachable target looks
 * exactly like a quest nobody has got round to yet: the book renders it, the
 * index returns it, the content validator passed it. Nothing in the mod ever
 * asked whether a target was a block that exists, let alone whether it was the
 * only block that should count. So these tests assert both, over the whole
 * registry rather than a hand-listed set — a ninth ore variant added by Mojang
 * or a modpack fails here instead of in someone's save.
 *
 * <p>Registered in {@link ModTestFunctions}; each method is named by a
 * {@code test_instance} JSON.
 */
public final class OreVariantGameTests {

    /**
     * The quests a broken block would advance, resolved exactly the way
     * {@code QuestEvents.onBlockBreak} resolves them — through the mod's own
     * id conversion, not a copy of it, so a change to either side shows up
     * here.
     *
     * <p>What this deliberately does not do is award progress to a player.
     * Quest progress lives in a <i>synced</i> data attachment, and the mock
     * player a headless GameTest can make has no connection, so the first
     * write throws "Payload neoforge:sync_attachments may not be sent to the
     * client". The paying half of that path is already covered by
     * {@link QuestIndexGameTests}; what was broken here was the lookup, and
     * the lookup is what these assert.
     */
    private static List<Quest> questsFor(RegistryAccess access, Block block) {
        return QuestRegistry.triggeredBy(access, TriggerType.BLOCK_BREAK,
                QuestEvents.blockId(block.defaultBlockState()));
    }

    /**
     * The gap that let this ship: nothing ever checked that a block_break
     * target names a block that exists. A typo is silent — the quest just
     * sits at 0/N forever with no error anywhere.
     */
    public static void everyBlockBreakTargetIsARealBlock(GameTestHelper helper) {
        RegistryAccess access = helper.getLevel().registryAccess();
        List<String> bad = new ArrayList<>();

        for (Quest quest : QuestRegistry.all(access)) {
            if (quest.triggerType() != TriggerType.BLOCK_BREAK) {
                continue;
            }
            Identifier id = Identifier.tryParse(quest.triggerTarget());
            if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
                bad.add(quest.id() + " -> '" + quest.triggerTarget() + "'");
            }
        }

        if (!bad.isEmpty()) {
            helper.fail("block_break quests target blocks that do not exist, so they"
                    + " can never progress: " + bad);
        }
        helper.succeed();
    }

    /**
     * THE regression test. For every quest that mines an ore, the deepslate
     * form of that ore has to fire it too.
     *
     * <p>Derived from the block registry rather than a fixed list, so this
     * keeps holding if a variant is added later.
     */
    public static void everyOreQuestAlsoCountsItsDeepslateVariant(GameTestHelper helper) {
        RegistryAccess access = helper.getLevel().registryAccess();
        List<String> missing = new ArrayList<>();

        for (Quest quest : QuestRegistry.all(access)) {
            if (quest.triggerType() != TriggerType.BLOCK_BREAK) {
                continue;
            }
            Identifier target = Identifier.tryParse(quest.triggerTarget());
            if (target == null || !target.getPath().endsWith("_ore") || target.getPath().startsWith("deepslate_")) {
                continue;
            }

            Identifier variant = Identifier.fromNamespaceAndPath(
                    target.getNamespace(), "deepslate_" + target.getPath());
            if (!BuiltInRegistries.BLOCK.containsKey(variant)) {
                // No deepslate form exists (nether quartz, ancient debris).
                continue;
            }

            boolean covered = QuestRegistry
                    .triggeredBy(access, TriggerType.BLOCK_BREAK, variant.toString())
                    .contains(quest);
            if (!covered) {
                missing.add(quest.id() + " ignores " + variant);
            }
        }

        if (!missing.isEmpty()) {
            helper.fail("ore quests do not count their deepslate variant, so mining"
                    + " below y=0 earns nothing: " + missing);
        }
        helper.succeed();
    }

    /** The quest the player feedback named by hand. Plain deepslate must count. */
    public static void breakingDeepslateHitsItsQuest(GameTestHelper helper) {
        RegistryAccess access = helper.getLevel().registryAccess();

        List<String> ids = new ArrayList<>();
        questsFor(access, Blocks.DEEPSLATE).forEach(quest -> ids.add(quest.id()));
        if (!ids.contains("mine_deepslate")) {
            helper.fail(
                    "breaking deepslate must advance mine_deepslate, but it fires " + ids);
        }

        // Cobbled deepslate is what you get, not what you broke. If it ever
        // starts firing the quest, a player could farm it by replacing blocks.
        List<String> cobbled = new ArrayList<>();
        questsFor(access, Blocks.COBBLED_DEEPSLATE).forEach(quest -> cobbled.add(quest.id()));
        if (!cobbled.isEmpty()) {
            helper.fail(
                    "cobbled deepslate must not advance anything, it fires " + cobbled);
        }
        helper.succeed();
    }

    /**
     * The fix, checked through the handler's own id conversion: deepslate
     * diamond ore is what a player actually finds, since diamond generates
     * below y=16 and deepslate starts at y=0.
     */
    public static void breakingDeepslateDiamondOreHitsTheDiamondQuest(GameTestHelper helper) {
        RegistryAccess access = helper.getLevel().registryAccess();

        List<String> ids = new ArrayList<>();
        questsFor(access, Blocks.DEEPSLATE_DIAMOND_ORE).forEach(quest -> ids.add(quest.id()));
        if (!ids.contains("mine_diamond")) {
            helper.fail("breaking deepslate diamond ore must advance"
                    + " mine_diamond, but it fires " + ids);
        }
        helper.succeed();
    }

    /**
     * Both forms must feed ONE quest, not two. Split across a pair of quests
     * a player who mined four of each would be half-done twice over instead
     * of seven eighths of the way to one payout.
     */
    public static void bothOreFormsFeedTheSameQuest(GameTestHelper helper) {
        RegistryAccess access = helper.getLevel().registryAccess();

        List<Quest> stone = questsFor(access, Blocks.IRON_ORE);
        List<Quest> deep = questsFor(access, Blocks.DEEPSLATE_IRON_ORE);

        if (stone.isEmpty() || !stone.equals(deep)) {
            List<String> a = new ArrayList<>();
            List<String> b = new ArrayList<>();
            stone.forEach(quest -> a.add(quest.id()));
            deep.forEach(quest -> b.add(quest.id()));
            helper.fail("iron ore and deepslate iron ore must advance the same"
                    + " quest so one lifetime total covers both; stone fires " + a
                    + " and deepslate fires " + b);
        }
        helper.succeed();
    }

    private OreVariantGameTests() {
    }
}
