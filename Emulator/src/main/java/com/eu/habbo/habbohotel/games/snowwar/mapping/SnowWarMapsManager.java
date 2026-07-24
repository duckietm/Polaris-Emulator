package com.eu.habbo.habbohotel.games.snowwar.mapping;

import com.eu.habbo.Emulator;
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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads and caches SnowWar arenas (README 5.3).
 *
 * The primary source is the room_models table: the row named by
 * gamecenter.snowwar.map.model.&lt;mapId&gt; (default snowstorm_arena_&lt;mapId&gt;)
 * provides the heightmap column plus the public_items column, which stores
 * one entry per line:
 *
 *   &lt;classname&gt; &lt;x&gt; &lt;y&gt; &lt;rotation&gt; [walkableHeight collisionHeight]
 *   snowball_machine &lt;x&gt; &lt;y&gt;
 *   spawn &lt;x&gt; &lt;y&gt; &lt;width&gt; &lt;height&gt;
 *
 * The arena editor (:snowwarsave) rewrites the item lines. When the model
 * row is missing, the bundled classpath resources under /snowwar/ are used
 * (a file with the same name under tools/snowwar_maps/ takes precedence),
 * and missing machine/spawn sections in the DB also fall back to those
 * files so a freshly-edited arena keeps working.
 */
