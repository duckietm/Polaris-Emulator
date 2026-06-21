package com.eu.habbo.messages.incoming.housekeeping;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HousekeepingInputGuardContractTest {
    @Test
    void stringDrivenHousekeepingHandlersUseSharedLimits() throws Exception {
        Path base = Path.of("src/main/java/com/eu/habbo/messages/incoming/housekeeping");

        for (String handler : List.of(
                "HousekeepingBanUserEvent.java",
                "HousekeepingForceDisconnectUserEvent.java",
                "HousekeepingKickUserEvent.java",
                "HousekeepingMuteUserEvent.java",
                "HousekeepingTradeLockUserEvent.java",
                "HousekeepingSendHotelAlertEvent.java",
                "HousekeepingSearchRoomsEvent.java",
                "HousekeepingFindUserByNameEvent.java"
        )) {
            String source = Files.readString(base.resolve(handler));

            assertTrue(source.contains("HousekeepingInputGuard.normalize"),
                    handler + " must normalize client-provided strings before use");
            assertTrue(source.contains("HousekeepingInputGuard.isWithinLimit"),
                    handler + " must bound client-provided strings before expensive work or broadcast");
        }
    }

    @Test
    void auditedFreeTextIsSanitizedBeforePersistence() throws Exception {
        Path base = Path.of("src/main/java/com/eu/habbo/messages/incoming/housekeeping");

        for (String handler : List.of(
                "HousekeepingBanUserEvent.java",
                "HousekeepingForceDisconnectUserEvent.java",
                "HousekeepingKickUserEvent.java",
                "HousekeepingMuteUserEvent.java",
                "HousekeepingTradeLockUserEvent.java",
                "HousekeepingSendHotelAlertEvent.java"
        )) {
            String source = Files.readString(base.resolve(handler));

            assertTrue(source.contains("HousekeepingInputGuard.auditValue"),
                    handler + " must collapse control whitespace before writing free text to audit detail");
        }
    }
}
