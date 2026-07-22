package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.games.snowwar.mapping.SnowWarMapsManager;
import com.eu.habbo.habbohotel.items.interactions.InteractionCustomValues;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.HabboItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * :snowwarsave - publishes the furniture of the SnowWar editor room into
 * room_models.public_items, the same column the original Habbo used for
 * public room furniture. The room must use the SnowWar room model. Machine
 * (snowball_machine) and spawn lines already stored in the column are
 * preserved; only the furniture lines are replaced.
 *
 * Saved line format (parsed back by SnowWarMapsManager):
 *   classname x y rotation walkableHeight collisionHeight
 */
public class SnowWarSaveCommand extends Command {

    // One "block" of SnowWar collision height in world units (block_basic).
    private static final int COLLISION_PER_TILE_HEIGHT = 2300;

    public SnowWarSaveCommand() {
        super("cmd_snowwar_save", Emulator.getTexts().getValue("commands.keys.cmd_snowwar_save", "snowwarsave").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        Room room = gameClient.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null) {
            return true;
        }

        // Use the rooms.model column, not the active layout name: once the
        // floor plan editor is used the layout loads as custom_<roomId>, but
        // the room keeps pointing at the SnowWar model.
        int mapId = findMapIdForModel(getRoomModelName(room.getId()));
        if (mapId <= 0) {
            gameClient.getHabbo().whisper(
                    Emulator.getTexts().getValue("commands.error.cmd_snowwar_save.wrong_room",
                            "This room does not use the SnowWar arena model."),
                    RoomChatMessageBubbles.ALERT);
            return true;
        }

        String modelName = SnowWarMapsManager.getModelName(mapId);

        // Keep the machine/spawn lines - they are game infrastructure, not
        // furniture, and are not placeable in the editor room.
        StringBuilder builder = new StringBuilder();
        String existing = readPublicItems(modelName);
        if (existing != null) {
            for (String line : existing.split("\\r\\n|\\r|\\n")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("snowball_machine ") || trimmed.startsWith("spawn ")) {
                    builder.append(trimmed).append("\r\n");
                }
            }
        }

        int saved = 0;
        int adImages = 0;
        for (HabboItem item : room.getFloorItems()) {
            String name = item.getBaseItem().getName();
            boolean walkable = item.getBaseItem().allowWalk() || item.getBaseItem().allowSit();
            int walkableHeight = walkable ? 0 : 3;
            int collisionHeight = Math.max(1150, (int) Math.round(item.getBaseItem().getHeight() * COLLISION_PER_TILE_HEIGHT));

            builder.append(name).append(' ')
                    .append(item.getX()).append(' ')
                    .append(item.getY()).append(' ')
                    .append(item.getRotation()).append(' ')
                    .append(walkableHeight).append(' ')
                    .append(collisionHeight);

            // Room-ad (ads_bg) furni carry an imageUrl in their custom values;
            // save it as a 7th token so the arena can draw the ad. URLs have
            // no whitespace, so they stay a single space-delimited token.
            String imageUrl = extractImageUrl(item);
            if (!imageUrl.isEmpty()) {
                builder.append(' ').append(imageUrl);
                adImages++;
            }

            builder.append("\r\n");
            saved++;
        }

        // Publish the room's ACTIVE floor plan too (custom_<roomId> layouts
        // included), so floor plan editor changes reach the game arena and
        // the editor room and the game always play on the same tiles.
        StringBuilder heightmap = new StringBuilder();
        for (String row : room.getLayout().getHeightmap().split("\\r\\n|\\r|\\n")) {
            if (!row.trim().isEmpty()) {
                if (heightmap.length() > 0) {
                    heightmap.append("\r\n");
                }
                heightmap.append(row.trim());
            }
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE room_models SET heightmap = ?, public_items = ? WHERE name = ?")) {
            statement.setString(1, heightmap.toString());
            statement.setString(2, builder.toString());
            statement.setString(3, modelName);
            if (statement.executeUpdate() == 0) {
                gameClient.getHabbo().whisper(
                        Emulator.getTexts().getValue("commands.error.cmd_snowwar_save.no_model",
                                "The SnowWar room model row is missing in room_models."),
                        RoomChatMessageBubbles.ALERT);
                return true;
            }
        }

        SnowWarMapsManager.invalidate(mapId);

        gameClient.getHabbo().whisper(
                Emulator.getTexts().getValue("commands.success.cmd_snowwar_save",
                        "SnowWar arena saved (%count% items, %ads% ad images). The next game uses the new layout.")
                        .replace("%count%", Integer.toString(saved))
                        .replace("%ads%", Integer.toString(adImages)),
                RoomChatMessageBubbles.ALERT);
        return true;
    }

    /**
     * Room-ad furni store their image behind an "imageUrl" custom value; be
     * lenient about key casing across furnidata variants.
     */
    private String extractImageUrl(HabboItem item) {
        if (!(item instanceof InteractionCustomValues)) {
            return "";
        }

        java.util.Map<String, String> values = ((InteractionCustomValues) item).values;
        for (java.util.Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase("imageUrl")) {
                String value = entry.getValue();
                return (value != null && !value.trim().isEmpty()) ? value.trim() : "";
            }
        }
        return "";
    }

    /**
     * Reverse lookup: which SnowWar map id uses this room model? Map ids are
     * small (1..9); checking the configured name for each avoids storing a
     * second mapping.
     */
    private int findMapIdForModel(String modelName) {
        for (int mapId = 1; mapId <= 9; mapId++) {
            if (SnowWarMapsManager.getModelName(mapId).equalsIgnoreCase(modelName)) {
                return mapId;
            }
        }
        return -1;
    }

    private String getRoomModelName(int roomId) throws SQLException {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT model FROM rooms WHERE id = ?")) {
            statement.setInt(1, roomId);
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    return set.getString("model");
                }
            }
        }
        return "";
    }

    private String readPublicItems(String modelName) throws SQLException {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT public_items FROM room_models WHERE name = ?")) {
            statement.setString(1, modelName);
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    return set.getString("public_items");
                }
            }
        }
        return null;
    }
}
