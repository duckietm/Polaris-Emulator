package com.eu.habbo.habbohotel.catalog.layouts;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.catalog.CatalogItem;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.WebApiBoxSql;
import com.eu.habbo.habbohotel.permissions.RankLimits;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.navigator.CanCreateRoomComposer;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoomBundleLayout extends SingleBundle {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomBundleLayout.class);

    public int roomId;
    public Room room;
    private int lastUpdate = 0;
    private boolean loaded = false;

    public RoomBundleLayout(ResultSet set) throws SQLException {
        super(set);

        this.roomId = set.getInt("room_id");
    }

    @Override
    public void serialize(ServerMessage message) {
        message.appendString("room_bundle");
        message.appendInt(3);
        message.appendString(this.getHeaderImage());
        message.appendString(this.getTeaserImage());
        message.appendString("");
        message.appendInt(4);
        message.appendString(this.getTextOne());
        message.appendString(this.getTextDetails());
        message.appendString(this.getTextTeaser());
        message.appendString(this.getTextTwo());
    }

    @Override
    protected boolean refreshesItemsOnRead() {
        return true;
    }

    @Override
    public Int2ObjectMap<CatalogItem> getCatalogItems() {
        if (Emulator.getIntUnixTimestamp() - this.lastUpdate < 120) {
            this.lastUpdate = Emulator.getIntUnixTimestamp();
            return super.getCatalogItems();
        }

        if (this.room == null) {
            RoomManager roomManager = Emulator.getGameEnvironment().getRoomManager();
            if (this.roomId > 0 && roomManager != null) {
                this.room = roomManager.loadRoom(this.roomId);

                if (this.room != null) this.room.preventUnloading = true;
            } else if (this.roomId <= 0) {
                LOGGER.error("No room id specified for room bundle {}({})", this.getPageName(), this.getId());
            }
        }

        if (this.room == null) {
            return super.getCatalogItems();
        }

        final CatalogItem[] item = {null};

        for (CatalogItem object : super.getCatalogItems().values()) {
            if (object != null) {
                item[0] = object;
                break;
            }
        }

        if (this.room.isPreLoaded()) {
            this.room.loadData();
            this.room.preventUncaching = true;
            this.room.preventUnloading = true;
        }
        if (item[0] != null) {
            item[0].getBundle().clear();

            Map<Item, Integer> items = new HashMap<>();

            for (HabboItem i : this.room.getFloorItems()) {
                if (!items.containsKey(i.getBaseItem())) {
                    items.put(i.getBaseItem(), 0);
                }

                items.put(i.getBaseItem(), items.get(i.getBaseItem()) + 1);
            }

            for (HabboItem i : this.room.getWallItems()) {
                if (!items.containsKey(i.getBaseItem())) {
                    items.put(i.getBaseItem(), 0);
                }

                items.put(i.getBaseItem(), items.get(i.getBaseItem()) + 1);
            }

            if (!item[0].getExtradata().isEmpty()) {
                items.put(
                        Emulator.getGameEnvironment()
                                .getItemManager()
                                .getItem(Integer.parseInt(item[0].getExtradata())),
                        1);
            }

            StringBuilder data = new StringBuilder();

            for (Map.Entry<Item, Integer> set : items.entrySet()) {
                data.append(set.getKey().getId())
                        .append(":")
                        .append(set.getValue())
                        .append(";");
            }

            item[0].setItemId(data.toString());
            item[0].loadBundle();
        }

        return super.getCatalogItems();
    }

    public void loadItems(Room room) {
        if (this.room != null) {
            this.room.preventUnloading = false;
        }

        this.room = room;
        this.room.preventUnloading = true;
        this.getCatalogItems();
        this.loaded = true;
    }

    public void buyRoom(Habbo habbo) {
        this.tryBuyRoom(habbo);
    }

    public void buyRoom(Habbo habbo, int userId, String userName) {
        this.tryBuyRoom(habbo, userId, userName);
    }

    /** {@link #tryBuyRoom(Habbo, int, String)} for the buyer's own account. */
    public boolean tryBuyRoom(Habbo habbo) {
        return this.tryBuyRoom(habbo, habbo.getHabboInfo().getId(), habbo.getHabboInfo().getUsername());
    }

    /**
     * Copies the bundle's room to the user and says whether the copy exists. The room, its furni, its custom model and
     * its bots are written in one transaction, so a database error leaves nothing behind; before, each statement was
     * committed on its own and every error was only logged, so the buyer paid for a room that was missing or empty.
     */
    public boolean tryBuyRoom(Habbo habbo, int userId, String userName) {
        if (!this.loaded) {
            this.loadItems(Emulator.getGameEnvironment().getRoomManager().loadRoom(this.roomId));
        }

        if (habbo != null) {
            int count = Emulator.getGameEnvironment()
                    .getRoomManager()
                    .getRoomsForHabbo(habbo)
                    .size();
            int max = RankLimits.rooms(habbo);

            if (count >= max) {
                habbo.getClient().sendResponse(new CanCreateRoomComposer(count, max));
                return false;
            }
        }

        if (this.room == null) return false;

        this.room.save();

        for (HabboItem item : this.room.getFloorItems()) {
            item.run();
        }

        for (HabboItem item : this.room.getWallItems()) {
            item.run();
        }

        this.getCatalogItems();
        int roomId = 0;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                roomId = this.copyRoom(connection, userId, userName);
                if (roomId == 0) {
                    connection.rollback();
                    return false;
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            LOGGER.error("Room bundle {} could not be copied for user {}", this.getId(), userId, e);
            return false;
        }

        // The copy is committed. A room that does not load now keeps its rows and loads on the next visit; only the
        // wall and floor settings below are skipped. Before, a null room here threw and the buyer was refunded for a
        // room that existed.
        Room r = Emulator.getGameEnvironment().getRoomManager().loadRoom(roomId);
        if (r != null) {
            r.setWallHeight(this.room.getWallHeight());
            r.setFloorSize(this.room.getFloorSize());
            r.setWallPaint(this.room.getWallPaint());
            r.setFloorPaint(this.room.getFloorPaint());
            r.setScore(0);
            r.setNeedsUpdate(true);
        }

        if (habbo != null && r != null) {
            Map<String, String> keys = new HashMap<>();
            keys.put("ROOMNAME", r.getName());
            keys.put("ROOMID", r.getId() + "");
            keys.put("OWNER", r.getOwnerName());
            keys.put("image", "${image.library.url}/notifications/room_bundle_" + this.getId() + ".png");
            habbo.getClient().sendResponse(new BubbleAlertComposer(BubbleAlertKeys.PURCHASING_ROOM.key, keys));
        }
        return true;
    }

    /** Writes the copy on {@code connection} and returns the new room id, or 0 when the room row was not created. */
    private int copyRoom(Connection connection, int userId, String userName) throws SQLException {
        int roomId = 0;
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO rooms (owner_id, owner_name, name, description, model, password, state, users_max, category, paper_floor, paper_wall, paper_landscape, thickness_wall, thickness_floor, moodlight_data, override_model)  (SELECT ?, ?, name, description, model, password, state, users_max, category, paper_floor, paper_wall, paper_landscape, thickness_wall, thickness_floor, moodlight_data, override_model FROM rooms WHERE id = ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                statement.setInt(1, userId);
                statement.setString(2, userName);
                statement.setInt(3, this.room.getId());
                statement.execute();
                try (ResultSet set = statement.getGeneratedKeys()) {
                    if (set.next()) {
                        roomId = set.getInt(1);
                    }
                }
            }

            if (roomId == 0) return 0;

            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO items (user_id, room_id, item_id, wall_pos, x, y, z, rot, extra_data, wired_data, limited_data, guild_id, allow_underpass) (SELECT ?, ?, item_id, wall_pos, x, y, z, rot, extra_data, wired_data, ?, ?, allow_underpass FROM items WHERE room_id = ?"
                            + " AND item_id NOT IN (" + WebApiBoxSql.BASE_ITEM_IDS + "))",
                    Statement.RETURN_GENERATED_KEYS)) {
                statement.setInt(1, userId);
                statement.setInt(2, roomId);
                statement.setString(3, "0:0");
                statement.setInt(4, 0);
                statement.setInt(5, this.room.getId());
                statement.execute();
            }

            if (this.room.hasCustomLayout()) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO room_models_custom (id, name, door_x, door_y, door_dir, heightmap) (SELECT ?, ?, door_x, door_y, door_dir, heightmap FROM room_models_custom WHERE id = ? LIMIT 1)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    statement.setInt(1, roomId);
                    statement.setString(2, "custom_" + roomId);
                    statement.setInt(3, this.room.getId());
                    statement.execute();
                }
            }

            if (Emulator.getConfig().getBoolean("bundle.bots.enabled")) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO bots (user_id, room_id, name, motto, figure, gender, x, y, z, chat_lines, chat_auto, chat_random, chat_delay, dance, type) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    synchronized (this.room.getCurrentBots()) {
                        statement.setInt(1, userId);
                        statement.setInt(2, roomId);
                        for (Bot bot : this.room.getCurrentBots().values()) {
                            statement.setString(3, bot.getName());
                            statement.setString(4, bot.getMotto());
                            statement.setString(5, bot.getFigure());
                            statement.setString(6, bot.getGender().name());
                            statement.setInt(7, bot.getRoomUnit().getX());
                            statement.setInt(8, bot.getRoomUnit().getY());
                            statement.setDouble(9, bot.getRoomUnit().getZ());
                            StringBuilder text = new StringBuilder();
                            for (String s : bot.getChatLines()) {
                                text.append(s).append("\r");
                            }
                            statement.setString(10, text.toString());
                            statement.setString(11, bot.isChatAuto() ? "1" : "0");
                            statement.setString(12, bot.isChatRandom() ? "1" : "0");
                            statement.setInt(13, bot.getChatDelay());
                            statement.setInt(
                                    14, bot.getRoomUnit().getDanceType().getType());
                            statement.setString(15, bot.getType());
                            statement.addBatch();
                        }
                    }
                    statement.executeBatch();
                }
            }
        return roomId;
    }
}
