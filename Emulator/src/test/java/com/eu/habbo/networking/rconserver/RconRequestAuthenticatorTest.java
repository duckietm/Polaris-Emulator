package com.eu.habbo.networking.rconserver;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RconRequestAuthenticatorTest {
    @Test
    void signatureBindsTimestampNonceCommandAndPayload() {
        JsonObject first = new JsonObject();
        first.addProperty("user_id", 42);
        JsonObject second = new JsonObject();
        second.addProperty("user_id", 43);

        String signature = RconRequestAuthenticator.sign("secret", 1234, "nonce", "givecredits", first);

        assertEquals(signature,
                RconRequestAuthenticator.sign("secret", 1234, "nonce", "givecredits", first));
        assertNotEquals(signature,
                RconRequestAuthenticator.sign("secret", 1235, "nonce", "givecredits", first));
        assertNotEquals(signature,
                RconRequestAuthenticator.sign("secret", 1234, "different", "givecredits", first));
        assertNotEquals(signature,
                RconRequestAuthenticator.sign("secret", 1234, "nonce", "setrank", first));
        assertNotEquals(signature,
                RconRequestAuthenticator.sign("secret", 1234, "nonce", "givecredits", second));
    }

    @Test
    void verificationRejectsBadStaleAndReplayedRequests() {
        long now = 10_000;
        String nonce = UUID.randomUUID().toString();
        JsonObject data = new JsonObject();
        data.addProperty("user_id", 42);
        JsonObject request = signedRequest("secret", now, nonce, "givecredits", data);

        assertTrue(RconRequestAuthenticator.verify("secret", now, 60,
                request, "givecredits", data));
        assertFalse(RconRequestAuthenticator.verify("secret", now, 60,
                request, "givecredits", data), "the same nonce must not be accepted twice");

        JsonObject stale = signedRequest("secret", now - 61, UUID.randomUUID().toString(),
                "givecredits", data);
        assertFalse(RconRequestAuthenticator.verify("secret", now, 60,
                stale, "givecredits", data));

        JsonObject bad = signedRequest("different-secret", now, UUID.randomUUID().toString(),
                "givecredits", data);
        assertFalse(RconRequestAuthenticator.verify("secret", now, 60,
                bad, "givecredits", data));
    }

    @Test
    void blankSecretPreservesLegacyRequests() {
        assertTrue(RconRequestAuthenticator.verify("", 1, 60,
                new JsonObject(), "legacy", new JsonObject()));
    }

    private static JsonObject signedRequest(String secret, long timestamp, String nonce,
                                            String key, JsonObject data) {
        JsonObject request = new JsonObject();
        request.addProperty("timestamp", timestamp);
        request.addProperty("nonce", nonce);
        request.addProperty("signature",
                RconRequestAuthenticator.sign(secret, timestamp, nonce, key, data));
        return request;
    }
}
