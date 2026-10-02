package com.eu.habbo.habbohotel.users.customization;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import javax.sql.DataSource;

public final class UserCustomizationRepository {

    private final DataSource dataSource;

    public UserCustomizationRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<NickIconOffer> findNickIcon(String iconKey) throws SQLException {
        String sql =
                "SELECT points, points_type, enabled " + "FROM custom_nick_icons_catalog WHERE icon_key = ? LIMIT 1";
        try (Connection connection = this.dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, iconKey);
            try (ResultSet set = statement.executeQuery()) {
                if (!set.next()) {
                    return Optional.empty();
                }
                return Optional.of(new NickIconOffer(
                        iconKey, set.getInt("points"), set.getInt("points_type"), set.getBoolean("enabled")));
            }
        }
    }

    public record NickIconOffer(String iconKey, int points, int pointsType, boolean enabled) {}
}
