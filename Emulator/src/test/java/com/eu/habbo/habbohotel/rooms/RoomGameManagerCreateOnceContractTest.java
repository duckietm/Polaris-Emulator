package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Two threads asking for the same game at once each created one, and the room ran both. */
class RoomGameManagerCreateOnceContractTest {

    @Test
    void lookupAndCreationShareTheLock() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/rooms/RoomGameManager.java"));
        String method = source.substring(source.indexOf("public Game getGameOrCreate("));

        int lock = method.indexOf("synchronized (this.games) {");
        int lookup = method.indexOf("this.getGame(gameType)");
        int create = method.indexOf("newInstance(this.room)");

        assertTrue(lock > 0 && lock < lookup && lookup < create);
    }
}
