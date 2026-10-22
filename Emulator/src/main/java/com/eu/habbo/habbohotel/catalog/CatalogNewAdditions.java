package com.eu.habbo.habbohotel.catalog;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.database.Database;
import com.eu.habbo.habbohotel.users.Habbo;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The "new additions" flag of the catalog index (AIR {@code HabboCatalog.onCatalogIndex}: when the index says there
 * are new additions the client opens the {@code new_additions} page and lights the "new" label on the catalogue
 * toolbar icon; the next time the catalogue opens it sends {@code MarkCatalogNewAdditionsPageOpened}).
 *
 * <p>A player has new additions when the catalogue has a {@code new_additions} page (caption_save) they may open and
 * it offers something newer than the newest offer they saw there: offers are catalog_items rows, so newer is a higher
 * id. The mark is the newest offer id of that page when the player last opened the catalogue with the flag set, kept
 * in {@code users_catalog_new_additions}. A player without a mark has not seen the page yet.
 */
public class CatalogNewAdditions {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogNewAdditions.class);

    /** AIR {@code CatalogPageName.CATALOG_PAGE_NEW_ADDITIONS}. */
    public static final String PAGE_NAME = "new_additions";

    public static final String ENABLED_KEY = "catalog.new_additions.enabled";

    private static final AtomicReference<CatalogNewAdditions> CURRENT =
            new AtomicReference<>(new CatalogNewAdditions(new JdbcStore()));

    private final Store store;

    public CatalogNewAdditions(Store store) {
        this.store = store;
    }

    public static CatalogNewAdditions get() {
        return CURRENT.get();
    }

    /** Swap the instance (tests); returns the one it replaced. Null puts the hotel's back. */
    public static CatalogNewAdditions use(CatalogNewAdditions next) {
        return CURRENT.getAndSet(next == null ? new CatalogNewAdditions(new JdbcStore()) : next);
    }

    /** The newest offer (catalog_items id) of a page, 0 for an empty page. */
    public static int newestOfferId(CatalogPage page) {
        if (page == null) return 0;
        int newest = 0;
        for (int id : page.catalogItemsView().keySet()) {
            if (id > newest) newest = id;
        }
        return newest;
    }

    /** Whether a player of {@code rank} (with or without club) may open {@code page} in the normal catalogue. */
    public static boolean opensFor(CatalogPage page, int rank, boolean hasClub) {
        return page != null
                && page.isVisible()
                && page.isEnabled()
                && page.getRank() <= rank
                && (!page.isClubOnly() || hasClub)
                && page.getCatalogPageType().matches(CatalogPageType.NORMAL);
    }

    /** The flag itself: the page opens for the player and offers something newer than their mark. */
    public static boolean hasNewAdditions(CatalogPage page, int rank, boolean hasClub, int lastSeenOfferId) {
        return opensFor(page, rank, hasClub) && newestOfferId(page) > lastSeenOfferId;
    }

    /** The flag of the normal catalogue index for {@code habbo}. */
    public boolean available(Habbo habbo) {
        CatalogPage page = this.page(habbo);
        if (page == null) return false;

        int rank = habbo.getHabboInfo().getRank() == null
                ? 0
                : habbo.getHabboInfo().getRank().getId();
        boolean hasClub = habbo.getHabboStats() != null && habbo.getHabboStats().hasActiveClub();
        if (!opensFor(page, rank, hasClub) || newestOfferId(page) <= 0) return false;

        try {
            return hasNewAdditions(
                    page,
                    rank,
                    hasClub,
                    this.store.lastSeen(habbo.getHabboInfo().getId()));
        } catch (SQLException e) {
            LOGGER.error(
                    "Catalog new additions mark could not be read userId={}",
                    habbo.getHabboInfo().getId(),
                    e);
            return false;
        }
    }

    /** MarkCatalogNewAdditionsPageOpened: the player saw everything the page offers now. */
    public void markOpened(Habbo habbo) {
        CatalogPage page = this.page(habbo);
        int newest = newestOfferId(page);
        if (newest <= 0) return;

        try {
            this.store.markSeen(habbo.getHabboInfo().getId(), newest, WiredPlatform.unixTimestamp());
        } catch (SQLException e) {
            LOGGER.error(
                    "Catalog new additions mark could not be saved userId={}",
                    habbo.getHabboInfo().getId(),
                    e);
        }
    }

    /** The new additions page, or null when the hotel has none or the feature is off. */
    protected CatalogPage page(Habbo habbo) {
        if (habbo == null || habbo.getHabboInfo() == null) return null;
        if (WiredPlatform.configuration() != null
                && !WiredPlatform.configuration().getBoolean(ENABLED_KEY, true)) return null;
        if (WiredPlatform.gameEnvironment() == null
                || WiredPlatform.gameEnvironment().getCatalogManager() == null) {
            return null;
        }
        return WiredPlatform.gameEnvironment().getCatalogManager().getCatalogPage(PAGE_NAME);
    }

    /** Where the marks live. */
    public interface Store {
        /** The newest offer id the player saw on the page, 0 when they never opened it. */
        int lastSeen(int userId) throws SQLException;

        void markSeen(int userId, int offerId, int seenAt) throws SQLException;
    }

    private static final class JdbcStore implements Store {
        private static Connection connection() throws SQLException {
            Database database = WiredPlatform.database();
            if (database == null) throw new SQLException("no database");
            return database.getDataSource().getConnection();
        }

        @Override
        public int lastSeen(int userId) throws SQLException {
            try (Connection connection = connection();
                    PreparedStatement statement = connection.prepareStatement(
                            "SELECT last_seen_offer_id FROM users_catalog_new_additions WHERE user_id = ? LIMIT 1")) {
                statement.setInt(1, userId);
                try (ResultSet set = statement.executeQuery()) {
                    return set.next() ? set.getInt("last_seen_offer_id") : 0;
                }
            }
        }

        @Override
        public void markSeen(int userId, int offerId, int seenAt) throws SQLException {
            try (Connection connection = connection();
                    PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO users_catalog_new_additions (user_id, last_seen_offer_id, seen_at) VALUES (?, ?, ?) "
                                    + "ON DUPLICATE KEY UPDATE last_seen_offer_id = GREATEST(last_seen_offer_id, VALUES(last_seen_offer_id)), "
                                    + "seen_at = VALUES(seen_at)")) {
                statement.setInt(1, userId);
                statement.setInt(2, offerId);
                statement.setInt(3, seenAt);
                statement.executeUpdate();
            }
        }
    }
}
