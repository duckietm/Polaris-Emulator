package polaris.hotel.items.interactions.wired.chest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.core.WiredManager;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.rooms.items.WiredTransactionFailComposer;

/**
 * Official chest behaviour: an upgrade leaves the chest locked, a refused withdrawal says why (12 not
 * a wired chest, 15 locked or no rights), and using a chest only opens it.
 */
class ChestOfficialRulesTest {
    private static final int OWNER = 2;

    private static User user(int id) {
        User user = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        GameClient client = mock(GameClient.class);
        when(info.getId()).thenReturn(id);
        when(user.getUserInfo()).thenReturn(info);
        when(user.getClient()).thenReturn(client);
        when(user.getRoomUnit()).thenReturn(mock(RoomUnit.class));
        when(client.getUser()).thenReturn(user);
        return user;
    }

    private static InteractionWiredChestCurrency currencyChest() {
        return new InteractionWiredChestCurrency(1, OWNER, mock(Item.class), "", 0, 0);
    }

    // --- upgrade locks --------------------------------------------------------------------------

    @Test
    void anUpgradedChestStartsLocked() {
        ChestStorage contents = new ChestStorage();
        assertFalse(contents.isLocked(), "a storage chest starts unlocked");

        contents.enableWired();

        assertTrue(contents.isWiredEnabled());
        assertTrue(contents.isLocked(), "the upgrade leaves the chest locked");
    }

    @Test
    void anUnlockedWiredChestIsNotRelockedByAnotherUpgradeRequest() {
        ChestStorage contents = new ChestStorage();
        contents.enableWired();
        contents.setLocked(false);

        contents.enableWired();

        assertFalse(contents.isLocked());
    }

    // --- withdraw refusals ----------------------------------------------------------------------

    @Test
    void aWithdrawalFromSomethingThatIsNoWiredChestIsFailure12() {
        User user = user(OWNER);
        assertEquals(
                ChestTransactionFailures.NOT_A_WIRED_CHEST,
                ChestTransactionFailures.withdrawRefusal(mock(UserItem.class), user, true));
        assertEquals(
                ChestTransactionFailures.NOT_A_WIRED_CHEST, ChestTransactionFailures.withdrawRefusal(null, user, true));
    }

    @Test
    void noRightsOrALockFromSomeoneElseIsFailure15() {
        InteractionWiredChestCurrency chest = currencyChest();
        User visitor = user(9);

        assertEquals(
                ChestTransactionFailures.NO_OR_LOCKED_CHESTS,
                ChestTransactionFailures.withdrawRefusal(chest, visitor, false));

        chest.getContents().setLocked(true);
        assertEquals(
                ChestTransactionFailures.NO_OR_LOCKED_CHESTS,
                ChestTransactionFailures.withdrawRefusal(chest, visitor, true));
        assertEquals(
                ChestTransactionFailures.NONE,
                ChestTransactionFailures.withdrawRefusal(chest, user(OWNER), true),
                "the owner is never locked out of their own chest");
    }

    @Test
    void aRefusalReachesThePlayerAsTheTransactionFailPacket() throws Exception {
        User user = user(OWNER);

        ChestTransactionFailures.send(user, ChestTransactionFailures.NOT_A_WIRED_CHEST);

        ArgumentCaptor<MessageComposer> sent = ArgumentCaptor.forClass(MessageComposer.class);
        verify(user.getClient()).sendResponse(sent.capture());
        assertTrue(sent.getValue() instanceof WiredTransactionFailComposer, "AIR WiredTransactionFail");
        assertEquals(12, ((WiredTransactionFailComposer) sent.getValue()).getReason());

        User quiet = user(OWNER);
        ChestTransactionFailures.send(quiet, ChestTransactionFailures.NONE);
        verify(quiet.getClient(), never()).sendResponse(any(MessageComposer.class));
    }

