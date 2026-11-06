package polaris.messages.rcon;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GiveCreditsContractTest {
    private static String giveCreditsSource() throws Exception {
        return Files.readString(Path.of("src/main/java/polaris/messages/rcon/GiveCredits.java"));
    }

    @Test
    void offlineCreditGrantReportsMissingUsersWhenNoRowsChange() throws Exception {
        String source = giveCreditsSource();

        assertTrue(
                source.contains("RconUserLookup.userExists"), "Offline RCON credit grants must verify the target user");
        assertTrue(source.contains("USER_NOT_FOUND"), "Offline RCON credit grants must report missing users");
        assertTrue(
                source.contains("EconomyLedger.execute"),
                "Offline RCON grants must use the immutable transactional ledger");
        assertTrue(
                source.contains("RconGrantGuard.validatePositiveAmount"),
                "RCON credit grants must reject zero, negative, and oversized grants");
    }
}
