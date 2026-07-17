package com.eu.habbo.habbohotel.users;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WalletMutationContractTest {
    @Test
    void creditsAndCurrenciesUseCheckedArithmeticUnderTheWalletLock() throws Exception {
        String info = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/users/HabboInfo.java"));

        int tryCredits = info.indexOf("boolean tryAddCredits(int credits)");
        int creditLock = info.indexOf("synchronized (this.currencyLock)", tryCredits);
        int creditMath = info.indexOf("WalletBalanceMath.checkedBalance(this.credits, credits)", creditLock);

        int tryCurrency = info.indexOf("boolean tryAddCurrencyAmount(int type, int amount)");
        int currencyLock = info.indexOf("synchronized (this.currencyLock)", tryCurrency);
        int currencyMath = info.indexOf("WalletBalanceMath.checkedBalance(current, amount)", currencyLock);

        assertTrue(creditLock > tryCredits && creditMath > creditLock,
                "credit read-modify-write must be checked while holding the wallet lock");
        assertTrue(currencyLock > tryCurrency && currencyMath > currencyLock,
                "currency read-modify-write must be checked while holding the wallet lock");
    }

    @Test
    void paidCustomBadgeDebitIsAtomicAndCreateIsSerializedPerUser() throws Exception {
        String badge = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/habbohotel/users/custombadge/CustomBadgeManager.java"));

        int create = badge.indexOf("public CustomBadge create(");
        int userLock = badge.indexOf("synchronized (userLock)", create);
        int count = badge.indexOf("this.countForUser(userId)", userLock);
        int atomicCreditDebit = badge.indexOf("habbo.tryTakeCredits(price)", count);
        int atomicPointDebit = badge.indexOf("habbo.tryTakePoints(currencyType, price)", count);

        assertTrue(userLock > create, "concurrent creates for one user must serialize");
        assertTrue(count > userLock, "badge cap check must happen inside the user mutation lock");
        assertTrue(atomicCreditDebit > count, "credit affordability and debit must be one wallet operation");
        assertTrue(atomicPointDebit > count, "point affordability and debit must be one wallet operation");
    }

    @Test
    void debitPluginsCannotSilentlyReduceTheAmountCharged() throws Exception {
        String habbo = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/users/Habbo.java"));
        String marketplace = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/habbohotel/catalog/marketplace/MarketPlace.java"));

        assertTrue(habbo.contains("event.credits != -credits"),
                "a successful exact credit debit must remove the requested amount");
        assertTrue(habbo.contains("event.type != type || event.points != -points"),
                "a successful exact points debit must preserve currency type and amount");
        assertTrue(marketplace.contains("event.credits != -price"));
        assertTrue(marketplace.contains(
                "event.type != MARKETPLACE_CURRENCY || event.points != -price"));
    }
}
