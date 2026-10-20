package com.eu.habbo.habbohotel.wired.leaderboard;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.rooms.RoomUserVariableStore;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** The variable leaderboard box reads and writes the official AIR storage format. */
class WiredLeaderboardCodecTest {

    private static WiredLeaderboardConfig sample() {
        return sample("user:77", "user:812", "user:1200");
    }

    private static WiredLeaderboardConfig sample(String filter, String coins, String wins) {
        TreeMap<String, String> overrides = new TreeMap<>();
        overrides.put(coins, "Coins");
        overrides.put(wins, "Wins");
        return new WiredLeaderboardConfig(
                "Top players",
                "This week",
                WiredLeaderboardCodec.SORT_HIGHEST_VALUE,
                2,
                List.of(
                        new WiredLeaderboardConfig.Column(WiredLeaderboardCodec.COLUMN_USERNAME, "Player", 3),
                        new WiredLeaderboardConfig.Column(WiredLeaderboardCodec.COLUMN_VALUE, "Score", 0),
                        new WiredLeaderboardConfig.Column(WiredLeaderboardCodec.COLUMN_LEVEL, "Lvl", 0)),
                true,
                true,
                WiredLeaderboardCodec.OPERAND_LITERAL,
                filter,
                "0",
                "-12",
                overrides,
                5,
                6,
                -10,
                40);
    }

    @Test
    void encodeAndDecodeRoundTrip() {
        WiredLeaderboardCodec.Stored stored = WiredLeaderboardCodec.encode(sample());

        assertEquals(WiredLeaderboardCodec.INT_PARAM_COUNT, stored.intParams().length);
        assertEquals(WiredLeaderboardCodec.VARIABLE_ID_COUNT, stored.variableIds().length);
        assertEquals(
                WiredLeaderboardCodec.STRING_FIELD_COUNT, stored.stringParam().split("\t", -1).length);
        // Overrides are stored by plain string order: "user:1200" before "user:812".
        assertEquals("user:77", stored.variableIds()[0]);
        assertEquals("user:1200", stored.variableIds()[2]);
        assertEquals("user:812", stored.variableIds()[3]);

        WiredLeaderboardCodec.Decoded decoded = WiredLeaderboardCodec.decode(stored, 0, 0);
        assertTrue(decoded.valid(), decoded.error());
        WiredLeaderboardCodec.Stored again = WiredLeaderboardCodec.encode(decoded.config());
        assertArrayEquals(stored.intParams(), again.intParams());
        assertArrayEquals(stored.variableIds(), again.variableIds());
        assertEquals(stored.stringParam(), again.stringParam());
        assertEquals(20, decoded.config().entriesPerPage());
        assertEquals(5, decoded.config().tileX());
        assertEquals(40, decoded.config().altitude());
    }

    @Test
    void theWireCarriesTheVariableIdsAfterTheTextFields() {
        WiredLeaderboardCodec.Stored stored = WiredLeaderboardCodec.encode(sample());
        String wire = WiredLeaderboardCodec.toWire(stored);

        assertEquals(29, wire.split("\t", -1).length);
        WiredLeaderboardCodec.Stored read = WiredLeaderboardCodec.fromWire(stored.intParams(), wire);
        assertArrayEquals(stored.variableIds(), read.variableIds());
        assertEquals(stored.stringParam(), read.stringParam());
        assertNull(WiredLeaderboardCodec.fromWire(stored.intParams(), "a\tb"));
    }

    @Test
    void anOfficialSaveSendsTheVariableIdsApart() {
        WiredLeaderboardCodec.Stored stored = WiredLeaderboardCodec.encode(sample());

        WiredLeaderboardCodec.Stored official =
                WiredLeaderboardCodec.fromSave(stored.intParams(), stored.stringParam(), stored.variableIds());
        assertArrayEquals(stored.variableIds(), official.variableIds());
        assertEquals(stored.stringParam(), official.stringParam());

        // Ours carries them in the text; the packet's ids are then ignored.
        WiredLeaderboardCodec.Stored ours = WiredLeaderboardCodec.fromSave(
                stored.intParams(), WiredLeaderboardCodec.toWire(stored), new String[] {"1", "2"});
        assertArrayEquals(stored.variableIds(), ours.variableIds());
    }

