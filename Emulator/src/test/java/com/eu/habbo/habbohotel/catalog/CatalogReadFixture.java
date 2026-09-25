package com.eu.habbo.habbohotel.catalog;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.database.Database;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboStats;
import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import org.mockito.MockedStatic;

/**
 * DB-free building blocks for the catalog read-index characterization tests: a {@link
 * CatalogManager} that never touches the database, and page/item/voucher/cloth/user fixtures
 * built through the same mock-{@link ResultSet} path the production loaders use, without a live
 * connection.
 */
final class CatalogReadFixture {

    private CatalogReadFixture() {}

    /** A {@link CatalogManager} constructed without running {@code initialize()}. */
    static CatalogManager manager() throws Exception {
        Field configField = Emulator.class.getDeclaredField("config");
        configField.setAccessible(true);
        Object previousConfig = configField.get(null);
        try {
            configField.set(
                    null,
                    new ConfigurationManager(Path.of("..", "config example", "config.ini.example")
                            .toString()));
            return new CatalogManager(false);
        } finally {
            configField.set(null, previousConfig);
        }
    }

    /** A concrete, DB-free {@link CatalogPage} whose fields are set directly. */
    static final class TestPage extends CatalogPage {
        TestPage(
                int id,
                int parentId,
                int orderNum,
                boolean visible,
                boolean enabled,
                boolean clubOnly,
                int rank,
                String pageName,
                String layout,
                CatalogPageType type) {
            this.id = id;
            this.parentId = parentId;
            this.orderNum = orderNum;
            this.visible = visible;
            this.enabled = enabled;
            this.clubOnly = clubOnly;
            this.rank = rank;
            this.pageName = pageName;
            this.caption = pageName;
            this.layout = layout;
            this.catalogPageType = type;
        }

        @Override
        public void serialize(com.eu.habbo.messages.ServerMessage message) {}
    }

    static TestPage page(
            int id,
            int parentId,
            int orderNum,
            boolean visible,
            boolean enabled,
            boolean clubOnly,
            int rank,
            String pageName,
            String layout,
            CatalogPageType type) {
        return new TestPage(id, parentId, orderNum, visible, enabled, clubOnly, rank, pageName, layout, type);
    }

    /**
     * Puts {@code page} into the page map for {@code type} and, when its parent is already
     * present in that same map, attaches it as a child page too.
     */
    static void attach(CatalogManager manager, CatalogPageType type, TestPage page) {
        manager.getCatalogPagesMap(type).put(page.getId(), page);

        CatalogPage parent = manager.getCatalogPagesMap(type).get(page.getParentId());
        if (parent != null) {
            parent.addChildPage(page);
        }
    }

    /** A {@link CatalogItem} built from a mock {@link ResultSet}, no database involved. */
    static CatalogItem item(int id, int pageId, int orderNum) {
        try {
            ResultSet set = mock(ResultSet.class);
            ResultSetMetaData metadata = mock(ResultSetMetaData.class);
            when(set.getMetaData()).thenReturn(metadata);
            when(metadata.getColumnCount()).thenReturn(0);

            when(set.getInt("id")).thenReturn(id);
            when(set.getInt("page_id")).thenReturn(pageId);
            when(set.getString("item_Ids")).thenReturn("");
            when(set.getString("catalog_name")).thenReturn("item_" + id);
            when(set.getInt("cost_credits")).thenReturn(0);
            when(set.getInt("cost_points")).thenReturn(0);
            when(set.getShort("points_type")).thenReturn((short) 0);
            when(set.getInt("amount")).thenReturn(1);
            when(set.getInt("limited_stack")).thenReturn(0);
            when(set.getInt("limited_sells")).thenReturn(0);
            when(set.getString("extradata")).thenReturn("");
            when(set.getBoolean("club_only")).thenReturn(false);
            when(set.getBoolean("have_offer")).thenReturn(false);
            when(set.getInt("offer_id")).thenReturn(0);
            when(set.getInt("order_number")).thenReturn(orderNum);

            return new CatalogItem(set);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /** A {@link Voucher} built from a mock {@link ResultSet}; the database is stubbed so history loads empty. */
    static Voucher voucher(String code) {
        try {
            ResultSet set = mock(ResultSet.class);
            when(set.getInt("id")).thenReturn(code.hashCode());
            when(set.getString("code")).thenReturn(code);
            when(set.getInt("credits")).thenReturn(0);
            when(set.getInt("points")).thenReturn(0);
            when(set.getInt("points_type")).thenReturn(0);
            when(set.getInt("catalog_item_id")).thenReturn(0);
            when(set.getInt("amount")).thenReturn(0);
            when(set.getInt("limit")).thenReturn(0);

            Database database = mock(Database.class);
            HikariDataSource dataSource = mock(HikariDataSource.class);
            Connection connection = mock(Connection.class);
            PreparedStatement statement = mock(PreparedStatement.class);
            ResultSet historySet = mock(ResultSet.class);
            when(database.getDataSource()).thenReturn(dataSource);
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(org.mockito.ArgumentMatchers.anyString()))
                    .thenReturn(statement);
            when(statement.executeQuery()).thenReturn(historySet);
            when(historySet.next()).thenReturn(false);

            try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
                emulator.when(Emulator::getDatabase).thenReturn(database);
                return new Voucher(set);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /** A {@link ClothItem} built from a mock {@link ResultSet}. */
    static ClothItem cloth(int id, String name) {
        try {
            ResultSet set = mock(ResultSet.class);
            when(set.getInt("id")).thenReturn(id);
            when(set.getString("name")).thenReturn(name);
            when(set.getString("setid")).thenReturn("1");
            return new ClothItem(set);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /** A mock {@link Habbo} whose rank and club status are stubbed for {@code getCatalogPages}. */
    static Habbo habbo(int rank, boolean club) {
        Habbo habbo = mock(Habbo.class);
        HabboInfo habboInfo = mock(HabboInfo.class);
        HabboStats habboStats = mock(HabboStats.class);
        Rank habboRank = mock(Rank.class);

        when(habbo.getHabboInfo()).thenReturn(habboInfo);
        when(habboInfo.getRank()).thenReturn(habboRank);
        when(habboRank.getId()).thenReturn(rank);
        when(habboInfo.getHabboStats()).thenReturn(habboStats);
        when(habboStats.hasActiveClub()).thenReturn(club);

        return habbo;
    }
}
