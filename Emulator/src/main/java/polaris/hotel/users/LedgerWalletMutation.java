package polaris.hotel.users;

import java.sql.SQLException;
import java.util.List;
import polaris.hotel.economy.EconomyLedger;
import polaris.hotel.economy.EconomyMutationResult;
import polaris.hotel.economy.EconomyOperation;

/**
 * Applies durable ledger results to an online wallet without invoking the
 * legacy whole-user snapshot save.
 */
public final class LedgerWalletMutation {
    private LedgerWalletMutation() {}

    public static EconomyMutationResult execute(User user, EconomyOperation operation) throws SQLException {
        return executeBatch(user, List.of(operation)).getFirst();
    }

    public static List<EconomyMutationResult> executeBatch(User user, List<EconomyOperation> operations)
            throws SQLException {
        return coordinated(user, () -> executeBatchWhileCoordinated(user, operations));
    }

    private static List<EconomyMutationResult> executeBatchWhileCoordinated(
            User user, List<EconomyOperation> operations) throws SQLException {
        if (user == null || operations == null || operations.isEmpty()) {
            throw new IllegalArgumentException("online ledger operation batch must not be empty");
        }
        int userId = user.getUserInfo().getId();
        if (operations.stream().anyMatch(operation -> operation == null || operation.userId() != userId)) {
            throw new IllegalArgumentException("ledger operation user does not match the online wallet");
        }

        List<EconomyMutationResult> results = EconomyLedger.executeBatch(operations);
        for (int index = 0; index < operations.size(); index++) {
            EconomyOperation operation = operations.get(index);
            applyCommitted(user, operation.currencyType(), results.get(index).balanceAfter());
        }
        return results;
    }

    public static void applyCommitted(User user, int currencyType, int balance) {
        if (user == null) {
            throw new IllegalArgumentException("habbo must not be null");
        }
        synchronized (user.getUserInfo().ledgerMutationLock()) {
            if (currencyType == EconomyLedger.CREDITS) {
                user.getUserInfo().applyPersistedCredits(balance);
                return;
            }
            user.getUserInfo().applyPersistedCurrencyAmount(currencyType, balance);
        }
    }

    public static <T, E extends Exception> T coordinated(User user, CoordinatedWork<T, E> work) throws E {
        if (user == null || work == null) {
            throw new IllegalArgumentException("coordinated wallet work must not be null");
        }
        synchronized (user.getUserInfo().ledgerMutationLock()) {
            return work.execute();
        }
    }

    public static <T, E extends Exception> T coordinated(User firstUser, User secondUser, CoordinatedWork<T, E> work)
            throws E {
        if (firstUser == null || secondUser == null || work == null) {
            throw new IllegalArgumentException("coordinated wallet work must not be null");
        }
        UserInfo first = firstUser.getUserInfo();
        UserInfo second = secondUser.getUserInfo();
        if (first == second) {
            synchronized (first.ledgerMutationLock()) {
                return work.execute();
            }
        }
        if (first.getId() > second.getId()) {
            UserInfo swap = first;
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
