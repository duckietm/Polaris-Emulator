package com.eu.habbo.habbohotel.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogItem;
import com.eu.habbo.messages.outgoing.users.ChatStyleNotificationComposer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The chat bubble styles a player bought (AIR 13 "purchasable chat styles", events 946 and
 * 2580). A style becomes purchasable by pointing {@code chat_bubbles.catalog_item_id} at
 * the catalog offer that sells it; buying that offer grants the style, which the chat
 * style selector then shows on top of the styles `chat.styles` already allows.
 *
 * <p>Ownership lives in {@code users_chat_styles} and is only read when the client asks
 * for its user data, so nothing has to be kept in sync per session.
 */
public final class ChatStyleRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatStyleRepository.class);

    /** catalog offer id -> chat bubble style id, refreshed with the chat bubbles. */
    private static final Map<Integer, Integer> CATALOG_ITEM_STYLES = new ConcurrentHashMap<>();

    private ChatStyleRepository() {}

    /** Re-reads which catalog offers sell a chat style. Called whenever chat bubbles reload. */
    public static void reload() {
        Map<Integer, Integer> styles = new HashMap<>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT type, catalog_item_id FROM chat_bubbles WHERE catalog_item_id > 0")) {
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    styles.put(set.getInt("catalog_item_id"), set.getInt("type"));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to load purchasable chat styles.", e);
            return;
        }

        CATALOG_ITEM_STYLES.keySet().retainAll(styles.keySet());
        CATALOG_ITEM_STYLES.putAll(styles);
    }

    public static List<Integer> findOwnedStyles(int userId) {
        List<Integer> styles = new ArrayList<>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT style_id FROM users_chat_styles WHERE user_id = ? ORDER BY style_id ASC")) {
            statement.setInt(1, userId);

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    styles.add(set.getInt("style_id"));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return styles;
    }

    /**
     * Grants the style the given catalog offer sells, if it sells one. Nothing happens for
     * a normal offer, and buying the same style twice is a no-op rather than a duplicate
     * notification.
     */
    public static void grantForPurchase(Habbo habbo, CatalogItem item) {
        if (habbo == null || item == null) return;

        Integer styleId = CATALOG_ITEM_STYLES.get(item.getId());

        if (styleId == null) return;

        grant(habbo, styleId);
    }

    /** Adds one style to the account and tells the client, unless the account already had it. */
    public static boolean grant(Habbo habbo, int styleId) {
        if (habbo == null || styleId <= 0) return false;

        int userId = habbo.getHabboInfo().getId();
        boolean added;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT IGNORE INTO users_chat_styles (user_id, style_id, timestamp) VALUES (?, ?, ?)")) {
            statement.setInt(1, userId);
            statement.setInt(2, styleId);
            statement.setInt(3, Emulator.getIntUnixTimestamp());
            added = statement.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
            return false;
        }

        if (added && habbo.getClient() != null) {
            habbo.getClient().sendResponse(new ChatStyleNotificationComposer(true, styleId));
        }

        return added;
    }

    /** Takes one style away again and tells the client, so staff can revoke a mis-sold style. */
    public static boolean revoke(Habbo habbo, int styleId) {
        if (habbo == null || styleId <= 0) return false;

        boolean removed;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM users_chat_styles WHERE user_id = ? AND style_id = ?")) {
            statement.setInt(1, habbo.getHabboInfo().getId());
            statement.setInt(2, styleId);
            removed = statement.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
            return false;
        }

        if (removed && habbo.getClient() != null) {
            habbo.getClient().sendResponse(new ChatStyleNotificationComposer(false, styleId));
        }

        return removed;
    }

    /** True when the account owns the style, used to gate the chat bubble a player picks. */
    public static boolean owns(int userId, int styleId) {
        return findOwnedStyles(userId).contains(styleId);
    }
}
