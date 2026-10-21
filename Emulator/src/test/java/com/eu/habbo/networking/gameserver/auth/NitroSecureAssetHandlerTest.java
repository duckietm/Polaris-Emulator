package com.eu.habbo.networking.gameserver.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NitroSecureAssetHandlerTest {

    @TempDir
    Path temporaryDirectory;

    private Object previousConfig;
    private EmbeddedChannel channel;

    @BeforeEach
    void installConfig() throws Exception {
        Field configField = Emulator.class.getDeclaredField("config");
        configField.setAccessible(true);
        this.previousConfig = configField.get(null);
        Path configFile = this.temporaryDirectory.resolve("config.ini");
        Files.writeString(configFile, "nitro.secure.master_key=test-key\nnitro.secure.assets.enabled=true\n");
        configField.set(null, new ConfigurationManager(configFile.toString()));
        this.channel = new EmbeddedChannel(new NitroSecureAssetHandler());
    }

    @AfterEach
    void restoreConfig() throws Exception {
        this.channel.finishAndReleaseAll();
        Field configField = Emulator.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(null, this.previousConfig);
    }

    private static String publicKey(String algorithm, Object parameters) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(algorithm);
        if (parameters instanceof Integer bits) {
            generator.initialize(bits);
        } else {
            generator.initialize((java.security.spec.AlgorithmParameterSpec) parameters);
        }
        return Base64.getEncoder()
                .encodeToString(generator.generateKeyPair().getPublic().getEncoded());
    }

    private FullHttpResponse bootstrap(String key) {
        String json = "{\"key\":\"" + key + "\"}";
        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.POST,
                "/nitro-sec/bootstrap",
                Unpooled.copiedBuffer(json, StandardCharsets.UTF_8));
        this.channel.writeInbound(request);
        FullHttpResponse response = this.channel.readOutbound();
        assertNotNull(response);
        return response;
    }

    private FullHttpResponse file(String key) {
        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "/nitro-sec/file?kind=config&file=a.json");
        request.headers().set("X-Nitro-Key", key);
        this.channel.writeInbound(request);
        FullHttpResponse response = this.channel.readOutbound();
        assertNotNull(response);
        return response;
    }

    @Test
    void bootstrapAnswersAValidP256KeyWithTheServerKey() throws Exception {
        FullHttpResponse response = bootstrap(publicKey("EC", new ECGenParameterSpec("secp256r1")));

        assertEquals(HttpResponseStatus.OK, response.status());
        String serverKey = JsonParser.parseString(response.content().toString(StandardCharsets.UTF_8))
                .getAsJsonObject()
                .get("key")
                .getAsString();
        assertFalse(serverKey.isEmpty());
        response.release();
    }

    @Test
    void bootstrapRefusesAKeyThatIsNotAValidP256PublicKey() throws Exception {
        String[] invalid = {
            "AAAA", "not base64 !!", publicKey("EC", new ECGenParameterSpec("secp384r1")), publicKey("RSA", 2048)
        };

        for (String key : invalid) {
            FullHttpResponse response = bootstrap(key);

            assertEquals(HttpResponseStatus.BAD_REQUEST, response.status(), key);
            response.release();
        }
    }

    @Test
    void aFileRequestWithAnInvalidKeyIsABadRequestNotAServerError() {
        FullHttpResponse response = file("AAAA");

        assertEquals(HttpResponseStatus.BAD_REQUEST, response.status());
        response.release();
    }

    @Test
    void theExampleMasterKeyAndABlankOneAreNotSecrets() {
        assertTrue(NitroSecureAssetHandler.isPlaceholderMasterKey(""));
        assertTrue(NitroSecureAssetHandler.isPlaceholderMasterKey("   "));
        assertTrue(NitroSecureAssetHandler.isPlaceholderMasterKey(null));
        assertTrue(NitroSecureAssetHandler.isPlaceholderMasterKey("change-me-to-a-long-random-secret"));
        assertTrue(NitroSecureAssetHandler.isPlaceholderMasterKey("Change_this"));
        assertFalse(NitroSecureAssetHandler.isPlaceholderMasterKey("9f2c1d0e"));
        assertFalse(NitroSecureAssetHandler.isPlaceholderMasterKey("test-key"));
    }
}
