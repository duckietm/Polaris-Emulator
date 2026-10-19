package com.eu.habbo.networking.rconserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

class RCONServerRateLimitKeyTest {
    @Test
    void connectionsFromOneAddressShareAKeyWhateverTheirSourcePort() throws Exception {
        InetAddress host = InetAddress.getByAddress(new byte[] {10, 0, 0, 7});

        assertEquals(
                RCONServer.rateLimitKey(new InetSocketAddress(host, 50001)),
                RCONServer.rateLimitKey(new InetSocketAddress(host, 50002)));
    }

    @Test
    void differentAddressesGetDifferentKeys() throws Exception {
        InetAddress one = InetAddress.getByAddress(new byte[] {10, 0, 0, 7});
        InetAddress two = InetAddress.getByAddress(new byte[] {10, 0, 0, 8});

        assertNotEquals(
                RCONServer.rateLimitKey(new InetSocketAddress(one, 50001)),
                RCONServer.rateLimitKey(new InetSocketAddress(two, 50001)));
    }

    @Test
    void anUnknownAddressStillGetsAKey() {
        assertEquals("unknown", RCONServer.rateLimitKey(null));
    }
}
