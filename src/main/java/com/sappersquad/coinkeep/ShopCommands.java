package com.sappersquad.coinkeep;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * {@code /coinkeep shop ...} - add, remove and reprice the catalog in game.
 *
 * <p>The shop has always been fully editable through datapack JSON, but that
 * is only reachable by someone willing to author a datapack. These commands
 * are the same capability for everyone else, and they are not a second
 * system: every one of them writes the same JSON into the world's own
 * datapack (see {@link ShopEditor}), so an admin who starts here can always
 * open the files later, and a modpack's own entries stay the baseline that
 * edits override.
 *
 * <p>Operator-only, because the shop is server-wide: one player repricing
 * diamonds changes the economy for everybody.
 */
public final class ShopCommands {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ShopCommands() {
    }

    /** Ids currently in the catalog - what the edit commands accept. */
    private static final SuggestionProvider<CommandSourceStack> ENTRY_IDS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(
                    ShopRegistry.all(ctx.getSource().getServer().registryAccess()).stream()
                            .map(ShopEntry::id).sorted().toList(),
                    builder);

    /** Ids this world has edited - what {@code restore} can undo. */
    private static final SuggestionProvider<CommandSourceStack> OVERRIDDEN_IDS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(ShopEditor.overriddenIds(ctx.getSource().getServer()), builder);

    private static final SuggestionProvider<CommandSourceStack> CATEGORY_IDS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(
                    ShopRegistry.categories(ctx.getSource().getServer().registryAccess()).stream()
                            .map(ShopCategory::id).sorted().toList(),
                    builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("coinkeep")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("shop")

                        // --- add the held item -------------------------------
                        .then(Commands.literal("add")
                                .then(Commands.argument("price", LongArgumentType.longArg(1))
                                        .executes(ctx -> add(ctx.getSource(),
                                                LongArgumentType.getLong(ctx, "price"), "materials"))
                                        .then(Commands.argument("category", StringArgumentType.word())
                                                .suggests(CATEGORY_IDS)
                                                .executes(ctx -> add(ctx.getSource(),
                                                        LongArgumentType.getLong(ctx, "price"),
                                                        StringArgumentType.getString(ctx, "category"))))))

                        // --- adjust an existing entry ------------------------
                        .then(Commands.literal("price")
                                .then(Commands.argument("id", StringArgumentType.string()).suggests(ENTRY_IDS)
                                        .then(Commands.argument("price", LongArgumentType.longArg(1))
                                                .executes(ctx -> {
                                                    long price = LongArgumentType.getLong(ctx, "price");
                                                    return edit(ctx.getSource(), id(ctx),
                                                            entry -> entry.withPrice(price),
                                                            "buy price set to $" + CurrencyItem.formatValue(price));
                                                }))))

                        .then(Commands.literal("sellprice")
                                .then(Commands.argument("id", StringArgumentType.string()).suggests(ENTRY_IDS)
                                        .then(Commands.argument("price", LongArgumentType.longArg(0))
                                                .executes(ctx -> {
                                                    long price = LongArgumentType.getLong(ctx, "price");
                                                    String note = price == 0
                                                            ? "sell price back to automatic (40% of buy)"
                                                            : "sell price set to $" + CurrencyItem.formatValue(price);
                                                    return edit(ctx.getSource(), id(ctx),
                                                            entry -> entry.withSellPrice(price), note);
                                                }))))

                        .then(Commands.literal("count")
                                .then(Commands.argument("id", StringArgumentType.string()).suggests(ENTRY_IDS)
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                .executes(ctx -> {
                                                    int count = IntegerArgumentType.getInteger(ctx, "count");
                                                    return edit(ctx.getSource(), id(ctx),
                                                            entry -> entry.withCount(count),
                                                            "now sold in stacks of " + count);
                                                }))))

                        .then(Commands.literal("limit")
                                .then(Commands.argument("id", StringArgumentType.string()).suggests(ENTRY_IDS)
                                        .then(Commands.argument("limit", IntegerArgumentType.integer(0))
                                                .executes(ctx -> {
                                                    int limit = IntegerArgumentType.getInteger(ctx, "limit");
                                                    String note = limit == 0
                                                            ? "purchase limit removed"
                                                            : "each player may now buy it " + limit + "x";
                                                    return edit(ctx.getSource(), id(ctx),
                                                            entry -> entry.withBuyLimit(limit), note);
                                                }))))

                        .then(Commands.literal("category")
                                .then(Commands.argument("id", StringArgumentType.string()).suggests(ENTRY_IDS)
                                        .then(Commands.argument("category", StringArgumentType.word())
                                                .suggests(CATEGORY_IDS)
                                                .executes(ctx -> {
                                                    String category = StringArgumentType.getString(ctx, "category");
                                                    return edit(ctx.getSource(), id(ctx),
                                                            entry -> entry.withCategory(category),
                                                            "moved to the " + category + " tab");
                                                }))))

