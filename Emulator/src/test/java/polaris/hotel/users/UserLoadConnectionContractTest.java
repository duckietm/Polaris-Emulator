package polaris.hotel.users;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Building a Habbo or UserInfo loads more on connections of its own. Built inside the connection of
 * the user's row, every login held one connection and waited for another, and a burst of logins
 * (after a restart) starved the pool. They are built from a copied row once that connection is back.
 */
class UserLoadConnectionContractTest {
    @Test
    void usersAreBuiltFromADetachedRow() throws Exception {
        String source = Files.readString(Path.of("src/main/java/polaris/hotel/users/UserManager.java"));
        String compact = source.replaceAll("\\s+", "");

        assertTrue(compact.contains("try(CachedRowSetset=DetachedRows.read(query,binder::bind))"));
        assertTrue(compact.contains("try(CachedRowSetrow=DetachedRows.read(sql,binder))"));
        assertTrue(compact.contains("returnrow.next()?newUserInfo(row):null;"));
        assertFalse(compact.contains("SqlQueries.queryOne(\"SELECT*FROMusers"), "UserInfo mapped inside the query");
    }
}
