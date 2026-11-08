package polaris.hotel.items.interactions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;

/** The add counter sums number signs, one per user, and survives malformed stored data. */
class InteractionVoteCounterTest {

    private static InteractionVoteCounter counter(String extradata) {
        return new InteractionVoteCounter(1, 1, mock(Item.class), extradata, 0, 0);
    }

    @Test
    void malformedStoredVotesStartAFrozenCounterAtZero() {
        assertEquals(0, counter("0,abc").getVotes());
        assertEquals(0, counter("1,").getVotes());
        assertEquals("1,0", counter("garbage").getExtradata());
        assertEquals(7, counter("0,7").getVotes());
    }

    @Test
    void numberSignsAreSummedOncePerUser() {
        InteractionVoteCounter counter = counter("0,0");
        Room room = mock(Room.class);

        counter.vote(room, 10, 7);
        counter.vote(room, 10, 9);
        counter.vote(room, 11, 3);

        assertEquals(10, counter.getVotes());
        assertEquals("0,10", counter.getExtradata());
    }

    @Test
    void symbolSignsAreNotVotes() {
        InteractionVoteCounter counter = counter("0,0");
        Room room = mock(Room.class);

        counter.vote(room, 10, 11);
        counter.vote(room, 10, 17);

        assertEquals(0, counter.getVotes());
        verify(room, never()).updateItem(counter);

        counter.vote(room, 10, 4);
        assertEquals(4, counter.getVotes());
    }

    @Test
    void aFrozenCounterIgnoresVotes() {
        InteractionVoteCounter counter = counter("1,2");

        counter.vote(mock(Room.class), 10, 5);

        assertEquals(2, counter.getVotes());
    }
}
