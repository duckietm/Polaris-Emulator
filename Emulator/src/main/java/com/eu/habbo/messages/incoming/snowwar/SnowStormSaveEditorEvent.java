package com.eu.habbo.messages.incoming.snowwar;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.games.snowwar.SnowWarConstants;
import com.eu.habbo.habbohotel.games.snowwar.SnowWarManager;
import com.eu.habbo.habbohotel.games.snowwar.mapping.SnowWarItemProperties;
import com.eu.habbo.habbohotel.games.snowwar.mapping.SnowWarMapsManager;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Header 6011: a permitted user (acc_snowwar_arena_build, rank 7 by default)
 * publishes a custom arena designed in the in-game WYSIWYG editor. The
 * item/spawn lists are written straight into room_models.public_items in the
 * same line format SnowWarMapsManager parses, so the next game plays the new
 * arena. The floor plan is persisted with the furniture and markers.
 *
 * Payload (see SnowWarSaveEditorComposer):
 *   int    mapId
 *   int    itemCount
 *   repeat { string name, int x, int y, int rotation, string imageUrl, int offsetZ, int state }
 *   int    spawnCount
 *   repeat { int x, int y }
 *   int    heightmapRowCount
 *   repeat { string row }
 *   string arenaName
 */
public class SnowStormSaveEditorEvent extends MessageHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SnowStormSaveEditorEvent.class);
    // Nitro's canonical floor-plan encoding: x is void, followed by playable
    // heights 0..q. Keep this protocol boundary explicit so malformed cells
    // cannot be persisted into room_models.heightmap.
    private static final String FLOOR_HEIGHT_SCHEME = "x0123456789abcdefghijklmnopq";

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null || !habbo.hasPermission(SnowWarManager.BUILD_PERMISSION)) {
            return;
        }

        int mapId = this.packet.readInt();
        if (!SnowWarManager.getInstance()
                .canSaveEditorArena(habbo.getHabboInfo().getId(), mapId)) {
            return;
        }
        int itemCount = this.packet.readInt();
        // Reject an implausible item count outright (each packet is
        // self-contained, so leftover bytes are discarded): keeps a malformed
        // or malicious save from bloating the arena definition.
        if (itemCount < 0 || itemCount > SnowWarConstants.EDITOR_MAX_ITEMS) {
            return;
        }

        var gameEnvironment = Emulator.getGameEnvironment();
        StringBuilder builder = new StringBuilder();
        List<int[]> itemPositions = new ArrayList<>();
        List<int[]> spawnPositions = new ArrayList<>();
        int adImages = 0;
        int machineCount = 0;

        for (int i = 0; i < itemCount; i++) {
            String name = this.packet.readString();
            int x = this.packet.readInt();
            int y = this.packet.readInt();
            int rotation = this.packet.readInt();
            String imageUrl = this.packet.readString();
            int offsetZ = this.packet.readInt();
            int state = Math.max(0, this.packet.readInt());

            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            name = name.trim();
            // The item lines are space/CRLF-delimited and re-parsed on load, so
            // a name or ad-image URL containing whitespace/newlines could inject
            // extra tokens or whole lines. Reject the whole save instead of
            // silently changing a tampered item's meaning.
            if (containsWhitespace(name)
                    || name.length() > SnowWarConstants.EDITOR_MAX_ITEM_NAME_LENGTH
                    || !isEditorCoordinateInRange(x, y)) {
                return;
            }
            itemPositions.add(new int[] {x, y});
            String normalizedImageUrl = imageUrl == null ? "" : imageUrl.trim();
            if (normalizedImageUrl.length() > SnowWarConstants.EDITOR_MAX_IMAGE_URL_LENGTH
                    || (!normalizedImageUrl.isEmpty() && containsWhitespace(normalizedImageUrl))) {
                return;
            }
            boolean hasImage = !normalizedImageUrl.isEmpty();

            // Machines are stored in the short "snowball_machine x y" form so
            // SnowWarMapsManager re-expands them (main tile + hidden collision
            // tiles + ammo point).
            if (SnowWarMapsManager.isMachineName(name)) {
                builder.append(name).append(' ').append(x).append(' ').append(y).append("\r\n");
                machineCount++;
                continue;
            }
            if (!isValidRotation(rotation)) {
                return;
            }

            int walkableHeight;
            int collisionHeight;
            if (hasImage) {
                // Room-ad backdrop furni: walkable, minimal collision.
                walkableHeight = 0;
                collisionHeight = 1150;
            } else if (SnowWarItemProperties.isKnownItem(name)) {
                walkableHeight = SnowWarItemProperties.getWalkableHeight(name);
                collisionHeight = SnowWarItemProperties.getCollisionHeight(name);
            } else {
                // Hotel furni placed like in a normal room: derive collision from
                // the real base item so walkable furni (rugs, tiles) stay walkable
                // and solid furni block, matching how it behaves in game.
                Item base = gameEnvironment.getItemManager().getItem(name);
                if (base != null) {
                    boolean walkable = base.allowWalk() || base.allowSit();
                    walkableHeight = walkable ? 0 : 3;
                    collisionHeight = Math.max(1150, (int) Math.round(base.getHeight() * 2300));
                    // Clamp the saved state to the furni's real state range
                    // (items_base.interaction_modes_count) so a tampered client
                    // can't persist a state past the last one.
                    state = Math.min(state, Math.max(0, base.getStateCount() - 1));
                } else {
                    // Truly unknown classname: treat as a solid tree-sized obstacle.
                    walkableHeight = 3;
                    collisionHeight = 4600;
                }
            }

            builder.append(name)
                    .append(' ')
                    .append(x)
                    .append(' ')
                    .append(y)
                    .append(' ')
                    .append(rotation)
                    .append(' ')
                    .append(walkableHeight)
                    .append(' ')
                    .append(collisionHeight);

            if (hasImage) {
                builder.append(' ').append(normalizedImageUrl).append(' ').append(offsetZ);
                adImages++;
            }

            // Multistate index is the last ordinary token:
            // "... [imageUrl offsetZ] state". Preserve AIR fixture altitude for
            // normal furni as an explicit z= token; older editor saves omit it.
            // A room-ad line therefore keeps its non-numeric URL at token 7, so
            // the loader can tell an ad apart from a normal furni's state token.
            builder.append(' ').append(state);
            if (!hasImage && offsetZ != 0) {
                builder.append(" z=").append(offsetZ);
            }

            builder.append("\r\n");
        }

        int spawnCount = this.packet.readInt();
        if (spawnCount < 0 || spawnCount > SnowWarConstants.EDITOR_MAX_SPAWNS) {
            return;
        }
        for (int i = 0; i < spawnCount; i++) {
            int x = this.packet.readInt();
            int y = this.packet.readInt();
            if (!isEditorCoordinateInRange(x, y)) {
                return;
            }
            spawnPositions.add(new int[] {x, y});
            builder.append("spawn ").append(x).append(' ').append(y).append(" 1 1\r\n");
        }

        // Floor plan (heightmap): persist the real Nitro editor's rectangular
        // x/0..q grid. SnowStorm treats every non-void height as playable floor.
        int rowCount = this.packet.readInt();
        if (rowCount < 0 || rowCount > SnowWarConstants.EDITOR_MAX_ROWS) {
            return;
        }
        List<String> sentHeightmapRows = new ArrayList<>(rowCount);
        for (int i = 0; i < rowCount; i++) {
            String row = this.packet.readString();
            if (row == null) {
                row = "";
            }
            // Strip CR/LF so a single row can't inject extra heightmap rows.
            sentHeightmapRows.add(row.replace("\r", "").replace("\n", ""));
        }
        List<String> heightmapRows = normalizeHeightmapRows(sentHeightmapRows);
        if (heightmapRows == null) {
            return;
        }
        if (!heightmapRows.isEmpty()) {
            for (int[] position : itemPositions) {
                if (!isEditorPositionValid(heightmapRows, position[0], position[1], false)) {
                    return;
                }
            }
            for (int[] position : spawnPositions) {
                if (!isEditorPositionValid(heightmapRows, position[0], position[1], true)) {
                    return;
                }
            }
        }
        String heightmap = String.join("\r\n", heightmapRows);

        String arenaName =
                normalizeArenaName(this.packet.bytesAvailable() > 0 ? this.packet.readString() : null, mapId);
        arenaName = gameEnvironment.getWordFilter().filter(arenaName, habbo);
        arenaName = normalizeArenaName(arenaName, mapId);

        String modelName = SnowWarMapsManager.getModelName(mapId);

        // Keep the existing floor plan if the editor sent none (older client).
        boolean writeHeightmap = !heightmapRows.isEmpty();

        int updated;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            // The editor packet contains only the visual objects it can edit.
            // Preserve the existing machine and spawn records when the client
            // sends none, otherwise a harmless furniture edit silently turns
            // an official arena into the random fallback layout.
            if (machineCount == 0 || spawnCount == 0) {
                try (PreparedStatement select =
                        connection.prepareStatement("SELECT public_items FROM room_models WHERE name = ?")) {
                    select.setString(1, modelName);
                    try (ResultSet result = select.executeQuery()) {
                        if (result.next()) {
                            appendPreservedGameplayLines(
                                    builder, result.getString("public_items"), machineCount == 0, spawnCount == 0);
                        }
                    }
                }
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    writeHeightmap
                            ? "UPDATE room_models SET public_items = ?, heightmap = ? WHERE name = ?"
                            : "UPDATE room_models SET public_items = ? WHERE name = ?")) {
                statement.setString(1, builder.toString());
                if (writeHeightmap) {
                    statement.setString(2, heightmap.toString());
                    statement.setString(3, modelName);
                } else {
                    statement.setString(2, modelName);
                }
                updated = statement.executeUpdate();
            }
        }

        if (updated != 1
                || !SnowWarManager.getInstance()
                        .publishArena(mapId, habbo.getHabboInfo().getId(), arenaName)) {
            return;
        }

        SnowWarMapsManager.invalidate(mapId);
        SnowWarManager.getInstance().onArenaPublished();

        LOGGER.info(
                "SnowWar arena {} ('{}') saved from the in-game editor by {} ({} items, {} spawns, {} ad images, {} floor rows).",
                mapId,
                modelName,
                habbo.getHabboInfo().getUsername(),
                itemCount,
                spawnCount,
                adImages,
                rowCount);
    }

    static String normalizeArenaName(String value, int mapId) {
        String name =
                value == null ? "" : value.replaceAll("\\p{Cntrl}", " ").trim().replaceAll("\\s+", " ");
        if (name.isEmpty()) {
            return "Custom Arena " + mapId;
        }
        return name.substring(0, Math.min(name.length(), 64));
    }

    static List<String> normalizeHeightmapRows(List<String> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        if (rows.size() > SnowWarConstants.EDITOR_MAX_ROWS) {
            return null;
        }

        List<String> normalized = new ArrayList<>(rows.size());
        int width = -1;
        int walkableTiles = 0;

        for (String value : rows) {
            if (value == null) {
                return null;
            }
            String row = value.replace("\r", "").replace("\n", "").toLowerCase();
            if (width < 0) {
                width = row.length();
            }
            if (row.isEmpty() || row.length() != width || row.length() > SnowWarConstants.EDITOR_MAX_COLUMNS) {
                return null;
            }
            for (int i = 0; i < row.length(); i++) {
                char cell = row.charAt(i);
                int heightIndex = FLOOR_HEIGHT_SCHEME.indexOf(cell);
                if (heightIndex > 0) {
                    walkableTiles++;
                } else if (heightIndex < 0) {
                    return null;
                }
            }
            normalized.add(row);
        }

        return walkableTiles >= SnowWarConstants.EDITOR_MIN_WALKABLE_TILES ? List.copyOf(normalized) : null;
    }

    static boolean isEditorPositionValid(List<String> heightmapRows, int x, int y, boolean requireWalkable) {
        if (heightmapRows == null
                || y < 0
                || y >= heightmapRows.size()
                || x < 0
                || x >= heightmapRows.get(y).length()) {
            return false;
        }
        return !requireWalkable
                || FLOOR_HEIGHT_SCHEME.indexOf(heightmapRows.get(y).charAt(x)) > 0;
    }

    private static boolean containsWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isEditorCoordinateInRange(int x, int y) {
        return x >= 0 && y >= 0 && x < SnowWarConstants.EDITOR_MAX_COLUMNS && y < SnowWarConstants.EDITOR_MAX_ROWS;
    }

    private static boolean isValidRotation(int rotation) {
        return rotation == 0 || rotation == 2 || rotation == 4 || rotation == 6;
    }

    static void appendPreservedGameplayLines(
            StringBuilder builder, String existingItems, boolean preserveMachines, boolean preserveSpawns) {
        if (existingItems == null || existingItems.isBlank()) {
            return;
        }

        for (String line : existingItems.split("\\r\\n|\\r|\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String name = trimmed.split("\\s+", 2)[0];
            if ((preserveMachines && SnowWarMapsManager.isMachineName(name))
                    || (preserveSpawns && name.equalsIgnoreCase("spawn"))) {
                builder.append(trimmed).append("\r\n");
            }
        }
    }
}
