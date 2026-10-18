package com.eu.habbo.imaging.camera.render;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WallColorResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(WallColorResolver.class);

    private final Map<String, Integer> wallpaintMap;

    public WallColorResolver(Path configFile) {
        this.wallpaintMap = loadWallpaintMap(configFile);
    }

    public Integer lookup(String wallPaint) {
        if (wallPaint == null || wallPaint.isEmpty()) {
            return null;
        }
        return this.wallpaintMap.get(wallPaint);
    }

    public int size() {
        return this.wallpaintMap.size();
    }

    private static Map<String, Integer> loadWallpaintMap(Path configFile) {
        Map<String, Integer> map = new HashMap<>();
        if (configFile == null || !Files.isRegularFile(configFile)) {
            LOGGER.debug("Camera Wall Colour Resolver -> No overrides loaded ({} not found).", configFile);
            return map;
        }

        Properties props = new Properties();
        try (InputStream in = new FileInputStream(configFile.toFile())) {
            props.load(in);
        } catch (Exception e) {
            LOGGER.warn("Failed to read camera wall colour overrides from {}", configFile, e);
            return map;
        }

        for (String key : props.stringPropertyNames()) {
            String value = props.getProperty(key, "").trim();
            if (value.isEmpty()) continue;
            try {
                int parsed = Integer.decode(value) & 0xFFFFFF;
                map.put(key.trim(), parsed);
            } catch (NumberFormatException nfe) {
                LOGGER.warn("Ignoring invalid camera wall colour entry {}={}", key, value);
            }
        }

        LOGGER.debug("Camera Wall Colour Resolver -> Loaded {} overrides from {}.", map.size(), configFile);
        return map;
    }
}
