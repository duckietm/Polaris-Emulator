package polaris.messages.rcon;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GiveBadgeContractTest {
    private static String giveBadgeSource() throws Exception {
        return Files.readString(Path.of("src/main/java/polaris/messages/rcon/GiveBadge.java"));
    }

    @Test
    void offlineBadgeGrantRequiresExistingUserBeforeInsert() throws Exception {
        String source = giveBadgeSource();

        assertTrue(
                source.contains("UserManager.getOfflineUserInfo(json.user_id)"),
                "Offline RCON badge grants must verify the target user exists");
        assertTrue(
                source.contains("RCONMessage.USER_NOT_FOUND"), "Offline RCON badge grants must report missing users");
        assertFalse(
                source.contains("(SELECT id FROM users WHERE users.id = ? LIMIT 1)"),
                "Offline RCON badge grants must not insert through a nullable user subquery");
    }
}
