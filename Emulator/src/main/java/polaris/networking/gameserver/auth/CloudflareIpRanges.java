package polaris.networking.gameserver.auth;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.core.ConfigurationManager;

/**
 * Trusts Cloudflare's published edge ranges as forwarded-IP proxies when the
 * operator points {@code ws.ip.header} at Cloudflare's own
 * {@code CF-Connecting-IP} header — the setups where Cloudflare connects to
 * the emulator (or the operator's proxy chain) directly.
 *
 * <p>The ranges are fetched once at startup from cloudflare.com over HTTPS. A
 * fetch failure only logs a warning and leaves the list empty — fail-closed:
 * the forwarded header is then honoured solely from {@code ws.ip.header.trusted}
 * peers. A garbled response (unparsable or absurdly long list) rejects the
 * whole load rather than half-applying it.
 *
 * <p>Operators must still firewall the origin so only Cloudflare can reach
 * it: once active, any peer inside the published ranges may set the header.
 */
public final class CloudflareIpRanges {

    private static final Logger LOGGER = LoggerFactory.getLogger(CloudflareIpRanges.class);

    public static final String CF_CONNECTING_IP = "CF-Connecting-IP";

    private static final String[] RANGE_URLS = {"https://www.cloudflare.com/ips-v4", "https://www.cloudflare.com/ips-v6"
    };
    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_RANGES = 256;

    private static final AtomicReference<List<CidrRange>> RANGES = new AtomicReference<>(List.of());

    private CloudflareIpRanges() {}

    /** Called once during startup, after config (including emulator_settings) is loaded. */
    public static void loadAtStartup(ConfigurationManager configuration) {
        String header = configuration.getValue("ws.ip.header", "").trim();
        String trusted = configuration.getValue("ws.ip.header.trusted", "").trim();

        if (header.isEmpty()) {
            LOGGER.debug("[ws.ip] ws.ip.header is not set — client IPs are taken from the socket peer.");
            return;
        }

        if (!CF_CONNECTING_IP.equalsIgnoreCase(header)) {
            LOGGER.info(
                    "[ws.ip] Forwarded-IP header '{}' is honoured from loopback and trusted peers: [{}]",
                    header,
                    trusted.isEmpty() ? "(none configured — set ws.ip.header.trusted)" : trusted);
            return;
        }

        try {
            List<CidrRange> loaded = fetchRanges();
            RANGES.set(List.copyOf(loaded));
            LOGGER.info(
                    "[ws.ip] ws.ip.header is {} — trusting {} published Cloudflare edge ranges as forwarded-IP proxies.",
                    CF_CONNECTING_IP,
                    loaded.size());
            // INFO, not DEBUG: debug logging is only switched on later in
            // startup, and the operator explicitly wants to see these.
            LOGGER.info("[ws.ip] Cloudflare edge ranges loaded: {}", join(loaded));
            if (!trusted.isEmpty()) {
                LOGGER.info("[ws.ip] Additional ws.ip.header.trusted peers: [{}]", trusted);
            }
        } catch (Exception e) {
            LOGGER.warn(
                    "Could not load Cloudflare IP ranges ({}); forwarded IPs will only be honoured from "
                            + "ws.ip.header.trusted peers until the next restart.",
                    e.getMessage());
        }
    }

    private static String join(List<CidrRange> loaded) {
        StringBuilder out = new StringBuilder();
        for (CidrRange range : loaded) {
            if (out.length() > 0) out.append(", ");
            out.append(range);
        }
        return out.toString();
    }

    private static List<CidrRange> fetchRanges() throws IOException, InterruptedException {
        List<CidrRange> loaded = new ArrayList<>();

        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(FETCH_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()) {
            for (String url : RANGE_URLS) {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(FETCH_TIMEOUT)
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200)
                    throw new IOException(url + " returned HTTP " + response.statusCode());

                for (String line : response.body().split("\n")) {
                    String candidate = line.trim();
                    if (candidate.isEmpty()) continue;
                    CidrRange range = CidrRange.parse(candidate);
                    if (range == null) throw new IOException(url + " returned unparsable range '" + candidate + "'");
                    if (loaded.size() >= MAX_RANGES)
                        throw new IOException(url + " returned more than " + MAX_RANGES + " ranges");
                    loaded.add(range);
                }
            }
        }

        if (loaded.isEmpty()) throw new IOException("Cloudflare returned no ranges");
        return loaded;
    }

    /** Whether the peer address is inside Cloudflare's published edge ranges. */
    public static boolean isCloudflareEdge(String peerIp) {
        List<CidrRange> current = RANGES.get();
        if (current.isEmpty()) return false;

        byte[] address = CidrRange.parseAddress(peerIp);
        if (address == null) return false;

        for (CidrRange range : current) {
            if (range.contains(address)) return true;
        }
        return false;
    }
}
