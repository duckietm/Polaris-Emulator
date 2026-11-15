package polaris.hotel.messenger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import polaris.hotel.users.User;

class MessengerChannelTest {
    /** A channel that keeps what it was sent. */
    private static final class TeamChannel extends ChannelBuddy {
        final List<String> heard = new ArrayList<>();

        TeamChannel(int id) {
            super(id, "Team", "", 1);
        }

        @Override
        public void onMessageReceived(User from, String message) {
            this.heard.add(message);
        }
    }

    @Test
    void aChannelTakesNoFriendSlotAndIsFoundByItsId() {
        Messenger messenger = new Messenger();
        TeamChannel team = new TeamChannel(-2);

        messenger.addChannel(null, team);
        messenger.addBuddy(new MessengerBuddy(7, "Anna", "", (short) 0, 1));

        assertEquals(1, messenger.countedFriends());
        assertSame(team, messenger.getChannel(-2));
        assertNull(messenger.getChannel(7));
        assertNull(messenger.getChannel(-5));
    }

    @Test
    void anOrdinaryBuddyUnderANegativeIdIsNoChannel() {
        Messenger messenger = new Messenger();
        messenger.addBuddy(new MessengerBuddy(-4, "Fake", "", (short) 0, 1));

        assertNull(messenger.getChannel(-4));
        assertEquals(0, messenger.countedFriends());
    }

    @Test
    void aChannelIsRemovedOnlyAsAChannel() {
        Messenger messenger = new Messenger();
        messenger.addChannel(null, new TeamChannel(-2));
        messenger.addBuddy(new MessengerBuddy(9, "Bob", "", (short) 0, 1));

        messenger.removeChannel(null, 9);
        messenger.removeChannel(null, -2);

        assertNull(messenger.getChannel(-2));
        assertEquals(1, messenger.countedFriends());
    }

    @Test
    void aChannelIdIsNegative() {
        assertThrows(IllegalArgumentException.class, () -> new TeamChannel(3));
    }
}
