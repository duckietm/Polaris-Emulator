package polaris.messages.incoming.rooms.pets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import polaris.hotel.items.Item;
import polaris.hotel.pets.HorsePet;
import polaris.hotel.users.UserItem;

/** A new saddle on a saddled horse gives the old saddle back instead of swallowing it. */
class HorseSaddleReplacementTest {
    private static UserItem furni(String name) {
        Item base = mock(Item.class);
        when(base.getName()).thenReturn(name);
        UserItem item = mock(UserItem.class);
        when(item.getBaseItem()).thenReturn(base);
        return item;
    }

    private static HorsePet horse(boolean saddled, int saddleItemId) {
        HorsePet horse = mock(HorsePet.class);
        when(horse.hasSaddle()).thenReturn(saddled);
        when(horse.getSaddleItemId()).thenReturn(saddleItemId);
        return horse;
    }

    @Test
    void aNewSaddleReplacesTheOneTheHorseWears() {
        assertEquals(4221, PetUseItemEvent.replacedSaddle(furni("horse_saddle2"), horse(true, 4221)));
    }

    @Test
    void nothingIsReplacedOnAHorseWithoutASaddleOrByAnotherProduct() {
        assertEquals(0, PetUseItemEvent.replacedSaddle(furni("horse_saddle1"), horse(false, 4221)));
        assertEquals(0, PetUseItemEvent.replacedSaddle(furni("horse_dye_3"), horse(true, 4221)));
        // An old horse that never recorded its saddle has nothing to hand back.
        assertEquals(0, PetUseItemEvent.replacedSaddle(furni("horse_saddle1"), horse(true, 0)));
    }
}