    @Test
    void aRowStoredWithBareIdsReadsAsTheSameVariablesAsAReSavedOne() {
        // A box stored before the list ids: bare box ids in every slot, the global operand too.
        TreeMap<String, String> overrides = new TreeMap<>();
        overrides.put("812", "Coins");
        overrides.put("1200", "Wins");
        WiredLeaderboardConfig old = new WiredLeaderboardConfig(
                "Top players",
                "",
                WiredLeaderboardCodec.SORT_HIGHEST_VALUE,
                0,
                List.of(new WiredLeaderboardConfig.Column(WiredLeaderboardCodec.COLUMN_USERNAME, "Player", 3)),
                true,
                true,
                WiredLeaderboardCodec.OPERAND_GLOBAL_VARIABLE,
                "77",
                "5",
                "",
                overrides,
                1,
                1,
                0,
                0);
        WiredLeaderboardCodec.Stored oldRow = WiredLeaderboardCodec.encode(old);
        assertEquals("77", oldRow.variableIds()[0]);

        WiredLeaderboardCodec.Decoded loaded = WiredLeaderboardCodec.decode(oldRow, 0, 0);
        assertTrue(loaded.valid(), loaded.error());
        WiredLeaderboardConfig config = loaded.config();
        assertEquals("user:77", config.filterVariableId());
        assertEquals("room:5", config.operandVariableId());
        assertEquals(
                List.of("user:1200", "user:812"),
                List.copyOf(config.displayOverrides().keySet()));

        // Written back with the list ids (on the next save), which read as the very same config.
        WiredLeaderboardCodec.Stored rewritten = WiredLeaderboardCodec.encode(config);
        assertArrayEquals(
                new String[] {"user:77", "room:5", "user:1200", "user:812", "0", "0", "0", "0", "0", "0", "0", "0"},
                rewritten.variableIds());
        WiredLeaderboardConfig resaved =
                WiredLeaderboardCodec.decode(rewritten, 0, 0).config();
        List<WiredLeaderboardDescriptor.Choice> choices =
                List.of(new WiredLeaderboardDescriptor.Choice("user:812", "Coins"));
        assertEquals(
                WiredLeaderboardService.semanticKey(config, choices),
                WiredLeaderboardService.semanticKey(resaved, choices));

        // Tokens from an older client read the same too.
        String[] tokens = oldRow.variableIds().clone();
        tokens[0] = "custom:77";
        tokens[2] = "custom:1200";
        WiredLeaderboardConfig fromTokens = WiredLeaderboardCodec.decode(
                        new WiredLeaderboardCodec.Stored(oldRow.intParams(), tokens, oldRow.stringParam()), 0, 0)
                .config();
        assertEquals(config.filterVariableId(), fromTokens.filterVariableId());
        assertEquals(config.displayOverrides(), fromTokens.displayOverrides());
    }

    @Test
    void anIdOfAnotherTargetOrABuiltInIsKeptAsItIs() {
        assertEquals("furni:7", WiredLeaderboardCodec.userVariableId("furni:7"));
        assertEquals("-234", WiredLeaderboardCodec.userVariableId("-234"));
        assertEquals("0", WiredLeaderboardCodec.userVariableId("n"));
        assertEquals("0", WiredLeaderboardCodec.globalVariableId(""));
        assertEquals("user:7", WiredLeaderboardCodec.userVariableId(" 7 "));
    }

    @Test
    void anOfficialSaveMayLeaveOutTheEmptySlotsAtTheEnd() {
        WiredLeaderboardCodec.Stored stored = WiredLeaderboardCodec.encode(sample());
        WiredLeaderboardCodec.Stored trimmed = WiredLeaderboardCodec.fromSave(
                stored.intParams(), stored.stringParam(), new String[] {"user:77", "n", "user:1200", "user:812"});

        assertArrayEquals(stored.variableIds(), trimmed.variableIds());
        WiredLeaderboardCodec.Decoded decoded = WiredLeaderboardCodec.decode(trimmed, 0, 0);
        assertTrue(decoded.valid(), decoded.error());
    }

    @Test
    void anEmptyBoxReadsAsTheDefaults() {
        WiredLeaderboardCodec.Decoded decoded =
                WiredLeaderboardCodec.decode(new WiredLeaderboardCodec.Stored(new int[0], new String[0], ""), 3, 4);

        assertTrue(decoded.valid());
        assertEquals(2, decoded.config().columns().size());
        assertEquals("Username", decoded.config().columns().get(0).label());
        assertEquals(3, decoded.config().tileX());
        assertEquals(10, decoded.config().entriesPerPage());
    }

    @Test
    void theOfficialValidationErrorsAreReported() {
        WiredLeaderboardCodec.Stored stored = WiredLeaderboardCodec.encode(sample());

        String[] noTitle = stored.stringParam().split("\t", -1);
        noTitle[0] = "";
        assertEquals("title_required", decode(stored.intParams(), stored.variableIds(), noTitle));

        String[] shortLabel = stored.stringParam().split("\t", -1);
        shortLabel[3] = "S";
        assertEquals("column_name_length", decode(stored.intParams(), stored.variableIds(), shortLabel));

        String[] noFilter = stored.variableIds().clone();
        noFilter[0] = "0";
        assertEquals(
                "filter_required",
                decode(stored.intParams(), noFilter, stored.stringParam().split("\t", -1)));

        int[] global = stored.intParams().clone();
        global[8] = WiredLeaderboardCodec.OPERAND_GLOBAL_VARIABLE;
        String[] noLiteral = stored.stringParam().split("\t", -1);
        noLiteral[6] = "";
        assertEquals("global_operand_required", decode(global, stored.variableIds(), noLiteral));
    }

