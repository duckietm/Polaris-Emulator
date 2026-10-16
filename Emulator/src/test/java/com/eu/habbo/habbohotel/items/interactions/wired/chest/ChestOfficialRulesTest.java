package com.eu.habbo.habbohotel.items.interactions.wired.chest;

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

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.rooms.items.WiredTradeCancelledComposer;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

/**
 * Official chest behaviour: an upgrade leaves the chest locked, a refused withdrawal says why (12 not
 * a wired chest, 15 locked or no rights), and using a chest only opens it.
 */
class ChestOfficialRulesTest {
    private static final int OWNER = 2;

    private static Habbo habbo(int id) {
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        GameClient client = mock(GameClient.class);
        when(info.getId()).thenReturn(id);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(habbo.getClient()).thenReturn(client);
        when(habbo.getRoomUnit()).thenReturn(mock(RoomUnit.class));
        when(client.getHabbo()).thenReturn(habbo);
        return habbo;
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
        Habbo habbo = habbo(OWNER);
        assertEquals(
                ChestTransactionFailures.NOT_A_WIRED_CHEST,
                ChestTransactionFailures.withdrawRefusal(mock(HabboItem.class), habbo, true));
        assertEquals(
                ChestTransactionFailures.NOT_A_WIRED_CHEST,
                ChestTransactionFailures.withdrawRefusal(null, habbo, true));
    }

    @Test
    void noRightsOrALockFromSomeoneElseIsFailure15() {
        InteractionWiredChestCurrency chest = currencyChest();
        Habbo visitor = habbo(9);

        assertEquals(
                ChestTransactionFailures.NO_OR_LOCKED_CHESTS,
                ChestTransactionFailures.withdrawRefusal(chest, visitor, false));

        chest.getContents().setLocked(true);
        assertEquals(
                ChestTransactionFailures.NO_OR_LOCKED_CHESTS,
                ChestTransactionFailures.withdrawRefusal(chest, visitor, true));
        assertEquals(
                ChestTransactionFailures.NONE,
                ChestTransactionFailures.withdrawRefusal(chest, habbo(OWNER), true),
                "the owner is never locked out of their own chest");
    }

    @Test
    void aRefusalReachesThePlayerAsTheTransactionFailPacket() throws Exception {
        Habbo habbo = habbo(OWNER);

        ChestTransactionFailures.send(habbo, ChestTransactionFailures.NOT_A_WIRED_CHEST);

        ArgumentCaptor<MessageComposer> sent = ArgumentCaptor.forClass(MessageComposer.class);
        verify(habbo.getClient()).sendResponse(sent.capture());
        assertTrue(sent.getValue() instanceof WiredTradeCancelledComposer);
        Field field = WiredTradeCancelledComposer.class.getDeclaredField("failureId");
        field.setAccessible(true);
        assertEquals(12, field.getInt(sent.getValue()));

        Habbo quiet = habbo(OWNER);
        ChestTransactionFailures.send(quiet, ChestTransactionFailures.NONE);
        verify(quiet.getClient(), never()).sendResponse(any(MessageComposer.class));
    }

    @Test
    void everyWithdrawHandlerAnswersItsRefusal() throws Exception {
        for (String handler :
                new String[] {"ChestWithdrawEvent", "ChestWithdrawFurniEvent", "ChestWithdrawAllFurniEvent"}) {
            String source = Files.readString(
                    Path.of("src/main/java/com/eu/habbo/messages/incoming/rooms/items/" + handler + ".java"));
            assertTrue(
                    source.contains("ChestTransactionFailures.withdrawRefusal(item, habbo, room.hasRights(habbo))"),
                    handler);
            assertTrue(source.contains("ChestTransactionFailures.send(habbo, refusal)"), handler);
        }
    }

    // --- using a chest only opens it ------------------------------------------------------------

    @Test
    void usingAChestDoesNotFireTheStateChangedTriggers() throws Exception {
        Habbo habbo = habbo(OWNER);
        Room room = mock(Room.class);
        when(room.hasRights(habbo)).thenReturn(true);

        InteractionWiredChest[] chests = {
            currencyChest(), new InteractionWiredChestFurni(3, OWNER, mock(Item.class), "", 0, 0)
        };

        try (MockedStatic<WiredManager> wired = mockStatic(WiredManager.class, CALLS_REAL_METHODS)) {
            for (InteractionWiredChest chest : chests) {
                // Keep the lid where it is, so opening sends no room-wide furni update.
                chest.getContents().setAppearanceState(InteractionWiredChest.APPEARANCE_ALWAYS_CLOSED);
                chest.onClick(habbo.getClient(), room, new Object[] {0});
            }

            wired.verify(() -> WiredManager.triggerFurniStateChanged(any(), any(), any()), never());
            wired.verify(() -> WiredManager.triggerFurniStateUpdated(any(), any(), any(), anyBoolean()), never());
        }

        // It did open: the window was sent for both chests.
        verify(habbo.getClient(), org.mockito.Mockito.atLeast(2)).sendResponse(any(MessageComposer.class));
    }
}
