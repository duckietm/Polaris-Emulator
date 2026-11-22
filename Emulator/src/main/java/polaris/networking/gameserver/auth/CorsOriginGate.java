package polaris.networking.gameserver.auth;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import java.net.URI;
import java.util.Locale;
import polaris.Emulator;

public final class CorsOriginGate {

    private static final String CONFIG_KEY = "ws.whitelist";
    private static final String CONFIG_DEFAULT = "localhost";

    private CorsOriginGate() {}

    private static String whitelist() {
        return Emulator.getConfig().getValue(CONFIG_KEY, CONFIG_DEFAULT);
    }

    public static boolean isAllowed(FullHttpRequest req) {
        if (req == null) return false;
        String origin = req.headers().get(HttpHeaderNames.ORIGIN);
        if (origin == null || origin.isEmpty()) return false;

        String host;
        URI uri;
        try {
            uri = new URI(origin);
            host = uri.getHost();
        } catch (Exception ignored) {
            return false;
        }
        if (host == null || host.isEmpty()) return false;
        if (host.startsWith("www.")) host = host.substring(4);

        String configured = whitelist();
        if (configured == null || configured.isEmpty()) return false;

        for (String entry : configured.split(",")) {
            if (matchesOrigin(uri, host, entry)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The request's origin when it may be put in a link that is mailed to a player: it has to be on the whitelist,
     * and the whitelist must name hosts. With a bare "*" entry any page passes, so a request sent with a made-up
     * Origin would otherwise get the hotel to mail a link to that address. Null when it cannot be trusted this way.
     */
    public static String linkableOrigin(FullHttpRequest req) {
        if (!isAllowed(req)) return null;

        String configured = whitelist();
        for (String entry : configured.split(",")) {
            if ("*".equals(entry.trim())) return null;
        }

        String origin = req.headers().get(HttpHeaderNames.ORIGIN);
        while (origin.endsWith("/")) origin = origin.substring(0, origin.length() - 1);
        return origin;
    }

    /**
     * An entry written with its scheme ("https://hotel.com" or "https://hotel.com:8443") also needs the origin's
     * scheme and port to match: a host-only entry let a page on http or another port of the same host read the
     * answers. An entry with the host alone matches as before.
     */
    static boolean matchesOrigin(URI origin, String host, String entry) {
        if (entry == null || !entry.contains("://")) return matches(host, entry);

        try {
            URI allowed = new URI(entry.trim());
            String allowedHost = allowed.getHost();
            if (allowedHost == null || origin.getScheme() == null || allowed.getScheme() == null) return false;
            if (allowedHost.startsWith("www.")) allowedHost = allowedHost.substring(4);

            return origin.getScheme().equalsIgnoreCase(allowed.getScheme())
                    && effectivePort(origin) == effectivePort(allowed)
                    && matches(host, allowedHost);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() != -1) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    /**
     * Whether an origin host is allowed by one whitelist entry: "*" allows every host, "*.example.com" (or
     * "*example.com") allows example.com and its subdomains, anything else allows that host only, all ignoring case.
     * The wildcard used to be a plain text suffix, so "*example.com" also allowed "evilexample.com".
     */
    public static boolean matches(String host, String entry) {
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
