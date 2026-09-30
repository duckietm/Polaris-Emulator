package com.eu.habbo.habbohotel.permissions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class PermissionKeyFixesContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/eu/habbo");

    private static String migrations() throws Exception {
        try (Stream<Path> files = Files.list(Path.of("src/main/resources/db/migration"))) {
            return files.filter(path -> path.toString().endsWith(".sql"))
                    .map(path -> {
                        try {
                            return Files.readString(path);
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .collect(Collectors.joining("\n"));
        }
    }

    private static String source(String path) throws Exception {
        return Files.readString(MAIN.resolve(path));
    }

    @Test
    void keysTheCodeChecksAreDefinedByAMigration() throws Exception {
        String sql = migrations();

        for (String key : new String[] {
            "acc_nomute",
            "cmd_roomfx",
            "cmd_calendar_staff",
            "acc_unload_any_room",
            "acc_hotelview_edit",
            "cmd_duckets",
            "cmd_super_ban",
            "acc_perk_mouse_zoom",
            "acc_perk_citizen"
        }) {
            assertTrue(sql.contains("'" + key + "'"), key + " is not defined in any migration");
        }
    }

    @Test
    void theMuteCheckAndTheRconDenyListUseRealKeys() throws Exception {
        assertFalse(source("habbohotel/users/Habbo.java").contains("\"acc_no_mute\""));

        String rcon = source("messages/rcon/ExecuteCommand.java");
        assertFalse(rcon.contains("\"cmd_pixels\""));
        assertFalse(rcon.contains("\"cmd_superban\""));
    }

    @Test
    void staffChecksReadPermissionsNotRankNumbers() throws Exception {
        for (String path : new String[] {
            "habbohotel/commands/UnloadRoomCommand.java",
            "habbohotel/hotelview/HotelViewManager.java",
            "messages/incoming/hotelview/HotelViewLandingRequestEvent.java",
            "messages/incoming/hotelview/HotelViewLandingSaveEvent.java",
            "messages/incoming/hotelview/HotelViewLandingSaveSceneEvent.java",
            "messages/incoming/hotelview/HotelViewLandingResetVotesEvent.java"
        }) {
            assertFalse(source(path).matches("(?s).*getRank\\(\\)\\.getId\\(\\) *[<>]=? *\\d.*"), path);
        }
    }

    @Test
    void theRankIsWrittenWhereItChangesNotOnEverySave() throws Exception {
        String info = source("habbohotel/users/HabboInfo.java");
        int save = info.indexOf("UPDATE users SET motto");
        assertTrue(save > -1);
        assertFalse(info.substring(save, info.indexOf("WHERE id = ?", save)).contains("rank"));

        String manager = source("habbohotel/users/HabboManager.java");
        int setRank = manager.indexOf("public void setRank(int userId, int rankId)");
        assertTrue(manager.indexOf("UPDATE users SET `rank` = ?", setRank) > setRank);

        assertTrue(source("messages/incoming/housekeeping/HousekeepingSetUserRankEvent.java")
                .contains("habboManager.setRank("));
    }
}
