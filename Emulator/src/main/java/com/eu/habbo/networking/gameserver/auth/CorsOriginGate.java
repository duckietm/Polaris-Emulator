package com.eu.habbo.networking.gameserver.auth;

import com.eu.habbo.Emulator;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;

import java.net.URI;

public final class CorsOriginGate {

    private static final String CONFIG_KEY    = "ws.whitelist";
    private static final String CONFIG_DEFAULT = "localhost";

    private CorsOriginGate() {
    }

    public static boolean isAllowed(FullHttpRequest req) {
        if (req == null) return false;
        String origin = req.headers().get(HttpHeaderNames.ORIGIN);
        if (origin == null || origin.isEmpty()) return false;

        String host;
        try {
            URI uri = new URI(origin);
            host = uri.getHost();
        } catch (Exception ignored) {
            return false;
        }
        if (host == null || host.isEmpty()) return false;
        if (host.startsWith("www.")) host = host.substring(4);

        String configured = Emulator.getConfig().getValue(CONFIG_KEY, CONFIG_DEFAULT);
        if (configured == null || configured.isEmpty()) return false;

        for (String entry : configured.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) continue;

            if ("*".equals(trimmed)) {
                return true;
            }
            if (trimmed.startsWith("*")) {
                String suffix = trimmed.substring(1);
                if (host.endsWith(suffix) || ("." + host).equals(suffix)) {
                    return true;
                }
            } else if (host.equals(trimmed)) {
                return true;
            }
        }
        return false;
    }
}
