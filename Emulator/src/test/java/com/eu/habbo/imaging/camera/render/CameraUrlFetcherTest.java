package com.eu.habbo.imaging.camera.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.imaging.camera.CameraTestAssets;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLSocketFactory;
import org.junit.jupiter.api.Test;

class CameraUrlFetcherTest {
    private static final InetAddress LOOPBACK = InetAddress.getLoopbackAddress();

    private static CameraRenderOptions options(String allowedHosts) {
        return new CameraRenderOptions(1_048_576, true, 30, 3000, 1000, 2000, 1_000_000, 1_000_000, allowedHosts, 0L);
    }

    private static InetAddress address(String literal) throws UnknownHostException {
        return InetAddress.getByName(literal);
    }

    /** A resolver that answers {@code first} once and {@code later} after that, counting calls. */
    private static final class RebindingResolver implements CameraUrlFetcher.Resolver {
        final AtomicInteger calls = new AtomicInteger();
        final InetAddress[] first;
        final InetAddress[] later;

        RebindingResolver(InetAddress[] first, InetAddress[] later) {
            this.first = first;
            this.later = later;
        }

        @Override
        public InetAddress[] resolve(String host) {
            return this.calls.getAndIncrement() == 0 ? this.first : this.later;
        }
    }

    private static CameraUrlFetcher fetcher(CameraUrlFetcher.Resolver resolver) {
        return new CameraUrlFetcher(resolver, (SSLSocketFactory) SSLSocketFactory.getDefault());
    }

    @Test
    void loopbackPrivateAndLinkLocalAnswersAreRefusedWithoutConnecting() throws Exception {
        for (String literal :
                List.of("127.0.0.1", "10.1.2.3", "192.168.0.10", "172.16.4.4", "169.254.169.254", "0.0.0.0", "::1")) {
            RebindingResolver resolver =
                    new RebindingResolver(new InetAddress[] {address(literal)}, new InetAddress[] {address(literal)});
            assertNull(fetcher(resolver).pinnedAddress("images.example", ""), literal);
            assertNull(fetcher(resolver).fetch("http://images.example/badge.png", options("")), literal);
        }
    }

    @Test
    void oneInternalAddressAmongPublicOnesRefusesTheHost() throws Exception {
        RebindingResolver resolver = new RebindingResolver(
                new InetAddress[] {address("93.184.216.34"), address("127.0.0.1")}, new InetAddress[0]);

        assertNull(fetcher(resolver).pinnedAddress("images.example", ""));
    }

    @Test
    void aPublicAnswerIsPinnedAndAnUnknownHostIsRefused() throws Exception {
        InetAddress publicAddress = address("93.184.216.34");
        RebindingResolver resolver =
                new RebindingResolver(new InetAddress[] {publicAddress}, new InetAddress[] {LOOPBACK});

        assertEquals(publicAddress, fetcher(resolver).pinnedAddress("images.example", ""));
        CameraUrlFetcher unknown = fetcher(host -> {
            throw new UnknownHostException(host);
        });
        assertNull(unknown.pinnedAddress("nowhere.invalid", ""));
    }

    @Test
    void nonHttpSchemesAndUserInfoAreRefused() throws Exception {
        CameraUrlFetcher fetcher = fetcher(host -> new InetAddress[] {address("93.184.216.34")});

        assertNull(fetcher.fetch("file:///etc/passwd", options("")));
        assertNull(fetcher.fetch("ftp://images.example/a.png", options("")));
        assertNull(fetcher.fetch("http://user:pass@images.example/a.png", options("")));
    }

