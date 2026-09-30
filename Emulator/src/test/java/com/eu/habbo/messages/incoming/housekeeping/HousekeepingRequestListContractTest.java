package com.eu.habbo.messages.incoming.housekeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class HousekeepingRequestListContractTest {
    private static final Path HANDLER =
            Path.of("src/main/java/com/eu/habbo/messages/incoming/housekeeping/HousekeepingRequestListEvent.java");

    @Test
    void theListKeysAreTheOnesTheClientAsksFor() {
        assertEquals(
                List.of(
                        "user.chatlog",
                        "user.visits",
                        "user.clones",
                        "user.names",
                        "user.sanctions",
                        "user.notes",
                        "user.temp_rank",
                        "user.private",
                        "room.chatlog",
                        "room.visits",
                        "hotel.bans",
                        "hotel.wordfilter",
                        "hotel.online",
                        "hotel.rooms",
                        "hotel.stats",
                        "hotel.permissions",
                        "hotel.security"),
                List.of(
                        HousekeepingRequestListEvent.USER_CHATLOG,
                        HousekeepingRequestListEvent.USER_VISITS,
                        HousekeepingRequestListEvent.USER_CLONES,
                        HousekeepingRequestListEvent.USER_NAMES,
                        HousekeepingRequestListEvent.USER_SANCTIONS,
                        HousekeepingRequestListEvent.USER_NOTES,
                        HousekeepingRequestListEvent.USER_TEMP_RANK,
                        HousekeepingRequestListEvent.USER_PRIVATE,
                        HousekeepingRequestListEvent.ROOM_CHATLOG,
                        HousekeepingRequestListEvent.ROOM_VISITS,
                        HousekeepingRequestListEvent.HOTEL_BANS,
                        HousekeepingRequestListEvent.HOTEL_WORDFILTER,
                        HousekeepingRequestListEvent.HOTEL_ONLINE,
                        HousekeepingRequestListEvent.HOTEL_ROOMS,
                        HousekeepingRequestListEvent.HOTEL_STATS,
                        HousekeepingRequestListEvent.HOTEL_PERMISSIONS,
                        HousekeepingRequestListEvent.HOTEL_SECURITY));
    }

    @Test
    void everyListReusesTheModToolQueryAndUnknownKeysAreRefused() throws Exception {
        String source = Files.readString(HANDLER);

        for (String query : List.of(
                "getUserChatlog(",
                "getRoomChatlog(",
                "getUserRoomVisits(",
                "getVisitsForRoom(",
                "getCloneAccounts(",
                "getNameChanges(",
                "getSanctions(")) {
            assertTrue(source.contains(query), "the list must reuse " + query);
        }

        assertTrue(
                source.contains("default -> HousekeepingListComposer.failure("), "an unknown list must get an answer");
    }
}
