package polaris.hotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectChangeVariableValue.OP_ASSIGN;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectChangeVariableValue.OP_BIT_COUNT;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectChangeVariableValue.OP_CLEAR_BIT;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectChangeVariableValue.OP_GET_BIT;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectChangeVariableValue.OP_NEXT_HIGH_BIT;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectChangeVariableValue.OP_NEXT_HIGH_BIT_EXCLUSIVE;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectChangeVariableValue.OP_NEXT_LOW_BIT;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectChangeVariableValue.OP_NEXT_LOW_BIT_EXCLUSIVE;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectChangeVariableValue.OP_PREV_HIGH_BIT;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectChangeVariableValue.OP_PREV_HIGH_BIT_EXCLUSIVE;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectChangeVariableValue.OP_PREV_LOW_BIT;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectChangeVariableValue.OP_PREV_LOW_BIT_EXCLUSIVE;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectChangeVariableValue.OP_SET_BIT;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectChangeVariableValue.OP_TOGGLE_BIT;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectChangeVariableValue.applyOperation;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectChangeVariableValue.normalizeOperation;

import org.junit.jupiter.api.Test;

/**
 * The bit family the official client offers under the "advanced" operators (codes 110-122). A "low"
 * bit is a cleared bit and a "high" bit is a set bit; a scan answers the bit position, or -1 when
 * nothing matches; an inclusive scan starts at the operand position, an exclusive one a position
 * further on. Values are 32-bit, so positions run 0..31 and position 31 is the sign bit.
 */
class WiredEffectChangeVariableValueBitOperationsTest {

    @Test
    void bitCountCountsSetBits() {
        assertEquals(3, applyOperation(OP_BIT_COUNT, 0b1011, 0));
        assertEquals(32, applyOperation(OP_BIT_COUNT, -1, 0));
        assertEquals(0, applyOperation(OP_BIT_COUNT, 0, 0));
    }

    @Test
    void getBitAnswersZeroOrOne() {
        assertEquals(1, applyOperation(OP_GET_BIT, 0b1010, 1));
        assertEquals(0, applyOperation(OP_GET_BIT, 0b1010, 2));
        assertEquals(1, applyOperation(OP_GET_BIT, -1, 31));
        assertEquals(0, applyOperation(OP_GET_BIT, 0b1010, 32));
    }

    @Test
    void setClearAndToggleTouchOnlyTheNamedBit() {
        assertEquals(0b1000, applyOperation(OP_SET_BIT, 0, 3));
        assertEquals(Integer.MIN_VALUE, applyOperation(OP_SET_BIT, 0, 31));
        assertEquals(0b1110, applyOperation(OP_CLEAR_BIT, 0b1111, 0));
        assertEquals(0, applyOperation(OP_TOGGLE_BIT, 0b1000, 3));
        assertEquals(1, applyOperation(OP_TOGGLE_BIT, 0, 0));
    }

    @Test
    void setClearAndToggleIgnoreAPositionOutsideTheWord() {
        assertEquals(0b1010, applyOperation(OP_SET_BIT, 0b1010, 32));
        assertEquals(0b1010, applyOperation(OP_CLEAR_BIT, 0b1010, -1));
        assertEquals(0b1010, applyOperation(OP_TOGGLE_BIT, 0b1010, 40));
    }

    @Test
    void inclusiveScansStartAtTheOperandPosition() {
        assertEquals(3, applyOperation(OP_NEXT_LOW_BIT, 0b0111, 0));
        assertEquals(3, applyOperation(OP_NEXT_LOW_BIT, 0b0111, 3));
        assertEquals(3, applyOperation(OP_NEXT_HIGH_BIT, 0b1000, 0));
        assertEquals(3, applyOperation(OP_NEXT_HIGH_BIT, 0b1000, 3));
        assertEquals(31, applyOperation(OP_NEXT_HIGH_BIT, Integer.MIN_VALUE, 0));
        assertEquals(0, applyOperation(OP_PREV_LOW_BIT, 0b1110, 3));
        assertEquals(31, applyOperation(OP_PREV_LOW_BIT, 0, 31));
        assertEquals(3, applyOperation(OP_PREV_HIGH_BIT, 0b1000, 5));
        assertEquals(3, applyOperation(OP_PREV_HIGH_BIT, 0b1000, 3));
    }

    @Test
    void exclusiveScansSkipTheOperandPosition() {
        assertEquals(3, applyOperation(OP_NEXT_LOW_BIT_EXCLUSIVE, 0b0110, 0));
        assertEquals(3, applyOperation(OP_NEXT_HIGH_BIT_EXCLUSIVE, 0b1001, 0));
        assertEquals(0, applyOperation(OP_PREV_LOW_BIT_EXCLUSIVE, 0b0110, 3));
        assertEquals(0, applyOperation(OP_PREV_HIGH_BIT_EXCLUSIVE, 0b1001, 3));
    }

    @Test
    void scansAnswerMinusOneWhenNothingMatches() {
        assertEquals(-1, applyOperation(OP_NEXT_LOW_BIT, -1, 0));
        assertEquals(-1, applyOperation(OP_NEXT_HIGH_BIT, 0, 0));
        assertEquals(-1, applyOperation(OP_PREV_LOW_BIT, 0b1111, 3));
        assertEquals(-1, applyOperation(OP_PREV_HIGH_BIT, 0b1000, 2));
        assertEquals(-1, applyOperation(OP_NEXT_LOW_BIT_EXCLUSIVE, 0, 31));
        assertEquals(-1, applyOperation(OP_NEXT_HIGH_BIT_EXCLUSIVE, 0b1001, 3));
        assertEquals(-1, applyOperation(OP_PREV_LOW_BIT_EXCLUSIVE, 0b0110, 0));
        assertEquals(-1, applyOperation(OP_PREV_HIGH_BIT_EXCLUSIVE, 0b1001, 0));
        assertEquals(-1, applyOperation(OP_NEXT_LOW_BIT, 0b0111, 40));
    }

    @Test
    void aBitOperationWithoutAnOperandLeavesTheValueAlone() {
        assertEquals(5, applyOperation(OP_GET_BIT, 5, null));
        assertEquals(5, applyOperation(OP_NEXT_HIGH_BIT, 5, null));
    }

    @Test
    void theWholeBitFamilyIsAcceptedOnSave() {
        for (int operation = 110; operation <= 122; operation++) {
            assertEquals(operation, normalizeOperation(operation), "operation " + operation);
        }
        assertEquals(OP_ASSIGN, normalizeOperation(123));
        assertEquals(OP_ASSIGN, normalizeOperation(109));
    }
}
