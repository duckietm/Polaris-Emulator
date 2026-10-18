package com.eu.habbo.habbohotel.wired.share;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * The versioned wired setup a room exports and another room imports.
 *
 * <p>A share code is {@link #CODE_PREFIX} followed by the base64 of the gzipped JSON of this document.
 * The JSON holds, per box, its furni type, its tile relative to the anchor (the lowest x and y of the
 * exported boxes), its rotation and the editor state its save packet carries. Furni picks never carry
 * item ids: a pick of another exported box is {@code "b<index>"}, a pick of any other furni is {@code
 * "f<index>"} into {@link #furni} (type, relative tile and its place in that tile's stack of the same
 * type). Variable references to an exported box become {@code "#<index>"} ({@code custom:#<index>} inside
 * the text). Nothing in a code is trusted on import: {@link #decode} only bounds and parses it.
 */
final class WiredSetupDocument {
    static final String FORMAT = "octane.wired.setup";
    static final int VERSION = 1;
    static final String CODE_PREFIX = "OWS1:";

    /** A share code travels as one packet string (unsigned short length). */
    static final int MAX_CODE_LENGTH = 65_000;
    /** Inflated JSON is bounded so a small code cannot expand into a large allocation. */
    static final int MAX_JSON_BYTES = 2 * 1024 * 1024;

    static final int MAX_BOXES = 300;
    static final int MAX_FURNI_REFERENCES = 1_000;
    static final int MAX_TYPE_LENGTH = 100;
    /** Relative tiles beyond this are not a room. */
    static final int MAX_OFFSET = 255;

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    String format = FORMAT;
    int version = VERSION;
    int anchorX;
    int anchorY;
    List<Box> boxes = new ArrayList<>();
    List<Furni> furni = new ArrayList<>();

    /** One exported box. */
    static final class Box {
        String type;
        String holder;
        int dx;
        int dy;
        int rot;
        boolean fresh;
        int[] ints;
        String text;
        List<String> picks;
        List<String> picks2;
        int[] furniSources;
        int[] userSources;
        List<String> variables;
        int delay;
        int quantifier;
        boolean filter;
        boolean invert;
    }

    /** A picked furni that is not one of the exported boxes. */
    static final class Furni {
        String type;
        int dx;
        int dy;
        /** Its place among the furni of the same type on that tile, lowest first. */
        int n;
    }

    /** Raised for a code that is not a readable setup of a supported version. */
    static final class InvalidCodeException extends Exception {
        private static final long serialVersionUID = 1L;

        final int status;

        InvalidCodeException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    String encode() {
        byte[] json = GSON.toJson(this).getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
            gzip.write(json);
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
        return CODE_PREFIX + Base64.getEncoder().encodeToString(compressed.toByteArray());
    }

    static WiredSetupDocument decode(String code) throws InvalidCodeException {
        if (code == null) throw new InvalidCodeException(WiredSetupShare.STATUS_INVALID_CODE, "empty");
        String trimmed = code.strip();
        if (trimmed.length() > MAX_CODE_LENGTH) {
            throw new InvalidCodeException(WiredSetupShare.STATUS_TOO_LARGE, "code too long");
        }
        if (!trimmed.startsWith(CODE_PREFIX)) {
            throw new InvalidCodeException(WiredSetupShare.STATUS_INVALID_CODE, "unknown prefix");
        }

        byte[] compressed;
        try {
            compressed = Base64.getMimeDecoder().decode(trimmed.substring(CODE_PREFIX.length()));
        } catch (IllegalArgumentException e) {
            throw new InvalidCodeException(WiredSetupShare.STATUS_INVALID_CODE, "not base64");
        }

        byte[] json = inflate(compressed);
        WiredSetupDocument document;
        try {
            document = GSON.fromJson(new String(json, StandardCharsets.UTF_8), WiredSetupDocument.class);
        } catch (JsonParseException | IllegalStateException | NumberFormatException e) {
            throw new InvalidCodeException(WiredSetupShare.STATUS_INVALID_CODE, "not json");
        }
        if (document == null || !FORMAT.equals(document.format)) {
            throw new InvalidCodeException(WiredSetupShare.STATUS_INVALID_CODE, "not a wired setup");
        }
        if (document.version != VERSION) {
            throw new InvalidCodeException(WiredSetupShare.STATUS_UNSUPPORTED_VERSION, "version");
        }
        if (document.boxes == null) document.boxes = new ArrayList<>();
        if (document.furni == null) document.furni = new ArrayList<>();
        if (document.boxes.size() > MAX_BOXES || document.furni.size() > MAX_FURNI_REFERENCES) {
            throw new InvalidCodeException(WiredSetupShare.STATUS_TOO_MANY_BOXES, "too many entries");
        }
        return document;
    }

    private static byte[] inflate(byte[] compressed) throws InvalidCodeException {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                if (out.size() + read > MAX_JSON_BYTES) {
                    throw new InvalidCodeException(WiredSetupShare.STATUS_TOO_LARGE, "inflates too large");
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new InvalidCodeException(WiredSetupShare.STATUS_INVALID_CODE, "not gzip");
        }
    }

    /** The box index of a {@code "b<index>"} pick, or -1. */
    static int boxReference(String pick) {
        return reference(pick, 'b');
    }

    /** The furni index of an {@code "f<index>"} pick, or -1. */
    static int furniReference(String pick) {
        return reference(pick, 'f');
    }

    private static int reference(String pick, char kind) {
        if (pick == null || pick.length() < 2 || pick.length() > 6 || pick.charAt(0) != kind) return -1;
        int value = 0;
        for (int i = 1; i < pick.length(); i++) {
            char c = pick.charAt(i);
            if (c < '0' || c > '9') return -1;
            value = value * 10 + (c - '0');
        }
        return value;
    }
}
