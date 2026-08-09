package com.sappersquad.coinkeep;

import com.mojang.logging.LogUtils;
import net.minecraft.core.RegistryAccess;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

import java.util.List;

/**
 * Checks quest content once the datapack registries actually exist.
 *
 * This matters more now that content is JSON: a hand-authored datapack can
 * typo a dependency id, and the only symptom in game would be a quest that
 * sits Locked forever with no error anywhere. Running on datapack sync means
 * /reload re-checks too, so a modpack author gets told immediately.
 */
@EventBusSubscriber(modid = Coinkeep.MODID)
public class QuestContentValidator {

    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        report(event.getServer().registryAccess());
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        // Fires on /reload and on player join; only re-report for /reload
        // (player == null) so joins don't spam the log.
        if (event.getPlayer() == null) {
            report(event.getPlayerList().getServer().registryAccess());
        }
    }

    private static void report(RegistryAccess access) {
        List<String> problems = QuestRegistry.validate(access);
        if (problems.isEmpty()) {
            LOGGER.info("Quest content OK: {} quests across {} lines, {} shop entries in {} categories",
                    QuestRegistry.all(access).size(),
                    QuestRegistry.lines(access).size(),
                    ShopRegistry.all(access).size(),
                    ShopRegistry.categories(access).size());
            // One greppable line per tab, so a server owner can see at a
            // glance which mod contributed which category and how big it is.
            ShopRegistry.categories(access).forEach(category ->
                    LOGGER.info("  shop category '{}' ({}): {} entries",
                            category.id(), category.getLabel(),
                            ShopRegistry.inCategory(access, category.id()).size()));
        } else {
            problems.forEach(problem -> LOGGER.error("QUEST CONTENT: {}", problem));
        }
    }
}
