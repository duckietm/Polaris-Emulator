package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameTimer;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameUpCounter;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.WiredTradingManager;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.WiredTradingSession;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContract;
import com.eu.habbo.habbohotel.pets.MonsterplantPet;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitType;
import com.eu.habbo.habbohotel.rooms.RoomWiredInternalVariables;
import com.eu.habbo.habbohotel.rooms.RoomWiredRuntime;
import com.eu.habbo.habbohotel.rooms.WiredHabbicons;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

/** AIR's clock, plant, habbicon and transaction built-in variables (turbo P1-12, README Deferred). */
class WiredAirBuiltInVariablesTest {
    private final Room room = mock(Room.class);

    private WiredContext context(WiredEvent event) {
        return new WiredContext(event, null, mock(WiredServices.class), new WiredState(10));
    }

    @Test
    void everyOfficialBuiltInIsReadByTheEngine() {
        for (RoomWiredInternalVariables.Row row : RoomWiredInternalVariables.officialRows()) {
            assertTrue(RoomWiredInternalVariables.isSupported(row), row.id() + " " + row.name());
        }
    }

    // ---- ~clock.* -------------------------------------------------------------------------

    @Test
    void anUpCounterShowsItsStateAndPulses() {
        InteractionGameUpCounter counter = mock(InteractionGameUpCounter.class);
        when(counter.getCurrentTimeMs()).thenReturn(12_500L);
        when(counter.isRunning()).thenReturn(true);

        assertEquals(25, WiredSmartVariables.readFurniValue(counter, "~clock.pulse_count"), "12.5 s is 25 pulses");
        assertEquals(1, WiredSmartVariables.readFurniValue(counter, "~clock.state"), "running");
        assertEquals(1, WiredSmartVariables.readFurniValue(counter, "~clock.is_game_aware"));

        when(counter.isPaused()).thenReturn(true);
        assertEquals(2, WiredSmartVariables.readFurniValue(counter, "~clock.state"), "paused");
        when(counter.isRunning()).thenReturn(false);
        when(counter.isPaused()).thenReturn(false);
        assertEquals(0, WiredSmartVariables.readFurniValue(counter, "~clock.state"), "initial");
    }

    @Test
    void aGameTimerShowsWhatItHasLeftInPulses() {
        InteractionGameTimer timer = mock(InteractionGameTimer.class);
        when(timer.getTimeNow()).thenReturn(30);

        assertEquals(60, WiredSmartVariables.readFurniValue(timer, "~clock.pulse_count"));
        assertNull(WiredSmartVariables.readFurniValue(mock(HabboItem.class), "~clock.state"), "not a clock");
    }

    @Test
    void pulseCountIsTheOnlyWritableClockPart() {
        assertTrue(WiredInternalVariableSupport.canUseFurniReference("~clock.state"));
        assertTrue(WiredInternalVariableSupport.canUseFurniDestination("~clock.pulse_count"));
        assertFalse(WiredInternalVariableSupport.canUseFurniDestination("~clock.state"));
        assertFalse(WiredInternalVariableSupport.canUseFurniDestination("~clock.is_game_aware"));

        InteractionGameUpCounter counter = mock(InteractionGameUpCounter.class);
        assertTrue(WiredSmartVariables.writeClockPulses(this.room, counter, 7));
        verify(counter).adjustCounter(this.room, 2, 0, 7);

        InteractionGameTimer timer = mock(InteractionGameTimer.class);
        assertTrue(WiredSmartVariables.writeClockPulses(this.room, timer, 21));
        verify(timer).setTimeNow(10);
        assertFalse(WiredSmartVariables.writeClockPulses(this.room, mock(HabboItem.class), 4));
    }

    // ---- ~plant.* -------------------------------------------------------------------------

