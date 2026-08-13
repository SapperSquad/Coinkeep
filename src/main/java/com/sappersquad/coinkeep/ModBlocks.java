package com.sappersquad.coinkeep;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(BuiltInRegistries.BLOCK, Coinkeep.MODID);

    // Deliberately tough: on a PvP server the vault is the thing worth
    // raiding, so it should take real effort to break into.
    // setId: 1.21.11 requires the properties to carry the registry id.
    public static final Supplier<Block> VAULT = BLOCKS.register("vault",
            () -> new VaultBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK,
                            Identifier.fromNamespaceAndPath(Coinkeep.MODID, "vault")))
                    .mapColor(MapColor.METAL)
                    .strength(6.0F, 1200.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.NETHERITE_BLOCK)));

    // VaultItem, not a plain BlockItem: a broken vault carries its money and
    // owner on the itemstack, and the tooltip has to show that.
    // useBlockDescriptionPrefix keeps it translating as block.coinkeep.vault.
    public static final DeferredHolder<Item, Item> VAULT_ITEM = ModItems.ITEMS.register("vault",
            () -> new VaultItem(VAULT.get(), new Item.Properties()
                    .setId(ModItems.itemId("vault"))
                    .useBlockDescriptionPrefix()));
}
