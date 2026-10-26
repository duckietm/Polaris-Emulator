package com.eu.habbo.habbohotel.games;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * A team's members were a HashSet the game cycle iterated while a leaving player was removed from a
 * packet thread: ConcurrentModificationException and a game stuck mid-round. The team score was a
 * plain int added to from several threads.
 */
class GameTeamConcurrencyTest {

    @Test
    void scoresCanBeReadWhilePlayersJoinAndLeave() throws Exception {
        GameTeam team = new GameTeam(GameTeamColors.RED);
        List<GamePlayer> players = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            players.add(mock(GamePlayer.class));
        }
        AtomicReference<Throwable> failure = new AtomicReference<>();
        long until = System.nanoTime() + 300_000_000L;

        Thread changer = Thread.ofPlatform().start(() -> {
            while (System.nanoTime() < until) {
                players.forEach(team::addMember);
                players.forEach(team::removeMember);
            }
        });
        Thread reader = Thread.ofPlatform().start(() -> {
            try {
                while (System.nanoTime() < until) {
                    team.getTotalScore();
                    team.resetScores();
                }
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        changer.join(5_000);
        reader.join(5_000);

        assertNull(failure.get(), () -> "reading the team while players changed threw " + failure.get());
    }

    @Test
    void teamScoreAddsUpFromManyThreads() throws Exception {
        GameTeam team = new GameTeam(GameTeamColors.BLUE);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            threads.add(Thread.ofPlatform().start(() -> {
                for (int point = 0; point < 10_000; point++) {
                    team.addTeamScore(1);
                }
            }));
        }
        for (Thread thread : threads) {
            thread.join(10_000);
        }

        assertEquals(80_000, team.getTeamScore());
    }

    @Test
    void theGameKeepsItsTeamsInAConcurrentMap() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/games/Game.java"));

        assertTrue(source.contains("Map<GameTeamColors, GameTeam> teams = new ConcurrentHashMap<>();"));
    }
}
