package com.eu.habbo.messages.incoming.housekeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.messages.outgoing.handshake.DisconnectReasonComposer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class HousekeepingHotelToolsContractTest {
    private static final Path BASE = Path.of("src/main/java/com/eu/habbo/messages/incoming/housekeeping");

    @Test
    void maintenanceActionsAreTheOnesTheClientSends() {
        assertEquals(
                List.of("status", "start", "cancel", "disable"),
                List.of(
                        HousekeepingMaintenanceEvent.STATUS,
                        HousekeepingMaintenanceEvent.START,
                        HousekeepingMaintenanceEvent.CANCEL,
                        HousekeepingMaintenanceEvent.DISABLE));
    }

    @Test
    void changingMaintenanceNeedsTheCommandPermissionAndAlwaysAnswersWithTheState() throws Exception {
        String source = Files.readString(BASE.resolve("HousekeepingMaintenanceEvent.java"));

        assertEquals("cmd_maintenance", HousekeepingMaintenanceEvent.PERMISSION);
        assertTrue(source.contains("if (!this.allowed())"));
        assertTrue(source.contains("hasPermission(PERMISSION)"));
        assertTrue(source.contains("new HousekeepingMaintenanceStatusComposer("));
    }

    @Test
    void theClientMapsTheMaintenanceReason() {
        // connectionStateUi.helpers.ts: getDisconnectReasonKey(-2) === 'maintenance'
        assertEquals(-2, DisconnectReasonComposer.MAINTENANCE);
    }

    @Test
    void wordFilterEditsNeedTheFilterPermissionAndReloadTheFilter() throws Exception {
        String source = Files.readString(BASE.resolve("HousekeepingWordFilterEvent.java"));

        assertEquals(
                List.of("add", "remove"), List.of(HousekeepingWordFilterEvent.ADD, HousekeepingWordFilterEvent.REMOVE));
        assertEquals("cmd_filterword", HousekeepingWordFilterEvent.PERMISSION);
        assertTrue(HousekeepingWordFilterEvent.ADD_SQL.contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(HousekeepingWordFilterEvent.REMOVE_SQL.contains("WHERE `key` = ?"));
        assertTrue(source.contains("new UpdateWordFilterCommand()"), "an edit must reload the filter");
    }

    @Test
    void alertScopesAreTheOnesTheClientOffersAndTheirFieldsAreOptional() throws Exception {
        String source = Files.readString(BASE.resolve("HousekeepingSendHotelAlertEvent.java"));

        assertEquals(
                List.of("hotel", "user", "room", "staff"),
                List.of(
                        HousekeepingSendHotelAlertEvent.SCOPE_HOTEL,
                        HousekeepingSendHotelAlertEvent.SCOPE_USER,
                        HousekeepingSendHotelAlertEvent.SCOPE_ROOM,
                        HousekeepingSendHotelAlertEvent.SCOPE_STAFF));
        assertTrue(
                source.contains("this.packet.bytesAvailable() > 0"),
                "a client that sends only the message keeps the hotel-wide alert");
        assertEquals(List.of("hotel", ""), List.of(HousekeepingSendHotelAlertEvent.parseRecipient("")));
        assertEquals(List.of("staff", ""), List.of(HousekeepingSendHotelAlertEvent.parseRecipient("staff")));
        assertEquals(List.of("user", "Frank"), List.of(HousekeepingSendHotelAlertEvent.parseRecipient("user:Frank")));
        assertEquals(List.of("room", "42"), List.of(HousekeepingSendHotelAlertEvent.parseRecipient("room: 42")));
        assertEquals(42, HousekeepingSendHotelAlertEvent.parseRoomId(" 42 "));
        assertEquals(0, HousekeepingSendHotelAlertEvent.parseRoomId("room"));
    }
}