    @Test
    void plantShapeColorAndHarvest() {
        MonsterplantPet plant = mock(MonsterplantPet.class);
        when(plant.getType()).thenReturn(12);
        when(plant.getHue()).thenReturn(10);
        when(plant.isFullyGrown()).thenReturn(true);

        assertEquals(11, WiredSmartVariables.readUserValue(plant, "~plant.shape"), "AIR's shapes are 0..11");
        assertEquals(10, WiredSmartVariables.readUserValue(plant, "~plant.color"));
        assertEquals(1, WiredSmartVariables.readUserValue(plant, "~plant.can_harvest"));
        when(plant.isDead()).thenReturn(true);
        assertEquals(0, WiredSmartVariables.readUserValue(plant, "~plant.can_harvest"), "a dead plant");
        assertFalse(WiredInternalVariableSupport.canUseUserDestination("~plant.shape"), "read only");
    }

    // ---- @habbicon / @event.habbicon -----------------------------------------------------------

    @Test
    void aShownHabbiconFadesWithTheBubble() {
        WiredHabbicons habbicons = new WiredHabbicons();
        habbicons.show(3, 42, 1_000L);

        assertEquals(42, habbicons.current(3, 1_000L + WiredHabbicons.VISIBLE_MS - 1));
        assertEquals(0, habbicons.current(3, 1_000L + WiredHabbicons.VISIBLE_MS));
        habbicons.show(3, 42, 0L);
        habbicons.show(3, 0, 0L);
        assertEquals(0, habbicons.current(3, 1L), "removed");
    }

    @Test
    void habbiconIsGivenToAvatarsAndReadBack() {
        RoomWiredRuntime runtime = mock(RoomWiredRuntime.class);
        WiredHabbicons habbicons = new WiredHabbicons();
        when(runtime.getHabbicons()).thenReturn(habbicons);
        when(this.room.getWiredRuntime()).thenReturn(runtime);
        RoomUnit unit = mock(RoomUnit.class);
        when(unit.getId()).thenReturn(5);
        when(unit.getRoomUnitType()).thenReturn(RoomUnitType.USER);

        assertEquals(0, WiredInternalVariableSupport.readUserValue(this.room, unit, "@habbicon"));
        assertFalse(WiredInternalVariableSupport.hasUserValue(this.room, unit, "@habbicon"));
        assertTrue(WiredHabbiconVariables.write(this.room, unit, 17));
        verify(this.room).sendComposer(any(ServerMessage.class));
        assertEquals(17, WiredInternalVariableSupport.readUserValue(this.room, unit, "@habbicon"));
        assertTrue(WiredInternalVariableSupport.hasUserValue(this.room, unit, "@habbicon"));
        assertTrue(WiredInternalVariableSupport.canUseUserDestination("@habbicon"));

        RoomUnit pet = mock(RoomUnit.class);
        when(pet.getRoomUnitType()).thenReturn(RoomUnitType.PET);
        assertNull(WiredInternalVariableSupport.readUserValue(this.room, pet, "@habbicon"), "pets show none");
        assertFalse(WiredHabbiconVariables.write(this.room, pet, 17));
    }

    @Test
    void theEventHabbiconIsTheOneTheEventIsAbout() {
        WiredEvent changed = WiredEvent.builder(WiredEvent.Type.VARIABLE_CHANGED, this.room)
                .habbiconId(42)
                .build();

        assertTrue(WiredInternalVariableSupport.canUseContextReference("@event.habbicon"));
        assertEquals(42, WiredInternalVariableSupport.readContextValue(this.context(changed), "@event.habbicon"));
        assertNull(WiredInternalVariableSupport.readContextValue(
                this.context(
                        WiredEvent.builder(WiredEvent.Type.USER_SAYS, this.room).build()),
                "@event.habbicon"));
    }

    // ---- @transaction.* / @event.transaction_complete.* ----------------------------------------

