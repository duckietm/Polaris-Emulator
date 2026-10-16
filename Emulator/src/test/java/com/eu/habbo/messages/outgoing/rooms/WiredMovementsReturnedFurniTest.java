package com.eu.habbo.messages.outgoing.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WiredMovementsReturnedFurniTest {

    private static WiredMovementsComposer.MovementData move(int id, int fromX, int toX, int rotation) {
        return WiredMovementsComposer.furniMovement(
                id, fromX, 1, toX, 1, 0.0, 0.0, rotation, 500, 0, WiredMovementsComposer.FURNI_ANCHOR_NONE, 0);
    }

    @Test
    void aFurniMovedAndBroughtBackByOneFiringGetsNoEntry() {
        List<WiredMovementsComposer.MovementData> movements =
                List.of(move(7, 1, 2, 0), move(7, 2, 1, 0), move(8, 1, 3, 0));

        List<WiredMovementsComposer.MovementData> kept =
                WiredMovementsComposer.withoutReturnedFurni(movements, Set.of(7, 8), Map.of(7, 0, 8, 0));

        assertEquals(List.of(movements.get(2)), kept);
    }

    @Test
    void aTurnedFurniAndFurniMovedByOtherFiringsKeepTheirEntries() {
        List<WiredMovementsComposer.MovementData> turned = List.of(move(7, 1, 2, 2), move(7, 2, 1, 2));
        List<WiredMovementsComposer.MovementData> chained = List.of(move(9, 1, 2, 0), move(9, 2, 1, 0));

        assertEquals(turned, WiredMovementsComposer.withoutReturnedFurni(turned, Set.of(7), Map.of(7, 0)));
        assertEquals(chained, WiredMovementsComposer.withoutReturnedFurni(chained, Set.of(), Map.of(9, 0)));
    }
}
