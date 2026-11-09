package polaris.messages.rcon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import polaris.hotel.GameEnvironment;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.modtool.HousekeepingAuditLog;
import polaris.hotel.modtool.ModToolIssue;
import polaris.hotel.modtool.ModToolManager;
import polaris.hotel.modtool.ModToolTicketState;
import polaris.hotel.users.User;
import polaris.hotel.users.UserManager;
import polaris.messages.incoming.housekeeping.HousekeepingDeleteRoomEvent;
import polaris.messages.incoming.housekeeping.HousekeepingKickUserEvent;
import polaris.messages.incoming.housekeeping.HousekeepingMaintenanceEvent;
import polaris.messages.incoming.housekeeping.HousekeepingOperator;
import polaris.messages.incoming.housekeeping.HousekeepingResetUserPasswordEvent;
import polaris.messages.incoming.housekeeping.HousekeepingResult;
import polaris.messages.incoming.housekeeping.HousekeepingSaveRoomSettingsEvent;
import polaris.messages.incoming.housekeeping.HousekeepingSetUserRankEvent;
import polaris.messages.incoming.housekeeping.HousekeepingTradeLockUserEvent;
import polaris.messages.incoming.housekeeping.HousekeepingWordFilterEvent;
import polaris.messages.outgoing.modtool.ModToolIssueResponseAlertComposer;

class HousekeepingRconTest {
    private static final Gson GSON = new Gson();

    private final GameEnvironment environment = mock(GameEnvironment.class);
    private final ModToolManager modTools = mock(ModToolManager.class);
    private final UserManager users = mock(UserManager.class);

    HousekeepingRconTest() {
        when(this.environment.getModToolManager()).thenReturn(this.modTools);
        when(this.environment.getUserManager()).thenReturn(this.users);
    }

    private Housekeeping command() {
        return new Housekeeping(() -> this.environment);
    }

    private static Housekeeping.JSON payload(String json) {
        return GSON.fromJson(json, Housekeeping.JSON.class);
    }

    private Housekeeping run(String json) {
        Housekeeping command = this.command();
        Housekeeping.JSON payload = payload(json);

        if (command.validate(payload)) {
            command.handle(GSON, payload);
        }

        return command;
    }

    @Test
    void theActionsAreTheContractsActions() {
        assertEquals(
                List.of(
                        "user.kick",
                        "user.trade_lock",
                        "user.reset_password",
                        "user.set_rank",
                        "user.grant_item",
                        "user.set_hc",
                        "user.note.add",
                        "user.note.delete",
                        "room.open",
                        "room.close",
                        "room.settings",
                        "room.mute",
                        "room.kick_all",
                        "room.transfer",
                        "room.delete",
                        "hotel.alert",
                        "hotel.maintenance",
                        "hotel.maintenance.status",
                        "hotel.lockdown",
                        "hotel.lockdown.status",
                        "hotel.wordfilter.add",
                        "hotel.wordfilter.remove",
                        "hotel.permission.set",
                        "hotel.reload",
                        "hotel.dashboard",
                        "ban.revoke",
                        "ticket.list",
                        "ticket.close",
                        "ticket.reply"),
                this.command().actionKeys());
    }

    @Test
    void anUnknownActionListsTheActions() {
        Housekeeping command = this.run("{\"action\":\"user.explode\"}");

        assertEquals(RCONMessage.STATUS_ERROR, command.status);
        assertEquals("Unknown action. Use one of: " + String.join(", ", command.actionKeys()), command.message);
    }

    @Test
    void theActionIsRequired() {
        Housekeeping command = this.run("{\"user_id\":5}");

        assertEquals(RCONMessage.STATUS_ERROR, command.status);
        assertEquals("action is required", command.message);
    }

    @Test
    void theDataMapsOntoTheSnakeCaseFields() {
        Housekeeping.JSON json = payload("{\"action\":\"room.settings\",\"room_id\":3,\"users_max\":25,"
                + "\"tags\":[\"a\",\"b\"],\"enabled\":true,\"close\":false,\"value\":0}");

        assertEquals(3, json.room_id);
        assertEquals(25, json.users_max);
        assertEquals(List.of("a", "b"), json.tags);
        assertEquals(Boolean.TRUE, json.enabled);
        assertEquals(Boolean.FALSE, json.close);
        assertEquals(0, json.value);
        assertNull(json.name, "a field left out stays null");
        assertNull(json.category_id);
    }

