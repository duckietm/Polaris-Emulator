package polaris.hotel.users.profile;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.core.ConfigurationManager;
import polaris.database.SqlQueries;

/**
 * The pictures a player uploads for the banner and the background of their profile window. The client
 * crops a picture to the exact size of its place and sends it as JPEG or PNG; it is decoded here and
 * written again as a fresh JPEG, so nothing but the pixels of the original reaches the disk.
 */
public class ProfileImageService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileImageService.class);

    public static final String ENABLED_SETTING = "profile.images.enabled";
    public static final String LOCATION_SETTING = "profile.images.location";
    public static final String COOLDOWN_SETTING = "profile.images.cooldown_seconds";
    /** The banner on top of the profile window and its gallery; off, nobody shows or picks one. */
    public static final String BANNER_SETTING = "profile.banner.enabled";
    /** The accent colour of the profile and of the owner's windows; off, nobody shows or picks one. */
    public static final String THEME_SETTING = "profile.theme.enabled";

    /** The features the profile packet and the options call report: one bit each. */
    public static final int FEATURE_BANNER = 1;

    public static final int FEATURE_UPLOAD = 2;
    public static final int FEATURE_THEME = 4;

    /** How many pictures of each kind a player keeps to pick again; older ones are deleted. */
    public static final int LIBRARY_SIZE = 6;

    /**
     * A background of the player's own: two colours as six hex digits each, the angle of the gradient
     * between them and a gallery card background laid over it (0 for none), as "rrggbb;rrggbb;angle;pattern".
     */
    private static final Pattern BACKGROUND_STYLE =
            Pattern.compile("[0-9a-f]{6};[0-9a-f]{6};(0|45|90|135|180|225|270|315);[0-9]{1,4}");

    public static final String DEFAULT_LOCATION = "profile-images";
    public static final int DEFAULT_COOLDOWN_SECONDS = 60;

    /** Under the 500 000 bytes the HTTP aggregator accepts for a whole request. */
    public static final int MAX_BYTES = 460_800;

    private static final Pattern FILE_NAME = Pattern.compile("[0-9]+_(banner|background)_[0-9a-f]{16}\\.(jpg|gif)");
    /** A file not named by any profile is removed once it is this old, so an upload being saved is never caught. */
    static final long ORPHAN_MIN_AGE_MILLIS = 10 * 60 * 1000L;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Where a picture goes, the column that names its file, and the exact size the client crops it to. */
    public enum Kind {
        BANNER("banner", "profile_banner_image", 1280, 220),
        BACKGROUND("background", "profile_background_image", 1280, 1380);

        public final String key;
        public final String column;
        public final int width;
        public final int height;

        Kind(String key, String column, int width, int height) {
            this.key = key;
            this.column = column;
            this.width = width;
            this.height = height;
        }

        public static Kind fromKey(String key) {
            for (Kind kind : values()) {
                if (kind.key.equalsIgnoreCase(key)) return kind;
            }
            return null;
        }
    }

    /** Why an upload was refused; the client shows its own text for each code. */
    public static final class ProfileImageException extends Exception {
        private final String code;

        public ProfileImageException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String getCode() {
            return this.code;
        }
    }

    private static final ProfileImageService INSTANCE = new ProfileImageService();

    /** The hotel's settings for uploads, read from its configuration by whoever owns it. */
    public record Settings(boolean enabled, String location, int cooldownSeconds, boolean banner, boolean theme) {
        public static final Settings DEFAULTS =
                new Settings(true, DEFAULT_LOCATION, DEFAULT_COOLDOWN_SECONDS, true, true);

        public Settings(boolean enabled, String location, int cooldownSeconds) {
            this(enabled, location, cooldownSeconds, true, true);
        }

        /** The {@code FEATURE_*} bits that are on. */
        public int features() {
            return (this.banner ? FEATURE_BANNER : 0)
                    | (this.enabled ? FEATURE_UPLOAD : 0)
                    | (this.theme ? FEATURE_THEME : 0);
        }

        public static Settings from(ConfigurationManager configuration) {
            if (configuration == null) return DEFAULTS;
            String location = configuration.getValue(LOCATION_SETTING, DEFAULT_LOCATION);
            return new Settings(
                    configuration.getBoolean(ENABLED_SETTING, true),
                    location == null || location.isBlank() ? DEFAULT_LOCATION : location,
                    Math.max(0, configuration.getInt(COOLDOWN_SETTING, DEFAULT_COOLDOWN_SECONDS)),
                    configuration.getBoolean(BANNER_SETTING, true),
                    configuration.getBoolean(THEME_SETTING, true));
        }
    }

    /** One picture a player uploaded and may put back on their profile. */
    public record LibraryPicture(int id, String fileName, int createdAt) {}

    /** One stored picture still on a profile, for the staff's review list. */
    public record StoredPicture(int id, int userId, String username, String kind, String fileName, int createdAt) {}

    private final Map<Integer, Long> lastUpload = new ConcurrentHashMap<>();
    private final Map<Integer, Long> lastChange = new ConcurrentHashMap<>();

    /** The shortest time between two changes that are not uploads (picking a saved picture, a style). */
    static final long CHANGE_COOLDOWN_MILLIS = 1000L;

    private final AtomicBoolean orphansChecked = new AtomicBoolean(false);
    private volatile Settings settings = Settings.DEFAULTS;

    public static ProfileImageService get() {
        return INSTANCE;
    }

    /** False when the player changed their pictures or style less than a second ago; otherwise counts this change. */
    public boolean allowChange(int userId) {
        long now = System.currentTimeMillis();
        Long last = this.lastChange.put(userId, now);
        return last == null || now - last >= CHANGE_COOLDOWN_MILLIS;
    }

    /** The hotel's settings as last read; the defaults until the first connection is set up. */
    public Settings settings() {
        return this.settings;
    }

    /** Takes the hotel's current settings; the HTTP handler passes them as it is set up for a connection. */
    public void configure(Settings settings) {
        this.settings = settings == null ? Settings.DEFAULTS : settings;

        // The first time the settings are known, the folder is cleared of pictures no profile names any more
        // (a deleted or reset user, a write the database missed), off the thread that set up the connection.
        if (this.orphansChecked.compareAndSet(false, true)) {
            Thread.ofVirtual().name("profile-images-cleanup").start(this::removeOrphans);
        }
    }

    /**
     * The stored file names of a user's banner and background pictures and their own background style, "" for
     * each one they do not have.
     */
    public static String[] storedNames(int userId) {
        return SqlQueries.queryOne(
                        "SELECT profile_banner_image, profile_background_image, profile_background_style FROM users "
                                + "WHERE id = ? LIMIT 1",
                        set -> new String[] {
                            nullToEmpty(set.getString(1)), nullToEmpty(set.getString(2)), nullToEmpty(set.getString(3))
                        },
                        userId)
                .orElse(new String[] {"", "", ""});
    }

    /** A background style as the client sends it, or null when it is not one. "" clears the style. */
    public static String normalizeBackgroundStyle(String style) {
        if (style == null) return null;
        String value = style.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) return "";
        return BACKGROUND_STYLE.matcher(value).matches() ? value : null;
    }

    /** The gallery card background a style lays over its colours, 0 for none. */
    public static int backgroundStylePattern(String style) {
        if (style == null || style.isEmpty()) return 0;
        return Integer.parseInt(style.substring(style.lastIndexOf(';') + 1));
    }

    public void setBackgroundStyle(int userId, String style) {
        SqlQueries.update("UPDATE users SET profile_background_style = ? WHERE id = ?", style, userId);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** Checks and stores a still picture, replaces the previous one of the same kind, and returns the new file name. */
    public String store(int userId, Kind kind, byte[] bytes) throws ProfileImageException {
        return store(userId, kind, bytes, null);
    }

    /**
     * Checks and stores a picture. A GIF banner keeps its animation: the client sends the original and where
     * it placed it, and every frame is placed here. Anything else is a still the client already cropped.
     */
    public String store(int userId, Kind kind, byte[] bytes, AnimatedBanner.Placement placement)
            throws ProfileImageException {
        Settings current = this.settings;
        if (!current.enabled()) {
            throw new ProfileImageException("disabled", "Profile pictures are turned off.");
        }
        if (isBlocked(userId)) {
            throw new ProfileImageException("blocked", "This user may not upload profile pictures.");
        }

        long now = System.currentTimeMillis();
        long cooldown = current.cooldownSeconds() * 1000L;
        Long last = this.lastUpload.get(userId);
        if (last != null && now - last < cooldown) {
            throw new ProfileImageException("rate_limited", "Wait before uploading again.");
        }

        byte[] encoded;
        String extension;
        if (AnimatedBanner.isGif(bytes)) {
            if (kind != Kind.BANNER) throw new ProfileImageException("invalid", "Only a banner can be animated.");
            if (bytes.length > MAX_BYTES) {
                throw new ProfileImageException("too_large", "The picture is larger than " + MAX_BYTES + " bytes.");
            }
            encoded = AnimatedBanner.render(bytes, placement);
            extension = "gif";
        } else {
            try {
                encoded = encodeJpeg(decode(kind, bytes));
            } catch (IOException e) {
                throw new ProfileImageException("failed", "The picture could not be encoded.");
            }
            extension = "jpg";
        }

        String fileName = userId + "_" + kind.key + "_" + randomHex() + "." + extension;
        Path directory = directory();
        Path target = directory.resolve(fileName);

        try {
            Files.createDirectories(directory);
            Files.write(target, encoded);
        } catch (IOException e) {
            LOGGER.error("Could not write the profile picture {}", target, e);
            throw new ProfileImageException("failed", "The picture could not be written.");
        }

        this.lastUpload.put(userId, now);
        // The picture it replaces stays in the player's library, to be picked again.
        replace(userId, kind, fileName);
        SqlQueries.update(
                "INSERT INTO users_profile_images (user_id, kind, file_name, created_at) VALUES (?, ?, ?, ?)",
                userId,
                kind.key,
                fileName,
                (int) (now / 1000L));
        prune(userId, kind);
        return fileName;
    }

    /** The pictures of one kind a player uploaded and still has, newest first. */
    public List<LibraryPicture> library(int userId, Kind kind) {
        return SqlQueries.query(
                "SELECT id, file_name, created_at FROM users_profile_images "
                        + "WHERE user_id = ? AND kind = ? AND removed_at IS NULL ORDER BY created_at DESC, id DESC",
                set -> new LibraryPicture(set.getInt("id"), set.getString("file_name"), set.getInt("created_at")),
                userId,
                kind.key);
    }

    /** Puts a picture from the player's own library back on their profile. */
    public boolean select(int userId, Kind kind, int pictureId) throws ProfileImageException {
        if (!this.settings.enabled()) throw new ProfileImageException("disabled", "Profile pictures are turned off.");
        if (isBlocked(userId)) throw new ProfileImageException("blocked", "This user may not use profile pictures.");

        LibraryPicture picture = owned(userId, kind, pictureId);
        if (picture == null) return false;
        replace(userId, kind, picture.fileName());
        return true;
    }

    /** Deletes a picture from the player's own library, and takes it off the profile if it is shown there. */
    public boolean forget(int userId, Kind kind, int pictureId) {
        LibraryPicture picture = owned(userId, kind, pictureId);
        if (picture == null) return false;

        String current = storedNames(userId)[kind == Kind.BANNER ? 0 : 1];
        if (current.equals(picture.fileName())) replace(userId, kind, null);
        markRemoved(picture.fileName(), userId);
        deleteFile(picture.fileName());
        return true;
    }

    private static LibraryPicture owned(int userId, Kind kind, int pictureId) {
        return SqlQueries.queryOne(
                        "SELECT id, file_name, created_at FROM users_profile_images "
                                + "WHERE id = ? AND user_id = ? AND kind = ? AND removed_at IS NULL LIMIT 1",
                        set -> new LibraryPicture(
                                set.getInt("id"), set.getString("file_name"), set.getInt("created_at")),
                        pictureId,
                        userId,
                        kind.key)
                .orElse(null);
    }

    /** Keeps the newest {@link #LIBRARY_SIZE} pictures of a kind, and always the one on the profile. */
    private void prune(int userId, Kind kind) {
        String current = storedNames(userId)[kind == Kind.BANNER ? 0 : 1];
        List<LibraryPicture> pictures = library(userId, kind);
        for (int index = LIBRARY_SIZE; index < pictures.size(); index++) {
            String fileName = pictures.get(index).fileName();
            if (fileName.equals(current)) continue;
            markRemoved(fileName, userId);
            deleteFile(fileName);
        }
    }

    /** Takes the picture of one kind off a profile, by its owner: it stays in their library. */
    public void remove(int userId, Kind kind) {
        remove(userId, kind, userId);
    }

    /**
     * Takes the picture of one kind off a profile. Its owner keeps it in their library; a member of staff
     * ({@code removedBy} another user) deletes it.
     */
    public void remove(int userId, Kind kind, int removedBy) {
        String previous = replace(userId, kind, null);
        if (removedBy == userId) return;
        markRemoved(previous, removedBy);
        deleteFile(previous);
    }

    /** Removes both pictures; with {@code blocked} set, also sets or clears the flag that stops the user uploading. */
    public void removeAll(int userId, Boolean blocked) {
        removeAll(userId, blocked, userId);
    }

    public void removeAll(int userId, Boolean blocked, int removedBy) {
        for (Kind kind : Kind.values()) {
            remove(userId, kind, removedBy);
        }
        if (blocked != null) setBlocked(userId, blocked);
    }

    /** The pictures on profiles now, newest first, for the staff to review. */
    public List<StoredPicture> recent(int limit, int offset) {
        return SqlQueries.query(
                "SELECT p.id, p.user_id, u.username, p.kind, p.file_name, p.created_at FROM users_profile_images p "
                        + "JOIN users u ON u.id = p.user_id WHERE p.removed_at IS NULL "
                        + "ORDER BY p.created_at DESC, p.id DESC LIMIT ? OFFSET ?",
                set -> new StoredPicture(
                        set.getInt("id"),
                        set.getInt("user_id"),
                        set.getString("username"),
                        set.getString("kind"),
                        set.getString("file_name"),
                        set.getInt("created_at")),
                Math.max(1, Math.min(100, limit)),
                Math.max(0, offset));
    }

    /** The owner of a reviewed picture still shown, or 0. */
    public int ownerOf(int pictureId) {
        return SqlQueries.queryOne(
                        "SELECT user_id FROM users_profile_images WHERE id = ? AND removed_at IS NULL LIMIT 1",
                        set -> set.getInt(1),
                        pictureId)
                .orElse(0);
    }

    /** A member of staff removes one reviewed picture, and with {@code block} stops its owner uploading. */
    public boolean removeReviewed(int pictureId, int staffId, boolean block) {
        StoredPicture picture = SqlQueries.queryOne(
                        "SELECT id, user_id, kind, file_name, created_at FROM users_profile_images "
                                + "WHERE id = ? AND removed_at IS NULL LIMIT 1",
                        set -> new StoredPicture(
                                set.getInt("id"),
                                set.getInt("user_id"),
                                "",
                                set.getString("kind"),
                                set.getString("file_name"),
                                set.getInt("created_at")),
                        pictureId)
                .orElse(null);
        Kind kind = picture == null ? null : Kind.fromKey(picture.kind());
        if (kind == null) return false;

        String current = storedNames(picture.userId())[kind == Kind.BANNER ? 0 : 1];
        if (current.equals(picture.fileName())) {
            remove(picture.userId(), kind, staffId);
        } else {
            // A picture only in the owner's library: nobody sees it, but it may not be picked again.
            markRemoved(picture.fileName(), staffId);
            deleteFile(picture.fileName());
        }
        if (block) setBlocked(picture.userId(), true);
        return true;
    }

    /** Deletes files in the folder that no profile names and that are old enough not to be an upload in flight. */
    int removeOrphans() {
        Path directory = directory();
        if (!Files.isDirectory(directory)) return 0;

        Set<String> named = new HashSet<>();
        try {
            SqlQueries.forEach(
                    "SELECT profile_banner_image, profile_background_image FROM users "
                            + "WHERE profile_banner_image IS NOT NULL OR profile_background_image IS NOT NULL",
                    set -> {
                        if (set.getString(1) != null) named.add(set.getString(1));
                        if (set.getString(2) != null) named.add(set.getString(2));
                    });
            // The pictures players keep in their library to pick again.
            SqlQueries.forEach(
                    "SELECT file_name FROM users_profile_images WHERE removed_at IS NULL",
                    set -> named.add(set.getString(1)));
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.warn("Profile pictures: could not read which files are in use, nothing was cleaned up", e);
            return 0;
        }

        long cutoff = System.currentTimeMillis() - ORPHAN_MIN_AGE_MILLIS;
        int removed = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                if (!isValidFileName(name) || named.contains(name)) continue;
                if (Files.getLastModifiedTime(file).toMillis() > cutoff) continue;
                Files.deleteIfExists(file);
                markRemoved(name, 0);
                removed++;
            }
        } catch (IOException e) {
            LOGGER.warn("Profile pictures: cleaning up {} stopped", directory, e);
        }
        if (removed > 0) LOGGER.info("Profile pictures: removed {} files no profile uses any more", removed);
        return removed;
    }

    private static void markRemoved(String fileName, int removedBy) {
        if (fileName == null || fileName.isEmpty()) return;
        SqlQueries.update(
                "UPDATE users_profile_images SET removed_at = ?, removed_by = ? WHERE file_name = ? AND removed_at IS NULL",
                (int) (System.currentTimeMillis() / 1000L),
                removedBy,
                fileName);
    }

    public void setBlocked(int userId, boolean blocked) {
        SqlQueries.update("UPDATE users SET profile_images_blocked = ? WHERE id = ?", blocked ? 1 : 0, userId);
    }

    /** The stored file for a name the client asked for, or null when the name is not one this service writes. */
    public Path resolve(String fileName) {
        if (fileName == null || !FILE_NAME.matcher(fileName).matches()) return null;

        Path root = directory().toAbsolutePath().normalize();
        Path file = root.resolve(fileName).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) return null;
        return file;
    }

    static boolean isValidFileName(String fileName) {
        return fileName != null && FILE_NAME.matcher(fileName).matches();
    }

    /** Decodes a JPEG or PNG of exactly the kind's size, reading the size before the pixels. */
    static BufferedImage decode(Kind kind, byte[] bytes) throws ProfileImageException {
        if (bytes == null || bytes.length == 0) {
            throw new ProfileImageException("invalid", "The request holds no picture.");
        }
        if (bytes.length > MAX_BYTES) {
            throw new ProfileImageException("too_large", "The picture is larger than " + MAX_BYTES + " bytes.");
        }
        if (!isJpeg(bytes) && !isPng(bytes)) {
            throw new ProfileImageException("invalid", "Only JPEG and PNG pictures are accepted.");
        }

        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw new ProfileImageException("invalid", "The picture cannot be read.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new ProfileImageException("invalid", "The picture cannot be read.");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                if (reader.getWidth(0) != kind.width || reader.getHeight(0) != kind.height) {
                    throw new ProfileImageException(
                            "invalid", "A " + kind.key + " is " + kind.width + "x" + kind.height + " pixels.");
                }
                BufferedImage image = reader.read(0);
                if (image == null) throw new ProfileImageException("invalid", "The picture cannot be read.");
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new ProfileImageException("invalid", "The picture cannot be read.");
        }
    }

    static byte[] encodeJpeg(BufferedImage source) throws IOException {
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }

        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.9f);
            writer.write(null, new IIOImage(rgb, null, null), param);
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }

    static boolean isJpeg(byte[] bytes) {
        return bytes.length > 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF;
    }

    static boolean isPng(byte[] bytes) {
        return bytes.length > 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
    }

    private static boolean isBlocked(int userId) {
        return SqlQueries.queryOne(
                                "SELECT profile_images_blocked FROM users WHERE id = ? LIMIT 1",
                                set -> set.getInt(1),
                                userId)
                        .orElse(0)
                == 1;
    }

    /** Writes the new file name (or none) and returns the file it replaced. */
    private static String replace(int userId, Kind kind, String fileName) {
        String previous = SqlQueries.queryOne(
                        "SELECT " + kind.column + " FROM users WHERE id = ? LIMIT 1", set -> set.getString(1), userId)
                .orElse(null);
        SqlQueries.update("UPDATE users SET " + kind.column + " = ? WHERE id = ?", fileName, userId);
        return previous;
    }

    private void deleteFile(String fileName) {
        if (!isValidFileName(fileName)) return;
        try {
            Files.deleteIfExists(directory().resolve(fileName));
        } catch (IOException e) {
            LOGGER.warn("Could not delete the old profile picture {}", fileName, e);
        }
    }

    private Path directory() {
        return Path.of(this.settings.location());
    }

    private static String randomHex() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
