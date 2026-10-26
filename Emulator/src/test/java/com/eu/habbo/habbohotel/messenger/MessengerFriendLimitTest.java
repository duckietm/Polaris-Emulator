package com.eu.habbo.habbohotel.messenger;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.eu.habbo.habbohotel.permissions.RankLimits;
import org.junit.jupiter.api.Test;

class MessengerFriendLimitTest {
    @Test
    void staffChatTakesNoFriendSlot() {
        Messenger messenger = new Messenger();

        messenger.addBuddy(new MessengerBuddy(StaffChatBuddy.BUDDY_ID, "Staff Chat", "", (short) 0, 1));
        messenger.addBuddy(new MessengerBuddy(7, "Anna", "", (short) 0, 1));

        assertEquals(1, messenger.countedFriends());
    }

    @Test
    void offlineFriendLimitUsesTheStoredMaximum() {
        assertEquals(500, RankLimits.friends(500, null));
        assertEquals(Messenger.MAXIMUM_FRIENDS, RankLimits.friends(0, null));
    }
}