public final class SnowWarMapsManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(SnowWarMapsManager.class);

    private static final String RESOURCE_DIR = "/snowwar/";
    private static final String OVERRIDE_DIR = "tools/snowwar_maps/";

    private static final ConcurrentHashMap<Integer, SnowWarMap> MAPS = new ConcurrentHashMap<>();

    private SnowWarMapsManager() {}

    public static SnowWarMap getMap(int mapId) {
        return MAPS.computeIfAbsent(mapId, SnowWarMapsManager::parseMap);
    }

    /**
     * Drops the cached arena so the next game re-reads room_models. Called
     * after :snowwarsave persists an edited layout.
     */
    public static void invalidate(int mapId) {
        MAPS.remove(mapId);
    }

    public static String getModelName(int mapId) {
        return Emulator.getConfig().getValue("gamecenter.snowwar.map.model." + mapId, "snowstorm_arena_" + mapId);
    }

    private static SnowWarMap parseMap(int mapId) {
        SnowWarMap databaseMap = parseDatabaseMap(mapId);
        if (databaseMap != null) {
            return databaseMap;
        }

        return parseFileMap(mapId);
    }

    private static SnowWarMap parseDatabaseMap(int mapId) {
        String modelName = getModelName(mapId);

        String heightmap = null;
        String publicItems = null;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement =
                 connection.prepareStatement("SELECT heightmap, public_items FROM room_models WHERE name = ?")) {
            statement.setString(1, modelName);
            try (ResultSet set = statement.executeQuery()) {
                if (!set.next()) {
                    LOGGER.info("SnowWar map {}: no room_models row '{}', using bundled files.", mapId, modelName);
                    return null;
                }
                heightmap = set.getString("heightmap");
                publicItems = set.getString("public_items");
            }
        } catch (SQLException e) {
            LOGGER.error("SnowWar map " + mapId + ": failed to read room_models, using bundled files.", e);
            return null;
        }

        if (heightmap == null || heightmap.trim().isEmpty()) {
            LOGGER.error(
                "SnowWar map {}: room_models row '{}' has an empty heightmap, using bundled files.",
                mapId,
                modelName);
            return null;
        }

        List<String> heightmapRows = new ArrayList<>();
        for (String row : heightmap.split("\\r\\n|\\r|\\n")) {
            if (!row.trim().isEmpty()) {
                heightmapRows.add(row.trim());
            }
        }

        List<SnowWarItem> items = new ArrayList<>();
        List<SnowWarPoint> machinePositions = new ArrayList<>();
        List<SnowWarSpawnCluster> spawnClusters = new ArrayList<>();

        if (publicItems != null) {
            for (String line : publicItems.split("\\r\\n|\\r|\\n")) {
                String[] parts = line.trim().split("\\s+");

                if (parts.length >= 5 && parts[0].equalsIgnoreCase("spawn")) {
                    spawnClusters.add(new SnowWarSpawnCluster(
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[3]),
                        Integer.parseInt(parts[4])));
                    continue;
                }

                if (parts.length >= 3 && parts[0].equals("snowball_machine")) {
                    addMachine(items, machinePositions, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                    continue;
                }

                if (parts.length < 4) {
                    continue;
                }

                String name = parts[0];
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int rotation = Integer.parseInt(parts[3]);

                if (parts.length >= 6) {
                    // Editor-saved hotel furniture carries explicit heights and,
                    // for room-ad furni, a trailing image URL (7th token) plus an
                    // optional vertical offset for the backdrop (8th token).
                    String imageUrl = parts.length >= 7 ? parts[6] : "";
                    int offsetZ = parts.length >= 8 ? parseIntSafe(parts[7]) : 0;
                    items.add(new SnowWarItem(
                        name,
                        x,
                        y,
                        rotation,
                        Integer.parseInt(parts[4]),
                        Integer.parseInt(parts[5]),
                        imageUrl,
                        offsetZ));
                } else if (SnowWarItemProperties.isKnownItem(name)) {
                    items.add(new SnowWarItem(name, x, y, rotation));
                } else {
                    // Unknown classname without explicit heights: treat as a
                    // solid tree-sized obstacle rather than dropping it.
                    items.add(new SnowWarItem(name, x, y, rotation, 3, 4600));
                }
            }
        }

        // Machines and spawn clusters are required for a playable arena; if
        // the edited layout doesn't define them, keep the bundled defaults.
        try {
            if (machinePositions.isEmpty()) {
                for (String line : readLines("arena_" + mapId + "_snowmachines.dat")) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length < 2) {
                        continue;
                    }
                    addMachine(items, machinePositions, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                }
            }

            if (spawnClusters.isEmpty()) {
                for (String cluster :
                    readContent("arena_" + mapId + "_spawn_clusters.dat").split("\\|")) {
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
            }
        } catch (IOException e) {
            LOGGER.warn("SnowWar map {}: no bundled machine/spawn fallback available.", mapId);
        }

        // Spawn clusters are optional since spawns are picked as random
        // spread-out walkable tiles; machines remain required for ammo.
        if (machinePositions.isEmpty()) {
            LOGGER.error("SnowWar map {}: room_models row '{}' is missing snowball machines.", mapId, modelName);
            return null;
        }

        applyItemSizes(items);

        LOGGER.info(
            "Loaded SnowWar map {} from room_models '{}' ({} items, {} machines, {} spawn clusters).",
            mapId,
            modelName,
            items.size(),
            machinePositions.size(),
            spawnClusters.size());

        return new SnowWarMap(mapId, heightmapRows, items, machinePositions, spawnClusters);
    }

    /**
     * Stamps the furni footprint (tile width/length) onto every non-hidden item
     * from its base furnidata, so the arena blocks the whole footprint and the
     * client can depth-sort multi-tile props by their front tile. Unknown
     * classnames (built-in SnowWar props, machine tiles) keep the 1x1 default.
     */
    private static void applyItemSizes(List<SnowWarItem> items) {
        for (SnowWarItem item : items) {
            if (item.isHidden()) {
                continue;
            }
            try {
                com.eu.habbo.habbohotel.items.Item base =
                    Emulator.getGameEnvironment().getItemManager().getItem(item.getName());
                if (base != null) {
                    item.setSize(base.getWidth(), base.getLength());
                }
                LOGGER.info(
                    "SnowWar item '{}' at ({},{}) rot {} -> size {}x{} (base found: {}, walkableHeight {})",
                    item.getName(),
                    item.getX(),
                    item.getY(),
                    item.getRotation(),
                    item.getWidth(),
                    item.getLength(),
                    base != null,
                    item.getWalkableHeight());
            } catch (Exception e) {
                // Item manager not ready or classname unknown: leave the 1x1
                // default rather than failing the whole arena load.
                LOGGER.warn("SnowWar item '{}' size lookup failed, keeping 1x1.", item.getName(), e);
            }
        }
    }

    private static int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void addMachine(List<SnowWarItem> items, List<SnowWarPoint> machinePositions, int x, int y) {
        machinePositions.add(new SnowWarPoint(x, y));

        // Main machine tile + hidden collision tiles (README 5.3).
        items.add(new SnowWarItem("snowball_machine", x, y, 0));
        items.add(new SnowWarItem("snowball_machine_hidden", x + 1, y, 0));
        items.add(new SnowWarItem("snowball_machine_hidden", x + 2, y, 0));
    }

    private static SnowWarMap parseFileMap(int mapId) {
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

                items.add(new SnowWarItem(
                    name, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3])));
            }

            List<SnowWarPoint> machinePositions = new ArrayList<>();
            for (String line : readLines("arena_" + mapId + "_snowmachines.dat")) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 2) {
                    continue;
                }

                addMachine(items, machinePositions, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
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

            applyItemSizes(items);

            LOGGER.info(
                "Loaded SnowWar map {} ({} items, {} machines, {} spawn clusters).",
                mapId,
                items.size(),
                machinePositions.size(),
                spawnClusters.size());

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
