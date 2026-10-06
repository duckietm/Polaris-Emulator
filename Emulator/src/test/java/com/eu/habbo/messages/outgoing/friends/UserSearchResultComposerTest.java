package com.eu.habbo.messages.outgoing.friends;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.messenger.MessengerBuddy;
import com.eu.habbo.messages.outgoing.Outgoing;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserSearchResultComposerTest {

    @Test
    void friendsShowTheirVisibleOnlineStateAndCanBeFollowedWhenOnline() {
        MessengerBuddy online = buddy(2, "bob", true);
        MessengerBuddy hidden = buddy(3, "alice", false);
        Set<MessengerBuddy> friends = new LinkedHashSet<>(List.of(hidden, online));

        ByteBuf payload = compose(friends, friends);

        assertEquals(2, payload.readInt());
        // Sorted by name length, then name: "bob" before "alice".
        assertResult(payload, 2, "bob", true, true, "look-2");
        assertResult(payload, 3, "alice", false, false, "look-3");
        assertEquals(0, payload.readInt());
        assertFalse(payload.isReadable());
    }

    @Test
    void nonFriendsShowOnlineOnlyWhenVisibleAndCannotBeFollowed() {
        MessengerBuddy online = buddy(4, "carl", true);
        MessengerBuddy hidden = buddy(5, "dave", false);

        ByteBuf payload = compose(new LinkedHashSet<>(List.of(online, hidden)), Set.of());

        assertEquals(0, payload.readInt());
        assertEquals(2, payload.readInt());
        assertResult(payload, 4, "carl", true, false, "look-4");
        assertResult(payload, 5, "dave", false, false, "");
        assertFalse(payload.isReadable());
    }

    private static ByteBuf compose(Set<MessengerBuddy> users, Set<MessengerBuddy> friends) {
        ByteBuf payload =
                new UserSearchResultComposer(users, friends, null).compose().get();
        payload.skipBytes(4);
        assertEquals(Outgoing.UserSearchResultComposer, payload.readUnsignedShort());
        return payload;
    }

    private static MessengerBuddy buddy(int id, String name, boolean visibleOnline) {
        MessengerBuddy buddy = mock(MessengerBuddy.class);
        when(buddy.getId()).thenReturn(id);
        when(buddy.getUsername()).thenReturn(name);
        when(buddy.getMotto()).thenReturn("motto");
        when(buddy.getLook()).thenReturn("look-" + id);
        when(buddy.isVisibleOnline()).thenReturn(visibleOnline);
        return buddy;
    }

    private static void assertResult(
            ByteBuf payload, int id, String name, boolean online, boolean followAllowed, String look) {
        assertEquals(id, payload.readInt());
        assertEquals(name, readString(payload));
        assertEquals("motto", readString(payload));
        assertEquals(online, payload.readBoolean());
        assertEquals(followAllowed, payload.readBoolean());
        assertEquals("", readString(payload));
        assertEquals(1, payload.readInt());
        assertEquals(look, readString(payload));
        assertEquals("", readString(payload));
    }

    private static String readString(ByteBuf payload) {
        int length = payload.readUnsignedShort();
        return payload.readCharSequence(length, StandardCharsets.UTF_8).toString();
    }
}
