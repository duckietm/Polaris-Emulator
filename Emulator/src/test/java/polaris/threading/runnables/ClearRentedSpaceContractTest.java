package polaris.threading.runnables;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** An expired rent found at load cleared nothing (renter already 0) and never took an online renter's items. */
class ClearRentedSpaceContractTest {

    @Test
    void theCleanupGoesThroughEndRent() throws Exception {
        String task = Files.readString(Path.of("src/main/java/polaris/threading/runnables/ClearRentedSpace.java"));

        assertTrue(task.contains("this.item.endRent();"));
        assertFalse(task.contains("new AddUserItemComposer("));
    }

    @Test
    void theRenterIsLeftForEndRentToClear() throws Exception {
        String space = Files.readString(
                Path.of("src/main/java/polaris/hotel/items/interactions/InteractionRentableSpace.java"));
        int schedule = space.indexOf("new ClearRentedSpace(");

        assertTrue(schedule > 0);
        assertFalse(space.substring(schedule, schedule + 400).contains("this.renterId = 0;"));
    }
}
