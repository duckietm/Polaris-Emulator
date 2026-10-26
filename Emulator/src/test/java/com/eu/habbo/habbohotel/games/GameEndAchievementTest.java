package com.eu.habbo.habbohotel.games;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * At the end of a game the winners' GamePlayerExperience went to the room owner, and the owner's
 * GameAuthorExperience counted the team totals, which include what Give Score boxes hand out: an owner
 * playing alone with a repeater and Give Score 1000 earned unbounded achievement progress.
 */
class GameEndAchievementTest {

    private static GamePlayer player(int played) {
        GamePlayer player = mock(GamePlayer.class);
        when(player.getScoreAchievementValue()).thenReturn(played);
        return player;
    }

    @Test
    void onlyThePointsPlayersScoredCount() {
        GameTeam red = new GameTeam(GameTeamColors.RED);
        red.addMember(player(12));
        red.addMember(player(0));
        red.addTeamScore(1000);
        GameTeam blue = new GameTeam(GameTeamColors.BLUE);
        blue.addMember(player(5));

        assertEquals(17, Game.playedPoints(List.of(red, blue)), "the team score from wired is left out");
    }

    @Test
    void theWinnerGetsTheirOwnExperience() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/games/Game.java"))
                .replaceAll("\\s+", " ");

        assertTrue(
                source.contains(
                        "AchievementManager.progressAchievement( winner, Emulator.getGameEnvironment() .getAchievementManager() .getAchievement(\"GamePlayerExperience\"))"));
        assertFalse(
                source.contains(
                        "roomOwner, Emulator.getGameEnvironment() .getAchievementManager() .getAchievement(\"GamePlayerExperience\")"));
        assertTrue(source.contains("getAchievement(\"GameAuthorExperience\"), playedPoints)"));
    }
}
