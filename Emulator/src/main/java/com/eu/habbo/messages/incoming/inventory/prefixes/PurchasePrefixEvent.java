package com.eu.habbo.messages.incoming.inventory.prefixes;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.modtool.WordFilterWord;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.UserPrefix;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.inventory.nickicons.UserNickIconsComposer;
import com.eu.habbo.messages.outgoing.inventory.prefixes.ActivePrefixUpdatedComposer;
import com.eu.habbo.messages.outgoing.inventory.prefixes.CustomPrefixPurchaseFailedComposer;
import com.eu.habbo.messages.outgoing.inventory.prefixes.PrefixReceivedComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserDataComposer;
import com.eu.habbo.messages.outgoing.users.UserCreditsComposer;
import com.eu.habbo.messages.outgoing.users.UserCurrencyComposer;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class PurchasePrefixEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(PurchasePrefixEvent.class);
    private static final String[] ALLOWED_FONTS = { "", "pixel", "cherry", "vampiro" };
    private static final String[] ALLOWED_EFFECTS = {
            "", "glow", "shadow", "italic", "outline", "underline", "pulse", "bounce", "wave", "shake",
            "discord-neon", "cartoon", "toon", "pop", "bold-glow", "rainbow", "frost", "gold", "glitch",
            "fire", "matrix", "sparkle"
    };
    private static final int MAX_ICON_LENGTH = 16;

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        String text = this.packet.readString();
        String color = this.packet.readString();
        String icon = this.packet.readString();
        String effect = this.packet.readString();
        String font = this.packet.readString();

        Habbo habbo = this.client.getHabbo();

        if (habbo == null) return;

        Map<String, Integer> settings = loadSettings();
        int maxLength = setting(settings, "max_length", 15);
        int minRank = setting(settings, "min_rank_to_buy", 1);
        int priceCredits = setting(settings, "price_credits", 5);
        int pricePoints = setting(settings, "price_points", 0);
        int pointsType = setting(settings, "points_type", 0);
        int fontPriceCredits = setting(settings, "font_price_credits", 10);
        int fontPricePoints = setting(settings, "font_price_points", 0);
        int fontPointsType = setting(settings, "font_points_type", pointsType);
        int maxPrefixes = setting(settings, "max_prefixes", 60);

        if (maxPrefixes > 0 && habbo.getInventory().getPrefixesComponent().getPrefixes().size() >= maxPrefixes) {
            this.fail(habbo, "You already own the maximum number of prefixes (" + maxPrefixes + ").");
            return;
        }

        text = text.trim();

        if (text.isEmpty() || text.length() > maxLength) {
            this.fail(habbo, "Prefix text is invalid or too long (max " + maxLength + " characters).");
            return;
        }

        if (containsControlChars(text)) {
            this.fail(habbo, "Prefix text contains invalid characters.");
            return;
        }

        if (containsFilteredWord(text)) {
            this.fail(habbo, "This prefix contains a blocked word.");
            return;
        }

        String[] colorParts = color.split(",");

        if (colorParts.length > text.length()) {
            this.fail(habbo, "Invalid color format.");
            return;
        }

        for (String part : colorParts) {
            if (!part.matches("^#[0-9A-Fa-f]{6}$")) {
                this.fail(habbo, "Invalid color format.");
                return;
            }
        }

        if (habbo.getHabboInfo().getRank().getId() < minRank) {
            this.fail(habbo, "Your rank is too low to purchase prefixes.");
            return;
        }

        if (icon == null) icon = "";
        icon = icon.trim();

        if (!isValidIcon(icon)) {
            this.fail(habbo, "Invalid prefix icon.");
            return;
        }

        if (effect == null) effect = "";
        effect = effect.trim().toLowerCase();

        if (!isAllowedEffect(effect)) {
            this.fail(habbo, "Invalid prefix effect.");
            return;
        }

        if (font == null) font = "";
        font = font.trim().toLowerCase();

        if (!isAllowedFont(font)) {
            this.fail(habbo, "Invalid font format.");
            return;
        }

        int totalPriceCredits = priceCredits + (!font.isEmpty() ? fontPriceCredits : 0);

        if (totalPriceCredits > 0 && habbo.getHabboInfo().getCredits() < totalPriceCredits) {
            this.fail(habbo, "Not enough credits.");
            return;
        }

        int totalPricePointsSameType = pricePoints + ((fontPricePoints > 0 && fontPointsType == pointsType && !font.isEmpty()) ? fontPricePoints : 0);

        if (totalPricePointsSameType > 0 && habbo.getHabboInfo().getCurrencyAmount(pointsType) < totalPricePointsSameType) {
            this.fail(habbo, "Not enough points.");
            return;
        }

        if (!font.isEmpty() && fontPricePoints > 0 && fontPointsType != pointsType && habbo.getHabboInfo().getCurrencyAmount(fontPointsType) < fontPricePoints) {
            this.fail(habbo, "Not enough points.");
            return;
        }

        if (totalPriceCredits > 0) {
            habbo.getHabboInfo().addCredits(-totalPriceCredits);
            this.client.sendResponse(new UserCreditsComposer(habbo));
        }

        if (totalPricePointsSameType > 0) {
            habbo.getHabboInfo().addCurrencyAmount(pointsType, -totalPricePointsSameType);
            this.client.sendResponse(new UserCurrencyComposer(habbo));
        }

        if (!font.isEmpty() && fontPricePoints > 0 && fontPointsType != pointsType) {
            habbo.getHabboInfo().addCurrencyAmount(fontPointsType, -fontPricePoints);
            this.client.sendResponse(new UserCurrencyComposer(habbo));
        }

        int storedPoints = totalPricePointsSameType;
        int storedPointsType = (storedPoints > 0) ? pointsType : ((!font.isEmpty() && fontPricePoints > 0) ? fontPointsType : pointsType);

        var prefix = new UserPrefix(habbo.getHabboInfo().getId(), text, color, icon, effect, font, 0, text, storedPoints, storedPointsType, true);
        prefix.run(); // Insert into DB synchronously to get the ID
        habbo.getInventory().getPrefixesComponent().addPrefix(prefix);
        habbo.getInventory().getPrefixesComponent().setActive(prefix.getId());
        this.client.sendResponse(new PrefixReceivedComposer(prefix));
        this.client.sendResponse(new ActivePrefixUpdatedComposer(prefix));
        this.client.sendResponse(new UserNickIconsComposer(habbo));

        if (habbo.getHabboInfo().getCurrentRoom() != null) {
            habbo.getHabboInfo().getCurrentRoom().sendComposer(new RoomUserDataComposer(habbo).compose());
        }
    }

    private void fail(Habbo habbo, String message) {
        this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, message));
        this.client.sendResponse(new CustomPrefixPurchaseFailedComposer(message));
    }

    private Map<String, Integer> loadSettings() {
        Map<String, Integer> settings = new HashMap<>();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT key_name, `value` FROM custom_prefix_settings");
             ResultSet set = statement.executeQuery()) {
            while (set.next()) {
                try {
                    settings.put(set.getString("key_name"), Integer.parseInt(set.getString("value")));
                } catch (NumberFormatException ignored) {}
            }
        } catch (SQLException e) {
            LOGGER.error("Error reading prefix settings", e);
        }
        return settings;
    }

    private int setting(Map<String, Integer> settings, String key, int defaultValue) {
        Integer value = settings.get(key);
        return value != null ? value : defaultValue;
    }

    private boolean containsFilteredWord(String text) {
        if (text == null || text.isEmpty()) return false;

        for (WordFilterWord word : Emulator.getGameEnvironment().getWordFilter().getWords()) {
            if (word.key != null && !word.key.isEmpty() && StringUtils.containsIgnoreCase(text, word.key)) {
                return true;
            }
        }

        return false;
    }

    private boolean isAllowedFont(String font) {
        for (String allowedFont : ALLOWED_FONTS) {
            if (allowedFont.equals(font)) {
                return true;
            }
        }

        return false;
    }

    private boolean isAllowedEffect(String effect) {
        for (String allowedEffect : ALLOWED_EFFECTS) {
            if (allowedEffect.equals(effect)) {
                return true;
            }
        }

        return false;
    }

    private boolean isValidIcon(String icon) {
        if (icon.isEmpty()) return true;
        if (icon.length() > MAX_ICON_LENGTH) return false;

        for (int i = 0; i < icon.length(); i++) {
            char c = icon.charAt(i);
            if (c < 0x20 || c == 0x7F || c == '<' || c == '>') return false;
        }

        return true;
    }

    private boolean containsControlChars(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 0x20 || c == 0x7F) return true;
        }

        return false;
    }
}
