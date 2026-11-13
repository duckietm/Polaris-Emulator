package polaris.messages.incoming.furnieditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FurniEditorImportTextFetchTest {
    private static final String FURNIDATA =
            "{\"roomitemtypes\":{\"furnitype\":[{\"classname\":\"chair\",\"name\":\"Chair\",\"description\":\"Sit\"}]}}";

    private final CountDownLatch release = new CountDownLatch(1);
    private final AtomicInteger requests = new AtomicInteger();
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/slow", exchange -> {
            requests.incrementAndGet();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, FURNIDATA);
        });
        server.createContext("/broken", exchange -> respond(exchange, 500, "down"));
        server.setExecutor(null);
        server.start();
    }

    @AfterEach
    void stopServer() {
        release.countDown();
        if (server != null) server.stop(0);
    }

    @Test
    void fetchesWithoutHoldingTheCallerCachesAndServesStaleOnFailure() throws Exception {
        String slow = url("/slow");

        CompletableFuture<JsonObject> pending = FurniEditorImportTextEvent.fetchCached(slow);

        // The caller (the packet thread) gets the future back while the server is still answering.
        assertFalse(pending.isDone());

        release.countDown();
        JsonObject root = pending.get(10, TimeUnit.SECONDS);
        assertTrue(root.has("roomitemtypes"));

        CompletableFuture<JsonObject> cached = FurniEditorImportTextEvent.fetchCached(slow);
        assertTrue(cached.isDone());
        assertSame(root, cached.get());
        assertEquals(1, requests.get());

        JsonObject stale =
                FurniEditorImportTextEvent.fetchCached(url("/broken")).get(10, TimeUnit.SECONDS);
        assertSame(root, stale);
    }

    private String url(String path) {
        return "http://" + server.getAddress().getHostString() + ":"
                + server.getAddress().getPort() + path;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
