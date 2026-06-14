package com.eu.habbo.messages.rcon;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SendGiftContractTest {
    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/messages/rcon/SendGift.java"));
    }

    @Test
    void validatesGiftTargetsAndItemsBeforeCreatingInventoryRows() throws Exception {
        String source = source();

        assertTrue(source.contains("json.user_id <= 0"),
                "RCON gifts must reject invalid target users");
        assertTrue(source.contains("json.itemid <= 0"),
                "RCON gifts must reject invalid item ids");
        assertTrue(source.contains("baseItem.allowGift()"),
                "RCON gifts must respect the item giftability flag");
        assertTrue(source.contains("HabboManager.getOfflineHabboInfo(json.user_id)"),
                "RCON gifts must resolve offline users through HabboManager");
        assertTrue(source.contains("HABBO_NOT_FOUND"),
                "RCON gifts must report missing users with the RCON missing-user status");
    }

    @Test
    void sanitizesGiftMessageAndHandlesMissingGiftConfiguration() throws Exception {
        String source = source();

        assertTrue(source.contains("sanitizeGiftMessage(json.message)"),
                "RCON gift extraData must use a sanitized message");
        assertTrue(source.contains("replace('\\t', ' ').replace('\\r', ' ').replace('\\n', ' ')"),
                "RCON gift messages must not inject gift extraData delimiters");
        assertTrue(source.contains("hotel.gifts.length.max"),
                "RCON gift messages must respect the configured gift length limit");
        assertTrue(source.contains("giftFurnis.size()"),
                "RCON gift creation must guard against empty gift wrapper configuration");
        assertTrue(source.contains("createGift(habboInfo.getUsername()"),
                "RCON gifts must create the wrapper for the canonical target username");
    }
}