    @Test
    void aUserInATransactionExposesIt() {
        RoomWiredRuntime runtime = mock(RoomWiredRuntime.class);
        WiredTradingManager trading = mock(WiredTradingManager.class);
        WiredTradingSession session = mock(WiredTradingSession.class);
        Habbo habbo = mock(Habbo.class);
        RoomUnit unit = mock(RoomUnit.class);
        when(this.room.getWiredRuntime()).thenReturn(runtime);
        when(runtime.getTradingManager()).thenReturn(trading);
        when(this.room.getHabbo(unit)).thenReturn(habbo);

        assertEquals(0, WiredInternalVariableSupport.readUserValue(this.room, unit, "@transaction.in_trade"));
        assertFalse(WiredInternalVariableSupport.hasUserValue(this.room, unit, "@transaction.in_trade"));
        assertNull(WiredInternalVariableSupport.readUserValue(this.room, unit, "@transaction.contract_id"));

        when(trading.getSession(habbo)).thenReturn(session);
        when(trading.contractId(habbo)).thenReturn(77);
        when(session.openedAtMillis()).thenReturn(1_700_000_000_000L);
        when(session.getState()).thenReturn(WiredTradingSession.STATE_COUNTDOWN);
        when(session.timesToSettle()).thenReturn(3);

        assertEquals(1, WiredInternalVariableSupport.readUserValue(this.room, unit, "@transaction.in_trade"));
        assertTrue(WiredInternalVariableSupport.hasUserValue(this.room, unit, "@transaction.in_trade"));
        assertEquals(77, WiredInternalVariableSupport.readUserValue(this.room, unit, "@transaction.contract_id"));
        assertEquals(
                1_700_000_000, WiredInternalVariableSupport.readUserValue(this.room, unit, "@transaction.start_time"));
        assertEquals(2, WiredInternalVariableSupport.readUserValue(this.room, unit, "@transaction.state"), "confirmed");
        assertEquals(3, WiredInternalVariableSupport.readUserValue(this.room, unit, "@transaction.current_multiplier"));
        assertFalse(WiredInternalVariableSupport.hasUserValue(this.room, unit, "@transaction.can_accept"));
        when(session.canAccept()).thenReturn(true);
        assertTrue(WiredInternalVariableSupport.hasUserValue(this.room, unit, "@transaction.can_accept"));
        assertFalse(WiredInternalVariableSupport.canUseUserDestination("@transaction.state"), "read only");
    }

    @Test
    void aCompletedTransactionTellsWhatItMoved() {
        List<InteractionWiredContract.Term> given = List.of(
                InteractionWiredContract.Term.currency(InteractionWiredContract.DIR_RECEIVE, -1, 25),
                InteractionWiredContract.Term.furni(InteractionWiredContract.DIR_RECEIVE, false, 9, "", 2),
                InteractionWiredContract.Term.furni(InteractionWiredContract.DIR_RECEIVE, true, 8, "", 1));
        WiredEvent completed = WiredEvent.builder(WiredEvent.Type.TRANSACTION_COMPLETE, this.room)
                .transactionTotals(WiredTransactionVariables.totals(3, 4, 60, given))
                .build();
        WiredContext ctx = this.context(completed);

        assertEquals(3, WiredInternalVariableSupport.readContextValue(ctx, "@event.transaction_complete.multiplier"));
        assertEquals(
                4,
                WiredInternalVariableSupport.readContextValue(ctx, "@event.transaction_complete.deposit.furni_count"));
        assertEquals(
                60,
                WiredInternalVariableSupport.readContextValue(ctx, "@event.transaction_complete.deposit.coins_count"));
        assertEquals(
                3,
                WiredInternalVariableSupport.readContextValue(
                        ctx, "@event.transaction_complete.withdrawal.furni_count"));
        assertEquals(
                25,
                WiredInternalVariableSupport.readContextValue(
                        ctx, "@event.transaction_complete.withdrawal.coins_count"));

        WiredContext bare = this.context(WiredEvent.builder(WiredEvent.Type.TRANSACTION_COMPLETE, this.room)
                .build());
        assertEquals(1, WiredInternalVariableSupport.readContextValue(bare, "@event.transaction_complete.multiplier"));
        assertNull(WiredInternalVariableSupport.readContextValue(
                this.context(WiredEvent.builder(WiredEvent.Type.TRANSACTION_FAIL, this.room)
                        .build()),
                "@event.transaction_complete.multiplier"));
    }
}
