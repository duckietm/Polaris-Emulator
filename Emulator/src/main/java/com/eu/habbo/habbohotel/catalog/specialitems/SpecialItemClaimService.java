package com.eu.habbo.habbohotel.catalog.specialitems;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemManager;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntFunction;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Free claims of the special items display ({@code SpecialItemsController} in the official client).
 *
 * <p>A showcase names one claim id in the client configuration ({@code special_items.<key>.free_claim}).
 * The hotel maps that id to a furni in {@code special_items_free_claims}; each user can claim it once,
 * recorded in {@code users_special_item_claims}.
 */
public final class SpecialItemClaimService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpecialItemClaimService.class);

    public static final int MAX_CLAIM_ID_LENGTH = 64;

    private static final Pattern CLAIM_ID = Pattern.compile("[A-Za-z0-9_.\\-]{1," + MAX_CLAIM_ID_LENGTH + "}");
    private static final AtomicReference<SpecialItemClaimService> INSTALLED = new AtomicReference<>();

    /** A claimable offer: the furni ({@code items_base.id}) a claim id hands out. */
    public record Offer(String claimId, int itemId) {}

    /** What a claim did: the furni given, or nothing, and whether the user now counts as having claimed. */
    public record ClaimResult(HabboItem item, boolean claimed) {}

    /** Creates the furni for a user, as {@code ItemManager.createItem}; null when it could not be created. */
    @FunctionalInterface
    public interface FurniFactory {
        HabboItem create(int userId, int itemId);
    }

    private final DataSource dataSource;
    private final IntFunction<Item> baseItems;
    private final FurniFactory furniFactory;

    public SpecialItemClaimService(DataSource dataSource, IntFunction<Item> baseItems, FurniFactory furniFactory) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.baseItems = Objects.requireNonNull(baseItems, "baseItems");
        this.furniFactory = Objects.requireNonNull(furniFactory, "furniFactory");
    }

    public SpecialItemClaimService(DataSource dataSource, ItemManager itemManager) {
        this(dataSource, itemManager::getItem, (userId, itemId) -> {
            Item item = itemManager.getItem(itemId);
            return item == null ? null : itemManager.createItem(userId, item, 0, 0, "");
        });
    }

    /** Installed once at start-up, like the other hotel runtimes. */
    public static void install(SpecialItemClaimService service) {
        INSTALLED.set(Objects.requireNonNull(service, "service"));
    }

    /** The installed service, or null before start-up finished. */
    public static SpecialItemClaimService installed() {
        return INSTALLED.get();
    }

    /** Claim ids are configuration keys: short, ASCII letters, digits and {@code _ . -} only. */
    public static boolean isValidClaimId(String claimId) {
        return claimId != null && CLAIM_ID.matcher(claimId).matches();
    }

    /** The enabled offer behind a claim id, if the hotel configured one. */
    public Optional<Offer> findOffer(String claimId) throws SQLException {
        if (!isValidClaimId(claimId)) return Optional.empty();

        try (Connection connection = this.dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT item_id FROM special_items_free_claims WHERE claim_id = ? AND enabled = 1")) {
            statement.setString(1, claimId);
            try (ResultSet set = statement.executeQuery()) {
                if (!set.next()) return Optional.empty();
                int itemId = set.getInt("item_id");
                return itemId > 0 ? Optional.of(new Offer(claimId, itemId)) : Optional.empty();
            }
        }
    }

    public boolean hasClaimed(int userId, String claimId) throws SQLException {
        if (!isValidClaimId(claimId)) return false;

        try (Connection connection = this.dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT 1 FROM users_special_item_claims WHERE user_id = ? AND claim_id = ? LIMIT 1")) {
            statement.setInt(1, userId);
            statement.setString(2, claimId);
            try (ResultSet set = statement.executeQuery()) {
                return set.next();
            }
        }
    }

    /**
     * The has-claimed answer: a claim id the hotel does not offer counts as claimed, so the window never
     * enables a claim that would fail.
     */
    public boolean claimedOrUnavailable(int userId, String claimId) throws SQLException {
        return this.findOffer(claimId).isEmpty() || this.hasClaimed(userId, claimId);
    }

    /**
     * Gives the claim's furni once. A second (or racing) claim, an unknown claim id or a furni type that
     * no longer exists gives nothing; a furni that could not be created leaves the claim open.
     */
    public ClaimResult claim(int userId, String claimId, int timestamp) throws SQLException {
        Optional<Offer> offer = this.findOffer(claimId);
        if (offer.isEmpty()) return new ClaimResult(null, true);

        if (this.baseItems.apply(offer.get().itemId()) == null) {
            LOGGER.warn(
                    "Special items claim {} points at missing items_base id {}",
                    claimId,
                    offer.get().itemId());
            return new ClaimResult(null, true);
        }

        if (!this.recordClaim(userId, offer.get(), timestamp)) return new ClaimResult(null, true);

        HabboItem item = this.furniFactory.create(userId, offer.get().itemId());
        if (item == null) {
            this.forgetClaim(userId, claimId);
            return new ClaimResult(null, false);
        }

        return new ClaimResult(item, true);
    }

    /**
     * Records the claim. Returns {@code true} only for the call that recorded it, so two racing claims
     * hand out one furni.
     */
    public boolean recordClaim(int userId, Offer offer, int timestamp) throws SQLException {
        try (Connection connection = this.dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO users_special_item_claims (user_id, claim_id, item_id, claimed_at) VALUES (?, ?, ?, ?)")) {
            statement.setInt(1, userId);
            statement.setString(2, offer.claimId());
            statement.setInt(3, offer.itemId());
            statement.setInt(4, timestamp);
            return statement.executeUpdate() == 1;
        } catch (SQLIntegrityConstraintViolationException duplicate) {
            return false;
        } catch (SQLException exception) {
            if ("23000".equals(exception.getSQLState())) return false;
            throw exception;
        }
    }

    /** Undoes a recorded claim whose furni could not be created, so the user can try again. */
    public void forgetClaim(int userId, String claimId) throws SQLException {
        try (Connection connection = this.dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM users_special_item_claims WHERE user_id = ? AND claim_id = ?")) {
            statement.setInt(1, userId);
            statement.setString(2, claimId);
            statement.executeUpdate();
        }
    }
}
