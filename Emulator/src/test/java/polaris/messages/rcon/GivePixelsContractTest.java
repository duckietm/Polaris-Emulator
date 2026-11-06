package polaris.messages.rcon;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GivePixelsContractTest {
    private static String givePixelsSource() throws Exception {
        return Files.readString(Path.of("src/main/java/polaris/messages/rcon/GivePixels.java"));
    }

    @Test
    void offlinePixelGrantCreatesMissingCurrencyRow() throws Exception {
        String source = givePixelsSource();

        assertTrue(
                source.contains("EconomyLedger.execute"),
                "Offline RCON pixel grants must use the immutable transactional ledger");
        assertTrue(
                source.contains("RconGrantGuard.validatePositiveAmount"),
                "RCON pixel grants must reject zero, negative, and oversized grants");
        assertTrue(
                source.contains("RconUserLookup.userExists"),
                "Offline RCON pixel grants must not create orphan currency rows for missing users");
    }
}
