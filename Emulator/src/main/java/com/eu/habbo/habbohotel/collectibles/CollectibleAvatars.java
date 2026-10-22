package com.eu.habbo.habbohotel.collectibles;

import com.eu.habbo.habbohotel.collectibles.CollectibleData.AvatarOutfit;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.Owned;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.WardrobeOutfit;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.WornOutfit;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import javax.sql.DataSource;

/**
 * Avatar collectibles: the looks of the avatar editor's NFT tab (AIR {@code avatar/nft/NftAvatarsModel},
 * {@code HabboAvatarEditor}). A product with a {@code collectible_avatar_outfits} row is wearable; owning one of
 * its tokens ({@code users_collectibles}) lets the owner put the look on. The worn token and the look it replaced
 * live in {@code users_collectible_outfits}; it counts as worn only while the user owns the token and still has
 * its look, so any other look change (or giving the token away) takes it off.
 */
public final class CollectibleAvatars {
    /** AIR {@code NftOutfit} collections: the editor's colours and names of a look. */
    public static final String CONTRACT_AVATAR = "habbo:avatar";

    public static final String CONTRACT_CLOTHES = "habbo:clothes";
    public static final String CONTRACT_GENESIS = "habbo:avatar_genesis";

    private static final String OWNED_OUTFITS = "SELECT uc.asset_id, uc.user_id, uc.product_code, uc.serial, "
            + "o.figure, o.gender, o.contract_key FROM users_collectibles uc "
            + "JOIN collectible_avatar_outfits o ON o.product_code = uc.product_code WHERE uc.user_id = ?";

    private final DataSource dataSource;

    public CollectibleAvatars(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    /** {@code M} or {@code F}, the way AIR's editor reads a look's gender ("f"/"F" female, else male). */
    public static String normalizeGender(String gender) {
        return gender != null && gender.trim().toUpperCase(Locale.ROOT).startsWith("F") ? "F" : "M";
    }

    /** Whether a user whose look is {@code currentLook} still wears {@code figure}. */
    public static boolean isWorn(String figure, String currentLook) {
        return figure != null
                && !figure.isBlank()
                && currentLook != null
                && figure.trim().equalsIgnoreCase(currentLook.trim());
    }

    /** The asset id the editor sends back as the wardrobe item id (AIR {@code NftWardrobeItem.id}). */
    public static OptionalLong parseAssetId(String id) {
        if (id == null) return OptionalLong.empty();
        String trimmed = id.trim();
        if (trimmed.isEmpty() || trimmed.length() > 19) return OptionalLong.empty();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c < '0' || c > '9') return OptionalLong.empty();
        }
        try {
            long value = Long.parseLong(trimmed);
            return value > 0 ? OptionalLong.of(value) : OptionalLong.empty();
        } catch (NumberFormatException exception) {
            return OptionalLong.empty();
        }
    }

    /** The avatar collectibles a user owns, oldest first. */
    public List<WardrobeOutfit> wardrobe(int userId) throws SQLException {
        List<WardrobeOutfit> outfits = new ArrayList<>();
        try (Connection connection = this.dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(OWNED_OUTFITS + " ORDER BY uc.asset_id")) {
            statement.setInt(1, userId);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) outfits.add(readOutfit(set));
            }
        }
        return outfits;
    }

    /** One avatar collectible of the user, empty when the user does not own that token or it is no look. */
    public Optional<WardrobeOutfit> owned(int userId, long assetId) throws SQLException {
        try (Connection connection = this.dataSource.getConnection()) {
            return owned(connection, userId, assetId);
        }
    }

    /** The avatar collectible the user wears while their look is {@code currentLook}, if any. */
    public Optional<WornOutfit> worn(int userId, String currentLook) throws SQLException {
        try (Connection connection = this.dataSource.getConnection()) {
            return worn(connection, userId, currentLook);
        }
    }

    /**
     * Puts on an avatar collectible the user owns: remembers it, and the look it replaces unless the user already
     * wears another avatar collectible (then the look from before that one stays the one to go back to). Empty,
     * nothing stored, when the user does not own the token or it is no look. The caller applies the figure.
     */
    public Optional<WardrobeOutfit> wear(int userId, long assetId, String currentLook, String currentGender, int now)
            throws SQLException {
        try (Connection connection = this.dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                Optional<WardrobeOutfit> outfit = owned(connection, userId, assetId);
                if (outfit.isEmpty()) {
                    connection.rollback();
                    return outfit;
                }
                Optional<WornOutfit> worn = worn(connection, userId, currentLook);
                String fallbackFigure =
                        worn.map(WornOutfit::fallbackFigure).orElse(currentLook == null ? "" : currentLook);
                String fallbackGender = worn.map(WornOutfit::fallbackGender).orElse(normalizeGender(currentGender));
                try (PreparedStatement upsert = connection.prepareStatement(
                        "INSERT INTO users_collectible_outfits (user_id, asset_id, fallback_figure, fallback_gender, worn_at) "
                                + "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE asset_id = VALUES(asset_id), "
                                + "fallback_figure = VALUES(fallback_figure), fallback_gender = VALUES(fallback_gender), "
                                + "worn_at = VALUES(worn_at)")) {
                    upsert.setInt(1, userId);
                    upsert.setLong(2, assetId);
                    upsert.setString(
                            3, fallbackFigure.length() > 255 ? fallbackFigure.substring(0, 255) : fallbackFigure);
                    upsert.setString(4, fallbackGender);
                    upsert.setInt(5, now);
                    upsert.executeUpdate();
                }
                connection.commit();
                return outfit;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    private static Optional<WardrobeOutfit> owned(Connection connection, int userId, long assetId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(OWNED_OUTFITS + " AND uc.asset_id = ?")) {
            statement.setInt(1, userId);
            statement.setLong(2, assetId);
            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? Optional.of(readOutfit(set)) : Optional.empty();
            }
        }
    }

    private static Optional<WornOutfit> worn(Connection connection, int userId, String currentLook)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT w.asset_id, w.fallback_figure, w.fallback_gender, o.figure FROM users_collectible_outfits w "
                        + "JOIN users_collectibles uc ON uc.asset_id = w.asset_id AND uc.user_id = w.user_id "
                        + "JOIN collectible_avatar_outfits o ON o.product_code = uc.product_code WHERE w.user_id = ?")) {
            statement.setInt(1, userId);
            try (ResultSet set = statement.executeQuery()) {
                if (!set.next() || !isWorn(set.getString("figure"), currentLook)) return Optional.empty();
                return Optional.of(new WornOutfit(
                        set.getLong("asset_id"),
                        set.getString("fallback_figure"),
                        normalizeGender(set.getString("fallback_gender"))));
            }
        }
    }

    private static WardrobeOutfit readOutfit(ResultSet set) throws SQLException {
        return new WardrobeOutfit(
                new Owned(
                        set.getLong("asset_id"),
                        set.getInt("user_id"),
                        set.getString("product_code"),
                        set.getInt("serial")),
                new AvatarOutfit(
                        set.getString("product_code"),
                        set.getString("figure"),
                        set.getString("gender"),
                        set.getString("contract_key")));
    }
}