                        // --- remove / put back -------------------------------
                        .then(Commands.literal("remove")
                                .then(Commands.argument("id", StringArgumentType.string()).suggests(ENTRY_IDS)
                                        .executes(ctx -> edit(ctx.getSource(), id(ctx),
                                                entry -> entry.withEnabled(false),
                                                "removed from the shop"))))

                        .then(Commands.literal("restore")
                                .then(Commands.argument("id", StringArgumentType.string()).suggests(OVERRIDDEN_IDS)
                                        .executes(ctx -> restore(ctx.getSource(), id(ctx)))))

                        .then(Commands.literal("restoreall")
                                .executes(ctx -> restoreAll(ctx.getSource())))

                        // --- look around -------------------------------------
                        .then(Commands.literal("list")
                                .executes(ctx -> listCategories(ctx.getSource()))
                                .then(Commands.argument("category", StringArgumentType.word())
                                        .suggests(CATEGORY_IDS)
                                        .executes(ctx -> listCategory(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "category")))))

                        .then(Commands.literal("where")
                                .executes(ctx -> where(ctx.getSource())))));
    }

    private static String id(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        return StringArgumentType.getString(ctx, "id");
    }

    // ==================== add ====================

    private static int add(CommandSourceStack source, long price, String category) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            source.sendFailure(Component.literal("Run this as a player - it lists the item you are holding."));
            return 0;
        }

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            source.sendFailure(Component.literal("Hold the item you want to sell in your main hand."));
            return 0;
        }

        MinecraftServer server = source.getServer();
        String id = freshId(server, held);
        ShopEntry entry = ShopEntry.fromStack(id, category, held, price);

        // A fresh entry derives its sell price and so cannot loop, but the
        // check is cheap and this is the one place a bad number could enter.
        if (entry.createsMoneyLoop()) {
            source.sendFailure(loopMessage(entry));
            return 0;
        }

        if (!write(source, entry, "Added " + entry.count() + "x " + entry.displayName()
                + " to the " + entry.category() + " tab for $" + CurrencyItem.formatValue(price)
                + "  (id: " + id + ")")) {
            return 0;
        }

        // Allowed, but say so: an unknown category still works - the entry
        // lands in a placeholder tab at the end of the sidebar - and silently
        // creating a mystery tab would be baffling.
        boolean known = ShopRegistry.categories(server.registryAccess()).stream()
                .anyMatch(c -> ShopCategory.normaliseId(c.id()).equals(entry.category()));
        if (!known) {
            source.sendSuccess(() -> Component.literal(
                    "Note: no category '" + entry.category() + "' exists yet, so it gets its own tab at the end."
            ).withStyle(ChatFormatting.YELLOW), false);
        }
        return 1;
    }

    /**
     * An id derived from the item, made unique.
     *
     * Checks the on-disk overrides as well as the live catalog: a removed
     * entry is absent from the registry but its file still exists, and
     * reusing that id would overwrite the removal.
     */
    private static String freshId(MinecraftServer server, ItemStack stack) {
        net.minecraft.resources.ResourceLocation key =
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        String base = key == null ? "item" : key.getPath();
        List<String> taken = new ArrayList<>(ShopEditor.overriddenIds(server));
        ShopRegistry.all(server.registryAccess()).forEach(entry -> taken.add(entry.id()));

        if (!taken.contains(base)) {
            return base;
        }
        for (int n = 2; ; n++) {
            String candidate = base + "_" + n;
            if (!taken.contains(candidate)) {
                return candidate;
            }
        }
    }

    // ==================== edit / restore ====================

    private static int edit(CommandSourceStack source, String id, UnaryOperator<ShopEntry> change, String note) {
        MinecraftServer server = source.getServer();
        ShopEntry current = ShopRegistry.byId(server.registryAccess(), id);
        if (current == null) {
            source.sendFailure(Component.literal("No shop entry '" + id + "'."
                    + (ShopEditor.overriddenIds(server).contains(id)
                    ? " It is currently removed - use /coinkeep shop restore " + id + " first."
                    : " Use /coinkeep shop list to see what is there.")));
            return 0;
        }

        ShopEntry updated = change.apply(current);
        if (updated.enabled() && updated.createsMoneyLoop()) {
            source.sendFailure(loopMessage(updated));
            return 0;
        }
        return write(source, updated, updated.displayName() + ": " + note) ? 1 : 0;
    }

    private static int restore(CommandSourceStack source, String id) {
        MinecraftServer server = source.getServer();
        try {
            if (!ShopEditor.delete(server, id)) {
                source.sendFailure(Component.literal("'" + id + "' has not been edited in this world."));
                return 0;
            }
        } catch (IOException e) {
            return ioFailure(source, e);
        }
        source.sendSuccess(() -> Component.literal("Restored '" + id + "' to its original settings.")
                .withStyle(ChatFormatting.GREEN), true);
        applyChanges(source, server);
        return 1;
    }

    private static int restoreAll(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        int removed;
        try {
            removed = ShopEditor.deleteAll(server);
        } catch (IOException e) {
            return ioFailure(source, e);
        }
        if (removed == 0) {
            source.sendFailure(Component.literal("The shop has not been edited in this world."));
            return 0;
        }
        int count = removed;
        source.sendSuccess(() -> Component.literal("Undid " + count + " shop edit" + (count == 1 ? "" : "s")
                + " - the catalog is back to what the mod and datapacks ship.")
                .withStyle(ChatFormatting.GREEN), true);
        applyChanges(source, server);
        return 1;
    }

    /** Writes one entry and applies it. @return false if the write failed. */
    private static boolean write(CommandSourceStack source, ShopEntry entry, String message) {
        MinecraftServer server = source.getServer();
        try {
            ShopEditor.save(server, entry);
        } catch (IOException e) {
            ioFailure(source, e);
            return false;
        }
        source.sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GREEN), true);
        applyChanges(source, server);
        return true;
    }

    /**
     * Reloads so the edit takes effect for everyone immediately.
     *
     * The success message has already been sent, because the file on disk is
     * the durable part - it survives even if the reload fails, and will apply
     * on the next one. Only a failure needs reporting here.
     */
    private static void applyChanges(CommandSourceStack source, MinecraftServer server) {
        ShopEditor.reload(server).exceptionally(error -> {
            LOGGER.warn("Coinkeep shop edit saved but the reload failed", error);
            source.sendFailure(Component.literal(
                    "Saved, but reloading failed - run /reload to apply it."));
            return null;
        });
    }

    private static int ioFailure(CommandSourceStack source, IOException e) {
        LOGGER.warn("Coinkeep could not write the shop datapack", e);
        source.sendFailure(Component.literal("Could not write to the world folder: " + e.getMessage()));
        return 0;
    }

    private static Component loopMessage(ShopEntry entry) {
        long sell = entry.baseSellPrice();
        long perUnit = Math.round(entry.perUnitBuyPrice());
        return Component.literal(
                "Refused: that would sell for $" + CurrencyItem.formatValue(sell)
                        + " but cost $" + CurrencyItem.formatValue(perUnit)
                        + " each to buy, so anyone could buy and re-sell it forever for free money."
                        + " Keep the sell price below the per-item buy price.");
    }

    // ==================== look around ====================

    private static int listCategories(CommandSourceStack source) {
        var access = source.getServer().registryAccess();
        List<ShopCategory> categories = ShopRegistry.categories(access);
        source.sendSuccess(() -> Component.literal("Shop tabs:").withStyle(ChatFormatting.GOLD), false);
        for (ShopCategory category : categories) {
            int size = ShopRegistry.inCategory(access, category.id()).size();
            source.sendSuccess(() -> Component.literal("  " + category.id() + " - " + size + " items")
                    .withStyle(ChatFormatting.GRAY), false);
        }
        int edits = ShopEditor.overriddenIds(source.getServer()).size();
        if (edits > 0) {
            source.sendSuccess(() -> Component.literal(edits + " entr" + (edits == 1 ? "y has" : "ies have")
                    + " been edited in this world.").withStyle(ChatFormatting.DARK_AQUA), false);
        }
        return categories.size();
    }

    private static int listCategory(CommandSourceStack source, String category) {
        var access = source.getServer().registryAccess();
        List<ShopEntry> entries = ShopRegistry.inCategory(access, category);
        if (entries.isEmpty()) {
            source.sendFailure(Component.literal("No tab '" + category + "'. Use /coinkeep shop list."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(category + ":").withStyle(ChatFormatting.GOLD), false);
        for (ShopEntry entry : entries) {
            source.sendSuccess(() -> Component.literal("  " + entry.id() + "  -  "
                    + entry.count() + "x " + entry.displayName()
                    + "  $" + CurrencyItem.formatValue(entry.price())).withStyle(ChatFormatting.GRAY), false);
        }
        return entries.size();
    }

    private static int where(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        if (!ShopEditor.packExists(server)) {
            source.sendSuccess(() -> Component.literal(
                    "No edits yet. The first /coinkeep shop change writes a datapack to "
                            + ShopEditor.packLocation(server)).withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        List<String> ids = ShopEditor.overriddenIds(server);
        source.sendSuccess(() -> Component.literal("Your shop edits (" + ids.size() + ") live in:")
                .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("  " + ShopEditor.packLocation(server))
                .withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal(
                "That folder is an ordinary datapack - copy it to another world, or edit the files by hand.")
                .withStyle(ChatFormatting.DARK_AQUA), false);
        return ids.size();
    }
}
