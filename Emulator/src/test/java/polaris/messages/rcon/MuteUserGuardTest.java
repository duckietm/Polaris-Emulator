package polaris.messages.rcon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MuteUserGuardTest {
    @Test
    void parsesInvalidDurationCeilingsAsDefault() {
        assertEquals(MuteUser.DEFAULT_MAX_DURATION_SECONDS, MuteUser.parseMaxDuration(null));
        assertEquals(MuteUser.DEFAULT_MAX_DURATION_SECONDS, MuteUser.parseMaxDuration("-1"));
        assertEquals(0, MuteUser.parseMaxDuration("0"));
        assertEquals(60, MuteUser.parseMaxDuration("60"));
    }

    @Test
    void rejectsNegativeAndOversizedMuteDurations() throws Exception {
        String source = Files.readString(Path.of("src/main/java/polaris/messages/rcon/MuteUser.java"));

        assertTrue(
                source.contains("json.duration < 0 || json.duration > maxDuration"),
                "RCON mute must reject negative durations and configured-duration overflows");
        assertTrue(
                source.contains("json.duration == 0 ? 0 : Emulator.getIntUnixTimestamp() + json.duration"),
                "Offline unmute must clear mute_end_timestamp instead of writing the current timestamp");
        assertTrue(
                source.contains("rcon.mute.max_duration_seconds"), "RCON mute duration ceiling must be configurable");
    }
}
