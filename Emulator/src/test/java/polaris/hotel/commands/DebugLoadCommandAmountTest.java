package polaris.hotel.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DebugLoadCommandAmountTest {
    @Test
    void withoutAnAmountItIsFiveHundred() {
        assertEquals(500, DebugLoadCommand.amountOf(new String[] {":debugload"}));
    }

    @Test
    void anAmountIsTakenUpToTheCap() {
        assertEquals(150, DebugLoadCommand.amountOf(new String[] {":debugload", "150"}));
        assertEquals(DebugLoadCommand.MAX_AMOUNT, DebugLoadCommand.amountOf(new String[] {":debugload", "99999"}));
    }

    @Test
    void anythingElseIsRefused() {
        assertEquals(0, DebugLoadCommand.amountOf(new String[] {":debugload", "0"}));
        assertEquals(0, DebugLoadCommand.amountOf(new String[] {":debugload", "-5"}));
        assertEquals(0, DebugLoadCommand.amountOf(new String[] {":debugload", "lots"}));
    }
}
