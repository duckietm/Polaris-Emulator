package com.eu.habbo.habbohotel.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.commands.ShutdownCommand.Action;
import com.eu.habbo.habbohotel.commands.ShutdownCommand.Request;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ShutdownCommandArgumentsTest {
    private static Request game(String line) {
        return ShutdownCommand.parse(line.split(" "), false);
    }

    private static Request console(String line) {
        return ShutdownCommand.parse(line.split(" "), true);
    }

    @Test
    void minutesFollowedByAReasonKeepBoth() {
        Request request = game("shutdown 5 Server update tonight");

        assertEquals(Action.SCHEDULE, request.action());
        assertEquals(5, request.minutes());
        assertEquals("Server update tonight", request.reason());
    }

    @Test
    void minutesWithoutReasonSchedulesWithoutReason() {
        Request request = game("shutdown 10");

        assertEquals(Action.SCHEDULE, request.action());
        assertEquals(10, request.minutes());
        assertNull(request.reason());
    }

    @Test
    void bareShutdownInGameOnlyShowsUsage() {
        assertEquals(Action.USAGE, game("shutdown").action());
    }

    @Test
    void zeroNegativeOrHugeMinutesShowUsage() {
        assertEquals(Action.USAGE, game("shutdown 0").action());
        assertEquals(Action.USAGE, game("shutdown 0 reason").action());
        assertEquals(Action.USAGE, game("shutdown -5").action());
        assertEquals(
                Action.USAGE,
                game("shutdown " + (ShutdownCommand.MAX_MINUTES + 1)).action());
        assertEquals(Action.USAGE, game("shutdown 99999999999999").action());
        assertEquals(
                ShutdownCommand.MAX_MINUTES,
                game("shutdown " + ShutdownCommand.MAX_MINUTES).minutes());
    }

    @Test
    void aReasonWithoutMinutesNeedsAnExplicitDelayInGame() {
        Request request = game("shutdown Server update");

        assertEquals(Action.USAGE, request.action());
        assertEquals("Server update", request.reason());
    }

    @Test
    void nowShutsDownImmediatelyWithOptionalReason() {
        assertEquals(Action.NOW, game("shutdown now").action());
        assertNull(game("shutdown now").reason());

        Request request = game("shutdown NOW broken database");
        assertEquals(Action.NOW, request.action());
        assertEquals(0, request.minutes());
        assertEquals("broken database", request.reason());
    }

    @Test
    void cancelIsRecognised() {
        assertEquals(Action.CANCEL, game("shutdown cancel").action());
        assertEquals(Action.CANCEL, game("shutdown Cancel").action());
        assertEquals(Action.CANCEL, console("stop cancel").action());
    }

    @Test
    void theConsoleStopKeepsStoppingAtOnce() {
        assertEquals(Action.NOW, console("stop").action());
        assertEquals(Action.NOW, ShutdownCommand.parse(new String[0], true).action());

        Request reason = console("stop maintenance");
        assertEquals(Action.NOW, reason.action());
        assertEquals("maintenance", reason.reason());

        Request delayed = console("stop 3 maintenance");
        assertEquals(Action.SCHEDULE, delayed.action());
        assertEquals(3, delayed.minutes());
    }

    @Test
    void cancelKeepsTheScheduledTaskAndRestoresTrading() throws Exception {
        String source =
                Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/commands/ShutdownCommand.java"));

        assertTrue(source.contains("private ScheduledFuture<?> pending;"));
        assertTrue(
                source.contains("this.pending = Emulator.getThreading().run(new ShutdownEmulator(message, deadline)"));
        assertTrue(source.contains("this.pending.cancel(false);"));
        assertTrue(source.contains("ShutdownEmulator.clearPending();"));
        assertTrue(source.contains("RoomTrade.TRADING_ENABLED = configuration == null"));
    }
}