    @Test
    void aKickRunsTheHandlersCoreAsTheSystemOperatorPastTheRankGuard() {
        try (MockedStatic<HousekeepingKickUserEvent> kick = mockStatic(HousekeepingKickUserEvent.class)) {
            kick.when(() -> HousekeepingKickUserEvent.kick(any(), anyInt(), any(), any()))
                    .thenReturn(HousekeepingResult.ok(5));

            Housekeeping command = this.run("{\"action\":\"user.kick\",\"user_id\":5,\"reason\":\"bye\"}");

            assertEquals(RCONMessage.STATUS_OK, command.status);
            assertEquals("", command.message);

            ArgumentCaptor<BooleanSupplier> guard = ArgumentCaptor.forClass(BooleanSupplier.class);
            kick.verify(() -> HousekeepingKickUserEvent.kick(
                    eq(HousekeepingOperator.system()), eq(5), eq("bye"), guard.capture()));
            assertTrue(guard.getValue().getAsBoolean());
        }
    }

    @Test
    void theSystemOperatorIsTheManager() {
        HousekeepingOperator system = HousekeepingOperator.system();

        assertEquals(0, system.id());
        assertEquals("All-in-1 Manager", system.name());
        assertEquals("rcon", system.ip());
        assertTrue(system.isSystem());
    }

    @Test
    void aFailureCarriesTheHandlersErrorKeyAndAStatus() {
        try (MockedStatic<HousekeepingTradeLockUserEvent> lock = mockStatic(HousekeepingTradeLockUserEvent.class)) {
            lock.when(() -> HousekeepingTradeLockUserEvent.tradeLock(any(), anyInt(), anyInt(), any(), any()))
                    .thenReturn(HousekeepingResult.fail("housekeeping.error.user_not_found"));

            Housekeeping command =
                    this.run("{\"action\":\"user.trade_lock\",\"user_id\":9,\"hours\":2,\"reason\":\"r\"}");

            assertEquals(RCONMessage.USER_NOT_FOUND, command.status);
            assertEquals("housekeeping.error.user_not_found", command.message);
            lock.verify(() -> HousekeepingTradeLockUserEvent.tradeLock(
                    eq(HousekeepingOperator.system()), eq(9), eq(2), eq("r"), any()));
        }
    }

    @Test
    void errorKeysMapOntoTheRconStatuses() {
        assertEquals(RCONMessage.USER_NOT_FOUND, Housekeeping.statusFor("housekeeping.error.user_not_found"));
        assertEquals(RCONMessage.USER_NOT_FOUND, Housekeeping.statusFor("housekeeping.error.new_owner_not_found"));
        assertEquals(RCONMessage.ROOM_NOT_FOUND, Housekeeping.statusFor("housekeeping.error.room_not_found"));
        assertEquals(RCONMessage.ROOM_NOT_FOUND, Housekeeping.statusFor("housekeeping.error.room_not_active"));
        assertEquals(RCONMessage.SYSTEM_ERROR, Housekeeping.statusFor("housekeeping.error.db_failed"));
        assertEquals(RCONMessage.STATUS_ERROR, Housekeeping.statusFor("housekeeping.error.invalid_input"));
        assertEquals(RCONMessage.STATUS_ERROR, Housekeeping.statusFor("housekeeping.error.user_offline"));
    }

    @Test
    void aRoomDeleteOfAnUnknownRoomIsRoomNotFound() {
        try (MockedStatic<HousekeepingDeleteRoomEvent> delete = mockStatic(HousekeepingDeleteRoomEvent.class)) {
            delete.when(() -> HousekeepingDeleteRoomEvent.delete(any(), anyInt(), any()))
                    .thenReturn(HousekeepingResult.fail("housekeeping.error.room_not_found"));

            Housekeeping command = this.run("{\"action\":\"room.delete\",\"room_id\":77}");

            assertEquals(RCONMessage.ROOM_NOT_FOUND, command.status);
            delete.verify(() -> HousekeepingDeleteRoomEvent.delete(eq(HousekeepingOperator.system()), eq(77), any()));
        }
    }

