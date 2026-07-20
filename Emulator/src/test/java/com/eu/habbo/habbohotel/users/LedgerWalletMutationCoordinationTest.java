package com.eu.habbo.habbohotel.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class LedgerWalletMutationCoordinationTest {
    @Test
    void serializesDurableMutationAndMemoryPublicationForOneOnlineWallet() throws Exception {
        HabboInfo info = new HabboInfo(42, 100);
        Habbo habbo = mock(Habbo.class);
        when(habbo.getHabboInfo()).thenReturn(info);

        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> LedgerWalletMutation.coordinated(habbo, () -> {
                firstEntered.countDown();
                releaseFirst.await();
                return 1;
            }));
            firstEntered.await();

            var second = executor.submit(() -> LedgerWalletMutation.coordinated(habbo, () -> 2));

            try {
                assertThrows(TimeoutException.class, () -> second.get(100, TimeUnit.MILLISECONDS));
            } finally {
                releaseFirst.countDown();
            }

            assertEquals(1, first.get());
            assertEquals(2, second.get());
        }
    }
}
