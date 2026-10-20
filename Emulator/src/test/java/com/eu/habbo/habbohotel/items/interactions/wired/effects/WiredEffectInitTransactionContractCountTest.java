package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.base;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.row;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestTransactionFailures;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContract;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContract.Term;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.rooms.items.WiredTransactionFailComposer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** AIR's Initiate Transaction runs exactly one contract; anything else fails with reason 21. */
class WiredEffectInitTransactionContractCountTest {
    private final List<WiredEvent> events = new ArrayList<>();
    private final Room room = mock(Room.class);
    private final RoomUnit unit = mock(RoomUnit.class);
    private final Habbo habbo = WiredEffectTestFixtures.habbo("Trader", this.unit);

    WiredEffectInitTransactionContractCountTest() {
        when(this.room.getLayout()).thenReturn(mock(RoomLayout.class));
        when(this.room.getHabbo(this.unit)).thenReturn(this.habbo);
        for (int id : new int[] {5, 6}) {
            InteractionWiredContract contract = mock(InteractionWiredContract.class);
            when(contract.getId()).thenReturn(id);
            when(this.room.getHabboItem(id)).thenReturn(contract);
        }
        InteractionWiredContract payment = mock(InteractionWiredContract.class);
        when(payment.getId()).thenReturn(7);
        when(payment.contractType()).thenReturn(InteractionWiredContract.TYPE_PAYMENT);
        when(payment.giveRulesFor(any(), any()))
                .thenReturn(List.of(List.of(Term.currency(InteractionWiredContract.DIR_PAY, -1, 30))));
        when(payment.getRuleFor(any(), any())).thenReturn(List.of());
        when(this.room.getHabboItem(7)).thenReturn(payment);
    }

    private WiredEffectInitTransaction box(String contractIds) throws Exception {
        WiredEffectInitTransaction box = new WiredEffectInitTransaction(1, 1, base(), "", 0, 0) {
            @Override
            void dispatch(WiredEvent event) {
                WiredEffectInitTransactionContractCountTest.this.events.add(event);
            }
        };
        box.loadWiredData(row("{\"contractIds\":" + contractIds + ",\"chestIds\":[]}"), null);
        return box;
    }

    private void run(WiredEffectInitTransaction box) {
        box.execute(WiredEffectTestFixtures.context(this.room, this.unit));
    }

    private void assertFailedWith21() {
        assertEquals(1, this.events.size());
        assertEquals(WiredEvent.Type.TRANSACTION_FAIL, this.events.get(0).getType());
        assertEquals(
                ChestTransactionFailures.MISCONFIG_TOO_MANY_OR_NO_CONTRACTS,
                this.events.get(0).getTransactionFailureReason());

        ArgumentCaptor<MessageComposer> sent = ArgumentCaptor.forClass(MessageComposer.class);
        verify(this.habbo.getClient()).sendResponse(sent.capture());
        assertTrue(sent.getValue() instanceof WiredTransactionFailComposer);
        assertEquals(21, ((WiredTransactionFailComposer) sent.getValue()).getReason());
    }

    @Test
    void severalContractsAreNoLongerPooledAndFailWith21() throws Exception {
        this.run(this.box("[5,6]"));

        this.assertFailedWith21();
    }

    @Test
    void noContractFailsWith21InsteadOfCompleting() throws Exception {
        this.run(this.box("[]"));

        this.assertFailedWith21();
    }

    @Test
    void aPaymentWithoutAUsableChestFailsWith15AndRunsTheFailedStack() throws Exception {
        // It used to tell the player (15) but never raised Transaction Failed, so the room's failure stack stayed
        // quiet.
        this.run(this.box("[7]"));

        assertEquals(1, this.events.size());
        assertEquals(WiredEvent.Type.TRANSACTION_FAIL, this.events.get(0).getType());
        assertEquals(
                ChestTransactionFailures.NO_OR_LOCKED_CHESTS, this.events.get(0).getTransactionFailureReason());

        ArgumentCaptor<MessageComposer> sent = ArgumentCaptor.forClass(MessageComposer.class);
        verify(this.habbo.getClient()).sendResponse(sent.capture());
        assertEquals(
                ChestTransactionFailures.NO_OR_LOCKED_CHESTS,
                ((WiredTransactionFailComposer) sent.getValue()).getReason());
    }

    @Test
    void reason21IsASetupErrorForTheMonitor() {
        assertEquals(21, ChestTransactionFailures.MISCONFIG_TOO_MANY_OR_NO_CONTRACTS);
        assertTrue(ChestTransactionFailures.countsOnMonitor(21));
        assertEquals(ChestTransactionFailures.NONE, WiredEffectInitTransaction.contractCountRefusal(1));
        assertEquals(21, WiredEffectInitTransaction.contractCountRefusal(0));
        assertEquals(21, WiredEffectInitTransaction.contractCountRefusal(2));
    }
}
