package com.eu.habbo.networking.rconserver;

import com.eu.habbo.Emulator;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

final class RconRequestAuthenticator {
    static final int DEFAULT_MAX_CLOCK_SKEW_SECONDS = 60;
    private static final int MAX_NONCE_LENGTH = 128;
    private static final Cache<String, Boolean> SEEN_NONCES = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    private RconRequestAuthenticator() {
    }

    static boolean enabled() {
        return !secret().isBlank();
    }

    static boolean verify(JsonObject request, String key, JsonElement data) {
        String secret = secret();
        int maxSkew = Emulator.getConfig() == null
                ? DEFAULT_MAX_CLOCK_SKEW_SECONDS
                : Math.max(1, Emulator.getConfig().getInt(
                        "rcon.auth.max_clock_skew_seconds", DEFAULT_MAX_CLOCK_SKEW_SECONDS));
        return verify(secret, Instant.now().getEpochSecond(), maxSkew, request, key, data);
    }

    static boolean verify(String secret, long now, int maxSkew, JsonObject request,
                          String key, JsonElement data) {
        if (secret.isBlank()) {
            return true;
        }

        try {
            long timestamp = request.get("timestamp").getAsLong();
            String nonce = request.get("nonce").getAsString();
            String signature = request.get("signature").getAsString();

            if (nonce.isBlank() || nonce.length() > MAX_NONCE_LENGTH
                    || Math.abs(now - timestamp) > maxSkew) {
                return false;
            }

            String expected = sign(secret, timestamp, nonce, key, data);
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                    signature.toLowerCase().getBytes(StandardCharsets.US_ASCII))) {
                return false;
            }

            return SEEN_NONCES.asMap().putIfAbsent(timestamp + ":" + nonce, Boolean.TRUE) == null;
        } catch (Exception ignored) {
            return false;
        }
    }

    static String sign(String secret, long timestamp, String nonce, String key, JsonElement data) {
        String canonical = timestamp + "\n" + nonce + "\n" + key + "\n" + data;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    private static String secret() {
        return Emulator.getConfig() == null
                ? ""
                : Emulator.getConfig().getValue("rcon.auth.secret", "").trim();
    }
}
