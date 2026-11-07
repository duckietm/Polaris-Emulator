package polaris.threading.runnables;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import polaris.Emulator;
import polaris.hotel.GameEnvironment;
import polaris.hotel.items.interactions.InteractionYoutubeTV;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomManager;
import polaris.messages.ServerMessage;

class YoutubeAdvanceVideoTest {
    private Field environmentField;
    private Object originalEnvironment;
    private Room room;
    private InteractionYoutubeTV tv;

    @BeforeEach
    void setUp() throws Exception {
        this.room = mock(Room.class);
        RoomManager rooms = mock(RoomManager.class);
        when(rooms.getRoom(12)).thenReturn(this.room);
        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getRoomManager()).thenReturn(rooms);
        this.environmentField = Emulator.class.getDeclaredField("gameEnvironment");
        this.environmentField.setAccessible(true);
        this.originalEnvironment = this.environmentField.get(null);
        this.environmentField.set(null, environment);

        this.tv = mock(InteractionYoutubeTV.class);
        when(this.tv.getId()).thenReturn(55);
        when(this.tv.getRoomId()).thenReturn(12);
        this.tv.autoAdvance = mock(ScheduledFuture.class);
    }

    @AfterEach
    void restore() throws IllegalAccessException {
        this.environmentField.set(null, this.originalEnvironment);
    }

    @Test
    void anUnloadedRoomStopsTheChain() {
        when(this.room.isLoaded()).thenReturn(false);

        // The chain rescheduled itself forever after the room went idle, keeping TV and room alive.
        new YoutubeAdvanceVideo(this.tv).run();

        assertNull(this.tv.autoAdvance);
        verify(this.room, never()).sendComposer(any(ServerMessage.class));
    }

    @Test
    void aReloadedRoomWithANewTvStopsTheOldChain() {
        when(this.room.isLoaded()).thenReturn(true);
        when(this.room.getUserItem(55)).thenReturn(mock(InteractionYoutubeTV.class));

        // After a reload the old chain changed the video of the new TV the visitors were watching.
        new YoutubeAdvanceVideo(this.tv).run();

        assertNull(this.tv.autoAdvance);
        verify(this.room, never()).sendComposer(any(ServerMessage.class));
    }
}
