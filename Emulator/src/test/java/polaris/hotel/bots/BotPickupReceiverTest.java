package polaris.hotel.bots;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * A room owner (or staff) picking up a visitor's bot wrote the visitor as its owner but put the bot in
 * the picker's inventory: two copies of the same bot id, one of them in the wrong inventory.
 */
class BotPickupReceiverTest {

    @Test
    void thePickedUpBotGoesToItsOwnersInventory() throws Exception {
        String source = Files.readString(Path.of("src/main/java/polaris/hotel/bots/BotManager.java"))
                .replaceAll("\\s+", " ");

        assertTrue(
                source.contains(
                        "User receiver = receiverInfo.getId() == (user == null ? -1 : user.getUserInfo().getId())"
                                + " ? user : Emulator.getGameEnvironment().getUserManager().getUser(receiverInfo.getId());"),
                "the receiver is the owner, online, and the picker only when they are the owner");
        assertFalse(source.contains(": user; if (receiver != null)"), "the picker is no longer the default receiver");
    }
}
