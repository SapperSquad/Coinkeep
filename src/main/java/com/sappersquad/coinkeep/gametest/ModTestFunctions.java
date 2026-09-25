package com.sappersquad.coinkeep.gametest;

import com.sappersquad.coinkeep.Coinkeep;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Consumer;

/**
 * 1.21.11 rebuilt the GameTest framework around registries. The annotations
 * ({@code @GameTestHolder}, {@code @GameTest}) are gone: a test is now a
 * {@code TEST_FUNCTION} registry entry paired with a
 * {@code data/coinkeep/test_instance/<name>.json} file that names it, its
 * structure and its environment. The test methods themselves are unchanged -
 * this class is only the new wiring.
 *
 * <p>Every name registered here must have a matching test_instance JSON, or
 * the test silently never runs. Keep the two lists in the same order so a
 * mismatch is visible at a glance.
 */
public final class ModTestFunctions {

    public static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, Coinkeep.MODID);

    static {
        // Vault wire-format tests (VaultSyncGameTests)
        TEST_FUNCTIONS.register("vault_every_balance_survives_the_short_wire",
                () -> VaultSyncGameTests::everyBalanceSurvivesTheShortWire);
        TEST_FUNCTIONS.register("vault_stored_value_round_trips_locally",
                () -> VaultSyncGameTests::storedValueRoundTripsLocally);
        TEST_FUNCTIONS.register("vault_negative_balances_are_clamped",
                () -> VaultSyncGameTests::negativeBalancesAreClamped);

        // Quest trigger-index tests (QuestIndexGameTests)
        TEST_FUNCTIONS.register("quest_every_quest_reachable_by_its_own_trigger",
                () -> QuestIndexGameTests::everyQuestIsReachableByItsOwnTrigger);
        TEST_FUNCTIONS.register("quest_index_matches_the_scan_exactly",
                () -> QuestIndexGameTests::indexMatchesTheScanExactly);
        TEST_FUNCTIONS.register("quest_unknown_triggers_return_empty",
                () -> QuestIndexGameTests::unknownTriggersReturnEmpty);
        TEST_FUNCTIONS.register("quest_trigger_type_is_part_of_the_key",
                () -> QuestIndexGameTests::triggerTypeIsPartOfTheKey);

        // Shop category migration tests (ShopCategoryGameTests)
        TEST_FUNCTIONS.register("shop_the_eight_built_ins_survive_the_conversion",
                () -> ShopCategoryGameTests::theEightBuiltInsSurviveTheConversion);
        TEST_FUNCTIONS.register("shop_every_shipped_entry_lands_in_its_old_category",
                () -> ShopCategoryGameTests::everyShippedEntryLandsInItsOldCategory);
        TEST_FUNCTIONS.register("shop_every_content_registry_is_synced_to_clients",
                () -> ShopCategoryGameTests::everyContentRegistryIsSyncedToClients);
        TEST_FUNCTIONS.register("shop_one_zero_zero_entry_json_still_parses",
                () -> ShopCategoryGameTests::oneZeroZeroEntryJsonStillParses);
        TEST_FUNCTIONS.register("shop_an_addon_category_json_round_trips",
                () -> ShopCategoryGameTests::anAddonCategoryJsonRoundTrips);
        TEST_FUNCTIONS.register("shop_content_still_validates",
                () -> ShopCategoryGameTests::contentStillValidates);

        // In-game shop editing (ShopEditGameTests)
        TEST_FUNCTIONS.register("shopedit_no_shipped_entry_resold_at_profit",
                () -> ShopEditGameTests::noShippedEntryCanBeBoughtAndResoldAtProfit);
        TEST_FUNCTIONS.register("shopedit_money_loop_guard_catches_a_bad_edit",
                () -> ShopEditGameTests::theMoneyLoopGuardCatchesABadEdit);
        TEST_FUNCTIONS.register("shopedit_entry_survives_write_and_read_back",
                () -> ShopEditGameTests::anEditedEntrySurvivesBeingWrittenAndReadBack);
        TEST_FUNCTIONS.register("shopedit_old_json_without_enabled_stays_visible",
                () -> ShopEditGameTests::oldJsonWithoutTheEnabledFlagStaysVisible);
        TEST_FUNCTIONS.register("shopedit_adding_held_item_captures_it",
                () -> ShopEditGameTests::addingAHeldItemCapturesWhatYouAreHolding);
        TEST_FUNCTIONS.register("shopedit_edit_written_as_datapack",
                () -> ShopEditGameTests::anEditIsWrittenAsADatapackAndReadsBack);
        TEST_FUNCTIONS.register("shopedit_editing_one_field_leaves_rest",
                () -> ShopEditGameTests::editingOneFieldLeavesTheRestUntouched);
    }

    private ModTestFunctions() {
    }
}
