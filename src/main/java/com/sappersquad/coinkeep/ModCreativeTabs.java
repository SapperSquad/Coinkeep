package com.sappersquad.coinkeep;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * A tab of our own rather than dumping 21 items into vanilla's Ingredients
 * tab, which is what the mod did before and is poor manners for a released
 * mod - it clutters a vanilla category every player uses.
 */
public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Coinkeep.MODID);

    public static final Supplier<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + Coinkeep.MODID))
                    .icon(() -> new ItemStack(ModItems.BOOK.get()))
                    .displayItems((parameters, output) -> {
                        // The book first - it's the entry point to everything.
                        output.accept(ModItems.BOOK.get());
                        output.accept(ModBlocks.VAULT_ITEM.get());
                        output.accept(ModItems.VAULT_CRACKER.get());
                        // Then currency, ascending, matching BILL_VALUES order.
                        for (DeferredHolder<Item, Item> bill : ModItems.BILLS.values()) {
                            output.accept(bill.get());
                        }
                    })
                    .build());
}
