package com.eu.habbo.networking.gameserver.wired;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** The variables API counted each IPv6 address apart, so rotating inside one /64 escaped its limits. */
class WiredApiLimitsIpv6Test {

    @Test
    void addressesOfOneSlash64ShareTheirBudgetAndTheirBlock() {
        WiredApiLimits limits = new WiredApiLimits(() -> 1_000L);
        WiredApiSettings settings = WiredApiSettings.defaults(true);

        for (int i = 1; i <= settings.perIp(); i++) {
            limits.acquireIp("2001:db8:7:7::" + Integer.toHexString(i), settings);
        }
        assertThrows(WiredApiException.class, () -> limits.acquireIp("2001:db8:7:7::ffff", settings));
        assertDoesNotThrow(() -> limits.acquireIp("2001:db8:7:8::1", settings), "another prefix has its own");

        for (int i = 1; i <= settings.authFailMax() + 1; i++) {
            limits.recordAuthFailure("2001:db8:9:9::" + Integer.toHexString(i), settings);
        }
        assertThrows(WiredApiException.class, () -> limits.checkNotBlocked("2001:db8:9:9::beef"));
    }
}
