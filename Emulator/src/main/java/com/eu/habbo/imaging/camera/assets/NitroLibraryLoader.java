package com.eu.habbo.imaging.camera.assets;

import com.eu.habbo.util.crypto.ZIP;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Reads asset libraries in the formats the Octane client loads.
 *
 * <ul>
 *   <li>A {@code .nitro} bundle: a big-endian {@code short} file count, then per file a {@code
 *       short} name length, the UTF-8 name, an {@code int} length and that many zlib-deflated
 *       bytes. One {@code .json} file holds the asset library, one image file the sheet.
 *   <li>A library folder (how the client ships {@code room}): a {@code *.json} asset library and
 *       loose images named {@code <library>_<asset>.png} or {@code <asset>.png}, in the folder or
 *       its {@code images} sub folder.
 * </ul>
 */
public final class NitroLibraryLoader {
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_.@\\-]{1,200}");

    private final long maxFileBytes;
    private final long maxSheetPixels;

    /**
     * @param maxFileBytes the largest bundle file and the largest inflated entry accepted
     * @param maxSheetPixels the largest sheet (width times height) decoded
     */
    public NitroLibraryLoader(long maxFileBytes, long maxSheetPixels) {
        this.maxFileBytes = Math.max(1024, maxFileBytes);
        this.maxSheetPixels = Math.max(1, maxSheetPixels);
    }

    public static boolean isSafeName(String name) {
        return name != null && SAFE_NAME.matcher(name).matches() && !name.contains("..");
    }

    /** Loads a {@code .nitro} file or a library folder; null when it is missing or unreadable. */
    public NitroLibrary load(Path path) throws IOException {
        if (path == null) {
            return null;
        }
        if (Files.isDirectory(path)) {
            return this.loadFolder(path);
        }
        if (!Files.isRegularFile(path)) {
            return null;
        }
        long size = Files.size(path);
        if (size > this.maxFileBytes) {
            throw new IOException(
                    "Bundle " + path.getFileName() + " is " + size + " bytes, over the limit of " + this.maxFileBytes);
        }
        return this.read(Files.readAllBytes(path), fallbackName(path));
    }

    /** Reads a {@code .nitro} or a {@code .hab} bundle, told apart by the {@code HAB\0} magic. */
    public NitroLibrary read(byte[] bundle, String fallbackName) throws IOException {
        return HabBundle.isHab(bundle) ? this.readHab(bundle, fallbackName) : this.readBundle(bundle, fallbackName);
    }

    /**
     * Reads a {@code .hab} bundle that holds what a {@code .nitro} holds: the asset library json and
     * one sheet image, as the client's OctaneBundle does. Habbo's flash-style libraries (xml and
     * loose images) are refused, as the client refuses them: they go through the converter first.
     */
    public NitroLibrary readHab(byte[] bundle, String fallbackName) throws IOException {
        int maxEntry = (int) Math.min(Integer.MAX_VALUE, this.maxFileBytes);
        HabBundle hab = HabBundle.read(bundle, maxEntry);
        HabBundle.Entry jsonEntry = null;
        HabBundle.Entry imageEntry = null;
        int images = 0;
        for (HabBundle.Entry entry : hab.entries()) {
            if (entry.isImage()) {
                images++;
                if (imageEntry == null) {
                    imageEntry = entry;
                }
            } else if (jsonEntry == null && entry.isJson()) {
                jsonEntry = entry;
            }
        }
        if (jsonEntry == null || images > 1) {
            throw new IOException("HAB bundle '" + hab.name()
                    + "' is a flash-style library (xml and loose images); convert it to a sheet bundle first");
        }
        JsonObject json = parseJson(new String(jsonEntry.bytes(), StandardCharsets.UTF_8));
        // Habbo's clothes and effect bundles name the library only in documentClass and the index.
        String name = string(json, "name");
        if (name == null || name.isEmpty()) {
            String documentClass = string(json, "documentClass");
            if (documentClass != null && !documentClass.isEmpty()) {
                json.addProperty("name", documentClass);
            } else if (!hab.name().isEmpty()) {
                json.addProperty("name", hab.name());
            }
        }
        BufferedImage sheet = imageEntry == null ? null : this.decode(new ByteArrayInputStream(imageEntry.bytes()));
        return build(json, fallbackName, sheet, null);
    }

    public NitroLibrary readBundle(byte[] bundle, String fallbackName) throws IOException {
        JsonObject json = null;
        byte[] image = null;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bundle))) {
            int fileCount = in.readShort();
            if (fileCount < 0 || fileCount > 64) {
                throw new IOException("Bundle holds an implausible file count: " + fileCount);
            }
            for (int i = 0; i < fileCount; i++) {
                int nameLength = in.readUnsignedShort();
                byte[] nameBytes = new byte[nameLength];
                in.readFully(nameBytes);
                String fileName = new String(nameBytes, StandardCharsets.UTF_8);
                int length = in.readInt();
                if (length < 0 || length > in.available()) {
                    throw new IOException("Bundle entry " + fileName + " has an invalid length " + length);
                }
                byte[] compressed = new byte[length];
                in.readFully(compressed);
                byte[] inflated = ZIP.inflate(compressed, (int) Math.min(Integer.MAX_VALUE, this.maxFileBytes));
                if (fileName.endsWith(".json")) {
                    json = parseJson(new String(inflated, StandardCharsets.UTF_8));
                } else if (!fileName.endsWith(".atlas") && image == null) {
                    image = inflated;
                }
            }
        } catch (EOFException e) {
            throw new IOException("Bundle is truncated", e);
        }
        if (json == null) {
            throw new IOException("Bundle has no asset library json");
        }
        BufferedImage sheet = image == null ? null : this.decode(new ByteArrayInputStream(image));
        return build(json, fallbackName, sheet, null);
    }

    private NitroLibrary loadFolder(Path folder) throws IOException {
        Path jsonFile = null;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, "*.json")) {
            for (Path candidate : stream) {
                if (jsonFile == null || candidate.getFileName().toString().endsWith(".asset.json")) {
                    jsonFile = candidate;
                }
            }
        }
        if (jsonFile == null || Files.size(jsonFile) > this.maxFileBytes) {
            return null;
        }
        JsonObject json = parseJson(Files.readString(jsonFile, StandardCharsets.UTF_8));
        Path root = folder.toAbsolutePath().normalize();
        NitroLibrary.LooseImageReader reader = (library, source) -> this.readLooseImage(root, library, source);
        return build(json, folder.getFileName().toString(), null, reader);
    }

    private BufferedImage readLooseImage(Path root, String library, String source) {
        if (!isSafeName(source)) {
            return null;
        }
        String[] candidates = {
            "images/" + library + "_" + source + ".png", library + "_" + source + ".png",
            "images/" + source + ".png", source + ".png"
        };
        for (String candidate : candidates) {
            Path file = root.resolve(candidate).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                continue;
            }
            try (InputStream in = Files.newInputStream(file)) {
                return this.decode(in);
            } catch (IOException e) {
                return null;
            }
        }
        return null;
    }

    /** Decodes an image after checking its dimensions, so a crafted header cannot force a huge allocation. */
    public BufferedImage decode(InputStream input) throws IOException {
        try (ImageInputStream stream = ImageIO.createImageInputStream(input)) {
            if (stream == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > this.maxSheetPixels) {
                    throw new IOException("Image of " + pixels + " pixels is over the limit of " + this.maxSheetPixels);
                }
                return toArgb(reader.read(0));
            } finally {
                reader.dispose();
            }
        }
    }

    private static BufferedImage toArgb(BufferedImage image) {
        if (image == null || image.getType() == BufferedImage.TYPE_INT_ARGB) {
            return image;
        }
        BufferedImage argb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        int[] row = new int[image.getWidth()];
        for (int y = 0; y < image.getHeight(); y++) {
            image.getRGB(0, y, image.getWidth(), 1, row, 0, image.getWidth());
            argb.setRGB(0, y, image.getWidth(), 1, row, 0, image.getWidth());
        }
        return argb;
    }

    private static JsonObject parseJson(String text) throws IOException {
        try {
            JsonElement element = JsonParser.parseString(text);
            if (!element.isJsonObject()) {
                throw new IOException("Asset library json is not an object");
            }
            return element.getAsJsonObject();
        } catch (RuntimeException | StackOverflowError e) {
            throw new IOException("Asset library json is invalid", e);
        }
    }

    static NitroLibrary build(
            JsonObject json, String fallbackName, BufferedImage sheet, NitroLibrary.LooseImageReader reader) {
        String name = string(json, "name");
        if (name == null || name.isEmpty()) {
            name = fallbackName;
        }

        Map<String, NitroLibrary.AssetDefinition> assets = new HashMap<>();
        JsonObject assetsJson = object(json, "assets");
        if (assetsJson != null) {
            for (Map.Entry<String, JsonElement> entry : assetsJson.entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject asset = entry.getValue().getAsJsonObject();
                assets.put(
                        entry.getKey(),
                        new NitroLibrary.AssetDefinition(
                                string(asset, "source"),
                                integer(asset, "x"),
                                integer(asset, "y"),
                                bool(asset, "flipH"),
                                bool(asset, "flipV"),
                                bool(asset, "usesPalette")));
            }
        }

        Map<String, NitroLibrary.Frame> frames = new HashMap<>();
        JsonObject spritesheet = object(json, "spritesheet");
        JsonObject framesJson = spritesheet == null ? null : object(spritesheet, "frames");
        if (framesJson != null) {
            for (Map.Entry<String, JsonElement> entry : framesJson.entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject frameJson = entry.getValue().getAsJsonObject();
                JsonObject frame = object(frameJson, "frame");
                if (frame == null) {
                    continue;
                }
                JsonObject spriteSource = object(frameJson, "spriteSourceSize");
                JsonObject sourceSize = object(frameJson, "sourceSize");
                frames.put(
                        removeFileExtension(entry.getKey()),
                        new NitroLibrary.Frame(
                                integer(frame, "x"),
                                integer(frame, "y"),
                                integer(frame, "w"),
                                integer(frame, "h"),
                                bool(frameJson, "rotated"),
                                bool(frameJson, "trimmed"),
                                spriteSource == null ? 0 : integer(spriteSource, "x"),
                                spriteSource == null ? 0 : integer(spriteSource, "y"),
                                sourceSize == null ? 0 : integer(sourceSize, "w"),
                                sourceSize == null ? 0 : integer(sourceSize, "h")));
            }
        }

        Map<String, NitroLibrary.Palette> palettes = new HashMap<>();
        JsonObject palettesJson = object(json, "palettes");
        if (palettesJson != null) {
            for (Map.Entry<String, JsonElement> entry : palettesJson.entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject palette = entry.getValue().getAsJsonObject();
                int[] rgb = new int[256];
                for (int i = 0; i < rgb.length; i++) {
                    rgb[i] = 0xFF000000;
                }
                JsonElement colours = palette.get("rgb");
                if (colours != null && colours.isJsonArray()) {
                    JsonArray array = colours.getAsJsonArray();
                    for (int i = 0; i < array.size() && i < 256; i++) {
                        JsonElement colour = array.get(i);
                        if (colour.isJsonArray() && colour.getAsJsonArray().size() >= 3) {
                            JsonArray c = colour.getAsJsonArray();
                            rgb[i] = 0xFF000000
                                    | ((c.get(0).getAsInt() & 0xFF) << 16)
                                    | ((c.get(1).getAsInt() & 0xFF) << 8)
                                    | (c.get(2).getAsInt() & 0xFF);
                        }
                    }
                }
                palettes.put(entry.getKey(), new NitroLibrary.Palette(string(palette, "source"), rgb));
            }
        }

        return new NitroLibrary(name, assets, frames, palettes, sheet, reader);
    }

    private static String fallbackName(Path path) {
        String file = path.getFileName() == null ? "" : path.getFileName().toString();
        return removeFileExtension(file);
    }

    static String removeFileExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static JsonObject object(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static String string(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    private static int integer(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null
                || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()) {
            return 0;
        }
        return (int) Math.round(element.getAsDouble());
    }

    private static boolean bool(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsBoolean();
    }
}
