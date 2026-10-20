package com.eu.habbo.habbohotel.wired.share;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableLeaderboard;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

class WiredSetupShareTest {
    private static WiredSetupDocument.Box box(String type) {
        WiredSetupDocument.Box box = new WiredSetupDocument.Box();
        box.type = type;
        box.holder = "ACTION";
        box.ints = new int[] {1, 2};
        box.text = "custom:#0";
        box.picks = List.of("b0", "f0");
        return box;
    }

    private static String gzipCode(String json) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(json.getBytes(StandardCharsets.UTF_8));
        }
        return WiredSetupDocument.CODE_PREFIX + Base64.getEncoder().encodeToString(out.toByteArray());
    }

    @Test
    void aCodeReadsBackAsTheSameDocument() throws Exception {
        WiredSetupDocument document = new WiredSetupDocument();
        document.anchorX = 4;
        document.anchorY = 7;
        document.boxes.add(box("wf_act_give_score"));
        WiredSetupDocument.Furni furni = new WiredSetupDocument.Furni();
        furni.type = "chair";
        furni.dx = 1;
        furni.n = 2;
        document.furni.add(furni);

        String code = document.encode();
        assertTrue(code.startsWith("OWS1:"));

        WiredSetupDocument read = WiredSetupDocument.decode(code);
        assertEquals(4, read.anchorX);
        assertEquals(7, read.anchorY);
        assertEquals("wf_act_give_score", read.boxes.get(0).type);
        assertArrayEquals(new int[] {1, 2}, read.boxes.get(0).ints);
        assertEquals(List.of("b0", "f0"), read.boxes.get(0).picks);
        assertEquals("chair", read.furni.get(0).type);
        assertEquals(2, read.furni.get(0).n);
    }

    @Test
    void codesThatAreNotASupportedSetupAreRefused() throws Exception {
        assertStatus(WiredSetupShare.STATUS_INVALID_CODE, null);
        assertStatus(WiredSetupShare.STATUS_INVALID_CODE, "hello");
        assertStatus(WiredSetupShare.STATUS_INVALID_CODE, "OWS1:@@@not base64");
        assertStatus(
                WiredSetupShare.STATUS_INVALID_CODE,
                "OWS1:" + Base64.getEncoder().encodeToString(new byte[] {1, 2, 3}));
        assertStatus(WiredSetupShare.STATUS_INVALID_CODE, gzipCode("[1,2,3]"));
        assertStatus(WiredSetupShare.STATUS_INVALID_CODE, gzipCode("{\"format\":\"other\",\"version\":1}"));
        assertStatus(
                WiredSetupShare.STATUS_UNSUPPORTED_VERSION,
                gzipCode("{\"format\":\"octane.wired.setup\",\"version\":2}"));
        assertStatus(WiredSetupShare.STATUS_TOO_LARGE, "OWS1:" + "A".repeat(WiredSetupDocument.MAX_CODE_LENGTH));
    }

    @Test
    void aSmallCodeCannotInflateIntoALargeDocument() throws Exception {
        String bomb = gzipCode("{\"format\":\"octane.wired.setup\",\"version\":1,\"pad\":\""
                + " ".repeat(WiredSetupDocument.MAX_JSON_BYTES + 10) + "\"}");
        assertTrue(bomb.length() < WiredSetupDocument.MAX_CODE_LENGTH);
        assertStatus(WiredSetupShare.STATUS_TOO_LARGE, bomb);
    }

    @Test
    void aSetupWithTooManyBoxesIsRefused() throws Exception {
        WiredSetupDocument document = new WiredSetupDocument();
        for (int i = 0; i <= WiredSetupDocument.MAX_BOXES; i++) document.boxes.add(box("wf_act_give_score"));
        assertStatus(WiredSetupShare.STATUS_TOO_MANY_BOXES, document.encode());
    }

    @Test
    void entriesOutsideTheLimitsAreRefused() {
        assertNull(WiredSetupImporter.validate(box("wf_act_give_score")));

        WiredSetupDocument.Box farAway = box("wf_act_give_score");
        farAway.dx = WiredSetupDocument.MAX_OFFSET + 1;
        assertNotNull(WiredSetupImporter.validate(farAway));

        WiredSetupDocument.Box unknownHolder = box("wf_act_give_score");
        unknownHolder.holder = "SOMETHING";
        assertNotNull(WiredSetupImporter.validate(unknownHolder));

        WiredSetupDocument.Box tooManyInts = box("wf_act_give_score");
        tooManyInts.ints = new int[101];
        assertNotNull(WiredSetupImporter.validate(tooManyInts));

        WiredSetupDocument.Box longText = box("wf_act_give_score");
        longText.text = "x".repeat(40_000);
        assertNotNull(WiredSetupImporter.validate(longText));

        WiredSetupDocument.Box tooManyVariables = box("wf_act_give_score");
        tooManyVariables.variables = new ArrayList<>();
        for (int i = 0; i < 21; i++) tooManyVariables.variables.add("@x");
        assertNotNull(WiredSetupImporter.validate(tooManyVariables));

        assertNotNull(WiredSetupImporter.validate(null));
        WiredSetupDocument.Box noType = box(" ");
        assertNotNull(WiredSetupImporter.validate(noType));
    }

    @Test
    void picksAreReadOnlyInTheirOwnShape() {
        assertEquals(3, WiredSetupDocument.boxReference("b3"));
        assertEquals(12, WiredSetupDocument.furniReference("f12"));
        assertEquals(-1, WiredSetupDocument.boxReference("f3"));
        assertEquals(-1, WiredSetupDocument.boxReference("b-1"));
        assertEquals(-1, WiredSetupDocument.boxReference("b"));
        assertEquals(-1, WiredSetupDocument.boxReference("b9999999"));
        assertEquals(-1, WiredSetupDocument.furniReference(null));
    }

    @Test
    void exportTurnsItemIdsIntoBoxReferences() {
        WiredSetupExporter exporter = new WiredSetupExporter(mock(Room.class));
        exporter.boxIndexes.put(501, 0);
        exporter.boxIndexes.put(777, 3);

        assertEquals("a\tcustom:#0\tcustom:0\tcustom:#3", exporter.exportText("a\tcustom:501\tcustom:9\tcustom:777"));
        // Slot by slot: an id of a box that is not exported, or an empty slot, is "none".
        assertEquals(
                List.of("#0", "custom:#3", "n", "@internal", "n"),
                exporter.exportVariables(new String[] {"501", "custom:777", "12", "@internal", ""}));
        // The variable list's ids of exported boxes keep their target.
        assertEquals(
                List.of("user:#0", "room:#3", "-125", "n", "user:41"),
                exporter.exportVariables(new String[] {"user:501", "room:777", "-125", "n", "user:41"}));
        // An echo's built-in source names its target; a reference names the shared variable of another room.
        assertEquals(
                List.of("ctx:@chat_type", "furni:@position_x", "user:9"),
                exporter.exportVariables(new String[] {"ctx:@chat_type", "furni:@position_x", "user:9"}));
    }

    @Test
    void echoAndReferenceSourcesSurviveTheImport() {
        GameClient client = mock(GameClient.class);
        when(client.getHabbo()).thenReturn(mock(Habbo.class));
        WiredSetupImporter importer = new WiredSetupImporter(mock(Room.class), client, new WiredSetupDocument());
        InteractionWired echoed = mock(InteractionWired.class);
        when(echoed.getId()).thenReturn(4242);
        importer.resolved.put(2, echoed);

        assertArrayEquals(
                new String[] {"4242", "ctx:@chat_type", "room:11"},
                importer.remapVariables(List.of("#2", "ctx:@chat_type", "room:11")));
    }

    @Test
    void importMapsReferencesOnlyToBoxesItMatched() {
        GameClient client = mock(GameClient.class);
        when(client.getHabbo()).thenReturn(mock(Habbo.class));
        WiredSetupImporter importer = new WiredSetupImporter(mock(Room.class), client, new WiredSetupDocument());
        InteractionWired matched = mock(InteractionWired.class);
        when(matched.getId()).thenReturn(9001);
        importer.resolved.put(0, matched);

        assertEquals("custom:9001|custom:0", importer.remapText("custom:#0|custom:#5"));
        assertArrayEquals(
                new String[] {"9001", "custom:9001", "n", "n", "n", "@built_in"},
                importer.remapVariables(List.of("#0", "custom:#0", "#4", "123", "custom:55", "@built_in")));
        assertArrayEquals(
                new String[] {"n", "user:9001", "-125"},
                importer.remapVariables(List.of("furni:#4", "user:#0", "-125")));
        // Item ids never come from a code: only box references and furni found in this room.
        assertArrayEquals(new int[] {9001}, importer.remapPicks(List.of("b0", "b1", "b0", "123", "f0"), 0, 0));
    }

    @Test
    void aLeaderboardKeepsItsVariablesThroughAShareCode() throws Exception {
        // Sort highest, username column, filter on, two display names; the filter variable is not exported.
        int[] ints = {0, 1, 0, -1, -1, -1, 1, 0, 0, 2, 1, 1, 0, 0, 3, 0, 0, 0, 0};
        String text =
                String.join("\t", "Top", "", "Player", "", "", "", "", "Gold", "Wins", "", "", "", "", "", "", "", "");
        WiredExtraVariableLeaderboard board = new WiredExtraVariableLeaderboard(50, 1, mock(Item.class), "", 0, 0);
        WiredSettings saved = new WiredSettings(ints, text, new int[0], 0);
        saved.setVariableIds(new String[] {"user:77", "n", "user:1200", "user:900"});
        assertTrue(board.saveData(saved, null));

        WiredSetupExporter exporter = new WiredSetupExporter(mock(Room.class));
        exporter.boxIndexes.put(900, 0);
        exporter.boxIndexes.put(1200, 1);
        List<String> exported = exporter.exportVariables(board.officialVariableIds());
        assertEquals(List.of("user:77", "n", "user:#1", "user:#0", "n", "n", "n", "n", "n", "n", "n", "n"), exported);

        GameClient client = mock(GameClient.class);
        when(client.getHabbo()).thenReturn(mock(Habbo.class));
        WiredSetupImporter importer = new WiredSetupImporter(mock(Room.class), client, new WiredSetupDocument());
        InteractionWired coins = mock(InteractionWired.class);
        when(coins.getId()).thenReturn(4900);
        InteractionWired wins = mock(InteractionWired.class);
        when(wins.getId()).thenReturn(5200);
        importer.resolved.put(0, coins);
        importer.resolved.put(1, wins);

        WiredExtraVariableLeaderboard copy = new WiredExtraVariableLeaderboard(51, 1, mock(Item.class), "", 0, 0);
        WiredSettings imported = new WiredSettings(ints, text, new int[0], 0);
        imported.setVariableIds(importer.remapVariables(exported));
        assertTrue(copy.saveData(imported, null));
        assertArrayEquals(
                new String[] {"user:77", "0", "user:4900", "user:5200", "0", "0", "0", "0", "0", "0", "0", "0"},
                copy.officialVariableIds());
        assertEquals("Gold", copy.getConfig().displayOverrides().get("user:5200"));
        assertEquals("Wins", copy.getConfig().displayOverrides().get("user:4900"));
    }

    private static void assertStatus(int status, String code) {
        WiredSetupDocument.InvalidCodeException failure =
                assertThrows(WiredSetupDocument.InvalidCodeException.class, () -> WiredSetupDocument.decode(code));
        assertEquals(status, failure.status, failure.getMessage());
    }
}
