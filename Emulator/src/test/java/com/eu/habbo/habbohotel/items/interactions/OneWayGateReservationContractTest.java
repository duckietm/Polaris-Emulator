package com.eu.habbo.habbohotel.items.interactions;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.items.interactions.interfaces.ConditionalGate;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * An open one-way gate made its tile walkable for everyone, so a second user could walk through it from the wrong side
 * while the first one passed. The open gate is reserved for the unit that opened it.
 */
class OneWayGateReservationContractTest {

    @Test
    void theGateStopsOtherUnitsLikeAConditionalGate() throws Exception {
        assertTrue(ConditionalGate.class.isAssignableFrom(InteractionOneWayGate.class));

        String gate = Files.readString(
                Path.of("src/main/java/com/eu/habbo/habbohotel/items/interactions/InteractionOneWayGate.java"));

        assertTrue(gate.contains("if (passing != null && roomUnit != passing) {"));
        int reserve = gate.indexOf("this.passingUnit = unit;");
        assertTrue(reserve > 0 && reserve < gate.indexOf("walkable = true;", reserve));
        assertTrue(gate.indexOf("this.passingUnit = null;", gate.indexOf("onFail.add(")) > 0);
        assertTrue(gate.indexOf("this.passingUnit = null;", gate.indexOf("private void refresh(Room room)")) > 0);
    }
}
