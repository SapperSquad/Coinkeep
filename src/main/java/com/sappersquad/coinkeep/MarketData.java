package com.sappersquad.coinkeep;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.HashMap;
import java.util.Map;

/**
 * How saturated this player's market is, per item.
 *
 * Deliberately PER PLAYER rather than server-global. Two reasons:
 *  - it rides the same synced-attachment pattern the balance and quest
 *    progress already use, so the client can price the sell screen live
 *    with no custom packet;
 *  - one player grinding cobblestone can't tank the price for everyone
 *    else on a server, which would punish latecomers.
 *
 * Flavour-wise: each player has their own buyers, and those buyers get
 * their fill.
 *
 * @param sold        item id -> how many sales the buyers have absorbed
 * @param lastDecayed game time (ticks) that recovery was last applied
 */
public record MarketData(Map<String, Integer> sold, long lastDecayed, Map<String, Integer> bought) {

    public static final Codec<MarketData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("sold").forGetter(MarketData::sold),
            Codec.LONG.optionalFieldOf("last_decayed", 0L).forGetter(MarketData::lastDecayed),
            // optionalFieldOf so worlds saved before buy limits existed still load.
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("bought", Map.of())
                    .forGetter(MarketData::bought)
    ).apply(instance, (sold, last, bought) ->
            new MarketData(new HashMap<>(sold), last, new HashMap<>(bought))));

    public static final StreamCodec<ByteBuf, MarketData> STREAM_CODEC =
            ByteBufCodecs.fromCodec(CODEC);

    public static MarketData empty() {
        return new MarketData(new HashMap<>(), 0L, new HashMap<>());
    }

    /** How many times this player has bought a limited entry. */
    public int boughtOf(String entryId) {
        return bought.getOrDefault(entryId, 0);
    }

    public void addBought(String entryId, int amount) {
        bought.merge(entryId, amount, Integer::sum);
    }

    public int soldOf(String entryId) {
        return sold.getOrDefault(entryId, 0);
    }

    public void addSold(String entryId, int amount) {
        sold.merge(entryId, amount, Integer::sum);
    }

    /** Records are shallow-immutable; the maps are mutated in place. */
    public MarketData withLastDecayed(long time) {
        return new MarketData(sold, time, bought);
    }
}