    @Test
    void aPasswordResetAnswersWithThePassword() {
        try (MockedStatic<HousekeepingResetUserPasswordEvent> reset =
                mockStatic(HousekeepingResetUserPasswordEvent.class)) {
            reset.when(() -> HousekeepingResetUserPasswordEvent.resetPassword(any(), anyInt(), any()))
                    .thenReturn(HousekeepingResult.ok(4, "Abc234defGHJ"));

            Housekeeping command = this.run("{\"action\":\"user.reset_password\",\"user_id\":4}");

            assertEquals(RCONMessage.STATUS_OK, command.status);
            assertEquals(
                    "Abc234defGHJ",
                    JsonParser.parseString(command.message)
                            .getAsJsonObject()
                            .get("password")
                            .getAsString());
        }
    }

    @Test
    void aRankChangePassesTheDurationAndGuardsThatAlwaysAllow() {
        try (MockedStatic<HousekeepingSetUserRankEvent> rank = mockStatic(HousekeepingSetUserRankEvent.class)) {
            rank.when(() -> HousekeepingSetUserRankEvent.setRank(any(), anyInt(), anyInt(), anyInt(), any(), any()))
                    .thenReturn(HousekeepingResult.ok(4));

            Housekeeping command = this.run("{\"action\":\"user.set_rank\",\"user_id\":4,\"rank\":7,\"seconds\":3600}");

            assertEquals(RCONMessage.STATUS_OK, command.status);
            rank.verify(() -> HousekeepingSetUserRankEvent.setRank(
                    eq(HousekeepingOperator.system()), eq(4), eq(7), eq(3600), any(), any()));
        }
    }

