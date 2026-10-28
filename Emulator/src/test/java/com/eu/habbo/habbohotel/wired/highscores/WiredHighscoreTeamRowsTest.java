package com.eu.habbo.habbohotel.wired.highscores;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;

/**
 * The team boards grouped rows by the hash of the list of names. Users whose names were not cached all became
 * "null" and shared one row, two different lists could share a hash, and one team listed in another order became
 * two teams. A team is now the set of its user ids.
 */
class WiredHighscoreTeamRowsTest {

    /** Nobody is cached: every name resolves to the same text. */
    private static final IntFunction<String> UNCACHED = id -> null;

    private static WiredHighscoreDataEntry entry(int score, boolean win, Integer... userIds) {
        return new WiredHighscoreDataEntry(1, List.of(userIds), score, win, 0);
    }

    private static List<Integer> values(List<WiredHighscoreRow> rows) {
        return rows.stream().map(WiredHighscoreRow::getValue).toList();
    }

    @Test
    void usersWithTheSameNameAreStillDifferentTeams() {
        List<WiredHighscoreDataEntry> entries = List.of(entry(10, true, 1), entry(30, true, 2), entry(20, true, 1));

        assertEquals(
                List.of(30, 20),
                values(WiredHighscoreManager.rows(entries, WiredHighscoreScoreType.PERTEAM, UNCACHED)));
        assertEquals(
                List.of(2, 1), values(WiredHighscoreManager.rows(entries, WiredHighscoreScoreType.MOSTWIN, UNCACHED)));
        assertEquals(
                List.of(30, 20),
                values(WiredHighscoreManager.rows(entries, WiredHighscoreScoreType.LONGESTTIME, UNCACHED)));
        assertEquals(
                List.of(10, 30),
                values(WiredHighscoreManager.rows(entries, WiredHighscoreScoreType.FASTESTTIME, UNCACHED)));
    }

    @Test
    void oneTeamListedInAnotherOrderIsOneTeam() {
        List<WiredHighscoreDataEntry> entries = List.of(entry(5, true, 7, 8), entry(9, true, 8, 7));

        assertEquals(
                List.of(9),
                values(WiredHighscoreManager.rows(entries, WiredHighscoreScoreType.PERTEAM, id -> "u" + id)));
        assertEquals(
                List.of(2),
                values(WiredHighscoreManager.rows(entries, WiredHighscoreScoreType.MOSTWIN, id -> "u" + id)));
    }

    @Test
    void classicKeepsEveryEntryAndShowsTheNames() {
        List<WiredHighscoreDataEntry> entries = List.of(entry(4, false, 1), entry(6, false, 1));

        List<WiredHighscoreRow> rows =
                WiredHighscoreManager.rows(entries, WiredHighscoreScoreType.CLASSIC, id -> "u" + id);

        assertEquals(List.of(6, 4), values(rows));
        assertEquals(List.of("u1"), rows.get(0).getUsers());
    }
}
