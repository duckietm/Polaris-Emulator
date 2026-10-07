package com.eu.habbo.messages.incoming.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GiftHideSenderContractTest {

    @Test
    void onlyTheKeyMayHideTheSender() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/eu/habbo/messages/incoming/catalog/CatalogBuyItemAsGiftEvent.java"));

        int read = source.indexOf("boolean showName = this.packet.readBoolean();");
        int gate = source.indexOf(
                "if (!showName && !this.client.getHabbo().hasPermission(GIFT_HIDE_SENDER)) showName = true;");
        int firstUse = source.indexOf("showName ?", read);

        assertEquals("acc_gift_hide_sender", CatalogBuyItemAsGiftEvent.GIFT_HIDE_SENDER);
        assertTrue(read > -1 && gate > read, "The flag must be checked right after it is read");
        assertTrue(gate < firstUse, "No gift may be built before the check");
    }
}