    @Test
    void theConnectionGoesToTheCheckedAddressWithTheOriginalHostHeader() throws Exception {
        byte[] png = CameraTestAssets.png(CameraTestAssets.image(2, 2, CameraTestAssets.RED));
        try (ServerSocket server = new ServerSocket(0, 1, LOOPBACK)) {
            List<String> requestHeads = new ArrayList<>();
            CompletableFuture<Void> serving = CompletableFuture.runAsync(() -> serve(server, png, requestHeads));
            // The name first resolves to the loopback server (allow-listed); every later answer
            // points somewhere else. Only the first answer may be used.
            RebindingResolver resolver =
                    new RebindingResolver(new InetAddress[] {LOOPBACK}, new InetAddress[] {address("192.0.2.1")});

            byte[] body = fetcher(resolver)
                    .fetch("http://img.test:" + server.getLocalPort() + "/badges/a.png?x=1", options("img.test"));

            serving.get(5, TimeUnit.SECONDS);
            assertArrayEquals(png, body);
            assertEquals(1, resolver.calls.get(), "the name is resolved exactly once");
            String head = requestHeads.get(0);
            assertTrue(head.startsWith("GET /badges/a.png?x=1 HTTP/1.1\r\n"), head);
            assertTrue(head.contains("\r\nHost: img.test:" + server.getLocalPort() + "\r\n"), head);
        }
    }

    @Test
    void theImageLoaderDecodesThePinnedFetch() throws Exception {
        byte[] png = CameraTestAssets.png(CameraTestAssets.image(3, 2, CameraTestAssets.GREEN));
        try (ServerSocket server = new ServerSocket(0, 1, LOOPBACK)) {
            CompletableFuture<Void> serving = CompletableFuture.runAsync(() -> serve(server, png, new ArrayList<>()));
            CameraImageLoader loader = new CameraImageLoader(
                    null, null, options("img.test"), fetcher(host -> new InetAddress[] {LOOPBACK}));

            var image = loader.readUrlImage("http://img.test:" + server.getLocalPort() + "/a.png");

            serving.get(5, TimeUnit.SECONDS);
            assertNotNull(image);
            assertEquals(3, image.getWidth());
            assertEquals(CameraTestAssets.GREEN, image.getRGB(0, 0));
        }
    }

    @Test
    void redirectsAndOversizedBodiesAreRefused() throws IOException {
        String redirect = "HTTP/1.1 302 Found\r\nLocation: http://127.0.0.1/\r\nContent-Length: 0\r\n\r\n";
        assertNull(CameraUrlFetcher.readResponse(stream(redirect), 100));
        String big = "HTTP/1.1 200 OK\r\nContent-Length: 500\r\n\r\n" + "x".repeat(500);
        assertNull(CameraUrlFetcher.readResponse(stream(big), 100));
        String untilClose = "HTTP/1.0 200 OK\r\n\r\n" + "x".repeat(101);
        assertNull(CameraUrlFetcher.readResponse(stream(untilClose), 100));
    }

    @Test
    void chunkedAndContentLengthBodiesAreRead() throws IOException {
        String chunked = "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n3\r\nabc\r\n2;ext=1\r\nde\r\n0\r\n\r\n";
        assertEquals("abcde", new String(CameraUrlFetcher.readResponse(stream(chunked), 100), StandardCharsets.UTF_8));
        String sized = "HTTP/1.1 200 OK\r\ncontent-length: 4\r\n\r\nwxyzEXTRA";
        assertEquals("wxyz", new String(CameraUrlFetcher.readResponse(stream(sized), 100), StandardCharsets.UTF_8));
        String overChunk = "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\nFF\r\n";
        assertNull(CameraUrlFetcher.readResponse(stream(overChunk), 100));
    }

    private static InputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static void serve(ServerSocket server, byte[] body, List<String> requestHeads) {
        try (Socket client = server.accept()) {
            client.setSoTimeout(3000);
            InputStream in = client.getInputStream();
            ByteArrayOutputStream head = new ByteArrayOutputStream();
            int last = 0;
            int b;
            while ((b = in.read()) >= 0) {
                head.write(b);
                last = (last << 8) | b;
                if (last == 0x0D0A0D0A) {
                    break;
                }
            }
            requestHeads.add(head.toString(StandardCharsets.ISO_8859_1));
            OutputStream out = client.getOutputStream();
            out.write(("HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: " + body.length + "\r\n\r\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
            out.write(body);
            out.flush();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
