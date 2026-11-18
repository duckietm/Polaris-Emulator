package polaris.hotel.commands;

import java.sql.Connection;
import java.sql.SQLException;
import polaris.hotel.economy.EconomyLedger;
import polaris.hotel.economy.EconomyMutationResult;
import polaris.hotel.economy.EconomyOperation;
import polaris.hotel.economy.EconomyOperationId;

/**
 * Moves currency from one user to another in one database transaction: the debit and the credit commit together or
 * not at all, so a failure, a disconnect or a second :pay running at the same time can neither duplicate nor lose an
 * amount. The ledger locks both balances and refuses one that would go below zero or past the integer range.
 */
final class PayTransaction {

    static final String REASON = CommandEconomy.reason("cmd_pay");

    enum Status {
        PAID,
        /** The sender's balance does not cover the amount. */
        NOT_ENOUGH,
        /** The receiver's balance would overflow. */
        RECEIVER_FULL
    }

    record Result(Status status, int senderBalance, int receiverBalance) {
        static Result refused(Status status) {
            return new Result(status, -1, -1);
        }
    }

    @FunctionalInterface
    interface Ledger {
        EconomyMutationResult apply(Connection connection, EconomyOperation operation) throws SQLException;
    }

    private PayTransaction() {}

    static Result execute(Connection connection, int senderId, int receiverId, int currencyType, int amount)
            throws SQLException {
        return execute(connection, EconomyLedger::apply, senderId, receiverId, currencyType, amount);
    }

    static Result execute(
            Connection connection, Ledger ledger, int senderId, int receiverId, int currencyType, int amount)
            throws SQLException {
        if (amount <= 0) throw new IllegalArgumentException("a payment must be positive");
        if (senderId == receiverId) throw new IllegalArgumentException("a user cannot pay themselves");

        String operationId = EconomyOperationId.create("cmd_pay:" + senderId);
        connection.setAutoCommit(false);
        try {
            EconomyMutationResult debit;
            try {
                debit = ledger.apply(
                        connection,
                        new EconomyOperation(
                                operationId + ":debit",
                                senderId,
                                senderId,
                                "pay_debit",
                                REASON,
                                currencyType,
                                -amount,
                                null,
                                "to=" + receiverId));
            } catch (IllegalArgumentException refused) {
                connection.rollback();
                return Result.refused(Status.NOT_ENOUGH);
            }

            EconomyMutationResult credit;
            try {
                credit = ledger.apply(
                        connection,
                        new EconomyOperation(
                                operationId + ":credit",
                                receiverId,
                                senderId,
                                "pay_credit",
                                REASON,
                                currencyType,
                                amount,
                                null,
                                "from=" + senderId));
            } catch (IllegalArgumentException refused) {
                connection.rollback();
                return Result.refused(Status.RECEIVER_FULL);
            }

            connection.commit();
            return new Result(Status.PAID, debit.balanceAfter(), credit.balanceAfter());
        } catch (SQLException | RuntimeException exception) {
            try {
                connection.rollback();
            } catch (SQLException rollbackException) {
                exception.addSuppressed(rollbackException);
            }
            throw exception;
        }
    }
}
