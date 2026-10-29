package com.eu.habbo.networking.gameserver.auth;

import com.eu.habbo.core.ConfigurationManager;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-visitor limits on the game socket: how many game connections one address may hold open, and a block on
 * ticket logins after repeated failures. A visitor is the {@link ClientAddressBucket} of the address a trusted
 * proxy forwarded, or of the socket peer; a trusted proxy that forwarded no address is not limited.
 */
public final class GameConnectionLimits {

    public static final String MAX_CONNECTIONS_SETTING = "ws.connections.per_ip";
    public static final String MAX_FAILURES_SETTING = "login.sso.max_failures";
    public static final String FAILURE_WINDOW_SETTING = "login.sso.failure_window_sec";
    public static final String BLOCK_SETTING = "login.sso.block_sec";

    private static final int MAX_ENTRIES = 100_000;
    private static final GameConnectionLimits SHARED = new GameConnectionLimits();

    /** {@code maxConnections} or {@code maxFailures} of 0 or less turns that limit off. */
    public record Settings(int maxConnections, int maxFailures, long failureWindowMillis, long blockMillis) {

        public static final Settings DEFAULTS = new Settings(20, 10, 300_000L, 600_000L);

        public static Settings from(ConfigurationManager config) {
            if (config == null) {
                return DEFAULTS;
            }
            return new Settings(
                    config.getInt(MAX_CONNECTIONS_SETTING, DEFAULTS.maxConnections()),
                    config.getInt(MAX_FAILURES_SETTING, DEFAULTS.maxFailures()),
                    Math.max(1, config.getInt(FAILURE_WINDOW_SETTING, 300)) * 1000L,
                    Math.max(1, config.getInt(BLOCK_SETTING, 600)) * 1000L);
        }
    }

    private record Failures(int count, long windowStartMillis, long blockedUntilMillis) {}

    private final Map<String, Integer> open = new ConcurrentHashMap<>();
    private final Map<String, Failures> failures = Caffeine.newBuilder()
            .maximumSize(MAX_ENTRIES)
            .<String, Failures>build()
            .asMap();

    GameConnectionLimits() {}

    public static GameConnectionLimits shared() {
        return SHARED;
    }

    /**
     * The visitor a connection counts under, or null when it is not limited: the forwarded address when a
     * trusted proxy named one, else the peer unless the peer is itself a trusted proxy.
     */
    public static String visitorOf(String forwardedIp, String peerIp, boolean peerIsTrustedProxy) {
        if (forwardedIp != null && !forwardedIp.isEmpty()) {
            return ClientAddressBucket.of(forwardedIp);
        }
        if (peerIp == null || peerIp.isEmpty() || peerIsTrustedProxy) {
            return null;
        }
        return ClientAddressBucket.of(peerIp);
    }

    /** Whether {@link #tryOpen} counts a connection of {@code visitor}, and so whether its close must release one. */
    public static boolean isCounted(String visitor, Settings settings) {
        return visitor != null && settings.maxConnections() > 0;
    }

    /** Counts one more open connection for {@code visitor}; false (and nothing counted) when it is at the cap. */
    public boolean tryOpen(String visitor, Settings settings) {
        if (!isCounted(visitor, settings)) {
            return true;
        }
        boolean[] admitted = {false};
        this.open.compute(visitor, (key, count) -> {
            int current = count == null ? 0 : count;
            if (current >= settings.maxConnections()) {
                return count;
            }
            admitted[0] = true;
            return current + 1;
        });
        return admitted[0];
    }

    /** Releases a connection counted by {@link #tryOpen}. */
    public void close(String visitor) {
        if (visitor == null) {
            return;
        }
        this.open.computeIfPresent(visitor, (key, count) -> count <= 1 ? null : count - 1);
    }

    public int openConnections(String visitor) {
        return visitor == null ? 0 : this.open.getOrDefault(visitor, 0);
    }

    public boolean isLoginBlocked(String visitor, long nowMillis) {
        if (visitor == null) {
            return false;
        }
        Failures current = this.failures.get(visitor);
        return current != null && current.blockedUntilMillis() > nowMillis;
    }

    /** Counts a login with a ticket that matched no user; the visitor is blocked once the window holds too many. */
    public void recordFailedLogin(String visitor, Settings settings, long nowMillis) {
        if (visitor == null || settings.maxFailures() <= 0) {
            return;
        }
        this.failures.compute(visitor, (key, previous) -> {
            if (previous != null && previous.blockedUntilMillis() > nowMillis) {
                return previous;
            }
            if (previous == null || nowMillis - previous.windowStartMillis() >= settings.failureWindowMillis()) {
                previous = new Failures(0, nowMillis, 0L);
            }
            int count = previous.count() + 1;
            long blockedUntil = count >= settings.maxFailures() ? nowMillis + settings.blockMillis() : 0L;
            return new Failures(count, previous.windowStartMillis(), blockedUntil);
        });
    }
}
