package com.eu.habbo.habbohotel.users;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The blocked users and the words of a room filter grew without a limit, one row each. */
class PersonalListLimitContractTest {

    private static final Path MAIN = Path.of("src/main/java/com/eu/habbo");

    @Test
    void blockingStopsAtTheLimit() throws Exception {
        String stats = Files.readString(MAIN.resolve("habbohotel/users/HabboStats.java"));

        assertTrue(stats.contains("static final int MAX_BLOCKED_USERS = 500;"));
        assertTrue(stats.contains("|| this.blockedUsers.size() >= MAX_BLOCKED_USERS) {"));
    }

    @Test
    void roomFilterStopsAtTheLimitAndRefusesBlankWords() throws Exception {
        String chat = Files.readString(MAIN.resolve("habbohotel/rooms/RoomChatManager.java"));
        String modify = Files.readString(MAIN.resolve("messages/incoming/rooms/RoomWordFilterModifyEvent.java"));

        assertTrue(chat.contains("static final int MAX_WORD_FILTER_WORDS = 200;"));
        assertTrue(chat.contains("this.wordFilterWords.size() >= MAX_WORD_FILTER_WORDS"));
        assertTrue(modify.contains("if (word.isEmpty()) {"));
    }
}
