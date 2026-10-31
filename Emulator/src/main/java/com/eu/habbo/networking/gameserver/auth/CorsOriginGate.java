package com.eu.habbo.networking.gameserver.auth;

import com.eu.habbo.Emulator;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;

import java.net.URI;
import java.util.Locale;

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
            if (matches(host, entry)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether an origin host is allowed by one whitelist entry: "*" allows every host, "*.example.com" (or
     * "*example.com") allows example.com and its subdomains, anything else allows that host only, all ignoring case.
     * The wildcard used to be a plain text suffix, so "*example.com" also allowed "evilexample.com".
     */
    static boolean matches(String host, String entry) {
        if (host == null || entry == null) return false;

        String trimmed = entry.trim().toLowerCase(Locale.ROOT);
        String origin = host.toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) return false;
        if ("*".equals(trimmed)) return true;

        if (trimmed.startsWith("*")) {
            String base = trimmed.substring(1);
            if (base.startsWith(".")) base = base.substring(1);
            if (base.isEmpty()) return false;

            return origin.equals(base) || origin.endsWith("." + base);
        }

        return origin.equals(trimmed);
    }
}
