package polaris.hotel.catalog.specialitems;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntFunction;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.hotel.items.Item;
import polaris.hotel.items.ItemManager;
import polaris.hotel.users.UserItem;

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
    public record ClaimResult(UserItem item, boolean claimed) {}

    /** The choice a claim carries when the client sent none. */
    public static final int NO_CHOICE = -1;

    /**
     * The result the client is told ({@code ClaimProductResultComposer}). The official client reads 0 and 1 as
     * claimed, 2 and 3 as not available, anything else as "try again".
     */
    public enum Outcome {
        CLAIMED(0),
        ALREADY_CLAIMED(1),
        UNAVAILABLE(2),
        INVALID_CHOICE(3),
        FAILED(4);

        private final int code;

        Outcome(int code) {
            this.code = code;
        }

        public int code() {
            return this.code;
        }

        /** Whether the user now counts as having claimed, for the has-claimed answer. */
        public boolean countsAsClaimed() {
            return this != FAILED && this != INVALID_CHOICE;
        }
    }

    /** A pet a choice gives: its type, its breed (the palette) and the name it gets. */
    public record PetReward(int type, int breed, String name) {
        /** The name a pet gets when the choice names none. */
        public static final String DEFAULT_NAME = "Pet";

        public PetReward {
            name = name == null || name.isBlank() ? DEFAULT_NAME : name.trim();
        }
    }

    /**
     * What a choice holds: a furni ({@code items_base} id, 0 for none), a pet (null for none) and a badge code (empty
     * for none).
     */
    public record Choice(int itemId, PetReward pet, String badge) {
        public Choice {
            badge = badge == null ? "" : badge.trim();
        }

        public Choice(int itemId, PetReward pet) {
            this(itemId, pet, "");
        }

        /** Whether the choice gives anything at all. */
        public boolean givesSomething() {
            return this.itemId > 0 || this.pet != null || !this.badge.isEmpty();
        }
    }

    /**
     * What a claim with a choice did: the furni given, or nothing, the result for the client, and what is still to be
     * given to the user who is online: the pet (null for none) and the badge (empty for none).
     */
    public record ChoiceResult(UserItem item, Outcome outcome, PetReward pet, String badge) {
        public ChoiceResult {
            badge = badge == null ? "" : badge;
        }

        public ChoiceResult(UserItem item, Outcome outcome) {
            this(item, outcome, null, "");
        }
    }

    /** Creates the furni for a user, as {@code ItemManager.createItem}; null when it could not be created. */
    @FunctionalInterface
    public interface FurniFactory {
        UserItem create(int userId, int itemId);
    }

    private final DataSource dataSource;
    private final IntFunction<Item> baseItems;
    private final FurniFactory furniFactory;
    private final Set<String> reportedUnavailable = ConcurrentHashMap.newKeySet();

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

    /**
     * The offer behind a claim id, if the hotel offers it. A claim is offered by its row in
     * {@code special_items_free_claims} (enabled, with a furni or with choices), or, with no such row, by its choices
     * alone: filling in the two dragons is enough. The row is only needed to give a furni without a choice, or to
     * switch a claim off ({@code enabled = 0}). The offer's furni is 0 when only the choices say what is given.
     */
    public Optional<Offer> findOffer(String claimId) throws SQLException {
        if (!isValidClaimId(claimId)) return Optional.empty();

        boolean hasRow = false;
        boolean enabled = false;
        int itemId = 0;

        try (Connection connection = this.dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT item_id, enabled FROM special_items_free_claims WHERE claim_id = ?")) {
            statement.setString(1, claimId);
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    hasRow = true;
                    enabled = set.getBoolean("enabled");
                    itemId = set.getInt("item_id");
                }
            }
        }

        if (hasRow && !enabled) return Optional.empty();
        if (itemId > 0) return Optional.of(new Offer(claimId, itemId));

        return this.hasChoices(claimId) ? Optional.of(new Offer(claimId, 0)) : Optional.empty();
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
        if (this.findOffer(claimId).isEmpty()) {
            // Said once per claim id: the window shows "claimed" to everyone, which looks like a bug without it.
            if (this.reportedUnavailable.add(claimId)) {
                LOGGER.warn(
                        "Free claim '{}' was asked for but is not offered (no enabled row in special_items_free_claims"
                                + " and no choices, or the row is disabled), so it shows as already claimed to everyone",
                        claimId);
            }
            return true;
        }

        return this.hasClaimed(userId, claimId);
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

        UserItem item = this.furniFactory.create(userId, offer.get().itemId());
        if (item == null) {
            this.forgetClaim(userId, claimId);
            return new ClaimResult(null, false);
        }

        return new ClaimResult(item, true);
    }

    /**
     * What a choice of a claim gives ({@code special_items_free_claim_choices}): a furni, a pet, a badge, or several. Empty when
     * the claim has no such choice, or the choice gives nothing.
     */
    public Optional<Choice> findChoice(String claimId, int choice) throws SQLException {
        try (Connection connection = this.dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT item_id, pet_type, pet_breed, pet_name, badge_code FROM special_items_free_claim_choices"
                                + " WHERE claim_id = ? AND choice = ?")) {
            statement.setString(1, claimId);
            statement.setInt(2, choice);
            try (ResultSet set = statement.executeQuery()) {
                if (!set.next()) return Optional.empty();

                int itemId = Math.max(0, set.getInt("item_id"));
                int petType = set.getInt("pet_type");
                PetReward pet = petType < 0
                        ? null
                        : new PetReward(petType, Math.max(0, set.getInt("pet_breed")), set.getString("pet_name"));

                Choice found = new Choice(itemId, pet, set.getString("badge_code"));

                return found.givesSomething() ? Optional.of(found) : Optional.empty();
            }
        }
    }

    /** Whether the claim lets the user choose: it has at least one row of choices. */
    public boolean hasChoices(String claimId) throws SQLException {
        try (Connection connection = this.dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT 1 FROM special_items_free_claim_choices WHERE claim_id = ? LIMIT 1")) {
            statement.setString(1, claimId);
            try (ResultSet set = statement.executeQuery()) {
                return set.next();
            }
        }
    }

    /**
     * A claim that may carry a choice (the dragon adoption sends 0 or 1; the special items display sends none,
     * {@link #NO_CHOICE}). Gives what the choice holds once per user and claim id: a choice the claim does not have, or
     * no choice for a claim that needs one, gives nothing and leaves the claim open. The furni is created here; a pet
     * is only named in the result, for the caller to create for the user who is online.
     */
    public ChoiceResult claim(int userId, String claimId, int choice, int timestamp) throws SQLException {
        Optional<Offer> offer = this.findOffer(claimId);
        if (offer.isEmpty()) return new ChoiceResult(null, Outcome.UNAVAILABLE);

        Choice chosen;
        if (choice == NO_CHOICE) {
            if (this.hasChoices(claimId)) return new ChoiceResult(null, Outcome.INVALID_CHOICE);
            chosen = new Choice(offer.get().itemId(), null);
        } else {
            Optional<Choice> found = this.findChoice(claimId, choice);
            if (found.isEmpty()) return new ChoiceResult(null, Outcome.INVALID_CHOICE);
            chosen = found.get();
        }

        int itemId = chosen.itemId();
        if (itemId > 0 && this.baseItems.apply(itemId) == null) {
            LOGGER.warn(
                    "Special items claim {} (choice {}) points at missing items_base id {}", claimId, choice, itemId);
            return new ChoiceResult(null, Outcome.UNAVAILABLE);
        }

        if (!this.recordClaim(userId, new Offer(claimId, itemId), timestamp)) {
            return new ChoiceResult(null, Outcome.ALREADY_CLAIMED);
        }

        UserItem item = null;
        if (itemId > 0) {
            item = this.furniFactory.create(userId, itemId);
            if (item == null) {
                this.forgetClaim(userId, claimId);
                return new ChoiceResult(null, Outcome.FAILED);
            }
        }

        return new ChoiceResult(item, Outcome.CLAIMED, chosen.pet(), chosen.badge());
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
