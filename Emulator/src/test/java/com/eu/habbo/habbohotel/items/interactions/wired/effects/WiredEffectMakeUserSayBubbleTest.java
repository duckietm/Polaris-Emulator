package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.RoomChatMessage;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.ChatStyleRepository;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Make User Say sends its line without a speaking Habbo, so every bubble check chat applies at send time
 * was skipped: a room owner could make a visitor say anything in the staff or ambassador bubble, or in a
 * catalog style nobody bought.
 */
class WiredEffectMakeUserSayBubbleTest {

    private static final int WIRED = RoomChatMessageBubbles.WIRED.getType();
    private static final int STAFF = RoomChatMessageBubbles.STAFF.getType();
    private static final int AMBASSADOR = RoomChatMessageBubbles.AMBASSADOR.getType();
    private static final int SOLD_STYLE = 300;

    private static Habbo editor(String... permissions) {
        HabboInfo info = mock(HabboInfo.class);
        when(info.getId()).thenReturn(5);
        Habbo habbo = mock(Habbo.class);
        when(habbo.getHabboInfo()).thenReturn(info);
        for (String permission : permissions)
            when(habbo.hasPermission(permission)).thenReturn(true);
        return habbo;
    }

    private static int allowed(int style, Habbo editor) {
        int[] banned = RoomChatMessage.BANNED_BUBBLES;
        RoomChatMessage.BANNED_BUBBLES = new int[] {STAFF};
        try (MockedStatic<ChatStyleRepository> styles = mockStatic(ChatStyleRepository.class)) {
            styles.when(() -> ChatStyleRepository.isPurchasable(anyInt()))
                    .thenAnswer(i -> i.<Integer>getArgument(0) == SOLD_STYLE);
            styles.when(() -> ChatStyleRepository.owns(anyInt(), anyInt())).thenReturn(false);
            return WiredEffectMakeUserSay.allowedBubble(style, editor);
        } finally {
            RoomChatMessage.BANNED_BUBBLES = banned;
        }
    }

    @Test
    void reservedBubblesFallBackToTheWiredOne() {
        Habbo owner = editor();

        assertEquals(WIRED, allowed(STAFF, owner), "a banned bubble");
        assertEquals(WIRED, allowed(AMBASSADOR, owner), "a bubble behind a permission");
        assertEquals(WIRED, allowed(SOLD_STYLE, owner), "a catalog style the editor does not own");
        assertEquals(WIRED, allowed(5000, owner), "an unknown id");
    }

    @Test
    void anOrdinaryBubbleIsKept() {
        assertEquals(
                RoomChatMessageBubbles.NORMAL.getType(), allowed(RoomChatMessageBubbles.NORMAL.getType(), editor()));
    }

    @Test
    void anEditorWithThePermissionsKeepsThem() {
        assertEquals(AMBASSADOR, allowed(AMBASSADOR, editor(Permission.ACC_AMBASSADOR)));
        assertEquals(STAFF, allowed(STAFF, editor(Permission.ACC_ANYCHATCOLOR)));
    }
}
