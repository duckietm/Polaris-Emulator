package polaris.hotel.pets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** The horse figure's body palettes: four breeds per colour, in the id ranges the official horse asset lists. */
class HorseCoatTest {
    @Test
    void everyColourHasFourPalettesInARow() {
        assertEquals(2, HorseCoat.firstPalette(1));
        assertEquals(22, HorseCoat.firstPalette(6));
        assertEquals(46, HorseCoat.firstPalette(12));
        // Colours 13 to 17 follow the first block of hair palettes, colour 18 the second.
        assertEquals(61, HorseCoat.firstPalette(13));
        assertEquals(77, HorseCoat.firstPalette(17));
        assertEquals(86, HorseCoat.firstPalette(18));
        assertEquals(0, HorseCoat.firstPalette(0));
        assertEquals(-1, HorseCoat.firstPalette(19));
    }

    @Test
    void aPaletteTellsItsBreed() {
        assertEquals(1, HorseCoat.breedOf(2));
        assertEquals(4, HorseCoat.breedOf(5));
        assertEquals(1, HorseCoat.breedOf(22));
        assertEquals(4, HorseCoat.breedOf(49));
        assertEquals(2, HorseCoat.breedOf(62));
        assertEquals(4, HorseCoat.breedOf(80));
        assertEquals(3, HorseCoat.breedOf(88));
        // The default coat and the hair palettes between the coat blocks are no breed of their own.
        assertEquals(1, HorseCoat.breedOf(0));
        assertEquals(1, HorseCoat.breedOf(55));
    }

    @Test
    void aBodyDyeKeepsTheBreed() {
        // Breed 3 of colour 1 (palette 4) dyed to colour 6 becomes breed 3 of colour 6.
        assertEquals(24, HorseCoat.dyed(4, 6));
        assertEquals(64, HorseCoat.dyed(5, 13));
        assertEquals(88, HorseCoat.dyed(63, 18));
        // A horse on the default coat gets the first breed, as before.
        assertEquals(2, HorseCoat.dyed(0, 1));
        assertEquals(61, HorseCoat.dyed(0, 13));
    }

    @Test
    void dyeZeroIsTheDefaultCoatAndAnUnknownColourKeepsTheOldRule() {
        assertEquals(0, HorseCoat.dyed(24, 0));
        assertEquals(98, HorseCoat.dyed(24, 25));
    }

    @Test
    void hairProductsReadTheirValueFromTheFurniData() {
        assertEquals(54, HorseCoat.hairValue("15 2,3 54,54"));
        assertEquals(103, HorseCoat.hairValue("15 2,3 103,103 1,1"));
        assertEquals(-1, HorseCoat.hairValue("15 2,3 -1,-1 1,1"));
        assertEquals(1, HorseCoat.hairValue(" 15 3,2 1,1 "));
        // A body dye ("15 Gold"), a saddle and an empty text are not hair values.
        assertNull(HorseCoat.hairValue("15 Gold"));
        assertNull(HorseCoat.hairValue("15 4 9 0 77"));
        assertNull(HorseCoat.hairValue(""));
        assertNull(HorseCoat.hairValue(null));
        assertNull(HorseCoat.hairValue("15 2,3 x,y"));
    }
}
