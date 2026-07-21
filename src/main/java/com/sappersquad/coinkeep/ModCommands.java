package com.sappersquad.coinkeep;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Optional;

/**
 * NeoForge's event subscriber annotation is now a standalone top-level
 * @EventBusSubscriber (Forge had it nested as @Mod.EventBusSubscriber).
 * Otherwise this reads almost identically to the Forge version - and is
 * actually a little simpler, since BalanceHelper doesn't need the
 * getCapability().ifPresent(...) lambda wrapping anymore.
 */
@EventBusSubscriber(modid = Coinkeep.MODID)
public class ModCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("balance")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    long balance = BalanceHelper.getBalance(player);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Balance: $" + CurrencyItem.formatValue(balance)
                    ).withStyle(ChatFormatting.GOLD), false);
                    return 1;
                }));

        // Meant to be called by other mods' reward systems (FTB Quests'
        // "Command" reward type) rather than typed by players directly -
        // hence requiring op permission. A quest's reward command would
        // read: addbalance @s 500
        dispatcher.register(Commands.literal("addbalance")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    long amount = LongArgumentType.getLong(ctx, "amount");

                                    BalanceHelper.addBalance(target, amount);
                                    target.displayClientMessage(Component.literal(
                                            "+$" + CurrencyItem.formatValue(amount) + " (balance: $"
                                                    + CurrencyItem.formatValue(BalanceHelper.getBalance(target)) + ")"
                                    ).withStyle(ChatFormatting.GOLD), false);

                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Gave $" + CurrencyItem.formatValue(amount) + " to " + target.getName().getString()
                                    ).withStyle(ChatFormatting.GRAY), true);
                                    return 1;
                                }))));

        // No /opentasks any more: quest progress is a synced attachment, so
        // the keybind and the book item both open the screen client-side
        // without a server round trip.

        // Player-to-player transfer. Physical bills already allow pooling
        // (whoever right-clicks a bill is credited), but paying for a
        // $200,000 item in bills means handing over dozens of items one
        // right-click at a time - so this exists for the top end.
        dispatcher.register(Commands.literal("pay")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                .executes(ctx -> pay(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player"),
                                        LongArgumentType.getLong(ctx, "amount"))))));

        // Cash conversion, driven by the book's Cash tab. Deliberately not
        // gated behind a block: the shop is portable, so making players walk
        // somewhere to touch their own money would be inconsistent.
        dispatcher.register(Commands.literal("withdraw")
                .then(Commands.argument("denomination", StringArgumentType.string())
                        .executes(ctx -> withdraw(ctx.getSource(),
                                StringArgumentType.getString(ctx, "denomination"), 1))
                        .then(Commands.argument("quantity", IntegerArgumentType.integer(1, 64))
                                .executes(ctx -> withdraw(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "denomination"),
                                        IntegerArgumentType.getInteger(ctx, "quantity"))))));

        dispatcher.register(Commands.literal("depositall")
                .executes(ctx -> depositAll(ctx.getSource())));

        // Triggered by the market screen's sell rows. Quantity is optional
        // (defaults to 1); "all" is handled client-side by passing a count.
        dispatcher.register(Commands.literal("sell")
                .then(Commands.argument("id", StringArgumentType.string())
                        .executes(ctx -> sell(ctx.getSource(), StringArgumentType.getString(ctx, "id"), 1))
                        .then(Commands.argument("quantity", IntegerArgumentType.integer(1))
                                .executes(ctx -> sell(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id"),
                                        IntegerArgumentType.getInteger(ctx, "quantity"))))));

        // Triggered by the Shop screen's "Buy" buttons.
        dispatcher.register(Commands.literal("buy")
                .then(Commands.argument("id", StringArgumentType.string())
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            String id = StringArgumentType.getString(ctx, "id");
                            ShopEntry entry = ShopRegistry.byId(player.level().registryAccess(), id);

                            if (entry == null) {
                                ctx.getSource().sendFailure(Component.literal("Unknown item: " + id));
                                return 0;
                            }

                            if (BalanceHelper.removeBalance(player, entry.price())) {
                                ItemStack stack = buildPurchasedStack(player, entry);
                                if (!player.getInventory().add(stack)) {
                                    player.drop(stack, false);
                                }
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                        "Bought " + entry.count() + "x " + entry.displayName()
                                                + " for $" + CurrencyItem.formatValue(entry.price())
                                                + " (balance: $" + CurrencyItem.formatValue(BalanceHelper.getBalance(player)) + ")"
                                ).withStyle(ChatFormatting.GREEN), false);
                            } else {
                                ctx.getSource().sendFailure(Component.literal(
                                        "Not enough money - need $" + CurrencyItem.formatValue(entry.price())
                                                + ", you have $" + CurrencyItem.formatValue(BalanceHelper.getBalance(player))
                                ));
                            }
                            return 1;
                        })));
    }

    /**
     * Only unmodified copies of an item may be sold.
     *
     * Selling matches on the ITEM, so without this a player selling "Elytra"
     * would have their enchanted Pathfinder consumed at the plain-Elytra
     * price, and selling "Netherite Sword" would eat The Reaper. Anything
     * enchanted or renamed is therefore excluded from sale entirely - it is
     * far better to refuse the sale than to silently destroy a $200,000 item.
     */
    private static boolean isPlain(ItemStack stack) {
        return !stack.has(DataComponents.CUSTOM_NAME)
                && stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty()
                && stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty()
                // A vault carrying money must never be sellable at the price of
                // an empty one - that would quietly destroy the savings inside.
                && stack.getOrDefault(ModDataComponents.VAULT_CONTENTS.get(), VaultContents.EMPTY).isEmpty();
    }

    /**
     * Transfers balance between players. Debits before crediting, and
     * refuses self-payment - paying yourself is a no-op that would only ever
     * be a typo, and letting it through makes "did that work?" ambiguous.
     */
    private static int pay(CommandSourceStack source, ServerPlayer target, long amount)
            throws CommandSyntaxException {
        ServerPlayer payer = source.getPlayerOrException();

        if (target.getUUID().equals(payer.getUUID())) {
            source.sendFailure(Component.literal("You can't pay yourself."));
            return 0;
        }
        if (!BalanceHelper.removeBalance(payer, amount)) {
            source.sendFailure(Component.literal(
                    "Not enough money - you have $" + CurrencyItem.formatValue(BalanceHelper.getBalance(payer))));
            return 0;
        }
        BalanceHelper.addBalance(target, amount);

        String payerName = payer.getGameProfile().getName();
        target.displayClientMessage(Component.literal(
                "+$" + CurrencyItem.formatValue(amount) + " from " + payerName
                        + "  (balance: $" + CurrencyItem.formatValue(BalanceHelper.getBalance(target)) + ")"
        ).withStyle(ChatFormatting.GOLD), false);

        source.sendSuccess(() -> Component.literal(
                "Paid $" + CurrencyItem.formatValue(amount) + " to " + target.getGameProfile().getName()
                        + "  (balance: $" + CurrencyItem.formatValue(BalanceHelper.getBalance(payer)) + ")"
        ).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    /**
     * Turns balance into physical bills. Withdraws only as many as the
     * balance covers rather than refusing outright, and never voids money:
     * the balance is debited per bill actually handed over, so a full
     * inventory drops the rest at your feet instead of losing it.
     */
    private static int withdraw(CommandSourceStack source, String denomination, int quantity)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        long faceValue = ModItems.billValue(denomination);

        if (faceValue <= 0) {
            source.sendFailure(Component.literal("Unknown denomination: " + denomination));
            return 0;
        }

        long balance = BalanceHelper.getBalance(player);
        int affordable = (int) Math.min(quantity, balance / faceValue);
        if (affordable <= 0) {
            source.sendFailure(Component.literal(
                    "Not enough money - a $" + CurrencyItem.formatValue(faceValue)
                            + " bill needs $" + CurrencyItem.formatValue(faceValue)
                            + ", you have $" + CurrencyItem.formatValue(balance)));
            return 0;
        }

        long cost = faceValue * affordable;
        if (!BalanceHelper.removeBalance(player, cost)) {
            source.sendFailure(Component.literal("Not enough money."));
            return 0;
        }

        ItemStack bills = new ItemStack(ModItems.BILLS.get(denomination).get(), affordable);
        if (!player.getInventory().add(bills)) {
            player.drop(bills, false);
        }

        int handed = affordable;
        source.sendSuccess(() -> Component.literal(
                "Withdrew " + handed + "x $" + CurrencyItem.formatValue(faceValue) + " bill"
                        + "  (balance: $" + CurrencyItem.formatValue(BalanceHelper.getBalance(player)) + ")"
        ).withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    /**
     * Banks every bill carried, in one action. Right-clicking bills one at a
     * time already works; this exists because doing it to a stack of 40 is
     * miserable.
     */
    private static int depositAll(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        long total = 0L;
        int bills = 0;

        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof CurrencyItem currency) {
                total += currency.getValue() * stack.getCount();
                bills += stack.getCount();
                stack.setCount(0);
            }
        }

        if (bills == 0) {
            source.sendFailure(Component.literal("You have no bills to deposit."));
            return 0;
        }

        BalanceHelper.addBalance(player, total);
        long deposited = total;
        int counted = bills;
        source.sendSuccess(() -> Component.literal(
                "Deposited " + counted + " bill" + (counted == 1 ? "" : "s")
                        + " worth $" + CurrencyItem.formatValue(deposited)
                        + "  (balance: $" + CurrencyItem.formatValue(BalanceHelper.getBalance(player)) + ")"
        ).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    /**
     * Sells up to {@code quantity} of an entry's item from the player's
     * inventory. Pays the saturating per-unit price (see MarketHelper), so
     * a big dump is worth less than the same items sold over time.
     *
     * Sells only what you actually have rather than failing outright - being
     * told "you only had 7" while still banking those 7 is friendlier than
     * refusing the whole transaction.
     */
    private static int sell(CommandSourceStack source, String id, int quantity) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ShopEntry entry = ShopRegistry.byId(player.level().registryAccess(), id);

        if (entry == null) {
            source.sendFailure(Component.literal("Unknown item: " + id));
            return 0;
        }
        if (!entry.sellable()) {
            source.sendFailure(Component.literal(entry.displayName() + " can't be sold here."));
            return 0;
        }

        MarketHelper.applyRecovery(player);

        int held = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(entry.item()) && isPlain(stack)) {
                held += stack.getCount();
            }
        }
        if (held <= 0) {
            source.sendFailure(Component.literal("You have no " + entry.displayName() + " to sell."));
            return 0;
        }

        int selling = Math.min(quantity, held);
        long payout = MarketHelper.quoteBulk(player, entry, selling);

        // Remove the items first; only pay for what was actually taken.
        int remaining = selling;
        for (ItemStack stack : player.getInventory().items) {
            if (remaining <= 0) {
                break;
            }
            if (stack.is(entry.item()) && isPlain(stack)) {
                int taken = Math.min(remaining, stack.getCount());
                stack.shrink(taken);
                remaining -= taken;
            }
        }

        BalanceHelper.addBalance(player, payout);
        MarketHelper.recordSale(player, entry, selling);

        int demandPercent = (int) Math.round(MarketHelper.demand(player, entry) * 100);
        source.sendSuccess(() -> Component.literal(
                "Sold " + selling + "x " + entry.displayName()
                        + " for $" + CurrencyItem.formatValue(payout)
                        + "  (demand now " + demandPercent + "%, balance $"
                        + CurrencyItem.formatValue(BalanceHelper.getBalance(player)) + ")"
        ).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    /**
     * Books and gear use DIFFERENT components: an enchanted book carries its
     * enchantments under STORED_ENCHANTMENTS (they're cargo, inert until
     * applied), while a tool or armour piece carries them under ENCHANTMENTS
     * (they're active). Writing gear enchantments to STORED_ would produce a
     * pickaxe that looks enchanted in the tooltip but does nothing.
     */
    private static ItemStack buildPurchasedStack(ServerPlayer player, ShopEntry entry) {
        ItemStack stack = new ItemStack(entry.item(), entry.count());

        if (entry.customName() != null && !entry.customName().isBlank()) {
            stack.set(DataComponents.CUSTOM_NAME,
                    Component.literal(entry.customName()).withStyle(ChatFormatting.GOLD));
        }

        var lookup = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var component = entry.isGear() ? DataComponents.ENCHANTMENTS : DataComponents.STORED_ENCHANTMENTS;
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(
                stack.getOrDefault(component, ItemEnchantments.EMPTY));
        boolean any = false;

        // Legacy single-enchantment form (the enchanted books).
        if (entry.enchantmentId() != null) {
            any |= applyEnchant(lookup, mutable, entry.enchantmentId(), entry.enchantmentLevel());
        }
        for (EnchantmentSpec spec : entry.enchantments()) {
            any |= applyEnchant(lookup, mutable, spec.id(), spec.level());
        }

        if (any) {
            stack.set(component, mutable.toImmutable());
        }
        return stack;
    }

    /** @return true if the enchantment resolved and was applied. */
    private static boolean applyEnchant(net.minecraft.core.HolderLookup.RegistryLookup<Enchantment> lookup,
                                        ItemEnchantments.Mutable target, String id, int level) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null || level <= 0) {
            return false;
        }
        Optional<Holder.Reference<Enchantment>> holder =
                lookup.get(ResourceKey.create(Registries.ENCHANTMENT, location));
        holder.ifPresent(h -> target.set(h, level));
        return holder.isPresent();
    }
}
