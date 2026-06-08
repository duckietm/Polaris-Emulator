package com.eu.habbo.habbohotel.items;

import com.eu.habbo.Emulator;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class FurnidataSourceResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(FurnidataSourceResolver.class);

    public enum Status {
        RESOLVED,
        SOURCE_MISSING,
        CONFIG_MISSING,
        UNRESOLVED_PLACEHOLDER,
        ERROR
    }

    public record Source(Path path, boolean directory, Status status, String message) {
        public boolean ok() {
            return this.status == Status.RESOLVED && this.path != null && Files.exists(this.path);
        }
    }

    private FurnidataSourceResolver() {
    }

    public static Source resolve() {
        try {
            String override = Emulator.getConfig().getValue("items.furnidata.path", "");
            if (!override.isEmpty()) {
                Path p = Paths.get(override);
                if (Files.exists(p)) return new Source(p, Files.isDirectory(p), Status.RESOLVED, "items.furnidata.path");
                return new Source(p, Files.isDirectory(p), Status.SOURCE_MISSING, "items.furnidata.path does not exist");
            }

            String rendererConfigPath = Emulator.getConfig().getValue("furni.editor.renderer.config.path", "");
            String assetBasePath = Emulator.getConfig().getValue("furni.editor.asset.base.path", "");

            if (!rendererConfigPath.isEmpty()) {
                Source fromRenderer = resolveFromRendererConfig(Paths.get(rendererConfigPath), assetBasePath.isEmpty() ? null : Paths.get(assetBasePath));
                if (fromRenderer.ok() || fromRenderer.status() == Status.UNRESOLVED_PLACEHOLDER) return fromRenderer;
            }

            Source fallback = resolveFromAssetBase(assetBasePath);
            if (fallback != null) return fallback;

            return new Source(null, false, Status.CONFIG_MISSING, "No furnidata source config found");
        } catch (Exception e) {
            LOGGER.warn("FurnidataSourceResolver failed", e);
            return new Source(null, false, Status.ERROR, e.getMessage() != null ? e.getMessage() : "Resolver error");
        }
    }

    public static Source resolveFromRendererConfig(Path rendererConfig, Path assetBase) {
        try {
            if (rendererConfig == null || !Files.exists(rendererConfig)) {
                return new Source(rendererConfig, false, Status.SOURCE_MISSING, "renderer-config path does not exist");
            }

            String raw = Files.readString(rendererConfig, StandardCharsets.UTF_8);
            JsonObject rendererObj = JsonParser.parseString(FurnidataReader.stripJson5(raw)).getAsJsonObject();
            String furniUrl = expandRendererUrl(rendererObj, "furnidata.url");

            if (furniUrl.isBlank()) return new Source(null, false, Status.CONFIG_MISSING, "furnidata.url is missing");
            if (hasUnresolvedPathPlaceholder(furniUrl)) return new Source(null, false, Status.UNRESOLVED_PLACEHOLDER, furniUrl);

            Source source = toLocalSource(assetBase, furniUrl);
            if (source == null) return new Source(null, false, Status.CONFIG_MISSING, "furni.editor.asset.base.path is missing");
            if (!Files.exists(source.path())) return new Source(source.path(), source.directory(), Status.SOURCE_MISSING, "Resolved source does not exist");

            return source;
        } catch (Exception e) {
            return new Source(null, false, Status.ERROR, e.getMessage() != null ? e.getMessage() : "renderer-config parse failed");
        }
    }

    private static Source resolveFromAssetBase(String assetBasePath) {
        if (assetBasePath == null || assetBasePath.isEmpty()) return null;

        Path dir = Paths.get(assetBasePath);
        Path split = dir.resolve("furnidata");
        if (Files.isDirectory(split)) return new Source(split, true, Status.RESOLVED, "asset base split furnidata");

        Path legacy = dir.resolve("FurnitureData.json");
        if (Files.exists(legacy)) return new Source(legacy, false, Status.RESOLVED, "asset base FurnitureData.json");

        return new Source(dir, true, Status.SOURCE_MISSING, "No furnidata or FurnitureData.json under asset base");
    }

    public static String expandRendererUrl(JsonObject rendererObj, String key) {
        if (rendererObj == null || !rendererObj.has(key)) return "";

        String value = rendererObj.get(key).getAsString();
        for (int i = 0; i < 10; i++) {
            int start = value.indexOf("${");
            if (start < 0) break;

            int end = value.indexOf('}', start + 2);
            if (end < 0) break;

            String placeholder = value.substring(start + 2, end);
            if (!rendererObj.has(placeholder)) break;

            value = value.substring(0, start) + rendererObj.get(placeholder).getAsString() + value.substring(end + 1);
        }

        return value;
    }

    public static Source toLocalSource(Path assetBase, String furniUrl) {
        if (furniUrl == null || furniUrl.isBlank()) return null;

        String cleanUrl = stripQueryAndFragment(furniUrl);
        boolean splitMode = cleanUrl.endsWith("/");

        if (!cleanUrl.startsWith("http")) {
            Path local = Paths.get(cleanUrl);
            return new Source(local, splitMode || Files.isDirectory(local), Status.RESOLVED, "local furnidata.url");
        }

        if (assetBase == null) return null;

        String urlPath;
        try {
            urlPath = URI.create(cleanUrl).getPath();
        } catch (Exception e) {
            int scheme = cleanUrl.indexOf("://");
            int pathStart = scheme >= 0 ? cleanUrl.indexOf('/', scheme + 3) : -1;
            urlPath = pathStart >= 0 ? cleanUrl.substring(pathStart) : cleanUrl;
        }

        String normalized = urlPath.replace('\\', '/');
        String baseName = assetBase.getFileName() != null ? assetBase.getFileName().toString() : "";
        String marker = "/" + baseName + "/";
        int markerIndex = baseName.isEmpty() ? -1 : normalized.indexOf(marker);

        Path candidate;
        if (markerIndex >= 0) {
            candidate = assetBase.resolve(normalized.substring(markerIndex + marker.length()));
        } else if (splitMode) {
            String trimmed = normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
            candidate = assetBase.resolve(trimmed.substring(trimmed.lastIndexOf('/') + 1));
        } else {
            candidate = assetBase.resolve(normalized.substring(normalized.lastIndexOf('/') + 1));
        }

        return new Source(candidate, splitMode || Files.isDirectory(candidate), Status.RESOLVED, "renderer-config furnidata.url");
    }

    private static boolean hasUnresolvedPathPlaceholder(String value) {
        if (value == null) return false;
        return stripQueryAndFragment(value).contains("${");
    }

    private static String stripQueryAndFragment(String value) {
        String out = value;
        int q = out.indexOf('?');
        if (q >= 0) out = out.substring(0, q);
        int h = out.indexOf('#');
        if (h >= 0) out = out.substring(0, h);
        return out;
    }
}
