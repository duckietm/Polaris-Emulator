package com.eu.habbo.networking.gameserver.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.ReferenceCountUtil;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NitroSecureApiHandlerTest {

    @TempDir
    Path temporaryDirectory;

    private Object previousConfig;
    private EmbeddedChannel channel;

    private void useConfig(String extraSettings) throws Exception {
        Field configField = Emulator.class.getDeclaredField("config");
        configField.setAccessible(true);
        this.previousConfig = configField.get(null);
        Path configFile = this.temporaryDirectory.resolve("config.ini");
        Files.writeString(
                configFile, "nitro.secure.master_key=test-key\nnitro.secure.api.enabled=true\n" + extraSettings);
        configField.set(null, new ConfigurationManager(configFile.toString()));
        this.channel = new EmbeddedChannel(new NitroSecureApiHandler());
    }

    @AfterEach
    void restoreConfig() throws Exception {
        if (this.channel != null) {
            this.channel.finishAndReleaseAll();
        }
        Field configField = Emulator.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(null, this.previousConfig);
    }

    private static String clientKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        return Base64.getEncoder()
                .encodeToString(generator.generateKeyPair().getPublic().getEncoded());
    }

    private static FullHttpRequest request(HttpMethod method, String uri, String body) {
        return new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, method, uri, Unpooled.copiedBuffer(body, StandardCharsets.UTF_8));
    }

    private static FullHttpRequest secureRequest(HttpMethod method, String uri, String body, String key) {
        FullHttpRequest request = request(method, uri, body);
        request.headers().set("X-Nitro-Api", "1");
        if (key != null) {
            request.headers().set("X-Nitro-Key", key);
        }
        return request;
    }

    @Test
    void plainCallsPassThroughWhileRequiredIsOff() throws Exception {
        useConfig("");
        FullHttpRequest request = request(HttpMethod.POST, "/api/auth/login", "{}");

        this.channel.writeInbound(request);

        assertSame(request, this.channel.readInbound());
        assertNull(this.channel.readOutbound());
        ReferenceCountUtil.release(request);
    }

    @Test
    void requiredRejectsPlainCallsToTheClientEndpointsAndReleasesThem() throws Exception {
        useConfig("nitro.secure.api.required=true\n");

        for (String uri : new String[] {"/api/auth/login", "/api/auth/news?x=1", "/api/badges", "/api/badges/custom"}) {
            FullHttpRequest request = request(HttpMethod.POST, uri, "{}");

            this.channel.writeInbound(request);

            FullHttpResponse response = this.channel.readOutbound();
            assertNotNull(response, uri);
            assertEquals(HttpResponseStatus.FORBIDDEN, response.status(), uri);
            assertNull(this.channel.readInbound(), uri);
            assertEquals(0, request.refCnt(), uri + " must be released");
            response.release();
        }
    }

    @Test
    void requiredLeavesTheEndpointsMeantForOtherCallersAlone() throws Exception {
        useConfig("nitro.secure.api.required=true\n");

        for (String uri : new String[] {
            "/api/cms/ping",
            "/api/health",
            "/api/maintenance",
            "/api/emustats",
            "/api/public/wired/variable",
            "/api/authx"
        }) {
            FullHttpRequest request = request(HttpMethod.GET, uri, "");

            this.channel.writeInbound(request);

            assertSame(request, this.channel.readInbound(), uri);
            assertNull(this.channel.readOutbound(), uri);
            ReferenceCountUtil.release(request);
        }
    }

    @Test
    void requiredStillAcceptsAnEncryptedCall() throws Exception {
        useConfig("nitro.secure.api.required=true\n");
        FullHttpRequest request = secureRequest(HttpMethod.GET, "/api/auth/news", "", clientKey());

        this.channel.writeInbound(request);

        assertNotNull(this.channel.readInbound());
        assertNull(this.channel.readOutbound());
    }

    @Test
    void aSecureCallWithoutAKeyIsRefusedAndReleased() throws Exception {
        useConfig("");
        FullHttpRequest request = secureRequest(HttpMethod.POST, "/api/auth/login", "{}", null);

        this.channel.writeInbound(request);

        FullHttpResponse response = this.channel.readOutbound();
        assertEquals(HttpResponseStatus.UNAUTHORIZED, response.status());
        assertNull(this.channel.readInbound());
        assertEquals(0, request.refCnt(), "the request must be released");
        response.release();
    }

    @Test
    void aSecureCallWithAnInvalidKeyIsABadRequestAndIsReleased() throws Exception {
        useConfig("");
        FullHttpRequest request = secureRequest(HttpMethod.POST, "/api/auth/login", "{}", "AAAA");

        this.channel.writeInbound(request);

        FullHttpResponse response = this.channel.readOutbound();
        assertEquals(HttpResponseStatus.BAD_REQUEST, response.status());
        assertNull(this.channel.readInbound());
        assertEquals(0, request.refCnt(), "the request must be released");
        response.release();
    }

    @Test
    void anOversizedEncryptedPayloadIsRefusedAndReleased() throws Exception {
        useConfig("nitro.secure.api.max_payload_bytes=8\n");
        FullHttpRequest request = secureRequest(HttpMethod.POST, "/api/auth/login", "x".repeat(100), clientKey());

        this.channel.writeInbound(request);

        FullHttpResponse response = this.channel.readOutbound();
        assertEquals(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, response.status());
        assertNull(this.channel.readInbound());
        assertEquals(0, request.refCnt(), "the request must be released");
        response.release();
    }

    @Test
    void aResponseThatCannotBeEncryptedIsNotSentInTheClear() throws Exception {
        useConfig("");
        FullHttpRequest request = secureRequest(HttpMethod.GET, "/api/auth/news", "", clientKey());
        this.channel.writeInbound(request);
        assertNotNull(this.channel.readInbound());

        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.copiedBuffer("secret", StandardCharsets.UTF_8));
        response.content().release();
        this.channel.writeOutbound(response);

        FullHttpResponse sent = this.channel.readOutbound();
        assertNotNull(sent);
        assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR, sent.status());
        assertFalse(sent.content().toString(StandardCharsets.UTF_8).contains("secret"));
        assertTrue(sent.content().readableBytes() < 64);
        sent.release();
    }
}
