package com.eu.habbo.messages.incoming.housekeeping;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HousekeepingAuditCoverageContractTest {
    private static final List<String> SENSITIVE_HANDLERS = List.of(
            "HousekeepingBanUserEvent.java",
            "HousekeepingMuteUserEvent.java",
            "HousekeepingResetUserPasswordEvent.java",
            "HousekeepingSetHcSubscriptionEvent.java",
            "HousekeepingTradeLockUserEvent.java",
            "HousekeepingGrantItemEvent.java",
            "HousekeepingTransferRoomOwnershipEvent.java",
            "HousekeepingSendHotelAlertEvent.java"
    );

    @Test
    void sensitiveHousekeepingActionsWriteAuditEntries() throws Exception {
        Path base = Path.of("src/main/java/com/eu/habbo/messages/incoming/housekeeping");

        for (String handler : SENSITIVE_HANDLERS) {
            String source = Files.readString(base.resolve(handler));
            assertTrue(source.contains("HousekeepingAuditLog.log"),
                    handler + " must append a housekeeping audit log entry after successful privileged actions");
        }
    }
}
