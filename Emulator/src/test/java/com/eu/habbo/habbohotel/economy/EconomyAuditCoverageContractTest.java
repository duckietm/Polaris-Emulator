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

        assertTrue(habbo.contains(
                "LedgerWalletMutation.execute(this, new EconomyOperation("));
        assertTrue(housekeepingCredits.contains("\"housekeeping.user.give_credits\""));
        assertTrue(housekeepingCurrency.contains("\"housekeeping.user.give_currency\""));
    }

    @Test
    void firstPartyCodeDoesNotUseLegacyDirectWalletMutators() throws Exception {
        Set<String> callers = new HashSet<>();
        try (var paths = Files.walk(SOURCES)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".java")).toList()) {
                if (path.getFileName().toString().equals("HabboInfo.java")) {
                    continue;
                }
                String source = Files.readString(path);
                if (source.contains(".addCredits(")
                        || source.contains(".setCredits(")
                        || source.contains(".tryAddCredits(")
                        || source.contains(".addCurrencyAmount(")
                        || source.contains(".setCurrencyAmount(")
                        || source.contains(".tryAddCurrencyAmount(")) {
                    callers.add(SOURCES.relativize(path).toString());
                }
            }
        }

        assertEquals(Set.of(), callers,
                "first-party wallet changes must use explicit ledger operations or committed-balance publication");
    }

    @Test
    void catalogPaymentReservationsUseAuditedLedgerMutations() throws Exception {
        String service = Files.readString(SOURCES.resolve(
                "com/eu/habbo/habbohotel/catalog/CatalogPaymentService.java"));

        assertTrue(service.contains("EconomyLedger.executeBatch(operations)"),
                "catalog payment reservations and refunds must be durable ledger operations");
        assertTrue(service.contains("LedgerWalletMutation.applyCommitted("),
                "online balances must publish the committed ledger result without a second snapshot save");
        assertTrue(!service.contains(".tryDebitCatalogPayment("));
        assertTrue(!service.contains(".refundCatalogPayment("));
    }
}
