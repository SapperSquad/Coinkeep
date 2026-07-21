package com.sappersquad.coinkeep;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

/**
 * A chapter of the quest book. The line carries the icon, and every quest
 * inside it falls back to that icon - so a line only needs art once, and
 * individual quests never have to supply their own.
 *
 * Loaded from data/&lt;namespace&gt;/coinkeep/quest_line/&lt;id&gt;.json.
 * {@code sortOrder} controls the order chapters appear in the sidebar,
 * which a datapack needs to be able to control since JSON files load in
 * an arbitrary order (unlike the old hand-written Java list).
 */
public record QuestLine(String id, String name, Item icon, String description, int sortOrder) {

    public static final Codec<QuestLine> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(QuestLine::id),
            Codec.STRING.fieldOf("name").forGetter(QuestLine::name),
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("icon").forGetter(QuestLine::icon),
            Codec.STRING.optionalFieldOf("description", "").forGetter(QuestLine::description),
            Codec.INT.optionalFieldOf("sort_order", 100).forGetter(QuestLine::sortOrder)
    ).apply(instance, QuestLine::new));
}
