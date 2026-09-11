package com.eu.habbo.messages.incoming.catalog;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RequestTargetOfferEventTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/" + path));
    }

    @Test
    void theHeaderWasAlreadyKnownAndIsFinallyRegistered() throws Exception {
        assertTrue(source("com/eu/habbo/messages/incoming/Incoming.java").contains("RequestTargetOfferEvent = 2487"));
        assertTrue(source("com/eu/habbo/messages/PacketManager.java")
                .contains("Incoming.RequestTargetOfferEvent, RequestTargetOfferEvent.class"));
    }

    @Test
    void itAnswersWithTheRunningOfferOnlyWhenThereIsOne() throws Exception {
        String handler = source("com/eu/habbo/messages/incoming/catalog/RequestTargetOfferEvent.java");

        assertTrue(handler.contains("TargetOffer.ACTIVE_TARGET_OFFER_ID <= 0"));
        assertTrue(handler.contains("getTargetOffer(TargetOffer.ACTIVE_TARGET_OFFER_ID)"));
        assertTrue(handler.contains("new TargetedOfferComposer(this.client.getHabbo(), offer)"));
    }
}
