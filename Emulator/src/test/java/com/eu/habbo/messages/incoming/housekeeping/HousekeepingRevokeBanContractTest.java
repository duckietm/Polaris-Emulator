package com.eu.habbo.messages.incoming.housekeeping;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HousekeepingRevokeBanContractTest {
    private static final Path HANDLER =
            Path.of("src/main/java/com/eu/habbo/messages/incoming/housekeeping/HousekeepingRevokeBanEvent.java");
    private static final Path SOURCE = Path.of("src/main/java/com/eu/habbo/habbohotel/modtool/ModToolBanList.java");
    private static final Path LIST =
            Path.of("src/main/java/com/eu/habbo/messages/incoming/housekeeping/HousekeepingRequestListEvent.java");

    @Test
    void aRevokeChecksTheOwnersRankBeforeEndingTheBan() throws Exception {
        String source = Files.readString(HANDLER);

        int guard = source.indexOf("HousekeepingTargetRankGuard.canTargetUser(");
        int revoke = source.indexOf("ModToolBanList.revoke(");

        assertTrue(source.contains("if (!this.allowed())"), "the panel permission comes first");
        assertTrue(guard > 0 && revoke > guard, "the rank guard must run before the ban is ended");
    }

    @Test
    void onlyTheChosenActiveBanIsEnded() throws Exception {
        String source = Files.readString(SOURCE);

        assertTrue(
                source.contains("UPDATE bans SET ban_expire = ? WHERE id = ? AND ban_expire > ?"),
                "a revoke ends one active ban by id and leaves the others");
    }

    @Test
    void theBansListCarriesTheBanId() throws Exception {
        assertTrue(Files.readString(LIST).contains("\"ban_id\""), "the client needs the ban id to revoke one ban");
    }
}
