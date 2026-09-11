package com.eu.habbo.messages.rcon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.core.TextsManager;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.commands.SubscriptionCommand;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.habbohotel.users.HabboStats;
import com.eu.habbo.habbohotel.users.subscriptions.Subscription;
import com.eu.habbo.habbohotel.users.subscriptions.SubscriptionManager;
import com.eu.habbo.habbohotel.users.subscriptions.SubscriptionScheduler;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SubscriptionRewardCoordinationTest {
    enum Mutation {
        RCON,
        COMMAND,
        EXPIRY
    }

    @ParameterizedTest
    @EnumSource(Mutation.class)
    void durationChangesWaitForTheCommittedReward(Mutation mutation) throws Exception {
        var constructor = HabboInfo.class.getDeclaredConstructor(int.class, int.class);
        constructor.setAccessible(true);
        HabboInfo info = constructor.newInstance(42, 0);
        info.setUsername("reward_user");
        var lockField = HabboInfo.class.getDeclaredField("ledgerMutationLock");
        lockField.setAccessible(true);
        Object mutationLock = lockField.get(info);
        Habbo habbo = mock(Habbo.class);
        HabboStats stats = mock(HabboStats.class);
        Subscription subscription = mock(Subscription.class);
        AtomicInteger remaining = new AtomicInteger(-1);
        var ownerField = HabboStats.class.getDeclaredField("habboInfo");
        ownerField.setAccessible(true);
        ownerField.set(stats, info);
        doCallRealMethod().when(stats).removeSubscription(anyString(), anyInt());
        when(habbo.getHabboInfo()).thenReturn(info);
        when(habbo.getHabboStats()).thenReturn(stats);
        when(stats.getSubscription(Subscription.HABBO_CLUB)).thenReturn(subscription);
        stats.subscriptions = Set.of(subscription);
        when(subscription.isActive()).thenReturn(true);
        when(subscription.getRemaining()).thenAnswer(ignored -> remaining.get());
        HabboManager manager = mock(HabboManager.class);
        when(manager.getHabboInfo(42)).thenReturn(info);
        when(manager.getHabbo(42)).thenReturn(habbo);
        when(manager.getHabbo("reward_user")).thenReturn(habbo);
        when(manager.getOnlineHabbos()).thenReturn(new ConcurrentHashMap<>(java.util.Map.of(42, habbo)));
        SubscriptionManager subscriptions = new SubscriptionManager();
        subscriptions.types.put(Subscription.HABBO_CLUB, Subscription.class);
        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getHabboManager()).thenReturn(manager);
        when(environment.getSubscriptionManager()).thenReturn(subscriptions);
        TextsManager texts = mock(TextsManager.class);
        when(texts.getValue(anyString(), anyString())).thenAnswer(call -> call.getArgument(1));
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            try (var emulator = mockStatic(Emulator.class)) {
                emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
                emulator.when(Emulator::getConfig).thenReturn(mock(ConfigurationManager.class));
                emulator.when(Emulator::getTexts).thenReturn(texts);
                started.countDown();
                switch (mutation) {
                    case RCON -> {
                        var payload = new ModifyUserSubscription.JSON();
                        payload.user_id = 42;
                        payload.type = Subscription.HABBO_CLUB;
                        payload.action = "remove";
                        var message = new ModifyUserSubscription();
                        message.handle(null, payload);
                        assertEquals(RCONMessage.STATUS_OK, message.status);
                    }
                    case COMMAND -> {
                        GameClient client = mock(GameClient.class);
                        when(client.getHabbo()).thenReturn(habbo);
                        mock(SubscriptionCommand.class, CALLS_REAL_METHODS)
                                .handle(client, new String[] {"sub", "reward_user", Subscription.HABBO_CLUB, "remove"});
                    }
                    case EXPIRY -> {
                        SubscriptionScheduler scheduler = mock(SubscriptionScheduler.class, CALLS_REAL_METHODS);
                        scheduler.setDisposed(true);
                        scheduler.run();
                    }
                }
            } catch (Throwable exception) {
                failure.set(exception);
            }
        });
        worker.setDaemon(true);
        synchronized (mutationLock) {
            worker.start();
            org.junit.jupiter.api.Assertions.assertTrue(started.await(5, TimeUnit.SECONDS));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (worker.isAlive() && worker.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline)
                Thread.onSpinWait();
            assertEquals(Thread.State.BLOCKED, worker.getState());
            remaining.set(172800);
        }
        worker.join(5000);
        assertFalse(worker.isAlive());
        assertNull(failure.get());
        verify(subscription, never()).onExpired();
        verify(subscription, never()).setActive(false);
        if (mutation != Mutation.EXPIRY) verify(subscription).addDuration(-172800);
    }
}
