package com.eu.habbo.habbohotel.games.snowwar.mapping;

import com.eu.habbo.habbohotel.games.snowwar.SnowWarPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads and caches SnowWar arenas (README 5.3).
 *
 * Map files are bundled as classpath resources under /snowwar/. A file with
 * the same name in the optional filesystem directory tools/snowwar_maps/
 * takes precedence, so arenas can be tweaked without rebuilding.
 */
public final class SnowWarMapsManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(SnowWarMapsManager.class);

    private static final String RESOURCE_DIR = "/snowwar/";
    private static final String OVERRIDE_DIR = "tools/snowwar_maps/";

    private static final ConcurrentHashMap<Integer, SnowWarMap> MAPS = new ConcurrentHashMap<>();

    private SnowWarMapsManager() {
    }

    public static SnowWarMap getMap(int mapId) {
        return MAPS.computeIfAbsent(mapId, SnowWarMapsManager::parseMap);
    }

    private static SnowWarMap parseMap(int mapId) {
        try {
            List<String> heightmapRows = readLines("arena_" + mapId + "_heightmap.txt");
            if (heightmapRows.isEmpty()) {
                LOGGER.error("SnowWar map {} has an empty heightmap.", mapId);
                return null;
            }

            List<SnowWarItem> items = new ArrayList<>();
            for (String line : readLines("arena_" + mapId + ".dat")) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 4) {
                    continue;
                }

                String name = parts[0];
                if (!SnowWarItemProperties.isKnownItem(name)) {
                    LOGGER.warn("SnowWar map {} contains unknown item '{}', skipping.", mapId, name);
                    continue;
                }

                items.add(new SnowWarItem(name,
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[3])));
            }

            List<SnowWarPoint> machinePositions = new ArrayList<>();
            for (String line : readLines("arena_" + mapId + "_snowmachines.dat")) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 2) {
                    continue;
                }

                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);

                machinePositions.add(new SnowWarPoint(x, y));

                // Main machine tile + hidden collision tiles (README 5.3).
                items.add(new SnowWarItem("snowball_machine", x, y, 0));
                items.add(new SnowWarItem("snowball_machine_hidden", x + 1, y, 0));
                items.add(new SnowWarItem("snowball_machine_hidden", x + 2, y, 0));
            }

            List<SnowWarSpawnCluster> spawnClusters = new ArrayList<>();
            String spawnsContent = readContent("arena_" + mapId + "_spawn_clusters.dat");
            for (String cluster : spawnsContent.split("\\|")) {
                String[] parts = cluster.trim().split("\\s+");
                if (parts.length < 4) {
                    continue;
                }

                spawnClusters.add(new SnowWarSpawnCluster(
                        Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[3])));
            }

            LOGGER.info("Loaded SnowWar map {} ({} items, {} machines, {} spawn clusters).",
                    mapId, items.size(), machinePositions.size(), spawnClusters.size());

            return new SnowWarMap(mapId, heightmapRows, items, machinePositions, spawnClusters);
        } catch (Exception e) {
            LOGGER.error("Failed to load SnowWar map " + mapId + ".", e);
            return null;
        }
    }

    private static List<String> readLines(String fileName) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String line : readContent(fileName).split("\\r\\n|\\r|\\n")) {
            if (!line.trim().isEmpty()) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static String readContent(String fileName) throws IOException {
        Path override = Paths.get(OVERRIDE_DIR + fileName);
        if (Files.exists(override)) {
            return new String(Files.readAllBytes(override), StandardCharsets.UTF_8);
        }

        try (InputStream stream = SnowWarMapsManager.class.getResourceAsStream(RESOURCE_DIR + fileName)) {
            if (stream == null) {
                throw new IOException("SnowWar map resource not found: " + RESOURCE_DIR + fileName);
            }

            StringBuilder builder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                char[] buffer = new char[4096];
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    builder.append(buffer, 0, read);
                }
            }
            return builder.toString();
        }
    }
}
