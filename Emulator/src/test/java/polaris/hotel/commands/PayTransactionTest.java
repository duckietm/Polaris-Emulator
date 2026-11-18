package polaris.hotel.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import polaris.hotel.economy.EconomyLedger;
import polaris.hotel.economy.EconomyMutationResult;
import polaris.hotel.economy.EconomyOperation;
import polaris.hotel.users.LedgerWalletMutation;
import polaris.hotel.users.OnlineWallets;
import polaris.hotel.users.User;

/** A :pay debits and credits in one transaction: both happen, or neither does. */
class PayTransactionTest {

    private static final int SENDER = 7;
    private static final int RECEIVER = 9;

    /**
     * A ledger over in-memory balances with the real ledger's rules, whose writes are staged per connection and only
     * land on commit. Reading and writing are deliberately not atomic, so two payments that were not serialised by
     * the caller could both spend the same balance.
     */
    private static final class StagedLedger implements PayTransaction.Ledger {
        final Map<Integer, Integer> committed = new HashMap<>();
        final Map<Connection, Map<Integer, Integer>> staged = new HashMap<>();
        final List<EconomyOperation> applied = new ArrayList<>();
        SQLException failCredit;

        StagedLedger(int sender, int receiver) {
            this.committed.put(SENDER, sender);
            this.committed.put(RECEIVER, receiver);
        }

        @Override
        public synchronized EconomyMutationResult apply(Connection connection, EconomyOperation operation)
                throws SQLException {
            if (this.failCredit != null && operation.delta() > 0) throw this.failCredit;
            Map<Integer, Integer> pending = this.staged.computeIfAbsent(connection, ignored -> new HashMap<>());
            int before = pending.getOrDefault(operation.userId(), this.committed.get(operation.userId()));
            Thread.yield();
            int after = EconomyLedger.checkedBalance(before, operation.delta());
            pending.put(operation.userId(), after);
            this.applied.add(operation);
            return new EconomyMutationResult(before, after, true);
        }

        Connection connection() throws SQLException {
            Connection connection = mock(Connection.class);
            Mockito.doAnswer(invocation -> {
                        synchronized (this) {
                            this.committed.putAll(this.staged.getOrDefault(connection, Map.of()));
                            this.staged.remove(connection);
                        }
                        return null;
                    })
                    .when(connection)
                    .commit();
            Mockito.doAnswer(invocation -> {
                        synchronized (this) {
                            this.staged.remove(connection);
                        }
                        return null;
                    })
                    .when(connection)
                    .rollback();
            return connection;
        }
    }

    @Test
    void aPaymentMovesTheAmountAndRecordsBothSidesUnderOneOperation() throws Exception {
        StagedLedger ledger = new StagedLedger(100, 5);

        PayTransaction.Result result =
                PayTransaction.execute(ledger.connection(), ledger, SENDER, RECEIVER, EconomyLedger.CREDITS, 40);

        assertEquals(PayTransaction.Status.PAID, result.status());
        assertEquals(60, result.senderBalance());
        assertEquals(45, result.receiverBalance());
        assertEquals(Map.of(SENDER, 60, RECEIVER, 45), ledger.committed);

        EconomyOperation debit = ledger.applied.get(0);
        EconomyOperation credit = ledger.applied.get(1);
        assertEquals(-40, debit.delta());
        assertEquals(SENDER, debit.userId());
        assertEquals(40, credit.delta());
        assertEquals(RECEIVER, credit.userId());
        assertEquals(SENDER, credit.actorId(), "the receiver's line names who paid");
        assertEquals("command.cmd_pay", debit.reason());
        assertEquals("command.cmd_pay", credit.reason());
        assertTrue(debit.operationId().startsWith("cmd_pay:7:"));
        assertEquals(
                debit.operationId().replace(":debit", ""), credit.operationId().replace(":credit", ""));
    }

    @Test
    void aSenderWhoCannotCoverItChangesNothing() throws Exception {
        StagedLedger ledger = new StagedLedger(30, 5);

        PayTransaction.Result result = PayTransaction.execute(ledger.connection(), ledger, SENDER, RECEIVER, 5, 31);

        assertEquals(PayTransaction.Status.NOT_ENOUGH, result.status());
        assertEquals(Map.of(SENDER, 30, RECEIVER, 5), ledger.committed);
        assertEquals(0, ledger.applied.size());
    }

    @Test
    void aReceiverWhoseBalanceWouldOverflowRollsTheDebitBack() throws Exception {
        StagedLedger ledger = new StagedLedger(100, Integer.MAX_VALUE - 10);

        PayTransaction.Result result =
                PayTransaction.execute(ledger.connection(), ledger, SENDER, RECEIVER, EconomyLedger.CREDITS, 50);

        assertEquals(PayTransaction.Status.RECEIVER_FULL, result.status());
        assertEquals(Map.of(SENDER, 100, RECEIVER, Integer.MAX_VALUE - 10), ledger.committed);
    }

    @Test
    void aDatabaseFailureAfterTheDebitRollsItBackAndIsReported() throws Exception {
        StagedLedger ledger = new StagedLedger(100, 5);
        ledger.failCredit = new SQLException("connection lost");

        assertThrows(
                SQLException.class,
                () -> PayTransaction.execute(ledger.connection(), ledger, SENDER, RECEIVER, EconomyLedger.CREDITS, 50));
        assertEquals(Map.of(SENDER, 100, RECEIVER, 5), ledger.committed);
    }

    @Test
    void selfPaymentAndNonPositiveAmountsNeverReachTheLedger() throws Exception {
        StagedLedger ledger = new StagedLedger(100, 5);

        assertThrows(
                IllegalArgumentException.class,
                () -> PayTransaction.execute(ledger.connection(), ledger, SENDER, SENDER, EconomyLedger.CREDITS, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> PayTransaction.execute(ledger.connection(), ledger, SENDER, RECEIVER, EconomyLedger.CREDITS, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> PayTransaction.execute(ledger.connection(), ledger, SENDER, RECEIVER, EconomyLedger.CREDITS, -5));
        assertEquals(0, ledger.applied.size());
    }

    @Test
    void twoPaymentsAtOnceCannotSpendTheSameBalanceTwice() throws Exception {
        for (int round = 0; round < 50; round++) {
            StagedLedger ledger = new StagedLedger(100, 0);
            User sender = user(SENDER);
            User receiver = user(RECEIVER);
            CountDownLatch start = new CountDownLatch(1);

            Callable<PayTransaction.Result> pay = () -> {
                start.await();
                return LedgerWalletMutation.coordinated(
                        sender,
                        receiver,
                        () -> PayTransaction.execute(
                                ledger.connection(), ledger, SENDER, RECEIVER, EconomyLedger.CREDITS, 80));
            };

            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                Future<PayTransaction.Result> first = executor.submit(pay);
                Future<PayTransaction.Result> second = executor.submit(pay);
                start.countDown();

                List<PayTransaction.Status> statuses =
                        List.of(first.get().status(), second.get().status());
                assertTrue(statuses.contains(PayTransaction.Status.PAID), statuses.toString());
                assertTrue(statuses.contains(PayTransaction.Status.NOT_ENOUGH), statuses.toString());
            }
            assertEquals(Map.of(SENDER, 20, RECEIVER, 80), ledger.committed);
        }
    }

    private static User user(int id) {
        return OnlineWallets.online(id);
    }
}