    @Test
    void everyWithdrawHandlerAnswersItsRefusal() throws Exception {
        for (String handler :
                new String[] {"ChestWithdrawEvent", "ChestWithdrawFurniEvent", "ChestWithdrawAllFurniEvent"}) {
            String source = Files.readString(
                    Path.of("src/main/java/polaris/messages/incoming/rooms/items/" + handler + ".java"));
            assertTrue(
                    source.contains("ChestTransactionFailures.withdrawRefusal(item, user, room.hasRights(user))"),
                    handler);
            assertTrue(source.contains("ChestTransactionFailures.send(user, refusal)"), handler);
        }
    }

    // --- using a chest only opens it ------------------------------------------------------------

    @Test
    void usingAChestDoesNotFireTheStateChangedTriggers() throws Exception {
        User user = user(OWNER);
        Room room = mock(Room.class);
        when(room.hasRights(user)).thenReturn(true);

        InteractionWiredChest[] chests = {
            currencyChest(), new InteractionWiredChestFurni(3, OWNER, mock(Item.class), "", 0, 0)
        };

        try (MockedStatic<WiredManager> wired = mockStatic(WiredManager.class, CALLS_REAL_METHODS)) {
            for (InteractionWiredChest chest : chests) {
                // Keep the lid where it is, so opening sends no room-wide furni update.
                chest.getContents().setAppearanceState(InteractionWiredChest.APPEARANCE_ALWAYS_CLOSED);
                chest.onClick(user.getClient(), room, new Object[] {0});
            }

            wired.verify(() -> WiredManager.triggerFurniStateChanged(any(), any(), any()), never());
            wired.verify(() -> WiredManager.triggerFurniStateUpdated(any(), any(), any(), anyBoolean()), never());
        }

        // It did open: the window was sent for both chests.
        verify(user.getClient(), org.mockito.Mockito.atLeast(2)).sendResponse(any(MessageComposer.class));
    }

    @Test
    void theOwnerOpensTheirOwnClosedChestWithoutRightsInTheRoom() throws Exception {
        User owner = user(OWNER);
        User visitor = user(9);
        Room room = mock(Room.class);

        InteractionWiredChest[] chests = {
            currencyChest(), new InteractionWiredChestFurni(3, OWNER, mock(Item.class), "", 0, 0)
        };
        for (InteractionWiredChest chest : chests) {
            // Closed to the room: only the owner and players with rights may look inside (AIR).
            chest.getContents().setAccessOpen(false);
            chest.onClick(owner.getClient(), room, new Object[] {0});
            chest.onClick(visitor.getClient(), room, new Object[] {0});
        }

        verify(owner.getClient(), org.mockito.Mockito.times(2))
                .sendResponse(org.mockito.ArgumentMatchers.isA(
                        polaris.messages.outgoing.rooms.items.ChestOpenComposer.class));
        verify(visitor.getClient(), never()).sendResponse(any(MessageComposer.class));
    }

    @Test
    void aPlayerWhoMayNotTradeIsRefusedADepositWithReasonEight() {
        User locked = user(OWNER);
        polaris.hotel.users.UserStats stats = mock(polaris.hotel.users.UserStats.class);
        when(locked.getUserStats()).thenReturn(stats);
        InteractionWiredChestCurrency chest = currencyChest();

        assertEquals(
                ChestTransactionFailures.USER_CANT_TRADE, ChestTransactionFailures.depositRefusal(chest, locked, true));
        chest.getContents().setAccessDonate(true);
        assertEquals(
                ChestTransactionFailures.USER_CANT_TRADE,
                ChestTransactionFailures.depositRefusal(chest, locked, false));
        assertFalse(ChestTransactionFailures.countsOnMonitor(ChestTransactionFailures.USER_CANT_TRADE));

        when(stats.allowTrade()).thenReturn(true);
        assertEquals(ChestTransactionFailures.NONE, ChestTransactionFailures.depositRefusal(chest, locked, false));
    }
}
