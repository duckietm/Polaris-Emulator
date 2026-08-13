package com.eu.habbo.habbohotel.catalog.versioning;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class CatalogStudioDocumentWireCodec {
    public static final String ENCODING = "GZIP_BASE64_UTF8";
    public static final int MAX_CHUNK_LENGTH = Short.MAX_VALUE;
    public static final int MAX_CHUNKS = 256;
    public static final int MAX_DECOMPRESSED_BYTES = 32 * 1024 * 1024;

    private CatalogStudioDocumentWireCodec() {}

    public static EncodedDocument encode(String document) {
        Objects.requireNonNull(document, "document");
        if (document.isEmpty()) return new EncodedDocument(ENCODING, List.of());

        byte[] documentBytes = document.getBytes(StandardCharsets.UTF_8);
        if (documentBytes.length > MAX_DECOMPRESSED_BYTES) {
            throw new IllegalArgumentException("Catalog Studio SQL document exceeds the decompressed size limit");
        }
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
            gzip.write(documentBytes);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to compress the Catalog Studio SQL document", exception);
        }

        String encoded = Base64.getEncoder().encodeToString(compressed.toByteArray());
        List<String> chunks = new ArrayList<>((encoded.length() / MAX_CHUNK_LENGTH) + 1);
        for (int offset = 0; offset < encoded.length(); offset += MAX_CHUNK_LENGTH) {
            chunks.add(encoded.substring(offset, Math.min(offset + MAX_CHUNK_LENGTH, encoded.length())));
        }
        if (chunks.size() > MAX_CHUNKS) {
            throw new IllegalArgumentException("Catalog Studio SQL document is too large to transfer safely");
        }
        return new EncodedDocument(ENCODING, chunks);
    }

    public static String decode(String encoding, List<String> chunks) {
        if (!ENCODING.equals(encoding)) {
            throw new IllegalArgumentException("Unsupported Catalog Studio document encoding: " + encoding);
        }
        Objects.requireNonNull(chunks, "chunks");
        if (chunks.size() > MAX_CHUNKS) {
            throw new IllegalArgumentException("Catalog Studio SQL document contains too many chunks");
        }
        int encodedLength = 0;
        for (String chunk : chunks) {
            Objects.requireNonNull(chunk, "chunk");
            if (chunk.length() > MAX_CHUNK_LENGTH) {
                throw new IllegalArgumentException("Catalog Studio SQL document contains an oversized chunk");
            }
            encodedLength = Math.addExact(encodedLength, chunk.length());
        }
        if (chunks.isEmpty()) return "";

        StringBuilder encoded = new StringBuilder(encodedLength);
        chunks.forEach(encoded::append);
        byte[] compressed;
        try {
            compressed = Base64.getDecoder().decode(encoded.toString());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Catalog Studio SQL document is not valid Base64", exception);
        }

        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed));
                ByteArrayOutputStream document = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = gzip.read(buffer)) >= 0) {
                total = Math.addExact(total, read);
                if (total > MAX_DECOMPRESSED_BYTES) {
                    throw new IllegalArgumentException(
                            "Catalog Studio SQL document exceeds the decompressed size limit");
                }
                document.write(buffer, 0, read);
            }
            return document.toString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Catalog Studio SQL document is not valid GZIP data", exception);
        }
    }

    public record EncodedDocument(String encoding, List<String> chunks) {
        public EncodedDocument {
            encoding = Objects.requireNonNull(encoding, "encoding");
            chunks = List.copyOf(chunks);
        }
    }
}
