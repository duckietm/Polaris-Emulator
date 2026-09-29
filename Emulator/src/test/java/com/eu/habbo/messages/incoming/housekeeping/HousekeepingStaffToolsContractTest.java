package com.eu.habbo.messages.incoming.housekeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class HousekeepingStaffToolsContractTest {
    private static final Path BASE = Path.of("src/main/java/com/eu/habbo/messages/incoming/housekeeping");

    @Test
    void aPermissionChangeNeedsTheCommandPermissionAndARankTheOperatorMayActOn() throws Exception {
        String source = Files.readString(BASE.resolve("HousekeepingSetPermissionEvent.java"));

        assertEquals("cmd_update_permissions", HousekeepingSetPermissionEvent.PERMISSION);
        assertTrue(source.contains("hasPermission(PERMISSION)"));
        assertTrue(
                source.contains("HousekeepingTargetRankGuard.canTargetRank(this.client.getHabbo(), rankId)"),
                "nobody widens a rank the rank policy keeps them from acting on");
        assertTrue(source.contains("value > maxValue.get()"), "a value above the permission's max is refused");
        assertTrue(source.contains("new UpdatePermissionsCommand()"), "a change reloads the permissions");
    }

    @Test
    void theRankColumnIsOnlyEverAValidatedRankId() throws Exception {
        String source = Files.readString(BASE.resolve("HousekeepingSetPermissionEvent.java"));

        assertEquals("rank_7", HousekeepingPermissionMatrix.rankColumn(7));
        assertTrue(
                source.indexOf("HousekeepingPermissionMatrix.rankExists(rankId)")
                        < source.indexOf("HousekeepingPermissionMatrix.set("),
                "the rank must exist before it becomes a column name");
    }

    @Test
    void onlyTheAuthorDeletesANote() {
        assertEquals(
                List.of("add", "delete"), List.of(HousekeepingUserNoteEvent.ADD, HousekeepingUserNoteEvent.DELETE));
        assertTrue(HousekeepingUserNotes.DELETE_SQL.contains("AND author_id = ?"));
        assertEquals(500, HousekeepingUserNotes.MAX_NOTE_LENGTH);
    }
}
