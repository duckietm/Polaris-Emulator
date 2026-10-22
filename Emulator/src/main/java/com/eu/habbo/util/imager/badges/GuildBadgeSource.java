package com.eu.habbo.util.imager.badges;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.sql.DataSource;

/** The badge codes of all guilds, for the badge imager's backfill. */
public final class GuildBadgeSource {
    private GuildBadgeSource() {}

    public static Supplier<List<String>> fromDataSource(DataSource dataSource) {
        return () -> {
            List<String> badges = new ArrayList<>();
            if (dataSource == null) {
                return badges;
            }
            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement();
                    ResultSet set = statement.executeQuery("SELECT DISTINCT badge FROM guilds")) {
                while (set.next()) {
                    badges.add(set.getString("badge"));
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Could not read the guild badges", e);
            }
            return badges;
        };
    }
}
