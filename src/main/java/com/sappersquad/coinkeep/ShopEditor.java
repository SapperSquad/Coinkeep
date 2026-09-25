package com.sappersquad.coinkeep;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * Writes shop edits into a real datapack inside the world folder, then
 * reloads.
 *
 * <p><b>Why a datapack rather than saved world data.</b> The shop is a synced
 * datapack registry, and Coinkeep deliberately ships no custom network
 * packets. An override layer stored in level data would have needed its own
 * sync path, a second source of truth, and a merge rule at every read site.
 * Writing the edits as datapack JSON instead means the existing pipeline does
 * all of it for free: the registry reload syncs to every client exactly as a
 * modpack's own datapack does, {@link ShopRegistry}'s registry-identity cache
 * invalidates on its own, and the content validator checks the result.
 *
 * <p>The side effect is the best part: what comes out is an ordinary datapack
 * at {@code <world>/datapacks/coinkeep_shop}. A server owner can zip it, hand
 * it to someone else, commit it, or open the files and hand-edit them. In-game
 * editing and datapack editing are the same feature, not two.
 *
 * <p>Removing a shipped item works the same way. A datapack cannot delete a
 * registry entry, so "remove" writes an override of that entry with
 * {@code "enabled": false}, which {@link ShopRegistry} drops on load.
 */
public final class ShopEditor {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Folder name under {@code <world>/datapacks/}. */
    public static final String PACK_ID = "coinkeep_shop";

    /**
     * Mirrors where the mod's own entries live, so a file here overrides the
     * shipped entry of the same name: data/&lt;namespace&gt;/coinkeep/shop_entry.
     */
    private static final String ENTRY_DIR = "data/" + Coinkeep.MODID + "/" + Coinkeep.MODID + "/shop_entry";

    private ShopEditor() {
    }

    private static Path packRoot(MinecraftServer server) {
        return server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(PACK_ID);
    }

    private static Path entryFile(MinecraftServer server, String id) {
        return packRoot(server).resolve(ENTRY_DIR).resolve(id + ".json");
    }

    /** True once the player has made at least one edit in this world. */
    public static boolean packExists(MinecraftServer server) {
        return Files.isDirectory(packRoot(server));
    }

    /** Where to tell the player their datapack lives. */
    public static Path packLocation(MinecraftServer server) {
        return packRoot(server);
    }

    /**
     * Entry ids this world has overridden, i.e. the ones {@code restore} can
     * put back. Read off disk rather than tracked in memory so that
     * hand-added files count too.
     */
    public static List<String> overriddenIds(MinecraftServer server) {
        Path dir = packRoot(server).resolve(ENTRY_DIR);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> {
                        String file = path.getFileName().toString();
                        ids.add(file.substring(0, file.length() - ".json".length()));
                    });
        } catch (IOException e) {
            LOGGER.warn("Could not list Coinkeep shop overrides", e);
            return List.of();
        }
        ids.sort(String::compareTo);
        return List.copyOf(ids);
    }

    /**
     * Writes one entry, creating the datapack on first use.
     *
     * @throws IOException if the world folder cannot be written, which the
     *                     command surfaces rather than swallowing - a silent
     *                     failure here would look like the edit worked until
     *                     the next restart.
     */
    public static void save(MinecraftServer server, ShopEntry entry) throws IOException {
        ensurePack(server);
        JsonElement json = ShopEntry.CODEC.encodeStart(JsonOps.INSTANCE, entry)
                .result()
                .orElseThrow(() -> new IOException("could not serialise shop entry '" + entry.id() + "'"));
        Path file = entryFile(server, entry.id());
        Files.createDirectories(file.getParent());
        Files.writeString(file, GSON.toJson(json) + "\n", StandardCharsets.UTF_8);
    }

    /** Drops an override, returning the entry to whatever the mod/modpack ships. */
    public static boolean delete(MinecraftServer server, String id) throws IOException {
        return Files.deleteIfExists(entryFile(server, id));
    }

    /** Drops every override at once. @return how many files were removed. */
    public static int deleteAll(MinecraftServer server) throws IOException {
        int removed = 0;
        for (String id : overriddenIds(server)) {
            if (delete(server, id)) {
                removed++;
            }
        }
        return removed;
    }

    private static void ensurePack(MinecraftServer server) throws IOException {
        Path root = packRoot(server);
        Path mcmeta = root.resolve("pack.mcmeta");
        if (Files.exists(mcmeta)) {
            return;
        }
        Files.createDirectories(root);
        // Read the format from the running game rather than hardcoding it, so
        // this stays correct on every Minecraft version Coinkeep ships for.
        int format = SharedConstants.getCurrentVersion().getPackVersion(PackType.SERVER_DATA);
        Files.writeString(mcmeta, """
                {
                  "pack": {
                    "description": "Coinkeep shop edits for this world - written by /coinkeep shop",
                    "pack_format": %d
                  }
                }
                """.formatted(format), StandardCharsets.UTF_8);
    }

    /**
     * Applies what was just written.
     *
     * <p>Deliberately the same two steps vanilla's {@code /reload} takes:
     * rescan the pack folder, then reload with the new pack included. A pack
     * that has just appeared is not in the selected list yet, so simply
     * reloading the current selection would write the file and change
     * nothing - the bug this method exists to avoid.
     */
    public static java.util.concurrent.CompletableFuture<Void> reload(MinecraftServer server) {
        PackRepository repository = server.getPackRepository();
        repository.reload();

        Collection<String> selected = new ArrayList<>(repository.getSelectedIds());
        Collection<String> disabled = server.getWorldData().getDataConfiguration().dataPacks().getDisabled();
        for (String id : repository.getAvailableIds()) {
            if (!disabled.contains(id) && !selected.contains(id)) {
                selected.add(id);
            }
        }
        return server.reloadResources(selected);
    }
}
