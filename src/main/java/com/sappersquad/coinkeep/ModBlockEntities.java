package com.sappersquad.coinkeep;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, Coinkeep.MODID);

    public static final Supplier<BlockEntityType<VaultBlockEntity>> VAULT =
            BLOCK_ENTITIES.register("vault",
                    () -> BlockEntityType.Builder.of(VaultBlockEntity::new, ModBlocks.VAULT.get()).build(null));
}
