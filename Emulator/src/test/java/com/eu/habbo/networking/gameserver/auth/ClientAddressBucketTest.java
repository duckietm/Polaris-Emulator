package com.eu.habbo.networking.gameserver.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Per-address limits counted every IPv6 address apart, so a client rotating through its own /64 - any
 * IPv6 line hands out 2^64 of them - started from zero on each one; and the login tables grew without
 * limit inside one window.
 */
class ClientAddressBucketTest {

    @Test
    void anIpv6ClientIsItsSlash64() {
        assertEquals("2001:db8:1:2::/64", ClientAddressBucket.of("2001:db8:1:2::1"));
        assertEquals("2001:db8:1:2::/64", ClientAddressBucket.of("2001:db8:1:2:ffff:ffff:ffff:fffe"));
        assertNotEquals(ClientAddressBucket.of("2001:db8:1:2::1"), ClientAddressBucket.of("2001:db8:1:3::1"));
        assertEquals("198.51.100.4", ClientAddressBucket.of("198.51.100.4"));
        assertEquals("198.51.100.4", ClientAddressBucket.of("::ffff:198.51.100.4"), "IPv4-mapped is IPv4");
        assertEquals("not-an-ip", ClientAddressBucket.of("not-an-ip"));
        assertEquals("", ClientAddressBucket.of(""));
    }

    @Test
    void rotatingInsideOneSlash64DoesNotResetTheLoginLockout() {
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getBoolean(anyString(), anyBoolean())).thenAnswer(i -> i.getArgument(1));
        when(config.getInt(anyString(), anyInt())).thenAnswer(i -> i.getArgument(1));
        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getConfig).thenReturn(config);
            AuthRateLimiter.resetForTests();

            for (int i = 1; i <= 5; i++) {
                AuthRateLimiter.recordFailure("2001:db8:1:2::" + Integer.toHexString(i));
            }

            assertTrue(AuthRateLimiter.isLocked("2001:db8:1:2::abcd"), "a sixth address of the prefix is locked");
            assertFalse(AuthRateLimiter.isLocked("2001:db8:1:3::1"), "the next prefix is someone else");
            AuthRateLimiter.resetForTests();
        }
    }

    @Test
    void theLoginTablesAreCapped() throws Exception {
        String source =
                Files.readString(Path.of("src/main/java/com/eu/habbo/networking/gameserver/auth/AuthRateLimiter.java"));

        assertFalse(source.contains("new ConcurrentHashMap<>()"));
        assertTrue(source.contains("Caffeine.newBuilder().maximumSize(MAX_ENTRIES)"));
    }
}
