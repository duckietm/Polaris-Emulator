package com.eu.habbo.habbohotel.games.snowwar;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SnowWarTeamsTest {

    @Test
    void withoutRematchersItIsTheOldRoundRobin() {
        Map<Integer, Integer> teams = SnowWarTeams.assign(List.of(1, 2, 3, 4, 5), Map.of(), 2);
        assertEquals(List.of(0, 1, 0, 1, 0), List.copyOf(teams.values()));
    }

    @Test
    void rematchersKeepTheirTeamsAndNewcomersFillTheSmallerTeam() {
        // 1 and 3 were blue (1), 2 was red (0); 4 and 5 are new.
        Map<Integer, Integer> teams = SnowWarTeams.assign(List.of(1, 2, 3, 4, 5), Map.of(1, 1, 2, 0, 3, 1), 2);
        assertEquals(1, teams.get(1));
        assertEquals(0, teams.get(2));
        assertEquals(1, teams.get(3));
        assertEquals(0, teams.get(4));
        assertEquals(0, teams.get(5));
    }

    @Test
    void unevenRematchTeamsAreBalancedMovingTheLatestRematcher() {
        // Three rematchers from the blue team and one red, nobody new.
        Map<Integer, Integer> teams = SnowWarTeams.assign(List.of(1, 2, 3, 4), Map.of(1, 1, 2, 1, 3, 1, 4, 0), 2);
        assertEquals(1, teams.get(1));
        assertEquals(1, teams.get(2));
        assertEquals(0, teams.get(3));
        assertEquals(0, teams.get(4));
    }

    @Test
    void aNewcomerMovesBeforeARematcher() {
        // Three blue rematchers and two newcomers: the newcomers fill red first,
        // so nobody who rematched has to change team.
        Map<Integer, Integer> teams = SnowWarTeams.assign(List.of(1, 2, 3, 4, 5), Map.of(1, 1, 2, 1, 3, 1), 2);
        assertEquals(List.of(1, 1, 1, 0, 0), List.copyOf(teams.values()));

        // Four blue rematchers and one newcomer: one rematcher (the latest) must move.
        teams = SnowWarTeams.assign(List.of(1, 2, 3, 4, 5), Map.of(1, 1, 2, 1, 3, 1, 4, 1), 2);
        assertEquals(List.of(1, 1, 1, 0, 0), List.copyOf(teams.values()));
    }

    @Test
    void invalidPreferencesAreIgnored() {
        Map<Integer, Integer> teams = SnowWarTeams.assign(List.of(1, 2), Map.of(1, 7), 2);
        assertEquals(List.of(0, 1), List.copyOf(teams.values()));
    }
}
