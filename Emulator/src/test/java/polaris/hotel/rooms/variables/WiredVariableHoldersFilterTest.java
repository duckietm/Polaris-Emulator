package polaris.hotel.rooms.variables;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The variable overview's menus, with the values the official client sends. */
class WiredVariableHoldersFilterTest {
    private static final int HABBO = UserVariableHolders.ofUser(5);
    private static final int PET = UserVariableHolders.ofPet(6);
    private static final int BOT = UserVariableHolders.ofBot(7);

    private static List<RoomWiredVariableCatalog.Holder> holders() {
        return List.of(holder(HABBO, 10, 300, 900), holder(PET, 30, 100, 700), holder(BOT, 20, 200, 800));
    }

    private static RoomWiredVariableCatalog.Holder holder(int key, int value, long created, long updated) {
        return new RoomWiredVariableCatalog.Holder(
                RoomWiredVariableCatalog.HOLDER_USER, key, "", value, created, updated, true);
    }

    private static List<Integer> keys(int userType, int sort) {
        return RoomWiredVariableCatalog.filterAndSort(null, holders(), userType, sort).stream()
                .map(RoomWiredVariableCatalog.Holder::getEntityId)
                .toList();
    }

    @Test
    void theUserTypeMenuPicksHabbosPetsOrBots() {
        assertEquals(List.of(PET, BOT, HABBO), keys(-1, 0));
        assertEquals(List.of(HABBO), keys(1, 0));
        assertEquals(List.of(PET), keys(2, 0));
        assertEquals(List.of(BOT), keys(4, 0));
    }

    @Test
    void theSortMenuOrdersByValueCreationOrUpdate() {
        assertEquals(List.of(PET, BOT, HABBO), keys(-1, 0));
        assertEquals(List.of(HABBO, BOT, PET), keys(-1, 1));
        assertEquals(List.of(PET, BOT, HABBO), keys(-1, 2));
        assertEquals(List.of(HABBO, BOT, PET), keys(-1, 3));
        assertEquals(List.of(PET, BOT, HABBO), keys(-1, 4));
        assertEquals(List.of(HABBO, BOT, PET), keys(-1, 5));
    }
}
