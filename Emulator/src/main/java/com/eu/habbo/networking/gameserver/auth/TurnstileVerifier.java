package com.eu.habbo.networking.gameserver.auth;

import com.eu.habbo.Emulator;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class TurnstileVerifier {
    private static final Logger LOGGER = LoggerFactory.getLogger(TurnstileVerifier.class);
    private static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private TurnstileVerifier() {}

    public static boolean isEnabled() {
        return Emulator.getConfig() != null
                && Emulator.getConfig().getBoolean("login.turnstile.enabled", false);
    }

    public static boolean verify(String token, String remoteIp) {
        if (!isEnabled()) return true;

        if (token == null || token.isEmpty()) return false;

        String secret = Emulator.getConfig().getValue("login.turnstile.secretkey", "");
        if (secret.isEmpty()) {
            LOGGER.warn("login.turnstile.enabled=1 but login.turnstile.secretkey is empty — refusing the request");
            return false;
        }

        var form = new StringBuilder();
        form.append("secret=").append(URLEncoder.encode(secret, StandardCharsets.UTF_8));
        form.append("&response=").append(URLEncoder.encode(token, StandardCharsets.UTF_8));
        if (remoteIp != null && !remoteIp.isEmpty()) {
            form.append("&remoteip=").append(URLEncoder.encode(remoteIp, StandardCharsets.UTF_8));
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(VERIFY_URL))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form.toString(), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                LOGGER.warn("Turnstile siteverify returned HTTP {} for ip={}", response.statusCode(), remoteIp);
                return false;
            }

            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            boolean success = json.has("success") && json.get("success").getAsBoolean();
            if (!success) {
                LOGGER.info("Turnstile token rejected for ip={} body={}", remoteIp, response.body());
            }
            return success;
        } catch (Exception e) {
            LOGGER.error("Turnstile verification failed for ip=" + remoteIp, e);
            return false;
        }
    }
}
