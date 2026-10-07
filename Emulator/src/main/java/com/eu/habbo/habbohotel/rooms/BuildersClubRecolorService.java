package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.database.Database;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.items.AddFloorItemComposer;
import com.eu.habbo.messages.outgoing.rooms.items.RemoveFloorItemComposer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BuildersClubRecolorService {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildersClubRecolorService.class);

    public static final int SCOPE_THIS = 0;

    public static final int SCOPE_SAME_IN_ROOM = 1;

    static final int MAX_ITEMS_PER_REQUEST = 500;
    static final long COOLDOWN_MS = 500L;

    static final long COOLDOWN_PER_ITEM_MS = 10L;

    private static final ConcurrentHashMap<Integer, Long> USER_NEXT = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<Integer, Long> ROOM_NEXT = new ConcurrentHashMap<>();
    private static final int MAX_TRACKED = 10_000;

    public enum Result {
        DONE,
        TOO_FAST,
        NO_RIGHTS,
        NOT_FOUND,
        NOT_RECOLORABLE,
        NOT_BUILDERS_CLUB,
        NO_SUCH_COLOR
    }

    private BuildersClubRecolorService() {}

    public static Result recolor(Habbo habbo, int itemId, int colorIndex, int scope) {
        if (habbo == null || habbo.getHabboInfo() == null) {
            return Result.NO_RIGHTS;
        }
        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null) {
            return Result.NO_RIGHTS;
        }

        int userId = habbo.getHabboInfo().getId();
        long now = System.currentTimeMillis();
        if (!admit(userId, room.getId(), now)) {
            return Result.TOO_FAST;
        }
        if (!room.isLoaded() || !BuildersClubRoomSupport.canPlaceInRoom(habbo, room)) {
            return Result.NO_RIGHTS;
        }

        HabboItem clicked = room.getHabboItem(itemId);
        if (clicked == null
                || clicked.getBaseItem() == null
                || clicked.getBaseItem().getType() != FurnitureType.FLOOR) {
            return Result.NOT_FOUND;
        }

        String family = familyOf(clicked.getBaseItem().getName());
        if (family == null) {
            return Result.NOT_RECOLORABLE;
        }
        if (colorIndex < 0 || clicked.getBaseItem().getName().equalsIgnoreCase(family + "*" + colorIndex)) {
            return Result.NO_SUCH_COLOR;
        }

        GameEnvironment environment = WiredPlatform.gameEnvironment();
        Database database = WiredPlatform.database();
        if (environment == null || database == null) {
            return Result.NOT_FOUND;
        }

        Item target = environment.getItemManager().getItem(family + "*" + colorIndex);
        if (!interchangeable(clicked.getBaseItem(), target)) {
            return Result.NO_SUCH_COLOR;
        }

        try (Connection connection = database.getDataSource().getConnection()) {
            if (!offeredInBuildersClub(connection, target.getId())) {
                return Result.NO_SUCH_COLOR;
            }

            Set<Integer> buildersClubItems = buildersClubItemsIn(connection, room.getId());
            if (!buildersClubItems.contains(clicked.getId())) {
                return Result.NOT_BUILDERS_CLUB;
            }

            List<HabboItem> items = new ArrayList<>();
            if (scope == SCOPE_SAME_IN_ROOM) {
                int sourceBaseId = clicked.getBaseItem().getId();
                for (HabboItem item : room.getFloorItems()) {
                    if (items.size() >= MAX_ITEMS_PER_REQUEST) {
                        break;
                    }
                    if (item != null
                            && item.getBaseItem() != null
                            && item.getBaseItem().getId() == sourceBaseId
                            && buildersClubItems.contains(item.getId())) {
                        items.add(item);
                    }
                }
            } else {
                items.add(clicked);
            }

            List<HabboItem> stored = store(connection, items, target.getId(), room.getId());
            charge(userId, room.getId(), now, stored.size());
            for (HabboItem item : stored) {
                if (room.getHabboItem(item.getId()) != item) {
                    continue;
                }
                item.setBaseItem(target);
                room.sendComposer(new RemoveFloorItemComposer(item, true).compose());
                room.sendComposer(new AddFloorItemComposer(item, room.getFurniOwnerName(item.getUserId())).compose());
            }
            return Result.DONE;
        } catch (SQLException e) {
            LOGGER.error("Could not recolor Builders Club furni {} in room {}", itemId, room.getId(), e);
            return Result.NOT_FOUND;
        }
    }

    static String familyOf(String itemName) {
        if (itemName == null) {
            return null;
        }
        int star = itemName.lastIndexOf('*');
        if (star <= 0 || star == itemName.length() - 1) {
            return null;
        }
        for (int i = star + 1; i < itemName.length(); i++) {
            if (!Character.isDigit(itemName.charAt(i))) {
                return null;
            }
        }
        return itemName.substring(0, star);
    }

    static boolean interchangeable(Item from, Item to) {
        return from != null
                && to != null
                && from.getId() != to.getId()
                && to.getType() == FurnitureType.FLOOR
                && from.getWidth() == to.getWidth()
                && from.getLength() == to.getLength()
                && Double.compare(from.getHeight(), to.getHeight()) == 0
                && from.allowStack() == to.allowStack()
                && from.allowWalk() == to.allowWalk()
                && from.allowSit() == to.allowSit()
                && from.allowLay() == to.allowLay()
                && from.getStateCount() == to.getStateCount()
                && from.getInteractionType() != null
                && to.getInteractionType() != null
                && from.getInteractionType().getType()
                        == to.getInteractionType().getType();
    }

    static boolean admit(int userId, int roomId, long now) {
        if (USER_NEXT.size() >= MAX_TRACKED) {
            USER_NEXT.clear();
        }
        if (ROOM_NEXT.size() >= MAX_TRACKED) {
            ROOM_NEXT.clear();
        }
        if (ROOM_NEXT.getOrDefault(roomId, 0L) > now) {
            return false;
        }
        boolean[] admitted = {false};
        USER_NEXT.compute(userId, (key, next) -> {
            if (next != null && next > now) {
                return next;
            }
            admitted[0] = true;
            return now + COOLDOWN_MS;
        });
        return admitted[0];
    }

    static void charge(int userId, int roomId, long now, int count) {
        long cost = (long) count * COOLDOWN_PER_ITEM_MS;
        USER_NEXT.merge(userId, now + Math.max(COOLDOWN_MS, cost), Math::max);
        if (count > 1) {
            ROOM_NEXT.merge(roomId, now + cost, Math::max);
        }
    }

    static boolean offeredInBuildersClub(Connection connection, int itemId) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement("SELECT 1 FROM catalog_items_bc WHERE item_ids = ? LIMIT 1")) {
            statement.setString(1, String.valueOf(itemId));
            try (ResultSet set = statement.executeQuery()) {
                return set.next();
            }
        }
    }

    static Set<Integer> buildersClubItemsIn(Connection connection, int roomId) throws SQLException {
        Set<Integer> ids = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT builders_club_items.item_id"
                + " FROM builders_club_items INNER JOIN items ON items.id = builders_club_items.item_id"
                + " WHERE items.room_id = ?")) {
            statement.setInt(1, roomId);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    ids.add(set.getInt(1));
                }
            }
        }
        return ids;
    }

    static List<HabboItem> store(Connection connection, List<HabboItem> items, int baseItemId, int roomId)
            throws SQLException {
        List<HabboItem> stored = new ArrayList<>();
        if (items.isEmpty()) {
            return stored;
        }
        try (PreparedStatement statement =
                connection.prepareStatement("UPDATE items SET item_id = ? WHERE id = ? AND room_id = ?")) {
            for (HabboItem item : items) {
                statement.setInt(1, baseItemId);
                statement.setInt(2, item.getId());
                statement.setInt(3, roomId);
                statement.addBatch();
            }
            int[] counts = statement.executeBatch();
            for (int i = 0; i < items.size() && i < counts.length; i++) {
                if (counts[i] > 0 || counts[i] == Statement.SUCCESS_NO_INFO) {
                    stored.add(items.get(i));
                }
            }
        }
        return stored;
    }
}
