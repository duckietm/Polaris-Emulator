package com.eu.habbo.habbohotel.catalog.marketplace;

import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.economy.EconomyLedger;
import com.eu.habbo.habbohotel.economy.EconomyMutationResult;
import com.eu.habbo.habbohotel.economy.EconomyOperation;
import com.eu.habbo.habbohotel.economy.EconomyOperationId;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.sql.DataSource;

/**
 * Marketplace selling tokens, as the official client expects them ({@code MarketplaceModel.proceedOfferMaking}
 * and the {@code buy_marketplace_tokens} window).
 *
 * <p>Every offer costs one token. A user without tokens is answered 4 by the can-make-offer check and the
 * client offers a batch of {@link Settings#amount()} tokens for {@link Settings#price()} credits (packet 1866).
 * The whole system is off unless the hotel sets {@code marketplace.tokens.enabled}; offers are then free, as
 * before. Balances live in {@code users_marketplace_tokens} and are only changed with conditional SQL, so two
 * concurrent offers can never spend the same token.
 */
public final class MarketplaceTokenService {
    public static final String ENABLED_SETTING = "marketplace.tokens.enabled";
    public static final String PRICE_SETTING = "marketplace.tokens.price";
    public static final String AMOUNT_SETTING = "marketplace.tokens.amount";

    public static final int DEFAULT_PRICE = 1;
    public static final int DEFAULT_AMOUNT = 5;

    private static final String READ_TOKENS = "SELECT tokens FROM users_marketplace_tokens WHERE user_id = ?";
    private static final String SPEND_TOKENS =
            "UPDATE users_marketplace_tokens SET tokens = tokens - ? WHERE user_id = ? AND tokens >= ?";
    private static final String ADD_TOKENS = """
            INSERT INTO users_marketplace_tokens (user_id, tokens) VALUES (?, ?)
            ON DUPLICATE KEY UPDATE tokens = LEAST(2147483647, tokens + VALUES(tokens))
            """;

    private static final AtomicReference<MarketplaceTokenService> INSTALLED = new AtomicReference<>();

    /** The hotel's token settings: whether offers cost tokens, and what one batch costs and holds. */
    public record Settings(boolean enabled, int price, int amount) {
        public Settings {
            price = Math.max(0, price);
            amount = Math.max(1, amount);
        }

        public static Settings from(ConfigurationManager configuration) {
            return new Settings(
                    configuration.getBoolean(ENABLED_SETTING, false),
                    configuration.getInt(PRICE_SETTING, DEFAULT_PRICE),
                    configuration.getInt(AMOUNT_SETTING, DEFAULT_AMOUNT));
        }
    }

    /** The result of buying a batch: the credits balance after the charge and the tokens now held. */
    public record Purchase(int creditsAfter, int tokens) {}

    private final DataSource dataSource;
    private final Supplier<Settings> settings;

    public MarketplaceTokenService(DataSource dataSource, Supplier<Settings> settings) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public MarketplaceTokenService(DataSource dataSource, ConfigurationManager configuration) {
        this(dataSource, () -> Settings.from(configuration));
    }

    public static void install(MarketplaceTokenService service) {
        INSTALLED.set(Objects.requireNonNull(service, "service"));
    }

    /** The installed service, or null before start-up finished (offers are then free). */
    public static MarketplaceTokenService installed() {
        return INSTALLED.get();
    }

    /** The settings of the installed service; tokens off, with the official batch, when none is installed. */
    public static Settings currentSettings() {
        MarketplaceTokenService service = installed();
        return service == null ? new Settings(false, DEFAULT_PRICE, DEFAULT_AMOUNT) : service.settings();
    }

    /** True when the installed service makes offers cost tokens. */
    public static boolean tokensRequired() {
        return currentSettings().enabled();
    }

    public Settings settings() {
        return this.settings.get();
    }

    public int tokens(int userId) throws SQLException {
        try (Connection connection = this.dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(READ_TOKENS)) {
            statement.setInt(1, userId);
            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? Math.max(0, set.getInt("tokens")) : 0;
            }
        }
    }

    /** Takes {@code amount} tokens, all or nothing; false when the user holds fewer. */
    public boolean spend(int userId, int amount) throws SQLException {
        if (amount <= 0) return true;
        try (Connection connection = this.dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(SPEND_TOKENS)) {
            statement.setInt(1, amount);
            statement.setInt(2, userId);
            statement.setInt(3, amount);
            return statement.executeUpdate() > 0;
        }
    }

    /** Gives tokens back, for an offer that was paid for but could not be posted. */
    public void refund(int userId, int amount) throws SQLException {
        if (amount <= 0) return;
        try (Connection connection = this.dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(ADD_TOKENS)) {
            statement.setInt(1, userId);
            statement.setInt(2, amount);
            statement.executeUpdate();
        }
    }

    /**
     * Charges one batch's price in credits and adds its tokens, in one transaction: either both happen or
     * neither. Throws {@link IllegalArgumentException} when the user cannot pay.
     */
    public Purchase buy(int userId) throws SQLException {
        Settings current = this.settings();
        try (Connection connection = this.dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int creditsAfter = -1;
                if (current.price() > 0) {
                    EconomyMutationResult charge = EconomyLedger.apply(
                            connection,
                            new EconomyOperation(
                                    EconomyOperationId.create("marketplace-tokens:" + userId),
                                    userId,
                                    userId,
                                    "marketplace_tokens_purchase",
                                    "catalog.marketplace.tokens",
                                    EconomyLedger.CREDITS,
                                    -current.price(),
                                    null,
                                    "tokens=" + current.amount()));
                    creditsAfter = charge.balanceAfter();
                }

                try (PreparedStatement statement = connection.prepareStatement(ADD_TOKENS)) {
                    statement.setInt(1, userId);
                    statement.setInt(2, current.amount());
                    statement.executeUpdate();
                }

                int tokens;
                try (PreparedStatement statement = connection.prepareStatement(READ_TOKENS)) {
                    statement.setInt(1, userId);
                    try (ResultSet set = statement.executeQuery()) {
                        tokens = set.next() ? set.getInt("tokens") : 0;
                    }
                }

                connection.commit();
                return new Purchase(creditsAfter, tokens);
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            }
        }
    }
}