    @Test
    void roomSettingsLeftOutAreNullSoTheRoomKeepsThem() {
        try (MockedStatic<HousekeepingSaveRoomSettingsEvent> save =
                mockStatic(HousekeepingSaveRoomSettingsEvent.class)) {
            save.when(() -> HousekeepingSaveRoomSettingsEvent.save(
                            any(), anyInt(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(HousekeepingResult.ok(3));

            Housekeeping command = this.run("{\"action\":\"room.settings\",\"room_id\":3,\"name\":\"Lobby\"}");

            assertEquals(RCONMessage.STATUS_OK, command.status);
            save.verify(() -> HousekeepingSaveRoomSettingsEvent.save(
                    eq(HousekeepingOperator.system()),
                    eq(3),
                    eq("Lobby"),
                    isNull(),
                    isNull(),
                    isNull(),
                    isNull(),
                    isNull(),
                    any()));
        }
    }

    @Test
    void maintenanceNowIsAStartWithoutCountdown() {
        try (MockedStatic<HousekeepingMaintenanceEvent> maintenance = mockStatic(HousekeepingMaintenanceEvent.class)) {
            maintenance
                    .when(() -> HousekeepingMaintenanceEvent.apply(any(), anyString(), any(), anyInt()))
                    .thenReturn(HousekeepingResult.ok(0));

            Housekeeping command =
                    this.run("{\"action\":\"hotel.maintenance\",\"mode\":\"now\",\"message\":\"Back soon\"}");

            assertEquals(RCONMessage.STATUS_OK, command.status);
            maintenance.verify(
                    () -> HousekeepingMaintenanceEvent.apply(HousekeepingOperator.system(), "start", "Back soon", 0));
        }
    }

    @Test
    void aWordFilterRemovalPassesNoReplacement() throws Exception {
        try (MockedStatic<HousekeepingWordFilterEvent> filter = mockStatic(HousekeepingWordFilterEvent.class)) {
            filter.when(() -> HousekeepingWordFilterEvent.edit(any(), anyString(), any(), any(), any()))
                    .thenReturn(HousekeepingResult.fail("housekeeping.error.word_not_found"));

            Housekeeping command = this.run("{\"action\":\"hotel.wordfilter.remove\",\"word\":\"darn\"}");

            assertEquals(RCONMessage.STATUS_ERROR, command.status);
            assertEquals("housekeeping.error.word_not_found", command.message);
            filter.verify(() -> HousekeepingWordFilterEvent.edit(
                    eq(HousekeepingOperator.system()), eq("remove"), eq("darn"), eq(""), any()));
        }
    }

    @Test
    void theLockdownAndAPermissionNeedTheirValue() {
        Housekeeping lockdown = this.run("{\"action\":\"hotel.lockdown\"}");
        Housekeeping permission =
                this.run("{\"action\":\"hotel.permission.set\",\"permission\":\"cmd_kick\",\"rank\":3}");

        assertEquals(RCONMessage.STATUS_ERROR, lockdown.status);
        assertEquals("housekeeping.error.invalid_input", lockdown.message);
        assertEquals(RCONMessage.STATUS_ERROR, permission.status);
        assertEquals("housekeeping.error.invalid_input", permission.message);
    }

    @Test
    void aReloadTargetOutsideThePanelsIsRefused() {
        Housekeeping command = this.run("{\"action\":\"hotel.reload\",\"target\":\"quests\"}");

        assertEquals(RCONMessage.STATUS_ERROR, command.status);
        assertEquals(
                "Unknown target. Use one of: catalog, texts, permissions, items, navigator, config, wordfilter",
                command.message);
    }

    // ---- tickets ----

    private static ModToolIssue issue(int id, ModToolTicketState state, int reporterId) {
        ModToolIssue issue = mock(ModToolIssue.class);
        issue.id = id;
        issue.state = state;
        issue.senderId = reporterId;
        issue.senderUsername = "reporter" + reporterId;
        issue.reportedId = 50;
        issue.reportedUsername = "reported";
        issue.message = "help " + id;
        issue.category = 2;
        issue.timestamp = 1_700_000_000 + id;
        issue.modId = -1;
        issue.modName = "";
        return issue;
    }

    private void tickets(ModToolIssue... issues) {
        Map<Integer, ModToolIssue> tickets = new LinkedHashMap<>();
        for (ModToolIssue issue : issues) tickets.put(issue.id, issue);
        when(this.modTools.getTickets()).thenReturn(tickets);
        when(this.modTools.getTicket(anyInt())).thenAnswer(i -> tickets.get(i.<Integer>getArgument(0)));
    }

    @Test
    void theTicketListHoldsTheOpenAndPickedTickets() {
        ModToolIssue picked = issue(3, ModToolTicketState.PICKED, 11);
        picked.modId = 8;
        picked.modName = "Mod";
        this.tickets(issue(2, ModToolTicketState.OPEN, 10), picked, issue(4, ModToolTicketState.CLOSED, 12));

        Housekeeping command = this.run("{\"action\":\"ticket.list\"}");

        assertEquals(RCONMessage.STATUS_OK, command.status);
        JsonArray list = JsonParser.parseString(command.message).getAsJsonArray();
        assertEquals(2, list.size());

        JsonObject open = list.get(0).getAsJsonObject();
        assertEquals(2, open.get("id").getAsInt());
        assertEquals("open", open.get("state").getAsString());
        assertEquals(10, open.get("reporter_id").getAsInt());
        assertEquals("reporter10", open.get("reporter_name").getAsString());
        assertEquals("reported", open.get("reported_name").getAsString());
        assertEquals(0, open.get("moderator_id").getAsInt());
        assertEquals("", open.get("room_name").getAsString());
        assertEquals(1_700_000_002, open.get("created_at").getAsInt());

        JsonObject held = list.get(1).getAsJsonObject();
        assertEquals("picked", held.get("state").getAsString());
        assertEquals(8, held.get("moderator_id").getAsInt());
        assertEquals("Mod", held.get("moderator_name").getAsString());
    }

    @Test
    void aTicketCloseNeedsAKnownResolutionAndAnOpenTicket() {
        this.tickets(issue(4, ModToolTicketState.CLOSED, 12));

        Housekeeping badResolution = this.run("{\"action\":\"ticket.close\",\"ticket_id\":4,\"resolution\":\"maybe\"}");
        Housekeeping closed = this.run("{\"action\":\"ticket.close\",\"ticket_id\":4,\"resolution\":\"useless\"}");

        assertEquals("housekeeping.error.invalid_input", badResolution.message);
        assertEquals(RCONMessage.STATUS_ERROR, closed.status);
        assertEquals("housekeeping.error.ticket_not_found", closed.message);
        verify(this.modTools, never()).closeTicketAsUseless(any(), any());
    }

    @Test
    void aTicketIsTakenByTheSystemOperatorAndClosedWithTheVerdict() {
        ModToolIssue ticket = issue(5, ModToolTicketState.OPEN, 10);
        User reporter = mock(User.class);
        when(this.users.getUser(10)).thenReturn(reporter);
        this.tickets(ticket);

        try (MockedStatic<HousekeepingAuditLog> audit = mockStatic(HousekeepingAuditLog.class)) {
            Housekeeping command = this.run("{\"action\":\"ticket.close\",\"ticket_id\":5,\"resolution\":\"abusive\"}");

            assertEquals(RCONMessage.STATUS_OK, command.status);
            verify(this.modTools).pickTicket(ticket, 0, "All-in-1 Manager");
            verify(this.modTools).closeTicketAsAbusive(ticket, reporter);
            audit.verify(() -> HousekeepingAuditLog.log(
                    eq(0),
                    eq("All-in-1 Manager"),
                    eq("ticket.close"),
                    eq(HousekeepingAuditLog.TARGET_USER),
                    eq(10),
                    anyString(),
                    eq("ticket=5 resolution=abusive"),
                    eq("rcon")));
        }
    }

    @Test
    void aReplyReachesAnOnlineReporterAndMayCloseTheTicket() {
        ModToolIssue ticket = issue(6, ModToolTicketState.OPEN, 10);
        User reporter = mock(User.class);
        GameClient client = mock(GameClient.class);
        when(reporter.getClient()).thenReturn(client);
        when(this.users.getUser(10)).thenReturn(reporter);
        this.tickets(ticket);

        try (MockedStatic<HousekeepingAuditLog> audit = mockStatic(HousekeepingAuditLog.class)) {
            Housekeeping command =
                    this.run("{\"action\":\"ticket.reply\",\"ticket_id\":6,\"message\":\"Sorted!\",\"close\":true}");

            assertEquals(RCONMessage.STATUS_OK, command.status);
            verify(client).sendResponse(any(ModToolIssueResponseAlertComposer.class));
            verify(this.modTools).closeTicketAsHandled(ticket, reporter);
        }
    }

    @Test
    void aReplyToAnOfflineReporterIsRefused() {
        this.tickets(issue(6, ModToolTicketState.OPEN, 10));

        Housekeeping offline = this.run("{\"action\":\"ticket.reply\",\"ticket_id\":6,\"message\":\"Hi\"}");
        Housekeeping empty = this.run("{\"action\":\"ticket.reply\",\"ticket_id\":6,\"message\":\" \"}");

        assertEquals("housekeeping.error.user_offline", offline.message);
        assertEquals("housekeeping.error.invalid_input", empty.message);
        verify(this.modTools, never()).closeTicketAsHandled(any(), any());
        assertFalse(offline.status == RCONMessage.STATUS_OK);
    }

    @Test
    void anExceptionIsASystemError() {
        try (MockedStatic<HousekeepingKickUserEvent> kick = mockStatic(HousekeepingKickUserEvent.class)) {
            kick.when(() -> HousekeepingKickUserEvent.kick(any(), anyInt(), any(), any()))
                    .thenThrow(new IllegalStateException("boom"));

            Housekeeping command = this.run("{\"action\":\"user.kick\",\"user_id\":5}");

            assertEquals(RCONMessage.SYSTEM_ERROR, command.status);
        }
    }

    @Test
    void lockdownRequestsPassTheirValue() {
        try (MockedStatic<polaris.messages.incoming.housekeeping.HousekeepingLockdownEvent> lockdown =
                mockStatic(polaris.messages.incoming.housekeeping.HousekeepingLockdownEvent.class)) {
            lockdown.when(() -> polaris.messages.incoming.housekeeping.HousekeepingLockdownEvent.set(
                            any(), anyBoolean(), any()))
                    .thenReturn(HousekeepingResult.ok(1));

            Housekeeping command = this.run("{\"action\":\"hotel.lockdown\",\"enabled\":true}");

            assertEquals(RCONMessage.STATUS_OK, command.status);
            lockdown.verify(() -> polaris.messages.incoming.housekeeping.HousekeepingLockdownEvent.set(
                    eq(HousekeepingOperator.system()), eq(true), any()));
        }
    }
}
