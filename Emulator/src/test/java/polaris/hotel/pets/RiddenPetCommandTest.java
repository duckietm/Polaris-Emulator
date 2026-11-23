package polaris.hotel.pets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import polaris.hotel.pets.actions.ActionFree;
import polaris.hotel.pets.actions.ActionSit;
import sun.misc.Unsafe;

/** A ridden horse does not listen to "free"; any other command first puts the rider down. */
class RiddenPetCommandTest {
    private static PetCommand command(PetAction action) throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        PetCommand command = (PetCommand) unsafe.allocateInstance(PetCommand.class);
        Field action0 = PetCommand.class.getDeclaredField("action");
        unsafe.putObject(command, unsafe.objectFieldOffset(action0), action);
        return command;
    }

    @Test
    void freeIsIgnoredWhileRidden() throws Exception {
        assertTrue(Pet.ignoredWhileRidden(command(mock(ActionFree.class))));
    }

    @Test
    void otherCommandsAreNot() throws Exception {
        assertFalse(Pet.ignoredWhileRidden(command(mock(ActionSit.class))));
        assertFalse(Pet.ignoredWhileRidden(null));
    }
}
