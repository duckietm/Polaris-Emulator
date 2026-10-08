package com.eu.habbo.messages.incoming.housekeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HousekeepingAreasTest {
    private static final Path BASE = Path.of("src/main/java/com/eu/habbo/messages/incoming/housekeeping");

    /** Every handler that changes something, with the area it must name. */
    private static final Map<String, String> HANDLER_AREAS = Map.ofEntries(
            Map.entry("FindUserById", "USERS"),
            Map.entry("FindUserByName", "USERS"),
            Map.entry("ForceDisconnectUser", "USERS"),
            Map.entry("KickUser", "USERS"),
            Map.entry("MuteUser", "USERS"),
            Map.entry("TradeLockUser", "USERS"),
            Map.entry("ResetUserPassword", "USERS"),
            Map.entry("UserNote", "USERS"),
            Map.entry("BanUser", "BANS"),
            Map.entry("UnbanUser", "BANS"),
            Map.entry("RevokeBan", "BANS"),
            Map.entry("GiveCredits", "ECONOMY"),
            Map.entry("GiveCurrency", "ECONOMY"),
            Map.entry("GrantItem", "ECONOMY"),
            Map.entry("SetHcSubscription", "ECONOMY"),
            Map.entry("FindRoomById", "ROOMS"),
            Map.entry("SearchRooms", "ROOMS"),
            Map.entry("RoomState", "ROOMS"),
            Map.entry("MuteRoom", "ROOMS"),
            Map.entry("KickAllFromRoom", "ROOMS"),
            Map.entry("TransferRoomOwnership", "ROOMS"),
            Map.entry("DeleteRoom", "ROOMS"),
            Map.entry("SaveRoomSettings", "ROOMS"),
            Map.entry("SetPermission", "PERMISSIONS"),
            Map.entry("SetUserRank", "PERMISSIONS"),
            Map.entry("SendHotelAlert", "HOTEL"),
            Map.entry("Reload", "HOTEL"),
            Map.entry("Maintenance", "HOTEL"),
            Map.entry("WordFilter", "HOTEL"),
            Map.entry("Lockdown", "HOTEL"));

    @Test
    void everyActingHandlerNamesItsArea() throws Exception {
        for (Map.Entry<String, String> entry : HANDLER_AREAS.entrySet()) {
            String source = Files.readString(BASE.resolve("Housekeeping" + entry.getKey() + "Event.java"));

            assertTrue(
                    source.contains("return HousekeepingAreas." + entry.getValue() + ";"),
                    entry.getKey() + " must require the " + entry.getValue() + " area");
        }
    }

    @Test
    void listsFollowTheirKey() {
        assertEquals("acc_hk_users", HousekeepingAreas.forList("user.chatlog"));
        assertEquals("acc_hk_users", HousekeepingAreas.forList("user.private"));
        assertEquals("acc_hk_rooms", HousekeepingAreas.forList("room.visits"));
        assertEquals("acc_hk_bans", HousekeepingAreas.forList("hotel.bans"));
        assertEquals("acc_hk_permissions", HousekeepingAreas.forList("hotel.permissions"));
        assertEquals("acc_hk_hotel", HousekeepingAreas.forList("hotel.wordfilter"));
        assertEquals("acc_hk_hotel", HousekeepingAreas.forList("hotel.security"));
        assertNull(HousekeepingAreas.forList("hotel.online"), "the live and dashboard lists are overview");
        assertNull(HousekeepingAreas.forList("hotel.stats"));
    }

    @Test
    void theMigrationGivesEachAreaWhatPanelAccessHadSoNobodyLosesAnything() throws Exception {
        String migration = Files.readString(
                Path.of("src/main/resources/db/migration/V20260930230000__housekeeping_area_permissions.sql"));

        for (String key : new String[] {
            "acc_hk_users", "acc_hk_bans", "acc_hk_economy", "acc_hk_rooms", "acc_hk_permissions", "acc_hk_hotel"
        }) {
            assertTrue(migration.contains("'" + key + "'"), key + " must be defined and copied");
        }

        assertTrue(migration.contains("hk.`permission_key` = ''acc_housekeeping''"));
    }
}
