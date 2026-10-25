package com.eu.habbo.habbohotel.games.wired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

import com.eu.habbo.habbohotel.games.Game;
import com.eu.habbo.habbohotel.games.GameState;
import com.eu.habbo.habbohotel.rooms.Room;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

/**
 * WiredGame declared its own state field, shadowing Game.state. Read as a Game, the state stayed IDLE
 * forever, so Give Score and Give Score to Team never scored for Wired teams; getState() answered
 * RUNNING forever, so every timer stop ran onEnd again and saved the scores once more.
 */
class WiredGameStateTest {

    @Test
    void thereIsOneStateAndItFollowsTheGame() {
        Game game = new WiredGame(mock(Room.class));

        assertEquals(GameState.IDLE, game.getState(), "no game has started yet");
        assertEquals(GameState.IDLE, game.state);

        game.initialise();
        assertEquals(GameState.RUNNING, game.getState());
        assertEquals(GameState.RUNNING, game.state, "what Give Score reads agrees with getState()");

        game.stop();
        assertEquals(GameState.IDLE, game.getState(), "a stopped game is not ended a second time");
    }

    @Test
    void wiredGameDeclaresNoStateOfItsOwn() {
        for (Field field : WiredGame.class.getDeclaredFields()) {
            assertFalse(field.getName().equals("state"), "a second state field shadows Game.state");
        }
    }
}
