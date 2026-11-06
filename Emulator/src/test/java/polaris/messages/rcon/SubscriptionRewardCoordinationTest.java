package polaris.messages.rcon;

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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import polaris.Emulator;
import polaris.core.ConfigurationManager;
import polaris.core.TextsManager;
import polaris.hotel.GameEnvironment;
import polaris.hotel.commands.SubscriptionCommand;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserManager;
import polaris.hotel.users.UserStats;
import polaris.hotel.users.subscriptions.Subscription;
import polaris.hotel.users.subscriptions.SubscriptionManager;
import polaris.hotel.users.subscriptions.SubscriptionScheduler;

class SubscriptionRewardCoordinationTest {
    enum Mutation {
        RCON,
        COMMAND,
        EXPIRY
    }

    @ParameterizedTest
    @EnumSource(Mutation.class)
    void durationChangesWaitForTheCommittedReward(Mutation mutation) throws Exception {
        var constructor = UserInfo.class.getDeclaredConstructor(int.class, int.class);
        constructor.setAccessible(true);
        UserInfo info = constructor.newInstance(42, 0);
        info.setUsername("reward_user");
        var lockField = UserInfo.class.getDeclaredField("ledgerMutationLock");
        lockField.setAccessible(true);
        Object mutationLock = lockField.get(info);
        User user = mock(User.class);
        UserStats stats = mock(UserStats.class);
        Subscription subscription = mock(Subscription.class);
        AtomicInteger remaining = new AtomicInteger(-1);
        var ownerField = UserStats.class.getDeclaredField("userInfo");
        ownerField.setAccessible(true);
        ownerField.set(stats, info);
        doCallRealMethod().when(stats).removeSubscription(anyString(), anyInt());
        when(user.getUserInfo()).thenReturn(info);
        when(user.getUserStats()).thenReturn(stats);
        when(stats.getSubscription(Subscription.CLUB)).thenReturn(subscription);
        stats.subscriptions = Set.of(subscription);
        when(subscription.isActive()).thenReturn(true);
        when(subscription.getRemaining()).thenAnswer(ignored -> remaining.get());
        UserManager manager = mock(UserManager.class);
        when(manager.getUserInfo(42)).thenReturn(info);
        when(manager.getUser(42)).thenReturn(user);
        when(manager.getUser("reward_user")).thenReturn(user);
        when(manager.getOnlineUsers()).thenReturn(new ConcurrentHashMap<>(java.util.Map.of(42, user)));
        SubscriptionManager subscriptions = new SubscriptionManager();
        subscriptions.types.put(Subscription.CLUB, Subscription.class);
        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getUserManager()).thenReturn(manager);
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
                        payload.type = Subscription.CLUB;
                        payload.action = "remove";
                        var message = new ModifyUserSubscription();
                        message.handle(null, payload);
                        assertEquals(RCONMessage.STATUS_OK, message.status);
                    }
                    case COMMAND -> {
                        GameClient client = mock(GameClient.class);
                        when(client.getUser()).thenReturn(user);
                        mock(SubscriptionCommand.class, CALLS_REAL_METHODS)
                                .handle(client, new String[] {"sub", "reward_user", Subscription.CLUB, "remove"});
                    }
                    case EXPIRY -> {
                        // The sweep itself, not run(): the scheduler's run() only starts
                        // its chain of ticks, and an enabled one would need the threading
                        // pool this test does not stand up.
                        SubscriptionScheduler scheduler = mock(SubscriptionScheduler.class, CALLS_REAL_METHODS);
                        scheduler.expireSubscriptions();
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
