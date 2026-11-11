package polaris.hotel.users.customization;

import java.sql.SQLException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.hotel.economy.EconomyLedger;
import polaris.hotel.economy.EconomyOperation;
import polaris.hotel.economy.EconomyOperationId;
import polaris.hotel.modtool.WordFilterWord;
import polaris.hotel.users.LedgerWalletMutation;
import polaris.hotel.users.User;
import polaris.hotel.users.UserNickIcon;
import polaris.hotel.users.UserPrefix;

public final class UserCustomizationPurchaseService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserCustomizationPurchaseService.class);
    private static final String[] ALLOWED_FONTS = {"", "pixel", "cherry", "vampiro"};
    private static final String[] ALLOWED_EFFECTS = {
        "",
        "glow",
        "shadow",
        "italic",
        "outline",
        "underline",
        "pulse",
        "bounce",
        "wave",
        "shake",
        "discord-neon",
        "cartoon",
        "toon",
        "pop",
        "bold-glow",
        "rainbow",
        "frost",
        "gold",
        "glitch",
        "fire",
        "matrix",
        "sparkle"
    };
    private static final int MAX_ICON_LENGTH = 16;
    /** Widths of user_prefixes.text and user_prefixes.color. */
    private static final int TEXT_COLUMN_LENGTH = 50;

    private static final int COLOR_COLUMN_LENGTH = 255;

    private final UserCustomizationRepository repository;
    private final Collection<WordFilterWord> filteredWords;

    public UserCustomizationPurchaseService(
            UserCustomizationRepository repository, Collection<WordFilterWord> filteredWords) {
        this.repository = repository;
        this.filteredWords = List.copyOf(filteredWords);
    }

    public PurchaseResult purchaseCustomPrefix(User user, CustomPrefixRequest request) {
        Map<String, Integer> settings = this.repository.loadPrefixSettings();
        int maxLength = setting(settings, "max_length", 15);
        int minRank = setting(settings, "min_rank_to_buy", 1);
        int priceCredits = setting(settings, "price_credits", 5);
        int pricePoints = setting(settings, "price_points", 0);
        int pointsType = setting(settings, "points_type", 0);
        int fontPriceCredits = setting(settings, "font_price_credits", 10);
        int fontPricePoints = setting(settings, "font_price_points", 0);
        int fontPointsType = setting(settings, "font_points_type", pointsType);
        int maxPrefixes = setting(settings, "max_prefixes", 60);

        String limitFailure = prefixLimitFailure(user, maxPrefixes);
        if (limitFailure != null) {
            return PurchaseResult.failure(limitFailure);
        }

        String text = request.text().trim();
        String icon = normalizeNullable(request.icon());
        String effect = normalizeNullable(request.effect()).toLowerCase();
        String font = normalizeNullable(request.font()).toLowerCase();
        String styleFailure = this.styleFailure(text, request.color(), icon, effect, font, maxLength, true);
        if (styleFailure != null) {
            return PurchaseResult.failure(styleFailure);
        }
        if (user.getUserInfo().getRank().getId() < minRank) {
            return PurchaseResult.failure("Your rank is too low to purchase prefixes.");
        }

        int totalPriceCredits = priceCredits + (!font.isEmpty() ? fontPriceCredits : 0);
        int totalPricePointsSameType = pricePoints
                + (fontPricePoints > 0 && fontPointsType == pointsType && !font.isEmpty() ? fontPricePoints : 0);
        if (totalPriceCredits > 0 && user.getUserInfo().getCredits() < totalPriceCredits) {
            return PurchaseResult.failure("Not enough credits.");
        }
        if (totalPricePointsSameType > 0
                && user.getUserInfo().getCurrencyAmount(pointsType) < totalPricePointsSameType) {
            return PurchaseResult.failure("Not enough points.");
        }
        if (!font.isEmpty()
                && fontPricePoints > 0
                && fontPointsType != pointsType
                && user.getUserInfo().getCurrencyAmount(fontPointsType) < fontPricePoints) {
            return PurchaseResult.failure("Not enough points.");
        }

        int storedPoints = totalPricePointsSameType;
        int storedPointsType =
                storedPoints > 0 ? pointsType : (!font.isEmpty() && fontPricePoints > 0 ? fontPointsType : pointsType);
        UserPrefix prefix = new UserPrefix(
                user.getUserInfo().getId(),
                text,
                request.color(),
                icon,
                effect,
                font,
                0,
                text,
                storedPoints,
                storedPointsType,
                true);
        // The row is stored before the currency is taken: a failed insert costs the player nothing, and a failed
        // debit removes the row again.
        String storeFailure = this.store(prefix);
        if (storeFailure != null) {
            return PurchaseResult.failure(storeFailure);
        }

        String operationId = EconomyOperationId.create(
                "prefix-purchase:" + user.getUserInfo().getId());
        List<EconomyOperation> operations = new ArrayList<>(3);
        addDebit(operations, operationId + ":credits", operationId, user, EconomyLedger.CREDITS, totalPriceCredits);
        addDebit(operations, operationId + ":points", operationId, user, pointsType, totalPricePointsSameType);
        if (!font.isEmpty() && fontPricePoints > 0 && fontPointsType != pointsType) {
            addDebit(operations, operationId + ":font-points", operationId, user, fontPointsType, fontPricePoints);
        }
        try {
            if (!operations.isEmpty()) {
                LedgerWalletMutation.executeBatch(user, operations);
            }
        } catch (IllegalArgumentException exception) {
            this.discard(prefix);
            return PurchaseResult.failure("Not enough currency.");
        } catch (SQLException exception) {
            LOGGER.error(
                    "Unable to debit prefix purchase for user {}",
                    user.getUserInfo().getId(),
                    exception);
            this.discard(prefix);
            return PurchaseResult.failure("Unable to complete the purchase.");
        }

        boolean currencyChanged = totalPricePointsSameType > 0
                || (!font.isEmpty() && fontPricePoints > 0 && fontPointsType != pointsType);
        return PurchaseResult.prefix(prefix, totalPriceCredits > 0, currencyChanged);
    }

    public PurchaseResult purchaseCatalogPrefix(User user, int catalogPrefixId) throws SQLException {
        if (user.getInventory().getPrefixesComponent().getPrefixByCatalogId(catalogPrefixId) != null) {
            return PurchaseResult.refresh();
        }
        // The catalog follows the same rank and count limits as a custom prefix.
        Map<String, Integer> settings = this.repository.loadPrefixSettings();
        String limitFailure = prefixLimitFailure(user, setting(settings, "max_prefixes", 60));
        if (limitFailure != null) {
            return PurchaseResult.failure(limitFailure);
        }
        if (user.getUserInfo().getRank().getId() < setting(settings, "min_rank_to_buy", 1)) {
            return PurchaseResult.failure("Your rank is too low to purchase prefixes.");
        }
        var offer = this.repository.findCatalogPrefix(catalogPrefixId).orElse(null);
        if (offer == null) {
            return PurchaseResult.unavailable();
        }
        if (offer.points() > 0 && user.getUserInfo().getCurrencyAmount(offer.pointsType()) < offer.points()) {
            return PurchaseResult.failure("Not enough points.");
        }
        UserPrefix prefix = new UserPrefix(
                user.getUserInfo().getId(),
                offer.text(),
                offer.color(),
                offer.icon(),
                offer.effect(),
                offer.font(),
                offer.id(),
                offer.displayName(),
                offer.points(),
                offer.pointsType(),
                false);
        String storeFailure = this.store(prefix);
        if (storeFailure != null) {
            return PurchaseResult.failure(storeFailure);
        }
        if (offer.points() > 0) {
            try {
                LedgerWalletMutation.execute(
                        user,
                        debit(
                                EconomyOperationId.create(
                                        "catalog-prefix:" + user.getUserInfo().getId() + ":" + catalogPrefixId),
                                user,
                                offer.pointsType(),
                                offer.points(),
                                "catalog_prefix_purchase",
                                "inventory.catalog_prefix.purchase",
                                "catalogPrefixId=" + catalogPrefixId));
            } catch (IllegalArgumentException exception) {
                this.discard(prefix);
                return PurchaseResult.failure("Not enough points.");
            } catch (SQLException exception) {
                LOGGER.error(
                        "Unable to debit catalog prefix {} for user {}",
                        catalogPrefixId,
                        user.getUserInfo().getId(),
                        exception);
                this.discard(prefix);
                return PurchaseResult.failure("Unable to complete the purchase.");
            }
        }
        return PurchaseResult.prefix(prefix, false, offer.points() > 0);
    }

    /**
     * The checks a prefix handed out by staff goes through: the same text, colour, icon and effect rules as a
     * purchase, and the owner's prefix limit. Returns the reason it is refused, or {@code null}.
     */
    public String grantedPrefixFailure(User target, String text, String color, String icon, String effect) {
        Map<String, Integer> settings = this.repository.loadPrefixSettings();
        String limitFailure = prefixLimitFailure(target, setting(settings, "max_prefixes", 60));
        if (limitFailure != null) {
            return limitFailure;
        }
        return this.styleFailure(
                text == null ? "" : text.trim(),
                color == null ? "" : color,
                normalizeNullable(icon),
                normalizeNullable(effect).toLowerCase(),
                "",
                setting(settings, "max_length", 15),
                false);
    }

    private String styleFailure(
            String text, String color, String icon, String effect, String font, int maxLength, boolean applyBlacklist) {
        // The setting may not ask for more than the column holds: a longer text failed the insert silently.
        int textLimit = Math.min(maxLength, TEXT_COLUMN_LENGTH);
        if (text.isEmpty() || text.length() > textLimit) {
            return "Prefix text is invalid or too long (max " + textLimit + " characters).";
        }
        if (containsControlChars(text)) {
            return "Prefix text contains invalid characters.";
        }
        if (this.containsFilteredWord(text, applyBlacklist)) {
            return "This prefix contains a blocked word.";
        }

        String[] colorParts = color.split(",");
        if (colorParts.length > text.length() || color.length() > COLOR_COLUMN_LENGTH) {
            return "Invalid color format.";
        }
        for (String part : colorParts) {
            if (!part.matches("^#[0-9A-Fa-f]{6}$")) {
                return "Invalid color format.";
            }
        }
        if (!isValidIcon(icon)) {
            return "Invalid prefix icon.";
        }
        if (!isAllowedEffect(effect)) {
            return "Invalid prefix effect.";
        }
        if (!isAllowedFont(font)) {
            return "Invalid font format.";
        }
        return null;
    }

    private static String prefixLimitFailure(User user, int maxPrefixes) {
        if (maxPrefixes > 0
                && user.getInventory().getPrefixesComponent().getPrefixes().size() >= maxPrefixes) {
            return "You already own the maximum number of prefixes (" + maxPrefixes + ").";
        }
        return null;
    }

    private String store(UserPrefix prefix) {
        try {
            this.repository.insertPrefix(prefix);
            return null;
        } catch (SQLException exception) {
            LOGGER.error("Unable to store a prefix for user {}", prefix.getUserId(), exception);
            return "Unable to complete the purchase.";
        }
    }

    private void discard(UserPrefix prefix) {
        try {
            this.repository.deletePrefix(prefix.getId(), prefix.getUserId());
        } catch (SQLException exception) {
            LOGGER.error("Unable to remove unpaid prefix {} of user {}", prefix.getId(), prefix.getUserId(), exception);
        }
    }

    public PurchaseResult purchaseNickIcon(User user, String iconKey) throws SQLException {
        if (user.getInventory().getNickIconsComponent().getNickIconByKey(iconKey) != null) {
            return PurchaseResult.failure("You already own this nick icon.");
        }
        var offer = this.repository.findNickIcon(iconKey).orElse(null);
        if (offer == null || !offer.enabled()) {
            return PurchaseResult.failure("This nick icon is not available.");
        }
        if (offer.points() > 0 && user.getUserInfo().getCurrencyAmount(offer.pointsType()) < offer.points()) {
            return PurchaseResult.failure("Not enough points.");
        }
        if (offer.points() > 0) {
            try {
                LedgerWalletMutation.execute(
                        user,
                        debit(
                                EconomyOperationId.create(
                                        "nick-icon:" + user.getUserInfo().getId() + ":" + iconKey),
                                user,
                                offer.pointsType(),
                                offer.points(),
                                "nick_icon_purchase",
                                "inventory.nick_icon.purchase",
                                iconKey));
            } catch (IllegalArgumentException exception) {
                return PurchaseResult.failure("Not enough points.");
            }
        }
        UserNickIcon nickIcon = new UserNickIcon(user.getUserInfo().getId(), iconKey);
        return PurchaseResult.nickIcon(nickIcon, offer.points() > 0);
    }

    public static boolean containsControlChars(String text) {
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            // Format characters (zero-width space, joiners, direction marks) hide a word from the filter, and '<' '>'
            // are markup in the clients that show the prefix.
            if (character < 0x20
                    || character == 0x7F
                    || Character.getType(character) == Character.FORMAT
                    || character == '<'
                    || character == '>') {
                return true;
            }
        }
        return false;
    }

    public static boolean isValidIcon(String icon) {
        if (icon.isEmpty()) {
            return true;
        }
        if (icon.length() > MAX_ICON_LENGTH) {
            return false;
        }
        for (int index = 0; index < icon.length(); index++) {
            char character = icon.charAt(index);
            if (character < 0x20 || character == 0x7F || character == '<' || character == '>') {
                return false;
            }
        }
        return true;
    }

    public static boolean isAllowedFont(String font) {
        return contains(ALLOWED_FONTS, font);
    }

    public static boolean isAllowedEffect(String effect) {
        return contains(ALLOWED_EFFECTS, effect);
    }

    private boolean containsFilteredWord(String text, boolean applyBlacklist) {
        // Compared without accents and invisible characters too: a blocked word with an accent or a zero-width
        // character in it passed the plain comparison and showed next to the name for the whole room.
        String folded = foldForFilter(text);
        for (WordFilterWord word : this.filteredWords) {
            if (word.key != null
                    && !word.key.isEmpty()
                    && (Strings.CI.contains(text, word.key) || folded.contains(foldForFilter(word.key)))) {
                return true;
            }
        }
        if (!applyBlacklist) {
            return false;
        }
        // The prefix blacklist (admin, staff, mod, owner, ...) keeps players from posing as the hotel team.
        for (String word : this.repository.loadPrefixBlacklist()) {
            if (folded.contains(foldForFilter(word))) {
                return true;
            }
        }
        return false;
    }

    /** Lower case, accents taken off, and format characters (zero-width spaces and joiners) removed. */
    static String foldForFilter(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKD)
                .replaceAll("[\\p{M}\\p{Cf}]", "")
                .toLowerCase(Locale.ROOT);
    }

    private static boolean contains(String[] values, String candidate) {
        for (String value : values) {
            if (value.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeNullable(String value) {
        return value == null ? "" : value.trim();
    }

    private static int setting(Map<String, Integer> settings, String key, int defaultValue) {
        return settings.getOrDefault(key, defaultValue);
    }

    private static void addDebit(
            List<EconomyOperation> operations, String id, String parentId, User user, int currency, int amount) {
        if (amount > 0) {
            operations.add(new EconomyOperation(
                    id,
                    user.getUserInfo().getId(),
                    user.getUserInfo().getId(),
                    "prefix_purchase",
                    "inventory.prefix.purchase",
                    currency,
                    -amount,
                    null,
                    parentId));
        }
    }

    private static EconomyOperation debit(
            String operationId, User user, int currency, int amount, String operation, String reason, String metadata) {
        return new EconomyOperation(
                operationId,
                user.getUserInfo().getId(),
                user.getUserInfo().getId(),
                operation,
                reason,
                currency,
                -amount,
                null,
                metadata);
    }

    public record CustomPrefixRequest(String text, String color, String icon, String effect, String font) {}

    public record PurchaseResult(
            Status status,
            String message,
            UserPrefix prefix,
            UserNickIcon nickIcon,
            boolean creditsChanged,
            boolean currencyChanged) {

        public static PurchaseResult failure(String message) {
            return new PurchaseResult(Status.FAILURE, message, null, null, false, false);
        }

        public static PurchaseResult unavailable() {
            return new PurchaseResult(Status.UNAVAILABLE, "", null, null, false, false);
        }

        public static PurchaseResult refresh() {
            return new PurchaseResult(Status.REFRESH, "", null, null, false, false);
        }

        public static PurchaseResult prefix(UserPrefix prefix, boolean creditsChanged, boolean currencyChanged) {
            return new PurchaseResult(Status.SUCCESS, "", prefix, null, creditsChanged, currencyChanged);
        }

        public static PurchaseResult nickIcon(UserNickIcon nickIcon, boolean currencyChanged) {
            return new PurchaseResult(Status.SUCCESS, "", null, nickIcon, false, currencyChanged);
        }
    }

    public enum Status {
        SUCCESS,
        FAILURE,
        REFRESH,
        UNAVAILABLE
    }
}
