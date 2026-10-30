package com.eu.habbo.messages.incoming.friends;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The same friend listed many times in one invite got the invite once per copy. */
class InviteFriendsDuplicatesContractTest {

    @Test
    void eachFriendIsInvitedOnce() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/eu/habbo/messages/incoming/friends/InviteFriendsEvent.java"));

        assertTrue(source.contains("final Set<Integer> userIds = new LinkedHashSet<>();"));
        assertFalse(source.contains("final int[] userIds = new int[count];"));
        assertTrue(source.contains("habbo.getClient() == null"));
    }
}
