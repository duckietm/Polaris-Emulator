package com.eu.habbo.habbohotel.rooms.competition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RoomCompetitionTest {
    private static RoomCompetition competition() {
        return new RoomCompetition(1, "spring", "Spring", List.of("chair"), 3, 100, 200, 200, 300, 0);
    }

    @Test
    void aWindowIsOpenFromItsStartUpToButNotIncludingItsEnd() {
        RoomCompetition competition = competition();

        assertFalse(competition.submissionOpen(99));
        assertTrue(competition.submissionOpen(100));
        assertTrue(competition.submissionOpen(199));
        assertFalse(competition.submissionOpen(200));
    }

    @Test
    void votingPicksUpWhereSubmittingStops() {
        RoomCompetition competition = competition();

        assertTrue(competition.votingOpen(200));
        assertTrue(competition.votingOpen(299));
        assertFalse(competition.votingOpen(300));
        assertFalse(competition.votingOpen(199));
    }

    @Test
    void theRequiredFurniListIsSplitOnCommasAndTrimmed() {
        assertEquals(List.of("chair", "table"), RoomCompetitionManager.splitFurni(" chair , table "));
        assertEquals(List.of(), RoomCompetitionManager.splitFurni(""));
        assertEquals(List.of(), RoomCompetitionManager.splitFurni(null));
        assertEquals(List.of("chair"), RoomCompetitionManager.splitFurni("chair,,"));
    }

    @Test
    void withoutAThresholdEveryRoomIsOldEnough() {
        RoomCompetition competition = competition();

        assertTrue(competition.acceptsRoomCreatedAt(0));
        assertTrue(competition.acceptsRoomCreatedAt(1));
    }

    @Test
    void withAThresholdOnlyRoomsMadeAfterItEnter() {
        RoomCompetition competition = new RoomCompetition(1, "spring", "Spring", List.of(), 3, 100, 200, 200, 300, 150);

        assertFalse(competition.acceptsRoomCreatedAt(150));
        assertTrue(competition.acceptsRoomCreatedAt(151));
    }

    @Test
    void aRoomWhoseAgeIsUnknownCountsAsTooOld() {
        RoomCompetition competition = new RoomCompetition(1, "spring", "Spring", List.of(), 3, 100, 200, 200, 300, 150);

        assertFalse(competition.acceptsRoomCreatedAt(0));
    }
}
