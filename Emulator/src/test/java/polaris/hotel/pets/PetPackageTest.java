package polaris.hotel.pets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import polaris.hotel.items.Item;

class PetPackageTest {
    private static Item item(String name, String customParams) {
        Item item = mock(Item.class);
        when(item.getName()).thenReturn(name);
        when(item.getCustomParams()).thenReturn(customParams);
        return item;
    }

    @Test
    void theFurniDataParamsNameThePetTypeAndTheNest() {
        assertEquals(new PetPackage(1, "newbie_nest"), PetPackage.fromCustomParams("1 newbie_nest"));
        assertEquals(new PetPackage(2, "waterbowl*2"), PetPackage.fromCustomParams(" 2 waterbowl*2 "));
        // A third word (the badge of the Valentine frog) does not matter.
        assertEquals(new PetPackage(11, "nest_frog"), PetPackage.fromCustomParams("11 nest_frog PET05"));
        assertEquals(new PetPackage(40, "my_nest"), PetPackage.of(item("custom_box", "40 my_nest")));
    }

    @Test
    void otherFurniAreNoPackage() {
        assertNull(PetPackage.fromCustomParams(null));
        assertNull(PetPackage.fromCustomParams(""));
        assertNull(PetPackage.fromCustomParams("16"));
        // A saddle, a dye and a monster plant potion start with a pet type too.
        assertNull(PetPackage.fromCustomParams("15 4 9 0 77"));
        assertNull(PetPackage.fromCustomParams("15 2,3 50,50"));
        assertNull(PetPackage.of(item("throne", "")));
        assertNull(PetPackage.of(null));
    }

    @Test
    void anOfficialPackageIsKnownByNameWithoutParams() {
        assertEquals(new PetPackage(12, "nest_dragon"), PetPackage.of(item("newbie_bronze_cave_dragon_package", "")));
        assertEquals(new PetPackage(8, "nest_chick"), PetPackage.of(item("nest_with_egg", null)));
        assertEquals("nest_with_statue", PetPackage.packageNameFor(14));
        assertNull(PetPackage.packageNameFor(15));
    }

    @Test
    void aPackageMayCarryThePetItHolds() {
        PetPackage dragon = new PetPackage(12, "nest_dragon");

        assertEquals("12 3 FF8800", dragon.figureIn("12 3 ff8800"));
        assertNull(dragon.figureIn(""));
        assertNull(dragon.figureIn("0"));
        // The figure of another pet type is not this package's.
        assertNull(dragon.figureIn("15 2 FFFFFF"));
        assertNull(dragon.figureIn("12 3 red"));
    }
}
