package com.eu.habbo.habbohotel.economy;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyAuditCoverageContractTest {
    private static final Path SOURCES = Path.of("src/main/java");

    @Test
    void durableWalletSqlIsCentralizedInLedgerSnapshotAndAccountBootstrap() throws Exception {
        Set<String> owners = new HashSet<>();
        try (var paths = Files.walk(SOURCES)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                if (source.contains("UPDATE users SET credits")
                        || source.contains("INSERT INTO users_currency")
                        || source.contains("UPDATE users_currency SET amount")) {
                    owners.add(path.getFileName().toString());
                }
            }
        }

        assertEquals(Set.of("EconomyLedger.java", "HabboInfo.java", "RegistrationSupport.java"), owners,
                "new durable wallet mutations must use EconomyLedger; only snapshot persistence and account bootstrap bypass it");
    }

    @Test
    void primaryEconomicFlowsUseTheLedgerInsideTheirTransaction() throws Exception {
        for (String relative : Set.of(
                "com/eu/habbo/habbohotel/catalog/CatalogPurchaseTransaction.java",
                "com/eu/habbo/habbohotel/catalog/marketplace/MarketPlacePurchaseTransaction.java",
                "com/eu/habbo/habbohotel/rooms/RoomTradeTransaction.java",
                "com/eu/habbo/messages/incoming/rooms/items/RedeemItemTransaction.java")) {
            String source = Files.readString(SOURCES.resolve(relative));
            assertTrue(source.contains("EconomyLedger.apply(connection"), relative);
        }
    }

    @Test
    void genericOnlineAndAdministrativeGrantsUseExplicitAuditedReasons() throws Exception {
        String habbo = Files.readString(SOURCES.resolve("com/eu/habbo/habbohotel/users/Habbo.java"));
        String housekeepingCredits = Files.readString(SOURCES.resolve(
                "com/eu/habbo/messages/incoming/housekeeping/HousekeepingGiveCreditsEvent.java"));
        String housekeepingCurrency = Files.readString(SOURCES.resolve(
                "com/eu/habbo/messages/incoming/housekeeping/HousekeepingGiveCurrencyEvent.java"));

        assertTrue(habbo.contains("EconomyLedger.execute(new EconomyOperation("));
        assertTrue(housekeepingCredits.contains("\"housekeeping.user.give_credits\""));
        assertTrue(housekeepingCurrency.contains("\"housekeeping.user.give_currency\""));
    }

    @Test
    void firstPartyCodeDoesNotUseLegacyDirectCreditMutators() throws Exception {
        Set<String> callers = new HashSet<>();
        try (var paths = Files.walk(SOURCES)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".java")).toList()) {
                if (path.getFileName().toString().equals("HabboInfo.java")) {
                    continue;
                }
                String source = Files.readString(path);
                if (source.contains(".addCredits(")
                        || source.contains(".setCredits(")
                        || source.contains(".tryAddCredits(")) {
                    callers.add(SOURCES.relativize(path).toString());
                }
            }
        }

        assertEquals(Set.of(), callers,
                "first-party credit changes must use explicit ledger operations or committed-balance publication");
    }
}
