package com.eu.habbo.messages.incoming.hotelview;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BadgeRequestContractTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/" + path));
    }

    @Test
    void theCodeIsKeptToWhatAHotelWouldConfigure() {
        assertTrue(BadgeRequestGuard.isValidCode("summer_2026"));
        assertTrue(BadgeRequestGuard.isValidCode("ACH-1"));
        assertFalse(BadgeRequestGuard.isValidCode(""));
        assertFalse(BadgeRequestGuard.isValidCode(null));
        assertFalse(BadgeRequestGuard.isValidCode("a.b"));
        assertFalse(BadgeRequestGuard.isValidCode("../secret"));
        assertFalse(BadgeRequestGuard.isValidCode("x".repeat(BadgeRequestGuard.MAX_LENGTH + 1)));
    }

    @Test
    void bothHeadersMatchTheRendererAndAreRegistered() throws Exception {
        String incoming = source("com/eu/habbo/messages/incoming/Incoming.java");
        String registry = source("com/eu/habbo/messages/PacketManager.java");

        assertTrue(incoming.contains("HotelViewClaimBadgeEvent = 3077"));
        assertTrue(incoming.contains("GetIsBadgeRequestFulfilledEvent = 1364"));
        assertTrue(registry.contains("Incoming.HotelViewClaimBadgeEvent, HotelViewClaimBadgeEvent.class"));
        assertTrue(
                registry.contains("Incoming.GetIsBadgeRequestFulfilledEvent, GetIsBadgeRequestFulfilledEvent.class"));
    }

    @Test
    void aClosedOfferGrantsNothingAndSaysSo() throws Exception {
        String claim = source("com/eu/habbo/messages/incoming/hotelview/HotelViewClaimBadgeEvent.java");

        assertTrue(claim.contains("badgeCode.isEmpty() || !Emulator.getConfig().getBoolean(key + \".enabled\")"));
        assertTrue(claim.indexOf("new HotelViewBadgeButtonConfigComposer(requestCode, false)")
                < claim.indexOf("BadgesComponent.createBadge(badgeCode, habbo)"));
    }

    @Test
    void aBadgeIsNeverGrantedTwiceAndTheAnswerCarriesTheRequestCode() throws Exception {
        String claim = source("com/eu/habbo/messages/incoming/hotelview/HotelViewClaimBadgeEvent.java");
        String query = source("com/eu/habbo/messages/incoming/hotelview/GetIsBadgeRequestFulfilledEvent.java");

        assertTrue(claim.contains("!habbo.getInventory().getBadgesComponent().hasBadge(badgeCode)"));
        assertTrue(claim.contains("new HotelViewBadgeButtonConfigComposer(requestCode, true)"));
        assertTrue(query.contains("new HotelViewBadgeButtonConfigComposer(requestCode, claimed)"));
    }
}
