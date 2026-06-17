package com.eu.habbo.messages.incoming.housekeeping;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HousekeepingGrantMutationContractTest {
    private static final Path CREDITS_SOURCE = Path.of(
            "src/main/java/com/eu/habbo/messages/incoming/housekeeping/HousekeepingGiveCreditsEvent.java");
    private static final Path CURRENCY_SOURCE = Path.of(
            "src/main/java/com/eu/habbo/messages/incoming/housekeeping/HousekeepingGiveCurrencyEvent.java");
    private static final Path GRANT_ITEM_SOURCE = Path.of(
            "src/main/java/com/eu/habbo/messages/incoming/housekeeping/HousekeepingGrantItemEvent.java");

    @Test
    void housekeepingGrantsRejectNegativeOrOversizedAmountsServerSide() throws IOException {
        String credits = Files.readString(CREDITS_SOURCE);
        String currency = Files.readString(CURRENCY_SOURCE);

        assertTrue(credits.contains("HousekeepingMutationGuard.isPositiveGrantAmount(amount)"),
                "credit grants must only accept positive bounded amounts");
        assertTrue(currency.contains("HousekeepingMutationGuard.isPositiveGrantAmount(amount)"),
                "currency grants must only accept positive bounded amounts");
    }

    @Test
    void housekeepingCurrencyGrantsRejectInvalidTypesAndMissingUsers() throws IOException {
        String currency = Files.readString(CURRENCY_SOURCE);

        assertTrue(currency.contains("HousekeepingMutationGuard.isCurrencyType(currencyType)"),
                "currency grants must reject negative currency types");
        assertTrue(currency.contains("HousekeepingMutationGuard.userExists(userId)"),
                "offline currency grants must not create orphan users_currency rows");
    }

    @Test
    void housekeepingItemGrantsRequireRealUsersAndItemsBeforeInsert() throws IOException {
        String grantItem = Files.readString(GRANT_ITEM_SOURCE);

        int userCheck = grantItem.indexOf("HousekeepingMutationGuard.userExists(userId)");
        int itemCheck = grantItem.indexOf("HousekeepingMutationGuard.itemExists(itemId)");
        int insert = grantItem.indexOf("INSERT INTO items");

        assertTrue(userCheck >= 0, "item grants must check the target user exists");
        assertTrue(itemCheck >= 0, "item grants must check the item base exists");
        assertTrue(userCheck < insert, "target user must be validated before item insert");
        assertTrue(itemCheck < insert, "item base must be validated before item insert");
    }
}
