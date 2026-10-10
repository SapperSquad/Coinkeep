package com.sappersquad.coinkeep;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * The live shop edits, layered on top of the shipped catalog.
 *
 * <p><b>Why this exists at all.</b> Shop entries are a datapack registry, and
 * Minecraft builds those exactly once, when the world loads - {@code /reload}
 * rebuilds recipes, loot and tags but hands the existing registries straight
 * back. So a registry can never carry an edit made during play, and the first
 * version of this feature could only tell players to restart.
 *
 * <p><b>Why the edits are NOT a loaded datapack.</b> They are written to
 * {@code <world>/coinkeep_shop}, which is a complete, valid datapack but sits
 * outside {@code datapacks/} so Minecraft does not load it. That is
 * deliberate, and it is what makes "restore" possible: the registry therefore
 * always holds the <i>pristine</i> shipped entry, and removing an override
 * reveals it again. Had the edits loaded as a datapack, a restart would bake
 * them into the registry and there would be nothing left to restore to.
 * Copying that folder into any world's {@code datapacks/} still works, so it
 * stays shareable.
 *
 * <p>The map is global, not per player - one shop for the server. The synced
 * attachment in {@link ModAttachments} is only the transport that gets it to
 * each client; both sides then read it from here.
 */
public final class ShopOverrides {

    /** id -> the entry that replaces (or adds to) the shipped one. */
    public static final Codec<Map<String, ShopEntry>> CODEC =
            Codec.unboundedMap(Codec.STRING, ShopEntry.CODEC);

    public static final StreamCodec<ByteBuf, Map<String, ShopEntry>> STREAM_CODEC =
            ByteBufCodecs.fromCodec(CODEC);

    private static Map<String, ShopEntry> active = Map.of();

    /**
     * Bumped on every real change. {@link ShopRegistry} caches its grouped
     * view against the registry identity, which does not change when an
     * override does - so this is the second half of that cache key, and
     * without it an edit would not show until something else invalidated it.
     */
    private static int generation;

    private ShopOverrides() {
    }

    public static Map<String, ShopEntry> active() {
        return active;
    }

    public static int generation() {
        return generation;
    }

    /** No-ops when nothing actually changed, so the cache is not thrown away for free. */
    public static void set(Map<String, ShopEntry> overrides) {
        Map<String, ShopEntry> copy = Map.copyOf(overrides);
        if (copy.equals(active)) {
            return;
        }
        active = copy;
        generation++;
    }

    /** Re-reads the edit folder and pushes the result to every online player. */
    public static void refreshFromDisk(MinecraftServer server) {
        set(ShopEditor.loadAll(server));
        pushToAll(server);
    }

    public static void pushToAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            pushTo(player);
        }
    }

    /** Sends the current edits to one player; NeoForge syncs the attachment for us. */
    public static void pushTo(ServerPlayer player) {
        player.setData(ModAttachments.SHOP_OVERRIDES.get(), active);
    }
}
