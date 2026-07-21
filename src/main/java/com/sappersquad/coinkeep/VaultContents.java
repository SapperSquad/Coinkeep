package com.sappersquad.coinkeep;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Optional;
import java.util.UUID;

/**
 * What a vault ITEM is carrying: the money inside it, and who it still
 * belongs to.
 *
 * Both matter. A vault broken by its owner keeps its cash, so the whole
 * thing can be picked up and moved - but it also keeps the owner, so if you
 * are killed carrying it, whoever loots it still has to crack it open. The
 * vault becomes portable loot rather than a safe you can only ever empty in
 * place.
 */
public record VaultContents(long stored, Optional<UUID> owner) {

    public static final VaultContents EMPTY = new VaultContents(0L, Optional.empty());

    public static final Codec<VaultContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.optionalFieldOf("stored", 0L).forGetter(VaultContents::stored),
            UUIDUtil.STRING_CODEC.optionalFieldOf("owner").forGetter(VaultContents::owner)
    ).apply(instance, VaultContents::new));

    public static final StreamCodec<ByteBuf, VaultContents> STREAM_CODEC =
            ByteBufCodecs.fromCodec(CODEC);

    public boolean isEmpty() {
        return stored <= 0L && owner.isEmpty();
    }
}
