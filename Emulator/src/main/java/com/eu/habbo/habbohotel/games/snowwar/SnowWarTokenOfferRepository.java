package com.eu.habbo.habbohotel.games.snowwar;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The AIR game-token offers ("get more games") and the per-user balance of
 * extra games they buy. AIR reads the offer list once (GetSnowWarGameTokens
 * 980 -> SnowWarGameTokens 3419) and buys one by its id (391).
 */
public final class SnowWarTokenOfferRepository {

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
}
