package polaris.hotel.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;
import polaris.hotel.economy.EconomyLedger;

/** :pay was seeded with a permission and texts but never implemented, so ":pay name 100 credits" was said as chat. */
class PayCommandTest {

    private static final String NAMES = "ducket;pixel;shell;diamond";
    private static final ToIntFunction<String> TYPES =
            name -> Map.of("ducket", 0, "pixel", 0, "shell", 4, "diamond", 5).getOrDefault(name, -2);

    @Test
    void onlyAPositiveWholeAmountThatFitsIsAccepted() {
        assertEquals(1, PayCommand.amount("1"));
        assertEquals(250, PayCommand.amount(" 250 "));
        assertEquals(Integer.MAX_VALUE, PayCommand.amount(Integer.toString(Integer.MAX_VALUE)));

        assertEquals(-1, PayCommand.amount("0"));
        assertEquals(-1, PayCommand.amount("-5"));
        assertEquals(-1, PayCommand.amount("2147483648"), "past the integer range");
        assertEquals(-1, PayCommand.amount("99999999999999999999"));
        assertEquals(-1, PayCommand.amount("1.5"));
        assertEquals(-1, PayCommand.amount("abc"));
        assertEquals(-1, PayCommand.amount(""));
        assertEquals(-1, PayCommand.amount(null));
    }

    @Test
    void creditsAndTheConfiguredCurrenciesAreKnown() {
        assertEquals(
                new PayCommand.Currency(EconomyLedger.CREDITS, "credits"),
                PayCommand.currency("Credits", NAMES, TYPES));
        assertEquals(
                new PayCommand.Currency(EconomyLedger.CREDITS, "credits"), PayCommand.currency("credit", NAMES, TYPES));
        assertEquals(new PayCommand.Currency(0, "ducket"), PayCommand.currency("duckets", NAMES, TYPES));
        assertEquals(new PayCommand.Currency(5, "diamond"), PayCommand.currency("DIAMOND", NAMES, TYPES));
        assertEquals(new PayCommand.Currency(4, "shell"), PayCommand.currency("shells", NAMES, TYPES));
    }

    @Test
    void anUnknownCurrencyOrOneWithoutAValidTypeIsRefused() {
        assertNull(PayCommand.currency("gold", NAMES, TYPES));
        assertNull(PayCommand.currency("", NAMES, TYPES));
        assertNull(PayCommand.currency(null, NAMES, TYPES));
        assertNull(PayCommand.currency("ducket", "", TYPES));
        assertNull(PayCommand.currency("ruby", "ruby", TYPES), "a name with no seasonal.currency.<name> type");
    }

    @Test
    void itIsRegisteredAndMovesBothWalletsUnderOneLock() throws Exception {
        String handler = Files.readString(Path.of("src/main/java/polaris/hotel/commands/CommandHandler.java"));
        String command = Files.readString(Path.of("src/main/java/polaris/hotel/commands/PayCommand.java"))
                .replaceAll("\\s+", "");

        assertTrue(handler.contains("addCommand(new PayCommand());"));
        assertTrue(command.contains("super(\"cmd_pay\""), "gated by cmd_pay");
        assertTrue(command.contains("LedgerWalletMutation.coordinated(sender,receiver,"), "both wallets are locked");
        assertTrue(command.contains("PayTransaction.execute("), "debit and credit share a transaction");
        assertTrue(!command.contains("giveCredits(") && !command.contains("givePoints("), "no separate grants");
    }
}
