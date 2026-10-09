package com.eu.habbo.habbohotel.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.rooms.RoomUserAction;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DanceCommandSixSevenContractTest {
    @Test
    void sixSevenIsAction67() {
        assertEquals(67, RoomUserAction.SIX_SEVEN.getAction());
        assertEquals(RoomUserAction.SIX_SEVEN, RoomUserAction.fromValue(67));
    }

    @Test
    void danceCommandHandles67BeforeTheBoundsCheck() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/commands/DanceCommand.java"));

        int sixSeven = source.indexOf("danceId == RoomUserAction.SIX_SEVEN.getAction()");
        int bounds = source.indexOf("danceId < 0 || danceId > 4");

        assertTrue(sixSeven > 0 && sixSeven < bounds, ":dance 67 must not hit the 0-4 bounds error");
        assertTrue(source.contains("new RoomUserActionComposer(habbo.getRoomUnit(), RoomUserAction.SIX_SEVEN)"));
    }
}
