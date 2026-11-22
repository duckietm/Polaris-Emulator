package polaris.messages.incoming.furnieditor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.permissions.Permission;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.furnieditor.FurniEditorImportTextResultComposer;
import polaris.messages.outgoing.furnieditor.FurniEditorResultComposer;

/**
 * Incoming 10049 — admin imports the official Habbo display name/description for a
 * furni's classname from a configured furnidata URL (e.g.
 * https://www.user.it/gamedata/furnidata_json/1). The fetched text only POPULATES
 * the editor fields client-side; the admin reviews and Saves via the normal flow.
 *
 * Source URL is admin-configured in emulator_settings ({@code furni.editor.import.url}),
 * never supplied by the client (no SSRF). The remote furnidata is cached with a TTL.
 */
public class FurniEditorImportTextEvent extends MessageHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(FurniEditorImportTextEvent.class);
    private static final List<String> SECTIONS = Arrays.asList("roomitemtypes", "wallitemtypes");

    // Shared TTL cache (the remote furnidata is multi-MB — do not refetch per click).
    private static volatile JsonObject CACHE;
    private static volatile String CACHE_URL;
    private static volatile long CACHE_TIME;

    @Override
    public void handle() throws Exception {
        if (!this.client.getUser().hasPermission(Permission.ACC_CATALOGFURNI)) {
            this.client.sendResponse(new FurniEditorResultComposer(false, "No permission"));
            return;
        }

        int itemId = this.packet.readInt();
        if (itemId <= 0) {
            this.client.sendResponse(new FurniEditorResultComposer(false, "Invalid item ID"));
            return;
        }

        String classname = FurniEditorUpdateFurnidataEvent.classnameForItem(itemId);
        if (classname == null) {
            this.client.sendResponse(new FurniEditorResultComposer(false, "Item not found"));
            return;
        }
        String cn = classname.trim().toLowerCase(Locale.ROOT);

        String url = Emulator.getConfig()
                .getValue("furni.editor.import.url", "https://www.habbo.it/gamedata/furnidata_json/1");
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            this.client.sendResponse(new FurniEditorResultComposer(false, "Import source not configured"));
            return;
        }

        // The remote fetch can take up to 30 s; it runs on the HTTP client's threads so the packet
        // thread, shared with other players, is not held while it waits.
        GameClient client = this.client;
        fetchCached(url)
                .thenAccept(root -> reply(client, root, classname, cn, itemId))
                .exceptionally(e -> {
                    LOGGER.error("FurniEditorImportTextEvent: import for item {} failed", itemId, e);
                    return null;
                });
    }

    private static void reply(GameClient client, JsonObject root, String classname, String cn, int itemId) {
        if (root == null) {
            client.sendResponse(new FurniEditorResultComposer(false, "Could not fetch Habbo furnidata"));
            return;
        }

        String foundName = null, foundDesc = null;
        outer:
        for (String section : SECTIONS) {
            if (!root.has(section) || !root.get(section).isJsonObject()) continue;
            JsonObject sec = root.getAsJsonObject(section);
            if (!sec.has("furnitype") || !sec.get("furnitype").isJsonArray()) continue;
            for (JsonElement el : sec.getAsJsonArray("furnitype")) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                if (!o.has("classname")) continue;
                if (o.get("classname")
                        .getAsString()
                        .trim()
                        .toLowerCase(Locale.ROOT)
                        .equals(cn)) {
                    foundName = (o.has("name") && !o.get("name").isJsonNull())
                            ? o.get("name").getAsString()
                            : "";
                    foundDesc = (o.has("description") && !o.get("description").isJsonNull())
                            ? o.get("description").getAsString()
                            : "";
                    break outer;
                }
            }
        }

        boolean found = (foundName != null);
        client.sendResponse(new FurniEditorImportTextResultComposer(
                found, found ? foundName : "", found ? foundDesc : "", classname));
        LOGGER.info(
                "FurniEditorImportTextEvent: admin {} import for classname '{}' (item {}) -> found={}",
                client.getUser().getUserInfo().getId(),
                classname,
                itemId,
                found);
    }

    /** Fetch the remote furnidata JSON with a TTL cache (serves stale on failure). */
    static CompletableFuture<JsonObject> fetchCached(String url) {
        long ttlMs;
        try {
            ttlMs = Long.parseLong(Emulator.getConfig().getValue("furni.editor.import.cache.ms", "600000"));
        } catch (Exception e) {
            ttlMs = 600000L;
        }

        long now = System.currentTimeMillis();
        if (CACHE != null && url.equals(CACHE_URL) && (now - CACHE_TIME) < ttlMs) {
            return CompletableFuture.completedFuture(CACHE);
        }

        try {
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "Polaris-FurniEditor")
                    .GET()
                    .build();
            return httpClient
                    .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(resp -> storeResponse(url, now, resp))
                    .exceptionally(e -> {
                        LOGGER.warn("FurniEditorImportTextEvent: failed to fetch {}", url, e);
                        return CACHE; // serve stale if available
                    });
        } catch (Exception e) {
            LOGGER.warn("FurniEditorImportTextEvent: failed to fetch {}", url, e);
            return CompletableFuture.completedFuture(CACHE); // serve stale if available
        }
    }

    private static JsonObject storeResponse(String url, long fetchedAt, HttpResponse<String> resp) {
        if (resp.statusCode() != 200) {
            LOGGER.warn("FurniEditorImportTextEvent: fetch {} returned HTTP {}", url, resp.statusCode());
            return CACHE; // serve stale if available
        }
        JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
        CACHE = root;
        CACHE_URL = url;
        CACHE_TIME = fetchedAt;
        return root;
    }
}
