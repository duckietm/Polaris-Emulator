package com.eu.habbo.habbohotel.users.customization;

import com.eu.habbo.habbohotel.economy.EconomyLedger;
import com.eu.habbo.habbohotel.economy.EconomyOperation;
import com.eu.habbo.habbohotel.economy.EconomyOperationId;
import com.eu.habbo.habbohotel.modtool.WordFilterWord;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.LedgerWalletMutation;
import com.eu.habbo.habbohotel.users.UserNickIcon;
import com.eu.habbo.habbohotel.users.UserPrefix;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private final UserCustomizationRepository repository;
    private final Collection<WordFilterWord> filteredWords;

    public UserCustomizationPurchaseService(
            UserCustomizationRepository repository, Collection<WordFilterWord> filteredWords) {
        this.repository = repository;
        this.filteredWords = List.copyOf(filteredWords);
    }

    public PurchaseResult purchaseCustomPrefix(Habbo habbo, CustomPrefixRequest request) {
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

        if (maxPrefixes > 0
                && habbo.getInventory().getPrefixesComponent().getPrefixes().size() >= maxPrefixes) {
            return PurchaseResult.failure("You already own the maximum number of prefixes (" + maxPrefixes + ").");
        }

        String text = request.text().trim();
        if (text.isEmpty() || text.length() > maxLength) {
            return PurchaseResult.failure("Prefix text is invalid or too long (max " + maxLength + " characters).");
        }
        if (containsControlChars(text)) {
            return PurchaseResult.failure("Prefix text contains invalid characters.");
        }
        if (this.containsFilteredWord(text)) {
            return PurchaseResult.failure("This prefix contains a blocked word.");
        }

        String[] colorParts = request.color().split(",");
        if (colorParts.length > text.length()) {
            return PurchaseResult.failure("Invalid color format.");
        }
        for (String part : colorParts) {
            if (!part.matches("^#[0-9A-Fa-f]{6}$")) {
                return PurchaseResult.failure("Invalid color format.");
            }
        }
        if (habbo.getHabboInfo().getRank().getId() < minRank) {
            return PurchaseResult.failure("Your rank is too low to purchase prefixes.");
        }

        String icon = normalizeNullable(request.icon());
        if (!isValidIcon(icon)) {
            return PurchaseResult.failure("Invalid prefix icon.");
        }
        String effect = normalizeNullable(request.effect()).toLowerCase();
        if (!isAllowedEffect(effect)) {
            return PurchaseResult.failure("Invalid prefix effect.");
        }
        String font = normalizeNullable(request.font()).toLowerCase();
        if (!isAllowedFont(font)) {
            return PurchaseResult.failure("Invalid font format.");
        }

        int totalPriceCredits = priceCredits + (!font.isEmpty() ? fontPriceCredits : 0);
        int totalPricePointsSameType = pricePoints
                + (fontPricePoints > 0 && fontPointsType == pointsType && !font.isEmpty() ? fontPricePoints : 0);
        if (totalPriceCredits > 0 && habbo.getHabboInfo().getCredits() < totalPriceCredits) {
            return PurchaseResult.failure("Not enough credits.");
        }
        if (totalPricePointsSameType > 0
                && habbo.getHabboInfo().getCurrencyAmount(pointsType) < totalPricePointsSameType) {
            return PurchaseResult.failure("Not enough points.");
        }
        if (!font.isEmpty()
                && fontPricePoints > 0
                && fontPointsType != pointsType
                && habbo.getHabboInfo().getCurrencyAmount(fontPointsType) < fontPricePoints) {
            return PurchaseResult.failure("Not enough points.");
        }

        String operationId = EconomyOperationId.create(
                "prefix-purchase:" + habbo.getHabboInfo().getId());
        List<EconomyOperation> operations = new ArrayList<>(3);
        addDebit(operations, operationId + ":credits", operationId, habbo, EconomyLedger.CREDITS, totalPriceCredits);
        addDebit(operations, operationId + ":points", operationId, habbo, pointsType, totalPricePointsSameType);
        if (!font.isEmpty() && fontPricePoints > 0 && fontPointsType != pointsType) {
            addDebit(operations, operationId + ":font-points", operationId, habbo, fontPointsType, fontPricePoints);
        }
        try {
            if (!operations.isEmpty()) {
                LedgerWalletMutation.executeBatch(habbo, operations);
            }
        } catch (IllegalArgumentException exception) {
            return PurchaseResult.failure("Not enough currency.");
        } catch (SQLException exception) {
            LOGGER.error(
                    "Unable to debit prefix purchase for user {}",
                    habbo.getHabboInfo().getId(),
                    exception);
            return PurchaseResult.failure("Unable to complete the purchase.");
        }

        int storedPoints = totalPricePointsSameType;
        int storedPointsType =
                storedPoints > 0 ? pointsType : (!font.isEmpty() && fontPricePoints > 0 ? fontPointsType : pointsType);
        UserPrefix prefix = new UserPrefix(
                habbo.getHabboInfo().getId(),
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
        boolean currencyChanged = totalPricePointsSameType > 0
                || (!font.isEmpty() && fontPricePoints > 0 && fontPointsType != pointsType);
        return PurchaseResult.prefix(prefix, totalPriceCredits > 0, currencyChanged);
    }

    public PurchaseResult purchaseCatalogPrefix(Habbo habbo, int catalogPrefixId) throws SQLException {
        if (habbo.getInventory().getPrefixesComponent().getPrefixByCatalogId(catalogPrefixId) != null) {
            return PurchaseResult.refresh();
        }
        var offer = this.repository.findCatalogPrefix(catalogPrefixId).orElse(null);
        if (offer == null) {
            return PurchaseResult.unavailable();
        }
        if (offer.points() > 0 && habbo.getHabboInfo().getCurrencyAmount(offer.pointsType()) < offer.points()) {
            return PurchaseResult.failure("Not enough points.");
        }
        if (offer.points() > 0) {
            try {
                LedgerWalletMutation.execute(
                        habbo,
                        debit(
                                EconomyOperationId.create(
                                        "catalog-prefix:" + habbo.getHabboInfo().getId() + ":" + catalogPrefixId),
                                habbo,
                                offer.pointsType(),
                                offer.points(),
                                "catalog_prefix_purchase",
                                "inventory.catalog_prefix.purchase",
                                "catalogPrefixId=" + catalogPrefixId));
            } catch (IllegalArgumentException exception) {
                return PurchaseResult.failure("Not enough points.");
            }
        }
        UserPrefix prefix = new UserPrefix(
                habbo.getHabboInfo().getId(),
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
        return PurchaseResult.prefix(prefix, false, offer.points() > 0);
    }

    public PurchaseResult purchaseNickIcon(Habbo habbo, String iconKey) throws SQLException {
        if (habbo.getInventory().getNickIconsComponent().getNickIconByKey(iconKey) != null) {
            return PurchaseResult.failure("You already own this nick icon.");
        }
        var offer = this.repository.findNickIcon(iconKey).orElse(null);
        if (offer == null || !offer.enabled()) {
            return PurchaseResult.failure("This nick icon is not available.");
        }
        if (offer.points() > 0 && habbo.getHabboInfo().getCurrencyAmount(offer.pointsType()) < offer.points()) {
            return PurchaseResult.failure("Not enough points.");
        }
        if (offer.points() > 0) {
            try {
                LedgerWalletMutation.execute(
                        habbo,
                        debit(
                                EconomyOperationId.create(
                                        "nick-icon:" + habbo.getHabboInfo().getId() + ":" + iconKey),
                                habbo,
                                offer.pointsType(),
                                offer.points(),
                                "nick_icon_purchase",
                                "inventory.nick_icon.purchase",
                                iconKey));
            } catch (IllegalArgumentException exception) {
                return PurchaseResult.failure("Not enough points.");
            }
        }
        UserNickIcon nickIcon = new UserNickIcon(habbo.getHabboInfo().getId(), iconKey);
        return PurchaseResult.nickIcon(nickIcon, offer.points() > 0);
    }

    public static boolean containsControlChars(String text) {
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character < 0x20 || character == 0x7F) {
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

    private boolean containsFilteredWord(String text) {
        for (WordFilterWord word : this.filteredWords) {
            if (word.key != null && !word.key.isEmpty() && Strings.CI.contains(text, word.key)) {
                return true;
            }
        }
        return false;
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
            List<EconomyOperation> operations, String id, String parentId, Habbo habbo, int currency, int amount) {
        if (amount > 0) {
            operations.add(new EconomyOperation(
                    id,
                    habbo.getHabboInfo().getId(),
                    habbo.getHabboInfo().getId(),
                    "prefix_purchase",
                    "inventory.prefix.purchase",
                    currency,
                    -amount,
                    null,
                    parentId));
        }
    }

    private static EconomyOperation debit(
            String operationId,
            Habbo habbo,
            int currency,
            int amount,
            String operation,
            String reason,
            String metadata) {
        return new EconomyOperation(
                operationId,
                habbo.getHabboInfo().getId(),
                habbo.getHabboInfo().getId(),
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
