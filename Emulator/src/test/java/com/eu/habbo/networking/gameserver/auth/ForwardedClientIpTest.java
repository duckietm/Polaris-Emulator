package com.eu.habbo.networking.gameserver.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The forwarded-IP chain is read from the right, because every proxy appends
 * the peer it saw: the leftmost hop is written by the client and must never
 * become the IP that bans and rate limits are keyed on.
 *
 * <p>No configuration is loaded here, so the only trusted hops are loopback.
 */
class ForwardedClientIpTest {

    @Test
    void takesTheOnlyHopWhenThereIsNoProxyChain() {
        assertEquals("203.0.113.7", AuthHttpUtil.resolveForwardedClientIp("203.0.113.7"));
    }

    @Test
    void ignoresAHopTheClientPrepended() {
        // The client sent "9.9.9.9"; the proxy appended the peer it really saw.
        assertEquals("203.0.113.7", AuthHttpUtil.resolveForwardedClientIp("9.9.9.9, 203.0.113.7"));
        assertEquals("203.0.113.7", AuthHttpUtil.resolveForwardedClientIp("1.1.1.1, 2.2.2.2, 203.0.113.7"));
    }

    @Test
    void skipsTrustedProxyHopsFromTheRight() {
        assertEquals("203.0.113.7", AuthHttpUtil.resolveForwardedClientIp("203.0.113.7, 127.0.0.1"));
        assertEquals("203.0.113.7", AuthHttpUtil.resolveForwardedClientIp("203.0.113.7, 127.0.0.1, ::1"));
    }

    @Test
    void fallsBackToTheSocketPeerWhenEveryHopIsATrustedProxy() {
        assertEquals("", AuthHttpUtil.resolveForwardedClientIp("127.0.0.1"));
        assertEquals("", AuthHttpUtil.resolveForwardedClientIp("127.0.0.1, ::1"));
    }

    @Test
    void discardsAChainThatIsNotReadable() {
        assertEquals("", AuthHttpUtil.resolveForwardedClientIp(null));
        assertEquals("", AuthHttpUtil.resolveForwardedClientIp("   "));
        assertEquals("", AuthHttpUtil.resolveForwardedClientIp("unknown"));
        assertEquals("", AuthHttpUtil.resolveForwardedClientIp("203.0.113.7, _hidden"));
        assertEquals("", AuthHttpUtil.resolveForwardedClientIp("203.0.113.7, evil.example.com"));
    }

    @Test
    void skipsEmptyHops() {
        // An empty hop makes no claim; the appended hop a proxy adds is still there.
        assertEquals("203.0.113.7", AuthHttpUtil.resolveForwardedClientIp("203.0.113.7,"));
        assertEquals("203.0.113.7", AuthHttpUtil.resolveForwardedClientIp("9.9.9.9,, 203.0.113.7"));
        assertEquals("", AuthHttpUtil.resolveForwardedClientIp(",,"));
    }

    @Test
    void stripsThePortAndBracketsAProxyMayAdd() {
        assertEquals("203.0.113.7", AuthHttpUtil.resolveForwardedClientIp("203.0.113.7:51324"));
        assertEquals("2001:db8::1", AuthHttpUtil.resolveForwardedClientIp("[2001:db8::1]:443, 127.0.0.1"));
        assertEquals("2001:db8::1", AuthHttpUtil.resolveForwardedClientIp("\"2001:db8::1\""));
    }
}
