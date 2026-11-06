package polaris.hotel.catalog.layouts;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * A room bundle was copied statement by statement with every error only logged, so the buyer kept paying for a room
 * that was missing or empty, and a room that did not load threw after the copy and refunded a room that existed.
 */
class RoomBundleCopyTransactionTest {

    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/polaris/hotel/catalog/layouts/RoomBundleLayout.java"));
    }

    @Test
    void theCopyIsOneTransactionThatSaysWhetherItExists() throws Exception {
        String layout = source();
        int method = layout.indexOf("public boolean tryBuyRoom(User user, int userId, String userName) {");
        int autoCommit = layout.indexOf("connection.setAutoCommit(false);", method);
        int copy = layout.indexOf("roomId = this.copyRoom(connection, userId, userName);", autoCommit);
        int commit = layout.indexOf("connection.commit();", copy);

        assertTrue(method > 0 && autoCommit > method && copy > autoCommit && commit > copy);
        assertTrue(layout.indexOf("connection.rollback();", copy) > copy);
        assertTrue(layout.indexOf("return false;", copy) > copy);
    }

    @Test
    void aRoomThatDoesNotLoadIsStillDelivered() throws Exception {
        String layout = source();
        int load = layout.indexOf("Room r = Emulator.getGameEnvironment().getRoomManager().loadRoom(roomId);");

        assertTrue(load > 0);
        assertTrue(layout.indexOf("if (r != null) {", load) > load);
    }

    @Test
    void theOldEntryPointsStayForPluginsAndRcon() throws Exception {
        String layout = source();

        assertTrue(layout.contains("public void buyRoom(User user) {"));
        assertTrue(layout.contains("public void buyRoom(User user, int userId, String userName) {"));
    }
}
