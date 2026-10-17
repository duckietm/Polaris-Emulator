package com.eu.habbo.imaging.camera.assets;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Habbo's {@code .hab} asset bundle, read the way the client's {@code HabBundle.ts} reads it:
 *
 * <pre>
 * offset size field
 * 0      4    magic "HAB\0"
 * 4      2    version (u16 LE), 1
 * 6      2    flags (u16 LE)
 * 8      4    compressed index length (u32 LE)
 * 12     4    uncompressed index length (u32 LE)
 * 16     4    data section length (u32 LE)
 * 20     ...  zlib JSON index, then the data section
 * </pre>
 *
 * The index is {@code {format: "hab", version, name, entries: [{name, mimeType, offset,
 * storedLength, originalLength, compression?}]}}; entry offsets count from the start of the data
 * section. Every length is checked, so a damaged file fails instead of reading past its end.
 */
public final class HabBundle {
    private static final int HEADER_LENGTH = 20;
    private static final int HAB_VERSION = 1;
    private static final long MAX_INDEX_LENGTH = 64L * 1024 * 1024;
    private static final int MAX_ENTRIES = 100_000;
    private static final long MAX_SAFE_INTEGER = (1L << 53) - 1;
    private static final Pattern IMAGE_NAME = Pattern.compile("(?i).*\\.(png|webp|jpe?g|gif)$");

    /** One file of the bundle; its bytes are inflated when it was stored deflated. */
    public record Entry(String name, String mimeType, byte[] bytes) {
        public boolean isImage() {
            return this.mimeType.startsWith("image/")
                    || IMAGE_NAME.matcher(this.name).matches();
        }

        public boolean isJson() {
            return "application/json".equals(this.mimeType)
                    || this.name.toLowerCase(Locale.ROOT).endsWith(".json");
        }
    }

    private final String name;
    private final List<Entry> entries;

    private HabBundle(String name, List<Entry> entries) {
        this.name = name;
        this.entries = List.copyOf(entries);
    }

    public String name() {
        return this.name;
    }

    public List<Entry> entries() {
        return this.entries;
    }

    /** True when the bytes start with the {@code HAB\0} magic. */
    public static boolean isHab(byte[] bytes) {
        return bytes != null
                && bytes.length >= HEADER_LENGTH
                && bytes[0] == 0x48
                && bytes[1] == 0x41
                && bytes[2] == 0x42
                && bytes[3] == 0;
    }

