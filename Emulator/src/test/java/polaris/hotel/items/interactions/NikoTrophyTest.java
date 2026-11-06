package polaris.hotel.items.interactions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import polaris.hotel.items.Item;
import polaris.hotel.users.UserItem;

class NikoTrophyTest {

    private static UserItem itemNamed(String name) {
        Item base = mock(Item.class);
        when(base.getName()).thenReturn(name);
        UserItem item = mock(UserItem.class);
        when(item.getBaseItem()).thenReturn(base);
        return item;
    }

    @Test
    void silverAndGoldNikoTrophiesCarryTheOfficialViews() {
        assertEquals(10, NikoTrophy.viewOf("prizetrophy_niko1"));
        assertEquals(20, NikoTrophy.viewOf("prizetrophy_niko2"));
    }

    @Test
    void otherItemsKeepTheirFallbackExtra() {
        assertEquals(0, NikoTrophy.viewOf("prizetrophy_hot"));
        assertEquals(1, NikoTrophy.extraOr(itemNamed("prizetrophy_hot"), 1));
        assertEquals(0, NikoTrophy.extraOr(itemNamed("prizetrophy_hot"), 0));
        assertEquals(1, NikoTrophy.extraOr(null, 1));
    }

    @Test
    void nikoTrophyReplacesTheFallbackExtra() {
        assertEquals(20, NikoTrophy.extraOr(itemNamed("prizetrophy_niko2"), 1));
        assertEquals(10, NikoTrophy.extraOr(itemNamed("prizetrophy_niko1"), 0));
    }
}
