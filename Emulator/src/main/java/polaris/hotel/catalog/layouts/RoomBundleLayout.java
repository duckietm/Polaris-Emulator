package polaris.hotel.catalog.layouts;

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
import polaris.Emulator;
import polaris.hotel.bots.Bot;
import polaris.hotel.catalog.CatalogItem;
import polaris.hotel.items.Item;
import polaris.hotel.items.WebApiBoxSql;
import polaris.hotel.permissions.RankLimits;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomManager;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.generic.alerts.BubbleAlertComposer;
import polaris.messages.outgoing.generic.alerts.BubbleAlertKeys;
import polaris.messages.outgoing.navigator.CanCreateRoomComposer;

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

            for (UserItem i : this.room.getFloorItems()) {
                if (!items.containsKey(i.getBaseItem())) {
                    items.put(i.getBaseItem(), 0);
                }

                items.put(i.getBaseItem(), items.get(i.getBaseItem()) + 1);
            }

            for (UserItem i : this.room.getWallItems()) {
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

    public void buyRoom(User user) {
        this.buyRoom(user, user.getUserInfo().getId(), user.getUserInfo().getUsername());
    }

    public void buyRoom(User user, int userId, String userName) {
        if (!this.loaded) {
            this.loadItems(Emulator.getGameEnvironment().getRoomManager().loadRoom(this.roomId));
        }

        if (user != null) {
            int count = Emulator.getGameEnvironment()
                    .getRoomManager()
                    .getRoomsForUser(user)
                    .size();
            int max = RankLimits.rooms(user);

            if (count >= max) {
                user.getClient().sendResponse(new CanCreateRoomComposer(count, max));
                return;
            }
        }

        if (this.room == null) return;

        this.room.save();

        for (UserItem item : this.room.getFloorItems()) {
            item.run();
        }

        for (UserItem item : this.room.getWallItems()) {
            item.run();
        }

        this.getCatalogItems();
        int roomId = 0;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
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

            if (roomId == 0) return;

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
                } catch (SQLException e) {
                    LOGGER.error("Caught SQL exception", e);
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
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        Room r = Emulator.getGameEnvironment().getRoomManager().loadRoom(roomId);
        r.setWallHeight(this.room.getWallHeight());
        r.setFloorSize(this.room.getFloorSize());
        r.setWallPaint(this.room.getWallPaint());
        r.setFloorPaint(this.room.getFloorPaint());
        r.setScore(0);
        r.setNeedsUpdate(true);
        Map<String, String> keys = new HashMap<>();
        keys.put("ROOMNAME", r.getName());
        keys.put("ROOMID", r.getId() + "");
        keys.put("OWNER", r.getOwnerName());
        keys.put("image", "${image.library.url}/notifications/room_bundle_" + this.getId() + ".png");

        if (user != null) {
            user.getClient().sendResponse(new BubbleAlertComposer(BubbleAlertKeys.PURCHASING_ROOM.key, keys));
        }
    }
}
