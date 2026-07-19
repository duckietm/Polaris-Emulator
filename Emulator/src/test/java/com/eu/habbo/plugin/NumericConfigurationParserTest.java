package com.eu.habbo.plugin;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NumericConfigurationParserTest {

    @Test
    void parsesSemicolonDelimitedIntegerArray() {
        assertArrayEquals(
                new int[]{40, 99},
                NumericConfigurationParser.parseIntArray("40;99", ";")
        );
    }

    @Test
    void parsesCommaDelimitedIntegerList() {
        assertEquals(
                List.of(0, 2, 8),
                NumericConfigurationParser.parseIntList("0,2,8", ",")
        );
    }
}
