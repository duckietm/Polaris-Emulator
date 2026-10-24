package com.eu.habbo.networking.gameserver.auth;

import com.eu.habbo.Emulator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class AuthRateLimiter {

    private static final Map<String, AtomicReference<State>> STATE = new ConcurrentHashMap<>();
    private static final Map<String, AtomicReference<ProbeState>> PROBE_STATE = new ConcurrentHashMap<>();

    // Guessing one account's password is throttled per account as well as per IP:
    // an IP-only counter is cleared by any successful login, so four guesses at a
    // staff account plus one login to the attacker's own account repeats forever.
    private static final Map<String, AtomicReference<AccountState>> ACCOUNT_STATE = new ConcurrentHashMap<>();
    private static final int MAX_BACKOFF_DOUBLINGS = 4;

    // Both maps are keyed by client IP and reachable by unauthenticated traffic.
    // recordSuccess removes STATE on login, but failed-only and probe-only IPs
    // never get removed otherwise — unbounded growth over the JVM lifetime.
    // Opportunistically evict window-expired entries once the maps get large.
    private static final int SWEEP_THRESHOLD = 10_000;
    private static final long SWEEP_MIN_INTERVAL_MS = 60_000L;
    private static volatile long lastSweepMillis = 0L;

    private AuthRateLimiter() {}

    private static void maybeSweep(long now) {
        if (STATE.size() < SWEEP_THRESHOLD
                && PROBE_STATE.size() < SWEEP_THRESHOLD
                && ACCOUNT_STATE.size() < SWEEP_THRESHOLD) return;
        if (now - lastSweepMillis < SWEEP_MIN_INTERVAL_MS) return;
        lastSweepMillis = now;

        long stateWindowMs = configInt("login.ratelimit.window_sec", 60) * 1000L;
        STATE.entrySet().removeIf(e -> {
            State s = e.getValue().get();
            return s == null || (s.lockedUntilMillis <= now && (now - s.windowStartMillis) > stateWindowMs);
        });

        long probeWindowMs = configInt("login.probe.window_sec", 60) * 1000L;
        PROBE_STATE.entrySet().removeIf(e -> {
            ProbeState p = e.getValue().get();
            return p == null || (now - p.windowStartMillis) > probeWindowMs;
        });

        long accountWindowMs = accountWindowMillis();
        ACCOUNT_STATE.entrySet().removeIf(e -> {
            AccountState s = e.getValue().get();
            return s == null || (s.lockedUntilMillis <= now && (now - s.windowStartMillis) > accountWindowMs);
        });
    }

    public static boolean isLocked(String ip) {
        if (!isEnabled() || ip == null || ip.isEmpty()) return false;

        AtomicReference<State> ref = STATE.get(ip);
        if (ref == null) return false;

        State current = ref.get();
        return current != null && current.lockedUntilMillis > System.currentTimeMillis();
    }

    public static long secondsUntilUnlock(String ip) {
        AtomicReference<State> ref = STATE.get(ip);
        if (ref == null) return 0;

        State current = ref.get();
        if (current == null) return 0;

        long remainingMs = current.lockedUntilMillis - System.currentTimeMillis();
        return remainingMs > 0 ? (remainingMs / 1000L) + 1L : 0L;
    }

    public static void recordFailure(String ip) {
        if (!isEnabled() || ip == null || ip.isEmpty()) return;

        long now = System.currentTimeMillis();
        maybeSweep(now);
        long windowMs = configInt("login.ratelimit.window_sec", 60) * 1000L;
        int maxAttempts = configInt("login.ratelimit.max_attempts", 5);
        long lockoutMs = configInt("login.ratelimit.lockout_sec", 120) * 1000L;

        STATE.computeIfAbsent(ip, k -> new AtomicReference<>(new State(0, 0L, 0L)))
                .updateAndGet(prev -> {
                    if (prev == null || (now - prev.windowStartMillis) > windowMs) {
                        return new State(1, now, 0L);
                    }

                    int attempts = prev.attempts + 1;
                    long lockedUntil = attempts >= maxAttempts ? now + lockoutMs : 0L;
                    return new State(attempts, prev.windowStartMillis, lockedUntil);
                });
    }

    public static void recordSuccess(String ip) {
        if (ip == null || ip.isEmpty()) return;
        STATE.remove(ip);
    }

    /**
     * Clears the throttles for one authenticated IP and account pair. Only the
     * account that actually proved its password is cleared, so a login to an
     * attacker's own account no longer resets the attempts spent on someone
     * else's.
     */
    public static void recordSuccess(String ip, String username) {
        recordSuccess(ip);
        String key = accountKey(username);
        if (key != null) ACCOUNT_STATE.remove(key);
    }

    /** Records a failed credential check against the account that was named. */
    public static void recordAccountFailure(String username) {
        if (!isEnabled()) return;
        String key = accountKey(username);
        if (key == null) return;

        long now = System.currentTimeMillis();
        maybeSweep(now);
        long windowMs = accountWindowMillis();
        int maxAttempts = configInt("login.ratelimit.account.max_attempts", 10);
        long lockoutMs = configInt("login.ratelimit.account.lockout_sec", 300) * 1000L;

        ACCOUNT_STATE
                .computeIfAbsent(key, k -> new AtomicReference<>(new AccountState(0, 0L, 0L, 0)))
                .updateAndGet(prev -> {
                    if (prev == null) {
                        return new AccountState(1, now, 0L, 0);
                    }
                    if ((now - prev.windowStartMillis) > windowMs) {
                        // A new window of attempts, but an active lockout outlives it:
                        // a long backoff must not be shortened by waiting it out badly.
                        return new AccountState(1, now, prev.lockedUntilMillis, prev.lockouts);
                    }

                    int attempts = prev.attempts + 1;
                    if (attempts < maxAttempts) {
                        return new AccountState(
                                attempts, prev.windowStartMillis, prev.lockedUntilMillis, prev.lockouts);
                    }
                    // Each further lockout of the same account lasts twice as long,
                    // so a slow, distributed guessing run gets steadily cheaper to
                    // outrun than the account is to guess.
                    int lockouts = Math.min(prev.lockouts + 1, MAX_BACKOFF_DOUBLINGS + 1);
                    long lockedFor = lockoutMs << (lockouts - 1);
                    return new AccountState(0, now, now + lockedFor, lockouts);
                });
    }

    public static boolean isAccountLocked(String username) {
        if (!isEnabled()) return false;
        String key = accountKey(username);
        if (key == null) return false;

        AtomicReference<AccountState> ref = ACCOUNT_STATE.get(key);
        if (ref == null) return false;

        AccountState current = ref.get();
        return current != null && current.lockedUntilMillis > System.currentTimeMillis();
    }

    public static long secondsUntilAccountUnlock(String username) {
        String key = accountKey(username);
        if (key == null) return 0;

        AtomicReference<AccountState> ref = ACCOUNT_STATE.get(key);
        if (ref == null) return 0;

        AccountState current = ref.get();
        if (current == null) return 0;

        long remainingMs = current.lockedUntilMillis - System.currentTimeMillis();
        return remainingMs > 0 ? (remainingMs / 1000L) + 1L : 0L;
    }

    /** Visible for tests: drops every counter so cases cannot leak into each other. */
    static void resetForTests() {
        STATE.clear();
        PROBE_STATE.clear();
        ACCOUNT_STATE.clear();
    }

    private static long accountWindowMillis() {
        return configInt("login.ratelimit.account.window_sec", 900) * 1000L;
    }

    /** Null when there is no account to key on; usernames are case-insensitive. */
    private static String accountKey(String username) {
        if (username == null) return null;
        String key = username.trim().toLowerCase(java.util.Locale.ROOT);
        return key.isEmpty() ? null : key;
    }

    public static boolean tryProbe(String ip) {
        if (!isEnabled() || ip == null || ip.isEmpty()) return true;
        if (isLocked(ip)) return false;

        long now = System.currentTimeMillis();
        maybeSweep(now);
        long windowMs = configInt("login.probe.window_sec", 60) * 1000L;
        int maxAttempts = configInt("login.probe.max_attempts", 20);

        ProbeState next = PROBE_STATE
                .computeIfAbsent(ip, k -> new AtomicReference<>(new ProbeState(0, now)))
                .updateAndGet(prev -> {
                    if (prev == null || (now - prev.windowStartMillis) > windowMs) {
                        return new ProbeState(1, now);
                    }
                    return new ProbeState(prev.count + 1, prev.windowStartMillis);
                });

        return next.count <= maxAttempts;
    }

    public static long secondsUntilProbeReset(String ip) {
        AtomicReference<ProbeState> ref = PROBE_STATE.get(ip);
        if (ref == null) return 0;
        ProbeState current = ref.get();
        if (current == null) return 0;
        long windowMs = configInt("login.probe.window_sec", 60) * 1000L;
        long remainingMs = (current.windowStartMillis + windowMs) - System.currentTimeMillis();
        return remainingMs > 0 ? (remainingMs / 1000L) + 1L : 0L;
    }

    private static boolean isEnabled() {
        return Emulator.getConfig() != null && Emulator.getConfig().getBoolean("login.ratelimit.enabled", true);
    }

    private static int configInt(String key, int fallback) {
        return Emulator.getConfig() != null ? Emulator.getConfig().getInt(key, fallback) : fallback;
    }

    private record State(int attempts, long windowStartMillis, long lockedUntilMillis) {}

    private record ProbeState(int count, long windowStartMillis) {}

    private record AccountState(int attempts, long windowStartMillis, long lockedUntilMillis, int lockouts) {}
}
