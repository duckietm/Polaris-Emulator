package com.eu.habbo.imaging.camera.assets;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The gamedata maps the client uses to find avatar libraries: {@code FigureMap.json} ({@code
 * {"libraries":[{"id":"hh_human_body","parts":[{"id":"1","type":"hd"}]}]}}) maps a part type and id
 * to the libraries holding it, and {@code EffectMap.json} ({@code {"effects":[{"id":"29",
 * "lib":"Dance1"}]}}) maps an effect id to its library.
 */
public final class FigureLibraryMap {
    private static final FigureLibraryMap EMPTY = new FigureLibraryMap(Map.of(), Map.of());

    private final Map<String, List<String>> partLibraries;
    private final Map<String, List<String>> effectLibraries;

    FigureLibraryMap(Map<String, List<String>> partLibraries, Map<String, List<String>> effectLibraries) {
        this.partLibraries = partLibraries;
        this.effectLibraries = effectLibraries;
    }

    public static FigureLibraryMap empty() {
        return EMPTY;
    }

    public static FigureLibraryMap load(Path figureMap, Path effectMap, long maxBytes) throws IOException {
        Map<String, List<String>> parts = new HashMap<>();
        Map<String, List<String>> effects = new HashMap<>();
        if (figureMap != null && Files.isRegularFile(figureMap)) {
            parseFigureMap(read(figureMap, maxBytes), parts);
        }
        if (effectMap != null && Files.isRegularFile(effectMap)) {
            parseEffectMap(read(effectMap, maxBytes), effects);
        }
        return new FigureLibraryMap(freeze(parts), freeze(effects));
    }

    public static FigureLibraryMap parse(String figureMapJson, String effectMapJson) throws IOException {
        Map<String, List<String>> parts = new HashMap<>();
        Map<String, List<String>> effects = new HashMap<>();
        if (figureMapJson != null) {
            parseFigureMap(figureMapJson, parts);
        }
        if (effectMapJson != null) {
            parseEffectMap(effectMapJson, effects);
        }
        return new FigureLibraryMap(freeze(parts), freeze(effects));
    }

    /** Libraries that hold figure part {@code type} with id {@code id} (e.g. {@code hd}, {@code 180}). */
    public List<String> librariesForPart(String type, String id) {
        return this.partLibraries.getOrDefault(type + ":" + id, List.of());
    }

    public List<String> librariesForEffect(String effectId) {
        return this.effectLibraries.getOrDefault(effectId, List.of());
    }

    public int partCount() {
        return this.partLibraries.size();
    }

    public int effectCount() {
        return this.effectLibraries.size();
    }

    private static String read(Path path, long maxBytes) throws IOException {
        if (Files.size(path) > maxBytes) {
            throw new IOException(path.getFileName() + " is over the size limit of " + maxBytes + " bytes");
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static JsonObject root(String json) throws IOException {
        try {
            JsonElement element = JsonParser.parseString(json);
            if (!element.isJsonObject()) {
                throw new IOException("Map json is not an object");
            }
            return element.getAsJsonObject();
        } catch (RuntimeException | StackOverflowError e) {
            throw new IOException("Map json is invalid", e);
        }
    }

    private static void parseFigureMap(String json, Map<String, List<String>> parts) throws IOException {
        JsonElement libraries = root(json).get("libraries");
        if (libraries == null || !libraries.isJsonArray()) {
            return;
        }
        for (JsonElement libraryElement : libraries.getAsJsonArray()) {
            if (!libraryElement.isJsonObject()) {
                continue;
            }
            JsonObject library = libraryElement.getAsJsonObject();
            String libraryId = text(library, "id");
            JsonElement partList = library.get("parts");
            if (libraryId == null || partList == null || !partList.isJsonArray()) {
                continue;
            }
            for (JsonElement partElement : partList.getAsJsonArray()) {
                if (!partElement.isJsonObject()) {
                    continue;
                }
                String id = text(partElement.getAsJsonObject(), "id");
                String type = text(partElement.getAsJsonObject(), "type");
                if (id != null && type != null) {
                    add(parts, type + ":" + id, libraryId);
                }
            }
        }
    }

    private static void parseEffectMap(String json, Map<String, List<String>> effects) throws IOException {
        JsonElement list = root(json).get("effects");
        if (list == null || !list.isJsonArray()) {
            return;
        }
        for (JsonElement effectElement : list.getAsJsonArray()) {
            if (!effectElement.isJsonObject()) {
                continue;
            }
            String id = text(effectElement.getAsJsonObject(), "id");
            String library = text(effectElement.getAsJsonObject(), "lib");
            if (id != null && library != null) {
                add(effects, id, library);
            }
        }
    }

    private static void add(Map<String, List<String>> map, String key, String library) {
        List<String> libraries = map.computeIfAbsent(key, ignored -> new ArrayList<>(1));
        if (!libraries.contains(library)) {
            libraries.add(library);
        }
    }

    private static Map<String, List<String>> freeze(Map<String, List<String>> map) {
        Map<String, List<String>> frozen = new HashMap<>(map.size() * 2);
        map.forEach((key, value) -> frozen.put(key, List.copyOf(value)));
        return Collections.unmodifiableMap(frozen);
    }

    private static String text(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }
}
