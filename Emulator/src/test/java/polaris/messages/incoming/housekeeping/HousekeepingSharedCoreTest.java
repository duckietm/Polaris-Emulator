package polaris.messages.incoming.housekeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The action cores the panel handlers and the RCON housekeeping command share: the input checks
 * come before anything is read or written, for the system operator as for a staff member, and the
 * handlers keep their own rank guards and hand them to the core.
 */
class HousekeepingSharedCoreTest {
    private static final Path BASE = Path.of("src/main/java/polaris/messages/incoming/housekeeping");
    private static final HousekeepingOperator SYSTEM = HousekeepingOperator.system();
    private static final String INVALID = "housekeeping.error.invalid_input";

    @Test
    void invalidInputIsRefusedBeforeAnyLookup() throws Exception {
        assertEquals(
                INVALID,
                HousekeepingKickUserEvent.kick(SYSTEM, 0, "", HousekeepingOperator.ALLOWED)
                        .message());
        assertEquals(
                INVALID,
                HousekeepingKickUserEvent.kick(SYSTEM, 3, "x".repeat(501), HousekeepingOperator.ALLOWED)
                        .message());
        assertEquals(
                INVALID,
                HousekeepingTradeLockUserEvent.tradeLock(SYSTEM, 3, 0, "", HousekeepingOperator.ALLOWED)
                        .message());
        assertEquals(
                INVALID,
                HousekeepingResetUserPasswordEvent.resetPassword(SYSTEM, -1, HousekeepingOperator.ALLOWED)
                        .message());
        assertEquals(
                INVALID,
                HousekeepingSetUserRankEvent.setRank(
                                SYSTEM,
                                3,
                                2,
                                HousekeepingSetUserRankEvent.MAX_TEMPORARY_SECONDS + 1,
                                rank -> true,
                                rank -> true)
                        .message());
        assertEquals(
                INVALID,
                HousekeepingGrantItemEvent.grantItem(SYSTEM, 3, 10, 0, HousekeepingOperator.ALLOWED)
                        .message());
        assertEquals(
                INVALID,
                HousekeepingSetHcSubscriptionEvent.setHc(SYSTEM, 3, -1, HousekeepingOperator.ALLOWED)
                        .message());
        assertEquals(
                INVALID,
                HousekeepingRoomStateEvent.setState(SYSTEM, 0, true, room -> true)
                        .message());
        assertEquals(
                INVALID,
                HousekeepingMuteRoomEvent.mute(SYSTEM, 4, -1, room -> true).message());
        assertEquals(
                INVALID,
                HousekeepingKickAllFromRoomEvent.kickAll(SYSTEM, 0, room -> true)
                        .message());
        assertEquals(
                INVALID,
                HousekeepingTransferRoomOwnershipEvent.transfer(SYSTEM, 4, 0, room -> true)
                        .message());
        assertEquals(
                INVALID,
                HousekeepingDeleteRoomEvent.delete(SYSTEM, 0, room -> true).message());
        assertEquals(
                INVALID,
                HousekeepingRevokeBanEvent.revoke(SYSTEM, 0, owner -> true).message());
    }

    @Test
    void roomSettingsAreCheckedOnlyWhereTheyAreGiven() {
        assertEquals(
                INVALID,
                HousekeepingSaveRoomSettingsEvent.save(
                                SYSTEM, 4, "x".repeat(51), null, null, null, null, null, room -> true)
                        .message());
        assertEquals(
                INVALID,
                HousekeepingSaveRoomSettingsEvent.save(SYSTEM, 4, null, null, 0, null, null, null, room -> true)
                        .message());
        assertEquals(
                INVALID,
                HousekeepingSaveRoomSettingsEvent.save(SYSTEM, 4, null, null, null, null, 3, null, room -> true)
                        .message());
        assertEquals(
                INVALID,
                HousekeepingSaveRoomSettingsEvent.save(
                                SYSTEM, 4, null, null, null, null, null, List.of("a", "b", "c"), room -> true)
                        .message());
        assertEquals(
                INVALID,
                HousekeepingSaveRoomSettingsEvent.save(
                                SYSTEM, 4, null, null, null, null, null, List.of("a;b"), room -> true)
                        .message());
    }

    @Test
    void alertsAndMaintenanceCheckTheirTextFirst() throws Exception {
        assertEquals(
                "housekeeping.error.alert_empty",
                HousekeepingSendHotelAlertEvent.send(SYSTEM, "  ", "hotel").message());
        assertEquals(
                "housekeeping.error.input_too_long",
                HousekeepingSendHotelAlertEvent.send(SYSTEM, "x".repeat(1001), "hotel")
                        .message());
        assertEquals(
                INVALID,
                HousekeepingMaintenanceEvent.apply(SYSTEM, "start", "", -1).message());
        assertEquals(
                INVALID,
                HousekeepingMaintenanceEvent.apply(SYSTEM, "start", "", 121).message());
        assertEquals(
                INVALID,
                HousekeepingMaintenanceEvent.apply(SYSTEM, "status", "", 0).message());
        assertEquals(
                "housekeeping.error.input_too_long",
                HousekeepingMaintenanceEvent.apply(SYSTEM, "start", "x".repeat(201), 5)
                        .message());
        assertEquals(
                INVALID,
                HousekeepingWordFilterEvent.edit(SYSTEM, "add", " ", "", () -> {})
                        .message());
        assertEquals(
                "housekeeping.error.input_too_long",
                HousekeepingWordFilterEvent.edit(SYSTEM, "add", "darn", "x".repeat(17), () -> {})
                        .message());
    }

    @Test
    void theLockdownStillNeedsTheTopRankFromThePanel() {
        HousekeepingResult refused = HousekeepingLockdownEvent.set(SYSTEM, true, () -> false);

        assertFalse(refused.success());
        assertEquals("housekeeping.error.rank_too_high", refused.message());
    }

    @Test
    void theHandlersHandTheirRankGuardsToTheCores() throws Exception {
        for (String handler : List.of(
                "HousekeepingKickUserEvent.java",
                "HousekeepingTradeLockUserEvent.java",
                "HousekeepingResetUserPasswordEvent.java",
                "HousekeepingGrantItemEvent.java",
                "HousekeepingSetHcSubscriptionEvent.java")) {
            String source = Files.readString(BASE.resolve(handler));

            assertTrue(
                    source.contains("() -> HousekeepingTargetRankGuard.canTargetUser(this.client.getUser(), userId)"),
                    handler + " must pass the operator's rank guard to the shared core");
            assertTrue(source.contains("this.operator()"), handler + " must audit as the logged-in staff member");
        }
    }

    @Test
    void theSystemOperatorDeletesAnyNoteAndStaffOnlyTheirOwn() {
        assertTrue(HousekeepingUserNotes.DELETE_SQL.contains("AND author_id = ?"));
        assertFalse(HousekeepingUserNotes.DELETE_ANY_SQL.contains("author_id"));
        assertTrue(HousekeepingUserNotes.DELETE_ANY_SQL.contains("WHERE id = ? AND user_id = ?"));
    }
}
