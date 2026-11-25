package polaris.hotel.bots;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import polaris.hotel.users.clothingvalidation.ClothingValidationManager;

class DebugLoadFiguresTest {
    @Test
    void fiveHundredBotsAllLookDifferent() {
        List<DebugLoadFigures.Look> looks = DebugLoadFigures.different(500, new Random(7));
        Set<String> figures = new HashSet<>();
        for (DebugLoadFigures.Look look : looks) figures.add(look.figure());

        assertEquals(500, looks.size());
        assertEquals(500, figures.size());
    }

    @Test
    void everyLookIsAWellFormedFigureWithTheMandatoryParts() {
        for (DebugLoadFigures.Look look : DebugLoadFigures.different(500, new Random(11))) {
            assertTrue(ClothingValidationManager.isStructurallyValidFigure(look.figure()), look.figure());
            for (String part : new String[] {"hd-", "hr-", "ch-", "lg-", "sh-"}) {
                assertTrue(look.figure().contains(part), look.figure());
            }
        }
    }

    @Test
    void aLookWearsThePartsOfItsOwnGender() {
        for (DebugLoadFigures.Look look : DebugLoadFigures.different(200, new Random(3))) {
            int head = Integer.parseInt(look.figure().split("\\.")[0].split("-")[1]);
            assertEquals(look.male(), head < 600, look.figure());
        }
    }
}
