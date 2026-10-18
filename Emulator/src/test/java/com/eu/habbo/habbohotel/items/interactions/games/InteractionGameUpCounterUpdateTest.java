package com.eu.habbo.habbohotel.items.interactions.games;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** A counter change goes out as an ObjectDataUpdate with our "now TAB base" data, not as a full ObjectUpdate. */
class InteractionGameUpCounterUpdateTest {

    @Test
    void anAdjustSendsTheDataOnly() {
        Room room = mock(Room.class);
        InteractionGameUpCounter counter = new InteractionGameUpCounter(1, 1, mock(Item.class), "0", 0, 0);

        try (MockedStatic<WiredManager> ignored = mockStatic(WiredManager.class)) {
            counter.adjustCounter(room, 2, 1, 0);
        }

        verify(room).sendComposer(any(ServerMessage.class));
        verify(room, never()).updateItem(any(HabboItem.class));
        assertEquals("60", counter.getExtradata().split("\t")[0]);
    }
}
