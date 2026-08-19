package com.eu.habbo.session;

import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.database.Database;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SessionRecoveryRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionRecoveryRuntime.class);
    private static final AtomicReference<SessionRecoveryService> SERVICE = new AtomicReference<>();

    private SessionRecoveryRuntime() {}

    public static void install(ConfigurationManager configuration, Database database) {
        if (!configuration.getBoolean("session.recovery.enabled", false)) {
            SERVICE.set(null);
            return;
        }
        int seconds = Math.max(30, Math.min(600, configuration.getInt("session.recovery.ttl.seconds", 120)));
        SERVICE.set(new SessionRecoveryService(
                new JdbcRecoveryTicketStore(database.getDataSource()),
                Clock.systemUTC(),
                new SecureRandom(),
                Duration.ofSeconds(seconds)));
    }

    public static String issue(int userId) {
        SessionRecoveryService service = SERVICE.get();
        if (service == null) {
            return "";
        }
        try {
            return service.issue(userId);
        } catch (RuntimeException exception) {
            LOGGER.error("Could not issue a session recovery ticket", exception);
            return "";
        }
    }

    public static OptionalInt consume(String token) {
        SessionRecoveryService service = SERVICE.get();
        if (service == null) {
            return OptionalInt.empty();
        }
        try {
            return service.consume(token);
        } catch (RuntimeException exception) {
            LOGGER.error("Could not consume a session recovery ticket", exception);
            return OptionalInt.empty();
        }
    }

    public static void checkpoint(Iterable<Integer> userIds) {
        SessionRecoveryService service = SERVICE.get();
        if (service == null) {
            return;
        }
        try {
            service.checkpoint(userIds);
        } catch (RuntimeException exception) {
            LOGGER.error("Could not checkpoint session recovery tickets", exception);
        }
    }
}
