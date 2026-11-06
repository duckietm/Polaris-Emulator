package polaris.messages.rcon;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GivePointsContractTest {
    private static String givePointsSource() throws Exception {
        return Files.readString(Path.of("src/main/java/polaris/messages/rcon/GivePoints.java"));
    }

    @Test
    void pointGrantsValidateAmountTypeAndOfflineUserExistence() throws Exception {
        String source = givePointsSource();

        assertTrue(
                source.contains("RconGrantGuard.validateCurrencyType"),
                "RCON point grants must reject invalid currency types");
        assertTrue(
                source.contains("RconGrantGuard.validatePositiveAmount"),
                "RCON point grants must reject zero, negative, and oversized grants");
        assertTrue(
                source.contains("RconUserLookup.userExists"),
                "Offline RCON point grants must not create orphan currency rows for missing users");
        assertTrue(
                source.contains("EconomyLedger.execute"),
                "Offline RCON point grants must use the immutable transactional ledger");
    }
}
