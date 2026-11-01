package com.eu.habbo.networking.gameserver.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The registration quota counted ip_register as text, so each address of an IPv6 /64 had a quota of its own and a
 * client with one /64 made as many accounts as it liked.
 */
class RegistrationQuotaBucketTest {

    @Test
    void accountsMadeAcrossOneIpv6Slash64ShareOneQuota() {
        assertTrue(AuthRateLimiter.registrationAllowed("2001:db8:aa:1::1", 2));
        AuthRateLimiter.recordRegistration("2001:db8:aa:1::1");
        AuthRateLimiter.recordRegistration("2001:db8:aa:1::abcd");

        assertFalse(AuthRateLimiter.registrationAllowed("2001:db8:aa:1:ffff::9", 2), "same /64");
        assertTrue(AuthRateLimiter.registrationAllowed("2001:db8:aa:2::1", 2), "another /64");
    }

    @Test
    void noLimitOrNoAddressLetsTheRegistrationThrough() {
        assertTrue(AuthRateLimiter.registrationAllowed("198.51.100.7", 0));
        assertTrue(AuthRateLimiter.registrationAllowed(null, 5));
        assertTrue(AuthRateLimiter.registrationAllowed("", 5));
    }
}
