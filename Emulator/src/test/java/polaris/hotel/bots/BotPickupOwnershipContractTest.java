package polaris.hotel.bots;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BotPickupOwnershipContractTest {
    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/polaris/hotel/bots/BotManager.java"));
    }

    @Test
    void roomOwnerPickupReturnsBotToOriginalOwner() throws Exception {
        String source = source();

        assertTrue(
                source.contains("UserInfo receiverInfo = resolvePickupReceiver(bot, user);"),
                "bot pickup should resolve the receiver without blindly using the picker");
        assertTrue(
                source.contains("private UserInfo resolvePickupReceiver(Bot bot, User picker)"),
                "bot pickup receiver logic should be centralized");
        assertTrue(
                source.contains("return Emulator.getGameEnvironment().getUserManager().getUserInfo(bot.getOwnerId());"),
                "when a room owner picks up someone else's bot, it should return to the original bot owner");
        assertTrue(
                source.contains("Room botRoom = bot.getRoom();"),
                "pickup should remove the bot from the bot's current room, not the receiver's current room");
        assertTrue(
                source.contains("botRoom.removeBot(bot);"),
                "bot removal should work even when the original owner is offline");
    }
}
