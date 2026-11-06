package polaris.hotel.items.interactions.wired.triggers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static polaris.hotel.items.interactions.wired.triggers.WiredTriggerTestSupport.boxBase;
import static polaris.hotel.items.interactions.wired.triggers.WiredTriggerTestSupport.room;

import org.junit.jupiter.api.Test;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomChatMessage;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.wired.core.WiredEvent;

/** Room chat is cut to the chat length (a full line stays whole), and a full-length keyword still fires. */
class WiredTriggerUserSaysKeywordLengthTest {

    @Test
    void chatIsCutToTheChatLengthAndAFullLineStaysWhole() {
        int previous = RoomChatMessage.MAXIMUM_LENGTH;
        RoomChatMessage.MAXIMUM_LENGTH = 100;
        try {
            String line = "x".repeat(250);
            assertEquals(line.substring(0, 100), RoomChatMessage.truncateToMaximumLength(line));
            assertEquals("y".repeat(100), RoomChatMessage.truncateToMaximumLength("y".repeat(100)));
            assertEquals("short", RoomChatMessage.truncateToMaximumLength("short"));
        } finally {
            RoomChatMessage.MAXIMUM_LENGTH = previous;
        }
    }

    @Test
    void aLongerKeywordIsKeptAsAFullLineAndFiresOnIt() {
        int previous = RoomChatMessage.MAXIMUM_LENGTH;
        RoomChatMessage.MAXIMUM_LENGTH = 200;
        WiredTriggerUserSaysKeyword trigger = new WiredTriggerUserSaysKeyword(1, 1, boxBase(), "", 0, 0);
        try {
            trigger.saveData(new WiredSettings(new int[] {0, 0, 0}, "k".repeat(300), new int[0], 0));
        } finally {
            RoomChatMessage.MAXIMUM_LENGTH = previous;
        }

        assertEquals(200, trigger.getKey().length());

        Room room = room(1);
        WiredEvent said = WiredEvent.builder(WiredEvent.Type.USER_SAYS, room)
                .actor(mock(RoomUnit.class))
                .text("k".repeat(200))
                .build();
        assertTrue(trigger.matches(null, said));
    }
}
