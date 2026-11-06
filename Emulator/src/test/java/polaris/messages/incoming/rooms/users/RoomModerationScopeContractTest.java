package polaris.messages.incoming.rooms.users;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RoomModerationScopeContractTest {
    @Test
    void roomUserBanAndMuteAreScopedToCurrentRoom() throws Exception {
        Path base = Path.of("src/main/java/polaris/messages/incoming/rooms/users");

        for (String handler :
                new String[] {"RoomUserBanEvent.java", "RoomUserMuteEvent.java", "UnbanRoomUserEvent.java"}) {
            String source = Files.readString(base.resolve(handler));

            assertTrue(
                    source.contains("getCurrentRoom()"),
                    handler + " must authorize room moderation against the user's current room");
            assertTrue(
                    source.contains("room.getId() != roomId"),
                    handler + " must reject client-supplied room ids that do not match the current room");
        }
    }
}
