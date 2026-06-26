package com.eu.habbo.habbohotel.users.custombadge;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboBadge;
import com.eu.habbo.habbohotel.users.inventory.BadgesComponent;
import com.eu.habbo.messages.outgoing.inventory.InventoryBadgesComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class CustomBadgeManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomBadgeManager.class);

    public static final int MAX_PER_USER = 5;
    public static final int BADGE_WIDTH = 40;
    public static final int BADGE_HEIGHT = 40;
    public static final int MAX_BADGE_SIZE_BYTES = 40960;

    private static final int RANDOM_SUFFIX_LENGTH = 5;
    private static final char[] RANDOM_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final Pattern BADGE_ID_PATTERN = Pattern.compile("^CUST[A-Z0-9]{" + RANDOM_SUFFIX_LENGTH + "}-\\d+$");

    private static final byte[] PNG_MAGIC = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };

    private static final int RATE_LIMIT_OPS = 5;
    private static final long RATE_LIMIT_WINDOW_MS = 60_000L;

    private final SecureRandom random = new SecureRandom();
    private final Map<Integer, long[]> rateBuckets = new ConcurrentHashMap<>();
    private final Map<String, BadgeText> textCache = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong textCacheVersion = new java.util.concurrent.atomic.AtomicLong();

    private volatile CustomBadgeSettings settings;

    public CustomBadgeManager() {
        this.reload();
    }

    public static final class BadgeText {
        public final String name;
        public final String description;
        public BadgeText(String name, String description) {
            this.name = name == null ? "" : name;
            this.description = description == null ? "" : description;
        }
    }

    public Map<String, BadgeText> getTextCache() {
        return java.util.Collections.unmodifiableMap(this.textCache);
    }

    public long getTextCacheVersion() {
        return this.textCacheVersion.get();
    }

    private void loadTextCache() {
        Map<String, BadgeText> next = new java.util.HashMap<>();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT `badge_id`, `badge_name`, `badge_description` FROM `user_custom_badge`")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    next.put(resultSet.getString("badge_id"),
                            new BadgeText(
                                    resultSet.getString("badge_name"),
                                    resultSet.getString("badge_description")));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("CustomBadgeManager -> Failed to load badge text cache.", e);
            return;
        }
        this.textCache.clear();
        this.textCache.putAll(next);
        this.textCacheVersion.incrementAndGet();
        LOGGER.info("CustomBadgeManager -> loaded {} custom badge texts into memory.", next.size());
    }

    public void reload() {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT `badge_path`, `badge_url`, `price_badge`, `currency_type` FROM `users_custom_badge_settings` ORDER BY `id` ASC LIMIT 1")) {

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    this.settings = new CustomBadgeSettings(
                            resultSet.getString("badge_path"),
                            resultSet.getString("badge_url"),
                            resultSet.getInt("price_badge"),
                            resultSet.getInt("currency_type"));
                } else {
                    this.settings = new CustomBadgeSettings(
                            "/var/www/gamedata/c_images/album1584",
                            "/gamedata/c_images/album1584",
                            0, -1);
                    LOGGER.warn("CustomBadgeManager -> No row found in users_custom_badge_settings, falling back to defaults.");
                }
            }
        } catch (SQLException e) {
            LOGGER.error("CustomBadgeManager -> Failed to load settings.", e);
        }

        loadTextCache();
    }

    public CustomBadgeSettings getSettings() {
        return this.settings;
    }

    public List<CustomBadge> listForUser(int userId) {
        List<CustomBadge> result = new ArrayList<>();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM `user_custom_badge` WHERE `user_id` = ? ORDER BY `date_created` ASC")) {
            statement.setInt(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(new CustomBadge(resultSet));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("CustomBadgeManager -> Failed to list badges for user " + userId, e);
        }
        return result;
    }

    public CustomBadge getByBadgeId(String badgeId) {
        if (badgeId == null || badgeId.isEmpty()) return null;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM `user_custom_badge` WHERE `badge_id` = ? LIMIT 1")) {
            statement.setString(1, badgeId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new CustomBadge(resultSet);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("CustomBadgeManager -> Failed to load badge " + badgeId, e);
        }
        return null;
    }

    public int countForUser(int userId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM `user_custom_badge` WHERE `user_id` = ?")) {
            statement.setInt(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("CustomBadgeManager -> Failed to count badges for user " + userId, e);
        }
        return 0;
    }

    public CustomBadge create(int userId, String name, String description, byte[] pngBytes) throws CustomBadgeException {
        enforceRateLimit(userId);

        if (this.countForUser(userId) >= MAX_PER_USER) {
            throw new CustomBadgeException("limit_reached", "Maximum of " + MAX_PER_USER + " custom badges reached.");
        }

        BufferedImage image = validatePng(pngBytes);

        chargeForCreate(userId);

        String badgeId = generateBadgeId();
        var now = (int) (System.currentTimeMillis() / 1000L);

        try {
            writeBadgeFile(badgeId, image);
        } catch (CustomBadgeException e) {
            refundForCreate(userId);
            throw e;
        }

        String safeName = sanitize(name, 64);
        String safeDesc = sanitize(description, 255);

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO `user_custom_badge` (`user_id`, `badge_id`, `badge_name`, `badge_description`, `date_created`, `date_edit`) VALUES (?, ?, ?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, userId);
            statement.setString(2, badgeId);
            statement.setString(3, safeName);
            statement.setString(4, safeDesc);
            statement.setInt(5, now);
            statement.setInt(6, now);
            statement.executeUpdate();

            int generatedId = 0;
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) generatedId = keys.getInt(1);
            }

            this.textCache.put(badgeId, new BadgeText(safeName, safeDesc));
            this.textCacheVersion.incrementAndGet();
            issueBadgeToInventory(userId, badgeId);

            return new CustomBadge(generatedId, userId, badgeId, safeName, safeDesc, now, now);
        } catch (SQLException e) {
            deleteBadgeFileQuietly(badgeId);
            refundForCreate(userId);
            LOGGER.error("CustomBadgeManager -> Failed to insert badge for user " + userId, e);
            throw new CustomBadgeException("db_error", "Could not save the badge.");
        }
    }

    public CustomBadge update(int userId, String oldBadgeId, String name, String description, byte[] pngBytes) throws CustomBadgeException {
        enforceRateLimit(userId);

        CustomBadge existing = getByBadgeId(oldBadgeId);
        if (existing == null || existing.getUserId() != userId) {
            throw new CustomBadgeException("not_found", "Badge not found.");
        }

        BufferedImage image = validatePng(pngBytes);

        String newBadgeId = generateBadgeId();
        var now = (int) (System.currentTimeMillis() / 1000L);

        writeBadgeFile(newBadgeId, image);

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE `user_custom_badge` SET `badge_id` = ?, `badge_name` = ?, `badge_description` = ?, `date_edit` = ? WHERE `id` = ?")) {
            statement.setString(1, newBadgeId);
            statement.setString(2, sanitize(name, 64));
            statement.setString(3, sanitize(description, 255));
            statement.setInt(4, now);
            statement.setInt(5, existing.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            deleteBadgeFileQuietly(newBadgeId);
            LOGGER.error("CustomBadgeManager -> Failed to update badge " + oldBadgeId, e);
            throw new CustomBadgeException("db_error", "Could not update the badge.");
        }

        String safeName = sanitize(name, 64);
        String safeDesc = sanitize(description, 255);
        this.textCache.remove(oldBadgeId);
        this.textCache.put(newBadgeId, new BadgeText(safeName, safeDesc));
        this.textCacheVersion.incrementAndGet();
        renameBadgeInInventory(userId, oldBadgeId, newBadgeId);
        deleteBadgeFileQuietly(oldBadgeId);
        return new CustomBadge(existing.getId(), userId, newBadgeId, safeName, safeDesc, existing.getDateCreated(), now);
    }

    public void delete(int userId, String badgeId) throws CustomBadgeException {
        enforceRateLimit(userId);

        CustomBadge existing = getByBadgeId(badgeId);
        if (existing == null || existing.getUserId() != userId) {
            throw new CustomBadgeException("not_found", "Badge not found.");
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM `user_custom_badge` WHERE `id` = ?")) {
            statement.setInt(1, existing.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("CustomBadgeManager -> Failed to delete badge " + badgeId, e);
            throw new CustomBadgeException("db_error", "Could not delete the badge.");
        }

        this.textCache.remove(badgeId);
        this.textCacheVersion.incrementAndGet();
        revokeBadgeFromInventory(userId, badgeId);
        deleteBadgeFileQuietly(badgeId);
    }

    public boolean isCustomBadgeId(String badgeId) {
        return badgeId != null && BADGE_ID_PATTERN.matcher(badgeId).matches();
    }

    public String generateBadgeId() {
        long timestamp = System.currentTimeMillis() / 1000L;
        for (int attempt = 0; attempt < 8; attempt++) {
            var suffix = new StringBuilder(RANDOM_SUFFIX_LENGTH);
            for (int i = 0; i < RANDOM_SUFFIX_LENGTH; i++) {
                suffix.append(RANDOM_ALPHABET[this.random.nextInt(RANDOM_ALPHABET.length)]);
            }
            String candidate = "CUST" + suffix + "-" + timestamp;
            if (getByBadgeId(candidate) == null) return candidate;
            timestamp++;
        }
        throw new IllegalStateException("Could not allocate a unique custom badge id after 8 attempts.");
    }

    public String publicUrlFor(String badgeId) {
        CustomBadgeSettings current = this.settings;
        if (current == null) return "";
        String base = current.getBadgeUrl();
        if (base == null || base.isEmpty()) return "";
        if (base.endsWith("/")) return base + badgeId + ".gif";
        return base + "/" + badgeId + ".gif";
    }

    private void chargeForCreate(int userId) throws CustomBadgeException {
        CustomBadgeSettings current = this.settings;
        if (current == null) return;
        int price = current.getPriceBadge();
        if (price <= 0) return;

        Habbo habbo = Emulator.getGameServer().getGameClientManager().getHabbo(userId);
        if (habbo == null) {
            throw new CustomBadgeException("must_be_online",
                    "You must be online in the hotel to create a paid badge.");
        }

        int currencyType = current.getCurrencyType();
        if (currencyType == -1) {
            if (habbo.getHabboInfo().getCredits() < price) {
                throw new CustomBadgeException("insufficient_funds",
                        "You don't have enough credits (need " + price + ").");
            }
            habbo.giveCredits(-price);
        } else {
            if (habbo.getHabboInfo().getCurrencyAmount(currencyType) < price) {
                throw new CustomBadgeException("insufficient_funds",
                        "You don't have enough of that currency (need " + price + ").");
            }
            habbo.givePoints(currencyType, -price);
        }
    }

    private void issueBadgeToInventory(int userId, String badgeId) {
        Habbo online = Emulator.getGameServer().getGameClientManager().getHabbo(userId);
        if (online != null) {
            BadgesComponent.createBadge(badgeId, online);
            if (online.getClient() != null) {
                online.getClient().sendResponse(new InventoryBadgesComposer(online));
            }
            return;
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO `users_badges` (`user_id`, `slot_id`, `badge_code`) VALUES (?, 0, ?)")) {
            statement.setInt(1, userId);
            statement.setString(2, badgeId);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("CustomBadgeManager -> Failed to issue offline badge " + badgeId + " to user " + userId, e);
        }
    }

    private void renameBadgeInInventory(int userId, String oldBadgeId, String newBadgeId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE `users_badges` SET `badge_code` = ? WHERE `user_id` = ? AND `badge_code` = ?")) {
            statement.setString(1, newBadgeId);
            statement.setInt(2, userId);
            statement.setString(3, oldBadgeId);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("CustomBadgeManager -> Failed to rename badge in users_badges " + oldBadgeId + " -> " + newBadgeId, e);
        }

        Habbo online = Emulator.getGameServer().getGameClientManager().getHabbo(userId);
        if (online == null) return;

        HabboBadge existing = online.getInventory().getBadgesComponent().getBadge(oldBadgeId);
        if (existing != null) existing.setCode(newBadgeId);

        if (online.getClient() != null) {
            online.getClient().sendResponse(new InventoryBadgesComposer(online));
        }
    }

    private void revokeBadgeFromInventory(int userId, String badgeId) {
        BadgesComponent.deleteBadge(userId, badgeId);

        Habbo online = Emulator.getGameServer().getGameClientManager().getHabbo(userId);
        if (online == null) return;

        online.getInventory().getBadgesComponent().removeBadge(badgeId);
        if (online.getClient() != null) {
            online.getClient().sendResponse(new InventoryBadgesComposer(online));
        }
    }

    private BufferedImage validatePng(byte[] data) throws CustomBadgeException {
        if (data == null || data.length == 0) {
            throw new CustomBadgeException("empty", "Badge image is empty.");
        }
        if (data.length > MAX_BADGE_SIZE_BYTES) {
            throw new CustomBadgeException("too_large", "Badge image exceeds " + MAX_BADGE_SIZE_BYTES + " bytes.");
        }

        if (data.length < PNG_MAGIC.length) {
            throw new CustomBadgeException("invalid_image", "Badge image must be a PNG.");
        }
        for (int i = 0; i < PNG_MAGIC.length; i++) {
            if (data[i] != PNG_MAGIC[i]) {
                throw new CustomBadgeException("invalid_image", "Badge image must be a PNG.");
            }
        }

        try (ImageInputStream peek = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            if (peek == null) throw new IOException("no input stream");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(peek);
            if (!readers.hasNext()) {
                throw new CustomBadgeException("invalid_image", "Badge image format not recognised.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(peek, true, true);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                if (w != BADGE_WIDTH || h != BADGE_HEIGHT) {
                    throw new CustomBadgeException("wrong_dimensions",
                            "Badge image must be " + BADGE_WIDTH + "x" + BADGE_HEIGHT + " pixels.");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new CustomBadgeException("invalid_image", "Badge image header could not be read.");
        }

        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(data));
        } catch (IOException e) {
            throw new CustomBadgeException("invalid_image", "Badge image could not be decoded.");
        }
        if (image == null
                || image.getWidth() != BADGE_WIDTH
                || image.getHeight() != BADGE_HEIGHT) {
            throw new CustomBadgeException("invalid_image", "Badge image could not be decoded.");
        }
        return image;
    }

    private void enforceRateLimit(int userId) throws CustomBadgeException {
        long now = System.currentTimeMillis();
        long[] bucket = this.rateBuckets.computeIfAbsent(userId, id -> new long[RATE_LIMIT_OPS]);
        synchronized (bucket) {
            long oldest = Long.MAX_VALUE;
            int oldestIdx = 0;
            for (int i = 0; i < bucket.length; i++) {
                if (bucket[i] < oldest) { oldest = bucket[i]; oldestIdx = i; }
            }
            if (oldest > now - RATE_LIMIT_WINDOW_MS) {
                throw new CustomBadgeException("rate_limited",
                        "Too many badge operations. Try again in a moment.");
            }
            bucket[oldestIdx] = now;
        }
    }

    private void refundForCreate(int userId) {
        CustomBadgeSettings current = this.settings;
        if (current == null) return;
        int price = current.getPriceBadge();
        if (price <= 0) return;

        Habbo habbo = Emulator.getGameServer().getGameClientManager().getHabbo(userId);
        if (habbo == null) {
            LOGGER.warn("CustomBadgeManager -> Could not refund {} (price {}): user offline", userId, price);
            return;
        }
        int currencyType = current.getCurrencyType();
        if (currencyType == -1) habbo.giveCredits(price);
        else habbo.givePoints(currencyType, price);
    }

    private void writeBadgeFile(String badgeId, BufferedImage source) throws CustomBadgeException {
        CustomBadgeSettings current = this.settings;
        if (current == null || current.getBadgePath() == null || current.getBadgePath().isEmpty()) {
            throw new CustomBadgeException("not_configured", "Custom badge storage path is not configured.");
        }
        try {
            Path dir = Paths.get(current.getBadgePath()).toAbsolutePath();
            Files.createDirectories(dir);
            Path target = dir.resolve(badgeId + ".gif");

            BufferedImage indexed = toIndexedGifImage(source);
            if (!ImageIO.write(indexed, "gif", target.toFile())) {
                throw new IOException("No GIF ImageWriter available.");
            }

            LOGGER.info("CustomBadgeManager -> wrote badge {} ({} bytes) to {}",
                    badgeId, Files.size(target), target);
        } catch (IOException e) {
            LOGGER.error("CustomBadgeManager -> Failed to write badge " + badgeId
                    + " to " + current.getBadgePath(), e);
            throw new CustomBadgeException("write_failed", "Could not save the badge file.");
        }
    }

    private static BufferedImage toIndexedGifImage(BufferedImage source) {
        int w = source.getWidth();
        int h = source.getHeight();
        int[] pixels = source.getRGB(0, 0, w, h, null, 0, w);

        Map<Integer, Integer> indexByColor = new LinkedHashMap<>();
        indexByColor.put(0, 0);

        for (int p : pixels) {
            int alpha = (p >>> 24) & 0xff;
            int key = (alpha < 128) ? 0 : (p | 0xFF000000);
            if (key == 0) continue;
            if (indexByColor.size() >= 256) break;
            indexByColor.computeIfAbsent(key, k -> indexByColor.size());
        }

        int n = indexByColor.size();
        byte[] r = new byte[n];
        byte[] g = new byte[n];
        byte[] b = new byte[n];
        int i = 0;
        for (Integer color : indexByColor.keySet()) {
            r[i] = (byte) ((color >>> 16) & 0xff);
            g[i] = (byte) ((color >>> 8) & 0xff);
            b[i] = (byte) (color & 0xff);
            i++;
        }

        var colorModel = new IndexColorModel(8, n, r, g, b, 0);
        var out = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_INDEXED, colorModel);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = pixels[y * w + x];
                int alpha = (p >>> 24) & 0xff;
                int key = (alpha < 128) ? 0 : (p | 0xFF000000);
                Integer idx = indexByColor.get(key);
                out.getRaster().setSample(x, y, 0, idx == null ? 0 : idx);
            }
        }

        return out;
    }

    private void deleteBadgeFileQuietly(String badgeId) {
        CustomBadgeSettings current = this.settings;
        if (current == null || current.getBadgePath() == null) return;
        var file = new File(current.getBadgePath(), badgeId + ".gif");
        if (file.exists() && !file.delete()) {
            LOGGER.warn("CustomBadgeManager -> Could not delete stale badge file: {}", file.getAbsolutePath());
        }
    }

    private static String sanitize(String value, int maxLength) {
        if (value == null) return "";
        var out = new StringBuilder(Math.min(value.length(), maxLength));
        for (int i = 0; i < value.length() && out.length() < maxLength; i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c == 0x7F) continue;
            out.append(c);
        }
        return out.toString().trim();
    }
}
