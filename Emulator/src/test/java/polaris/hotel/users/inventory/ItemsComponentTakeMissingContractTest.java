package polaris.hotel.users.inventory;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Crafting and the wired take-furni effect rely on a null when the inventory holds no item of the base item. */
class ItemsComponentTakeMissingContractTest {

    private static final Path SOURCE = Path.of("src/main/java/polaris/hotel/users/inventory/ItemsComponent.java");

    @Test
    void aMissingItemIsReportedAsNullBeforeAnythingIsRemoved() throws Exception {
        String source = Files.readString(SOURCE);
        int method = source.indexOf("public UserItem getAndRemoveUserItem(final Item item)");
        int nullReturn = source.indexOf("if (found == null) {", method);
        int remove = source.indexOf("this.tryRemoveUserItem(found)", method);

        assertTrue(method > 0);
        assertTrue(nullReturn > method && nullReturn < remove, "the null return comes before the removal");
    }
}
