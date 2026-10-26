package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.eu.habbo.messages.outgoing.rooms.WiredMovementsComposer;
import java.util.List;
import org.junit.jupiter.api.Test;

/** One WiredMovements lists the furni, then the users, then the turns (observed on AIR WIN63-202610201032). */
class WiredMovementsOrderTest {

    @Test
    void furniComeBeforeUsersAndTurnsLastEachInCollectedOrder() {
        WiredMovementsComposer.MovementData turn = WiredMovementsComposer.userDirectionUpdate(5, 2, 2);
        WiredMovementsComposer.MovementData user =
                WiredMovementsComposer.userSlideMovement(5, 1, 1, 2, 1, 0.0, 0.0, 2, 2, 500);
        WiredMovementsComposer.MovementData first = WiredMovementsComposer.furniMovement(81, 9, 1, 8, 1, 0.0, 0.0);
        WiredMovementsComposer.MovementData second = WiredMovementsComposer.furniMovement(80, 6, 1, 7, 1, 0.0, 0.0);

        assertEquals(
                List.of(first, second, user, turn),
                WiredMoveCarryHelper.furniFirst(List.of(turn, user, first, second)));
    }
}
