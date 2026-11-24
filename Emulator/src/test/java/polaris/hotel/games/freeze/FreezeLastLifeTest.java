package polaris.hotel.games.freeze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import polaris.hotel.games.GameTeamColors;
import polaris.hotel.users.User;

/**
 * The hit that takes a Freeze player's last life only freezes them; they leave the game when that freeze ends, with
 * the leaving effect, and are moved to an exit a moment later.
 */
class FreezeLastLifeTest {
    private int freezeTime;
    private int looseSnowballs;
    private int looseBoost;

    @BeforeEach
    void settings() {
        this.freezeTime = FreezeGame.FREEZE_TIME;
        this.looseSnowballs = FreezeGame.FREEZE_LOOSE_SNOWBALL;
        this.looseBoost = FreezeGame.FREEZE_LOOSE_BOOST;
        FreezeGame.FREEZE_TIME = 2;
        FreezeGame.FREEZE_LOOSE_SNOWBALL = 0;
        FreezeGame.FREEZE_LOOSE_BOOST = 0;
    }

    @AfterEach
    void restore() {
        FreezeGame.FREEZE_TIME = this.freezeTime;
        FreezeGame.FREEZE_LOOSE_SNOWBALL = this.looseSnowballs;
        FreezeGame.FREEZE_LOOSE_BOOST = this.looseBoost;
    }

    @Test
    void thePlayerIsOutOnlyWhenTheLastFreezeEnds() {
        User user = mock(User.class, RETURNS_DEEP_STUBS);
        FreezeGamePlayer player = new FreezeGamePlayer(user, GameTeamColors.RED);
        player.reset();

        // Two lives go without taking the player out.
        player.takeLife();
        player.takeLife();
        assertEquals(1, player.getLives());
        assertFalse(player.isOut());

        // The last hit freezes the player; they are still in the game while frozen.
        player.freeze();
        assertEquals(0, player.getLives());
        assertTrue(player.isFrozen());
        assertFalse(player.isOut());
        assertFalse(player.dead);

        player.cycle();
        assertFalse(player.isOut());

        // The freeze ends: the player is out, and is not set walking again by the freeze's end.
        player.cycle();
        assertTrue(player.isOut());
        verify(user.getRoomUnit(), never()).setCanWalk(true);
    }

    @Test
    void aPlayerWithLivesLeftWalksAgainWhenTheFreezeEnds() {
        User user = mock(User.class, RETURNS_DEEP_STUBS);
        FreezeGamePlayer player = new FreezeGamePlayer(user, GameTeamColors.RED);
        player.reset();

        player.freeze();
        player.cycle();
        player.cycle();

        assertEquals(2, player.getLives());
        assertFalse(player.isOut());
        verify(user.getRoomUnit()).setCanWalk(true);
    }

    @Test
    void theGameTakesThePlayerOutAfterTheTickAndMovesThemLater() throws Exception {
        String game = Files.readString(Path.of("src/main/java/polaris/hotel/games/freeze/FreezeGame.java"));

        // Out players are collected during the tick and leave after it; leaving costs the freeze points once more.
        assertTrue(game.indexOf("out.add((FreezeGamePlayer) player);") < game.indexOf("this.playerDies(player);"));
        assertTrue(game.contains("player.addScore(-FREEZE_LOOSE_POINTS);"));
        // The leaving effect is given at once, the move to an exit and the end of the effect are delayed.
        assertTrue(game.contains("this.room.giveEffect(user, LEAVING_EFFECT, -1);"));
        assertTrue(game.contains("Emulator.getThreading().run(() -> this.moveToExit(user), LEAVE_DELAY_MS);"));
        assertTrue(game.contains("Emulator.getThreading().run(new FreezeClearEffects(user), LEAVE_EFFECT_END_MS);"));
        assertEquals(4, FreezeGame.LEAVING_EFFECT);
        // Nobody stays frozen once the game is over.
        assertTrue(game.contains("p.getUser().getRoomUnit().setCanWalk(true);"));
    }
}
