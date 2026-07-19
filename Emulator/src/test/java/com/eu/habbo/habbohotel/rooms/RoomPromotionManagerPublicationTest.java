package com.eu.habbo.habbohotel.rooms;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomPromotionManagerPublicationTest {

    @Test
    void keepsExistingPromotionVisibleUntilReplacementQueryCompletes() throws Exception {
        CountDownLatch queryReached = new CountDownLatch(1);
        CountDownLatch allowQueryToComplete = new CountDownLatch(1);

        ResultSet resultSet = proxy(ResultSet.class, (method, arguments) -> {
            if (method.getName().equals("next")) {
                queryReached.countDown();
                assertTrue(allowQueryToComplete.await(5, TimeUnit.SECONDS));
                return false;
            }
            return defaultValue(method.getReturnType());
        });
        PreparedStatement statement = proxy(PreparedStatement.class, (method, arguments) -> {
            if (method.getName().equals("executeQuery")) {
                return resultSet;
            }
            return defaultValue(method.getReturnType());
        });
        Connection connection = proxy(Connection.class, (method, arguments) -> {
            if (method.getName().equals("prepareStatement")) {
                return statement;
            }
            return defaultValue(method.getReturnType());
        });

        RoomPromotionManager manager = new RoomPromotionManager(null, () -> 42);
        manager.setPromoted(true);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> load = executor.submit(() -> {
                manager.loadPromotion(true, connection);
                return null;
            });

            assertTrue(queryReached.await(5, TimeUnit.SECONDS));
            assertTrue(manager.getPromotedFlag(),
                    "the existing promotion must remain visible while its replacement is loading");

            allowQueryToComplete.countDown();
            load.get(5, TimeUnit.SECONDS);

            assertFalse(manager.getPromotedFlag());
        } finally {
            allowQueryToComplete.countDown();
            executor.shutdownNow();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, JdbcInvocation invocation) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, arguments) -> invocation.invoke(method, arguments));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }

    @FunctionalInterface
    private interface JdbcInvocation {
        Object invoke(java.lang.reflect.Method method, Object[] arguments) throws Throwable;
    }
}
