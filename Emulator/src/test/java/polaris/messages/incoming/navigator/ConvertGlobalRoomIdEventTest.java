package polaris.messages.incoming.navigator;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ConvertGlobalRoomIdEventTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/" + path));
    }

    @Test
    void theHeaderMatchesTheRendererAndIsRegistered() throws Exception {
        assertTrue(source("polaris/messages/incoming/Incoming.java").contains("ConvertGlobalRoomIdEvent = 314"));
        assertTrue(source("polaris/messages/PacketManager.java")
                .contains("Incoming.ConvertGlobalRoomIdEvent, ConvertGlobalRoomIdEvent.class"));
    }

    @Test
    void anIdGoesStraightThroughAndANameMustMatchExactly() throws Exception {
        String handler = source("polaris/messages/incoming/navigator/ConvertGlobalRoomIdEvent.java");

        assertTrue(handler.contains("NavigatorInputGuard.normalizeSearch(this.packet.readString())"));
        assertTrue(
                handler.contains("findVisibleRoomIdByName(target)"), "the exact-name match now lives in RoomManager");
        assertTrue(handler.contains("new ForwardToRoomComposer(roomId)"));
        assertTrue(handler.contains("return 1000;"));
    }
}
