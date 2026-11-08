package polaris.hotel.modtool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.database.SqlQueries;

/**
 * Every caution that bumps {@code users_settings.cfh_warnings} also leaves its detail in {@code user_cautions},
 * so the mod tool can list what the counter counts.
 */
class CautionRecordTest {
    private static final Path HANDLERS = Path.of("src/main/java/polaris/messages/incoming/modtool");

    @Test
    void aCautionIsInsertedWithItsStaffTypeReasonAndLength() {
        List<Object[]> parameters = new ArrayList<>();
        List<String> sql = new ArrayList<>();

        try (MockedStatic<SqlQueries> queries = mockStatic(SqlQueries.class)) {
            queries.when(() -> SqlQueries.update(anyString(), any(Object[].class)))
                    .thenAnswer(invocation -> {
                        Object[] arguments = invocation.getArguments();
                        sql.add((String) arguments[0]);
                        parameters.add(Arrays.copyOfRange(arguments, 1, arguments.length));
                        return 1;
                    });

            ModToolManager.recordCaution(7, 3, "MUTE", "x".repeat(300), 2);
            ModToolManager.recordCaution(7, 3, null, null, -4);
        }

        assertEquals(2, sql.size());
        assertTrue(sql.get(0)
                .startsWith("INSERT INTO user_cautions (user_id, staff_id, type, reason, length_hours, created_at)"));
        assertTrue(sql.get(0).contains("UNIX_TIMESTAMP()"));
        assertEquals(List.of(7, 3, "MUTE", "x".repeat(255), 2), List.of(parameters.get(0)));
        assertEquals(List.of(7, 3, "", "", 0), List.of(parameters.get(1)));
    }

    @Test
    void aFailingInsertLeavesTheCautionStanding() {
        try (MockedStatic<SqlQueries> queries = mockStatic(SqlQueries.class)) {
            queries.when(() -> SqlQueries.update(anyString(), any(Object[].class)))
                    .thenThrow(new SqlQueries.DataAccessException("down", null));

            ModToolManager.recordCaution(7, 3, "ALERT", "why", 0);
        }
    }

    @Test
    void theLengthIsTheOneTheAppliedLevelRuns() {
        assertEquals(0, ModToolSanctions.appliedHours(null, 5));
        assertEquals(0, ModToolSanctions.appliedHours(new ModToolSanctionLevelItem(1, "ALERT", 9, 0), 5));
        assertEquals(24, ModToolSanctions.appliedHours(new ModToolSanctionLevelItem(2, "BAN", 24, 0), 5));
        assertEquals(5, ModToolSanctions.appliedHours(new ModToolSanctionLevelItem(3, "MUTE", 9, 0), 5));
        assertEquals(1, ModToolSanctions.appliedHours(new ModToolSanctionLevelItem(3, "MUTE", 9, 0), 0));
    }

    @Test
    void everyWarningCounterBumpRecordsTheCaution() throws Exception {
        int sites = 0;

        try (var files = Files.list(HANDLERS)) {
            for (Path file : files.toList()) {
                String source = Files.readString(file).replace("\r\n", "\n");
                String bump = "ModToolManager.bumpUserSettingCounter(userId, \"cfh_warnings\");\n";
                int at = source.indexOf(bump);

                while (at >= 0) {
                    sites++;
                    String next = source.substring(at + bump.length()).stripLeading();
                    assertTrue(
                            next.startsWith("ModToolManager.recordCaution("),
                            file.getFileName() + " bumps cfh_warnings without recording the caution");
                    assertTrue(
                            next.substring(0, next.indexOf(';'))
                                    .contains("this.client.getUser().getUserInfo().getId()"),
                            file.getFileName() + " records the caution without the acting staff member");
                    at = source.indexOf(bump, at + bump.length());
                }
            }
        }

        assertEquals(4, sites, "the alert, the mute and the two default sanctions bump the warning counter");
    }
}
