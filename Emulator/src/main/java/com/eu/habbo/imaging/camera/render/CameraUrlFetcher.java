package com.eu.habbo.imaging.camera.render;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Fetches the bytes of an external sprite image ({@code http(s)://...}) with the host pinned: the
 * name is resolved once, every address is checked (no loopback, private, link-local, multicast or
 * wildcard address unless the host is allow-listed), and the connection goes to that checked
 * address. A DNS answer that changes between the check and the connection cannot redirect the fetch.
 *
 * <p>The request is a plain HTTP/1.1 GET carrying the original {@code Host} header; for https the
 * TLS handshake uses the original host name for SNI and certificate verification. Redirects are not
 * followed: anything but 200 is a failure.
 */
final class CameraUrlFetcher {
    private static final int MAX_HEADER_BYTES = 16 * 1024;

    /** Resolves a host name; tests replace it. */
    @FunctionalInterface
    interface Resolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    static final Resolver SYSTEM_RESOLVER = InetAddress::getAllByName;

    private final Resolver resolver;
    private final SSLSocketFactory sslSocketFactory;

    CameraUrlFetcher(Resolver resolver, SSLSocketFactory sslSocketFactory) {
        this.resolver = resolver;
        this.sslSocketFactory = sslSocketFactory;
    }

    static CameraUrlFetcher system() {
        return new CameraUrlFetcher(SYSTEM_RESOLVER, (SSLSocketFactory) SSLSocketFactory.getDefault());
    }

    /** The body of a 200 answer, or null when the URL is refused, fails or is over {@code maxBytes}. */
    byte[] fetch(String requestUrl, CameraRenderOptions options) throws IOException {
        URI uri;
        try {
            uri = URI.create(requestUrl);
        } catch (IllegalArgumentException e) {
            return null;
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        boolean https = "https".equals(scheme);
        if (!https && !"http".equals(scheme)) {
            return null;
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty() || uri.getRawUserInfo() != null) {
            return null;
        }
        String bareHost = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
        InetAddress address = this.pinnedAddress(bareHost, options.allowedHosts());
        if (address == null) {
            return null;
        }
        int port = uri.getPort() < 0 ? (https ? 443 : 80) : uri.getPort();
        if (port <= 0 || port > 65535) {
            return null;
        }

        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        if (uri.getRawQuery() != null) {
            path += "?" + uri.getRawQuery();
        }
        if (containsControl(path) || containsControl(host)) {
            return null;
        }
        try (Socket socket = this.connect(address, port, bareHost, https, options)) {
            int defaultPort = https ? 443 : 80;
            String hostHeader = host + (port == defaultPort ? "" : ":" + port);
            String request = "GET " + path + " HTTP/1.1\r\n"
                    + "Host: " + hostHeader + "\r\n"
                    + "User-Agent: Polaris-Camera\r\n"
                    + "Accept: image/*\r\n"
                    + "Connection: close\r\n\r\n";
            OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            return readResponse(deadlineStream(socket.getInputStream(), options), options.fetchMaxBytes());
        }
    }

    /**
     * Caps the whole read, not each read call.
     *
     * <p>{@code soTimeout} only bounds the wait for the <em>next</em> bytes, and the render
     * deadline was checked before a fetch, never during one. A server that answers with one
     * byte every soTimeout minus a little therefore held a camera worker for as long as it
     * liked - two of them by default, with a queue of sixteen, so the camera and every hotel
     * thumbnail behind it stayed busy. The sprite name comes out of the client's scene.
     *
     * <p>The ceiling is the fetch budget of the render ({@code fetchBudgetMs}), and the
     * render's own deadline when it has one, whichever comes first.
     */
    private static InputStream deadlineStream(InputStream in, CameraRenderOptions options) {
        long ceiling = System.nanoTime() + Math.max(0L, options.fetchBudgetMs()) * 1_000_000L;
        if (options.deadlineNanos() != 0L && options.deadlineNanos() - ceiling < 0) {
            ceiling = options.deadlineNanos();
        }
        return new DeadlineInputStream(in, ceiling);
    }

    /** An input stream that stops reading once a wall-clock deadline has passed. */
    static final class DeadlineInputStream extends java.io.FilterInputStream {
        private final long deadlineNanos;

        DeadlineInputStream(InputStream in, long deadlineNanos) {
            super(in);
            this.deadlineNanos = deadlineNanos;
        }

        private void checkDeadline() throws IOException {
            if (System.nanoTime() - this.deadlineNanos > 0) {
                throw new IOException("camera sprite fetch ran past its time budget");
            }
        }

        @Override
        public int read() throws IOException {
            this.checkDeadline();
            return super.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            this.checkDeadline();
            return super.read(buffer, offset, length);
        }
    }

    /**
     * Resolves {@code host} once and returns the address to connect to, or null when the name does
     * not resolve or any of its addresses is internal and the host is not allow-listed.
     */
    InetAddress pinnedAddress(String host, String allowedHosts) {
        if (host == null || host.isEmpty()) {
            return null;
        }
        InetAddress[] addresses;
        try {
            addresses = this.resolver.resolve(host);
        } catch (UnknownHostException | RuntimeException e) {
            return null;
        }
        if (addresses == null || addresses.length == 0 || addresses[0] == null) {
            return null;
        }
        if (CameraImageLoader.isHostAllowlisted(host, allowedHosts)) {
            return addresses[0];
        }
        for (InetAddress address : addresses) {
            if (address == null || isInternal(address)) {
                return null;
            }
        }
        return addresses[0];
    }

    static boolean isInternal(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress();
    }

    private Socket connect(InetAddress address, int port, String host, boolean https, CameraRenderOptions options)
            throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(address, port), options.fetchConnectTimeoutMs());
            socket.setSoTimeout(options.fetchReadTimeoutMs());
            if (!https) {
                return socket;
            }
            SSLSocket ssl = (SSLSocket) this.sslSocketFactory.createSocket(socket, host, port, true);
            SSLParameters parameters = ssl.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            if (!isIpLiteral(host)) {
                parameters.setServerNames(List.of(new SNIHostName(host)));
            }
            ssl.setSSLParameters(parameters);
            ssl.setSoTimeout(options.fetchReadTimeoutMs());
            ssl.startHandshake();
            return ssl;
        } catch (IOException | RuntimeException e) {
            socket.close();
            throw e;
        }
    }

    private static boolean isIpLiteral(String host) {
        return host.indexOf(':') >= 0 || host.matches("\\d{1,3}(\\.\\d{1,3}){3}");
    }

    private static boolean containsControl(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x21 || c == 0x7F) {
                return true;
            }
        }
        return false;
    }

    /** Reads a 200 answer's body (Content-Length, chunked, or until close), at most {@code maxBytes}. */
    static byte[] readResponse(InputStream in, int maxBytes) throws IOException {
        String head = readHead(in);
        if (head == null) {
            return null;
        }
        String[] lines = head.split("\r\n");
        String[] status = lines[0].split(" ", 3);
        if (status.length < 2 || !status[0].startsWith("HTTP/1.") || !"200".equals(status[1])) {
            return null;
        }
        long contentLength = -1;
        boolean chunked = false;
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String name = lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = lines[i].substring(colon + 1).trim();
            if (name.equals("content-length")) {
                try {
                    contentLength = Long.parseLong(value);
                } catch (NumberFormatException e) {
                    return null;
                }
            } else if (name.equals("transfer-encoding")) {
                chunked = value.toLowerCase(Locale.ROOT).contains("chunked");
            }
        }
        if (chunked) {
            return readChunked(in, maxBytes);
        }
        if (contentLength > maxBytes) {
            return null;
        }
        if (contentLength >= 0) {
            byte[] body = in.readNBytes((int) contentLength);
            return body.length == contentLength ? body : null;
        }
        return readBounded(in, maxBytes);
    }

    private static String readHead(InputStream in) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int last = 0;
        while (head.size() < MAX_HEADER_BYTES) {
            int b = in.read();
            if (b < 0) {
                return null;
            }
            head.write(b);
            last = (last << 8) | b;
            if (last == 0x0D0A0D0A) {
                String text = head.toString(StandardCharsets.ISO_8859_1);
                return text.substring(0, text.length() - 4);
            }
        }
        return null;
    }

    private static byte[] readChunked(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        while (true) {
            String sizeLine = readLine(in);
            if (sizeLine == null) {
                return null;
            }
            int semicolon = sizeLine.indexOf(';');
            String hex = (semicolon >= 0 ? sizeLine.substring(0, semicolon) : sizeLine).trim();
            long size;
            try {
                size = Long.parseLong(hex, 16);
            } catch (NumberFormatException e) {
                return null;
            }
            if (size < 0 || body.size() + size > maxBytes) {
                return null;
            }
            if (size == 0) {
                return body.toByteArray();
            }
            byte[] chunk = in.readNBytes((int) size);
            if (chunk.length != size) {
                return null;
            }
            body.write(chunk);
            if (readLine(in) == null) {
                return null;
            }
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        while (line.size() < 1024) {
            int b = in.read();
            if (b < 0) {
                return null;
            }
            if (b == '\n') {
                String text = line.toString(StandardCharsets.ISO_8859_1);
                return text.endsWith("\r") ? text.substring(0, text.length() - 1) : text;
            }
            line.write(b);
        }
        return null;
    }

    private static byte[] readBounded(InputStream stream, int maxBytes) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(Math.min(maxBytes, 16384));
        byte[] chunk = new byte[8192];
        int total = 0;
        int read;
        while ((read = stream.read(chunk)) != -1) {
            if (total + read > maxBytes) {
                return null;
            }
            buffer.write(chunk, 0, read);
            total += read;
        }
        return buffer.toByteArray();
    }
}
