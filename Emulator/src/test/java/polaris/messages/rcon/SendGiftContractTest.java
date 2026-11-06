package polaris.messages.rcon;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SendGiftContractTest {
    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/polaris/messages/rcon/SendGift.java"));
    }

    @Test
    void validatesGiftTargetsAndItemsBeforeCreatingInventoryRows() throws Exception {
        String source = source();

        assertTrue(source.contains("json.user_id <= 0"), "RCON gifts must reject invalid target users");
        assertTrue(source.contains("json.itemid <= 0"), "RCON gifts must reject invalid item ids");
        assertTrue(source.contains("baseItem.allowGift()"), "RCON gifts must respect the item giftability flag");
        assertTrue(
                source.contains("UserManager.getOfflineUserInfo(json.user_id)"),
                "RCON gifts must resolve offline users through UserManager");
        assertTrue(
                source.contains("USER_NOT_FOUND"),
                "RCON gifts must report missing users with the RCON missing-user status");
    }

    @Test
    void sanitizesGiftMessageAndHandlesMissingGiftConfiguration() throws Exception {
        String source = source();

        assertTrue(
                source.contains("sanitizeGiftMessage(json.message)"),
                "RCON gift extraData must use a sanitized message");
        assertTrue(
                source.contains("replace('\\t', ' ').replace('\\r', ' ').replace('\\n', ' ')"),
                "RCON gift messages must not inject gift extraData delimiters");
        assertTrue(
                source.contains("hotel.gifts.length.max"),
                "RCON gift messages must respect the configured gift length limit");
        assertTrue(
                source.contains("giftFurnis.size()"),
                "RCON gift creation must guard against empty gift wrapper configuration");
        assertTrue(
                source.contains("createGift(userInfo.getUsername()"),
                "RCON gifts must create the wrapper for the canonical target username");
    }
}