    /**
     * Reads every entry.
     *
     * @param maxEntryBytes the largest index and the largest inflated entry accepted
     */
    public static HabBundle read(byte[] bytes, int maxEntryBytes) throws IOException {
        if (!isHab(bytes)) {
            throw new IOException("Not a HAB bundle (missing \"HAB\\0\" magic)");
        }
        ByteBuffer header = ByteBuffer.wrap(bytes, 0, HEADER_LENGTH).order(ByteOrder.LITTLE_ENDIAN);
        int version = Short.toUnsignedInt(header.getShort(4));
        long indexLength = Integer.toUnsignedLong(header.getInt(8));
        long indexRawLength = Integer.toUnsignedLong(header.getInt(12));
        long dataLength = Integer.toUnsignedLong(header.getInt(16));
        long dataStart = HEADER_LENGTH + indexLength;

        if (version != HAB_VERSION) {
            throw new IOException("HAB bundle version " + version + " is not supported");
        }
        long indexLimit = Math.min(MAX_INDEX_LENGTH, maxEntryBytes);
        if (indexLength > indexLimit || indexRawLength > indexLimit) {
            throw new IOException("HAB index is too large");
        }
        if (dataStart + dataLength > bytes.length) {
            throw new IOException("HAB bundle is shorter than its header says");
        }

        byte[] indexBytes = inflate(bytes, HEADER_LENGTH, (int) indexLength, (int) indexLimit, false);
        if (indexRawLength != 0 && indexBytes.length != indexRawLength) {
            throw new IOException("HAB index has the wrong length");
        }

        JsonObject index;
        try {
            JsonElement parsed = JsonParser.parseString(new String(indexBytes, StandardCharsets.UTF_8));
            index = parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (RuntimeException | StackOverflowError e) {
            throw new IOException("HAB index is not valid json", e);
        }
        JsonElement format = index == null ? null : index.get("format");
        JsonElement entriesJson = index == null ? null : index.get("entries");
        if (format == null
                || !format.isJsonPrimitive()
                || !"hab".equals(format.getAsString())
                || entriesJson == null
                || !entriesJson.isJsonArray()) {
            throw new IOException("HAB index is not a \"hab\" index");
        }
        JsonArray array = entriesJson.getAsJsonArray();
        if (array.size() > MAX_ENTRIES) {
            throw new IOException("HAB index has too many entries");
        }

        Set<String> names = new HashSet<>();
        List<Entry> entries = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            JsonObject entry = element.isJsonObject() ? element.getAsJsonObject() : null;
            String entryName = entry == null ? null : string(entry, "name");
            if (entryName == null || !names.add(entryName)) {
                throw new IOException("HAB entry \"" + entryName + "\" is missing or listed twice");
            }
            long offset = safeInteger(entry, "offset");
            long storedLength = safeInteger(entry, "storedLength");
            if (offset < 0 || storedLength < 0 || offset + storedLength > dataLength) {
                throw new IOException("HAB entry \"" + entryName + "\" lies outside the data section");
            }
            String compression = entry.has("compression") ? string(entry, "compression") : null;
            if (entry.has("compression") && !"deflate".equals(compression) && !"none".equals(compression)) {
                throw new IOException(
                        "HAB entry \"" + entryName + "\" uses unknown compression \"" + compression + "\"");
            }
            if (storedLength > maxEntryBytes) {
                throw new IOException("HAB entry \"" + entryName + "\" is too large");
            }
            int start = (int) (dataStart + offset);
            byte[] data;
            if ("deflate".equals(compression)) {
                data = inflateEntry(bytes, start, (int) storedLength, maxEntryBytes);
            } else {
                data = new byte[(int) storedLength];
                System.arraycopy(bytes, start, data, 0, data.length);
            }
            if (entry.has("originalLength") && data.length != safeInteger(entry, "originalLength")) {
                throw new IOException("HAB entry \"" + entryName + "\" has the wrong length");
            }
            String mimeType = entry.has("mimeType") ? string(entry, "mimeType") : "";
            entries.add(new Entry(entryName, mimeType == null ? "" : mimeType, data));
        }

        String bundleName = index.has("name") ? string(index, "name") : "";
        return new HabBundle(bundleName == null ? "" : bundleName, entries);
    }

    /** Habbo stores zlib streams; a raw deflate stream is accepted too. */
    private static byte[] inflateEntry(byte[] bytes, int offset, int length, int maxBytes) throws IOException {
        try {
            return inflate(bytes, offset, length, maxBytes, false);
        } catch (IOException zlibFailed) {
            return inflate(bytes, offset, length, maxBytes, true);
        }
    }

    private static byte[] inflate(byte[] bytes, int offset, int length, int maxBytes, boolean raw) throws IOException {
        Inflater inflater = new Inflater(raw);
        inflater.setInput(bytes, offset, length);
        byte[] buffer = new byte[8192];
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(16, Math.min(length * 4, maxBytes)))) {
            long total = 0;
            while (!inflater.finished()) {
                int count;
                try {
                    count = inflater.inflate(buffer);
                } catch (DataFormatException e) {
                    throw new IOException("Malformed compressed HAB data", e);
                }
                if (count == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) {
                        throw new IOException("Truncated compressed HAB data");
                    }
                    break;
                }
                total += count;
                if (total > maxBytes) {
                    throw new IOException("Inflated HAB data exceeds limit: " + maxBytes);
                }
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        } finally {
            inflater.end();
        }
    }

    private static String string(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null
                        && element.isJsonPrimitive()
                        && element.getAsJsonPrimitive().isString()
                ? element.getAsString()
                : null;
    }

    /** A JavaScript safe integer ({@code Number.isSafeInteger}), or -1. */
    private static long safeInteger(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null
                || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()) {
            return -1;
        }
        double value = element.getAsDouble();
        if (value != Math.rint(value) || Math.abs(value) > MAX_SAFE_INTEGER) {
            return -1;
        }
        return (long) value;
    }
}
