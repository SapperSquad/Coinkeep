package com.sappersquad.coinkeep;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Optional;

/**
 * One quest in a {@link QuestLine}.
 *
 * Two authoring rules are enforced here rather than left to whoever adds
 * content, because both were real complaints about other quest mods:
 *
 *  - {@code name} may be left null/blank and a sensible one is derived from
 *    the trigger, so a quest can never render as unnamed.
 *  - {@code icon} may be left null and is resolved from the trigger target,
 *    then a reward, then the parent line's icon - so you only need art once
 *    per line, not once per quest.
 *
 * @param dependencies ids of quests that must be completed first. Empty means
 *                     the quest is available immediately.
 */
public record Quest(
        String id,
        String lineId,
        String name,
        String description,
        Item icon,
        List<String> dependencies,
        TriggerType triggerType,
        String triggerTarget,
        int requiredCount,
        List<QuestReward> rewards,
        int maxTier,
        double targetGrowth,
        double rewardGrowth
) {
    /**
     * Progress is a LIFETIME running total that is never reset - clearing a
     * tier just raises the bar. So a player who has mined 40 iron still has
     * 40 toward the next, bigger threshold rather than starting at zero.
     */
    public int targetForTier(int tier) {
        return Math.max(1, (int) Math.round(requiredCount * Math.pow(targetGrowth, Math.max(0, tier - 1))));
    }

    /** Total lifetime count to have cleared tiers 1..tier. */
    public int cumulativeTargetForTier(int tier) {
        int total = 0;
        for (int i = 1; i <= tier; i++) {
            total += targetForTier(i);
        }
        return total;
    }

    /**
     * Money rewards scale per tier. Growth defaults to matching targetGrowth,
     * so the pay-per-item stays flat: later tiers are bigger, not richer.
     * That keeps an infinitely repeatable quest from becoming a money
     * printer, the same way the market's saturation does.
     */
    public double rewardMultiplierForTier(int tier) {
        return Math.pow(rewardGrowth, Math.max(0, tier - 1));
    }

    /** maxTier 0 means it repeats forever. */
    public boolean isMaxed(int completedTiers) {
        return maxTier > 0 && completedTiers >= maxTier;
    }

    public boolean repeatable() {
        return maxTier != 1;
    }
    /**
     * Loaded from data/&lt;namespace&gt;/coinkeep/quest/&lt;id&gt;.json.
     *
     * name / description / icon are all optional: leaving them out is the
     * normal case, because resolveName() and resolveIcon() derive them from
     * the trigger. That is what keeps adding a quest to one short JSON file.
     */
    public static final Codec<Quest> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(Quest::id),
            Codec.STRING.fieldOf("line").forGetter(Quest::lineId),
            Codec.STRING.optionalFieldOf("name").forGetter(q -> Optional.ofNullable(q.name())),
            Codec.STRING.optionalFieldOf("description").forGetter(q -> Optional.ofNullable(q.description())),
            BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("icon")
                    .forGetter(q -> Optional.ofNullable(q.icon())),
            Codec.STRING.listOf().optionalFieldOf("dependencies", List.of()).forGetter(Quest::dependencies),
            TriggerType.CODEC.fieldOf("trigger").forGetter(Quest::triggerType),
            Codec.STRING.fieldOf("target").forGetter(Quest::triggerTarget),
            Codec.INT.optionalFieldOf("count", 1).forGetter(Quest::requiredCount),
            QuestReward.CODEC.listOf().optionalFieldOf("rewards", List.of()).forGetter(Quest::rewards),
            // 1 = one-shot (the default), 0 = repeats forever, N = caps at N.
            Codec.INT.optionalFieldOf("max_tier", 1).forGetter(Quest::maxTier),
            Codec.DOUBLE.optionalFieldOf("target_growth", 1.5).forGetter(Quest::targetGrowth),
            Codec.DOUBLE.optionalFieldOf("reward_growth", 1.5).forGetter(Quest::rewardGrowth)
    ).apply(instance, (id, line, name, description, icon, dependencies, trigger, target, count, rewards,
                       maxTier, targetGrowth, rewardGrowth) ->
            new Quest(id, line, name.orElse(null), description.orElse(null), icon.orElse(null),
                    dependencies, trigger, target, count, rewards, maxTier, targetGrowth, rewardGrowth)));

    /** Never blank - falls back to a generated description of the trigger. */
    public String resolveName() {
        if (name != null && !name.isBlank()) {
            return name;
        }
        String target = prettyTargetName();
        return switch (triggerType) {
            case BLOCK_BREAK -> "Mine " + requiredCount + "x " + target;
            case MOB_KILL -> "Kill " + requiredCount + "x " + target;
            case ITEM_CRAFT -> "Craft " + requiredCount + "x " + target;
            // Advancement ids already read as titles ("story/mine_stone" ->
            // "Mine Stone"), so prefixing "Earn" just makes them clumsy.
            case ADVANCEMENT -> target;
        };
    }

    /** Explicit icon, else the trigger's item, else a reward, else the line's. */
    public ItemStack resolveIcon(QuestLine line) {
        if (icon != null) {
            return new ItemStack(icon);
        }
        Item fromTrigger = triggerItem();
        if (fromTrigger != Items.AIR) {
            return new ItemStack(fromTrigger);
        }
        if (!rewards.isEmpty()) {
            return rewards.get(0).icon();
        }
        return new ItemStack(line == null || line.icon() == null ? Items.PAPER : line.icon());
    }

    /** The item matching the trigger target id, or AIR when there isn't one. */
    private Item triggerItem() {
        Identifier key = Identifier.tryParse(triggerTarget);
        if (key == null) {
            return Items.AIR;
        }
        return BuiltInRegistries.ITEM.getValue(key);
    }

    /** "minecraft:diamond_ore" -> "Diamond Ore" (registry name when possible). */
    private String prettyTargetName() {
        Item item = triggerItem();
        if (item != Items.AIR) {
            return item.getName(item.getDefaultInstance()).getString();
        }
        String raw = triggerTarget.contains(":")
                ? triggerTarget.substring(triggerTarget.indexOf(':') + 1)
                : triggerTarget;
        raw = raw.substring(raw.lastIndexOf('/') + 1).replace('_', ' ');
        StringBuilder out = new StringBuilder(raw.length());
        boolean upper = true;
        for (char c : raw.toCharArray()) {
            out.append(upper ? Character.toUpperCase(c) : c);
            upper = c == ' ';
        }
        return out.toString();
    }
}
