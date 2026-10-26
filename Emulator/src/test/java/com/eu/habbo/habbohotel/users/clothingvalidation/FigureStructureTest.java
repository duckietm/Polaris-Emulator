package com.eu.habbo.habbohotel.users.clothingvalidation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The structural figure guard runs with figuredata validation off, which is the
 * default: it is what keeps a figure inside {@code users.look}'s varchar(256) and
 * out of the room broadcast, independently of which clothes a user may wear.
 */
class FigureStructureTest {

    @Test
    void acceptsTheFiguresTheClientSends() {
        assertTrue(ClothingValidationManager.isStructurallyValidFigure(
                "hr-115-42.hd-195-19.ch-3030-82.lg-275-1408.fa-1201.ca-1804-64"));
        assertTrue(ClothingValidationManager.isStructurallyValidFigure("hd-180-1"));
        assertTrue(ClothingValidationManager.isStructurallyValidFigure(
                "hd-180-1.ch-210-66.lg-270-82.sh-290-91.ha-1012-110.he-1604-73"));
    }

    @Test
    void rejectsAFigureThatWouldNotFitTheColumn() {
        StringBuilder tooLong = new StringBuilder("hd-180-1");
        while (tooLong.length() <= ClothingValidationManager.MAX_FIGURE_LENGTH) {
            tooLong.append(".ch-210-66");
        }
        assertFalse(ClothingValidationManager.isStructurallyValidFigure(tooLong.toString()));
    }

    @Test
    void rejectsAFigureWithTooManyParts() {
        String part = "hd-180-1";
        assertFalse(ClothingValidationManager.isStructurallyValidFigure((part + ".").repeat(30) + part));
    }

    @Test
    void rejectsAnythingThatIsNotAFigure() {
        assertFalse(ClothingValidationManager.isStructurallyValidFigure(null));
        assertFalse(ClothingValidationManager.isStructurallyValidFigure(""));
        assertFalse(ClothingValidationManager.isStructurallyValidFigure("   "));
        assertFalse(ClothingValidationManager.isStructurallyValidFigure("hd"));
        assertFalse(ClothingValidationManager.isStructurallyValidFigure("hd-"));
        assertFalse(ClothingValidationManager.isStructurallyValidFigure("hd-180-1."));
        assertFalse(ClothingValidationManager.isStructurallyValidFigure("hd-180-1..ch-210-66"));
        assertFalse(ClothingValidationManager.isStructurallyValidFigure("hdx-180-1"));
        assertFalse(ClothingValidationManager.isStructurallyValidFigure("hd-180-1.<script>"));
        assertFalse(ClothingValidationManager.isStructurallyValidFigure("hd-180-1\nhd-180-1"));
        assertFalse(ClothingValidationManager.isStructurallyValidFigure("hd-99999999-1"));
    }
}
