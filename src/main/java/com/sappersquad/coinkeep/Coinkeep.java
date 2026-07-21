package com.sappersquad.coinkeep;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(Coinkeep.MODID)
public class Coinkeep {

    public static final String MODID = "coinkeep";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Coinkeep(IEventBus modEventBus, ModContainer modContainer) {
        // Blocks before items: the vault's BlockItem is registered into
        // ModItems and needs the block to exist first.
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModDataComponents.COMPONENTS.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);

        // SERVER-scoped: death rules are the host's call and must not vary
        // per client. Written to serverconfig/coinkeep-server.toml.
        modContainer.registerConfig(ModConfig.Type.SERVER, CoinkeepConfig.SPEC);

        // Quest content is validated on server start / datapack reload
        // instead of here - the datapack registries do not exist yet at mod
        // construction time. See QuestContentValidator.
        LOGGER.info("HELLO FROM {}", MODID);
    }
}
