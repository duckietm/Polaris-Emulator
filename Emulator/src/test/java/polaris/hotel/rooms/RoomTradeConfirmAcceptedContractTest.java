package polaris.hotel.rooms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The confirm handler checked "accepted" outside the trade's lock, so a changed offer could still be confirmed. */
class RoomTradeConfirmAcceptedContractTest {

    @Test
    void theAcceptanceIsCheckedInsideTheSynchronizedConfirm() throws Exception {
        String trade = Files.readString(Path.of("src/main/java/polaris/hotel/rooms/RoomTrade.java"));
        String confirm = trade.substring(trade.indexOf("public synchronized void confirm(User trader)"));

        int check = confirm.indexOf("if (user == null || !user.getAccepted()) return;");
        assertTrue(check > 0 && check < confirm.indexOf("user.confirm();"));
    }

    @Test
    void theHandlerLeavesTheCheckToTheTrade() throws Exception {
        String handler =
                Files.readString(Path.of("src/main/java/polaris/messages/incoming/trading/TradeConfirmEvent.java"));

        assertFalse(handler.contains("getRoomTradeUserForUser(user).getAccepted()"));
    }
}
