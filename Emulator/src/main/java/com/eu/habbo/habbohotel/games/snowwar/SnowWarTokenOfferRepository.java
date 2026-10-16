package com.eu.habbo.habbohotel.games.snowwar;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The AIR game-token offers ("get more games"), the per-user balance of
 * extra games they buy and the free daily games used. AIR reads the offer list once (GetSnowWarGameTokens
 * 980 -> SnowWarGameTokens 3419) and buys one by its id (391).
 */
public final class SnowWarTokenOfferRepository implements SnowWarGameAllowance {

    private static final Logger LOGGER = LoggerFactory.getLogger(SnowWarTokenOfferRepository.class);
    private final SnowWarConnectionProvider connections;

    SnowWarTokenOfferRepository(SnowWarConnectionProvider connections) {
        this.connections = connections;
    }

    /** One row of the offer table; {@code games} is what a purchase credits. */
    public record Offer(
            int offerId, String localizationId, int priceInCredits, int priceInPoints, int pointsType, int games) {}

    public List<Offer> loadOffers() {
        List<Offer> offers = new ArrayList<>();
        String sql = "SELECT id, localization_id, price_credits, price_points, points_type, games "
                + "FROM snowwar_token_offers WHERE enabled = '1' ORDER BY order_num, id";
        try (Connection connection = this.connections.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                offers.add(new Offer(
                        result.getInt("id"),
                        result.getString("localization_id"),
                        result.getInt("price_credits"),
                        result.getInt("price_points"),
                        result.getInt("points_type"),
                        result.getInt("games")));
            }
        } catch (SQLException exception) {
            LOGGER.error("Unable to load the SnowWar game token offers", exception);
        }
        return offers;
    }

    public Offer findOffer(int offerId) {
        return this.loadOffers().stream()
                .filter(offer -> offer.offerId() == offerId)
                .findFirst()
                .orElse(null);
    }

    /** Extra games this user has bought and not used yet. */
    public int getExtraGames(int userId) {
        try (Connection connection = this.connections.openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT games FROM snowwar_game_tokens WHERE user_id = ? LIMIT 1")) {
            statement.setInt(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Math.max(0, result.getInt(1)) : 0;
            }
        } catch (SQLException exception) {
            LOGGER.error("Unable to read the SnowWar game token balance", exception);
            return 0;
        }
    }

    public boolean addExtraGames(int userId, int games) {
        if (games <= 0) {
            return false;
        }
        String sql = "INSERT INTO snowwar_game_tokens (user_id, games) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE games = games + VALUES(games)";
        try (Connection connection = this.connections.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            statement.setInt(2, games);
            statement.executeUpdate();
            return true;
        } catch (SQLException exception) {
            LOGGER.error("Unable to credit SnowWar game tokens", exception);
            return false;
        }
    }

    @Override
    public ConsumeResult tryConsumeGame(int userId, LocalDate today, int freePerDay) {
        if (freePerDay < 0) {
            return ConsumeResult.UNLIMITED;
        }
        java.sql.Date day = java.sql.Date.valueOf(today);
        // Each step is one conditional statement, so two matches charging the
        // same user at once can never both use the last game.
        try (Connection connection = this.connections.openConnection()) {
            try (PreparedStatement ensure = connection.prepareStatement(
                    "INSERT IGNORE INTO snowwar_game_tokens (user_id, games, free_day, free_used) VALUES (?, 0, ?, 0)")) {
                ensure.setInt(1, userId);
                ensure.setDate(2, day);
                ensure.executeUpdate();
            }
            try (PreparedStatement newDay =
                    connection.prepareStatement("UPDATE snowwar_game_tokens SET free_day = ?, free_used = 0 "
                            + "WHERE user_id = ? AND (free_day IS NULL OR free_day <> ?)")) {
                newDay.setDate(1, day);
                newDay.setInt(2, userId);
                newDay.setDate(3, day);
                newDay.executeUpdate();
            }
            if (freePerDay > 0) {
                try (PreparedStatement free =
                        connection.prepareStatement("UPDATE snowwar_game_tokens SET free_used = free_used + 1 "
                                + "WHERE user_id = ? AND free_day = ? AND free_used < ?")) {
                    free.setInt(1, userId);
                    free.setDate(2, day);
                    free.setInt(3, freePerDay);
                    if (free.executeUpdate() == 1) {
                        return ConsumeResult.FREE;
                    }
                }
            }
            try (PreparedStatement token = connection.prepareStatement(
                    "UPDATE snowwar_game_tokens SET games = games - 1 WHERE user_id = ? AND games > 0")) {
                token.setInt(1, userId);
                if (token.executeUpdate() == 1) {
                    return ConsumeResult.TOKEN;
                }
            }
            return ConsumeResult.NO_GAMES;
        } catch (SQLException exception) {
            LOGGER.error("Unable to charge a SnowWar game", exception);
            return ConsumeResult.ERROR;
        }
    }

    @Override
    public boolean refundGame(int userId) {
        return this.addExtraGames(userId, 1);
    }

    @Override
    public int gamesLeft(int userId, LocalDate today, int freePerDay) {
        if (freePerDay < 0) {
            return -1;
        }
        try (Connection connection = this.connections.openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT games, free_day, free_used FROM snowwar_game_tokens WHERE user_id = ? LIMIT 1")) {
            statement.setInt(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return freePerDay;
                }
                java.sql.Date day = result.getDate("free_day");
                int used = day != null && day.toLocalDate().equals(today) ? result.getInt("free_used") : 0;
                return Math.max(0, freePerDay - used) + Math.max(0, result.getInt("games"));
            }
        } catch (SQLException exception) {
            LOGGER.error("Unable to read the SnowWar games left", exception);
            // Unknown: don't block joining; the charge at match start decides.
            return freePerDay;
        }
    }
}
