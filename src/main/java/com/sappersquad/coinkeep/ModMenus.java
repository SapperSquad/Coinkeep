package com.sappersquad.coinkeep;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(BuiltInRegistries.MENU, Coinkeep.MODID);

    // IMenuTypeExtension.create gives the client constructor a FriendlyByteBuf,
    // which is how the vault's BlockPos reaches the client menu.
    public static final Supplier<MenuType<VaultMenu>> VAULT_MENU = MENUS.register("vault",
            () -> IMenuTypeExtension.create(VaultMenu::new));
}
