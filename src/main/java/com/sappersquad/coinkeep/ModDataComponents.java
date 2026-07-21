package com.sappersquad.coinkeep;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModDataComponents {

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(BuiltInRegistries.DATA_COMPONENT_TYPE, Coinkeep.MODID);

    /** Rides on the vault ITEM so a broken vault keeps its money and owner. */
    public static final Supplier<DataComponentType<VaultContents>> VAULT_CONTENTS =
            COMPONENTS.register("vault_contents",
                    () -> DataComponentType.<VaultContents>builder()
                            .persistent(VaultContents.CODEC)
                            .networkSynchronized(VaultContents.STREAM_CODEC)
                            .build());
}
