package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.games.Game;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages games within a room.
 */
public class RoomGameManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomGameManager.class);

    private final Room room;
    private final Set<Game> games;

    public RoomGameManager(Room room) {
        this.room = room;
        this.games = ConcurrentHashMap.newKeySet();
    }

    /**
     * Adds a game to the room.
     */
    public boolean addGame(Game game) {
        synchronized (this.games) {
            return this.games.add(game);
        }
    }

    /**
     * Removes and disposes a game from the room.
     */
    public boolean deleteGame(Game game) {
        game.stop();
        game.dispose();
        synchronized (this.games) {
            return this.games.remove(game);
        }
    }

    /**
     * Gets a game by its type.
     */
    public Game getGame(Class<? extends Game> gameType) {
        if (gameType == null) {
            return null;
        }

        synchronized (this.games) {
            for (Game game : this.games) {
                if (gameType.isInstance(game)) {
                    return game;
                }
            }
        }

        return null;
    }

    /**
     * Gets or creates a game of the specified type. The lookup and the creation happen under the lock of the games:
     * two threads asking at once each created a game and both were added, so a room ran two of the same game.
     */
    public Game getGameOrCreate(Class<? extends Game> gameType) {
        synchronized (this.games) {
            Game game = this.getGame(gameType);
            if (game == null) {
                try {
                    game = gameType.getDeclaredConstructor(Room.class).newInstance(this.room);
                    this.addGame(game);
                } catch (Exception e) {
                    LOGGER.error("Error getting game {}", gameType.getName(), e);
                }
            }

            return game;
        }
    }

    /**
     * Gets all games in the room.
     */
    public Set<Game> getGames() {
        return this.games;
    }

    /**
     * Disposes all games.
     */
    public void dispose() {
        for (Game game : this.games) {
            game.dispose();
        }
        this.games.clear();
    }
}
