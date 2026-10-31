package com.eu.habbo.messages.outgoing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The roster and friend-list composers counted null entries they then skipped, so the client misread the packet. */
class NullSkippingCountContractTest {

    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/messages/outgoing/" + path));
    }

    @Test
    void theRosterCountsWhatItWrites() throws Exception {
        String users = source("rooms/users/RoomUsersComposer.java");

        assertTrue(users.contains("List<Habbo> habbos = this.habbos.stream().filter(Objects::nonNull).toList();"));
        assertTrue(users.contains("List<Bot> bots = this.bots.stream().filter(Objects::nonNull).toList();"));
        assertFalse(users.contains("this.response.appendInt(this.habbos.size());"));
        assertFalse(users.contains("this.response.appendInt(this.bots.size());"));
    }

    @Test
    void theFriendUpdateCountsWhatItWrites() throws Exception {
        String friends = source("friends/UpdateFriendComposer.java");

        assertTrue(friends.contains("this.response.appendInt(present.size()); // totalbuddies"));
        assertTrue(friends.contains("for(MessengerBuddy buddy : present){") || friends.contains("for (MessengerBuddy buddy : present) {"));
    }
}
