package com.sappersquad.coinkeep;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Drives quest progress. Only quests that are unlocked (all prerequisites
 * complete) and not yet finished can advance, so a locked quest can't be
 * completed early by accident.
 */
@EventBusSubscriber(modid = Coinkeep.MODID)
public class QuestEvents {

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player.level().isClientSide()) {
            return;
        }
        advance(player, TriggerType.BLOCK_BREAK, blockId(event.getState()), 1);
    }

    @SubscribeEvent
    public static void onMobKill(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof Player player)) {
            return;
        }
        String killedId = EntityType.getKey(event.getEntity().getType()).toString();
        advance(player, TriggerType.MOB_KILL, killedId, 1);
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        advance(player, TriggerType.ITEM_CRAFT, itemId(event.getCrafting()), event.getCrafting().getCount());
    }

    @SubscribeEvent
    public static void onAdvancementEarned(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        // Advancements are one-shot, so jump straight to the required count.
        advance(player, TriggerType.ADVANCEMENT, event.getAdvancement().id().toString(), Integer.MAX_VALUE);
    }

    private static void advance(Player player, TriggerType type, String targetId, int amount) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        // Machines mine and kill through FakePlayers, which ARE ServerPlayers
        // and would sail through this method - progress and rewards would land
        // on the machine's phantom profile, and one-shot ITEM rewards go to a
        // fake "inventory" some machine mods collect from. Quests pay for
        // playing; automation gets paid at the market by selling what it digs.
        if (serverPlayer instanceof net.neoforged.neoforge.common.util.FakePlayer) {
            return;
        }
        net.minecraft.core.RegistryAccess access = player.level().registryAccess();
        // Indexed lookup, not a scan of the whole book: this runs on every
        // block break, mob kill and craft, so its cost has to be independent
        // of how many quests a modpack has added.
        for (Quest quest : QuestRegistry.triggeredBy(access, type, targetId)) {
            int cleared = QuestHelper.getTier(player, quest.id());
            if (quest.isMaxed(cleared) || !QuestHelper.isUnlocked(player, quest)) {
                continue;
            }

            // Lifetime running total - never reset, so clearing a tier just
            // raises the bar for the next one.
            int nextTarget = quest.cumulativeTargetForTier(cleared + 1);
            int gain = amount == Integer.MAX_VALUE ? nextTarget : amount;
            QuestHelper.setProgress(player, quest.id(),
                    QuestHelper.getProgress(player, quest.id()) + gain);

            // One gain can clear several tiers at once (a big advancement, or
            // a huge craft), so loop rather than assuming a single step.
            while (!quest.isMaxed(cleared)
                    && QuestHelper.getProgress(player, quest.id()) >= quest.cumulativeTargetForTier(cleared + 1)) {
                cleared++;
                QuestHelper.setTier(player, quest.id(), cleared);
                completeTier(serverPlayer, quest, cleared);
            }
        }
    }

    private static void completeTier(ServerPlayer player, Quest quest, int tier) {
        if (quest.isMaxed(tier)) {
            QuestHelper.markCompleted(player, quest.id());
        }

        double multiplier = quest.rewardMultiplierForTier(tier);
        StringBuilder rewardText = new StringBuilder();
        for (QuestReward reward : quest.rewards()) {
            QuestReward granted = reward;
            if (reward instanceof QuestReward.Money money) {
                // Money scales with the tier; item and command rewards are
                // one-off flavour and only pay out the first time through.
                granted = new QuestReward.Money(Math.max(1L, Math.round(money.amount() * multiplier)));
            } else if (tier > 1) {
                continue;
            }
            granted.grant(player);
            if (rewardText.length() > 0) {
                rewardText.append(", ");
            }
            rewardText.append(granted.describe());
        }

        String heading = quest.repeatable()
                ? "Tier " + tier + " complete: " + quest.resolveName()
                : "Quest complete: " + quest.resolveName();
        player.displayClientMessage(Component.literal(heading).withStyle(ChatFormatting.GOLD), false);
        if (rewardText.length() > 0) {
            player.displayClientMessage(Component.literal("Rewards: " + rewardText)
                    .withStyle(ChatFormatting.GREEN), false);
        }
        if (quest.repeatable() && !quest.isMaxed(tier)) {
            int next = quest.cumulativeTargetForTier(tier + 1);
            player.displayClientMessage(Component.literal(
                            "Next tier: " + next + " total (" + quest.targetForTier(tier + 1) + " more)")
                    .withStyle(ChatFormatting.DARK_AQUA), false);
        }

        // Announce anything this quest just unlocked.
        for (Quest candidate : QuestRegistry.all(player.level().registryAccess())) {
            if (candidate.dependencies().contains(quest.id())
                    && !QuestHelper.isCompleted(player, candidate.id())
                    && QuestHelper.isUnlocked(player, candidate)) {
                player.displayClientMessage(Component.literal("Unlocked: " + candidate.resolveName())
                        .withStyle(ChatFormatting.AQUA), false);
            }
        }
    }

    private static String blockId(BlockState state) {
        Identifier key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return key == null ? "" : key.toString();
    }

    private static String itemId(ItemStack stack) {
        Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }
}
