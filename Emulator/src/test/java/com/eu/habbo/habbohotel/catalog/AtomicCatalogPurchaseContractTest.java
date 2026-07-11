package com.eu.habbo.habbohotel.catalog;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AtomicCatalogPurchaseContractTest {
    @Test
    void coordinatorCommitsAssetsAndConditionalChargesTogether() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/habbohotel/catalog/CatalogPurchaseTransaction.java"));

        int begin = source.indexOf("connection.setAutoCommit(false)");
        int persist = source.indexOf("work.persist(connection)", begin);
        int credit = source.indexOf("chargeCredits(connection", persist);
        int points = source.indexOf("chargePoints(connection", credit);
        int commit = source.indexOf("connection.commit()", points);

        assertTrue(begin > -1);
        assertTrue(persist > begin);
        assertTrue(credit > persist);
        assertTrue(points > credit);
        assertTrue(commit > points);
        assertTrue(source.contains("UPDATE users SET credits = credits - ? WHERE id = ? AND credits >= ?"));
        assertTrue(source.contains("UPDATE users_currency SET amount = amount - ? WHERE user_id = ? AND type = ? AND amount >= ?"));
        assertTrue(source.contains("connection.rollback()"));
    }
}
