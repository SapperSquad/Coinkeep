package com.sappersquad.coinkeep;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Per-player quest state. Unlike the old ContainerData approach this rides
 * on a synced attachment (see ModAttachments), so the quest book can be a
 * plain Screen with no menu and no custom packets - and it scales past the
 * fixed int-array size ContainerData imposes.
 */
public record QuestProgressData(Map<String, Integer> progress, Set<String> completed, Map<String, Integer> tiers) {

    public static final Codec<QuestProgressData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("progress").forGetter(QuestProgressData::progress),
            Codec.STRING.listOf().fieldOf("completed")
                    .forGetter(data -> new ArrayList<>(data.completed())),
            // optionalFieldOf so saves written before tiers existed still load.
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("tiers", Map.of())
                    .forGetter(QuestProgressData::tiers)
    ).apply(instance, (progress, completed, tiers) ->
            new QuestProgressData(new HashMap<>(progress), new HashSet<>(completed), new HashMap<>(tiers))));

    /** Sync piggybacks on the persistence codec - no hand-written stream codec. */
    public static final StreamCodec<ByteBuf, QuestProgressData> STREAM_CODEC =
            ByteBufCodecs.fromCodec(CODEC);

    public static QuestProgressData empty() {
        return new QuestProgressData(new HashMap<>(), new HashSet<>(), new HashMap<>());
    }

    /** Tiers already cleared for a quest. 0 = working on tier 1. */
    public int getTier(String questId) {
        return tiers.getOrDefault(questId, 0);
    }

    public void setTier(String questId, int tier) {
        tiers.put(questId, tier);
    }

    public int getProgress(String questId) {
        return progress.getOrDefault(questId, 0);
    }

    public void setProgress(String questId, int value) {
        progress.put(questId, value);
    }

    public void incrementProgress(String questId, int amount) {
        progress.merge(questId, amount, Integer::sum);
    }

    public boolean isCompleted(String questId) {
        return completed.contains(questId);
    }

    public void markCompleted(String questId) {
        completed.add(questId);
    }
}
