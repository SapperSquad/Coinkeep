package com.sappersquad.coinkeep.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.sappersquad.coinkeep.Coinkeep;
import com.sappersquad.coinkeep.ShopEditor;
import com.sappersquad.coinkeep.ShopEntry;
import com.sappersquad.coinkeep.ShopRegistry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Covers in-game shop editing ({@code /coinkeep shop}).
 *
 * <p>The editing commands write {@link ShopEntry} JSON into a datapack in the
 * world folder, so the things that can silently break are: the codec failing
 * to survive a write/read round trip (every saved edit corrupt), the new
 * {@code enabled} flag changing how old files parse (every existing datapack
 * broken), and the money-loop guard letting through an entry that can be
 * bought and re-sold at a profit (the economy over).
 */
@GameTestHolder(Coinkeep.MODID)
@PrefixGameTestTemplate(false)
public class ShopEditGameTests {

    /**
     * THE economy invariant, checked across the whole shipped catalog: no
     * entry may sell for at least what one costs to buy. One entry breaking
     * this is worth infinite money to whoever notices, and it is exactly the
     * mistake a hand-edit or a careless rebalance would introduce.
     */
    @GameTest(template = "empty")
    public static void noShippedEntryCanBeBoughtAndResoldAtProfit(GameTestHelper helper) {
        RegistryAccess access = helper.getLevel().registryAccess();
        int checked = 0;
        for (ShopEntry entry : ShopRegistry.all(access)) {
            if (entry.createsMoneyLoop()) {
                helper.fail("'" + entry.id() + "' sells for $" + entry.baseSellPrice()
                        + " but costs $" + Math.round(entry.perUnitBuyPrice())
                        + " each - buying and re-selling it prints money");
            }
            checked++;
        }
        if (checked == 0) {
            helper.fail("no shop entries loaded, so this proved nothing");
        }
        helper.succeed();
    }

    /** The guard has to actually fire, not just never trip on good data. */
    @GameTest(template = "empty")
    public static void theMoneyLoopGuardCatchesABadEdit(GameTestHelper helper) {
        ShopEntry sane = ShopEntry.fromStack("test_sane", "materials",
                new ItemStack(Items.DIAMOND, 1), 1000L);
        helper.assertFalse(sane.createsMoneyLoop(),
                "a derived sell price (40%) must never count as a loop");

        // Sell for exactly the buy price is already a loop: free to churn,
        // and any market recovery above the floor makes it profitable.
        helper.assertTrue(sane.withSellPrice(1000L).createsMoneyLoop(),
                "selling at the buy price must be refused");
        helper.assertTrue(sane.withSellPrice(1500L).createsMoneyLoop(),
                "selling above the buy price must be refused");

        // The bulk case that shipped as a real bug once: price buys a STACK,
        // selling is per item, so the comparison must be per unit.
        ShopEntry bulk = ShopEntry.fromStack("test_bulk", "materials",
                new ItemStack(Items.REDSTONE, 16), 250L);
        helper.assertTrue(bulk.withSellPrice(100L).createsMoneyLoop(),
                "16 for $250 is ~$15 each, so $100 each back must be refused");
        helper.succeed();
    }

    /**
     * What {@code /coinkeep shop} writes must read back identically - this is
     * the whole persistence path for in-game edits.
     */
    @GameTest(template = "empty")
    public static void anEditedEntrySurvivesBeingWrittenAndReadBack(GameTestHelper helper) {
        ShopEntry original = ShopEntry.fromStack("test_round_trip", "rare",
                        new ItemStack(Items.DIAMOND_SWORD, 1), 4200L)
                .withSellPrice(900L)
                .withBuyLimit(3)
                .withCount(2);

        var encoded = ShopEntry.CODEC.encodeStart(JsonOps.INSTANCE, original).result();
        helper.assertTrue(encoded.isPresent(), "entry must serialise to JSON");

        var decoded = ShopEntry.CODEC.parse(JsonOps.INSTANCE, encoded.get()).result();
        helper.assertTrue(decoded.isPresent(), "written JSON must parse back");
        helper.assertTrue(original.equals(decoded.get()),
                "round trip changed the entry:\n  wrote " + original + "\n  read  " + decoded.get());
        helper.succeed();
    }

