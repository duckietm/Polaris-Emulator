package polaris.hotel.modtool;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** bans.timestamp is NOT NULL without a default: the ban of an offline user has to write it like any other ban. */
class OfflineBanTimestampTest {

    @Test
    void anOfflineBanWritesWhenItWasGiven() throws Exception {
        String manager = Files.readString(Path.of("src/main/java/polaris/hotel/modtool/ModToolManager.java"));
        int start = manager.indexOf("public ModToolBan createOfflineUserBan(");
        int end = manager.indexOf("public void alert(", start);

        assertTrue(start > 0 && end > start);

        String body = manager.substring(start, end);

        assertTrue(body.contains(
                "INSERT INTO bans (user_id, ip, machine_id, user_staff_id, timestamp, ban_expire, ban_reason, type)"));
        assertTrue(body.contains("statement.setInt(5, now);"));
        assertTrue(body.contains("statement.setInt(6, banExpiry(now, duration));"));
        assertTrue(body.contains("statement.setString(8, type.getType());"));
    }
}
