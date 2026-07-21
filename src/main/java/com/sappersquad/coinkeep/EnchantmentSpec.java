package com.sappersquad.coinkeep;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One enchantment on a shop entry, e.g. {"id": "minecraft:efficiency", "level": 5}.
 *
 * Levels are deliberately kept at or below the vanilla maximum. Above-max
 * levels do work on a directly-sold item, but the anvil hard-clamps to
 * getMaxLevel() (AnvilMenu:195), so anything the player later re-combines
 * would silently lose the extra levels - and selling power that vanilla
 * cannot produce would turn this from an economy mod into a progression mod.
 */
public record EnchantmentSpec(String id, int level) {

    public static final Codec<EnchantmentSpec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(EnchantmentSpec::id),
            Codec.INT.optionalFieldOf("level", 1).forGetter(EnchantmentSpec::level)
    ).apply(instance, EnchantmentSpec::new));
}
