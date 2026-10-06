package com.eu.habbo.messages.incoming.friends;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StalkFriendHiddenOnlineTest {

    @Test
    void aHiddenFriendCannotBeFollowedExceptByStaff() {
        assertTrue(StalkFriendEvent.hiddenFrom(true, false));
        assertFalse(StalkFriendEvent.hiddenFrom(true, true));
        assertFalse(StalkFriendEvent.hiddenFrom(false, false));
    }

    @Test
    void aHiddenFriendGetsTheOfflineAnswerBeforeAnyOther() throws Exception {
        String source =
                Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/friends/StalkFriendEvent.java"));

        int hidden = source.indexOf("hiddenFrom(habbo.getHabboStats().hideOnline, canStalk)");
        int offline = source.indexOf("StalkErrorComposer.FRIEND_OFFLINE", hidden);
        int blocked = source.indexOf("StalkErrorComposer.FRIEND_BLOCKED_STALKING");
        int forward = source.indexOf("new ForwardToRoomComposer(");

        assertTrue(hidden > -1 && offline > hidden, "A hidden friend must answer as offline");
        assertTrue(offline < blocked && offline < forward, "Nothing else may reveal a hidden friend first");
    }
}
