package com.eu.habbo.networking.gameserver.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Guessing one account's password is throttled per account, not only per IP:
 * the IP counter is wiped by any successful login, so without this the attempts
 * spent on a staff account are reset by logging in to one's own account.
 */
class AuthRateLimiterAccountTest {

    private static final int MAX_ATTEMPTS = 10;

    private MockedStatic<Emulator> emulator;

    @BeforeEach
    void stubConfiguration() {
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getBoolean(anyString(), anyBoolean())).thenAnswer(i -> i.getArgument(1));
        when(config.getInt(anyString(), anyInt())).thenAnswer(i -> i.getArgument(1));

        emulator = mockStatic(Emulator.class);
        emulator.when(Emulator::getConfig).thenReturn(config);
        AuthRateLimiter.resetForTests();
    }

    @AfterEach
    void close() {
        AuthRateLimiter.resetForTests();
        emulator.close();
    }

    private static void failNTimes(String username, int times) {
        for (int i = 0; i < times; i++) {
            AuthRateLimiter.recordAccountFailure(username);
        }
    }

    @Test
    void locksTheAccountAfterTheConfiguredAttempts() {
        failNTimes("Admin", MAX_ATTEMPTS - 1);
        assertFalse(AuthRateLimiter.isAccountLocked("Admin"));

        AuthRateLimiter.recordAccountFailure("Admin");
        assertTrue(AuthRateLimiter.isAccountLocked("Admin"));
        assertTrue(AuthRateLimiter.secondsUntilAccountUnlock("Admin") > 0);
    }

    @Test
    void countsAttemptsRegardlessOfCase() {
        failNTimes("Admin", 5);
        failNTimes("ADMIN", 5);
        assertTrue(AuthRateLimiter.isAccountLocked("admin"));
    }

    @Test
    void loggingInToAnotherAccountDoesNotResetTheAttemptsOnThisOne() {
        failNTimes("Admin", MAX_ATTEMPTS - 1);

        // The attacker logs in to their own account from the same IP: that used
        // to clear the only counter there was.
        AuthRateLimiter.recordSuccess("203.0.113.7", "Attacker");

        AuthRateLimiter.recordAccountFailure("Admin");
        assertTrue(AuthRateLimiter.isAccountLocked("Admin"));
    }

    @Test
    void theOwnerLoggingInClearsTheirOwnAccountThrottle() {
        failNTimes("Admin", MAX_ATTEMPTS - 1);
        AuthRateLimiter.recordSuccess("203.0.113.7", "Admin");

        failNTimes("Admin", MAX_ATTEMPTS - 1);
        assertFalse(AuthRateLimiter.isAccountLocked("Admin"));
    }

    @Test
    void doesNothingWithoutAnAccountToKeyOn() {
        AuthRateLimiter.recordAccountFailure(null);
        AuthRateLimiter.recordAccountFailure("   ");
        assertFalse(AuthRateLimiter.isAccountLocked(null));
        assertFalse(AuthRateLimiter.isAccountLocked(""));
    }
}
