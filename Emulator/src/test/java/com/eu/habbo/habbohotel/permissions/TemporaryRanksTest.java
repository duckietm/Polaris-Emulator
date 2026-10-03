package com.eu.habbo.habbohotel.permissions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TemporaryRanksTest {
    @Test
    void onlyItsOwnRankChangesKeepTheTimer() {
        assertTrue(TemporaryRanks.keepsTimer("temporary 7d"));
        assertTrue(TemporaryRanks.keepsTimer(TemporaryRanks.VIA_ENDED));
        assertFalse(TemporaryRanks.keepsTimer("give_rank"));
        assertFalse(TemporaryRanks.keepsTimer("housekeeping"));
        assertFalse(TemporaryRanks.keepsTimer(null));
    }

    @Test
    void anyOtherRankChangeEndsTheTimer() throws Exception {
        String manager = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/users/HabboManager.java"));
        int setRank = manager.indexOf(
                "public void setRank(int userId, int rankId, int actorId, String actorName, String via)");
        int keeps = manager.indexOf("TemporaryRanks.keepsTimer(via)", setRank);
        int clear = manager.indexOf("TemporaryRanks.clear(userId)", keeps);

        assertTrue(setRank > -1 && keeps > setRank && clear > keeps);
    }

    @Test
    void expiredOverridesAreCheckedWithoutGaps() {
        int now = 1_000_000;

        assertTrue(TemporaryRanks.overrideCheckStart(0, now) == now - 120);
        assertTrue(TemporaryRanks.overrideCheckStart(now - 60, now) == now - 60);
    }

    @Test
    void anExpiredOverrideRefreshesTheClient() throws Exception {
        String source =
                Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/permissions/TemporaryRanks.java"));
        int method = source.indexOf("void refreshExpiredOverrides(int now)");

        assertTrue(source.indexOf("this.refreshExpiredOverrides(now)") > -1);
        assertTrue(source.indexOf(".invalidate(userId)", method) > method);
        assertTrue(source.indexOf("new UserPermissionsComposer(online)", method) > method);
    }
}