    @Test
    void malformedConfigsAreRefused() {
        WiredLeaderboardCodec.Stored stored = WiredLeaderboardCodec.encode(sample());

        int[] usernameNotFirst = stored.intParams().clone();
        usernameNotFirst[2] = WiredLeaderboardCodec.COLUMN_VALUE;
        usernameNotFirst[3] = WiredLeaderboardCodec.COLUMN_USERNAME;
        assertEquals(
                "invalid_configuration",
                decode(
                        usernameNotFirst,
                        stored.variableIds(),
                        stored.stringParam().split("\t", -1)));

        int[] offset = stored.intParams().clone();
        offset[12] = 33;
        assertEquals(
                "invalid_configuration",
                decode(offset, stored.variableIds(), stored.stringParam().split("\t", -1)));

        String[] literal = stored.stringParam().split("\t", -1);
        literal[6] = "007";
        assertEquals("invalid_configuration", decode(stored.intParams(), stored.variableIds(), literal));

        int[] sort = stored.intParams().clone();
        sort[0] = 6;
        assertEquals(
                "invalid_configuration",
                decode(sort, stored.variableIds(), stored.stringParam().split("\t", -1)));
    }

    @Test
    void suggestFormatsTechnicalNamesLikeTheClient() {
        assertEquals("Coins Won", WiredLeaderboardCodec.suggest("coins_won"));
        assertEquals("Best Lap Time", WiredLeaderboardCodec.suggest("BEST--lap_time"));
        assertEquals("Variable", WiredLeaderboardCodec.suggest("__"));
        assertEquals("Variable", WiredLeaderboardCodec.suggest(""));
    }

    @Test
    void sortModesMapToTheStoreOrder() {
        assertEquals(RoomUserVariableStore.Order.VALUE, WiredLeaderboardQuery.orderOf(0));
        assertTrue(WiredLeaderboardQuery.isDescending(0));
        assertFalse(WiredLeaderboardQuery.isDescending(1));
        assertEquals(RoomUserVariableStore.Order.CREATION_TIME, WiredLeaderboardQuery.orderOf(2));
        assertFalse(WiredLeaderboardQuery.isDescending(2));
        assertTrue(WiredLeaderboardQuery.isDescending(3));
        assertEquals(RoomUserVariableStore.Order.UPDATE_TIME, WiredLeaderboardQuery.orderOf(5));
        assertTrue(WiredLeaderboardQuery.isDescending(5));
        assertEquals(0, WiredLeaderboardCodec.userBoxIdOf("n"));
        assertEquals(812, WiredLeaderboardCodec.userBoxIdOf("812"));
        assertEquals(812, WiredLeaderboardCodec.userBoxIdOf("user:812"));
        assertEquals(812, WiredLeaderboardCodec.userBoxIdOf("custom:812"));
        assertEquals(0, WiredLeaderboardCodec.userBoxIdOf("room:812"));
        assertEquals(0, WiredLeaderboardCodec.userBoxIdOf("99999999999"));
        assertEquals(5, WiredLeaderboardCodec.globalBoxIdOf("room:5"));
        assertEquals(5, WiredLeaderboardCodec.globalBoxIdOf("5"));
        assertEquals(0, WiredLeaderboardCodec.globalBoxIdOf("user:5"));
    }

    @Test
    void subscriptionsNameOneOrTwoAscendingChunks() {
        assertTrue(WiredLeaderboardService.isValidChunkSet(new int[] {0}));
        assertTrue(WiredLeaderboardService.isValidChunkSet(new int[] {3, 4}));
        assertFalse(WiredLeaderboardService.isValidChunkSet(new int[0]));
        assertFalse(WiredLeaderboardService.isValidChunkSet(new int[] {4, 3}));
        assertFalse(WiredLeaderboardService.isValidChunkSet(new int[] {1, 1}));
        assertFalse(WiredLeaderboardService.isValidChunkSet(new int[] {-1}));
        assertFalse(WiredLeaderboardService.isValidChunkSet(new int[] {0, 1, 2}));
    }

    @Test
    void onlyWhatChangesTheRowsMovesTheRevision() {
        WiredLeaderboardConfig config = sample();
        List<WiredLeaderboardDescriptor.Choice> choices =
                List.of(new WiredLeaderboardDescriptor.Choice("user:812", "Coins"));
        String key = WiredLeaderboardService.semanticKey(config, choices);

        WiredLeaderboardConfig moved = config.withLocation(9, 9, 0, 0);
        assertEquals(key, WiredLeaderboardService.semanticKey(moved, choices));
        assertFalse(key.equals(WiredLeaderboardService.semanticKey(
                config, List.of(new WiredLeaderboardDescriptor.Choice("user:1200", "Wins")))));
    }

    private static String decode(int[] ints, String[] ids, String[] strings) {
        WiredLeaderboardCodec.Decoded decoded = WiredLeaderboardCodec.decode(
                new WiredLeaderboardCodec.Stored(ints, ids, String.join("\t", strings)), 0, 0);
        assertFalse(decoded.valid(), Arrays.toString(ints));
        return decoded.error();
    }
}
