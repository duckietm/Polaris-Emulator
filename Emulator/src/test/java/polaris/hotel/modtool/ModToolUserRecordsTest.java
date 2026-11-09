package polaris.hotel.modtool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.database.SqlQueries;

/**
 * The lists behind the counts of the mod tool's user info: which query each kind runs, how its rows map onto the
 * wire entry, and that the linked accounts follow the rule of the count they open from.
 */
class ModToolUserRecordsTest {
    private static final int USER = 42;

    @Test
    void cautionsAreReadNewestFirstWithTheStaffNameAndTheUndetailedRest() {
        List<String> sql = new ArrayList<>();
        ResultSet row = row(Map.of(
                "created_at", 1_700_000_000,
                "username", "bob",
                "staff_name", "mod",
                "type", "MUTE",
                "reason", "spam",
                "length_hours", 2));

        try (MockedStatic<SqlQueries> queries = mockStatic(SqlQueries.class)) {
            queries.when(() -> SqlQueries.query(anyString(), any(), eq(USER), eq(ModToolUserRecords.LIMIT)))
                    .thenAnswer(invocation -> {
                        sql.add(invocation.getArgument(0));
                        return List.of(invocation
                                .<SqlQueries.RowMapper<?>>getArgument(1)
                                .map(row));
                    });
            queries.when(() -> SqlQueries.queryOne(eq(ModToolUserRecords.CAUTION_COUNTER_SQL), any(), eq(USER)))
                    .thenReturn(Optional.of(5));
            queries.when(() -> SqlQueries.queryOne(eq(ModToolUserRecords.CAUTION_ROWS_SQL), any(), eq(USER)))
                    .thenReturn(Optional.of(1));

            ModToolUserRecords.Records records = ModToolUserRecords.load(USER, ModToolUserRecords.CAUTIONS);

            assertEquals(4, records.undetailed());
            assertEquals(
                    List.of(new ModToolUserRecords.Entry(
                            1_700_000_000, "bob", "mod", "MUTE", "spam", 1_700_000_000 + 2 * 3600)),
                    records.entries());
        }

        assertEquals(List.of(ModToolUserRecords.CAUTIONS_SQL), sql);
        assertTrue(sql.get(0).contains("FROM user_cautions c"));
        assertTrue(sql.get(0).contains("LEFT JOIN users staff ON staff.id = c.staff_id"));
        assertTrue(sql.get(0).contains("WHERE c.user_id = ? ORDER BY c.id DESC LIMIT ?"));
    }

    @Test
    void theUndetailedCountIsNeverNegative() {
        assertEquals(0, ModToolUserRecords.undetailed(1, 3));
        assertEquals(2, ModToolUserRecords.undetailed(2, 0));
    }

    @Test
    void ownBansMapTheBanRowAndAPermanentBanHasNoExpiry() {
        ResultSet row = row(Map.of(
                "timestamp", 1_600_000_000,
                "username", "bob",
                "staff_name", "",
                "type", "machine",
                "ban_reason", "cheating",
                "ban_expire", Integer.MAX_VALUE));
        List<String> sql = new ArrayList<>();

        try (MockedStatic<SqlQueries> queries = mockStatic(SqlQueries.class)) {
            queries.when(() -> SqlQueries.query(anyString(), any(), eq(USER), eq(ModToolUserRecords.LIMIT)))
                    .thenAnswer(invocation -> {
                        sql.add(invocation.getArgument(0));
                        return List.of(invocation
                                .<SqlQueries.RowMapper<?>>getArgument(1)
                                .map(row));
                    });

            ModToolUserRecords.Records records = ModToolUserRecords.load(USER, ModToolUserRecords.BANS);

            assertEquals(0, records.undetailed());
            assertEquals(
                    List.of(new ModToolUserRecords.Entry(1_600_000_000, "bob", "", "machine", "cheating", 0)),
                    records.entries());
        }

        assertEquals(List.of(ModToolUserRecords.BANS_SQL), sql);
        assertTrue(sql.get(0).contains("WHERE bans.user_id = ? ORDER BY bans.timestamp DESC"));
        assertTrue(sql.get(0).contains("LEFT JOIN users staff ON staff.id = bans.user_staff_id"));
    }

