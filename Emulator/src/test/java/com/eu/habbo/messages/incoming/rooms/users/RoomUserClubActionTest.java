package com.eu.habbo.messages.incoming.rooms.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** AIR: without Habbo Club the club expressions and dances 2 to 4 are ignored. */
class RoomUserClubActionTest {

    @Test
    void clubExpressionsAreKissLaughFourSixAndSixtySeven() {
        int[] club = IntStream.rangeClosed(0, 100)
                .filter(RoomUserInputGuard::isClubOnlyAction)
                .toArray();
        assertEquals("[2, 3, 4, 6, 67]", java.util.Arrays.toString(club));
    }

    @Test
    void clubDancesAreTwoToFour() {
        int[] club = IntStream.rangeClosed(-1, 6)
                .filter(RoomUserInputGuard::isClubOnlyDance)
                .toArray();
        assertEquals("[2, 3, 4]", java.util.Arrays.toString(club));
    }

    @Test
    void bothHandlersCheckClubBeforeActing() throws Exception {
        String action = source("RoomUserActionEvent");
        int actionGuard = action.indexOf("RoomUserInputGuard.isClubOnlyAction(action)");
        assertTrue(actionGuard > 0 && actionGuard < action.indexOf("new UserIdleEvent"));
        assertTrue(action.indexOf("hasActiveClub()", actionGuard) > actionGuard);

        String dance = source("RoomUserDanceEvent");
        int danceGuard = dance.indexOf("RoomUserInputGuard.isClubOnlyDance(danceId)");
        assertTrue(danceGuard > 0 && danceGuard < dance.indexOf("new UserIdleEvent"));
        assertTrue(dance.indexOf("hasActiveClub()", danceGuard) > danceGuard);
    }

    private static String source(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/rooms/users/" + name + ".java"));
    }
}
