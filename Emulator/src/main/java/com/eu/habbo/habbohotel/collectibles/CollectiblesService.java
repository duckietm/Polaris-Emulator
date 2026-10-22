package com.eu.habbo.habbohotel.collectibles;

import com.eu.habbo.habbohotel.collectibles.CollectibleData.BoxPrize;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.Collection;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.CollectionState;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.CollectorScore;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.LevelReward;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.MintTokenOffer;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.Mintable;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.OpenReward;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.Owned;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.Product;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntUnaryOperator;
import javax.sql.DataSource;

/**
 * Collectibles ownership in the database: products, sets, owned tokens and their serials, claims, the
 * collector's high score and relic stamps. See {@code docs/collectibles.md}.
 *
 * <p>Currencies are not touched here; handlers take and refund them around the calls that mint.
 */
public final class CollectiblesService {
    public static final int MAX_CODE_LENGTH = 64;

    private final DataSource dataSource;
    private final IntUnaryOperator random;
    private final CollectibleAvatars avatars;

    public CollectiblesService(DataSource dataSource) {
        this(dataSource, bound -> ThreadLocalRandom.current().nextInt(bound));
    }

    public CollectiblesService(DataSource dataSource, IntUnaryOperator random) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.random = Objects.requireNonNull(random, "random");
        this.avatars = new CollectibleAvatars(dataSource);
    }

    /** Avatar collectibles: the wearable looks among the user's tokens. */
    public CollectibleAvatars avatars() {
        return this.avatars;
    }

    /** Product codes and collection ids are short ASCII keys. */
    public static boolean isValidCode(String code) {
        if (code == null || code.isEmpty() || code.length() > MAX_CODE_LENGTH) return false;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (!(Character.isLetterOrDigit(c) && c < 128) && c != '_' && c != '-' && c != '.' && c != ':') {
                return false;
            }
        }
        return true;
    }

    /** The collector wallet of a user: the only wallet the hub shows. */
    public static String walletOf(int userId) {
        return "hotel-" + userId;
    }

    // ---------------------------------------------------------------- reading

    /** Every product, in {@code sort_order}. */
    public Map<String, Product> products() throws SQLException {
        Map<String, Product> products = new LinkedHashMap<>();
        try (Connection connection = this.dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet set = statement.executeQuery(
                        "SELECT * FROM collectible_products ORDER BY sort_order, product_code")) {
            while (set.next()) {
                Product product = readProduct(set);
                products.put(product.productCode(), product);
            }
        }
        return products;
    }

    public Optional<Product> product(String productCode) throws SQLException {
        if (!isValidCode(productCode)) return Optional.empty();
        try (Connection connection = this.dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT * FROM collectible_products WHERE product_code = ?")) {
            statement.setString(1, productCode);
            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? Optional.of(readProduct(set)) : Optional.empty();
            }
        }
    }

    /** The guild shop: products for sale, in {@code sort_order}. */
    public List<Product> shopOffers() throws SQLException {
        List<Product> offers = new ArrayList<>();
        for (Product product : this.products().values()) {
            if (product.shopEnabled() && product.emeraldPrice() > 0) offers.add(product);
        }
        return offers;
    }

    /** Enabled sets, in {@code sort_order}. */
    public List<Collection> collections() throws SQLException {
        List<Collection> collections = new ArrayList<>();
        try (Connection connection = this.dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet set = statement.executeQuery(
                        "SELECT * FROM collectible_collections WHERE enabled = 1 ORDER BY sort_order, collection_id")) {
            while (set.next()) {
                collections.add(new Collection(
                        set.getString("collection_id"),
                        set.getString("name"),
                        set.getInt("completion_xp"),
                        set.getString("reward_product_code"),
                        set.getString("bonus_product_code"),
                        set.getInt("bonus_start"),
                        set.getInt("bonus_end")));
            }
        }
        return collections;
    }

    /** The tokens a user owns, oldest first. */
    public List<Owned> owned(int userId) throws SQLException {
        List<Owned> owned = new ArrayList<>();
        try (Connection connection = this.dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT asset_id, user_id, product_code, serial FROM users_collectibles WHERE user_id = ? ORDER BY asset_id")) {
            statement.setInt(1, userId);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    owned.add(new Owned(
                            set.getLong("asset_id"),
                            set.getInt("user_id"),
                            set.getString("product_code"),
                            set.getInt("serial")));
                }
            }
        }
        return owned;
    }

    public static Map<String, Integer> counts(List<Owned> owned) {
        Map<String, Integer> counts = new HashMap<>();
        for (Owned token : owned) counts.merge(token.productCode(), 1, Integer::sum);
        return counts;
    }

    /** How often the user made each claim, by claim key. */
    public Map<String, Integer> claimed(int userId) throws SQLException {
        Map<String, Integer> claimed = new HashMap<>();
        try (Connection connection = this.dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT claim_key, claimed_amount FROM users_collectible_claims WHERE user_id = ?")) {
            statement.setInt(1, userId);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) claimed.put(set.getString("claim_key"), set.getInt("claimed_amount"));
            }
        }
        return claimed;
    }

    /** The user's sets as the collections tab shows them. */
    public List<CollectionState> collectionStates(int userId, int now) throws SQLException {
        Map<String, Product> products = this.products();
        Map<String, Integer> counts = counts(this.owned(userId));
        Map<String, Integer> claimed = this.claimed(userId);
        List<CollectionState> states = new ArrayList<>();
        for (Collection collection : this.collections()) {
            states.add(CollectorProgress.state(collection, products, counts, claimed, now));
        }
        return states;
    }

    /** The collector XP and level, raising the stored high score when it was beaten. */
    public CollectorScore collectorScore(int userId, int xpPerLevel) throws SQLException {
        int score = CollectorProgress.score(counts(this.owned(userId)), this.products(), this.collections());
        try (Connection connection = this.dataSource.getConnection()) {
            ensureStats(connection, userId);
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE users_collector_stats SET highest_score = GREATEST(highest_score, ?) WHERE user_id = ?")) {
                update.setInt(1, score);
                update.setInt(2, userId);
                update.executeUpdate();
            }
            int highest = score;
            try (PreparedStatement select =
                    connection.prepareStatement("SELECT highest_score FROM users_collector_stats WHERE user_id = ?")) {
                select.setInt(1, userId);
                try (ResultSet set = select.executeQuery()) {
                    if (set.next()) highest = Math.max(score, set.getInt(1));
                }
            }
            return new CollectorScore(score, highest, CollectorProgress.level(score, xpPerLevel));
        }
    }

    // ---------------------------------------------------------------- minting

    /**
     * Mints the next serial of a product to a user. Empty when the product does not exist or its mint
     * limit is reached. The serial and the token are written in one transaction.
     */
    public Optional<Owned> mint(int userId, String productCode, String source, int now) throws SQLException {
        if (!isValidCode(productCode)) return Optional.empty();
        try (Connection connection = this.dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                Optional<Owned> owned = mint(connection, userId, productCode, source, now);
                if (owned.isPresent()) connection.commit();
                else connection.rollback();
                return owned;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    private static Optional<Owned> mint(Connection connection, int userId, String productCode, String source, int now)
            throws SQLException {
        int serial;
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT mint_limit, minted_count FROM collectible_products WHERE product_code = ? FOR UPDATE")) {
            select.setString(1, productCode);
            try (ResultSet set = select.executeQuery()) {
                if (!set.next()) return Optional.empty();
                int limit = set.getInt("mint_limit");
                int minted = set.getInt("minted_count");
                if (limit >= 0 && minted >= limit) return Optional.empty();
                serial = minted + 1;
            }
        }
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE collectible_products SET minted_count = ? WHERE product_code = ?")) {
            update.setInt(1, serial);
            update.setString(2, productCode);
            update.executeUpdate();
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO users_collectibles (user_id, product_code, serial, source, created_at) VALUES (?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            insert.setInt(1, userId);
            insert.setString(2, productCode);
            insert.setInt(3, serial);
            insert.setString(4, source.length() > 16 ? source.substring(0, 16) : source);
            insert.setInt(5, now);
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("users_collectibles insert returned no asset id");
                return Optional.of(new Owned(keys.getLong(1), userId, productCode, serial));
            }
        }
    }

    // ---------------------------------------------------------------- claims

    /**
     * Claims a set's completion reward ({@code bonus} false) or its bonus ({@code bonus} true): once per
     * user, only for a complete set, the bonus only inside its window. Empty when nothing was given.
     */
    public Optional<Owned> claimCollection(int userId, String collectionId, boolean bonus, int now)
            throws SQLException {
        if (!isValidCode(collectionId)) return Optional.empty();
        CollectionState state = null;
        for (CollectionState candidate : this.collectionStates(userId, now)) {
            if (candidate.collection().collectionId().equals(collectionId)) state = candidate;
        }
        if (state == null) return Optional.empty();

        Product product = bonus ? state.bonusProduct() : state.rewardProduct();
        CollectibleData.Claim claim = bonus ? state.bonusClaim() : state.rewardClaim();
        if (product == null || claim == null || claim.status() != CollectibleData.CLAIM_AVAILABLE) {
            return Optional.empty();
        }
        if (!this.recordClaim(userId, claim.claimId(), 1, now)) return Optional.empty();

        Optional<Owned> owned = this.mint(userId, product.productCode(), bonus ? "bonus" : "reward", now);
        if (owned.isEmpty()) this.unrecordClaim(userId, claim.claimId());
        return owned;
    }

    /** The level rewards a user can still claim. */
    public List<OpenReward> openRewards(int userId, int level, int now) throws SQLException {
        Map<String, Product> products = this.products();
        Map<String, Integer> claimed = this.claimed(userId);
        List<OpenReward> open = new ArrayList<>();
        for (LevelReward reward : this.levelRewards()) {
            if (!CollectorProgress.levelRewardOpen(reward, level, now)) continue;
            Product product = products.get(reward.productCode());
            int amount = claimed.getOrDefault(reward.claimKey(), 0);
            if (product == null || amount >= reward.claimLimit()) continue;
            open.add(new OpenReward(reward, product, amount));
        }
        return open;
    }

    /** Claims every open level reward as often as it can still be claimed. */
    public List<Owned> claimLevelRewards(int userId, int level, int now) throws SQLException {
        List<Owned> minted = new ArrayList<>();
        for (OpenReward open : this.openRewards(userId, level, now)) {
            for (int i = open.claimedAmount(); i < open.reward().claimLimit(); i++) {
                if (!this.recordClaim(
                        userId, open.reward().claimKey(), open.reward().claimLimit(), now)) break;
                Optional<Owned> owned = this.mint(userId, open.product().productCode(), "level", now);
                if (owned.isEmpty()) {
                    this.unrecordClaim(userId, open.reward().claimKey());
                    break;
                }
                minted.add(owned.get());
            }
        }
        return minted;
    }

    public List<LevelReward> levelRewards() throws SQLException {
        List<LevelReward> rewards = new ArrayList<>();
        try (Connection connection = this.dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet set = statement.executeQuery(
                        "SELECT * FROM collectible_level_rewards WHERE enabled = 1 ORDER BY min_level, id")) {
            while (set.next()) {
                rewards.add(new LevelReward(
                        set.getInt("id"),
                        set.getInt("min_level"),
                        set.getString("product_code"),
                        Math.max(1, set.getInt("claim_limit")),
                        set.getInt("valid_from"),
                        set.getInt("valid_to")));
            }
        }
        return rewards;
    }

    /**
     * Adds one to a claim's counter, staying below {@code limit}. True only for the call that counted it,
     * so racing claims give the product once.
     */
    public boolean recordClaim(int userId, String claimKey, int limit, int now) throws SQLException {
        try (Connection connection = this.dataSource.getConnection()) {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO users_collectible_claims (user_id, claim_key, claimed_amount, claimed_at) VALUES (?, ?, 1, ?)")) {
                insert.setInt(1, userId);
                insert.setString(2, claimKey);
                insert.setInt(3, now);
                return insert.executeUpdate() == 1;
            } catch (SQLIntegrityConstraintViolationException duplicate) {
                // counted before: fall through to the guarded increment
            } catch (SQLException exception) {
                if (!"23000".equals(exception.getSQLState())) throw exception;
            }
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE users_collectible_claims SET claimed_amount = claimed_amount + 1, claimed_at = ? "
                            + "WHERE user_id = ? AND claim_key = ? AND claimed_amount < ?")) {
                update.setInt(1, now);
                update.setInt(2, userId);
                update.setString(3, claimKey);
                update.setInt(4, limit);
                return update.executeUpdate() == 1;
            }
        }
    }

    private void unrecordClaim(int userId, String claimKey) throws SQLException {
        try (Connection connection = this.dataSource.getConnection();
                PreparedStatement update = connection.prepareStatement(
                        "UPDATE users_collectible_claims SET claimed_amount = claimed_amount - 1 "
                                + "WHERE user_id = ? AND claim_key = ? AND claimed_amount > 0")) {
            update.setInt(1, userId);
            update.setString(2, claimKey);
            update.executeUpdate();
        }
    }

    // ---------------------------------------------------------------- transfer

    /** Moves every token of {@code fromUserId} to {@code toUserId} and returns what moved. */
    public List<Owned> transferAll(int fromUserId, int toUserId) throws SQLException {
        if (fromUserId == toUserId) return List.of();
        List<Owned> moved = new ArrayList<>();
        try (Connection connection = this.dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT asset_id, product_code, serial FROM users_collectibles WHERE user_id = ? ORDER BY asset_id FOR UPDATE")) {
                    select.setInt(1, fromUserId);
                    try (ResultSet set = select.executeQuery()) {
                        while (set.next()) {
                            moved.add(new Owned(
                                    set.getLong("asset_id"),
                                    toUserId,
                                    set.getString("product_code"),
                                    set.getInt("serial")));
                        }
                    }
                }
                try (PreparedStatement update =
                        connection.prepareStatement("UPDATE users_collectibles SET user_id = ? WHERE user_id = ?")) {
                    update.setInt(1, toUserId);
                    update.setInt(2, fromUserId);
                    update.executeUpdate();
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
        return moved;
    }

    // ---------------------------------------------------------------- reward boxes

    /** The prizes of a reward box furni ({@code items_base} id); empty when it is no reward box. */
    public List<BoxPrize> boxPrizes(int itemBaseId) throws SQLException {
        List<BoxPrize> prizes = new ArrayList<>();
        try (Connection connection = this.dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT product_code, weight FROM collectible_reward_boxes WHERE item_id = ? ORDER BY product_code")) {
            statement.setInt(1, itemBaseId);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) prizes.add(new BoxPrize(set.getString("product_code"), set.getInt("weight")));
            }
        }
        return prizes;
    }

    /**
     * Draws a prize of the box and mints it, trying the other prizes when one is sold out. Empty when the
     * box has nothing left to give.
     */
    public Optional<Owned> openBox(int userId, int itemBaseId, int now) throws SQLException {
        List<BoxPrize> prizes = new ArrayList<>(this.boxPrizes(itemBaseId));
        while (!prizes.isEmpty()) {
            BoxPrize prize = CollectorProgress.draw(prizes, this.random);
            if (prize == null) return Optional.empty();
            Optional<Owned> owned = this.mint(userId, prize.productCode(), "box", now);
            if (owned.isPresent()) return owned;
            prizes.remove(prize);
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------- relics

    public List<Mintable> mintables() throws SQLException {
        List<Mintable> mintables = new ArrayList<>();
        try (Connection connection = this.dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet set = statement.executeQuery(
                        "SELECT * FROM collectible_mintables WHERE enabled = 1 ORDER BY item_id")) {
            while (set.next()) {
                mintables.add(new Mintable(
                        set.getInt("item_id"),
                        set.getString("product_code"),
                        Math.max(0, set.getInt("price")),
                        set.getInt("start_time"),
                        set.getInt("end_time")));
            }
        }
        return mintables;
    }

    public Optional<Mintable> mintable(int itemBaseId) throws SQLException {
        return this.mintables().stream()
                .filter(m -> m.baseItemId() == itemBaseId)
                .findFirst();
    }

    /**
     * Whether a relic can be collected now. Like AIR ({@code MintInventoryListTab.isMintPeriodExpired}), a relic
     * without an end ({@code end_time} 0) counts as over: the client disables its collect button.
     */
    public static boolean mintOpen(Mintable mintable, int now) {
        if (mintable.startTime() > 0 && now < mintable.startTime()) return false;
        return mintable.endTime() > 0 && now <= mintable.endTime();
    }

    public List<MintTokenOffer> mintTokenOffers() throws SQLException {
        List<MintTokenOffer> offers = new ArrayList<>();
        try (Connection connection = this.dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet set = statement.executeQuery(
                        "SELECT * FROM collectible_mint_token_offers WHERE enabled = 1 ORDER BY offer_id")) {
            while (set.next()) {
                offers.add(new MintTokenOffer(
                        set.getInt("offer_id"),
                        set.getString("product_code"),
                        Math.max(0, set.getInt("silver_price")),
                        Math.max(0, set.getInt("amount"))));
            }
        }
        return offers;
    }

    public Optional<MintTokenOffer> mintTokenOffer(int offerId) throws SQLException {
        return this.mintTokenOffers().stream()
                .filter(o -> o.offerId() == offerId)
                .findFirst();
    }

    public int mintTokens(int userId) throws SQLException {
        try (Connection connection = this.dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT mint_tokens FROM users_collector_stats WHERE user_id = ?")) {
            statement.setInt(1, userId);
            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? set.getInt(1) : 0;
            }
        }
    }

    /** Adds (or, negative, takes) stamps; false when the user has too few to take. */
    public boolean addMintTokens(int userId, int delta) throws SQLException {
        try (Connection connection = this.dataSource.getConnection()) {
            ensureStats(connection, userId);
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE users_collector_stats SET mint_tokens = mint_tokens + ? WHERE user_id = ? AND mint_tokens + ? >= 0")) {
                update.setInt(1, delta);
                update.setInt(2, userId);
                update.setInt(3, delta);
                return update.executeUpdate() == 1;
            }
        }
    }

    /**
     * Turns a furni into its relic: takes the price in stamps and mints the product. The caller removes the
     * furni once this returns a token. Empty (stamps kept) when the furni is no open relic, the user has too
     * few stamps or the product is sold out.
     */
    public Optional<Owned> mintRelic(int userId, int itemBaseId, int now) throws SQLException {
        Optional<Mintable> mintable = this.mintable(itemBaseId);
        if (mintable.isEmpty() || !mintOpen(mintable.get(), now)) return Optional.empty();
        int price = mintable.get().price();
        if (price > 0 && !this.addMintTokens(userId, -price)) return Optional.empty();
        Optional<Owned> owned = this.mint(userId, mintable.get().productCode(), "mint", now);
        if (owned.isEmpty() && price > 0) this.addMintTokens(userId, price);
        return owned;
    }

    // ---------------------------------------------------------------- helpers

    private static void ensureStats(Connection connection, int userId) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT IGNORE INTO users_collector_stats (user_id, highest_score, mint_tokens) VALUES (?, 0, 0)")) {
            insert.setInt(1, userId);
            insert.executeUpdate();
        }
    }

    private static Product readProduct(ResultSet set) throws SQLException {
        return new Product(
                set.getString("product_code"),
                set.getInt("product_type"),
                set.getString("item_type_id"),
                set.getInt("score"),
                set.getString("rarity"),
                set.getString("pet_figure"),
                parseIds(set.getString("figure_set_ids")),
                set.getString("collection_id"),
                set.getInt("emerald_price"),
                set.getBoolean("shop_enabled"),
                set.getInt("mint_limit"),
                set.getInt("minted_count"));
    }

    static List<Integer> parseIds(String value) {
        List<Integer> ids = new ArrayList<>();
        if (value == null || value.isBlank()) return ids;
        for (String part : value.split("[,;]")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            try {
                ids.add(Integer.parseInt(trimmed));
            } catch (NumberFormatException ignored) {
                // a malformed id is skipped, the others still preview
            }
        }
        return ids;
    }
}