    /**
     * Every shop_entry JSON written before 1.4.0 lacks {@code enabled}. If it
     * did not default to true, every existing datapack would go silently
     * empty on update.
     */
    @GameTest(template = "empty")
    public static void oldJsonWithoutTheEnabledFlagStaysVisible(GameTestHelper helper) {
        ShopEntry legacy = parse(helper, """
                { "id": "test_legacy", "category": "rare", "item": "minecraft:elytra", "price": 100 }""");
        helper.assertTrue(legacy.enabled(),
                "an entry with no 'enabled' field must stay in the shop");

        ShopEntry removed = parse(helper, """
                { "id": "test_removed", "category": "rare", "item": "minecraft:elytra",
                  "price": 100, "enabled": false }""");
        helper.assertFalse(removed.enabled(), "'enabled': false must parse as removed");
        helper.succeed();
    }

    /**
     * Adding the item in your hand has to list exactly that item. Enchanted
     * gear keeps its enchantments on ENCHANTMENTS; an enchanted book keeps
     * them as cargo on STORED_ENCHANTMENTS, or buying one would hand over a
     * magic book that transfers nothing at an anvil.
     */
    @GameTest(template = "empty")
    public static void addingAHeldItemCapturesWhatYouAreHolding(GameTestHelper helper) {
        RegistryAccess access = helper.getLevel().registryAccess();
        var lookup = access.lookupOrThrow(Registries.ENCHANTMENT);
        var sharpness = lookup.getOrThrow(Enchantments.SHARPNESS);

        // Plain stack: item, stack size and name captured as held.
        ItemStack plain = new ItemStack(Items.GOLD_INGOT, 7);
        ShopEntry plainEntry = ShopEntry.fromStack("test_plain", "materials", plain, 500L);
        helper.assertTrue(plainEntry.item() == Items.GOLD_INGOT, "item must be captured");
        helper.assertTrue(plainEntry.count() == 7, "stack size must be captured");
        helper.assertTrue(plainEntry.sellable(), "a plain item must stay sellable");

        // Enchanted gear.
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        var gearEnchants = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        gearEnchants.set(sharpness, 4);
        sword.set(DataComponents.ENCHANTMENTS, gearEnchants.toImmutable());
        sword.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Testblade"));

        ShopEntry gear = ShopEntry.fromStack("test_gear", "signature", sword, 90000L);
        helper.assertTrue("Testblade".equals(gear.customName()), "custom name must be captured");
        helper.assertTrue(gear.enchantments().size() == 1, "enchantment must be captured");
        helper.assertTrue(gear.isGear(), "a sword with enchantments is gear");
        ItemStack rebuiltGear = gear.createStack(access);
        helper.assertTrue(
                rebuiltGear.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)
                        .getLevel(sharpness) == 4,
                "gear must be handed over with LIVE enchantments");

        // Enchanted book: same capture, different destination component.
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        var bookEnchants = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        bookEnchants.set(sharpness, 3);
        book.set(DataComponents.STORED_ENCHANTMENTS, bookEnchants.toImmutable());

