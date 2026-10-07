package com.eu.habbo.habbohotel.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** items.furnidata.path: a local file, a URL that is downloaded, and a clear message for anything else. */
class FurnidataSourceResolverTest {
    private static final String FURNIDATA = "{\"roomitemtypes\":{\"furnitype\":[]}}";

    private HttpServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/gamedata/FurnitureData.json", exchange -> respond(exchange, 200, FURNIDATA));
        server.createContext("/gamedata/error.json", exchange -> respond(exchange, 200, "<html>Not here</html>"));
        server.createContext("/gamedata/missing.json", exchange -> respond(exchange, 404, "{}"));
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void aLocalFurnidataFileIsUsed(@TempDir Path dir) throws Exception {
        Path file = Files.writeString(dir.resolve("FurnitureData.json"), "{}");
        assertFalse(FurnidataDownloader.isDownloadedCopy(file));

        FurnidataSourceResolver.Source source = FurnidataSourceResolver.resolveConfigured(file.toString(), "", "");

        assertTrue(source.ok());
        assertEquals(file, source.path());
    }

    @Test
    void aUrlIsDownloadedIntoALocalJsonCopy() throws Exception {
        String url = url("/gamedata/FurnitureData.json");
        Files.deleteIfExists(FurnidataDownloader.cachePathFor(url));

        FurnidataSourceResolver.Source source = FurnidataSourceResolver.resolveConfigured(url, "", "");

        assertTrue(source.ok());
        assertTrue(source.path().toString().endsWith(".json"));
        assertEquals(FURNIDATA, Files.readString(source.path()));
        assertTrue(FurnidataDownloader.isDownloadedCopy(source.path()));
    }

    @Test
    void anErrorPageOrAFailedRequestNeverBecomesTheFurnidata() throws Exception {
        for (String path : new String[] {"/gamedata/error.json", "/gamedata/missing.json"}) {
            String url = url(path);
            Path cache = FurnidataDownloader.cachePathFor(url);
            Files.deleteIfExists(cache);

            assertFalse(FurnidataDownloader.download(url, cache));
            assertFalse(Files.exists(cache));
        }
    }

    @Test
    void anInvalidPathNamesTheSettingInsteadOfFailing() {
        FurnidataSourceResolver.Source source = FurnidataSourceResolver.resolveConfigured("bad\u0000path.json", "", "");

        assertEquals(FurnidataSourceResolver.Status.ERROR, source.status());
        assertTrue(source.message().contains("items.furnidata.path"));
    }

    @Test
    void onlyHttpAndHttpsCountAsUrls() {
        assertTrue(FurnidataDownloader.isUrl(" HTTPS://camwijs.eu/gamedata/FurnitureData.json"));
        assertTrue(FurnidataDownloader.isUrl("http://192.168.0.8/gamedata/config/FurnitureData.json"));
        assertFalse(FurnidataDownloader.isUrl("file:///etc/passwd"));
        assertFalse(FurnidataDownloader.isUrl("C:/Hotel/FurnitureData.json"));
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
