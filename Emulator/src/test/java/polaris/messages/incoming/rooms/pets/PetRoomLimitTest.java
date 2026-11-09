package polaris.messages.incoming.rooms.pets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import polaris.hotel.permissions.Permission;
import polaris.hotel.pets.Pet;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;

/** The pet limit of a room holds for a placed pet, a monsterplant seed and a pet box alike. */
class PetRoomLimitTest {

    private static Room roomWith(int pets) {
        Int2ObjectMap<Pet> current = new Int2ObjectOpenHashMap<>();
        for (int i = 0; i < pets; i++) {
            current.put(i, mock(Pet.class));
        }
        Room room = mock(Room.class);
        when(room.getCurrentPets()).thenReturn(current);
        return room;
    }

    @Test
    void aFullRoomTakesNoPetUnlessTheUserMayHaveAnyNumber() {
        User user = mock(User.class);
        assertFalse(PetPlaceEvent.isFullFor(roomWith(Room.MAXIMUM_PETS - 1), user));
        assertTrue(PetPlaceEvent.isFullFor(roomWith(Room.MAXIMUM_PETS), user));

        when(user.hasPermission(Permission.ACC_UNLIMITED_PETS)).thenReturn(true);
        assertFalse(PetPlaceEvent.isFullFor(roomWith(Room.MAXIMUM_PETS), user));
    }

    @Test
    void theSeedAndThePetBoxAskBeforeTheyClaimTheFurni() throws Exception {
        for (String file : List.of(
                "src/main/java/polaris/messages/incoming/rooms/items/ToggleFloorItemEvent.java",
                "src/main/java/polaris/messages/incoming/rooms/pets/PetPackageNameEvent.java")) {
            String source = Files.readString(Path.of(file));
            int check = source.indexOf("PetPlaceEvent.isFullFor(room");
            assertTrue(check > 0, file);
            assertTrue(check < source.indexOf("room.claimItem(item)", check), file);
        }
    }
}
