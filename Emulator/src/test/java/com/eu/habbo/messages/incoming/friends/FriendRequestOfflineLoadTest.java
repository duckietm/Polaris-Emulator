package com.eu.habbo.messages.incoming.friends;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** A friend request to an offline user built the whole account before any refusal, on every repeat. */
class FriendRequestOfflineLoadTest {

    @Test
    void theCheapRefusalsComeBeforeTheAccountIsBuilt() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/eu/habbo/messages/incoming/friends/FriendRequestEvent.java"));
        int light = source.indexOf("SELECT users.id, users_settings.block_friendrequests FROM users");
        int refusal = source.indexOf("this.refusedBeforeLoading(", light);
        int build = source.indexOf("targetHabbo = new Habbo(set);", refusal);

        assertTrue(light > 0 && refusal > light && build > refusal);
        assertTrue(source.indexOf("Messenger.friendRequested(targetId, userId)", build) > build);
    }
}
