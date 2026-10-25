package com.eu.habbo.habbohotel.users.subscriptions;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * An HC payday row pays once. The payout used to come first and the row was marked
 * afterwards with no condition, so two sessions of the same account reading the same
 * unclaimed row each paid it out.
 */
class PayDayClaimOnceTest {

    private static final Path SOURCE =
            Path.of("src/main/java/com/eu/habbo/habbohotel/users/subscriptions/SubscriptionHabboClub.java");

    @Test
    void theRowIsClaimedBeforeAnythingIsPaid() throws Exception {
        String source = Files.readString(SOURCE);

        assertTrue(
                source.contains("UPDATE logs_hc_payday SET claimed = 1 WHERE id = ? AND claimed = 0"),
                "the claim must only take a row that is still unclaimed:\n" + source);

        int claim = source.indexOf("claimPayDayRow(connection, logId)");
        int pay = source.indexOf("claimPayDay(habbo, totalPayout, currency)");

        assertTrue(claim > 0 && pay > 0, source);
        assertTrue(claim < pay, "the row is claimed before the payout");
    }

    @Test
    void aPayoutThatDidNotHappenGivesTheRowBack() throws Exception {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("releasePayDayRow(connection, logId)"), "a failed payout must not eat the row");
        assertTrue(source.contains("UPDATE logs_hc_payday SET claimed = 0 WHERE id = ?"), source);
    }
}
