package com.eu.habbo.habbohotel.items.interactions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Custom values are stored as key=value; entries. A builders list ("alice;bob") lost every name after the
 * first at the next load, and a side of an area that was not a number broke every check on the area.
 */
class InteractionCustomValuesStorageTest {

    @Test
    void aValueHoldingTheSeparatorSurvivesStorage() {
        for (String value : new String[] {"alice;bob;carol", "50% off", "a%3Bb", "", "plain"}) {
            assertEquals(value, InteractionCustomValues.decode(InteractionCustomValues.encode(value)), value);
        }
        assertEquals("alice%3Bbob", InteractionCustomValues.encode("alice;bob"));
    }

    @Test
    void storedValuesWithoutTheSeparatorReadAsBefore() {
        assertEquals("https://example.com/a.png?x=1", InteractionCustomValues.decode("https://example.com/a.png?x=1"));
    }

    @Test
    void aSavedNumberIsHeldToItsRange() {
        assertEquals("5", InteractionCustomValues.boundedInt("5", 128));
        assertEquals("128", InteractionCustomValues.boundedInt("99999", 128));
        assertEquals("0", InteractionCustomValues.boundedInt("-4", 128));
        assertNull(InteractionCustomValues.boundedInt("abc", 128));
        assertNull(InteractionCustomValues.boundedInt("", 128));
    }

    @Test
    void aStoredValueThatIsNotANumberReadsAsZero() {
        assertEquals(3, InteractionCustomValues.intValue(Map.of("tilesLeft", "3"), "tilesLeft"));
        assertEquals(0, InteractionCustomValues.intValue(Map.of("tilesLeft", "x"), "tilesLeft"));
        assertEquals(0, InteractionCustomValues.intValue(Map.of(), "tilesLeft"));
    }
}
