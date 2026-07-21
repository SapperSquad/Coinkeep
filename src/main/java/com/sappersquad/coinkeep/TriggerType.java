package com.sappersquad.coinkeep;

import com.mojang.serialization.Codec;

import java.util.Locale;

public enum TriggerType {
    BLOCK_BREAK,
    MOB_KILL,
    ITEM_CRAFT,
    ADVANCEMENT;

    /** Written in JSON as lowercase, e.g. "trigger": "block_break". */
    public static final Codec<TriggerType> CODEC = Codec.STRING.xmap(
            name -> TriggerType.valueOf(name.toUpperCase(Locale.ROOT)),
            type -> type.name().toLowerCase(Locale.ROOT));
}