    @Test
    void linkedAccountsFollowTheRuleOfTheirCount() throws Exception {
        String sql = ModToolUserRecords.LINKED_BANS_SQL;

        assertTrue(sql.contains("JOIN bans ON bans.machine_id = me.machine_id"));
        assertTrue(sql.contains("me.machine_id <> ''"), "an empty machine id links nobody, as the count says 0");
        assertTrue(sql.contains("bans.user_id <> me.id"), "the user's own bans are another list");
        assertTrue(sql.contains("SELECT MAX(newest.id) FROM bans newest"), "one row per account, as COUNT(DISTINCT)");

        String count = Files.readString(
                Path.of("src/main/java/polaris/messages/outgoing/modtool/ModToolUserInfoComposer.java"));
        assertTrue(count.contains("COUNT(DISTINCT user_id)"));
        assertTrue(count.contains("machine_id = ?"));

        try (MockedStatic<SqlQueries> queries = mockStatic(SqlQueries.class)) {
            queries.when(() -> SqlQueries.query(eq(sql), any(), eq(USER), eq(ModToolUserRecords.LIMIT)))
                    .thenReturn(List.of());

            assertEquals(
                    List.of(),
                    ModToolUserRecords.load(USER, ModToolUserRecords.LINKED_BANS)
                            .entries());
        }
    }

    @Test
    void tradeLocksComeFromTheSanctionsWithoutADate() {
        ResultSet row = row(Map.of("username", "bob", "reason", "scam", "trade_locked_until", 1_800_000_000));

        try (MockedStatic<SqlQueries> queries = mockStatic(SqlQueries.class)) {
            queries.when(() -> SqlQueries.query(
                            eq(ModToolUserRecords.TRADE_LOCKS_SQL), any(), eq(USER), eq(ModToolUserRecords.LIMIT)))
                    .thenAnswer(invocation -> List.of(
                            invocation.<SqlQueries.RowMapper<?>>getArgument(1).map(row)));

            assertEquals(
                    List.of(new ModToolUserRecords.Entry(0, "bob", "", "TRADE_LOCK", "scam", 1_800_000_000)),
                    ModToolUserRecords.load(USER, ModToolUserRecords.TRADE_LOCKS)
                            .entries());
        }

        assertTrue(ModToolUserRecords.TRADE_LOCKS_SQL.contains(
                "WHERE sanctions.habbo_id = ? AND sanctions.trade_locked_until > 0 ORDER BY sanctions.id DESC"));
    }

    @Test
    void aFailingDatabaseAnswersAnEmptyList() {
        try (MockedStatic<SqlQueries> queries = mockStatic(SqlQueries.class)) {
            queries.when(() -> SqlQueries.query(anyString(), any(), any(Object[].class)))
                    .thenThrow(new SqlQueries.DataAccessException("down", null));

            ModToolUserRecords.Records records = ModToolUserRecords.load(USER, ModToolUserRecords.BANS);

            assertEquals(0, records.undetailed());
            assertEquals(List.of(), records.entries());
        }
    }

    @Test
    void expiriesWithoutAnEndAreZero() {
        assertEquals(0, ModToolUserRecords.expiry(0));
        assertEquals(0, ModToolUserRecords.expiry(-5));
        assertEquals(0, ModToolUserRecords.expiry(Integer.MAX_VALUE));
        assertEquals(1_800_000_000, ModToolUserRecords.expiry(1_800_000_000));
        assertEquals(0, ModToolUserRecords.cautionExpiry(1_700_000_000, 0));
        assertEquals(0, ModToolUserRecords.cautionExpiry(0, 5));
        assertEquals(1_700_003_600, ModToolUserRecords.cautionExpiry(1_700_000_000, 1));
    }

    @Test
    void onlyTheFourKindsAreKnown() {
        assertTrue(ModToolUserRecords.isKind(0));
        assertTrue(ModToolUserRecords.isKind(3));
        assertTrue(!ModToolUserRecords.isKind(-1));
        assertTrue(!ModToolUserRecords.isKind(4));
    }

    private static ResultSet row(Map<String, Object> values) {
        ResultSet set = mock(ResultSet.class);
        try {
            when(set.getInt(anyString())).thenAnswer(invocation -> (Integer) values.get(invocation.getArgument(0)));
            when(set.getString(anyString()))
                    .thenAnswer(invocation -> (String) values.get(invocation.<String>getArgument(0)));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return set;
    }
}
