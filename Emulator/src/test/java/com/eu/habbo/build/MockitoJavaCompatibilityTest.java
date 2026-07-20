package com.eu.habbo.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.rooms.Room;
import org.junit.jupiter.api.Test;

class MockitoJavaCompatibilityTest {

    @Test
    void mocksConcretePolarisTypesOnTheRequiredJdk() {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(7);

        assertEquals(7, room.getId());
    }
}
