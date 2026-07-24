package com.eu.habbo.messages.incoming.snowwar;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.games.snowwar.SnowWarManager;
import com.eu.habbo.habbohotel.games.snowwar.mapping.SnowWarItemProperties;
import com.eu.habbo.habbohotel.games.snowwar.mapping.SnowWarMapsManager;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import java.sql.Connection;
import java.sql.PreparedStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Header 6011: a permitted user (acc_snowwar_edit, rank 7 by default)
 * publishes an arena layout designed in the in-game WYSIWYG editor. The
 * item/spawn lists are written straight into room_models.public_items in the
 * same line format SnowWarMapsManager parses, so the next game plays the new
 * arena. The floor plan (heightmap) is untouched - the editor only moves
 * furniture and markers.
 *
 * Payload (see SnowWarSaveEditorComposer):
 *   int    mapId
 *   int    itemCount
 *   repeat { string name, int x, int y, int rotation, string imageUrl, int offsetZ }
 *   int    spawnCount
 *   repeat { int x, int y }
 *   int    heightmapRowCount
 *   repeat { string row }
 */
public class SnowStormSaveEditorEvent extends MessageHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SnowStormSaveEditorEvent.class);

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null || !habbo.hasPermission(SnowWarManager.EDIT_PERMISSION)) {
            return;
        }

        int mapId = this.packet.readInt();
        int itemCount = this.packet.readInt();

        StringBuilder builder = new StringBuilder();
        int adImages = 0;

        for (int i = 0; i < itemCount; i++) {
            String name = this.packet.readString();
            int x = this.packet.readInt();
            int y = this.packet.readInt();
            int rotation = this.packet.readInt();
            String imageUrl = this.packet.readString();
            int offsetZ = this.packet.readInt();
            boolean hasImage = imageUrl != null && !imageUrl.trim().isEmpty();

            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            name = name.trim();

            // Machines are stored in the short "snowball_machine x y" form so
            // SnowWarMapsManager re-expands them (main tile + hidden collision
            // tiles + ammo point).
            if (name.equals("snowball_machine")) {
                builder.append("snowball_machine ")
                        .append(x)
                        .append(' ')
                        .append(y)
                        .append("\r\n");
                continue;
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
                Item base = Emulator.getGameEnvironment().getItemManager().getItem(name);
                if (base != null) {
                    boolean walkable = base.allowWalk() || base.allowSit();
                    walkableHeight = walkable ? 0 : 3;
                    collisionHeight = Math.max(1150, (int) Math.round(base.getHeight() * 2300));
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
                builder.append(' ').append(imageUrl.trim()).append(' ').append(offsetZ);
                adImages++;
            }

            builder.append("\r\n");
        }

        int spawnCount = this.packet.readInt();
        for (int i = 0; i < spawnCount; i++) {
            int x = this.packet.readInt();
            int y = this.packet.readInt();
            builder.append("spawn ").append(x).append(' ').append(y).append(" 1 1\r\n");
        }

        // Floor plan (heightmap): the editor can reshape the arena, so persist
        // the sent grid too. 'x'/'X' cells are void, anything else is walkable.
        int rowCount = this.packet.readInt();
        StringBuilder heightmap = new StringBuilder();
        for (int i = 0; i < rowCount; i++) {
            String row = this.packet.readString();
            if (row == null) {
                row = "";
            }
            if (heightmap.length() > 0) {
                heightmap.append("\r\n");
            }
            heightmap.append(row);
        }

        String modelName = SnowWarMapsManager.getModelName(mapId);

        // Keep the existing floor plan if the editor sent none (older client).
        boolean writeHeightmap = rowCount > 0 && heightmap.length() > 0;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
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
            statement.executeUpdate();
        }

        SnowWarMapsManager.invalidate(mapId);

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
}