        ShopEntry bookEntry = ShopEntry.fromStack("test_book", "enchantments", book, 8000L);
        helper.assertTrue(bookEntry.enchantments().size() == 1, "book enchantment must be captured");
        helper.assertFalse(bookEntry.isGear(), "an enchanted book is never gear");
        ItemStack rebuiltBook = bookEntry.createStack(access);
        helper.assertTrue(
                rebuiltBook.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY)
                        .getLevel(sharpness) == 3,
                "a book must be handed over with STORED enchantments, not live ones");
        helper.succeed();
    }

    /**
     * The real persistence path, exercised against the real world folder: an
     * edit becomes a datapack file on disk, that file reads back as the same
     * entry, and undoing the edit removes it.
     *
     * <p>Everything else about editing is in-memory and cheap to get right.
     * This is the part that touches the filesystem, and a silent failure here
     * looks exactly like success until the next restart, when the edit is
     * gone.
     */
    @GameTest(template = "empty")
    public static void anEditIsWrittenAsADatapackAndReadsBack(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        String id = "gametest_written_entry";
        ShopEntry entry = ShopEntry.fromStack(id, "materials",
                new ItemStack(Items.COPPER_INGOT, 3), 750L).withBuyLimit(2);

        try {
            try {
                ShopEditor.save(server, entry);
            } catch (IOException e) {
                helper.fail("could not write the shop datapack: " + e);
                return;
            }

            Path pack = ShopEditor.packLocation(server);
            Path file = pack.resolve("data/coinkeep/coinkeep/shop_entry/" + id + ".json");
            helper.assertTrue(Files.isRegularFile(file),
                    "an edit must land at <world>/datapacks/coinkeep_shop/... but " + file + " is missing");
            helper.assertTrue(Files.isRegularFile(pack.resolve("pack.mcmeta")),
                    "the generated folder must be a loadable datapack (no pack.mcmeta)");

            // Through actual disk, not just through the codec in memory.
            String json;
            try {
                json = Files.readString(file);
            } catch (IOException e) {
                helper.fail("wrote the file but could not read it back: " + e);
                return;
            }
            var reparsed = ShopEntry.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result();
            helper.assertTrue(reparsed.isPresent(), "the written file must be valid shop_entry JSON");
            helper.assertTrue(entry.equals(reparsed.get()),
                    "the file on disk is not the entry that was saved:\n  saved " + entry
                            + "\n  file  " + reparsed.get());

            helper.assertTrue(ShopEditor.overriddenIds(server).contains(id),
                    "a saved edit must be listed as an override, or restore cannot find it");

            // Undo, which is what /coinkeep shop restore does.
            try {
                helper.assertTrue(ShopEditor.delete(server, id), "restore must report that it removed something");
            } catch (IOException e) {
                helper.fail("could not remove the override: " + e);
                return;
            }
            helper.assertFalse(Files.exists(file), "restore must delete the override file");
            helper.assertFalse(ShopEditor.overriddenIds(server).contains(id),
                    "a restored entry must stop being listed as an override");
            helper.succeed();
        } finally {
            // Never leave test data in the world, whatever happened above -
            // a leftover override would change the next run's catalog.
            try {
                ShopEditor.delete(server, id);
            } catch (IOException ignored) {
                // Nothing useful to do; the assertions above already reported.
            }
        }
    }

    /** An edit must change one field and leave the rest of the entry alone. */
    @GameTest(template = "empty")
    public static void editingOneFieldLeavesTheRestUntouched(GameTestHelper helper) {
        ShopEntry base = ShopEntry.fromStack("test_edit", "ores",
                new ItemStack(Items.IRON_INGOT, 4), 800L).withBuyLimit(5);

        assertIdentityKept(helper, base.withPrice(1600L), base, "price");
        assertIdentityKept(helper, base.withCount(8), base, "count");
        assertIdentityKept(helper, base.withEnabled(false), base, "enabled");
        helper.assertTrue(base.withPrice(1600L).price() == 1600L, "price must actually change");
        helper.assertTrue(base.withPrice(1600L).buyLimit() == 5, "buy limit must be preserved");
        helper.assertTrue(base.withEnabled(false).item() == Items.IRON_INGOT,
                "removing must preserve the item, so restoring works");
        helper.succeed();
    }

    /** Asserts an edit kept the fields that identify the entry. */
    private static void assertIdentityKept(GameTestHelper helper, ShopEntry edited, ShopEntry base, String changed) {
        helper.assertTrue(edited.id().equals(base.id()), changed + " edit must keep the id");
        helper.assertTrue(edited.item() == base.item(), changed + " edit must keep the item");
        helper.assertTrue(edited.category().equals(base.category()), changed + " edit must keep the category");
    }

    private static ShopEntry parse(GameTestHelper helper, String json) {
        var result = ShopEntry.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
        helper.assertTrue(result.result().isPresent(),
                "shop_entry must parse: " + result.error().map(Object::toString).orElse(""));
        return result.result().orElseThrow();
    }
}
