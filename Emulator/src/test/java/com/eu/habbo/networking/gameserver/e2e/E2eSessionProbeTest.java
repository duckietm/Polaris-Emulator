package com.eu.habbo.networking.gameserver.e2e;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.CryptoConfig;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.gameclients.GameClientManager;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class E2eSessionProbeTest {
    private GameClientManager clients;

    @BeforeEach
    void setUp() throws Exception {
        var crypto = Emulator.class.getDeclaredField("crypto");
        crypto.setAccessible(true);
        crypto.set(null, new CryptoConfig(false, "", "", ""));
        clients = new GameClientManager();
    }

    @Test
    void staysInvisibleWhenDisabled() {
        assertNull(E2eSessionProbe.evaluate(
                HttpMethod.GET,
                "/__e2e/session-count?userId=7",
                new InetSocketAddress("127.0.0.1", 1234),
                clients,
                false));
    }

    @Test
    void rejectsNonLoopbackClients() {
        var response = E2eSessionProbe.evaluate(
                HttpMethod.GET,
                "/__e2e/session-count?userId=7",
                new InetSocketAddress("203.0.113.5", 1234),
                clients,
                true);

        assertEquals(HttpResponseStatus.FORBIDDEN, response.status());
    }

    @Test
    void rejectsMalformedUserIds() {
        var response = E2eSessionProbe.evaluate(
                HttpMethod.GET,
                "/__e2e/session-count?userId=nope",
                new InetSocketAddress("::1", 1234),
                clients,
                true);

        assertEquals(HttpResponseStatus.BAD_REQUEST, response.status());
    }

    @Test
    void reportsAndDropsTheOwnedSession() {
        EmbeddedChannel channel = new EmbeddedChannel();
        clients.claimAuthenticatedSession(7, new GameClient(channel));

        var count = E2eSessionProbe.evaluate(
                HttpMethod.GET,
                "/__e2e/session-count?userId=7",
                new InetSocketAddress("127.0.0.1", 1234),
                clients,
                true);
        assertEquals(HttpResponseStatus.OK, count.status());
        assertEquals("{\"activeSessions\":1}", count.body());

        var drop = E2eSessionProbe.evaluate(
                HttpMethod.POST,
                "/__e2e/drop?userId=7",
                new InetSocketAddress("127.0.0.1", 1234),
                clients,
                true);
        assertEquals(HttpResponseStatus.NO_CONTENT, drop.status());
        assertFalse(channel.isOpen());
    }
}
