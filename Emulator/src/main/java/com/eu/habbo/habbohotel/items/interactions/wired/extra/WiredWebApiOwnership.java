package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.database.Database;
import com.eu.habbo.habbohotel.catalog.CatalogItem;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemInteraction;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ownership rules of the Variables Web API box: it cannot change hands, a user owns at most
 * {@code wired.api.max_per_user} (inventory and rooms together, default 1) and a room holds at most
 * one. Enforced here in code, whatever the {@code items_base} flags say.
 */
public final class WiredWebApiOwnership {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredWebApiOwnership.class);
    public static final String ONE_PER_USER_KEY = "wiredfurni.web_api.error.one_per_user";
    public static final String ONE_PER_ROOM_KEY = "wiredfurni.web_api.error.one_per_room";
    public static final String NOT_TRANSFERABLE_KEY = "wiredfurni.web_api.error.not_transferable";
    public static final String MAX_PER_USER_KEY = "wiredfurni.web_api.error.max_per_user";
    public static final String MAX_PER_USER_SETTING = "wired.api.max_per_user";
    public static final int MAX_PER_USER_LIMIT = 100;

    private static final String OWNED_SQL =
            "SELECT 1 FROM items INNER JOIN items_base ON items_base.id = items.item_id "
                    + "WHERE items.user_id = ? AND (items_base.interaction_type = ? OR items_base.item_name = ?) LIMIT 1";
    private static final String OWNED_COUNT_SQL =
            "SELECT COUNT(*) FROM items INNER JOIN items_base ON items_base.id = items.item_id "
                    + "WHERE items.user_id = ? AND (items_base.interaction_type = ? OR items_base.item_name = ?)";

    private WiredWebApiOwnership() {}

    /**
     * By interaction or by item name, so editing {@code interaction_type} in the database does not
     * turn the box into a normal, tradeable furni.
     */
    public static boolean isWebApiItem(Item baseItem) {
        if (baseItem == null) {
            return false;
        }
        if (WiredExtraVariableWebApi.INTERACTION_TYPE.equalsIgnoreCase(baseItem.getName())) {
            return true;
        }
        ItemInteraction interaction = baseItem.getInteractionType();
        return interaction != null
                && (WiredExtraVariableWebApi.INTERACTION_TYPE.equalsIgnoreCase(interaction.getName())
                        || (interaction.getType() != null
                                && WiredExtraVariableWebApi.class.isAssignableFrom(interaction.getType())));
    }

    public static boolean isWebApiItem(HabboItem item) {
        return item instanceof WiredExtraVariableWebApi || (item != null && isWebApiItem(item.getBaseItem()));
    }

    /**
     * The web-api box standing in the room, or null. Also null when the room somehow holds more than
     * one (a database edit): the API then refuses the room instead of picking one of their keys.
     */
    public static WiredExtraVariableWebApi boxIn(Room room) {
        WiredExtraVariableWebApi found = null;
        for (WiredExtraVariableWebApi box : boxesIn(room)) {
            if (found != null) {
                return null;
            }
            found = box;
        }
        return found;
    }

    private static List<WiredExtraVariableWebApi> boxesIn(Room room) {
        if (room == null || room.getRoomSpecialTypes() == null) {
            return List.of();
        }
        List<WiredExtraVariableWebApi> boxes = new ArrayList<>(1);
        for (InteractionWiredExtra extra : room.getRoomSpecialTypes().getExtras()) {
            if (extra instanceof WiredExtraVariableWebApi box) {
                boxes.add(box);
            }
        }
        return boxes;
    }

    public static final String OWN_ROOM_ONLY_KEY = "wiredfurni.web_api.error.own_room_only";

    /**
     * Why placing {@code item} in {@code room} must be refused, or null: the box only goes into a
     * room its owner owns, and a room holds one. Otherwise a rights holder could park an inert box
     * that blocks the room owner's own.
     */
    public static String placementRefusal(Room room, HabboItem item) {
        if (!isWebApiItem(item) || room == null) {
            return null;
        }
        if (room.getOwnerId() != item.getUserId()) {
            return OWN_ROOM_ONLY_KEY;
        }
        return wouldExceedRoomLimit(room, item) ? ONE_PER_ROOM_KEY : null;
    }

    /** Whether placing {@code item} would put a second web-api box in the room. */
    public static boolean wouldExceedRoomLimit(Room room, HabboItem item) {
        if (!isWebApiItem(item)) {
            return false;
        }
        for (WiredExtraVariableWebApi existing : boxesIn(room)) {
            if (existing.getId() != item.getId()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Why a purchase of {@code amount} times this offer must be refused, or null when it may go
     * ahead: it would take the buyer over {@code wired.api.max_per_user} boxes.
     */
    public static String purchaseRefusal(CatalogItem offer, int amount, int userId) {
        return purchaseRefusal(offer, amount, userId, WiredWebApiOwnership::ownedCount, maxPerUser());
    }

    static String purchaseRefusal(CatalogItem offer, int amount, int userId, IntUnaryOperator owned, int max) {
        if (offer == null) {
            return null;
        }
        long boxes = 0;
        for (Item baseItem : offer.getBaseItems()) {
            if (isWebApiItem(baseItem)) {
                boxes += (long) Math.max(1, offer.getItemAmount(baseItem.getId())) * Math.max(1, amount);
            }
        }
        if (boxes == 0) {
            return null;
        }
        return exceedsLimit(owned.applyAsInt(userId), boxes, max) ? limitKey(max) : null;
    }

    /** Whether the user may receive {@code adding} more boxes under {@code wired.api.max_per_user}. */
    public static boolean canOwnMore(int userId, int adding) {
        return !exceedsLimit(ownedCount(userId), adding, maxPerUser());
    }

    /** The refusal text for a user at the limit. */
    public static String limitKey() {
        return limitKey(maxPerUser());
    }

    static String limitKey(int max) {
        return max <= 1 ? ONE_PER_USER_KEY : MAX_PER_USER_KEY;
    }

    static boolean exceedsLimit(int owned, long adding, int max) {
        return adding > 0 && (long) owned + adding > max;
    }

    /** {@code wired.api.max_per_user}, 1 to {@link #MAX_PER_USER_LIMIT}; 1 when unset. */
    public static int maxPerUser() {
        ConfigurationManager config = WiredPlatform.configuration();
        int value = config == null ? 1 : config.getInt(MAX_PER_USER_SETTING, 1);
        return Math.max(1, Math.min(MAX_PER_USER_LIMIT, value));
    }

    /**
     * How many boxes the user owns (inventory and rooms). Answers {@link Integer#MAX_VALUE} when the
     * check cannot be made, so a failing database refuses rather than hands out another box.
     */
    public static int ownedCount(int userId) {
        Database database = WiredPlatform.database();
        if (database == null || userId <= 0) {
            return Integer.MAX_VALUE;
        }
        try (Connection connection = database.getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(OWNED_COUNT_SQL)) {
            statement.setInt(1, userId);
            statement.setString(2, WiredExtraVariableWebApi.INTERACTION_TYPE);
            statement.setString(3, WiredExtraVariableWebApi.INTERACTION_TYPE);
            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? set.getInt(1) : 0;
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to count web API boxes of user {}", userId, e);
            return Integer.MAX_VALUE;
        }
    }

    /**
     * Whether the user already owns a web-api box anywhere. Answers true when the check cannot be
     * made, so a failing database refuses rather than hands out a second box.
     */
    public static boolean ownsOne(int userId) {
        Database database = WiredPlatform.database();
        if (database == null || userId <= 0) {
            return true;
        }
        try (Connection connection = database.getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(OWNED_SQL)) {
            statement.setInt(1, userId);
            statement.setString(2, WiredExtraVariableWebApi.INTERACTION_TYPE);
            statement.setString(3, WiredExtraVariableWebApi.INTERACTION_TYPE);
            try (ResultSet set = statement.executeQuery()) {
                return set.next();
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to check web API box ownership for user {}", userId, e);
            return true;
        }
    }
}
