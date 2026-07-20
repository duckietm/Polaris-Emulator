package com.eu.habbo.habbohotel.users;

import com.eu.habbo.habbohotel.economy.EconomyLedger;
import com.eu.habbo.habbohotel.economy.EconomyMutationResult;
import com.eu.habbo.habbohotel.economy.EconomyOperation;
import java.sql.SQLException;
import java.util.List;

/**
 * Applies durable ledger results to an online wallet without invoking the
 * legacy whole-user snapshot save.
 */
public final class LedgerWalletMutation {
    private LedgerWalletMutation() {}

    public static EconomyMutationResult execute(Habbo habbo, EconomyOperation operation) throws SQLException {
        return executeBatch(habbo, List.of(operation)).getFirst();
    }

    public static List<EconomyMutationResult> executeBatch(Habbo habbo, List<EconomyOperation> operations)
            throws SQLException {
        return coordinated(habbo, () -> executeBatchWhileCoordinated(habbo, operations));
    }

    private static List<EconomyMutationResult> executeBatchWhileCoordinated(
            Habbo habbo, List<EconomyOperation> operations) throws SQLException {
        if (habbo == null || operations == null || operations.isEmpty()) {
            throw new IllegalArgumentException("online ledger operation batch must not be empty");
        }
        int userId = habbo.getHabboInfo().getId();
        if (operations.stream().anyMatch(operation -> operation == null || operation.userId() != userId)) {
            throw new IllegalArgumentException("ledger operation user does not match the online wallet");
        }

        List<EconomyMutationResult> results = EconomyLedger.executeBatch(operations);
        for (int index = 0; index < operations.size(); index++) {
            EconomyOperation operation = operations.get(index);
            applyCommitted(habbo, operation.currencyType(), results.get(index).balanceAfter());
        }
        return results;
    }

    public static void applyCommitted(Habbo habbo, int currencyType, int balance) {
        if (habbo == null) {
            throw new IllegalArgumentException("habbo must not be null");
        }
        synchronized (habbo.getHabboInfo().ledgerMutationLock()) {
            if (currencyType == EconomyLedger.CREDITS) {
                habbo.getHabboInfo().applyPersistedCredits(balance);
                return;
            }
            habbo.getHabboInfo().applyPersistedCurrencyAmount(currencyType, balance);
        }
    }

    public static <T, E extends Exception> T coordinated(Habbo habbo, CoordinatedWork<T, E> work) throws E {
        if (habbo == null || work == null) {
            throw new IllegalArgumentException("coordinated wallet work must not be null");
        }
        synchronized (habbo.getHabboInfo().ledgerMutationLock()) {
            return work.execute();
        }
    }

    public static <T, E extends Exception> T coordinated(
            Habbo firstHabbo, Habbo secondHabbo, CoordinatedWork<T, E> work) throws E {
        if (firstHabbo == null || secondHabbo == null || work == null) {
            throw new IllegalArgumentException("coordinated wallet work must not be null");
        }
        HabboInfo first = firstHabbo.getHabboInfo();
        HabboInfo second = secondHabbo.getHabboInfo();
        if (first == second) {
            synchronized (first.ledgerMutationLock()) {
                return work.execute();
            }
        }
        if (first.getId() > second.getId()) {
            HabboInfo swap = first;
            first = second;
            second = swap;
        }
        synchronized (first.ledgerMutationLock()) {
            synchronized (second.ledgerMutationLock()) {
                return work.execute();
            }
        }
    }

    @FunctionalInterface
    public interface CoordinatedWork<T, E extends Exception> {
        T execute() throws E;
    }
}
