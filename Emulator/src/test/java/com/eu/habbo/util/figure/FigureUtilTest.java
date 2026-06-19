package com.eu.habbo.util.figure;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FigureUtilTest {

    @Test
    void getFigureBitsParsesSetsByType() {
        Map<String, String> bits = FigureUtil.getFigureBits("hr-100-7.hd-180-1.ch-210-66");
        assertEquals(3, bits.size());
        assertEquals("100-7", bits.get("hr"));
        assertEquals("180-1", bits.get("hd"));
        assertEquals("210-66", bits.get("ch"));
    }

    @Test
    void getFigureBitsHandlesTypeWithoutValue() {
        Map<String, String> bits = FigureUtil.getFigureBits("hr");
        assertEquals("", bits.get("hr"));
    }

    @Test
    void mergeFiguresCombinesSingleSets() {
        assertEquals("hr-100-7.hd-180-1", FigureUtil.mergeFigures("hr-100-7", "hd-180-1"));
    }

    @Test
    void mergeFiguresRespectsLimitOnFirstFigure() {
        // Only the "hr" type from figure1 is kept; all of figure2 is appended.
        assertEquals("hr-1.ch-3", FigureUtil.mergeFigures("hr-1", "ch-3", new String[]{"hr"}));
    }

    @Test
    void mergeFiguresDropsExcludedFirstFigureType() {
        assertEquals("ch-3", FigureUtil.mergeFigures("hr-1", "ch-3", new String[]{"lg"}));
    }

    @Test
    void hasBlacklistedClothingDetectsBannedPartId() {
        assertTrue(FigureUtil.hasBlacklistedClothing("hr-100-7", Set.of(100)));
        assertFalse(FigureUtil.hasBlacklistedClothing("hr-100-7", Set.of(999)));
    }

    @Test
    void hasBlacklistedClothingIgnoresMalformedSets() {
        // A set without a part id must not throw and must not match.
        assertFalse(FigureUtil.hasBlacklistedClothing("hr", Set.of(100)));
    }
}
